package com.secret.abilities;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

public class SecretAbilitiesClient implements ClientModInitializer {
    private static KeyBinding menuKey;
    private static KeyBinding xrayKey;
    private static KeyBinding espKey;
    private static KeyBinding waterWalkKey;
    private static KeyBinding statsHudKey;
    private static KeyBinding waypointHudKey;
    private static KeyBinding addWaypointKey;

    @Override
    public void onInitializeClient() {
        WaypointManager.load();

        menuKey = register("key.secretabilities.menu", GLFW.GLFW_KEY_F9);
        xrayKey = register("key.secretabilities.xray", GLFW.GLFW_KEY_UNKNOWN);
        espKey = register("key.secretabilities.esp", GLFW.GLFW_KEY_UNKNOWN);
        waterWalkKey = register("key.secretabilities.water_walk", GLFW.GLFW_KEY_UNKNOWN);
        statsHudKey = register("key.secretabilities.stats_hud", GLFW.GLFW_KEY_UNKNOWN);
        waypointHudKey = register("key.secretabilities.waypoint_hud", GLFW.GLFW_KEY_UNKNOWN);
        addWaypointKey = register("key.secretabilities.add_waypoint", GLFW.GLFW_KEY_UNKNOWN);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null) {
                ModState.localPlayerUuid = client.player.getUuid();
            }

            while (menuKey.wasPressed()) {
                client.setScreen(new AbilityScreen());
            }

            while (xrayKey.wasPressed()) {
                ModState.xray = !ModState.xray;
                refreshXray();
                showToggle(client, "X-Ray", ModState.xray);
            }

            while (espKey.wasPressed()) {
                ModState.playerEsp = !ModState.playerEsp;
                showToggle(client, "Player ESP", ModState.playerEsp);
            }

            while (waterWalkKey.wasPressed()) {
                ModState.walkOnWater = !ModState.walkOnWater;
                showToggle(client, "Walk on Water", ModState.walkOnWater);
            }

            while (statsHudKey.wasPressed()) {
                ModState.statsHud = !ModState.statsHud;
                showToggle(client, "FPS / Ping HUD", ModState.statsHud);
            }

            while (waypointHudKey.wasPressed()) {
                ModState.waypointHud = !ModState.waypointHud;
                showToggle(client, "Waypoint HUD", ModState.waypointHud);
            }

            while (addWaypointKey.wasPressed()) {
                WaypointManager.Waypoint waypoint = WaypointManager.addCurrent(client);
                if (waypoint != null && client.player != null) {
                    client.player.sendMessage(
                            Text.literal("Saved " + waypoint.name()).formatted(Formatting.AQUA),
                            true
                    );
                }
            }
        });
    }

    private static KeyBinding register(String translationKey, int defaultKey) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(
                translationKey,
                InputUtil.Type.KEYSYM,
                defaultKey,
                KeyBinding.Category.MISC
        ));
    }

    private static void showToggle(MinecraftClient client, String name, boolean enabled) {
        if (client.player == null) {
            return;
        }

        client.player.sendMessage(
                Text.literal(name + ": ")
                        .append(Text.literal(enabled ? "ON" : "OFF")
                                .formatted(enabled ? Formatting.GREEN : Formatting.RED)),
                true
        );
    }

    public static void refreshXray() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.worldRenderer != null) {
            client.worldRenderer.reload();
        }
    }
}
