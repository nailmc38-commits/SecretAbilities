package com.survivalutils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;

import java.util.HashMap;
import java.util.Map;

public final class WarningManager {
    public enum Severity { INFO, CAUTION, DANGER }

    public record Warning(String text, Severity severity, long expiresAt) {}

    private final Map<String, Long> cooldowns = new HashMap<>();
    private Warning active;
    private ThreatAnalyzer.Level lastThreat = ThreatAnalyzer.Level.CLEAR;
    private int ticks;

    public Warning active() {
        if (active != null && System.currentTimeMillis() > active.expiresAt()) active = null;
        return active;
    }

    public void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            active = null;
            return;
        }

        if (++ticks % 20 != 0) return;

        SurvivalConfig cfg = SurvivalUtilsClient.CONFIG;
        var p = client.player;

        ThreatAnalyzer.Snapshot threat = ThreatAnalyzer.analyze(client);

        if (cfg.isEnabled(Feature.FIRE_ALERT) && p.isOnFire()) {
            push(client, "fire", "WARNING // ON FIRE // I recommend reaching water or using fire resistance.", Severity.DANGER);
            return;
        }

        if (cfg.isEnabled(Feature.DROWNING_ALERT) && p.getAir() < 80) {
            push(client, "air", "WARNING // AIR CRITICAL // I recommend surfacing immediately.", Severity.DANGER);
            return;
        }

        if (cfg.isEnabled(Feature.FALL_ALERT)
                && p.fallDistance >= 8f
                && p.getVelocity().y < -0.45) {
            String clutch = SurvivalUtilsClient.AUTO_CLUTCH.status();
            push(client, "fall",
                    "WARNING // DANGEROUS FALL "
                            + String.format(java.util.Locale.ROOT, "%.1fm", p.fallDistance)
                            + " // CLUTCH " + clutch,
                    Severity.DANGER);
            return;
        }

        if (cfg.isEnabled(Feature.LAVA_ALERT) && lavaNearby(client)) {
            push(client, "lava",
                    "WARNING // LAVA NEARBY // I recommend slowing down and keeping blocks or water ready.",
                    Severity.CAUTION);
            return;
        }

        if (cfg.isEnabled(Feature.LOW_HEALTH_ALERT) && p.getHealth() <= 8f) {
            push(client, "health", "WARNING // LOW HEALTH // I recommend disengaging and healing.", Severity.DANGER);
            return;
        }

        if (cfg.isEnabled(Feature.DURABILITY_ALERT)) {
            var held = p.getMainHandStack();
            if (!held.isEmpty() && held.isDamageable()) {
                int pct = InventoryUtil.durabilityPercent(held);
                if (pct <= 10) {
                    push(client, "durability",
                            "WARNING // " + held.getName().getString() + " at " + pct + "% // I recommend repairing or switching tools.",
                            Severity.CAUTION);
                    return;
                }
            }
        }

        if (cfg.isEnabled(Feature.INVENTORY_FULL_ALERT) && InventoryUtil.freeSlots(p) <= 2) {
            push(client, "inventory", "WARNING // INVENTORY ALMOST FULL // I recommend storing or dropping low-value items.", Severity.CAUTION);
            return;
        }

        if (cfg.isEnabled(Feature.LOW_HUNGER_ALERT) && p.getHungerManager().getFoodLevel() <= 6) {
            push(client, "hunger", "WARNING // LOW FOOD // I recommend eating before continuing.", Severity.CAUTION);
            return;
        }

        if (cfg.isEnabled(Feature.CREEPER_ALERT)) {
            int creepers = client.world.getEntitiesByClass(
                    CreeperEntity.class,
                    p.getBoundingBox().expand(8),
                    e -> e.isAlive()).size();

            if (creepers > 0) {
                push(client, "creeper", "WARNING // CREEPER CLOSE // I recommend creating distance.", Severity.DANGER);
                return;
            }
        }

        if (cfg.isEnabled(Feature.SKELETON_ALERT)) {
            int skeletons = client.world.getEntitiesByClass(
                    SkeletonEntity.class,
                    p.getBoundingBox().expand(12),
                    e -> e.isAlive()).size();

            if (skeletons > 0) {
                push(client, "skeleton", "WARNING // RANGED HOSTILE DETECTED // I recommend using cover or a shield.", Severity.CAUTION);
                return;
            }
        }

        if (cfg.isEnabled(Feature.NO_TOTEM_ALERT)
                && threat.level().ordinal() >= ThreatAnalyzer.Level.HIGH.ordinal()
                && InventoryUtil.count(p, "totem_of_undying") <= 0) {
            push(client, "totem", "WARNING // HIGH THREAT WITH NO TOTEM // I recommend disengaging.", Severity.DANGER);
            return;
        }

        if (cfg.isEnabled(Feature.NIGHT_WARNING)) {
            long t = Math.floorMod(client.world.getTimeOfDay(), 24000L);
            if (t < 13000L) {
                int seconds = (int)Math.round((13000L - t) / 20.0);
                if (seconds <= 60) {
                    push(client, "night", "NIGHT IN " + seconds + "s // I recommend finding shelter or preparing for combat.", Severity.INFO);
                }
            }
        }

        if (cfg.isEnabled(Feature.LIGHT_WARNING)
                && client.world.getLightLevel(p.getBlockPos()) <= 3) {
            push(client, "light", "LOW LIGHT // hostile spawn risk is elevated.", Severity.INFO);
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
                        .findFirst()
                        .orElse(null);

                if (visible != null) {
                    double distance = Math.sqrt(p.squaredDistanceTo(visible));
                    extra = " // " + String.format(java.util.Locale.ROOT, "%.1fm away", distance);
                }

                push(client, "spec:" + name,
                        "WARNING // [SPEC] " + name + extra + " // spectator detected.",
                        Severity.DANGER);
                break;
            }
        }

        if (cfg.isEnabled(Feature.PLAYER_PROXIMITY_ALERT)) {
            var nearest = client.world.getPlayers().stream()
                    .filter(other -> other != p)
                    .filter(other -> other.squaredDistanceTo(p)
                            <= cfg.playerRadarRadius * cfg.playerRadarRadius)
                    .min(java.util.Comparator.comparingDouble(p::squaredDistanceTo))
                    .orElse(null);

            if (nearest != null) {
                boolean spectator = false;
                if (client.getNetworkHandler() != null) {
                    var info = client.getNetworkHandler().getPlayerListEntry(nearest.getUuid());
                    spectator = info != null && info.getGameMode() == GameMode.SPECTATOR;
                }

                if (!spectator) {
                    String name = nearest.getGameProfile().name();
                    double distance = Math.sqrt(p.squaredDistanceTo(nearest));
                    push(client, "player:" + name,
                            "PLAYER NEAR // " + name + " // "
                                    + String.format(java.util.Locale.ROOT, "%.1fm", distance),
                            Severity.CAUTION);
                }
            }
        }

        if (cfg.isEnabled(Feature.THREAT_ALERT)
                && threat.level().ordinal() >= ThreatAnalyzer.Level.HIGH.ordinal()) {
            boolean increased = threat.level().ordinal() > lastThreat.ordinal();
            if (increased || allowed("threat")) {
                push(client, "threat",
                        "WARNING // THREAT " + threat.level()
                                + " // SCORE " + threat.score()
                                + " // " + threat.recommendation(),
                        threat.level() == ThreatAnalyzer.Level.CRITICAL
                                ? Severity.DANGER
                                : Severity.CAUTION);
            }
        }

        lastThreat = threat.level();
    }

    private boolean lavaNearby(MinecraftClient client) {
        if (client.player == null || client.world == null) return false;

        BlockPos base = client.player.getBlockPos();

        for (int y = -1; y <= 1; y++) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    BlockPos pos = base.add(x, y, z);
                    if (client.world.getFluidState(pos).isIn(FluidTags.LAVA)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean allowed(String key) {
        return System.currentTimeMillis() >= cooldowns.getOrDefault(key, 0L);
    }

    private void push(MinecraftClient client, String key, String text, Severity severity) {
        long now = System.currentTimeMillis();
        if (now < cooldowns.getOrDefault(key, 0L)) return;

        cooldowns.put(key, now + SurvivalUtilsClient.CONFIG.warningCooldownSeconds * 1000L);
        active = new Warning(text, severity, now + 4200L);

        if (SurvivalUtilsClient.CONFIG.warningActionbar && client.player != null) {
            Formatting color = switch (severity) {
                case INFO -> Formatting.YELLOW;
                case CAUTION -> Formatting.GOLD;
                case DANGER -> Formatting.RED;
            };
            client.player.sendMessage(Text.literal(text).formatted(color, Formatting.BOLD), true);
        }
    }
}
