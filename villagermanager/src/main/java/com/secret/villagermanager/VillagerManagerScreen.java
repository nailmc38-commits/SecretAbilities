package com.secret.villagermanager;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.text.Text;

import java.util.List;

public final class VillagerManagerScreen extends Screen {
    private static final int PER_PAGE = 6;

    private final Screen parent;
    private int page;

    public VillagerManagerScreen(Screen parent) {
        super(Text.literal("Villager Manager"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        clearChildren();

        MinecraftClient client = this.client;
        if (client == null || client.player == null) return;

        List<VillagerEntity> villagers = VillagerManagerClient.nearby(client, 32.0);
        int center = width / 2;
        int top = Math.max(24, height / 2 - 105);

        ButtonWidget header = ButtonWidget.builder(
                Text.literal("Nearby Villagers: " + villagers.size() + " | click a row to track"),
                b -> {}
        ).dimensions(center - 170, top, 340, 20).build();
        header.active = false;
        addDrawableChild(header);

        int start = page * PER_PAGE;
        int end = Math.min(villagers.size(), start + PER_PAGE);

        for (int i = start; i < end; i++) {
            VillagerEntity villager = villagers.get(i);
            double distance = Math.sqrt(client.player.squaredDistanceTo(villager));
            String tracked = VillagerManagerClient.isTracked(villager) ? "★ " : "";
            String label = tracked
                    + VillagerManagerClient.professionName(villager)
                    + " L" + VillagerManagerClient.level(villager)
                    + " | " + String.format("%.1fm", distance)
                    + " | " + villager.getBlockPos().getX()
                    + " " + villager.getBlockPos().getY()
                    + " " + villager.getBlockPos().getZ();

            addDrawableChild(ButtonWidget.builder(Text.literal(label), button -> {
                VillagerManagerClient.track(client, villager);
                rebuild();
            }).dimensions(center - 170, top + 27 + (i - start) * 24, 340, 20).build());
        }

        int y = top + 27 + PER_PAGE * 24 + 4;

        if (page > 0) {
            addDrawableChild(ButtonWidget.builder(Text.literal("< Prev"), b -> {
                page--;
                rebuild();
            }).dimensions(center - 170, y, 80, 20).build());
        }

        int maxPage = villagers.isEmpty() ? 0 : (villagers.size() - 1) / PER_PAGE;
        if (page < maxPage) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Next >"), b -> {
                page++;
                rebuild();
            }).dimensions(center + 90, y, 80, 20).build());
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Rescan"), b -> rebuild())
                .dimensions(center - 85, y, 80, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Clear Track"), b -> {
            VillagerManagerClient.clearTracking(client);
            rebuild();
        }).dimensions(center + 5, y, 80, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
                .dimensions(center - 50, y + 28, 100, 20).build());
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
