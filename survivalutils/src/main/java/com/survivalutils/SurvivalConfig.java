package com.survivalutils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;

public final class SurvivalConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("survival-utils-v2.json");

    private EnumMap<Feature, Boolean> enabled = new EnumMap<>(Feature.class);

    public boolean hudRight = true;
    public int maxMainLines = 22;
    public int playerRadarRadius = 32;
    public double threatRadius = 18.0;
    public int warningCooldownSeconds = 7;
    public boolean warningBanner = true;
    public boolean warningActionbar = true;
    public double clutchMinFallDistance = 3.0;
    public int clutchTriggerBlocks = 4;

    public SurvivalConfig() {
        resetDefaults();
    }

    public boolean isEnabled(Feature feature) {
        return enabled.getOrDefault(feature, feature.defaultEnabled);
    }

    public void toggle(Feature feature) {
        set(feature, !isEnabled(feature));
    }

    public void set(Feature feature, boolean value) {
        enabled.put(feature, value);
        save();
    }

    public void setCategory(Feature.Category category, boolean value) {
        for (Feature feature : Feature.values()) {
            if (feature.category == category) enabled.put(feature, value);
        }
        save();
    }

    public int enabledCount(Feature.Category category) {
        int count = 0;
        for (Feature feature : Feature.values()) {
            if (feature.category == category && isEnabled(feature)) count++;
        }
        return count;
    }

    public void resetDefaults() {
        enabled.clear();
        for (Feature feature : Feature.values()) {
            enabled.put(feature, feature.defaultEnabled);
        }
    }

    public void load() {
        try {
            if (!Files.exists(PATH)) {
                save();
                return;
            }

            SurvivalConfig loaded = GSON.fromJson(
                    Files.readString(PATH, StandardCharsets.UTF_8),
                    SurvivalConfig.class);

            if (loaded == null) return;

            this.enabled = loaded.enabled == null
                    ? new EnumMap<>(Feature.class)
                    : loaded.enabled;
            this.hudRight = loaded.hudRight;
            this.maxMainLines = loaded.maxMainLines <= 0 ? 22 : loaded.maxMainLines;
            this.playerRadarRadius = Math.max(8, loaded.playerRadarRadius);
            this.threatRadius = Math.max(8.0, loaded.threatRadius);
            this.warningCooldownSeconds = Math.max(2, loaded.warningCooldownSeconds);
            this.warningBanner = loaded.warningBanner;
            this.warningActionbar = loaded.warningActionbar;
            this.clutchMinFallDistance = loaded.clutchMinFallDistance <= 0.0 ? 3.0 : loaded.clutchMinFallDistance;
            this.clutchTriggerBlocks = Math.max(2, Math.min(4, loaded.clutchTriggerBlocks <= 0 ? 4 : loaded.clutchTriggerBlocks));

            for (Feature feature : Feature.values()) {
                enabled.putIfAbsent(feature, feature.defaultEnabled);
            }
        } catch (Exception ignored) {
            resetDefaults();
        }
    }

    public void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }
}
