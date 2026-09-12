package com.secret.dogbreeder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DogBreederClient implements ClientModInitializer {
    private static final double RANGE = 4.25;
    private static final int ACTION_DELAY_TICKS = 7;
    private static final int SAME_WOLF_COOLDOWN_TICKS = 18;

    private static final Map<Integer, Integer> wolfCooldowns = new HashMap<>();

    private static boolean enabled;
    private static int actionDelay;
    private static int statusDelay;
    private static int previousHotbarSlot = -1;

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("breed")
                    .executes(context -> {
                        setEnabled(!enabled);
                        context.getSource().sendFeedback(statusText());
                        return 1;
                    })
                    .then(ClientCommandManager.literal("on").executes(context -> {
                        setEnabled(true);
                        context.getSource().sendFeedback(statusText());
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("off").executes(context -> {
                        setEnabled(false);
                        context.getSource().sendFeedback(statusText());
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("status").executes(context -> {
                        context.getSource().sendFeedback(statusText());
                        return 1;
                    }))
            );
        });

        ClientTickEvents.END_CLIENT_TICK.register(DogBreederClient::tick);
    }

    private static void setEnabled(boolean value) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (value && !enabled && client.player != null) {
            previousHotbarSlot = client.player.getInventory().getSelectedSlot();
        }
        enabled = value;
        actionDelay = 0;
        statusDelay = 0;
        wolfCooldowns.clear();
        if (!enabled && client.player != null && previousHotbarSlot >= 0 && previousHotbarSlot <= 8) {
            client.player.getInventory().setSelectedSlot(previousHotbarSlot);
            previousHotbarSlot = -1;
        }
    }

    private static Text statusText() {
        return Text.literal("DogBreeder: ")
                .append(Text.literal(enabled ? "ON" : "OFF")
                        .formatted(enabled ? Formatting.GREEN : Formatting.RED));
    }

    private static void tick(MinecraftClient client) {
        tickWolfCooldowns();
        if (!enabled || client.player == null || client.world == null || client.interactionManager == null) return;

        ClientPlayerEntity player = client.player;
        if (statusDelay > 0) statusDelay--;
        if (actionDelay > 0) {
            actionDelay--;
            ensureSteakInHand(client);
            return;
        }

        if (!ensureSteakInHand(client)) {
            if (statusDelay <= 0) {
                player.sendMessage(Text.literal("DogBreeder: no steak found in your inventory.").formatted(Formatting.RED), true);
                statusDelay = 40;
            }
            return;
        }

        List<WolfEntity> wolves = ownedNearbyWolves(client);
        if (wolves.isEmpty()) {
            if (statusDelay <= 0) {
                player.sendMessage(Text.literal("DogBreeder: no owned wolves within " + RANGE + " blocks.").formatted(Formatting.YELLOW), true);
                statusDelay = 40;
            }
            return;
        }

        WolfEntity target = chooseTarget(player, wolves);
        if (target == null) {
            if (statusDelay <= 0) {
                player.sendMessage(Text.literal("DogBreeder: nearby wolves are already bred/grown or cooling down.").formatted(Formatting.GRAY), true);
                statusDelay = 40;
            }
            return;
        }

        client.interactionManager.interactEntity(player, target, Hand.MAIN_HAND);
        player.swingHand(Hand.MAIN_HAND);
        wolfCooldowns.put(target.getId(), SAME_WOLF_COOLDOWN_TICKS);
        actionDelay = ACTION_DELAY_TICKS;
        String action = target.isBaby() ? "Growing baby wolf" : "Feeding adult wolf";
        player.sendMessage(Text.literal("DogBreeder: " + action + " • steak " + totalSteakCount(player)).formatted(Formatting.GREEN), true);
    }

    private static List<WolfEntity> ownedNearbyWolves(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        List<WolfEntity> result = new ArrayList<>();
        for (WolfEntity wolf : client.world.getEntitiesByClass(
                WolfEntity.class,
                player.getBoundingBox().expand(RANGE),
                wolf -> wolf.isAlive() && wolf.isTamed() && wolf.isOwner(player))) {
            result.add(wolf);
        }
        result.sort(Comparator.comparingDouble(wolf -> player.squaredDistanceTo(wolf)));
        return result;
    }

    private static WolfEntity chooseTarget(ClientPlayerEntity player, List<WolfEntity> wolves) {
        List<WolfEntity> readyAdults = new ArrayList<>();
        List<WolfEntity> lovingAdults = new ArrayList<>();
        List<WolfEntity> babies = new ArrayList<>();

        for (WolfEntity wolf : wolves) {
            if (wolfCooldowns.getOrDefault(wolf.getId(), 0) > 0) continue;
            if (wolf.isBaby()) babies.add(wolf);
            else if (wolf.isInLove()) lovingAdults.add(wolf);
            else if (wolf.getBreedingAge() == 0) readyAdults.add(wolf);
        }

        readyAdults.sort(Comparator.comparingDouble(wolf -> player.squaredDistanceTo(wolf)));
        babies.sort(Comparator.comparingInt(WolfEntity::getBreedingAge)
                .thenComparingDouble(wolf -> player.squaredDistanceTo(wolf)));

        if (readyAdults.size() >= 2 || (!lovingAdults.isEmpty() && !readyAdults.isEmpty())) return readyAdults.get(0);
        if (!babies.isEmpty()) return babies.get(0);
        if (!readyAdults.isEmpty()) return readyAdults.get(0);
        return null;
    }

    private static boolean ensureSteakInHand(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.interactionManager == null) return false;

        int selected = player.getInventory().getSelectedSlot();
        ItemStack selectedStack = player.getInventory().getStack(selected);
        if (selectedStack.isOf(Items.COOKED_BEEF) && !selectedStack.isEmpty()) return true;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.isOf(Items.COOKED_BEEF) && !stack.isEmpty()) {
                player.getInventory().setSelectedSlot(slot);
                return true;
            }
        }

        if (client.currentScreen == null && player.currentScreenHandler.syncId == 0) {
            for (int slot = 9; slot < 36; slot++) {
                ItemStack stack = player.getInventory().getStack(slot);
                if (stack.isOf(Items.COOKED_BEEF) && !stack.isEmpty()) {
                    client.interactionManager.clickSlot(
                            player.currentScreenHandler.syncId,
                            slot,
                            selected,
                            SlotActionType.SWAP,
                            player
                    );
                    return false;
                }
            }
        }
        return false;
    }

    private static int totalSteakCount(ClientPlayerEntity player) {
        int total = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.isOf(Items.COOKED_BEEF)) total += stack.getCount();
        }
        return total;
    }

    private static void tickWolfCooldowns() {
        wolfCooldowns.replaceAll((id, ticks) -> ticks - 1);
        wolfCooldowns.entrySet().removeIf(entry -> entry.getValue() <= 0);
    }
}
