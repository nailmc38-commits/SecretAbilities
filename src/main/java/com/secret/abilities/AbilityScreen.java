package com.secret.abilities;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public class AbilityScreen extends Screen {
    private static final String[] STRUCTURE_NAMES = {
            "Village", "Stronghold", "Trial Chambers", "Ancient City", "Woodland Mansion",
            "Ocean Monument", "Nether Fortress", "Bastion", "End City"
    };

    private static final String[] STRUCTURE_IDS = {
            "#minecraft:village", "minecraft:stronghold", "minecraft:trial_chambers",
            "minecraft:ancient_city", "minecraft:mansion", "minecraft:monument",
            "minecraft:fortress", "minecraft:bastion_remnant", "minecraft:end_city"
    };

    private ModuleCategory currentTab = ModuleCategory.BOT;
    private int selectedStructure;

    public AbilityScreen() {
        super(Text.literal("Void Client"));
    }

    @Override
    protected void init() {
        rebuild();
    }

    private Text moduleText(ClientModule module) {
        return Text.literal(module.name + "   ")
                .append(Text.literal(module.isEnabled() ? "ON" : "OFF")
                        .formatted(module.isEnabled() ? Formatting.GREEN : Formatting.RED));
    }

    private void rebuild() {
        this.clearChildren();

        int panelWidth = Math.min(830, this.width - 20);
        int panelHeight = Math.min(440, this.height - 20);
        int left = (this.width - panelWidth) / 2;
        int top = Math.max(10, (this.height - panelHeight) / 2);
        int contentLeft = left + 20;
        int contentWidth = panelWidth - 40;

        ModuleCategory[] tabs = ModuleCategory.values();
        int tabGap = 3;
        int tabWidth = (contentWidth - tabGap * (tabs.length - 1)) / tabs.length;
        int tabY = top + 47;

        for (int i = 0; i < tabs.length; i++) {
            ModuleCategory tab = tabs[i];
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal(tab.label).formatted(currentTab == tab ? Formatting.AQUA : Formatting.GRAY),
                    button -> {
                        currentTab = tab;
                        rebuild();
                    }
            ).dimensions(contentLeft + i * (tabWidth + tabGap), tabY, tabWidth, 23).build());
        }

        int contentTop = top + 113;

        if (currentTab == ModuleCategory.MACROS) addMacrosTab(contentLeft, contentTop, contentWidth);
        else addModuleGrid(currentTab, contentLeft, contentTop, contentWidth);

        addSpecialControls(currentTab, contentLeft, contentTop, contentWidth);

        int footerY = top + panelHeight - 35;

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("KEYBINDS").formatted(Formatting.AQUA),
                button -> {
                    if (this.client != null) this.client.setScreen(new KeybindsScreen(this, this.client.options));
                }
        ).dimensions(contentLeft, footerY, 110, 23).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("DISABLE ALL"),
                button -> {
                    ModuleRegistry.disableAll();
                    rebuild();
                }
        ).dimensions(contentLeft + 116, footerY, 110, 23).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("CLOSE"),
                button -> this.close()
        ).dimensions(left + panelWidth - 130, footerY, 110, 23).build());
    }

    private void addModuleGrid(ModuleCategory category, int x, int y, int width) {
        List<ClientModule> modules = ModuleRegistry.byCategory(category);
        int gap = 8;
        int columnWidth = (width - gap) / 2;

        for (int i = 0; i < modules.size(); i++) {
            ClientModule module = modules.get(i);
            int column = i / 5;
            int row = i % 5;
            if (column > 1) {
                column = 1;
                row = i - 5;
            }

            int bx = x + column * (columnWidth + gap);
            int by = y + row * 30;

            this.addDrawableChild(ButtonWidget.builder(
                    moduleText(module),
                    button -> {
                        ModuleRegistry.toggle(module.id);
                        button.setMessage(moduleText(module));
                        if ("panic".equals(module.id)) rebuild();
                    }
            ).dimensions(bx, by, columnWidth, 24).build());
        }
    }

    private void addSpecialControls(ModuleCategory category, int x, int y, int width) {
        int specialY = y + 160;
        int half = (width - 8) / 2;

        if (category == ModuleCategory.BOT) {
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Mine Target: minecraft:" + RuntimeSettings.mineTarget()),
                    button -> {
                        RuntimeSettings.nextMineTarget();
                        button.setMessage(Text.literal("Mine Target: minecraft:" + RuntimeSettings.mineTarget()));
                    }
            ).dimensions(x, specialY, half, 24).build());

            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal(BaritoneBridge.isAvailable() ? "Baritone: DETECTED" : "Baritone: NOT DETECTED")
                            .formatted(BaritoneBridge.isAvailable() ? Formatting.GREEN : Formatting.YELLOW),
                    button -> {}
            ).dimensions(x + half + 8, specialY, half, 24).build());

            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Restart Auto Mine"),
                    button -> VoidClientRuntime.restartAutoMine()
            ).dimensions(x, specialY + 30, half, 24).build());

            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Go To Nearest Waypoint"),
                    button -> VoidClientRuntime.pathToNearestWaypoint()
            ).dimensions(x + half + 8, specialY + 30, half, 24).build());
        }

        if (category == ModuleCategory.MOVEMENT) {
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal(String.format("Speed: %.2fx", RuntimeSettings.speedMultiplier)),
                    button -> {
                        RuntimeSettings.nextSpeed();
                        button.setMessage(Text.literal(String.format("Speed: %.2fx", RuntimeSettings.speedMultiplier)));
                    }
            ).dimensions(x, specialY, half, 24).build());

            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal(String.format("Flight Speed: %.2f", RuntimeSettings.flightSpeed)),
                    button -> {
                        RuntimeSettings.nextFlightSpeed();
                        button.setMessage(Text.literal(String.format("Flight Speed: %.2f", RuntimeSettings.flightSpeed)));
                    }
            ).dimensions(x + half + 8, specialY, half, 24).build());
        }

        if (category == ModuleCategory.WORLD) {
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Structure: " + STRUCTURE_NAMES[selectedStructure]),
                    button -> {
                        selectedStructure = (selectedStructure + 1) % STRUCTURE_NAMES.length;
                        button.setMessage(Text.literal("Structure: " + STRUCTURE_NAMES[selectedStructure]));
                    }
            ).dimensions(x, specialY, half, 24).build());

            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Locate Structure"),
                    button -> {
                        if (this.client != null && this.client.getNetworkHandler() != null
                                && ModuleRegistry.isEnabled("structure_finder")) {
                            this.client.getNetworkHandler().sendChatCommand(
                                    "locate structure " + STRUCTURE_IDS[selectedStructure]);
                            this.close();
                        }
                    }
            ).dimensions(x + half + 8, specialY, half, 24).build());

            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Add Waypoint (" + WaypointManager.count() + ")"),
                    button -> {
                        if (this.client != null) {
                            WaypointManager.addCurrent(this.client);
                            button.setMessage(Text.literal("Add Waypoint (" + WaypointManager.count() + ")"));
                        }
                    }
            ).dimensions(x, specialY + 30, half, 24).build());

            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Clear Waypoints"),
                    button -> {
                        WaypointManager.clear();
                        button.setMessage(Text.literal("Waypoints Cleared").formatted(Formatting.YELLOW));
                    }
            ).dimensions(x + half + 8, specialY + 30, half, 24).build());
        }

        if (category == ModuleCategory.CLIENT) {
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Open Minecraft Keybinds"),
                    button -> {
                        if (this.client != null) this.client.setScreen(new KeybindsScreen(this, this.client.options));
                    }
            ).dimensions(x, specialY, half, 24).build());

            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Enabled Modules: " + ModuleRegistry.enabledCount()),
                    button -> {}
            ).dimensions(x + half + 8, specialY, half, 24).build());
        }
    }

    private void addMacrosTab(int x, int y, int width) {
        List<MacroManager.Macro> macros = MacroManager.all();
        int gap = 8;
        int columnWidth = (width - gap) / 2;
        int shown = Math.min(10, macros.size());

        for (int i = 0; i < shown; i++) {
            int index = i;
            MacroManager.Macro macro = macros.get(i);
            int column = i / 5;
            int row = i % 5;

            this.addDrawableChild(ButtonWidget.builder(
                    macroText(macro),
                    button -> {
                        MacroManager.toggle(index);
                        button.setMessage(macroText(MacroManager.all().get(index)));
                    }
            ).dimensions(x + column * (columnWidth + gap), y + row * 30, columnWidth, 24).build());
        }

        int specialY = y + 160;
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("CREATE / EDIT MACROS").formatted(Formatting.AQUA),
                button -> {
                    if (this.client != null) this.client.setScreen(new MacroEditorScreen(this));
                }
        ).dimensions(x, specialY, width, 26).build());
    }

    private Text macroText(MacroManager.Macro macro) {
        return Text.literal(macro.name + " [" + macro.key + "]   ")
                .append(Text.literal(macro.enabled ? "ON" : "OFF")
                        .formatted(macro.enabled ? Formatting.GREEN : Formatting.RED));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        int panelWidth = Math.min(830, this.width - 20);
        int panelHeight = Math.min(440, this.height - 20);
        int left = (this.width - panelWidth) / 2;
        int top = Math.max(10, (this.height - panelHeight) / 2);
        int right = left + panelWidth;
        int bottom = top + panelHeight;

        context.fill(left + 4, top + 5, right + 4, bottom + 5, 0x70000000);
        context.fill(left, top, right, bottom, 0xF011151C);
        context.fill(left, top, right, top + 3, 0xFF35E8FF);
        context.fill(left, top, right, top + 40, 0xFF171D26);
        context.fill(left + 18, top + 78, right - 18, top + 79, 0x5535E8FF);

        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("VOID").formatted(Formatting.WHITE)
                        .append(Text.literal(" CLIENT").formatted(Formatting.AQUA)),
                left + 20, top + 15, 0xFFFFFF);

        context.drawTextWithShadow(this.textRenderer, Text.literal("Fabric 1.21.11"),
                right - 104, top + 15, 0xFF8995A6);

        context.drawTextWithShadow(this.textRenderer,
                Text.literal(currentTab.label.toUpperCase()).formatted(Formatting.AQUA),
                left + 20, top + 84, 0xFFFFFF);

        String subtitle = switch (currentTab) {
            case BOT -> "Automation, pathing and Beat Game controls.";
            case PVP -> "Combat information and inventory helpers.";
            case MOVEMENT -> "Movement modules. Server-authoritative movement may be corrected.";
            case RENDER -> "Client-side visual modules.";
            case WORLD -> "Waypoints, structures and world utilities.";
            case MACROS -> "Custom toggleable macros with mouse, commands, movement and delays.";
            case PLAYER -> "Inventory and player utility modules.";
            case HUD -> "Toggleable on-screen information.";
            case CLIENT -> "Void Client profiles, themes, keybinds and safety controls.";
        };

        context.drawTextWithShadow(this.textRenderer, Text.literal(subtitle),
                left + 20, top + 98, 0xFF8E99A8);

        if (currentTab == ModuleCategory.BOT) {
            context.drawTextWithShadow(this.textRenderer,
                    Text.literal("Status: " + VoidClientRuntime.botStatus()),
                    left + 20, top + 326, 0xFF93A0B0);
        }

        if (currentTab == ModuleCategory.MACROS) {
            context.drawTextWithShadow(this.textRenderer,
                    Text.literal("Running: " + MacroManager.runningName()),
                    left + 20, top + 326, 0xFF93A0B0);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
