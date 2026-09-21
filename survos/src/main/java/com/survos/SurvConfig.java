package com.survos;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SurvConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("surv-os.json");

    public boolean hudEnabled = true;
    public boolean voiceEnabled = true;
    public boolean voiceAutoStart = true;
    public boolean requireWakeWord = false;
    public String wakeWord = "surv";
    public String microphone = "System Default";
    public double voiceConfidence = 0.43;
    public boolean showHealth = true;
    public boolean showHunger = true;
    public boolean showArmor = true;
    public boolean showXp = true;
    public boolean showCoords = true;
    public boolean showDimension = true;
    public boolean showDayNight = true;
    public boolean showDurability = true;
    public boolean showInventory = true;
    public boolean showHostiles = true;
    public boolean showAutomation = true;
    public boolean showVoice = true;
    public boolean showBiome = true;
    public boolean showLight = true;
    public boolean showWeather = true;
    public boolean showEffects = true;
    public boolean showTotems = true;
    public boolean showArrows = true;
    public boolean showFoodCount = true;
    public boolean showHeldItem = true;
    public boolean showFps = true;
    public boolean showPing = true;
    public boolean showNearbyPlayers = false;
    public boolean showMemory = false;
    public boolean compactHud = true;
    public boolean hudRight = true;
    public int maxHudLines = 18;

    // Secondary stats HUD.
    public boolean statsHudEnabled = true;
    public boolean statsShowSessionTime = true;
    public boolean statsShowDistance = true;
    public boolean statsShowBlocksMined = true;
    public boolean statsShowMobHits = true;
    public boolean statsShowCurrentTask = true;
    public boolean statsShowGoalRate = true;
    public boolean statsShowInventoryFree = true;
    public boolean statsShowPlayPhase = true;

    // Helmet HUD appears only while a helmet is equipped.
    public boolean helmetHudEnabled = true;
    public boolean helmetShowHelmet = true;
    public boolean helmetShowThreat = true;
    public boolean helmetShowCoords = true;
    public boolean helmetShowTask = true;
    public boolean helmetShowDurability = true;
    public boolean helmetShowVoice = true;
    public boolean helmetShowTime = true;
    public boolean smartAlerts = true;
    public boolean spectatorWarnings = true;
    public boolean autoEatSafety = true;
    public boolean lowHealthSafety = true;
    public float retreatHealth = 8.0f;
    public boolean avoidCreepers = true;
    public boolean avoidEndermen = true;
    public boolean collectLoot = true;
    public double automationRange = 14.0;
    public double maxDistanceFromStart = 64.0;
    public int durabilityStopPercent = 5;
    public String profile = "SURVIVAL";
    public boolean debugAutomation = false;
    public boolean shortVoiceReplies = true;
    public boolean aiEnabled = true;
    public boolean aiAutoStart = true;
    public boolean aiSpeakReplies = true;
    public boolean ttsEnabled = true;
    public String voiceStyle = "CINEMATIC";
    public boolean autoDimensionProfiles = true;
    public boolean autoHotbar = false;
    public boolean[] autoRefillSlots = new boolean[] {
            false,false,false,false,false,false,false,false,false
    };
    public String[] autoRefillItems = new String[] {
            "","","","","","","","",""
    };
    public boolean autoDeathWaypoint = true;
    public boolean showGoalRate = true;
    public String hudTheme = "CYAN";
    public boolean memoryEnabled = true;
    public boolean playSafety = true;
    public boolean navigatorBreakObstacles = true;

    // Combat hotbar defaults. Slot numbers are 1-9; values are item-id keywords.
    public String combatSlot1 = "sword";
    public String combatSlot2 = "ender_pearl";
    public String combatSlot3 = "golden_apple";
    public String combatSlot4 = "obsidian";
    public String combatSlot5 = "end_crystal";
    public String combatSlot6 = "respawn_anchor";
    public String combatSlot6Fallback = "totem_of_undying";
    public String combatSlot7 = "glowstone";
    public String combatSlot7Fallback = "totem_of_undying";
    public String combatSlot8 = "totem_of_undying";
    public String combatSlot9 = "shield";

    public static SurvConfig load() {
        try {
            if (Files.exists(FILE)) {
                SurvConfig cfg = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), SurvConfig.class);
                if (cfg != null) {
                    cfg.sanitize();
                    return cfg;
                }
            }
        } catch (Exception ignored) {}
        SurvConfig cfg = new SurvConfig();
        cfg.save();
        return cfg;
    }

    public void sanitize() {
        if (autoRefillSlots == null || autoRefillSlots.length != 9) {
            boolean[] old = autoRefillSlots;
            autoRefillSlots = new boolean[9];
            if (old != null) {
                System.arraycopy(old, 0, autoRefillSlots, 0, Math.min(old.length, 9));
            }
        }

        if (autoRefillItems == null || autoRefillItems.length != 9) {
            String[] old = autoRefillItems;
            autoRefillItems = new String[9];
            java.util.Arrays.fill(autoRefillItems, "");
            if (old != null) {
                System.arraycopy(old, 0, autoRefillItems, 0, Math.min(old.length, 9));
            }
        }

        for (int i = 0; i < 9; i++) {
            if (autoRefillItems[i] == null) autoRefillItems[i] = "";
        }
    }

    public void save() {
        sanitize();
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }

    public boolean backup() {
        try {
            Path backup = FILE.resolveSibling("surv-os.backup.json");
            save();
            Files.copy(FILE, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static SurvConfig restoreBackup() {
        try {
            Path backup = FILE.resolveSibling("surv-os.backup.json");
            if (!Files.exists(backup)) return null;
            Files.copy(backup, FILE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return load();
        } catch (Exception e) {
            return null;
        }
    }

    public void applyProfile(String name) {
        profile = name == null ? "SURVIVAL" : name.toUpperCase();
        switch (profile) {
            case "MINING" -> {
                showCoords = true; showDurability = true; showInventory = true;
                showHostiles = true; maxHudLines = 11; compactHud = true;
            }
            case "COMBAT", "GRINDING" -> {
                showHealth = true; showArmor = true; showHunger = true;
                showHostiles = true; showDurability = true; showAutomation = true;
                maxHudLines = 12;
            }
            case "BASE" -> {
                showCoords = false; showHostiles = false; showInventory = true;
                showAutomation = true; maxHudLines = 8;
            }
            case "NETHER" -> {
                showCoords = true; showHealth = true; showArmor = true;
                showHunger = true; showDurability = true; showHostiles = true;
                maxHudLines = 12;
            }
            case "BUILDING" -> {
                showCoords = true; showInventory = true; showDurability = true;
                showHostiles = false; maxHudLines = 8;
            }
            default -> {
                profile = "SURVIVAL";
                showHealth = true; showHunger = true; showArmor = true;
                showCoords = true; showDayNight = true; showDurability = true;
                showInventory = true; showHostiles = true; maxHudLines = 10;
            }
        }
        save();
    }
}
