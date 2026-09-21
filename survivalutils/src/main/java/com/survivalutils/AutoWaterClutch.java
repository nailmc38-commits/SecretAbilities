package com.survivalutils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

public final class AutoWaterClutch {
    private String status = "OFF";
    private boolean triggeredThisFall;
    private boolean pressingUse;
    private int useTicks;
    private int cooldownTicks;

    private int oldSelectedSlot = -1;
    private float oldPitch;
    private boolean swappedFromInventory;
    private int sourceInventoryIndex = -1;
    private int clutchHotbarSlot = -1;

    public String status() {
        return status;
    }

    public void tick(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null) {
            reset(client);
            status = "OFF";
            return;
        }

        var player = client.player;

        if (!SurvivalUtilsClient.CONFIG.isEnabled(Feature.AUTO_WATER_CLUTCH)) {
            reset(client);
            status = "OFF";
            return;
        }

        if (cooldownTicks > 0) cooldownTicks--;

        if (useTicks > 0) {
            pressingUse = true;
            client.options.useKey.setPressed(true);
            useTicks--;

            if (useTicks == 0) {
                client.options.useKey.setPressed(false);
                pressingUse = false;
                restoreInventory(client);
                status = "USED";
                cooldownTicks = 12;
            }
            return;
        }

        if (player.isOnGround() || player.isTouchingWater()) {
            triggeredThisFall = false;
            if (cooldownTicks <= 0) {
                status = InventoryUtil.hasWaterBucket(player) ? "ARMED" : "NO WATER";
            }
            return;
        }

        if (player.isFallFlying() || player.getAbilities().flying) {
            status = "STANDBY";
            return;
        }

        if (!InventoryUtil.hasWaterBucket(player)) {
            status = "NO WATER";
            return;
        }

        status = "ARMED";

        if (triggeredThisFall || cooldownTicks > 0 || client.currentScreen != null) return;

        double vy = player.getVelocity().y;
        if (player.fallDistance < SurvivalUtilsClient.CONFIG.clutchMinFallDistance || vy > -0.42) return;

        int ground = groundDistance(client, SurvivalUtilsClient.CONFIG.clutchTriggerBlocks);
        if (ground < 1 || ground > SurvivalUtilsClient.CONFIG.clutchTriggerBlocks) return;

        if (!selectWaterBucket(client)) {
            status = "NO WATER";
            return;
        }

        oldPitch = player.getPitch();
        player.setPitch(90.0f);

        triggeredThisFall = true;
        status = "TRIGGERING";
        useTicks = 3;
        pressingUse = true;
        client.options.useKey.setPressed(true);
    }

    private int groundDistance(MinecraftClient client, int maxBlocks) {
        var player = client.player;
        int x = player.getBlockX();
        int z = player.getBlockZ();
        int baseY = (int)Math.floor(player.getY());

        for (int i = 1; i <= maxBlocks; i++) {
            BlockPos pos = new BlockPos(x, baseY - i, z);
            var state = client.world.getBlockState(pos);

            if (!state.getCollisionShape(client.world, pos).isEmpty()) {
                return i;
            }
        }

        return -1;
    }

    private boolean selectWaterBucket(MinecraftClient client) {
        var player = client.player;
        int invIndex = InventoryUtil.findInventoryIndex(player, "water_bucket");
        if (invIndex < 0) return false;

        oldSelectedSlot = player.getInventory().getSelectedSlot();
        oldPitch = player.getPitch();
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
        player.setPitch(oldPitch);

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

    private void reset(MinecraftClient client) {
        if (client != null && client.options != null && pressingUse) {
            client.options.useKey.setPressed(false);
        }
        pressingUse = false;
        useTicks = 0;
        triggeredThisFall = false;
        restoreInventory(client);
    }
}
