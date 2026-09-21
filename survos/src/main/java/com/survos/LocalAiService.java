package com.survos;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class LocalAiService {
    public record AiAction(String tool, JsonObject args) {}
    public record AiReply(String say, List<AiAction> actions) {}
    private record Turn(String role, String text) {}

    private static final Gson GSON = new Gson();
    private static final String RUNTIME_URL =
            "https://github.com/ggml-org/llama.cpp/releases/download/b10964/llama-b10964-bin-win-cpu-x64.zip";
    private static final String MODEL_URL =
            "https://huggingface.co/ggml-org/Qwen3-0.6B-GGUF/resolve/main/Qwen3-0.6B-Q4_0.gguf?download=true";
    private static final int PORT = 11439;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8)).build();
    private final AtomicBoolean booting = new AtomicBoolean();
    private final Deque<Turn> history = new ArrayDeque<>();

    private volatile Process server;
    private volatile String status = "OFFLINE";
    private volatile int downloadPercent;
    private volatile boolean ready;
    private volatile String lastReply = "";

    public String status() { return status; }
    public int downloadPercent() { return downloadPercent; }
    public boolean ready() { return ready; }
    public String lastReply() { return lastReply; }

    public void ensureStarted() {
        if (ready || booting.getAndSet(true)) return;
        Thread.ofVirtual().name("SURV-AI-Boot").start(() -> {
            try {
                status = "PREPARING";
                Path root = FabricLoader.getInstance().getGameDir().resolve("surv-ai");
                Path runtime = root.resolve("llama");
                Path exe = runtime.resolve("llama-server.exe");
                Path model = root.resolve("Qwen3-0.6B-Q4_0.gguf");
                Files.createDirectories(root);

                if (!Files.exists(exe)) {
                    status = "DOWNLOADING RUNTIME";
                    Path zip = root.resolve("llama-win-x64.zip");
                    download(RUNTIME_URL, zip);
                    unzip(zip, runtime);
                    Files.deleteIfExists(zip);
                    Path found = findFile(runtime, "llama-server.exe");
                    if (found != null) exe = found;
                }

                if (!Files.exists(exe)) {
                    Path found = findFile(runtime, "llama-server.exe");
                    if (found != null) exe = found;
                }

                if (!Files.exists(exe)) throw new FileNotFoundException("llama-server.exe");

                if (!Files.exists(model) || Files.size(model) < 300_000_000L) {
                    status = "DOWNLOADING AI " + downloadPercent + "%";
                    download(MODEL_URL, model);
                }

                if (server != null && server.isAlive()) server.destroyForcibly();
                status = "STARTING AI";
                int threads = Math.max(3, Math.min(5, Runtime.getRuntime().availableProcessors() - 2));
                ProcessBuilder pb = new ProcessBuilder(
                        exe.toAbsolutePath().toString(),
                        "-m", model.toAbsolutePath().toString(),
                        "--host", "127.0.0.1",
                        "--port", Integer.toString(PORT),
                        "-c", "1536",
                        "-b", "128",
                        "-t", Integer.toString(threads),
                        "--no-webui",
                        "--reasoning-format", "none"
                );
                pb.directory(runtime.toFile());
                pb.redirectErrorStream(true);
                server = pb.start();
                Thread.ofVirtual().name("SURV-AI-Log").start(() -> drain(server));

                long end = System.currentTimeMillis() + 90_000L;
                while (System.currentTimeMillis() < end && server.isAlive()) {
                    if (health()) {
                        ready = true;
                        status = "ONLINE";
                        break;
                    }
                    Thread.sleep(350);
                }
                if (!ready) status = "AI START ERROR";
            } catch (Throwable t) {
                status = "AI ERROR";
            } finally {
                booting.set(false);
            }
        });
    }

    public void stop() {
        ready = false;
        status = "OFFLINE";
        if (server != null) {
            try { server.destroyForcibly(); } catch (Throwable ignored) {}
        }
        server = null;
    }

    public void ask(String user, String gameContext, Consumer<AiReply> callback) {
        ensureStarted();
        if (!ready) {
            String msg = status.startsWith("DOWNLOADING") ? status : "AI is " + status.toLowerCase(Locale.ROOT);
            callback.accept(new AiReply(msg, List.of()));
            return;
        }
        Thread.ofVirtual().name("SURV-AI-Chat").start(() -> {
            try {
                JsonObject req = new JsonObject();
                req.addProperty("model", "Qwen3-0.6B");
                req.addProperty("temperature", 0.20);
                req.addProperty("top_p", 0.85);
                req.addProperty("max_tokens", 110);
                req.addProperty("stream", false);

                JsonArray messages = new JsonArray();
                addMessage(messages, "system", systemPrompt(gameContext));
                synchronized (history) {
                    for (Turn t : history) addMessage(messages, t.role(), t.text());
                }
                addMessage(messages, "user", user + "\n/no_think");
                req.add("messages", messages);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + PORT + "/v1/chat/completions"))
                        .timeout(Duration.ofSeconds(20))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(req), StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                String content = root.getAsJsonArray("choices").get(0).getAsJsonObject()
                        .getAsJsonObject("message").get("content").getAsString();
                AiReply parsed = parseReply(stripHidden(content));
                lastReply = parsed.say();

                synchronized (history) {
                    history.addLast(new Turn("user", user));
                    history.addLast(new Turn("assistant", parsed.say()));
                    while (history.size() > 4) history.removeFirst();
                }
                callback.accept(parsed);
            } catch (Throwable t) {
                ready = false;
                status = "AI CONNECTION ERROR";
                callback.accept(new AiReply("My local AI connection failed. I kept automation controls available.", List.of()));
                ensureStarted();
            }
        });
    }

    private AiReply parseReply(String content) {
        try {
            int a = content.indexOf('{'), b = content.lastIndexOf('}');
            if (a >= 0 && b > a) {
                JsonObject o = JsonParser.parseString(content.substring(a, b + 1)).getAsJsonObject();
                String say = o.has("say") ? stripHidden(o.get("say").getAsString()) : "Okay.";
                if (say.length() > 180) say = say.substring(0, 180);
                List<AiAction> actions = new ArrayList<>();
                if (o.has("actions") && o.get("actions").isJsonArray()) {
                    for (JsonElement el : o.getAsJsonArray("actions")) {
                        if (!el.isJsonObject()) continue;
                        JsonObject x = el.getAsJsonObject();
                        String tool = x.has("tool") ? x.get("tool").getAsString() : "";
                        JsonObject args = x.has("args") && x.get("args").isJsonObject() ? x.getAsJsonObject("args") : new JsonObject();
                        if (!tool.isBlank()) actions.add(new AiAction(tool, args));
                    }
                }
                return new AiReply(say, actions);
            }
        } catch (Throwable ignored) {}
        String safe = stripHidden(content == null ? "" : content).trim();
        if (safe.length() > 180) safe = safe.substring(0, 180);
        return new AiReply(safe.isBlank() ? "Okay." : safe, List.of());
    }

    private String systemPrompt(String context) {
        return """
You are SURV, a fast local Minecraft assistant. /no_think.
Be natural, calm, concise and useful. Never output reasoning, analysis, chain-of-thought, hidden thoughts, or planning narration.
Speak in one short sentence unless the user asks for details.
Return ONLY JSON: {"say":"short reply","actions":[{"tool":"name","args":{}}]}

TOOLS:
start_task(mode,target?,count?) modes=MINING,MOB_GRIND,TREE_FARM,CROP_FARM,FISHING,ANIMAL_FARM
queue_task(mode,target?,count?) | return_start | stop_task | pause_task | resume_task
move_player(direction,seconds) | turn_player(degrees) | jump | use_item | eat | select_item(item)
look_hostile(range?) | attack_hostile(range?)
set_profile(name) | add_rule(type,value,item?) | clear_rules
save_waypoint(name) | go_waypoint(name) | navigate_to(x,y,z) | start_route_recording(name) | stop_route_recording | play_route(name)
play_mode(enabled) | remember(text) | recall(query)
apply_loadout(name)
set_villager_target(enchantment,min_level?,max_price?,delay_ms?,start?)
toggle_villager | enchant_item(enchants) | craft_item(item,count?,max?)
set_hud(module,enabled) | find_storage(item) | session_stats

Use tools when the user asks you to do something in-game. Never claim an action happened without the matching tool.
If the user says play the game, take over, autopilot, or play for me, use play_mode enabled=true.
If the user says stop playing or give control back, use play_mode enabled=false.
If the user gives coordinates, use navigate_to.
If the user asks you to remember something, use remember. If they ask what you remember, use recall.
No anti-cheat bypass, hidden/x-ray knowledge, admin/server commands, or OS commands.

STATE:
""" + context;
    }

    private static String stripHidden(String text) {
        if (text == null) return "";
        String out = text
                .replaceAll("(?is)<think>.*?</think>", "")
                .replaceAll("(?is)<analysis>.*?</analysis>", "")
                .replaceAll("(?is)<reasoning>.*?</reasoning>", "")
                .replaceAll("(?i)^\\s*(analysis|reasoning|thought process|chain of thought)\\s*:\\s*.*$", "")
                .trim();

        int open = out.toLowerCase(Locale.ROOT).indexOf("<think>");
        if (open >= 0) out = out.substring(0, open).trim();
        return out;
    }

    private static void addMessage(JsonArray arr, String role, String content) {
        JsonObject m = new JsonObject();
        m.addProperty("role", role);
        m.addProperty("content", content);
        arr.add(m);
    }

    private boolean health() {
        try {
            HttpRequest r = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + PORT + "/health"))
                    .timeout(Duration.ofSeconds(2)).GET().build();
            return http.send(r, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private void download(String url, Path out) throws Exception {
        Files.createDirectories(out.getParent());
        Path tmp = out.resolveSibling(out.getFileName() + ".part");
        long done = Files.exists(tmp) ? Files.size(tmp) : 0L;

        HttpURLConnection con = (HttpURLConnection) URI.create(url).toURL().openConnection();
        con.setInstanceFollowRedirects(true);
        con.setConnectTimeout(15000);
        con.setReadTimeout(45000);
        con.setRequestProperty("User-Agent", "SURV-OS/4.0");
        if (done > 0) con.setRequestProperty("Range", "bytes=" + done + "-");

        int code = con.getResponseCode();
        if (code >= 300 && code < 400 && con.getHeaderField("Location") != null) {
            con.disconnect();
            download(con.getHeaderField("Location"), out);
            return;
        }

        if (done > 0 && code != 206) {
            Files.deleteIfExists(tmp);
            done = 0L;
            con.disconnect();
            con = (HttpURLConnection) URI.create(url).toURL().openConnection();
            con.setInstanceFollowRedirects(true);
            con.setConnectTimeout(15000);
            con.setReadTimeout(45000);
            con.setRequestProperty("User-Agent", "SURV-OS/4.0");
            code = con.getResponseCode();
        }

        if (code < 200 || code >= 300) throw new IOException("Download HTTP " + code);

        long remaining = con.getContentLengthLong();
        long expected = remaining > 0 ? done + remaining : -1L;

        try (InputStream in = con.getInputStream();
             OutputStream os = Files.newOutputStream(
                     tmp,
                     StandardOpenOption.CREATE,
                     done > 0 ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING)) {

            byte[] buf = new byte[1024 * 256];
            int n;
            while ((n = in.read(buf)) > 0) {
                os.write(buf, 0, n);
                done += n;
                if (expected > 0) {
                    downloadPercent = (int)Math.min(99, Math.round(done * 100.0 / expected));
                    if (status.startsWith("DOWNLOADING AI"))
                        status = "DOWNLOADING AI " + downloadPercent + "%";
                }
            }
        } finally {
            con.disconnect();
        }

        Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
        downloadPercent = 100;
    }

    private static void unzip(Path zip, Path dir) throws IOException {
        Files.createDirectories(dir);
        try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                Path out = dir.resolve(e.getName()).normalize();
                if (!out.startsWith(dir)) throw new IOException("Unsafe zip entry");
                Files.createDirectories(out.getParent());
                Files.copy(zin, out, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static Path findFile(Path root, String fileName) {
        try (var stream = Files.walk(root, 4)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase(fileName))
                    .findFirst().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static void drain(Process p) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            while (br.readLine() != null) {}
        } catch (Exception ignored) {}
    }
}
