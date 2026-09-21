package com.survivalutils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.consume.UseAction;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;

import java.util.OptionalInt;

public final class InventoryUtil {
    private InventoryUtil() {}

    public static String id(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        return Registries.ITEM.getId(stack.getItem()).getPath();
    }

    public static int count(PlayerEntity player, String keyword) {
        if (player == null || keyword == null || keyword.isBlank()) return 0;
        String q = keyword.toLowerCase();
        int total = 0;

        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isEmpty() && id(stack).contains(q)) {
                total += stack.getCount();
            }
        }

        ItemStack off = player.getOffHandStack();
        if (!off.isEmpty() && id(off).contains(q)) total += off.getCount();

        return total;
    }

    public static int foodCount(PlayerEntity player) {
        if (player == null) return 0;
        int total = 0;

        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            if (stack.getItem().getUseAction(stack) == UseAction.EAT) {
                String id = id(stack);
                if (!id.contains("rotten_flesh")
                        && !id.contains("spider_eye")
                        && !id.contains("pufferfish")) {
                    total += stack.getCount();
                }
            }
        }

        return total;
    }

    public static int freeSlots(PlayerEntity player) {
        if (player == null) return 0;
        int free = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            if (player.getInventory().getStack(i).isEmpty()) free++;
        }
        return free;
    }

    public static int durabilityPercent(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isDamageable()) return 100;
        int max = stack.getMaxDamage();
        if (max <= 0) return 100;
        return Math.max(0, Math.min(100,
                (int)Math.round((max - stack.getDamage()) * 100.0 / max)));
    }

    public static ItemStack findShield(PlayerEntity player) {
        if (player == null) return ItemStack.EMPTY;

        ItemStack off = player.getOffHandStack();
        if (!off.isEmpty() && id(off).contains("shield")) return off;

        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isEmpty() && id(stack).contains("shield")) return stack;
        }

        return ItemStack.EMPTY;
    }

    public static ItemStack findBestPickaxe(PlayerEntity player) {
        if (player == null) return ItemStack.EMPTY;

        ItemStack best = ItemStack.EMPTY;
        int bestDurability = -1;

        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty() || !id(stack).contains("pickaxe")) continue;

            int durability = durabilityPercent(stack);
            if (durability > bestDurability) {
                bestDurability = durability;
                best = stack;
            }
        }

        return best;
    }

    public static ItemStack helmet(PlayerEntity player) {
        return player == null ? ItemStack.EMPTY : player.getEquippedStack(EquipmentSlot.HEAD);
    }

    public static boolean hasBed(PlayerEntity player) {
        return count(player, "_bed") > 0;
    }

    public static boolean hasWaterBucket(PlayerEntity player) {
        return count(player, "water_bucket") > 0;
    }

    public static int findInventoryIndex(PlayerEntity player, String keyword) {
        if (player == null || keyword == null || keyword.isBlank()) return -1;
        String q = keyword.toLowerCase();
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isEmpty() && id(stack).contains(q)) return i;
        }
        return -1;
    }

    public static int firstEmptyHotbar(PlayerEntity player) {
        if (player == null) return -1;
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).isEmpty()) return i;
        }
        return -1;
    }

    public static boolean swapInventoryToHotbar(MinecraftClient client, int invIndex, int hotbarSlot) {
        if (client == null || client.player == null || client.interactionManager == null) return false;
        PlayerEntity player = client.player;

        if (invIndex >= 0 && invIndex < 9) return true;

        OptionalInt slot = player.playerScreenHandler.getSlotIndex(player.getInventory(), invIndex);
        if (slot.isEmpty()) return false;

        client.interactionManager.clickSlot(
                player.playerScreenHandler.syncId,
                slot.getAsInt(),
                hotbarSlot,
                SlotActionType.SWAP,
                player);
        return true;
    }
}

