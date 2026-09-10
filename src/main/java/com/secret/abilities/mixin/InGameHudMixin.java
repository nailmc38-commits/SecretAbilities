package com.secret.abilities.mixin;

import com.secret.abilities.ModState;
import com.secret.abilities.WaypointManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void secretabilities$renderHud(
            DrawContext context,
            RenderTickCounter tickCounter,
            CallbackInfo ci
    ) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.player == null || client.world == null) {
            return;
        }

        int y = 6;

        if (ModState.statsHud) {
            int fps = client.getCurrentFps();
            int ping = -1;

            ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();
            if (networkHandler != null) {
                PlayerListEntry entry = networkHandler.getPlayerListEntry(client.player.getUuid());
                if (entry != null) {
                    ping = entry.getLatency();
                }
            }

            String pingText = ping >= 0 ? ping + " ms" : "--";
            context.drawTextWithShadow(
                    client.textRenderer,
                    Text.literal("FPS: " + fps + " | Ping: " + pingText),
                    6,
                    y,
                    0xFFFFFF
            );
            y += 13;
        }

        if (ModState.waypointHud) {
            String dimension = client.world.getRegistryKey().getValue().toString();
            List<WaypointManager.Waypoint> visible = new ArrayList<>();

            for (WaypointManager.Waypoint waypoint : WaypointManager.getAll()) {
                if (waypoint.dimension().equals(dimension)) {
                    visible.add(waypoint);
                }
            }

            visible.sort(Comparator.comparingDouble(waypoint ->
                    client.player.squaredDistanceTo(
                            waypoint.x() + 0.5,
                            waypoint.y() + 0.5,
                            waypoint.z() + 0.5
                    )
            ));

            int shown = 0;
            for (WaypointManager.Waypoint waypoint : visible) {
                if (shown >= 5) {
                    break;
                }

                double distance = Math.sqrt(client.player.squaredDistanceTo(
                        waypoint.x() + 0.5,
                        waypoint.y() + 0.5,
                        waypoint.z() + 0.5
                ));

                String line = waypoint.name()
                        + " [" + waypoint.x() + ", " + waypoint.y() + ", " + waypoint.z() + "] "
                        + Math.round(distance) + "m";

                context.drawTextWithShadow(
                        client.textRenderer,
                        Text.literal(line),
                        6,
                        y,
                        0xFFFFFF
                );

                y += 11;
                shown++;
            }
        }
    }
}
