package com.secret.abilities;

public final class RuntimeSettings {
    private RuntimeSettings() {}

    public static final String[] MINE_TARGETS = {
            "diamond_ore", "deepslate_diamond_ore", "ancient_debris",
            "iron_ore", "deepslate_iron_ore", "gold_ore", "deepslate_gold_ore",
            "emerald_ore", "deepslate_emerald_ore", "redstone_ore",
            "deepslate_redstone_ore", "lapis_ore", "deepslate_lapis_ore",
            "coal_ore", "deepslate_coal_ore", "copper_ore", "deepslate_copper_ore",
            "nether_quartz_ore", "obsidian", "stone", "deepslate"
    };

    public static int mineTargetIndex = 0;
    public static double speedMultiplier = 1.10;
    public static float flightSpeed = 0.08f;

    public static String mineTarget() {
        return MINE_TARGETS[mineTargetIndex];
    }

    public static void nextMineTarget() {
        mineTargetIndex = (mineTargetIndex + 1) % MINE_TARGETS.length;
        if (ModuleRegistry.isEnabled("auto_mine")) VoidClientRuntime.restartAutoMine();
    }

    public static void nextSpeed() {
        double[] values = {1.05, 1.10, 1.15, 1.20, 1.30};
        int index = 0;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < values.length; i++) {
            double distance = Math.abs(values[i] - speedMultiplier);
            if (distance < best) {
                best = distance;
                index = i;
            }
        }
        speedMultiplier = values[(index + 1) % values.length];
    }

    public static void nextFlightSpeed() {
        float[] values = {0.05f, 0.08f, 0.12f, 0.18f, 0.25f};
        int index = 0;
        float best = Float.MAX_VALUE;
        for (int i = 0; i < values.length; i++) {
            float distance = Math.abs(values[i] - flightSpeed);
            if (distance < best) {
                best = distance;
                index = i;
            }
        }
        flightSpeed = values[(index + 1) % values.length];
    }
}
