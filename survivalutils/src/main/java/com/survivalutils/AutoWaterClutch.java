package com.survivalutils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class AutoWaterClutch {
    private String status = "OFF";
    private boolean triggeredThisFall;
    private int cooldownTicks;
    private int restoreTicks;

    private int oldSelectedSlot = -1;
    private boolean swappedFromInventory;
    private int sourceInventoryIndex = -1;
    private int clutchHotbarSlot = -1;

    public String status() {
        return status;
    }

    public void tick(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null) {
            clearState(client);
            status = "OFF";
            return;
        }

        var player = client.player;

        if (!SurvivalUtilsClient.CONFIG.isEnabled(Feature.AUTO_WATER_CLUTCH)) {
            clearState(client);
            status = "OFF";
            return;
        }

        if (cooldownTicks > 0) cooldownTicks--;

        if (restoreTicks > 0) {
            restoreTicks--;
            if (restoreTicks == 0) {
                restoreInventory(client);
                status = player.isOnGround() ? "ARMED" : "USED";
            }
        }

        if (player.isOnGround() || player.isTouchingWater()) {
            triggeredThisFall = false;
            if (cooldownTicks <= 0 && restoreTicks <= 0) {
                status = InventoryUtil.hasWaterBucket(player) ? "ARMED" : "NO WATER";
            }
            return;
        }

        if (player.isGliding() || player.getAbilities().flying) {
            status = "STANDBY";
            return;
        }

        if (!InventoryUtil.hasWaterBucket(player)) {
            status = "NO WATER";
            return;
        }

        if (restoreTicks <= 0) status = "ARMED";

        if (triggeredThisFall || cooldownTicks > 0 || restoreTicks > 0 || client.currentScreen != null) return;

        double vy = player.getVelocity().y;
        if (player.fallDistance < SurvivalUtilsClient.CONFIG.clutchMinFallDistance || vy > -0.42) return;

        BlockPos ground = groundBlock(client, SurvivalUtilsClient.CONFIG.clutchTriggerBlocks);
        if (ground == null) return;

        if (!selectWaterBucket(client)) {
            status = "NO WATER";
            return;
        }

        if (client.interactionManager == null) {
            restoreInventory(client);
            return;
        }

        Vec3d hitPos = Vec3d.ofCenter(ground).add(0.0, 0.5, 0.0);
        BlockHitResult hit = new BlockHitResult(
                hitPos,
                Direction.UP,
                ground,
                false
        );

        client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
        player.swingHand(Hand.MAIN_HAND);

        triggeredThisFall = true;
        status = "TRIGGERING";
        restoreTicks = 2;
        cooldownTicks = 12;
    }

    private BlockPos groundBlock(MinecraftClient client, int maxBlocks) {
        var player = client.player;
        int x = player.getBlockX();
        int z = player.getBlockZ();
        int baseY = (int)Math.floor(player.getY());

        for (int i = 1; i <= maxBlocks; i++) {
            BlockPos pos = new BlockPos(x, baseY - i, z);
            var state = client.world.getBlockState(pos);

            if (!state.getCollisionShape(client.world, pos).isEmpty()) {
                return pos;
            }
        }

        return null;
    }

    private boolean selectWaterBucket(MinecraftClient client) {
        var player = client.player;
        int invIndex = InventoryUtil.findInventoryIndex(player, "water_bucket");
        if (invIndex < 0) return false;

        oldSelectedSlot = player.getInventory().getSelectedSlot();
        swappedFromInventory = false;
        sourceInventoryIndex = -1;

        if (invIndex < 9) {
            clutchHotbarSlot = invIndex;
            player.getInventory().setSelectedSlot(invIndex);
            return true;
        }

        int empty = InventoryUtil.firstEmptyHotbar(player);
        clutchHotbarSlot = empty >= 0 ? empty : oldSelectedSlot;

        if (!InventoryUtil.swapInventoryToHotbar(client, invIndex, clutchHotbarSlot)) {
            clutchHotbarSlot = -1;
            return false;
        }

        swappedFromInventory = true;
        sourceInventoryIndex = invIndex;
        player.getInventory().setSelectedSlot(clutchHotbarSlot);
        return true;
    }

    private void restoreInventory(MinecraftClient client) {
        if (client == null || client.player == null) return;

        var player = client.player;

        if (swappedFromInventory && sourceInventoryIndex >= 9 && clutchHotbarSlot >= 0) {
            InventoryUtil.swapInventoryToHotbar(client, sourceInventoryIndex, clutchHotbarSlot);
        }

        if (oldSelectedSlot >= 0 && oldSelectedSlot < 9) {
            player.getInventory().setSelectedSlot(oldSelectedSlot);
        }

        oldSelectedSlot = -1;
        sourceInventoryIndex = -1;
        clutchHotbarSlot = -1;
        swappedFromInventory = false;
    }

    private void clearState(MinecraftClient client) {
        restoreTicks = 0;
        triggeredThisFall = false;
        restoreInventory(client);
    }
}
