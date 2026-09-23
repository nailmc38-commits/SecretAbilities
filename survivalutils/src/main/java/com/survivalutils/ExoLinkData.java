package com.survivalutils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ExoLinkData {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("exo-link");
    private static final Path CONFIG = ROOT.resolve("config.json");

    public static final Settings SETTINGS = new Settings();
    private static boolean loaded;

    private ExoLinkData() {}

    public static final class Settings {
        public boolean exoHud = true;
        public boolean powerArmorFrame = true;
        public boolean visorCracks = true;
        public boolean identify = true;
        public boolean advisor = true;
        public boolean escapeVector = true;
        public boolean threatMemory = true;
        public boolean damageDirection = true;
        public boolean emergencyMode = true;
        public boolean pack = true;
        public boolean echo = true;
        public boolean satellite = true;
        public boolean companion = true;
        public boolean exoScript = true;
        public boolean sounds = true;
        public boolean bootSound = true;
        public boolean warningSound = true;
        public boolean criticalSound = true;
        public boolean targetSound = true;
        public boolean damageSound = true;
        public boolean repairSound = true;
        public boolean satelliteSound = true;
        public boolean toolSound = true;
        public boolean hudAnimations = true;
        public boolean perServerProfiles = true;
        public boolean autoEmergencyLayout = true;
        public double hudScale = 1.0;
        public int dataRetentionDays = 30;
        public final LinkedHashMap<String, Boolean> warnings = defaultWarnings();

        private static LinkedHashMap<String, Boolean> defaultWarnings() {
            LinkedHashMap<String, Boolean> m = new LinkedHashMap<>();
            String[] keys = {
                    "fire","drowning","dangerous_fall","low_health","critical_health",
                    "lava","creeper","skeleton","hostile_close","multiple_hostiles",
                    "hostile_behind","player_near","player_very_close","player_behind",
                    "multiple_players","spectator","known_hostile","no_totem",
                    "armor_critical","helmet_critical","chest_critical","legs_critical","boots_critical",
                    "held_item_critical","shield_critical","elytra_critical",
                    "low_hunger","inventory_full","night","low_light",
                    "low_rockets_flying","projectile_near","sudden_damage","surrounded",
                    "combat_undergeared","losing_advantage","mob_targeting_you",
                    "explosion_near","ping_spike","water_bucket_missing_fall",
                    "nether_lava","escape_blocked","power_core_critical"
            };
            for (String key : keys) m.put(key, true);
            return m;
        }
    }

    public static void load() {
        if (loaded) return;
        loaded = true;
        try {
            Files.createDirectories(ROOT);
            if (Files.exists(CONFIG)) {
                Settings saved = GSON.fromJson(Files.readString(CONFIG, StandardCharsets.UTF_8), Settings.class);
                if (saved != null) copy(saved);
            }
            for (Map.Entry<String, Boolean> e : Settings.defaultWarnings().entrySet()) {
                SETTINGS.warnings.putIfAbsent(e.getKey(), e.getValue());
            }
            save();
        } catch (Exception ignored) {}
    }

    private static void copy(Settings s) {
        SETTINGS.exoHud=s.exoHud; SETTINGS.powerArmorFrame=s.powerArmorFrame; SETTINGS.visorCracks=s.visorCracks;
        SETTINGS.identify=s.identify; SETTINGS.advisor=s.advisor; SETTINGS.escapeVector=s.escapeVector;
        SETTINGS.threatMemory=s.threatMemory; SETTINGS.damageDirection=s.damageDirection; SETTINGS.emergencyMode=s.emergencyMode;
        SETTINGS.pack=s.pack; SETTINGS.echo=s.echo; SETTINGS.satellite=s.satellite; SETTINGS.companion=s.companion;
        SETTINGS.exoScript=s.exoScript; SETTINGS.sounds=s.sounds; SETTINGS.bootSound=s.bootSound;
        SETTINGS.warningSound=s.warningSound; SETTINGS.criticalSound=s.criticalSound; SETTINGS.targetSound=s.targetSound;
        SETTINGS.damageSound=s.damageSound; SETTINGS.repairSound=s.repairSound; SETTINGS.satelliteSound=s.satelliteSound;
        SETTINGS.toolSound=s.toolSound; SETTINGS.hudAnimations=s.hudAnimations; SETTINGS.perServerProfiles=s.perServerProfiles;
        SETTINGS.autoEmergencyLayout=s.autoEmergencyLayout; SETTINGS.hudScale=Math.max(0.65, Math.min(1.35, s.hudScale));
        SETTINGS.dataRetentionDays=Math.max(1, Math.min(365, s.dataRetentionDays));
        if (s.warnings != null) SETTINGS.warnings.putAll(s.warnings);
    }

    public static Settings snapshot() {
        load();
        return GSON.fromJson(GSON.toJson(SETTINGS),Settings.class);
    }

    public static void applyProfile(Settings profile) {
        if(profile==null)return;
        load();
        boolean keepProfiles=SETTINGS.perServerProfiles;
        copy(profile);
        SETTINGS.perServerProfiles=keepProfiles;
        for (Map.Entry<String, Boolean> e : Settings.defaultWarnings().entrySet()) {
            SETTINGS.warnings.putIfAbsent(e.getKey(), e.getValue());
        }
        save();
    }

    public static boolean warning(String key) {
        load();
        return SETTINGS.warnings.getOrDefault(key, true);
    }

    public static void toggleWarning(String key) {
        load();
        SETTINGS.warnings.put(key, !warning(key));
        save();
    }

    public static Path root() { load(); return ROOT; }

    public static void save() {
        try {
            Files.createDirectories(ROOT);
            Files.writeString(CONFIG, GSON.toJson(SETTINGS), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }
}
