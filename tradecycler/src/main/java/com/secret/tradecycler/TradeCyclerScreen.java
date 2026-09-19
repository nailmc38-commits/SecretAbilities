package com.secret.tradecycler;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class TradeCyclerScreen extends Screen {
    private final Screen parent;
    private final TradeCyclerConfig config;

    private TextFieldWidget enchantmentField;
    private TextFieldWidget levelField;
    private TextFieldWidget priceField;
    private TextFieldWidget delayField;

    public TradeCyclerScreen(Screen parent, TradeCyclerConfig config) {
        super(Text.literal("TradeCycler Settings"));
        this.parent = parent;
        this.config = config;
    }

    @Override
    protected void init() {
        int center = this.width / 2;
        int fieldX = center - 90;
        int y = this.height / 2 - 78;

        enchantmentField = new TextFieldWidget(this.textRenderer, fieldX, y, 180, 20, Text.literal("Target enchantment"));
        enchantmentField.setMaxLength(64);
        enchantmentField.setText(config.enchantment);
        this.addDrawableChild(enchantmentField);

        levelField = new TextFieldWidget(this.textRenderer, fieldX, y + 38, 180, 20, Text.literal("Minimum level"));
        levelField.setMaxLength(2);
        levelField.setText(Integer.toString(config.minimumLevel));
        this.addDrawableChild(levelField);

        priceField = new TextFieldWidget(this.textRenderer, fieldX, y + 76, 180, 20, Text.literal("Maximum emerald price"));
        priceField.setMaxLength(2);
        priceField.setText(Integer.toString(config.maxEmeraldPrice));
        this.addDrawableChild(priceField);

        delayField = new TextFieldWidget(this.textRenderer, fieldX, y + 114, 180, 20, Text.literal("Delay milliseconds"));
        delayField.setMaxLength(4);
        delayField.setText(Integer.toString(config.delayMs));
        this.addDrawableChild(delayField);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Save & Close"), button -> saveAndClose())
                .dimensions(center - 90, y + 148, 88, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> closeToParent())
                .dimensions(center + 2, y + 148, 88, 20).build());
    }

    private void saveAndClose() {
        config.enchantment = enchantmentField.getText();
        config.minimumLevel = parseInt(levelField.getText(), config.minimumLevel);
        config.maxEmeraldPrice = parseInt(priceField.getText(), config.maxEmeraldPrice);
        config.delayMs = parseInt(delayField.getText(), config.delayMs);
        config.save();
        closeToParent();
    }

    private int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private void closeToParent() {
        if (this.client != null) this.client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        this.renderBackground(context, mouseX, mouseY, deltaTicks);
        int center = this.width / 2;
        int y = this.height / 2 - 92;
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, center, y - 18, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, "Enchantment (example: minecraft:mending)", center - 90, y, 0xCFCFCF);
        context.drawTextWithShadow(this.textRenderer, "Minimum level", center - 90, y + 38, 0xCFCFCF);
        context.drawTextWithShadow(this.textRenderer, "Maximum emerald price", center - 90, y + 76, 0xCFCFCF);
        context.drawTextWithShadow(this.textRenderer, "Reroll delay (ms)", center - 90, y + 114, 0xCFCFCF);
        super.render(context, mouseX, mouseY, deltaTicks);
    }
}
