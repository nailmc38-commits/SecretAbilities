package com.secret.tradecycler;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class TradeCyclerScreen extends Screen {
    private static final String[] ENCHANTMENTS = {
            "minecraft:mending",
            "minecraft:unbreaking",
            "minecraft:protection",
            "minecraft:sharpness",
            "minecraft:efficiency",
            "minecraft:fortune",
            "minecraft:silk_touch",
            "minecraft:power"
    };

    private static final int[] PRICES = {10, 15, 18, 20, 24, 32, 48, 64};
    private static final int[] DELAYS = {250, 400, 650, 1000, 1500, 2000};

    private final Screen parent;
    private final TradeCyclerConfig config;

    public TradeCyclerScreen(Screen parent, TradeCyclerConfig config) {
        super(Text.literal("TradeCycler Settings"));
        this.parent = parent;
        this.config = config;
    }

    @Override
    protected void init() {
        int center = this.width / 2;
        int y = Math.max(35, this.height / 2 - 75);

        addDrawableChild(ButtonWidget.builder(enchantmentText(), button -> {
            cycleEnchantment();
            button.setMessage(enchantmentText());
        }).dimensions(center - 110, y, 220, 20).build());

        addDrawableChild(ButtonWidget.builder(levelText(), button -> {
            config.minimumLevel = config.minimumLevel >= 5 ? 1 : config.minimumLevel + 1;
            button.setMessage(levelText());
        }).dimensions(center - 110, y + 26, 220, 20).build());

        addDrawableChild(ButtonWidget.builder(priceText(), button -> {
            config.maxEmeraldPrice = nextValue(PRICES, config.maxEmeraldPrice);
            button.setMessage(priceText());
        }).dimensions(center - 110, y + 52, 220, 20).build());

        addDrawableChild(ButtonWidget.builder(delayText(), button -> {
            config.delayMs = nextValue(DELAYS, config.delayMs);
            button.setMessage(delayText());
        }).dimensions(center - 110, y + 78, 220, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Save & Close"), button -> {
            config.save();
            closeToParent();
        }).dimensions(center - 110, y + 112, 106, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> closeToParent())
                .dimensions(center + 4, y + 112, 106, 20).build());
    }

    private Text enchantmentText() {
        return Text.literal("Enchantment: " + shortName(config.enchantment));
    }

    private Text levelText() {
        return Text.literal("Minimum level: " + config.minimumLevel);
    }

    private Text priceText() {
        return Text.literal("Maximum price: " + config.maxEmeraldPrice + " emeralds");
    }

    private Text delayText() {
        return Text.literal("Delay: " + config.delayMs + " ms");
    }

    private void cycleEnchantment() {
        String current = config.enchantment;
        for (int i = 0; i < ENCHANTMENTS.length; i++) {
            if (ENCHANTMENTS[i].equals(current)) {
                config.enchantment = ENCHANTMENTS[(i + 1) % ENCHANTMENTS.length];
                return;
            }
        }
        config.enchantment = ENCHANTMENTS[0];
    }

    private int nextValue(int[] values, int current) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) return values[(i + 1) % values.length];
            if (values[i] > current) return values[i];
        }
        return values[0];
    }

    private String shortName(String id) {
        return id != null && id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : String.valueOf(id);
    }

    private void closeToParent() {
        if (this.client != null) this.client.setScreen(parent);
    }

    @Override
    public void close() {
        closeToParent();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
