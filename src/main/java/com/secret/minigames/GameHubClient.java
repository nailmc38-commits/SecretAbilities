package com.secret.minigames;

import com.secret.minigames.games.MiniMinecraftGame;
import com.secret.minigames.screens.GameHubScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class GameHubClient implements ClientModInitializer {
    public static final MiniMinecraftGame MINI_MINECRAFT = new MiniMinecraftGame();
    private static KeyBinding openHubKey;

    @Override
    public void onInitializeClient() {
        openHubKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.minigamehub.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F9,
                KeyBinding.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openHubKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new GameHubScreen());
                }
            }
        });
    }
}
