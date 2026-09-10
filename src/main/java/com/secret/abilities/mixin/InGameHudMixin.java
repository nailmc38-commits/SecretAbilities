package com.secret.abilities.mixin;

import com.secret.abilities.ClientModule;
import com.secret.abilities.ModuleRegistry;
import com.secret.abilities.VoidClientRuntime;
import com.secret.abilities.WaypointManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
    private void voidclient$renderHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        int y = 6;

        if (ModuleRegistry.isEnabled("stats_hud")) {
            int fps = client.getCurrentFps();
            int ping = -1;
            ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();

            if (networkHandler != null) {
                PlayerListEntry entry = networkHandler.getPlayerListEntry(client.player.getUuid());
                if (entry != null) ping = entry.getLatency();
            }

            y = line(context, client, y,
                    "Void Client | FPS " + fps + " | Ping " + (ping >= 0 ? ping + "ms" : "--"));
        }

        if (ModuleRegistry.isEnabled("coords_hud")) {
            y = line(context, client, y,
                    String.format("XYZ %.1f / %.1f / %.1f",
                            client.player.getX(), client.player.getY(), client.player.getZ()));
        }

        if (ModuleRegistry.isEnabled("cps")) {
            y = line(context, client, y, "CPS: " + VoidClientRuntime.cps());
        }

        if (ModuleRegistry.isEnabled("combat_timer")) {
            y = line(context, client, y, "Combat: " + VoidClientRuntime.combatSeconds() + "s");
        }

        if (ModuleRegistry.isEnabled("damage_indicator")) {
            y = line(context, client, y, String.format("Last damage: %.1f", VoidClientRuntime.lastDamage()));
        }

        if (ModuleRegistry.isEnabled("target_hud")
                && client.targetedEntity instanceof LivingEntity living) {
            y = line(context, client, y,
                    "Target: " + living.getName().getString() + " | HP " + String.format("%.1f", living.getHealth()));
        }

        if (ModuleRegistry.isEnabled("reach_display") && client.targetedEntity != null) {
            double distance = client.player.distanceTo(client.targetedEntity);
            y = line(context, client, y, String.format("Reach: %.2fm", distance));
        }

        if (ModuleRegistry.isEnabled("inventory_hud")) {
            int used = 0;
            for (int i = 0; i < 36; i++) {
                if (!client.player.getInventory().getStack(i).isEmpty()) used++;
            }
            y = line(context, client, y, "Inventory: " + used + "/36");
        }

        if (ModuleRegistry.isEnabled("potion_hud")) {
            y = line(context, client, y, "Effects: " + client.player.getStatusEffects().size());
        }

        if (ModuleRegistry.isEnabled("armor_hud") || ModuleRegistry.isEnabled("durability_hud")) {
            StringBuilder armor = new StringBuilder("Armor: ");
            boolean first = true;
            EquipmentSlot[] armorSlots = {
                    EquipmentSlot.HEAD,
                    EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS,
                    EquipmentSlot.FEET
            };

            for (EquipmentSlot slot : armorSlots) {
                ItemStack stack = client.player.getEquippedStack(slot);
                if (stack.isEmpty()) continue;
                if (!first) armor.append(" | ");
                first = false;
                armor.append(stack.getName().getString());

                if (ModuleRegistry.isEnabled("durability_hud") && stack.isDamageable()) {
                    armor.append(" ")
                            .append(stack.getMaxDamage() - stack.getDamage())
                            .append("/")
                            .append(stack.getMaxDamage());
                }
            }

            if (!first) y = line(context, client, y, armor.toString());
        }

        if (ModuleRegistry.isEnabled("item_counters")) {
            int totems = count(client, Items.TOTEM_OF_UNDYING);
            int crystals = count(client, Items.END_CRYSTAL);
            int pearls = count(client, Items.ENDER_PEARL);
            int gapples = count(client, Items.GOLDEN_APPLE) + count(client, Items.ENCHANTED_GOLDEN_APPLE);
            y = line(context, client, y,
                    "Totem " + totems + " | Crystal " + crystals + " | Pearl " + pearls + " | Gapple " + gapples);
        }

        if (ModuleRegistry.isEnabled("waypoints")) {
            y = drawWaypoints(context, client, y);
        }

        if (ModuleRegistry.isEnabled("beat_game")
                || ModuleRegistry.isEnabled("auto_mine")
                || ModuleRegistry.isEnabled("pathfinder")) {
            y = line(context, client, y, "Bot: " + VoidClientRuntime.botStatus());
        }

        if (ModuleRegistry.isEnabled("active_modules")) {
            int screenWidth = context.getScaledWindowWidth();
            int rightY = 6;
            int shown = 0;

            for (ClientModule module : ModuleRegistry.all()) {
                if (!module.isEnabled() || "active_modules".equals(module.id)) continue;

                String text = module.name;
                int x = screenWidth - client.textRenderer.getWidth(text) - 6;
                context.drawTextWithShadow(client.textRenderer, Text.literal(text), x, rightY, 0xFFFFFF);
                rightY += 11;
                if (++shown >= 12) break;
            }
        }
    }

    private int drawWaypoints(DrawContext context, MinecraftClient client, int y) {
        String dimension = client.world.getRegistryKey().getValue().toString();
        List<WaypointManager.Waypoint> visible = new ArrayList<>();

        for (WaypointManager.Waypoint waypoint : WaypointManager.getAll()) {
            if (waypoint.dimension().equals(dimension)) visible.add(waypoint);
        }

        visible.sort(Comparator.comparingDouble(waypoint ->
                client.player.squaredDistanceTo(waypoint.x() + 0.5, waypoint.y() + 0.5, waypoint.z() + 0.5)));

        int shown = 0;
        for (WaypointManager.Waypoint waypoint : visible) {
            if (shown >= 5) break;

            double distance = Math.sqrt(client.player.squaredDistanceTo(
                    waypoint.x() + 0.5, waypoint.y() + 0.5, waypoint.z() + 0.5));

            y = line(context, client, y,
                    waypoint.name() + " [" + waypoint.x() + ", " + waypoint.y() + ", " + waypoint.z()
                            + "] " + Math.round(distance) + "m");
            shown++;
        }
        return y;
    }

    private int line(DrawContext context, MinecraftClient client, int y, String text) {
        context.drawTextWithShadow(client.textRenderer, Text.literal(text), 6, y, 0xFFFFFF);
        return y + 12;
    }

    private int count(MinecraftClient client, Item item) {
        int count = 0;
        for (int i = 0; i < client.player.getInventory().size(); i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (stack.isOf(item)) count += stack.getCount();
        }
        return count;
    }
}
