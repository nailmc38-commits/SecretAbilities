package com.survivalutils;

import net.minecraft.client.MinecraftClient;

public final class SurvivalStats {
    private long sessionStart = System.currentTimeMillis();
    private double journeyDistance;
    private double lastX;
    private double lastZ;
    private boolean hadPlayer;
    private double speedBlocksPerSecond;

    public void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            hadPlayer = false;
            speedBlocksPerSecond = 0.0;
            return;
        }

        double x = client.player.getX();
        double z = client.player.getZ();

        if (hadPlayer) {
            double dx = x - lastX;
            double dz = z - lastZ;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d < 16.0) journeyDistance += d;
        }

        speedBlocksPerSecond = client.player.getVelocity().horizontalLength() * 20.0;
        lastX = x;
        lastZ = z;
        hadPlayer = true;
    }

    public long sessionSeconds() {
        return Math.max(0L, (System.currentTimeMillis() - sessionStart) / 1000L);
    }

    public double journeyDistance() {
        return journeyDistance;
    }

    public double speedBlocksPerSecond() {
        return speedBlocksPerSecond;
    }

    public void reset() {
        sessionStart = System.currentTimeMillis();
        journeyDistance = 0.0;
        hadPlayer = false;
        speedBlocksPerSecond = 0.0;
    }
}
