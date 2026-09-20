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
    public boolean requireWakeWord = true;
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
    public boolean compactHud = true;
    public boolean hudRight = false;
    public int maxHudLines = 10;
    public boolean smartAlerts = true;
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

    public static SurvConfig load() {
        try {
            if (Files.exists(FILE)) {
                SurvConfig cfg = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), SurvConfig.class);
                if (cfg != null) return cfg;
            }
        } catch (Exception ignored) {}
        SurvConfig cfg = new SurvConfig();
        cfg.save();
        return cfg;
    }

    public void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
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
