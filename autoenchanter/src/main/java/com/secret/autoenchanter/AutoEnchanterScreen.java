package com.secret.autoenchanter;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AutoEnchanterScreen extends Screen {
    public record SelectedEnchant(String id, int level) {}

    private static final int PER_PAGE = 8;

    private final Screen parent;
    private final String itemId;
    private final List<AutoEnchanterClient.EnchantChoice> choices;
    private final Map<String, Integer> selected = new LinkedHashMap<>();
    private int page;

    public AutoEnchanterScreen(Screen parent, String itemId, List<AutoEnchanterClient.EnchantChoice> choices) {
        super(Text.literal("AutoEnchanter"));
        this.parent = parent;
        this.itemId = itemId;
        this.choices = choices;
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        clearChildren();

        int center = width / 2;
        int top = Math.max(24, height / 2 - 104);

        ButtonWidget target = ButtonWidget.builder(
                Text.literal("Target: " + shortItemName(itemId)),
                button -> {}
        ).dimensions(center - 155, top, 310, 20).build();
        target.active = false;
        addDrawableChild(target);

        int startIndex = page * PER_PAGE;
        int endIndex = Math.min(choices.size(), startIndex + PER_PAGE);

        for (int i = startIndex; i < endIndex; i++) {
            int local = i - startIndex;
            int col = local % 2;
            int row = local / 2;
            AutoEnchanterClient.EnchantChoice choice = choices.get(i);

            int x = center - 155 + col * 157;
            int y = top + 28 + row * 26;

            addDrawableChild(ButtonWidget.builder(labelFor(choice), button -> {
                cycle(choice);
                button.setMessage(labelFor(choice));
            }).dimensions(x, y, 153, 20).build());
        }

        int navY = top + 28 + 4 * 26;
        if (page > 0) {
            addDrawableChild(ButtonWidget.builder(Text.literal("< Previous"), button -> {
                page--;
                rebuild();
            }).dimensions(center - 155, navY, 98, 20).build());
        }

        if ((page + 1) * PER_PAGE < choices.size()) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Next >"), button -> {
                page++;
                rebuild();
            }).dimensions(center + 57, navY, 98, 20).build());
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Clear"), button -> {
            selected.clear();
            rebuild();
        }).dimensions(center - 51, navY, 102, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("START AUTO"), button -> start())
                .dimensions(center - 155, navY + 28, 153, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> closeToParent())
                .dimensions(center + 2, navY + 28, 153, 20).build());
    }

    private void cycle(AutoEnchanterClient.EnchantChoice choice) {
        int current = selected.getOrDefault(choice.id(), 0);
        int next = current >= choice.maxLevel() ? 0 : current + 1;

        if (next > 0) {
            List<String> conflicts = new ArrayList<>();
            for (String existing : selected.keySet()) {
                if (selected.getOrDefault(existing, 0) > 0
                        && AutoEnchanterClient.conflicts(choice.id(), existing)) {
                    conflicts.add(existing);
                }
            }
            for (String conflict : conflicts) {
                selected.remove(conflict);
            }
            selected.put(choice.id(), next);
        } else {
            selected.remove(choice.id());
        }

        rebuild();
    }

    private Text labelFor(AutoEnchanterClient.EnchantChoice choice) {
        int level = selected.getOrDefault(choice.id(), 0);
        return Text.literal(choice.label() + ": " + (level == 0 ? "OFF" : roman(level)));
    }

    private void start() {
        MinecraftClient client = this.client;
        if (client == null) return;

        List<SelectedEnchant> values = new ArrayList<>();
        for (AutoEnchanterClient.EnchantChoice choice : choices) {
            int level = selected.getOrDefault(choice.id(), 0);
            if (level > 0) values.add(new SelectedEnchant(choice.id(), level));
        }

        AutoEnchanterClient.startAuto(client, itemId, values);
    }

    private String roman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> Integer.toString(level);
        };
    }

    private String shortItemName(String id) {
        String out = id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
        return out.replace('_', ' ');
    }

    private void closeToParent() {
        if (client != null) client.setScreen(parent);
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
