package com.survos;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.registry.Registries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class ThreatAnalyzer {
    public enum Level {
        CLEAR, GUARDED, ELEVATED, HIGH, CRITICAL
    }

    public record Snapshot(
            Level level,
            int score,
            int readiness,
            int hostileCount,
            double nearestDistance,
            String nearestType,
            List<String> reasons,
            String recommendation
    ) {
        public int color() {
            return switch (level) {
                case CLEAR -> 0xFF72F0A1;
                case GUARDED -> 0xFFE7E879;
                case ELEVATED -> 0xFFFFC15A;
                case HIGH -> 0xFFFF8A5B;
                case CRITICAL -> 0xFFFF5656;
            };
        }

        public String shortLine() {
            StringBuilder out = new StringBuilder("THREAT ")
                    .append(level)
                    .append(" // ")
                    .append(score);

            if (hostileCount > 0) {
                out.append(" // ").append(hostileCount).append(" mobs");
            }

            if (nearestType != null && !nearestType.isBlank()) {
                out.append(" // ")
                        .append(nearestType)
                        .append(" ")
                        .append(String.format(Locale.ROOT, "%.1fm", nearestDistance));
            }
            return out.toString();
        }

        public String reasonLine() {
            if (reasons == null || reasons.isEmpty()) return "RISK none";
            return "RISK " + String.join(" + ", reasons.subList(0, Math.min(3, reasons.size())));
        }
    }

    private ThreatAnalyzer() {}

    public static Snapshot analyze(MinecraftClient client, SurvConfig cfg) {
        if (client == null || client.player == null || client.world == null) {
            return new Snapshot(Level.CLEAR, 0, 100, 0, -1, "", List.of(), "No world");
        }

        var p = client.player;
        double radius = Math.max(8.0, Math.min(32.0, cfg.threatRadius));

        List<HostileEntity> mobs = client.world.getEntitiesByClass(
                HostileEntity.class,
                p.getBoundingBox().expand(radius),
                e -> e.isAlive());

        mobs.sort(Comparator.comparingDouble(p::squaredDistanceTo));

        int score = 0;
        List<String> reasons = new ArrayList<>();

        float hp = p.getHealth();
        if (hp <= 6f) {
            score += 8;
            reasons.add("very low HP");
        } else if (hp <= 10f) {
            score += 5;
            reasons.add("low HP");
        } else if (hp <= 14f) {
            score += 2;
        }

        int armor = p.getArmor();
        if (armor <= 4) {
            score += 4;
            reasons.add("very low armor");
        } else if (armor <= 8) {
            score += 2;
            reasons.add("low armor");
        }

        int food = p.getHungerManager().getFoodLevel();
        if (food <= 4) {
            score += 3;
            reasons.add("starving");
        } else if (food <= 7) {
            score += 1;
            reasons.add("low food");
        }

        if (p.isOnFire()) {
            score += 7;
            reasons.add("on fire");
        }

        int air = p.getAir();
        if (air < 60) {
            score += 7;
            reasons.add("drowning");
        } else if (air < 140) {
            score += 3;
            reasons.add("low air");
        }

        int hostileCount = mobs.size();
        if (hostileCount > 0) score += Math.min(8, hostileCount);

        double nearestDistance = -1.0;
        String nearestType = "";

        if (!mobs.isEmpty()) {
            HostileEntity nearest = mobs.get(0);
            nearestDistance = Math.sqrt(p.squaredDistanceTo(nearest));
            nearestType = Registries.ENTITY_TYPE.getId(nearest.getType()).getPath();

            if (nearestDistance < 3.0) {
                score += 5;
                reasons.add("mob in melee range");
            } else if (nearestDistance < 6.0) {
                score += 3;
                reasons.add("mob very close");
            } else if (nearestDistance < 10.0) {
                score += 1;
            }
        }

        for (HostileEntity mob : mobs) {
            String id = Registries.ENTITY_TYPE.getId(mob.getType()).getPath();
            double distance = Math.sqrt(p.squaredDistanceTo(mob));

            if (id.contains("warden")) {
                score += 12;
                reasons.add("warden");
            } else if (id.equals("wither")) {
                score += 10;
                reasons.add("wither");
            } else if (id.contains("ravager")) {
                score += 6;
                reasons.add("ravager");
            } else if (id.contains("creeper") && distance < 8.0) {
                score += 5;
                reasons.add("creeper close");
            } else if (id.contains("piglin_brute")) {
                score += 5;
                reasons.add("piglin brute");
            } else if (id.contains("blaze") || id.contains("ghast")) {
                score += 3;
            } else if (id.contains("witch")) {
                score += 3;
            } else if (id.contains("skeleton") || id.contains("shulker")) {
                score += 2;
            }
        }

        int totems = InventoryManager.count(p, "totem_of_undying");
        if (totems == 0 && score >= 8) {
            score += 2;
            reasons.add("no totem");
        }

        Level level;
        if (score >= 18) level = Level.CRITICAL;
        else if (score >= 12) level = Level.HIGH;
        else if (score >= 7) level = Level.ELEVATED;
        else if (score >= 3) level = Level.GUARDED;
        else level = Level.CLEAR;

        int readiness = readiness(client);

        String recommendation = switch (level) {
            case CRITICAL -> "I recommend disengaging immediately. Heal, shield, or create distance.";
            case HIGH -> "I recommend disengaging if you cannot control the fight. Avoid getting surrounded.";
            case ELEVATED -> "I recommend staying alert and keeping an escape route.";
            case GUARDED -> "I recommend monitoring nearby threats.";
            case CLEAR -> "Area looks stable.";
        };

        return new Snapshot(
                level,
                score,
                readiness,
                hostileCount,
                nearestDistance,
                nearestType,
                List.copyOf(reasons),
                recommendation
        );
    }

    public static int readiness(MinecraftClient client) {
        if (client == null || client.player == null) return 0;

        var p = client.player;
        int score = 100;

        score -= Math.round(
                (1f - Math.min(1f, p.getHealth() / Math.max(1f, p.getMaxHealth()))) * 35f);

        score -= Math.max(0, 20 - p.getArmor());
        score -= Math.max(0, 20 - p.getHungerManager().getFoodLevel());

        if (InventoryManager.foodCount(p) == 0) score -= 12;
        if (InventoryManager.count(p, "totem_of_undying") == 0) score -= 8;
        if (InventoryManager.count(p, "shield") == 0) score -= 5;

        var held = p.getMainHandStack();
        if (!held.isEmpty() && held.isDamageable()) {
            int dura = InventoryManager.durabilityPercent(held);
            if (dura <= 5) score -= 15;
            else if (dura <= 15) score -= 7;
        }

        return Math.max(0, Math.min(100, score));
    }
}
