package com.survivalutils;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class SurvivalUtilsScreen extends Screen {
    private Feature.Category category = Feature.Category.HUD;
    private int scrollOffset;

    private int panelLeft;
    private int panelTop;
    private int panelRight;
    private int panelBottom;
    private int sidebarWidth = 126;
    private int contentTop;
    private int contentBottom;

    public SurvivalUtilsScreen() {
        super(Text.literal("Survival Utils V2"));
    }

    @Override
    protected void init() {
        panelLeft = Math.max(8, (width - Math.min(680, width - 16)) / 2);
        panelRight = width - panelLeft;
        panelTop = 12;
        panelBottom = height - 12;
        contentTop = panelTop + 42;
        contentBottom = panelBottom - 12;
        rebuild();
    }

    private void rebuild() {
        clearChildren();

        int sidebarX = panelLeft + 10;
        int y = contentTop;

        for (Feature.Category c : Feature.Category.values()) {
            String label = (c == category ? "> " : "") + c.title;
            addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> {
                category = c;
                scrollOffset = 0;
                rebuild();
            }).dimensions(sidebarX, y, sidebarWidth - 18, 22).build());
            y += 26;
        }

        if (category == Feature.Category.SETTINGS) {
            buildSettings();
        } else {
            buildFeatures();
        }
    }

    private void buildFeatures() {
        List<Feature> list = featuresFor(category);
        int x = panelLeft + sidebarWidth + 12;
        int contentWidth = panelRight - x - 12;
        int rowHeight = 48;

        for (int i = 0; i < list.size(); i++) {
            Feature feature = list.get(i);
            int y = contentTop + i * rowHeight - scrollOffset;
            if (y + 36 < contentTop || y > contentBottom) continue;

            boolean on = SurvivalUtilsClient.CONFIG.isEnabled(feature);
            addDrawableChild(ButtonWidget.builder(
                    Text.literal(on ? "ON" : "OFF"),
                    b -> {
                        SurvivalUtilsClient.CONFIG.toggle(feature);
                        rebuild();
                    }
            ).dimensions(x + contentWidth - 58, y + 4, 50, 22).build());
        }
    }

    private void buildSettings() {
        int x = panelLeft + sidebarWidth + 12;
        int contentWidth = panelRight - x - 12;
        int y = contentTop - scrollOffset;

        y = settingButton(x, y, contentWidth,
                "Main HUD side: " + (SurvivalUtilsClient.CONFIG.hudRight ? "RIGHT" : "LEFT"),
                () -> {
                    SurvivalUtilsClient.CONFIG.hudRight = !SurvivalUtilsClient.CONFIG.hudRight;
                    SurvivalUtilsClient.CONFIG.save();
                });

        y = settingButton(x, y, contentWidth,
                "Warning banner: " + onOff(SurvivalUtilsClient.CONFIG.warningBanner),
                () -> {
                    SurvivalUtilsClient.CONFIG.warningBanner = !SurvivalUtilsClient.CONFIG.warningBanner;
                    SurvivalUtilsClient.CONFIG.save();
                });

        y = settingButton(x, y, contentWidth,
                "Actionbar warnings: " + onOff(SurvivalUtilsClient.CONFIG.warningActionbar),
                () -> {
                    SurvivalUtilsClient.CONFIG.warningActionbar = !SurvivalUtilsClient.CONFIG.warningActionbar;
                    SurvivalUtilsClient.CONFIG.save();
                });

        y = settingButton(x, y, contentWidth,
                "Player radar radius: " + SurvivalUtilsClient.CONFIG.playerRadarRadius + "m",
                () -> {
                    int r = SurvivalUtilsClient.CONFIG.playerRadarRadius;
                    SurvivalUtilsClient.CONFIG.playerRadarRadius =
                            r < 16 ? 16 : r < 24 ? 24 : r < 32 ? 32 : r < 48 ? 48 : r < 64 ? 64 : 16;
                    SurvivalUtilsClient.CONFIG.save();
                });

        y = settingButton(x, y, contentWidth,
                "Threat scan radius: " + (int)SurvivalUtilsClient.CONFIG.threatRadius + "m",
                () -> {
                    int r = (int)SurvivalUtilsClient.CONFIG.threatRadius;
                    SurvivalUtilsClient.CONFIG.threatRadius =
                            r < 12 ? 12 : r < 18 ? 18 : r < 24 ? 24 : r < 32 ? 32 : 12;
                    SurvivalUtilsClient.CONFIG.save();
                });

        y = settingButton(x, y, contentWidth,
                "Water clutch trigger: " + SurvivalUtilsClient.CONFIG.clutchTriggerBlocks + " blocks",
                () -> {
                    int r = SurvivalUtilsClient.CONFIG.clutchTriggerBlocks;
                    SurvivalUtilsClient.CONFIG.clutchTriggerBlocks = r <= 2 ? 3 : r == 3 ? 4 : 2;
                    SurvivalUtilsClient.CONFIG.save();
                });

        y = settingButton(x, y, contentWidth,
                "Warning cooldown: " + SurvivalUtilsClient.CONFIG.warningCooldownSeconds + "s",
                () -> {
                    int r = SurvivalUtilsClient.CONFIG.warningCooldownSeconds;
                    SurvivalUtilsClient.CONFIG.warningCooldownSeconds =
                            r < 3 ? 3 : r < 5 ? 5 : r < 7 ? 7 : r < 10 ? 10 : 3;
                    SurvivalUtilsClient.CONFIG.save();
                });

        y += 10;

        y = settingButton(x, y, contentWidth,
                "RESET SESSION STATS",
                () -> SurvivalUtilsClient.STATS.reset());

        y = settingButton(x, y, contentWidth,
                "RESET FEATURE DEFAULTS",
                () -> {
                    SurvivalUtilsClient.CONFIG.resetDefaults();
                    SurvivalUtilsClient.CONFIG.save();
                });

        y += 10;

        settingButton(x, y, contentWidth,
                "SEEDCRACKERX 2.15.6 BUNDLED // command: /seedcracker gui",
                () -> {
                    if (client != null && client.player != null) {
                        client.player.sendMessage(
                                Text.literal("SeedCrackerX is bundled. Use /seedcracker gui")
                                        .formatted(Formatting.AQUA),
                                false);
                    }
                });
    }

    private int settingButton(int x, int y, int width, String text, Runnable action) {
        if (y + 28 >= contentTop && y <= contentBottom) {
            addDrawableChild(ButtonWidget.builder(Text.literal(text), b -> {
                action.run();
                rebuild();
            }).dimensions(x, y, width, 24).build());
        }
        return y + 32;
    }

    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }

    private List<Feature> featuresFor(Feature.Category c) {
        List<Feature> list = new ArrayList<>();
        for (Feature feature : Feature.values()) {
            if (feature.category == c) list.add(feature);
        }
        return list;
    }

    private int maxScroll() {
        if (category == Feature.Category.SETTINGS) {
            int contentHeight = 11 * 32 + 40;
            return Math.max(0, contentHeight - (contentBottom - contentTop));
        }

        int rows = featuresFor(category).size();
        int contentHeight = rows * 48;
        return Math.max(0, contentHeight - (contentBottom - contentTop));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX < panelLeft + sidebarWidth || mouseX > panelRight
                || mouseY < contentTop || mouseY > contentBottom) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        int old = scrollOffset;
        scrollOffset = Math.max(0,
                Math.min(maxScroll(), scrollOffset - (int)Math.round(verticalAmount * 30.0)));

        if (scrollOffset != old) {
            rebuild();
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xE905090D);
        ctx.fill(panelLeft, panelTop, panelRight, panelBottom, 0xE00A1118);
        ctx.fill(panelLeft, panelTop, panelRight, panelTop + 2, 0xFF4CE8E2);
        ctx.fill(panelLeft + sidebarWidth, contentTop - 5, panelLeft + sidebarWidth + 1, panelBottom - 8, 0x554CE8E2);

        ctx.drawTextWithShadow(
                textRenderer,
                Text.literal("SURVIVAL // UTILS V2").formatted(Formatting.AQUA, Formatting.BOLD),
                panelLeft + 12,
                panelTop + 10,
                0xFFEAFBFF);

        ctx.drawTextWithShadow(
                textRenderer,
                "NO AI // F9 TO CLOSE",
                panelRight - textRenderer.getWidth("NO AI // F9 TO CLOSE") - 12,
                panelTop + 11,
                0xFF7E919C);

        if (category != Feature.Category.SETTINGS) {
            renderFeatureDescriptions(ctx);
        } else {
            ctx.drawTextWithShadow(
                    textRenderer,
                    "SETTINGS // SCROLL FOR MORE",
                    panelLeft + sidebarWidth + 14,
                    panelTop + 30,
                    0xFF9FB4C0);
        }

        renderScrollbar(ctx);
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void renderFeatureDescriptions(DrawContext ctx) {
        List<Feature> list = featuresFor(category);
        int x = panelLeft + sidebarWidth + 16;
        int contentWidth = panelRight - x - 12;
        int rowHeight = 48;

        ctx.drawTextWithShadow(
                textRenderer,
                category.title.toUpperCase() + " // " + SurvivalUtilsClient.CONFIG.enabledCount(category)
                        + "/" + list.size() + " ON",
                x,
                panelTop + 30,
                0xFF9FB4C0);

        for (int i = 0; i < list.size(); i++) {
            Feature feature = list.get(i);
            int y = contentTop + i * rowHeight - scrollOffset;
            if (y + 36 < contentTop || y > contentBottom) continue;

            boolean on = SurvivalUtilsClient.CONFIG.isEnabled(feature);
            int edge = on ? 0xFF4CE8E2 : 0xFF263B46;

            ctx.fill(x - 4, y, x + contentWidth, y + 40, 0x55101A22);
            ctx.fill(x - 4, y, x - 2, y + 40, edge);

            ctx.drawTextWithShadow(
                    textRenderer,
                    feature.title,
                    x + 4,
                    y + 6,
                    on ? 0xFFE7F8FC : 0xFF87959E);

            ctx.drawTextWithShadow(
                    textRenderer,
                    trim(feature.description, 52),
                    x + 4,
                    y + 22,
                    0xFF788A95);
        }
    }

    private void renderScrollbar(DrawContext ctx) {
        int max = maxScroll();
        if (max <= 0) return;

        int trackTop = contentTop;
        int trackBottom = contentBottom;
        int trackH = Math.max(20, trackBottom - trackTop);
        int thumbH = Math.max(24, (int)(trackH * (trackH / (double)(trackH + max))));
        int thumbY = trackTop + (int)((trackH - thumbH) * (scrollOffset / (double)max));

        ctx.fill(panelRight - 7, trackTop, panelRight - 5, trackBottom, 0xFF1D2B34);
        ctx.fill(panelRight - 8, thumbY, panelRight - 4, thumbY + thumbH, 0xFF55E8E2);
    }

    private static String trim(String value, int max) {
        if (value == null || value.length() <= max) return value == null ? "" : value;
        return value.substring(0, Math.max(0, max - 1)) + "…";
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_F9 || input.key() == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
