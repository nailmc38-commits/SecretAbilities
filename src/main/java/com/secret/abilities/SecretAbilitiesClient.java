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

import java.util.LinkedHashMap;
import java.util.Map;

public class SecretAbilitiesClient implements ClientModInitializer {
    private static KeyBinding menuKey;
    private static KeyBinding addWaypointKey;
    private static KeyBinding panicKey;
    private static final Map<String, KeyBinding> MODULE_KEYS = new LinkedHashMap<>();

    @Override
    public void onInitializeClient() {
        ModuleRegistry.load();
        WaypointManager.load();
        MacroManager.load();

        menuKey = register("key.voidclient.menu", GLFW.GLFW_KEY_F9);
        addWaypointKey = register("key.voidclient.add_waypoint", GLFW.GLFW_KEY_UNKNOWN);
        panicKey = register("key.voidclient.panic", GLFW.GLFW_KEY_UNKNOWN);

        for (ClientModule module : ModuleRegistry.all()) {
            if ("panic".equals(module.id)) continue;
            KeyBinding key = register("key.voidclient.module." + module.id, GLFW.GLFW_KEY_UNKNOWN);
            MODULE_KEYS.put(module.id, key);
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null) ModState.localPlayerUuid = client.player.getUuid();

            while (menuKey.wasPressed()) client.setScreen(new AbilityScreen());

            while (panicKey.wasPressed()) {
                ModuleRegistry.disableAll();
                showMessage(client, "All modules disabled", Formatting.RED);
            }

            while (addWaypointKey.wasPressed()) {
                WaypointManager.Waypoint waypoint = WaypointManager.addCurrent(client);
                if (waypoint != null) showMessage(client, "Saved " + waypoint.name(), Formatting.AQUA);
            }

            if (client.currentScreen == null) {
                for (Map.Entry<String, KeyBinding> entry : MODULE_KEYS.entrySet()) {
                    while (entry.getValue().wasPressed()) {
                        ModuleRegistry.toggle(entry.getKey());
                        ClientModule module = ModuleRegistry.get(entry.getKey());
                        if (module != null && ModuleRegistry.isEnabled("notifications")) showToggle(client, module);
                    }
                }
            }

            VoidClientRuntime.tick(client);
        });
    }

    private static KeyBinding register(String translationKey, int defaultKey) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(
                translationKey, InputUtil.Type.KEYSYM, defaultKey, KeyBinding.Category.MISC));
    }

    private static void showToggle(MinecraftClient client, ClientModule module) {
        if (client.player == null) return;

        client.player.sendMessage(
                Text.literal(module.name + ": ")
                        .append(Text.literal(module.isEnabled() ? "ON" : "OFF")
                                .formatted(module.isEnabled() ? Formatting.GREEN : Formatting.RED)),
                true
        );
    }

    private static void showMessage(MinecraftClient client, String message, Formatting formatting) {
        if (client.player != null) client.player.sendMessage(Text.literal(message).formatted(formatting), true);
    }

    public static void refreshXray() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.worldRenderer != null) client.worldRenderer.reload();
    }
}
