package com.survos;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import java.util.Comparator;
import java.util.List;

public final class AutomationManager {
    public enum Mode { IDLE, MOB_GRIND, MINING, TREE_FARM, CROP_FARM, FISHING, ANIMAL_FARM, INVENTORY }
    public enum State { IDLE, SEARCHING, MOVING, INTERACTING, FIGHTING, LOOTING, EATING, ESCAPING, PAUSED, BLOCKED }

    private Mode mode = Mode.IDLE;
    private State state = State.IDLE;
    private Entity target;
    private Vec3d start;
    private String reason = "Ready";
    private boolean paused;
    private int attackCooldown;

    public Mode mode() { return mode; }
    public State state() { return state; }
    public String reason() { return reason; }
    public boolean active() { return mode != Mode.IDLE; }
    public boolean paused() { return paused; }

    public void start(Mode newMode, MinecraftClient client) {
        stopMovement(client);
        mode = newMode == null ? Mode.IDLE : newMode;
        state = mode == Mode.IDLE ? State.IDLE : State.SEARCHING;
        paused = false;
        target = null;
        if (client.player != null) start = client.player.getEntityPos();
        reason = mode == Mode.IDLE ? "Ready" : "Task started";
        SurvOsClient.notice("Automation: " + pretty(mode));
    }

    public void pause(MinecraftClient client) {
        if (!active()) return;
        paused = true;
        state = State.PAUSED;
        reason = "Paused by user";
        stopMovement(client);
        SurvOsClient.notice("Automation paused");
    }

    public void resume() {
        if (!active()) return;
        paused = false;
        state = State.SEARCHING;
        reason = "Resumed";
        SurvOsClient.notice("Automation resumed");
    }

    public void stop(MinecraftClient client, String why) {
        stopMovement(client);
        mode = Mode.IDLE;
        state = State.IDLE;
        target = null;
        paused = false;
        reason = why == null ? "Stopped" : why;
        SurvOsClient.notice("Automation stopped" + (why == null ? "" : ": " + why));
    }

    public void tick(MinecraftClient client, SurvConfig cfg) {
        if (!active() || paused || client.player == null || client.world == null || client.interactionManager == null) return;
        PlayerEntity p = client.player;
        if (attackCooldown > 0) attackCooldown--;
        if (cfg.lowHealthSafety && p.getHealth() <= cfg.retreatHealth) {
            state = State.ESCAPING;
            reason = "Low health";
            target = null;
            stopMovement(client);
            return;
        }
        if (start != null && p.getEntityPos().distanceTo(start) > cfg.maxDistanceFromStart) {
            state = State.BLOCKED;
            reason = "Distance limit reached";
            stopMovement(client);
            return;
        }
        switch (mode) {
            case MOB_GRIND -> tickMobGrind(client, cfg);
            case MINING -> passiveTask("Mining assistant: local scan mode", client);
            case TREE_FARM -> passiveTask("Tree farm: local scan mode", client);
            case CROP_FARM -> passiveTask("Crop farm: local scan mode", client);
            case FISHING -> passiveTask("Fishing assistant active", client);
            case ANIMAL_FARM -> passiveTask("Animal farm assistant active", client);
            case INVENTORY -> passiveTask("Inventory rules active", client);
            default -> {}
        }
    }

    private void passiveTask(String text, MinecraftClient client) {
        state = State.SEARCHING;
        reason = text;
        stopMovement(client);
    }

    private void tickMobGrind(MinecraftClient client, SurvConfig cfg) {
        PlayerEntity p = client.player;
        if (target == null || !target.isAlive() || target.distanceTo(p) > cfg.automationRange + 4) target = nearestHostile(client, cfg);
        if (target == null) {
            if (cfg.collectLoot) {
                ItemEntity item = nearestItem(client, cfg);
                if (item != null) {
                    target = item; state = State.LOOTING; reason = "Collecting loot";
                } else {
                    state = State.SEARCHING; reason = "Scanning for hostiles"; stopMovement(client); return;
                }
            } else {
                state = State.SEARCHING; reason = "Scanning for hostiles"; stopMovement(client); return;
            }
        }
        double d = p.distanceTo(target);
        lookAt(p, target);
        if (target instanceof ItemEntity) {
            if (d < 1.4) { target = null; stopMovement(client); }
            else setForward(client, true);
            return;
        }
        state = State.FIGHTING;
        reason = target.getType().getName().getString() + " " + String.format("%.1fm", d);
        if (d > 3.0) setForward(client, true);
        else {
            setForward(client, false);
            if (attackCooldown <= 0 && p.getAttackCooldownProgress(0.0f) >= 0.9f) {
                client.interactionManager.attackEntity(p, target);
                p.swingHand(Hand.MAIN_HAND);
                attackCooldown = 4;
            }
        }
    }

    private Entity nearestHostile(MinecraftClient client, SurvConfig cfg) {
        PlayerEntity p = client.player;
        Box box = p.getBoundingBox().expand(cfg.automationRange);
        List<HostileEntity> mobs = client.world.getEntitiesByClass(
                HostileEntity.class, box,
                e -> e.isAlive() && (!cfg.avoidCreepers || !(e instanceof CreeperEntity))
                        && (!cfg.avoidEndermen || !(e instanceof EndermanEntity)));
        return mobs.stream().min(Comparator.comparingDouble(p::squaredDistanceTo)).orElse(null);
    }

    private ItemEntity nearestItem(MinecraftClient client, SurvConfig cfg) {
        PlayerEntity p = client.player;
        List<ItemEntity> items = client.world.getEntitiesByClass(
                ItemEntity.class, p.getBoundingBox().expand(Math.min(8.0, cfg.automationRange)), Entity::isAlive);
        return items.stream().min(Comparator.comparingDouble(p::squaredDistanceTo)).orElse(null);
    }

    private static void lookAt(PlayerEntity p, Entity e) {
        Vec3d from = p.getEyePos();
        Vec3d to = e.getBoundingBox().getCenter();
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float)(MathHelper.atan2(dz, dx) * 57.295776) - 90.0f;
        float pitch = (float)(-(MathHelper.atan2(dy, flat) * 57.295776));
        p.setYaw(yaw);
        p.setPitch(MathHelper.clamp(pitch, -90.0f, 90.0f));
    }

    private static void setForward(MinecraftClient client, boolean down) {
        if (client.options != null) client.options.forwardKey.setPressed(down);
    }

    public static void stopMovement(MinecraftClient client) {
        if (client == null || client.options == null) return;
        client.options.forwardKey.setPressed(false);
        client.options.backKey.setPressed(false);
        client.options.leftKey.setPressed(false);
        client.options.rightKey.setPressed(false);
        client.options.jumpKey.setPressed(false);
        client.options.sneakKey.setPressed(false);
        client.options.useKey.setPressed(false);
        client.options.attackKey.setPressed(false);
    }

    private static String pretty(Mode m) { return m.name().replace('_', ' ').toLowerCase(); }
}
