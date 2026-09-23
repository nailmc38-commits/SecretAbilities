package com.survivalutils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;

import java.util.*;

public final class MobControlManager {
    public enum Order { NONE, HOLD, FOLLOW }

    private static final LinkedHashSet<UUID> SELECTED = new LinkedHashSet<>();
    private static final ArrayDeque<UUID> COMMAND_QUEUE = new ArrayDeque<>();
    private static Order queuedOrder = Order.NONE;
    private static int ticks;
    private static int commanded;
    private static int skipped;

    private MobControlManager() {}

    public static void tick(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null) {
            COMMAND_QUEUE.clear();
            queuedOrder = Order.NONE;
            return;
        }

        if (++ticks % 4 != 0) return;

        SELECTED.removeIf(id -> {
            Entity e = client.world.getEntity(id);
            return !(e instanceof TameableEntity tame) || !tame.isAlive() || !tame.isOwner(client.player);
        });

        if (COMMAND_QUEUE.isEmpty() || queuedOrder == Order.NONE) return;

        UUID id = COMMAND_QUEUE.removeFirst();
        Entity e = client.world.getEntity(id);
        if (!(e instanceof TameableEntity tame) || !tame.isOwner(client.player)) {
            skipped++;
            finishIfDone(client);
            return;
        }

        boolean wantSitting = queuedOrder == Order.HOLD;
        boolean isSitting = tame.isInSittingPose() || tame.isSitting();

        if (isSitting == wantSitting) {
            commanded++;
            finishIfDone(client);
            return;
        }

        if (client.player.squaredDistanceTo(tame) > 36.0) {
            skipped++;
            finishIfDone(client);
            return;
        }

        int empty = findEmptyHotbar(client);
        if (empty < 0) {
            skipped++;
            finishIfDone(client);
            return;
        }

        int old = client.player.getInventory().getSelectedSlot();
        client.player.getInventory().setSelectedSlot(empty);
        client.interactionManager.interactEntity(client.player, tame, Hand.MAIN_HAND);
        client.player.swingHand(Hand.MAIN_HAND);
        client.player.getInventory().setSelectedSlot(old);
        commanded++;
        finishIfDone(client);
    }

    public static void toggleLookedAt(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null) return;
        if (!(client.crosshairTarget instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof TameableEntity tame)) {
            message(client, "Look at one of your tamed mobs first.");
            return;
        }
        if (!tame.isOwner(client.player)) {
            message(client, "That mob is not yours, so SURV cannot command it.");
            return;
        }

        UUID id = tame.getUuid();
        if (SELECTED.remove(id)) {
            message(client, "Removed " + tame.getName().getString() + " from squad.");
        } else {
            SELECTED.add(id);
            message(client, "Selected " + tame.getName().getString() + ".");
        }
    }

    public static void selectNearby(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null) return;
        var mobs = client.world.getEntitiesByClass(
                TameableEntity.class,
                client.player.getBoundingBox().expand(32),
                tame -> tame.isAlive() && tame.isOwner(client.player)
        );
        for (TameableEntity tame : mobs) SELECTED.add(tame.getUuid());
        message(client, "Selected " + mobs.size() + " owned tameable mob(s) nearby.");
    }

    public static void clear(MinecraftClient client) {
        SELECTED.clear();
        COMMAND_QUEUE.clear();
        queuedOrder = Order.NONE;
        message(client, "Squad selection cleared.");
    }

    public static void order(MinecraftClient client, Order order) {
        if (SELECTED.isEmpty()) {
            message(client, "Select your tameable mobs first.");
            return;
        }
        COMMAND_QUEUE.clear();
        COMMAND_QUEUE.addAll(SELECTED);
        queuedOrder = order;
        commanded = 0;
        skipped = 0;
        message(client, order == Order.HOLD
                ? "HOLD order queued."
                : "FOLLOW/RALLY order queued.");
    }

    private static void finishIfDone(MinecraftClient client) {
        if (!COMMAND_QUEUE.isEmpty()) return;
        Order finished = queuedOrder;
        queuedOrder = Order.NONE;
        String note = skipped > 0
                ? " // " + skipped + " skipped (too far/no empty hotbar slot)"
                : "";
        message(client, finished.name() + " complete // " + commanded + " commanded" + note);
    }

    private static int findEmptyHotbar(MinecraftClient client) {
        for (int i=0;i<9;i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (stack.isEmpty()) return i;
        }
        return -1;
    }

    public static int selectedCount() { return SELECTED.size(); }
    public static Order queuedOrder() { return queuedOrder; }

    private static void message(MinecraftClient client, String text) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal("[SURV // MOB] " + text), false);
        }
        SurvMegaState.log("MOB", text);
    }
}
