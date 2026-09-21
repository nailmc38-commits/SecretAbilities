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
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
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
    private static ThreatAnalyzer.Level lastThreatLevel = ThreatAnalyzer.Level.CLEAR;
    private static long lastThreatNotice;
    private static long lastAiOfflineNotice;
    private static String lastDimension = "";
    private static BlockPos lastAlivePos;
    private static String lastAliveDimension = "";
    private static boolean deathWaypointSaved;
    private static final ArrayDeque<String> COMMAND_HISTORY = new ArrayDeque<>();
    private static final Map<String, Long> SPECTATOR_WARN_COOLDOWN = new HashMap<>();

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

        ThreatAnalyzer.Snapshot threat = ThreatAnalyzer.analyze(client, CONFIG);
        long now = System.currentTimeMillis();

        boolean high = threat.level().ordinal() >= ThreatAnalyzer.Level.HIGH.ordinal();
        boolean increased = threat.level().ordinal() > lastThreatLevel.ordinal();

        if (high && (increased || now - lastThreatNotice > 20_000L)) {
            lastThreatNotice = now;
            String warning = "WARNING // " + threat.shortLine()
                    + " // " + threat.recommendation();
            notice(warning);

            if (CONFIG.threatVoiceWarnings) {
                speakAlert("Warning. Threat level " + threat.level().name().toLowerCase(Locale.ROOT)
                        + ". " + threat.recommendation() + ".");
            }
        }

        if (CONFIG.alertFire && client.player.isOnFire()) {
            notice("WARNING // ON FIRE");
            speakAlert("Warning. You are on fire.");
        }

        if (CONFIG.alertLowAir && client.player.getAir() < 80) {
            notice("WARNING // AIR LOW");
            speakAlert("Warning. Air is critically low.");
        }

        if (CONFIG.alertLowArmor && client.player.getArmor() <= 4 && high) {
            notice("WARNING // VERY LOW ARMOR");
        }

        if (CONFIG.alertNoTotem
                && high
                && InventoryManager.count(client.player, "totem_of_undying") <= 0) {
            notice("WARNING // NO TOTEM");
        }

        lastThreatLevel = threat.level();

        if (CONFIG.spectatorWarnings && client.getNetworkHandler() != null) {
            String self = client.player.getGameProfile().name();

            for (var entry : client.getNetworkHandler().getPlayerList()) {
                if (entry.getGameMode() != GameMode.SPECTATOR) continue;

                String name = entry.getProfile().name();
                if (name == null || name.equalsIgnoreCase(self)) continue;

                long nextAllowed = SPECTATOR_WARN_COOLDOWN.getOrDefault(name, 0L);
                if (now < nextAllowed) continue;

                SPECTATOR_WARN_COOLDOWN.put(name, now + 30_000L);

                String warning = "WARNING // " + name + " is in spectator.";
                notice(warning);
                speakAlert("Warning. " + name + " is in spectator mode.");
            }

            SPECTATOR_WARN_COOLDOWN.entrySet().removeIf(e -> e.getValue() + 120_000L < now);
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

        if (handleDirectVoiceCommand(client, q)) return;

        askAi(q, true);
    }

    private static boolean handleDirectVoiceCommand(MinecraftClient client, String q) {
        if (client.player == null) return false;

        if (q.equals("jump") || q.equals("jump now")) {
            CONTROL.jump();
            TTS.speak("Jumping.", "MINIMAL");
            return true;
        }

        if (q.equals("eat") || q.equals("eat food") || q.equals("eat something")) {
            boolean ok = CONTROL.eat(client);
            TTS.speak(ok ? "Eating." : "I don't see usable food.", "MINIMAL");
            return true;
        }

        if (q.startsWith("move forward") || q.startsWith("walk forward") || q.equals("forward")) {
            CONTROL.move("forward", voiceSeconds(q, 2.0));
            TTS.speak("Moving forward.", "MINIMAL");
            return true;
        }

        if (q.startsWith("move back") || q.startsWith("walk back") || q.equals("back")) {
            CONTROL.move("back", voiceSeconds(q, 2.0));
            TTS.speak("Moving back.", "MINIMAL");
            return true;
        }

        if (q.startsWith("move left") || q.equals("left")) {
            CONTROL.move("left", voiceSeconds(q, 2.0));
            TTS.speak("Moving left.", "MINIMAL");
            return true;
        }

        if (q.startsWith("move right") || q.equals("right")) {
            CONTROL.move("right", voiceSeconds(q, 2.0));
            TTS.speak("Moving right.", "MINIMAL");
            return true;
        }

        if (q.equals("turn left") || q.equals("look left")) {
            CONTROL.turn(client, -90f);
            TTS.speak("Turning left.", "MINIMAL");
            return true;
        }

        if (q.equals("turn right") || q.equals("look right")) {
            CONTROL.turn(client, 90f);
            TTS.speak("Turning right.", "MINIMAL");
            return true;
        }

        if (q.equals("go home") || q.equals("take me home")) {
            boolean ok = AUTOMATION.goToWaypoint(client, "home");
            TTS.speak(ok ? "Going home." : "I don't have a home waypoint yet.", "NORMAL");
            return true;
        }

        if (q.contains("combat loadout") || q.equals("gear up")) {
            InventoryManager.applyLoadout(client, "COMBAT");
            TTS.speak("Combat loadout applied.", "TACTICAL");
            return true;
        }

        if (q.contains("loadout")) {
            String name = q
                    .replace("equip", "")
                    .replace("switch to", "")
                    .replace("use my", "")
                    .replace("use", "")
                    .replace("my", "")
                    .replace("loadout", "")
                    .trim();

            if (!name.isBlank()) {
                InventoryManager.applyLoadout(client, name);
                TTS.speak(name + " loadout applied.", "TACTICAL");
                return true;
            }
        }

        if (q.startsWith("mine ") || q.startsWith("get me ")) {
            String target = q.startsWith("mine ")
                    ? q.substring(5).trim()
                    : q.substring(7).trim();

            if (!target.isBlank()
                    && !target.contains("wood")
                    && !target.contains("log")
                    && target.length() < 40) {
                AUTOMATION.setGoal(target, 64);
                AUTOMATION.start(AutomationManager.Mode.MINING, client);
                TTS.speak("Mining " + target + ".", "NORMAL");
                return true;
            }
        }

        if (q.contains("get wood") || q.contains("get logs") || q.contains("chop trees")) {
            AUTOMATION.setGoal("log", 32);
            AUTOMATION.start(AutomationManager.Mode.TREE_FARM, client);
            TTS.speak("Getting wood.", "NORMAL");
            return true;
        }

        if (q.contains("go to ") || q.contains("navigate to ")) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(-?\\\\d+)\\\\D+(-?\\\\d+)\\\\D+(-?\\\\d+)")
                    .matcher(q);
            if (m.find()) {
                int x = Integer.parseInt(m.group(1));
                int y = Integer.parseInt(m.group(2));
                int z = Integer.parseInt(m.group(3));
                AUTOMATION.navigateTo(client, x, y, z);
                TTS.speak("Navigating.", "NORMAL");
                return true;
            }
        }

        return false;
    }

    private static double voiceSeconds(String q, double fallback) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\\\d+(?:\\\\.\\\\d+)?)")
                .matcher(q);
        if (!m.find()) return fallback;
        try {
            return Math.max(0.25, Math.min(10.0, Double.parseDouble(m.group(1))));
        } catch (Exception e) {
            return fallback;
        }
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
            notice("AI // " + msg + " // direct controls still available");
            long now = System.currentTimeMillis();
            if (spokenRequest && CONFIG.aiSpeakReplies && now - lastAiOfflineNotice > 15_000L) {
                lastAiOfflineNotice = now;
                TTS.speak("My local AI server isn't connected, but direct commands still work.", "NORMAL");
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

        int hostile = client.world.getEntitiesByClass(
                HostileEntity.class,
                p.getBoundingBox().expand(14),
                e -> e.isAlive()
        ).size();

        String held = p.getMainHandStack().isEmpty()
                ? "empty"
                : Registries.ITEM.getId(p.getMainHandStack().getItem()).getPath()
                + " (" + InventoryManager.durabilityPercent(p.getMainHandStack()) + "% durability)";

        String biome = client.world.getBiome(p.getBlockPos())
                .getKey()
                .map(k -> k.getValue().getPath())
                .orElse("unknown");

        String weather = client.world.isThundering()
                ? "thunder"
                : (client.world.isRaining() ? "rain" : "clear");

        ThreatAnalyzer.Snapshot threat = ThreatAnalyzer.analyze(client, CONFIG);

        return """
PLAYER
health=%.1f/%.1f
food=%d/20
armor=%d/20
xp_level=%d
position=%d,%d,%d
dimension=%s
biome=%s
light=%d
weather=%s
held_item=%s
free_inventory_slots=%d
nearby_hostiles=%d
threat_level=%s
threat_score=%d
survival_readiness=%d
threat_reasons=%s
threat_recommendation=%s

HOTBAR
%s

EQUIPMENT
%s

FULL INVENTORY
%s

VISIBLE IMPORTANT BLOCKS
%s

AUTOMATION
mode=%s
state=%s
reason=%s
goal=%s %d
queued_tasks=%d

PLAY
enabled=%s
phase=%s
status=%s
directive=%s

RULES
%s

SAVED LOCATIONS
%s

ROUTES
%s

REMEMBERED STORAGE
known_container_hits_for_diamond=%s

SESSION
%s
""".formatted(
                p.getHealth(), p.getMaxHealth(),
                p.getHungerManager().getFoodLevel(),
                p.getArmor(),
                p.experienceLevel,
                p.getBlockX(), p.getBlockY(), p.getBlockZ(),
                client.world.getRegistryKey().getValue(),
                biome,
                client.world.getLightLevel(p.getBlockPos()),
                weather,
                held,
                InventoryManager.freeSlots(p),
                hostile,
                threat.level(),
                threat.score(),
                threat.readiness(),
                threat.reasons(),
                threat.recommendation(),
                InventoryManager.hotbarSummary(p),
                InventoryManager.equipmentSummary(p),
                InventoryManager.fullSummary(p),
                WorldScanner.nearbySummary(client, 10),
                AUTOMATION.mode(),
                AUTOMATION.state(),
                AUTOMATION.reason(),
                AUTOMATION.goalItem(),
                AUTOMATION.goalCount(),
                AUTOMATION.queuedTasks(),
                PLAY.enabled(),
                PLAY.phase(),
                PLAY.status(),
                PLAY.directiveStatus(),
                RULES.all(),
                MEMORY.waypointDetails(),
                MEMORY.routeNames(),
                MEMORY.containersWith("diamond"),
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
        ThreatAnalyzer.Snapshot threat = ThreatAnalyzer.analyze(client, CONFIG);
        return String.format(
                "HP %.1f | Food %d | Armor %d | XP %d | THREAT %s/%d | READY %d%% | %s | PLAY %s | AI %s/%s | MIC %s",
                client.player.getHealth(),
                client.player.getHungerManager().getFoodLevel(),
                client.player.getArmor(),
                client.player.experienceLevel,
                threat.level(),
                threat.score(),
                threat.readiness(),
                AUTOMATION.mode(),
                PLAY.enabled() ? PLAY.phase() : "OFF",
                AI.status(),
                AI.backendName(),
                VOICE.status()
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
        if (!CONFIG.hudEnabled
                || client.player == null
                || client.world == null
                || client.textRenderer == null) return;

        renderMainHud(ctx, client);
        renderStatsHud(ctx, client);
        renderHelmetHud(ctx, client);
    }

    private static void renderMainHud(DrawContext ctx, MinecraftClient client) {
        List<Line> lines = new ArrayList<>();
        var p = client.player;

        if (CONFIG.showHealth) {
            lines.add(new Line(
                    String.format(Locale.ROOT, "HP %.1f/%.1f", p.getHealth(), p.getMaxHealth()),
                    p.getHealth() <= CONFIG.retreatHealth ? 0xFFFF5D5D : 0xFF7CFFB2));
        }

        if (CONFIG.showHunger)
            lines.add(new Line("FOOD " + p.getHungerManager().getFoodLevel() + "/20", 0xFFFFD36A));

        if (CONFIG.showFoodCount)
            lines.add(new Line("FOOD ITEMS " + InventoryManager.foodCount(p), 0xFFFFC96A));

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

        if (CONFIG.showBiome) {
            String biome = client.world.getBiome(p.getBlockPos())
                    .getKey()
                    .map(k -> k.getValue().getPath())
                    .orElse("unknown");
            lines.add(new Line("BIOME " + biome, 0xFF9FD7B7));
        }

        if (CONFIG.showLight)
            lines.add(new Line("LIGHT " + client.world.getLightLevel(p.getBlockPos()), 0xFFFFE58A));

        if (CONFIG.showDayNight)
            lines.add(new Line(dayNight(client), 0xFF7DE3FF));

        if (CONFIG.showWeather) {
            String weather = client.world.isThundering()
                    ? "THUNDER"
                    : (client.world.isRaining() ? "RAIN" : "CLEAR");
            lines.add(new Line("WEATHER " + weather, 0xFF8EC6FF));
        }

        if (CONFIG.showEffects)
            lines.add(new Line("EFFECTS " + p.getStatusEffects().size(), 0xFFD9A7FF));

        if (CONFIG.showHeldItem) {
            ItemStack held = p.getMainHandStack();
            lines.add(new Line(
                    "HELD " + (held.isEmpty() ? "empty" : held.getName().getString()),
                    0xFFC6D3DB));
        }

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

        if (CONFIG.showTotems)
            lines.add(new Line(
                    "TOTEMS " + InventoryManager.count(p, "totem_of_undying"),
                    0xFFFFD98A));

        if (CONFIG.showArrows)
            lines.add(new Line(
                    "ARROWS " + InventoryManager.count(p, "arrow"),
                    0xFFD8E0E6));

        ThreatAnalyzer.Snapshot threat = ThreatAnalyzer.analyze(client, CONFIG);

        if (CONFIG.showHostiles) {
            lines.add(new Line(threat.shortLine(), threat.color()));

            if (CONFIG.showThreatReasons && !threat.reasons().isEmpty()) {
                lines.add(new Line(threat.reasonLine(), threat.color()));
            }

            if (CONFIG.showReadiness) {
                int readyColor = threat.readiness() >= 75
                        ? 0xFF74F0A6
                        : (threat.readiness() >= 45 ? 0xFFFFC65A : 0xFFFF6666);
                lines.add(new Line(
                        "READINESS " + threat.readiness() + "% // " + threat.recommendation(),
                        readyColor));
            }
        }

        if (CONFIG.showNearbyPlayers) {
            var nearbyPlayers = client.world.getPlayers().stream()
                    .filter(x -> x != p && x.squaredDistanceTo(p) <= 32 * 32)
                    .sorted(java.util.Comparator.comparingDouble(p::squaredDistanceTo))
                    .toList();

            lines.add(new Line("PLAYERS NEAR " + nearbyPlayers.size(), 0xFF9DD0FF));

            int shown = 0;
            for (var other : nearbyPlayers) {
                if (shown++ >= 3) break;

                double dist = Math.sqrt(p.squaredDistanceTo(other));
                boolean spectator = false;

                if (client.getNetworkHandler() != null) {
                    var info = client.getNetworkHandler().getPlayerListEntry(other.getUuid());
                    spectator = info != null && info.getGameMode() == GameMode.SPECTATOR;
                }

                String name = other.getGameProfile().name();
                lines.add(new Line(
                        "  " + name
                                + " " + String.format(Locale.ROOT, "%.1fm", dist)
                                + (spectator ? " [SPEC]" : ""),
                        spectator ? 0xFFFF8C8C : 0xFFB6DFFF));
            }
        }

        if (CONFIG.showFps)
            lines.add(new Line("FPS " + client.getCurrentFps(), 0xFF9AF0D2));

        if (CONFIG.showPing && client.getNetworkHandler() != null) {
            var entry = client.getNetworkHandler().getPlayerListEntry(p.getUuid());
            if (entry != null)
                lines.add(new Line("PING " + entry.getLatency() + "ms", 0xFF9FB4C0));
        }

        if (PLAY.enabled())
            lines.add(new Line("PLAY " + PLAY.phase() + " // " + PLAY.status(), 0xFFFFD76A));

        if (CONFIG.showAutomation) {
            String auto = "AUTO " + AUTOMATION.mode() + " // " + AUTOMATION.state();
            if (!AUTOMATION.goalItem().isBlank() && AUTOMATION.goalCount() > 0) {
                int have = InventoryManager.count(p, AUTOMATION.goalItem());
                auto += " // " + have + "/" + AUTOMATION.goalCount();
            }
            lines.add(new Line(auto, AUTOMATION.active() ? 0xFF64F2FF : 0xFF66727D));

            if (CONFIG.showGoalRate
                    && !AUTOMATION.goalItem().isBlank()
                    && AUTOMATION.goalCount() > 0) {
                int have = InventoryManager.count(p, AUTOMATION.goalItem());
                int remaining = Math.max(0, AUTOMATION.goalCount() - have);
                double rate = STATS.perHour(AUTOMATION.goalItem());
                String rateText = rate < 0.1
                        ? "RATE learning…"
                        : String.format(
                                Locale.ROOT,
                                "RATE %.1f/h // ETA %s",
                                rate,
                                STATS.eta(AUTOMATION.goalItem(), remaining));
                lines.add(new Line(rateText, 0xFF9FC6D8));
            }
        }

        if (CONFIG.showVoice)
            lines.add(new Line(
                    "VOICE " + VOICE.status() + " // AI " + AI.status(),
                    AI.ready() ? 0xFF63FFF2 : 0xFF91A0AD));

        if (CONFIG.showMemory)
            lines.add(new Line(
                    "MEM " + CHAT_MEMORY.noteCount() + " notes // "
                            + MEMORY.waypointNames().size() + " wp",
                    0xFFB7A7FF));

        int count = Math.min(CONFIG.maxHudLines, lines.size());
        renderPanel(
                ctx,
                client,
                CONFIG.hudRight,
                7,
                "SURV // " + CONFIG.profile,
                lines.subList(0, count),
                themeAccent());
    }

    private static void renderStatsHud(DrawContext ctx, MinecraftClient client) {
        if (!CONFIG.statsHudEnabled || client.player == null) return;

        List<Line> stats = new ArrayList<>();
        var p = client.player;

        if (CONFIG.statsShowSessionTime)
            stats.add(new Line(
                    "SESSION " + (STATS.seconds() / 60) + "m " + (STATS.seconds() % 60) + "s",
                    0xFFB9C6D0));

        if (CONFIG.statsShowDistance)
            stats.add(new Line("DIST " + (int)STATS.distance() + "m", 0xFF89E7FF));

        if (CONFIG.statsShowBlocksMined)
            stats.add(new Line("MINED " + STATS.blocksMined(), 0xFFC6E58A));

        if (CONFIG.statsShowMobHits)
            stats.add(new Line("MOB HITS " + STATS.mobsHit(), 0xFFFF9D8D));

        if (CONFIG.statsShowInventoryFree)
            stats.add(new Line("FREE SLOTS " + InventoryManager.freeSlots(p), 0xFFB5C2CC));

        if (CONFIG.statsShowPlayPhase)
            stats.add(new Line(
                    "PLAY " + (PLAY.enabled() ? PLAY.phase() : "OFF"),
                    PLAY.enabled() ? 0xFFFFD76A : 0xFF697782));

        if (CONFIG.statsShowCurrentTask)
            stats.add(new Line(
                    "TASK " + AUTOMATION.mode() + " / " + AUTOMATION.state(),
                    AUTOMATION.active() ? 0xFF64F2FF : 0xFF697782));

        if (CONFIG.statsShowGoalRate
                && !AUTOMATION.goalItem().isBlank()
                && AUTOMATION.goalCount() > 0) {
            int have = InventoryManager.count(p, AUTOMATION.goalItem());
            int remaining = Math.max(0, AUTOMATION.goalCount() - have);
            stats.add(new Line(
                    "GOAL " + have + "/" + AUTOMATION.goalCount()
                            + " // " + STATS.eta(AUTOMATION.goalItem(), remaining),
                    0xFF9FC6D8));
        }

        if (stats.isEmpty()) return;

        renderPanel(
                ctx,
                client,
                false,
                Math.max(92, ctx.getScaledWindowHeight() / 3),
                "SURV // STATS",
                stats,
                0xFF87BFFF);
    }

    private static void renderHelmetHud(DrawContext ctx, MinecraftClient client) {
        if (!CONFIG.helmetHudEnabled || client.player == null) return;

        var p = client.player;
        ItemStack helmet = p.getEquippedStack(EquipmentSlot.HEAD);
        if (helmet.isEmpty()) return;

        List<Line> data = new ArrayList<>();

        if (CONFIG.helmetShowHelmet) {
            String text = "HELM " + helmet.getName().getString();
            if (helmet.isDamageable()) {
                text += " " + InventoryManager.durabilityPercent(helmet) + "%";
            }
            data.add(new Line(text, 0xFF8EEAFF));
        }

        if (CONFIG.helmetShowThreat) {
            ThreatAnalyzer.Snapshot threat = ThreatAnalyzer.analyze(client, CONFIG);
            data.add(new Line(
                    "THREAT " + threat.level() + " // " + threat.score()
                            + " // READY " + threat.readiness() + "%",
                    threat.color()));

            if (CONFIG.showNearestThreat
                    && threat.nearestType() != null
                    && !threat.nearestType().isBlank()) {
                data.add(new Line(
                        "NEAREST " + threat.nearestType()
                                + " " + String.format(Locale.ROOT, "%.1fm", threat.nearestDistance()),
                        threat.color()));
            }
        }

        if (CONFIG.helmetShowCoords)
            data.add(new Line(
                    "NAV " + p.getBlockX() + " " + p.getBlockY() + " " + p.getBlockZ(),
                    0xFFC9E6F2));

        if (CONFIG.helmetShowTask)
            data.add(new Line(
                    PLAY.enabled()
                            ? "PLAY " + PLAY.phase()
                            : "AUTO " + AUTOMATION.mode() + " / " + AUTOMATION.state(),
                    0xFFFFD76A));

        if (CONFIG.helmetShowDurability) {
            ItemStack held = p.getMainHandStack();
            if (!held.isEmpty() && held.isDamageable()) {
                data.add(new Line(
                        "TOOL " + InventoryManager.durabilityPercent(held) + "%",
                        0xFFB7C7D6));
            }
        }

        if (CONFIG.helmetShowVoice)
            data.add(new Line(
                    "LINK " + VOICE.status() + " / " + AI.backendName(),
                    0xFF6CF7E8));

        if (CONFIG.helmetShowTime)
            data.add(new Line(dayNight(client), 0xFF84D7FF));

        int width = 0;
        for (Line line : data) {
            width = Math.max(width, client.textRenderer.getWidth(line.text));
        }

        int pad = 5;
        int lineH = 11;
        int boxW = Math.max(180, width + pad * 2);
        int boxH = data.size() * lineH + 21;
        int left = (ctx.getScaledWindowWidth() - boxW) / 2;
        int top = 7;
        int right = left + boxW;

        int accent = themeAccent();
        ctx.fill(left, top, right, top + boxH, 0x8A050B10);
        ctx.fill(left, top, right, top + 2, accent);
        ctx.fill(left, top, left + 2, top + boxH, 0x554CE8E2);
        ctx.fill(right - 2, top, right, top + boxH, 0x554CE8E2);

        ctx.drawCenteredTextWithShadow(
                client.textRenderer,
                "HELM // SURV LINK",
                (left + right) / 2,
                top + 5,
                accent);

        int y = top + 17;
        for (Line line : data) {
            ctx.drawCenteredTextWithShadow(
                    client.textRenderer,
                    line.text,
                    (left + right) / 2,
                    y,
                    line.color);
            y += lineH;
        }
    }

    private static int nearbyHostiles(MinecraftClient client, double radius) {
        if (client.player == null || client.world == null) return 0;
        return client.world.getEntitiesByClass(
                HostileEntity.class,
                client.player.getBoundingBox().expand(radius),
                e -> e.isAlive()).size();
    }

    private static void renderPanel(
            DrawContext ctx,
            MinecraftClient client,
            boolean rightSide,
            int y,
            String title,
            List<Line> lines,
            int accent
    ) {
        if (lines == null || lines.isEmpty()) return;

        int lineH = 11;
        int pad = 4;
        int widest = client.textRenderer.getWidth(title);

        for (Line l : lines) {
            widest = Math.max(widest, client.textRenderer.getWidth(l.text));
        }

        int boxW = widest + pad * 2;
        int boxH = lines.size() * lineH + pad * 2 + 10;
        int left = rightSide
                ? ctx.getScaledWindowWidth() - boxW - 7
                : 7;
        int right = left + boxW;

        ctx.fill(left, y, right, y + boxH, 0xB5091118);
        ctx.fill(left, y, right, y + 2, accent);
        ctx.drawText(
                client.textRenderer,
                title,
                left + pad,
                y + 5,
                accent,
                true);

        int ty = y + 16;
        for (Line l : lines) {
            ctx.drawText(
                    client.textRenderer,
                    l.text,
                    left + pad,
                    ty,
                    l.color,
                    true);
            ty += lineH;
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
