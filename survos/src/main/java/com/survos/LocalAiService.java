package com.survos;

import com.google.gson.*;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class LocalAiService {
    public record AiAction(String tool, JsonObject args) {}
    public record AiReply(String say, List<AiAction> actions) {}
    private record Turn(String role, String text) {}

    private enum Backend { NONE, LM_STUDIO, OLLAMA, OPENAI_LOCAL }

    private static final Gson GSON = new Gson();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    private final AtomicBoolean detecting = new AtomicBoolean();
    private final Deque<Turn> history = new ArrayDeque<>();

    private volatile Backend backend = Backend.NONE;
    private volatile String baseUrl = "";
    private volatile String model = "";
    private volatile String status = "SEARCHING";
    private volatile boolean ready;
    private volatile String lastReply = "";
    private volatile long lastDetectMs;

    public String status() { return status; }
    public boolean ready() { return ready; }
    public String lastReply() { return lastReply; }
    public int downloadPercent() { return 0; }

    public String backendName() {
        return switch (backend) {
            case LM_STUDIO -> "LM STUDIO";
            case OLLAMA -> "OLLAMA";
            case OPENAI_LOCAL -> "LOCAL OPENAI";
            default -> "NONE";
        };
    }

    public void ensureStarted() {
        if (ready) return;
        long now = System.currentTimeMillis();
        if (now - lastDetectMs < 2500L || detecting.getAndSet(true)) return;
        lastDetectMs = now;

        Thread.ofVirtual().name("SURV-AI-Detect").start(() -> {
            try {
                status = "SEARCHING LOCAL AI";

                if (detectOpenAi(
                        "http://127.0.0.1:1234/v1",
                        Backend.LM_STUDIO,
                        "LM STUDIO")) {
                    return;
                }

                if (detectOllama()) {
                    return;
                }

                if (detectOpenAi(
                        "http://127.0.0.1:11439/v1",
                        Backend.OPENAI_LOCAL,
                        "LOCAL OPENAI")) {
                    return;
                }

                ready = false;
                backend = Backend.NONE;
                baseUrl = "";
                model = "";
                status = "NO LOCAL AI SERVER";
            } finally {
                detecting.set(false);
            }
        });
    }

    public void stop() {
        ready = false;
        backend = Backend.NONE;
        baseUrl = "";
        model = "";
        status = "OFFLINE";
    }

    public void ask(String user, String gameContext, Consumer<AiReply> callback) {
        if (!ready) {
            ensureStarted();
            callback.accept(new AiReply(
                    "I can't reach a local AI server yet. Direct voice controls and takeover still work.",
                    List.of()));
            return;
        }

        Thread.ofVirtual().name("SURV-AI-Chat").start(() -> {
            try {
                String content = switch (backend) {
                    case OLLAMA -> askOllama(user, gameContext);
                    case LM_STUDIO, OPENAI_LOCAL -> askOpenAi(user, gameContext);
                    default -> "";
                };

                AiReply parsed = parseReply(stripHidden(content));
                lastReply = parsed.say();

                synchronized (history) {
                    history.addLast(new Turn("user", compact(user)));
                    history.addLast(new Turn("assistant", compact(parsed.say())));
                    while (history.size() > 6) history.removeFirst();
                }

                callback.accept(parsed);
            } catch (Throwable t) {
                ready = false;
                status = "AI CONNECTION LOST";
                backend = Backend.NONE;
                callback.accept(new AiReply(
                        "The local AI connection dropped. Direct controls are still available.",
                        List.of()));
                ensureStarted();
            }
        });
    }

    private String askOpenAi(String user, String gameContext) throws Exception {
        JsonObject req = new JsonObject();
        req.addProperty("model", model);
        req.addProperty("temperature", 0.20);
        req.addProperty("top_p", 0.85);
        req.addProperty("max_tokens", 130);
        req.addProperty("stream", false);

        JsonArray messages = buildMessages(user, gameContext);
        req.add("messages", messages);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .timeout(Duration.ofSeconds(18))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        GSON.toJson(req),
                        StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = http.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }

        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        return root.getAsJsonArray("choices")
                .get(0).getAsJsonObject()
                .getAsJsonObject("message")
                .get("content").getAsString();
    }

    private String askOllama(String user, String gameContext) throws Exception {
        JsonObject req = new JsonObject();
        req.addProperty("model", model);
        req.addProperty("stream", false);

        JsonArray messages = buildMessages(user, gameContext);
        req.add("messages", messages);

        JsonObject options = new JsonObject();
        options.addProperty("temperature", 0.20);
        options.addProperty("num_predict", 130);
        req.add("options", options);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/chat"))
                .timeout(Duration.ofSeconds(18))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        GSON.toJson(req),
                        StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = http.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }

        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        return root.getAsJsonObject("message").get("content").getAsString();
    }

    private JsonArray buildMessages(String user, String gameContext) {
        JsonArray messages = new JsonArray();
        addMessage(messages, "system", systemPrompt(gameContext));

        synchronized (history) {
            for (Turn t : history) {
                addMessage(messages, t.role(), t.text());
            }
        }

        addMessage(messages, "user", user + "\n/no_think");
        return messages;
    }

    private boolean detectOpenAi(
            String candidateBase,
            Backend candidateBackend,
            String label
    ) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(candidateBase + "/models"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();

            HttpResponse<String> response = http.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) return false;

            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonArray data = root.has("data") && root.get("data").isJsonArray()
                    ? root.getAsJsonArray("data")
                    : new JsonArray();

            if (data.isEmpty()) return false;

            JsonObject first = data.get(0).getAsJsonObject();
            String id = first.has("id") ? first.get("id").getAsString() : "";
            if (id.isBlank()) return false;

            backend = candidateBackend;
            baseUrl = candidateBase;
            model = id;
            ready = true;
            status = label + " ONLINE";
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean detectOllama() {
        try {
            String candidate = "http://127.0.0.1:11434";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(candidate + "/api/tags"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();

            HttpResponse<String> response = http.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) return false;

            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonArray models = root.has("models") && root.get("models").isJsonArray()
                    ? root.getAsJsonArray("models")
                    : new JsonArray();

            if (models.isEmpty()) return false;

            JsonObject first = models.get(0).getAsJsonObject();
            String name = first.has("name") ? first.get("name").getAsString() : "";
            if (name.isBlank()) return false;

            backend = Backend.OLLAMA;
            baseUrl = candidate;
            model = name;
            ready = true;
            status = "OLLAMA ONLINE";
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private AiReply parseReply(String content) {
        try {
            int a = content.indexOf('{');
            int b = content.lastIndexOf('}');

            if (a >= 0 && b > a) {
                JsonObject o = JsonParser.parseString(
                        content.substring(a, b + 1)).getAsJsonObject();

                String say = o.has("say")
                        ? stripHidden(o.get("say").getAsString())
                        : "Okay.";

                if (say.length() > 180) say = say.substring(0, 180);

                List<AiAction> actions = new ArrayList<>();
                if (o.has("actions") && o.get("actions").isJsonArray()) {
                    for (JsonElement el : o.getAsJsonArray("actions")) {
                        if (!el.isJsonObject()) continue;
                        JsonObject x = el.getAsJsonObject();
                        String tool = x.has("tool")
                                ? x.get("tool").getAsString()
                                : "";
                        JsonObject args = x.has("args") && x.get("args").isJsonObject()
                                ? x.getAsJsonObject("args")
                                : new JsonObject();
                        if (!tool.isBlank()) {
                            actions.add(new AiAction(tool, args));
                        }
                    }
                }

                return new AiReply(say, actions);
            }
        } catch (Throwable ignored) {}

        String safe = stripHidden(content == null ? "" : content).trim();
        if (safe.length() > 180) safe = safe.substring(0, 180);

        return new AiReply(
                safe.isBlank() ? "Okay." : safe,
                List.of());
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

    private static void addMessage(
            JsonArray arr,
            String role,
            String content
    ) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content);
        arr.add(message);
    }

    private static String compact(String value) {
        if (value == null) return "";
        String one = value.replaceAll("\\s+", " ").trim();
        return one.length() <= 180 ? one : one.substring(0, 180);
    }
}
