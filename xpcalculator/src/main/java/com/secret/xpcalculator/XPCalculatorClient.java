package com.secret.xpcalculator;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class XPCalculatorClient implements ClientModInitializer {
    private static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.xpcalculator.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F6,
                KeyBinding.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.player != null) {
                    client.setScreen(new XPCalculatorScreen(client.currentScreen));
                }
            }
        });
    }

    static long xpAtStartOfLevel(int level) {
        if (level <= 16) {
            return (long) level * level + 6L * level;
        }
        if (level <= 31) {
            return Math.round(2.5 * level * level - 40.5 * level + 360.0);
        }
        return Math.round(4.5 * level * level - 162.5 * level + 2220.0);
    }

    static int xpToNextLevel(int level) {
        if (level <= 15) return 2 * level + 7;
        if (level <= 30) return 5 * level - 38;
        return 9 * level - 158;
    }

    static long currentXp(MinecraftClient client) {
        if (client.player == null) return 0;
        int level = client.player.experienceLevel;
        long base = xpAtStartOfLevel(level);
        long within = (long) Math.floor(client.player.experienceProgress * xpToNextLevel(level));
        return base + within;
    }
}
