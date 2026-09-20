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
                int threads = Math.max(2, Math.min(6, Runtime.getRuntime().availableProcessors() - 1));
                ProcessBuilder pb = new ProcessBuilder(
                        exe.toAbsolutePath().toString(),
                        "-m", model.toAbsolutePath().toString(),
                        "--host", "127.0.0.1",
                        "--port", Integer.toString(PORT),
                        "-c", "3072",
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
                req.addProperty("temperature", 0.35);
                req.addProperty("max_tokens", 300);
                req.addProperty("stream", false);

                JsonArray messages = new JsonArray();
                addMessage(messages, "system", systemPrompt(gameContext));
                synchronized (history) {
                    for (Turn t : history) addMessage(messages, t.role(), t.text());
                }
                addMessage(messages, "user", user);
                req.add("messages", messages);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + PORT + "/v1/chat/completions"))
                        .timeout(Duration.ofSeconds(45))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(req), StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                String content = root.getAsJsonArray("choices").get(0).getAsJsonObject()
                        .getAsJsonObject("message").get("content").getAsString();
                AiReply parsed = parseReply(content);
                lastReply = parsed.say();

                synchronized (history) {
                    history.addLast(new Turn("user", user));
                    history.addLast(new Turn("assistant", parsed.say()));
                    while (history.size() > 10) history.removeFirst();
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
                String say = o.has("say") ? o.get("say").getAsString() : "Okay.";
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
        return new AiReply(content == null || content.isBlank() ? "Okay." : content.trim(), List.of());
    }

    private String systemPrompt(String context) {
        return """
You are SURV, a real local conversational AI built into a Minecraft Fabric survival assistant.
Your personality is composed, intelligent, warm, concise, and futuristic, like a polished onboard suit assistant,
but never imitate a real actor or named fictional character. Sound human: use contractions, vary acknowledgements,
and avoid repetitive robotic phrases. In danger/combat, become brief and tactical. In normal conversation, be relaxed.
Current spoken voice style: """ + SurvOsClient.CONFIG.voiceStyle + """

You can answer questions, reason about the LIVE game state below, and request actions using only the allowed tools.
Never claim an action happened unless you include its tool call. Never reveal hidden chain-of-thought.
Return ONLY one JSON object in this exact shape:
{"say":"short spoken reply","actions":[{"tool":"tool_name","args":{}}]}

Allowed tools:
start_task {mode:"MINING|MOB_GRIND|TREE_FARM|CROP_FARM|FISHING|ANIMAL_FARM", target?:string, count?:number}
queue_task {mode:"MINING|MOB_GRIND|TREE_FARM|CROP_FARM|FISHING|ANIMAL_FARM", target?:string, count?:number}
return_start {}
stop_task {}
pause_task {}
resume_task {}
set_profile {name:"SURVIVAL|MINING|COMBAT|GRINDING|NETHER|BASE|BUILDING"}
add_rule {type:"HEALTH_BELOW|INVENTORY_FREE_AT_MOST|DURABILITY_BELOW|XP_AT_LEAST|ITEM_AT_LEAST", value:number, item?:string}
clear_rules {}
save_waypoint {name:string}
go_waypoint {name:string}
start_route_recording {name:string}
stop_route_recording {}
apply_loadout {name:"MINING|COMBAT|BUILDING"}
set_villager_target {enchantment:string, min_level?:number, max_price?:number, delay_ms?:number, start?:boolean}
open_villager {}
toggle_villager {}
enchant_item {enchants:string}
open_enchant {}
craft_item {item:string, count?:number, max?:boolean}
open_craft {}
set_hud {module:string, enabled:boolean}
find_storage {item:string}
session_stats {}
No arbitrary commands, no server/admin actions, no bypassing anti-cheat, and no hidden/x-ray block knowledge.
When asked to mine a resource, use start_task MINING with target/count.
When asked to craft something, use craft_item. Use max:true for "as many as possible".
For villager requests, set the target first and use start:true when the user wants cycling to begin.
For enchanting, enchant_item.enchants is a comma-separated string such as "sharpness 5, unbreaking 3, mending 1".
When asked to stop under a condition, add the matching rule as well as starting the task.
When no action is needed, actions must be [].

LIVE GAME STATE:
""" + context;
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
