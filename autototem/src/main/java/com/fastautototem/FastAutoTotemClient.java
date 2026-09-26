package com.fastautototem;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.OptionalInt;

public final class FastAutoTotemClient implements ClientModInitializer {
    public enum Mode { ALWAYS, EMPTY_ONLY }

    private static boolean enabled = true;
    private static Mode mode = Mode.ALWAYS;
    private static KeyBinding toggleKey;
    private static int swaps;
    private static int cooldownTicks;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.fastautototem.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F8,
                "category.fastautototem"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(FastAutoTotemClient::tick);
        registerCommands();
    }

    private static void tick(MinecraftClient client) {
        while (toggleKey.wasPressed()) {
            enabled = !enabled;
            feedback(client, "Auto Totem // " + (enabled ? "ON" : "OFF"));
        }

        if (cooldownTicks > 0) cooldownTicks--;

        if (!enabled || cooldownTicks > 0 || client.player == null || client.interactionManager == null) return;

        // Avoid inventory desync while another handled screen/container is open.
        if (client.currentScreen != null) return;

        ItemStack offhand = client.player.getOffHandStack();
        if (offhand.isOf(Items.TOTEM_OF_UNDYING)) return;

        if (mode == Mode.EMPTY_ONLY && !offhand.isEmpty()) return;

        int inventoryIndex = findTotem(client);
        if (inventoryIndex < 0) return;

        OptionalInt screenSlot = client.player.playerScreenHandler.getSlotIndex(
                client.player.getInventory(),
                inventoryIndex
        );
        if (screenSlot.isEmpty()) return;

        // In player inventory SWAP button 40 is the offhand slot.
        client.interactionManager.clickSlot(
                client.player.playerScreenHandler.syncId,
                screenSlot.getAsInt(),
                40,
                SlotActionType.SWAP,
                client.player
        );

        cooldownTicks = 1;
        swaps++;
        feedback(client, "Totem equipped // " + countTotems(client) + " left");
    }

    private static int findTotem(MinecraftClient client) {
        for (int i = 0; i < client.player.getInventory().size(); i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (stack.isOf(Items.TOTEM_OF_UNDYING)) return i;
        }
        return -1;
    }

    private static int countTotems(MinecraftClient client) {
        int count = client.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING) ? 1 : 0;
        for (int i = 0; i < client.player.getInventory().size(); i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (stack.isOf(Items.TOTEM_OF_UNDYING)) count += stack.getCount();
        }
        return count;
    }

    private static void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("autototem")
                    .executes(ctx -> {
                        enabled = !enabled;
                        feedback(MinecraftClient.getInstance(), "Auto Totem // " + (enabled ? "ON" : "OFF"));
                        return 1;
                    })
                    .then(ClientCommandManager.literal("on").executes(ctx -> {
                        enabled = true;
                        feedback(MinecraftClient.getInstance(), "Auto Totem // ON");
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("off").executes(ctx -> {
                        enabled = false;
                        feedback(MinecraftClient.getInstance(), "Auto Totem // OFF");
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("status").executes(ctx -> {
                        MinecraftClient c = MinecraftClient.getInstance();
                        feedback(c, "Status // " + (enabled ? "ON" : "OFF")
                                + " // Mode " + mode
                                + " // Swaps " + swaps
                                + (c.player != null ? " // Totems " + countTotems(c) : ""));
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("mode")
                            .then(ClientCommandManager.argument("mode", StringArgumentType.word())
                                    .suggests((ctx,b) -> {
                                        b.suggest("always");
                                        b.suggest("empty");
                                        return b.buildFuture();
                                    })
                                    .executes(ctx -> {
                                        String value = StringArgumentType.getString(ctx, "mode");
                                        if (value.equalsIgnoreCase("always")) mode = Mode.ALWAYS;
                                        else if (value.equalsIgnoreCase("empty")) mode = Mode.EMPTY_ONLY;
                                        else {
                                            feedback(MinecraftClient.getInstance(), "Modes: always | empty");
                                            return 0;
                                        }
                                        feedback(MinecraftClient.getInstance(), "Mode // " + mode);
                                        return 1;
                                    }))));
        });
    }

    private static void feedback(MinecraftClient client, String message) {
        if (client != null && client.player != null) {
            client.player.sendMessage(Text.literal("[AUTO TOTEM] " + message), true);
        }
    }
}
