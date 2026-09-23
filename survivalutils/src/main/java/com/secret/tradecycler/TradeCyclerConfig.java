package com.secret.tradecycler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class TradeCyclerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("tradecycler.json");

    public String enchantment = "minecraft:mending";
    public int minimumLevel = 1;
    public int maxEmeraldPrice = 20;
    public int delayMs = 650;

    public static TradeCyclerConfig load() {
        if (!Files.exists(PATH)) {
            TradeCyclerConfig config = new TradeCyclerConfig();
            config.save();
            return config;
        }

        try {
            TradeCyclerConfig config = GSON.fromJson(Files.readString(PATH, StandardCharsets.UTF_8), TradeCyclerConfig.class);
            if (config == null) config = new TradeCyclerConfig();
            config.sanitize();
            return config;
        } catch (Exception ignored) {
            return new TradeCyclerConfig();
        }
    }

    public void save() {
        sanitize();
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public int delayTicks() {
        return Math.max(2, delayMs / 50);
    }

    public void sanitize() {
        String value = enchantment == null ? "mending" : enchantment.trim().toLowerCase(Locale.ROOT);
        if (value.isBlank()) value = "mending";
        if (!value.contains(":")) value = "minecraft:" + value;
        enchantment = value;
        minimumLevel = Math.max(1, Math.min(5, minimumLevel));
        maxEmeraldPrice = Math.max(1, Math.min(64, maxEmeraldPrice));
        delayMs = Math.max(100, Math.min(5000, delayMs));
    }
}
