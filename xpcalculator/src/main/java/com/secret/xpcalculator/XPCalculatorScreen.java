package com.secret.xpcalculator;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class XPCalculatorScreen extends Screen {
    private final Screen parent;
    private int targetLevel;

    public XPCalculatorScreen(Screen parent) {
        super(Text.literal("XP Calculator"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (client != null && client.player != null && targetLevel == 0) {
            targetLevel = Math.max(client.player.experienceLevel + 1, 30);
        }
        rebuild();
    }

    private void rebuild() {
        clearChildren();
        MinecraftClient client = this.client;
        if (client == null || client.player == null) return;

        int center = width / 2;
        int top = Math.max(30, height / 2 - 95);

        long currentXp = XPCalculatorClient.currentXp(client);
        long targetXp = XPCalculatorClient.xpAtStartOfLevel(targetLevel);
        long needed = Math.max(0, targetXp - currentXp);
        long avgBottles = (needed + 6) / 7;
        long bestCaseBottles = (needed + 10) / 11;
        long worstCaseBottles = (needed + 2) / 3;

        ButtonWidget current = ButtonWidget.builder(
                Text.literal("Current: Level " + client.player.experienceLevel + " | ~" + currentXp + " XP"),
                b -> {}
        ).dimensions(center - 160, top, 320, 20).build();
        current.active = false;
        addDrawableChild(current);

        ButtonWidget target = ButtonWidget.builder(
                Text.literal("Target: Level " + targetLevel),
                b -> {}
        ).dimensions(center - 160, top + 28, 320, 20).build();
        target.active = false;
        addDrawableChild(target);

        ButtonWidget result = ButtonWidget.builder(
                Text.literal(needed == 0 ? "Already at/above target" : "XP needed: " + needed),
                b -> {}
        ).dimensions(center - 160, top + 56, 320, 20).build();
        result.active = false;
        addDrawableChild(result);

        ButtonWidget bottles = ButtonWidget.builder(
                Text.literal("XP bottles: ~" + avgBottles + " avg | " + bestCaseBottles + "-" + worstCaseBottles + " possible"),
                b -> {}
        ).dimensions(center - 160, top + 84, 320, 20).build();
        bottles.active = false;
        addDrawableChild(bottles);

        addDrawableChild(ButtonWidget.builder(Text.literal("-5"), b -> change(-5))
                .dimensions(center - 160, top + 116, 60, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("-1"), b -> change(-1))
                .dimensions(center - 95, top + 116, 60, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+1"), b -> change(1))
                .dimensions(center + 35, top + 116, 60, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+5"), b -> change(5))
                .dimensions(center + 100, top + 116, 60, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Level 30"), b -> setTarget(30))
                .dimensions(center - 160, top + 144, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Level 50"), b -> setTarget(50))
                .dimensions(center - 50, top + 144, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Level 100"), b -> setTarget(100))
                .dimensions(center + 60, top + 144, 100, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
                .dimensions(center - 50, top + 172, 100, 20).build());
    }

    private void change(int amount) {
        targetLevel = Math.max(0, targetLevel + amount);
        rebuild();
    }

    private void setTarget(int level) {
        targetLevel = level;
        rebuild();
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
