package com.survivalutils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

import java.util.Comparator;
import java.util.Locale;

public final class WarningManager {
    public enum Severity { INFO, CAUTION, DANGER }

    public record Warning(String text, Severity severity, long expiresAt) {}
    private record Candidate(String key, String text, Severity severity, int priority) {}

    private Warning active;
    private String activeKey = "";
    private long lastActionbarAt;
    private String lastActionbarKey = "";
    private int ticks;

    public Warning active() {
        if (active != null && System.currentTimeMillis() > active.expiresAt()) {
            active = null;
            activeKey = "";
        }
        return active;
    }

    public void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            active = null;
            activeKey = "";
            return;
        }

        // Check 5 times per second so fast threats such as falls/creepers are not missed.
        if (++ticks % 4 != 0) return;

        SurvivalConfig cfg = SurvivalUtilsClient.CONFIG;
        var p = client.player;
        var threat = ThreatAnalyzer.analyze(client);
        Candidate best = null;

        if (cfg.isEnabled(Feature.FIRE_ALERT) && p.isOnFire()) {
            best = better(best, c("fire",
                    "WARNING // ON FIRE // REACH WATER OR USE FIRE RESISTANCE",
                    Severity.DANGER, 100));
        }

        if (cfg.isEnabled(Feature.DROWNING_ALERT) && p.getAir() < 80) {
            best = better(best, c("air",
                    "WARNING // AIR CRITICAL // SURFACE NOW",
                    Severity.DANGER, 100));
        }

        if (cfg.isEnabled(Feature.FALL_ALERT)
                && p.fallDistance >= 7f
                && p.getVelocity().y < -0.42) {
            best = better(best, c("fall",
                    "WARNING // DANGEROUS FALL "
                            + String.format(Locale.ROOT, "%.1fm", p.fallDistance)
                            + " // CLUTCH " + SurvivalUtilsClient.AUTO_CLUTCH.status(),
                    Severity.DANGER, 96));
        }

        if (cfg.isEnabled(Feature.LOW_HEALTH_ALERT) && p.getHealth() <= 8f) {
            best = better(best, c("health",
                    "WARNING // LOW HEALTH // DISENGAGE AND HEAL",
                    Severity.DANGER, 94));
        }

        if (cfg.isEnabled(Feature.SPECTATOR_ALERT) && client.getNetworkHandler() != null) {
            String self = p.getGameProfile().name();
            for (var entry : client.getNetworkHandler().getPlayerList()) {
                if (entry.getGameMode() != GameMode.SPECTATOR) continue;
                String name = entry.getProfile().name();
                if (name == null || name.equalsIgnoreCase(self)) continue;

                String extra = "";
                var visible = client.world.getPlayers().stream()
                        .filter(other -> other != p && other.getGameProfile().name().equalsIgnoreCase(name))
                        .findFirst().orElse(null);
                if (visible != null) {
                    extra = " // " + String.format(Locale.ROOT, "%.1fm", Math.sqrt(p.squaredDistanceTo(visible)));
                }
                best = better(best, c("spec:" + name,
                        "WARNING // SPECTATOR " + name + extra + " // DETECTED",
                        Severity.DANGER, 92));
                break;
            }
        }

        if (cfg.isEnabled(Feature.CREEPER_ALERT)) {
            var nearest = client.world.getEntitiesByClass(
                    CreeperEntity.class,
                    p.getBoundingBox().expand(8),
                    e -> e.isAlive()).stream()
                    .min(Comparator.comparingDouble(p::squaredDistanceTo))
                    .orElse(null);
            if (nearest != null) {
                double d = Math.sqrt(p.squaredDistanceTo(nearest));
                best = better(best, c("creeper",
                        "WARNING // CREEPER // " + String.format(Locale.ROOT, "%.1fm", d)
                                + " // CREATE DISTANCE",
                        Severity.DANGER, 90));
            }
        }

        if (cfg.isEnabled(Feature.PLAYER_PROXIMITY_ALERT)) {
            var nearest = client.world.getPlayers().stream()
                    .filter(other -> other != p)
                    .filter(other -> other.squaredDistanceTo(p)
                            <= cfg.playerRadarRadius * cfg.playerRadarRadius)
                    .min(Comparator.comparingDouble(p::squaredDistanceTo))
                    .orElse(null);

            if (nearest != null) {
                boolean spectator = false;
                if (client.getNetworkHandler() != null) {
                    var info = client.getNetworkHandler().getPlayerListEntry(nearest.getUuid());
                    spectator = info != null && info.getGameMode() == GameMode.SPECTATOR;
                }
                if (!spectator) {
                    double distance = Math.sqrt(p.squaredDistanceTo(nearest));
                    String name = nearest.getGameProfile().name();
                    Severity sev = distance <= 8 ? Severity.DANGER : Severity.CAUTION;
                    int priority = distance <= 8 ? 88 : 75;
                    best = better(best, c("player:" + name,
                            "PLAYER NEAR // " + name + " // "
                                    + String.format(Locale.ROOT, "%.1fm", distance),
                            sev, priority));
                }
            }
        }

        if (cfg.isEnabled(Feature.NO_TOTEM_ALERT)
                && threat.level().ordinal() >= ThreatAnalyzer.Level.HIGH.ordinal()
                && InventoryUtil.count(p, "totem_of_undying") <= 0) {
            best = better(best, c("totem",
                    "WARNING // HIGH THREAT + NO TOTEM // DISENGAGE",
                    Severity.DANGER, 84));
        }

        if (cfg.isEnabled(Feature.THREAT_ALERT)
                && threat.level().ordinal() >= ThreatAnalyzer.Level.HIGH.ordinal()) {
            best = better(best, c("threat",
                    "WARNING // THREAT " + threat.level()
                            + " // SCORE " + threat.score()
                            + " // " + threat.recommendation(),
                    threat.level() == ThreatAnalyzer.Level.CRITICAL
                            ? Severity.DANGER : Severity.CAUTION,
                    threat.level() == ThreatAnalyzer.Level.CRITICAL ? 82 : 68));
        }

        if (cfg.isEnabled(Feature.LAVA_ALERT) && lavaNearby(client)) {
            best = better(best, c("lava",
                    "WARNING // LAVA VERY CLOSE // SLOW DOWN",
                    Severity.CAUTION, 64));
        }

        if (cfg.isEnabled(Feature.SKELETON_ALERT)) {
            var nearest = client.world.getEntitiesByClass(
                    SkeletonEntity.class,
                    p.getBoundingBox().expand(12),
                    e -> e.isAlive()).stream()
                    .min(Comparator.comparingDouble(p::squaredDistanceTo))
                    .orElse(null);
            if (nearest != null) {
                double d = Math.sqrt(p.squaredDistanceTo(nearest));
                best = better(best, c("skeleton",
                        "RANGED HOSTILE // SKELETON // "
                                + String.format(Locale.ROOT, "%.1fm", d)
                                + " // USE COVER OR SHIELD",
                        Severity.CAUTION, 58));
            }
        }

        if (cfg.isEnabled(Feature.DURABILITY_ALERT)) {
            var held = p.getMainHandStack();
            if (!held.isEmpty() && held.isDamageable()) {
                int pct = InventoryUtil.durabilityPercent(held);
                if (pct <= 10) {
                    best = better(best, c("durability",
                            "LOW DURABILITY // " + held.getName().getString()
                                    + " // " + pct + "%",
                            Severity.CAUTION, 48));
                }
            }
        }

        if (cfg.isEnabled(Feature.LOW_HUNGER_ALERT)
                && p.getHungerManager().getFoodLevel() <= 6) {
            best = better(best, c("hunger",
                    "LOW FOOD // EAT BEFORE CONTINUING",
                    Severity.CAUTION, 44));
        }

        if (cfg.isEnabled(Feature.INVENTORY_FULL_ALERT)
                && InventoryUtil.freeSlots(p) <= 2) {
            best = better(best, c("inventory",
                    "INVENTORY ALMOST FULL // " + InventoryUtil.freeSlots(p) + " SLOTS",
                    Severity.CAUTION, 40));
        }

        if (cfg.isEnabled(Feature.NIGHT_WARNING)) {
            long t = Math.floorMod(client.world.getTimeOfDay(), 24000L);
            if (t < 13000L) {
                int seconds = (int)Math.round((13000L - t) / 20.0);
                if (seconds <= 60) {
                    best = better(best, c("night",
                            "NIGHT IN " + seconds + "s // PREPARE OR FIND SHELTER",
                            Severity.INFO, 24));
                }
            }
        }

        if (cfg.isEnabled(Feature.LIGHT_WARNING)
                && client.world.getLightLevel(p.getBlockPos()) <= 3) {
            best = better(best, c("light",
                    "LOW LIGHT // HOSTILE SPAWN RISK",
                    Severity.INFO, 12));
        }

        if (best == null) {
            active = null;
            activeKey = "";
            return;
        }

        long now = System.currentTimeMillis();
        // Keep the banner alive while the condition remains true.
        active = new Warning(best.text(), best.severity(), now + 700L);
        activeKey = best.key();

        long repeatMs = switch (best.severity()) {
            case DANGER -> 2500L;
            case CAUTION -> Math.max(4000L, cfg.warningCooldownSeconds * 1000L);
            case INFO -> Math.max(8000L, cfg.warningCooldownSeconds * 1000L);
        };

        boolean changed = !best.key().equals(lastActionbarKey);
        if (cfg.warningActionbar && (changed || now - lastActionbarAt >= repeatMs)) {
            Formatting color = switch (best.severity()) {
                case INFO -> Formatting.YELLOW;
                case CAUTION -> Formatting.GOLD;
                case DANGER -> Formatting.RED;
            };
            p.sendMessage(Text.literal(best.text()).formatted(color, Formatting.BOLD), true);
            lastActionbarAt = now;
            lastActionbarKey = best.key();
            SurvMegaState.log("WARNING", best.text());
        }
    }

    private Candidate c(String key, String text, Severity severity, int priority) {
        return new Candidate(key, text, severity, priority);
    }

    private Candidate better(Candidate a, Candidate b) {
        if (a == null) return b;
        return b.priority() > a.priority() ? b : a;
    }

    private boolean lavaNearby(MinecraftClient client) {
        BlockPos base = client.player.getBlockPos();
        for (int y=-1;y<=1;y++) {
            for (int x=-2;x<=2;x++) {
                for (int z=-2;z<=2;z++) {
                    if (client.world.getFluidState(base.add(x,y,z)).isIn(FluidTags.LAVA)) return true;
                }
            }
        }
        return false;
    }
}
