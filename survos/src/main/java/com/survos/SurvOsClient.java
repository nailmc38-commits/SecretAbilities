package com.survos;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public final class SurvOsClient implements ClientModInitializer {
    public static final SurvConfig CONFIG = SurvConfig.load();
    public static final AutomationManager AUTOMATION = new AutomationManager();
    public static final VoiceService VOICE = new VoiceService();
    public static final LocalAiService AI = new LocalAiService();
    public static final TtsService TTS = new TtsService();
    public static final LegacyBridge LEGACY = new LegacyBridge();
    public static final WorldMemory MEMORY = new WorldMemory();
    public static final RuleEngine RULES = new RuleEngine();
    public static final StatsTracker STATS = new StatsTracker();
    public static final PlayerControlService CONTROL = new PlayerControlService();
    public static final ConversationMemory CHAT_MEMORY = new ConversationMemory();
    public static final PlayController PLAY = new PlayController();

    private static KeyBinding menuKey, voiceKey, emergencyKey;
    private static long armedUntil;
    private static int ticks;
    private static boolean voiceStarted;
    private static boolean aiStarted;
    private static long lastSpokenAlert;
    private static String lastDimension = "";
    private static BlockPos lastAlivePos;
    private static String lastAliveDimension = "";
    private static boolean deathWaypointSaved;
    private static final ArrayDeque<String> COMMAND_HISTORY = new ArrayDeque<>();

    @Override
    public void onInitializeClient() {
        LEGACY.initialize();

        TTS.setEnabled(CONFIG.ttsEnabled && CONFIG.aiSpeakReplies);
        TTS.setStyle(CONFIG.voiceStyle);

        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.survos.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F9, KeyBinding.Category.MISC));
        voiceKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.survos.voice", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F8, KeyBinding.Category.MISC));
        emergencyKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.survos.emergency", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F7, KeyBinding.Category.MISC));

        VOICE.setListener(SurvOsClient::onVoiceHeard);

        if (CONFIG.voiceEnabled && CONFIG.voiceAutoStart) {
            voiceStarted = true;
            VOICE.start(CONFIG);
        }
        if (CONFIG.aiEnabled && CONFIG.aiAutoStart) {
            aiStarted = true;
            AI.ensureStarted();
        }

        ClientTickEvents.END_CLIENT_TICK.register(SurvOsClient::tick);
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.of("survos", "main_hud"),
                SurvOsClient::renderHud
        );

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("surv")
                    .executes(ctx -> {
                        MinecraftClient.getInstance().setScreen(new SurvScreen(SurvScreen.Tab.DASHBOARD));
                        return 1;
                    })
                    .then(ClientCommandManager.literal("ask")
                            .then(ClientCommandManager.argument("message", StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        askAi(StringArgumentType.getString(ctx, "message"), false);
                                        return 1;
                                    }))));

            dispatcher.register(ClientCommandManager.literal("craft")
                    .then(ClientCommandManager.argument("item", StringArgumentType.greedyString())
                            .executes(ctx -> LEGACY.queueCraft(
                                    MinecraftClient.getInstance(),
                                    ctx.getSource(),
                                    StringArgumentType.getString(ctx, "item")
                            ))));
        });
    }

    private static void tick(MinecraftClient client) {
        while (menuKey.wasPressed()) {
            if (client.currentScreen instanceof SurvScreen) client.setScreen(null);
            else client.setScreen(new SurvScreen(SurvScreen.Tab.DASHBOARD));
        }

        while (voiceKey.wasPressed()) {
            if (VOICE.isRunning()) {
                VOICE.stop();
                CONFIG.voiceEnabled = false;
                CONFIG.voiceAutoStart = false;
                voiceStarted = false;
                notice("Voice off.");
            } else {
                CONFIG.voiceEnabled = true;
                CONFIG.voiceAutoStart = true;
                voiceStarted = true;
                VOICE.start(CONFIG);
                notice("Voice listening.");
            }
            CONFIG.save();
        }

        while (emergencyKey.wasPressed()) {
            if (PLAY.enabled()) PLAY.stop(client);
            AUTOMATION.stop(client, "Emergency stop");
            CONTROL.stop(client);
            TTS.stopAll();
            TTS.speak("Stopped. You have control.", "TACTICAL");
        }

        LEGACY.tick(client);
        ticks++;

        if (CONFIG.voiceEnabled && CONFIG.voiceAutoStart && !VOICE.isRunning()) {
            VOICE.start(CONFIG);
            voiceStarted = true;
        }

        if (CONFIG.aiEnabled && CONFIG.aiAutoStart && !AI.ready() && ticks % 80 == 0) {
            AI.ensureStarted();
        }

        if (client.player == null || client.world == null) {
            return;
        }
        MEMORY.tick(client);
        CHAT_MEMORY.tick(client);
        STATS.tick(client);
        handleDimensionProfile(client);
        handleDeathMemory(client);

        if (CONFIG.autoHotbar && ticks % 30 == 0 && !AUTOMATION.active()) {
            InventoryManager.tickAutoHotbar(client);
        }

        if (!AUTOMATION.active()) {
            CONTROL.tick(client);
        }

        PLAY.tick(client, CONFIG);
        AUTOMATION.tick(client, CONFIG);

        if (CONFIG.smartAlerts && ticks % 80 == 0) {
            smartAlerts(client);
        }
    }

    private static void smartAlerts(MinecraftClient client) {
        ItemStack held = client.player.getMainHandStack();
        if (!held.isEmpty() && held.isDamageable()) {
            int pct = InventoryManager.durabilityPercent(held);
            if (pct <= 10) {
                String msg = held.getName().getString() + " is at " + pct + " percent durability.";
                notice(msg);
                speakAlert(msg);
            }
        }

        if (InventoryManager.freeSlots(client.player) <= 2) {
            String msg = "Inventory is nearly full.";
            notice(msg);
            speakAlert(msg);
        }

        int hostile = client.world.getEntitiesByClass(
                HostileEntity.class,
                client.player.getBoundingBox().expand(8),
                Entity -> Entity.isAlive()
        ).size();
        if (hostile >= 4 && client.player.getHealth() <= 12f) {
            speakAlert("Multiple hostiles nearby. I recommend disengaging.");
        }
    }

    private static void speakAlert(String text) {
        long now = System.currentTimeMillis();
        if (now - lastSpokenAlert < 12_000L) return;
        lastSpokenAlert = now;
        if (CONFIG.aiSpeakReplies && CONFIG.ttsEnabled) TTS.speak(text, "TACTICAL");
    }

    private static void onVoiceHeard(String raw) {
        if (TTS.isSpeaking()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> handleVoice(client, raw));
    }

    private static void handleVoice(MinecraftClient client, String raw) {
        if (raw == null || raw.isBlank()) return;

        String q = raw.toLowerCase(Locale.ROOT).trim();
        boolean wake = q.matches(".*\\b(surv|serve|sir)\\b.*");

        if (wake) {
            q = q.replaceFirst("(?i).*?\\b(surv|serve|sir)\\b[ ,.:;-]*", "").trim();
            armedUntil = System.currentTimeMillis() + 8_000L;
        } else if (CONFIG.requireWakeWord && System.currentTimeMillis() > armedUntil) {
            return;
        }

        if (q.isBlank()) {
            armedUntil = System.currentTimeMillis() + 8_000L;
            notice("Listening…");
            TTS.speak("I'm listening.", "MINIMAL");
            return;
        }

        if (containsAny(q, "stop", "cancel everything", "stop everything", "cancel automation", "give me control")) {
            if (PLAY.enabled()) PLAY.stop(client);
            AUTOMATION.stop(client, "Voice command");
            CONTROL.stop(client);
            TTS.stopAll();
            TTS.speak("Stopped. You have control.", "TACTICAL");
            return;
        }

        if (containsAny(q, "play the game", "play for me", "take over", "autopilot")) {
            PLAY.start(client);
            return;
        }

        if (containsAny(q, "stop playing", "turn off play mode")) {
            PLAY.stop(client);
            return;
        }

        if (q.contains("pause")) {
            AUTOMATION.pause(client);
            TTS.speak("Paused.", "TACTICAL");
            return;
        }

        if (q.contains("resume") || q.contains("continue")) {
            AUTOMATION.resume();
            TTS.speak("Resuming.", "TACTICAL");
            return;
        }

        if (containsAny(q, "emergency", "abort")) {
            if (PLAY.enabled()) PLAY.stop(client);
            AUTOMATION.stop(client, "Voice abort");
            CONTROL.stop(client);
            TTS.stopAll();
            TTS.speak("All control released.", "TACTICAL");
            return;
        }

        askAi(q, true);
    }

    public static void askAi(String userText, boolean spokenRequest) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (userText == null || userText.isBlank()) return;
        rememberCommand(userText);
        if (CONFIG.memoryEnabled) CHAT_MEMORY.addTurn("user", userText);

        if (!CONFIG.aiEnabled) {
            notice("Local AI is disabled.");
            return;
        }

        AI.ensureStarted();

        if (!AI.ready()) {
            String msg = AI.status();
            notice("AI // " + msg);
            if (spokenRequest && CONFIG.aiSpeakReplies) {
                if (msg.startsWith("DOWNLOADING AI")) TTS.speak("I'm downloading my local language model. I'll be ready once that finishes.", "NORMAL");
                else TTS.speak("My local AI is starting up.", "NORMAL");
            }
            return;
        }

        String context = buildGameContext(client);
        if (CONFIG.memoryEnabled) {
            String memory = CHAT_MEMORY.context();
            if (!memory.isBlank()) context += "\n" + memory;
        }
        AI.ask(userText, context, reply -> client.execute(() -> {
            List<String> toolResults = new ArrayList<>();
            for (LocalAiService.AiAction action : reply.actions()) {
                String result = AiToolRouter.execute(client, action);
                toolResults.add(action.tool() + ": " + result);
            }

            String say = naturalizeReply(reply.say(), reply.actions(), toolResults);
            if (CONFIG.memoryEnabled) CHAT_MEMORY.addTurn("assistant", say);
            notice("AI // " + say);

            if (CONFIG.aiSpeakReplies && CONFIG.ttsEnabled) {
                TTS.setEnabled(true);
                TTS.setStyle(CONFIG.voiceStyle);
                TTS.speak(say, CONFIG.voiceStyle);
            }
        }));
    }

    private static String naturalizeReply(
            String original,
            List<LocalAiService.AiAction> actions,
            List<String> results
    ) {
        String say = original == null ? "" : original.trim();
        if (say.isBlank()) say = actions.isEmpty() ? "All systems nominal." : "Done.";

        for (int i = 0; i < actions.size() && i < results.size(); i++) {
            String tool = actions.get(i).tool();
            String result = results.get(i);
            int colon = result.indexOf(':');
            String value = colon >= 0 ? result.substring(colon + 1).trim() : result;

            if ("find_storage".equals(tool)) {
                say = value.equals("not remembered")
                        ? "I don't have a remembered container with that item yet."
                        : "I found it in remembered storage at " + value + ".";
            } else if ("session_stats".equals(tool)) {
                say = "Current session: " + value + ".";
            } else if ("go_waypoint".equals(tool) && value.equals("waypoint missing")) {
                say = "I don't have that waypoint saved yet.";
            }
        }
        return say;
    }

    public static String buildGameContext(MinecraftClient client) {
        if (client.player == null || client.world == null) return "No world loaded.";

        var p = client.player;
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (int i = 0; i < p.getInventory().size(); i++) {
            ItemStack stack = p.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            String id = Registries.ITEM.getId(stack.getItem()).getPath();
            counts.merge(id, stack.getCount(), Integer::sum);
        }

        StringBuilder inv = new StringBuilder();
        int shown = 0;
        for (var e : counts.entrySet()) {
            if (shown++ >= 10) break;
            if (!inv.isEmpty()) inv.append(", ");
            inv.append(e.getKey()).append(" x").append(e.getValue());
        }

        int hostile = client.world.getEntitiesByClass(
                HostileEntity.class,
                p.getBoundingBox().expand(14),
                e -> e.isAlive()
        ).size();

        String held = p.getMainHandStack().isEmpty()
                ? "empty"
                : Registries.ITEM.getId(p.getMainHandStack().getItem()).getPath()
                + " (" + InventoryManager.durabilityPercent(p.getMainHandStack()) + "% durability)";

        return """
Player:
health=%.1f/%.1f
food=%d/20
armor=%d/20
xp_level=%d
position=%d,%d,%d
dimension=%s
held_item=%s
free_inventory_slots=%d
nearby_hostiles=%d

Automation:
mode=%s
state=%s
reason=%s
goal=%s %d
queued_tasks=%d
play_mode=%s
play_phase=%s
play_status=%s

Rules:
%s

Waypoints:
%s

Remembered routes:
%s

Inventory summary:
%s

Session:
%s
""".formatted(
                p.getHealth(), p.getMaxHealth(),
                p.getHungerManager().getFoodLevel(),
                p.getArmor(),
                p.experienceLevel,
                p.getBlockX(), p.getBlockY(), p.getBlockZ(),
                client.world.getRegistryKey().getValue(),
                held,
                InventoryManager.freeSlots(p),
                hostile,
                AUTOMATION.mode(),
                AUTOMATION.state(),
                AUTOMATION.reason(),
                AUTOMATION.goalItem(),
                AUTOMATION.goalCount(),
                AUTOMATION.queuedTasks(),
                PLAY.enabled(),
                PLAY.phase(),
                PLAY.status(),
                RULES.all(),
                MEMORY.waypointNames(),
                MEMORY.routeNames(),
                inv,
                STATS.summary()
        );
    }

    private static void handleDimensionProfile(MinecraftClient client) {
        if (!CONFIG.autoDimensionProfiles || client.world == null) return;
        String dim = client.world.getRegistryKey().getValue().toString();
        if (dim.equals(lastDimension)) return;
        lastDimension = dim;

        if (dim.contains("the_nether")) {
            CONFIG.applyProfile("NETHER");
        } else if (dim.contains("the_end")) {
            CONFIG.applyProfile("COMBAT");
        } else if (dim.contains("overworld") && ("NETHER".equals(CONFIG.profile) || "COMBAT".equals(CONFIG.profile))) {
            CONFIG.applyProfile("SURVIVAL");
        }
    }

    private static void handleDeathMemory(MinecraftClient client) {
        if (!CONFIG.autoDeathWaypoint || client.player == null || client.world == null) return;
        String dim = client.world.getRegistryKey().getValue().toString();

        if (client.player.getHealth() > 0f) {
            lastAlivePos = new BlockPos(client.player.getBlockX(), client.player.getBlockY(), client.player.getBlockZ());
            lastAliveDimension = dim;
            deathWaypointSaved = false;
            return;
        }

        if (!deathWaypointSaved && lastAlivePos != null && !lastAliveDimension.isBlank()) {
            MEMORY.setWaypointAt("last_death", lastAlivePos, lastAliveDimension);
            deathWaypointSaved = true;
            notice("Saved last_death waypoint.");
        }
    }

    private static void rememberCommand(String text) {
        if (text == null || text.isBlank()) return;
        COMMAND_HISTORY.addFirst(text.trim());
        while (COMMAND_HISTORY.size() > 12) COMMAND_HISTORY.removeLast();
    }

    public static List<String> recentCommands() {
        return List.copyOf(COMMAND_HISTORY);
    }

    private static boolean containsAny(String q, String... terms) {
        for (String t : terms) if (q.contains(t)) return true;
        return false;
    }

    public static void notice(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(
                    Text.literal("SURV // ").formatted(Formatting.AQUA)
                            .append(Text.literal(message).formatted(Formatting.WHITE)),
                    true
            );
        }
    }

    public static String statusLine(MinecraftClient client) {
        if (client.player == null) return "No player";
        return String.format(
                "HP %.1f | Food %d | Armor %d | XP %d | %s | PLAY %s | AI %s",
                client.player.getHealth(),
                client.player.getHungerManager().getFoodLevel(),
                client.player.getArmor(),
                client.player.experienceLevel,
                AUTOMATION.mode(),
                PLAY.enabled() ? PLAY.phase() : "OFF",
                AI.status()
        );
    }

    public static String dayNight(MinecraftClient client) {
        if (client.world == null) return "No world";
        long t = Math.floorMod(client.world.getTimeOfDay(), 24000L);
        boolean day = t < 13000L;
        long left = day ? 13000L - t : 24000L - t;
        int sec = (int)Math.round(left / 20.0);
        return (day ? "Day" : "Night") + " // " + (sec / 60) + "m " + (sec % 60) + "s remaining";
    }

    private static void renderHud(DrawContext ctx, RenderTickCounter counter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!CONFIG.hudEnabled || client.player == null || client.world == null || client.textRenderer == null) return;

        List<Line> lines = new ArrayList<>();
        var p = client.player;

        if (CONFIG.showHealth)
            lines.add(new Line(
                    String.format("HP %.1f/%.1f", p.getHealth(), p.getMaxHealth()),
                    p.getHealth() <= CONFIG.retreatHealth ? 0xFFFF5D5D : 0xFF7CFFB2));

        if (CONFIG.showHunger)
            lines.add(new Line("FOOD " + p.getHungerManager().getFoodLevel() + "/20", 0xFFFFD36A));

        if (CONFIG.showArmor)
            lines.add(new Line("ARMOR " + p.getArmor() + "/20", 0xFF8BE9FD));

        if (CONFIG.showXp)
            lines.add(new Line("XP LVL " + p.experienceLevel, 0xFFC7FF77));

        if (CONFIG.showCoords)
            lines.add(new Line(
                    "XYZ " + p.getBlockX() + " " + p.getBlockY() + " " + p.getBlockZ(),
                    0xFFD7E3EA));

        if (CONFIG.showDimension)
            lines.add(new Line(
                    "DIM " + client.world.getRegistryKey().getValue().getPath(),
                    0xFF9FAEC0));

        if (CONFIG.showDayNight)
            lines.add(new Line(dayNight(client), 0xFF7DE3FF));

        if (CONFIG.showDurability) {
            ItemStack held = p.getMainHandStack();
            if (!held.isEmpty() && held.isDamageable()) {
                int pct = InventoryManager.durabilityPercent(held);
                lines.add(new Line(
                        "DURA " + pct + "% // " + held.getName().getString(),
                        pct <= 10 ? 0xFFFF5D5D : 0xFFB7C7D6));
            }
        }

        if (CONFIG.showInventory) {
            int free = InventoryManager.freeSlots(p);
            lines.add(new Line(
                    "INV " + free + " free",
                    free <= 2 ? 0xFFFF6B6B : 0xFFB7C7D6));
        }

        if (CONFIG.showHostiles) {
            int hostile = client.world.getEntitiesByClass(
                    HostileEntity.class,
                    p.getBoundingBox().expand(16),
                    e -> e.isAlive()
            ).size();
            String threat;
            int threatColor;
            if (hostile == 0) {
                threat = "CLEAR";
                threatColor = 0xFF76F7A8;
            } else if (p.getHealth() <= 8f || hostile >= 5) {
                threat = "HIGH // " + hostile;
                threatColor = 0xFFFF5D5D;
            } else if (hostile >= 2) {
                threat = "ELEVATED // " + hostile;
                threatColor = 0xFFFFB45D;
            } else {
                threat = "LOW // 1";
                threatColor = 0xFFFFE27A;
            }
            lines.add(new Line("THREAT " + threat, threatColor));
        }

        if (PLAY.enabled()) {
            lines.add(new Line("PLAY " + PLAY.phase() + " // " + PLAY.status(), 0xFFFFD76A));
        }

        if (CONFIG.showAutomation) {
            String auto = "AUTO " + AUTOMATION.mode() + " // " + AUTOMATION.state();
            if (!AUTOMATION.goalItem().isBlank() && AUTOMATION.goalCount() > 0) {
                int have = InventoryManager.count(p, AUTOMATION.goalItem());
                auto += " // " + have + "/" + AUTOMATION.goalCount();
            }
            lines.add(new Line(auto, AUTOMATION.active() ? 0xFF64F2FF : 0xFF66727D));

            if (CONFIG.showGoalRate && !AUTOMATION.goalItem().isBlank() && AUTOMATION.goalCount() > 0) {
                int have = InventoryManager.count(p, AUTOMATION.goalItem());
                int remaining = Math.max(0, AUTOMATION.goalCount() - have);
                double rate = STATS.perHour(AUTOMATION.goalItem());
                String rateText = rate < 0.1
                        ? "RATE learning…"
                        : String.format(Locale.ROOT, "RATE %.1f/h // ETA %s", rate, STATS.eta(AUTOMATION.goalItem(), remaining));
                lines.add(new Line(rateText, 0xFF9FC6D8));
            }
        }

        if (CONFIG.showVoice)
            lines.add(new Line(
                    "VOICE " + VOICE.status() + " // AI " + AI.status(),
                    AI.ready() ? 0xFF63FFF2 : 0xFF91A0AD));

        int count = Math.min(CONFIG.maxHudLines, lines.size());
        int y = 7, lineH = 11, pad = 4, widest = 0;
        for (int i = 0; i < count; i++) {
            widest = Math.max(widest, client.textRenderer.getWidth(lines.get(i).text));
        }

        int boxW = widest + pad * 2;
        int boxH = count * lineH + pad * 2 + 10;
        int left = CONFIG.hudRight ? ctx.getScaledWindowWidth() - boxW - 7 : 7;
        int right = left + boxW;

        int accent = themeAccent();
        ctx.fill(left, y, right, y + boxH, 0xB5091118);
        ctx.fill(left, y, right, y + 2, accent);
        ctx.drawText(
                client.textRenderer,
                "SURV // " + CONFIG.profile,
                left + pad, y + 5,
                accent,
                true
        );

        int ty = y + 16;
        for (int i = 0; i < count; i++) {
            Line l = lines.get(i);
            ctx.drawText(client.textRenderer, l.text, left + pad, ty, l.color, true);
            ty += lineH;
        }

        if (CONFIG.debugAutomation && AUTOMATION.active()) {
            ctx.drawText(
                    client.textRenderer,
                    AUTOMATION.reason(),
                    left + pad,
                    y + boxH + 3,
                    0xFF9FAEC0,
                    true
            );
        }
    }

    private static int themeAccent() {
        return switch (CONFIG.hudTheme == null ? "CYAN" : CONFIG.hudTheme) {
            case "AMBER" -> 0xFFFFB84D;
            case "GREEN" -> 0xFF66FF9A;
            case "RED" -> 0xFFFF6262;
            case "MONO" -> 0xFFE4E8EC;
            default -> 0xFF55E8E2;
        };
    }

    private record Line(String text, int color) {}
}
