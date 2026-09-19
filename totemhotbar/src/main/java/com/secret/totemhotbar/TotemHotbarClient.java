package com.secret.totemhotbar;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class TotemHotbarClient implements ClientModInitializer {
    public static final int RESERVED_HOTBAR_SLOT = 7; // keyboard key 8

    private static KeyBinding toggleKey;
    private static boolean enabled = true;
    private static int refillDelay = -1;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.totemhotbar.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F4,
                KeyBinding.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                enabled = !enabled;
                message(client, "Totem Hotbar " + (enabled ? "ON" : "OFF"));
            }

            if (!enabled || refillDelay < 0) return;

            if (refillDelay > 0) {
                refillDelay--;
                return;
            }

            refillDelay = -1;
            switchAndRefill(client);
        });
    }

    public static void onTotemPop(MinecraftClient client) {
        if (!enabled || client.player == null) return;
        refillDelay = 1;
    }

    private static void switchAndRefill(MinecraftClient client) {
        if (client.player == null || client.interactionManager == null || client.getNetworkHandler() == null) return;

        ItemStack reserved = client.player.getInventory().getStack(RESERVED_HOTBAR_SLOT);

        if (!reserved.isOf(Items.TOTEM_OF_UNDYING)) {
            int sourceInvIndex = findTotem(client);
            if (sourceInvIndex != -1) {
                int sourceHandlerSlot = inventoryIndexToHandlerSlot(sourceInvIndex);
                client.interactionManager.clickSlot(
                        client.player.currentScreenHandler.syncId,
                        sourceHandlerSlot,
                        RESERVED_HOTBAR_SLOT,
                        SlotActionType.SWAP,
                        client.player
                );
            }
        }

        ItemStack after = client.player.getInventory().getStack(RESERVED_HOTBAR_SLOT);
        if (after.isOf(Items.TOTEM_OF_UNDYING)) {
            client.player.getInventory().setSelectedSlot(RESERVED_HOTBAR_SLOT);
            client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(RESERVED_HOTBAR_SLOT));
            message(client, "Switched main hand to totem slot 8.");
        } else {
            message(client, "No spare totems found for slot 8.");
        }
    }

    private static int findTotem(MinecraftClient client) {
        for (int i = 0; i < 36; i++) {
            if (i == RESERVED_HOTBAR_SLOT) continue;
            if (client.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
                return i;
            }
        }
        return -1;
    }

    private static int inventoryIndexToHandlerSlot(int inventoryIndex) {
        if (inventoryIndex >= 0 && inventoryIndex <= 8) {
            return 36 + inventoryIndex;
        }
        return inventoryIndex;
    }

    private static void message(MinecraftClient client, String text) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal("[Totem Hotbar] " + text), false);
        }
    }
}
