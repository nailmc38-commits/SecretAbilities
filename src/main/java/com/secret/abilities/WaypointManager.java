package com.secret.abilities;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class WaypointManager {
    public record Waypoint(String name, String dimension, int x, int y, int z) {}

    private static final List<Waypoint> WAYPOINTS = new ArrayList<>();
    private static final Path FILE = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("secretabilities-waypoints.txt");

    private WaypointManager() {}

    public static void load() {
        WAYPOINTS.clear();

        if (!Files.exists(FILE)) {
            return;
        }

        try {
            for (String line : Files.readAllLines(FILE, StandardCharsets.UTF_8)) {
                String[] parts = line.split("\\|", 5);
                if (parts.length != 5) {
                    continue;
                }

                try {
                    WAYPOINTS.add(new Waypoint(
                            parts[0],
                            parts[1],
                            Integer.parseInt(parts[2]),
                            Integer.parseInt(parts[3]),
                            Integer.parseInt(parts[4])
                    ));
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
    }

    public static Waypoint addCurrent(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return null;
        }

        BlockPos pos = client.player.getBlockPos();
        String dimension = client.world.getRegistryKey().getValue().toString();
        Waypoint waypoint = new Waypoint(
                "Waypoint " + (WAYPOINTS.size() + 1),
                dimension,
                pos.getX(),
                pos.getY(),
                pos.getZ()
        );

        WAYPOINTS.add(waypoint);
        save();
        return waypoint;
    }

    public static void clear() {
        WAYPOINTS.clear();
        save();
    }

    public static List<Waypoint> getAll() {
        return Collections.unmodifiableList(WAYPOINTS);
    }

    public static int count() {
        return WAYPOINTS.size();
    }

    private static void save() {
        List<String> lines = new ArrayList<>();

        for (Waypoint waypoint : WAYPOINTS) {
            lines.add(
                    waypoint.name() + "|" +
                    waypoint.dimension() + "|" +
                    waypoint.x() + "|" +
                    waypoint.y() + "|" +
                    waypoint.z()
            );
        }

        try {
            Files.createDirectories(FILE.getParent());
            Files.write(FILE, lines, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }
}
