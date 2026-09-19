package com.secret.villagermanager;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class VillagerManagerClient implements ClientModInitializer {
    private static KeyBinding openKey;
    private static UUID trackedVillager;

    @Override
    public void onInitializeClient() {
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.villagermanager.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F7,
                KeyBinding.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.player != null && client.world != null) {
                    client.setScreen(new VillagerManagerScreen(client.currentScreen));
                }
            }

            if (trackedVillager != null && client.world != null) {
                for (VillagerEntity villager : nearby(client, 48.0)) {
                    villager.setGlowing(villager.getUuid().equals(trackedVillager));
                }
            }
        });
    }

    static List<VillagerEntity> nearby(MinecraftClient client, double radius) {
        if (client.player == null || client.world == null) return List.of();

        return client.world.getEntitiesByClass(
                        VillagerEntity.class,
                        client.player.getBoundingBox().expand(radius),
                        villager -> true
                ).stream()
                .sorted(Comparator.comparingDouble(v -> client.player.squaredDistanceTo(v)))
                .toList();
    }

    static String professionName(VillagerEntity villager) {
        return villager.getVillagerData().profession().getKey()
                .map(key -> key.getValue().getPath())
                .orElse("unknown");
    }

    static int level(VillagerEntity villager) {
        return villager.getVillagerData().level();
    }

    static void track(MinecraftClient client, VillagerEntity target) {
        if (client.world == null) return;

        for (VillagerEntity villager : nearby(client, 48.0)) {
            villager.setGlowing(false);
        }

        if (trackedVillager != null && trackedVillager.equals(target.getUuid())) {
            trackedVillager = null;
            message(client, "Tracking cleared.");
            return;
        }

        trackedVillager = target.getUuid();
        target.setGlowing(true);
        message(client, "Tracking " + professionName(target) + " villager at "
                + target.getBlockPos().getX() + ", "
                + target.getBlockPos().getY() + ", "
                + target.getBlockPos().getZ() + ".");
    }

    static void clearTracking(MinecraftClient client) {
        if (client.world != null) {
            for (VillagerEntity villager : nearby(client, 48.0)) {
                villager.setGlowing(false);
            }
        }
        trackedVillager = null;
        message(client, "Tracking cleared.");
    }

    static boolean isTracked(VillagerEntity villager) {
        return trackedVillager != null && trackedVillager.equals(villager.getUuid());
    }

    static void message(MinecraftClient client, String text) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal("[Villager Manager] " + text), false);
        }
    }
}
