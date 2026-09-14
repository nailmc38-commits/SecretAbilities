package com.secretgamer38.shieldtoggle;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;

public class ShieldToggleClient implements ClientModInitializer {
    private static boolean shieldEnabled = false;

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("shield")
                .executes(ctx -> {
                    shieldEnabled = true;
                    ctx.getSource().sendFeedback(Text.literal("§aShield hold: ON"));
                    return 1;
                })
                .then(ClientCommandManager.literal("down")
                    .executes(ctx -> {
                        shieldEnabled = false;
                        stopUsingShield();
                        ctx.getSource().sendFeedback(Text.literal("§cShield hold: OFF"));
                        return 1;
                    }))
            );
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!shieldEnabled || client.player == null || client.interactionManager == null) {
                return;
            }

            Hand shieldHand = getShieldHand(client);
            if (shieldHand == null) {
                return;
            }

            if (!client.player.isUsingItem()) {
                client.interactionManager.interactItem(client.player, shieldHand);
            }
        });
    }

    private static Hand getShieldHand(MinecraftClient client) {
        if (client.player.getOffHandStack().isOf(Items.SHIELD)) {
            return Hand.OFF_HAND;
        }
        if (client.player.getMainHandStack().isOf(Items.SHIELD)) {
            return Hand.MAIN_HAND;
        }
        return null;
    }

    private static void stopUsingShield() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.interactionManager != null && client.player.isUsingItem()) {
            client.interactionManager.stopUsingItem(client.player);
        }
    }
}
