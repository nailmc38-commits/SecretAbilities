package com.secret.abilities;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.function.BooleanSupplier;

public class AbilityScreen extends Screen {
    private enum Tab {
        BOT("Bot"),
        PVP("PvP"),
        MOVEMENT("Movement"),
        RENDER("Render"),
        WORLD("World");

        private final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private static final String[] STRUCTURE_NAMES = {
            "Village",
            "Stronghold",
            "Trial Chambers",
            "Ancient City",
            "Woodland Mansion",
            "Ocean Monument",
            "Nether Fortress",
            "Bastion",
            "End City"
    };

    private static final String[] STRUCTURE_IDS = {
            "#minecraft:village",
            "minecraft:stronghold",
            "minecraft:trial_chambers",
            "minecraft:ancient_city",
            "minecraft:mansion",
            "minecraft:monument",
            "minecraft:fortress",
            "minecraft:bastion_remnant",
            "minecraft:end_city"
    };

    private Tab currentTab = Tab.BOT;
    private int selectedStructure = 0;

    public AbilityScreen() {
        super(Text.literal("Secret Client"));
    }

    @Override
    protected void init() {
        rebuild();
    }

    private Text toggleText(String name, boolean enabled) {
        return Text.literal(name + "   ")
                .append(Text.literal(enabled ? "ON" : "OFF")
                        .formatted(enabled ? Formatting.GREEN : Formatting.RED));
    }

    private void addToggle(
            String name,
            BooleanSupplier state,
            Runnable toggle,
            int x,
            int y,
            int width
    ) {
        this.addDrawableChild(ButtonWidget.builder(
                toggleText(name, state.getAsBoolean()),
                button -> {
                    toggle.run();
                    button.setMessage(toggleText(name, state.getAsBoolean()));
                }
        ).dimensions(x, y, width, 26).build());
    }

    private void rebuild() {
        this.clearChildren();

        int panelWidth = Math.min(560, this.width - 30);
        int panelLeft = (this.width - panelWidth) / 2;
        int panelTop = Math.max(22, (this.height - 350) / 2);
        int contentLeft = panelLeft + 24;
        int contentTop = panelTop + 91;
        int contentWidth = panelWidth - 48;

        int tabGap = 5;
        int tabWidth = (contentWidth - tabGap * 4) / 5;
        int tabY = panelTop + 47;

        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Tab tab = tabs[i];
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal(tab.label).formatted(currentTab == tab ? Formatting.AQUA : Formatting.GRAY),
                    button -> {
                        currentTab = tab;
                        rebuild();
                    }
            ).dimensions(contentLeft + i * (tabWidth + tabGap), tabY, tabWidth, 24).build());
        }

        switch (currentTab) {
            case BOT -> addBotTab(contentLeft, contentTop, contentWidth);
            case PVP -> addPvpTab(contentLeft, contentTop, contentWidth);
            case MOVEMENT -> addMovementTab(contentLeft, contentTop, contentWidth);
            case RENDER -> addRenderTab(contentLeft, contentTop, contentWidth);
            case WORLD -> addWorldTab(contentLeft, contentTop, contentWidth);
        }

        int footerY = panelTop + 305;
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("KEYBINDS").formatted(Formatting.AQUA),
                button -> {
                    if (this.client != null) {
                        this.client.setScreen(new KeybindsScreen(this, this.client.options));
                    }
                }
        ).dimensions(contentLeft, footerY, 120, 24).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("CLOSE"),
                button -> this.close()
        ).dimensions(panelLeft + panelWidth - 144, footerY, 120, 24).build());
    }

    private void addBotTab(int x, int y, int width) {
        // /mine and other bot modules will live here later.
    }

    private void addPvpTab(int x, int y, int width) {
        // PvP modules will live here later.
    }

    private void addMovementTab(int x, int y, int width) {
        addToggle(
                "Walk on Water",
                () -> ModState.walkOnWater,
                () -> ModState.walkOnWater = !ModState.walkOnWater,
                x,
                y,
                width
        );
    }

    private void addRenderTab(int x, int y, int width) {
        addToggle(
                "X-Ray",
                () -> ModState.xray,
                () -> {
                    ModState.xray = !ModState.xray;
                    SecretAbilitiesClient.refreshXray();
                },
                x,
                y,
                width
        );

        addToggle(
                "Player ESP",
                () -> ModState.playerEsp,
                () -> ModState.playerEsp = !ModState.playerEsp,
                x,
                y + 34,
                width
        );

        addToggle(
                "FPS / Ping HUD",
                () -> ModState.statsHud,
                () -> ModState.statsHud = !ModState.statsHud,
                x,
                y + 68,
                width
        );
    }

    private void addWorldTab(int x, int y, int width) {
        addToggle(
                "Waypoint HUD",
                () -> ModState.waypointHud,
                () -> ModState.waypointHud = !ModState.waypointHud,
                x,
                y,
                width
        );

        int half = (width - 8) / 2;

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Structure: " + STRUCTURE_NAMES[selectedStructure]),
                button -> {
                    selectedStructure = (selectedStructure + 1) % STRUCTURE_NAMES.length;
                    button.setMessage(Text.literal("Structure: " + STRUCTURE_NAMES[selectedStructure]));
                }
        ).dimensions(x, y + 42, half, 26).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Find Nearest"),
                button -> {
                    if (this.client != null && this.client.getNetworkHandler() != null) {
                        this.client.getNetworkHandler().sendChatCommand(
                                "locate structure " + STRUCTURE_IDS[selectedStructure]
                        );
                        this.close();
                    }
                }
        ).dimensions(x + half + 8, y + 42, half, 26).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Add Waypoint Here"),
                button -> {
                    if (this.client != null) {
                        WaypointManager.Waypoint waypoint = WaypointManager.addCurrent(this.client);
                        if (waypoint != null) {
                            button.setMessage(Text.literal("Saved " + waypoint.name()).formatted(Formatting.GREEN));
                        }
                    }
                }
        ).dimensions(x, y + 76, half, 26).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Clear Waypoints"),
                button -> {
                    WaypointManager.clear();
                    button.setMessage(Text.literal("Waypoints Cleared").formatted(Formatting.YELLOW));
                }
        ).dimensions(x + half + 8, y + 76, half, 26).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        int panelWidth = Math.min(560, this.width - 30);
        int panelHeight = 350;
        int panelLeft = (this.width - panelWidth) / 2;
        int panelTop = Math.max(22, (this.height - panelHeight) / 2);
        int panelRight = panelLeft + panelWidth;
        int panelBottom = panelTop + panelHeight;

        // Shadow + main dark panel.
        context.fill(panelLeft + 4, panelTop + 5, panelRight + 4, panelBottom + 5, 0x70000000);
        context.fill(panelLeft, panelTop, panelRight, panelBottom, 0xF011151C);

        // Header and cyan accent.
        context.fill(panelLeft, panelTop, panelRight, panelTop + 40, 0xFF171D26);
        context.fill(panelLeft, panelTop, panelRight, panelTop + 3, 0xFF35E8FF);
        context.fill(panelLeft + 18, panelTop + 78, panelRight - 18, panelTop + 79, 0x5535E8FF);

        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("SECRET").formatted(Formatting.WHITE)
                        .append(Text.literal(" CLIENT").formatted(Formatting.AQUA)),
                panelLeft + 22,
                panelTop + 15,
                0xFFFFFF
        );

        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("Fabric 1.21.11"),
                panelRight - 102,
                panelTop + 15,
                0xFF8995A6
        );

        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal(currentTab.label.toUpperCase()),
                panelLeft + 24,
                panelTop + 83,
                0xFF35E8FF
        );

        String subtitle = switch (currentTab) {
            case BOT -> "Automation modules - /mine will live here later.";
            case PVP -> "PvP modules will live here.";
            case MOVEMENT -> "Movement abilities and mobility tools.";
            case RENDER -> "Visual modules and on-screen information.";
            case WORLD -> "Waypoints and world utilities.";
        };

        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal(subtitle),
                panelLeft + 24,
                panelTop + 102,
                0xFF8E99A8
        );

        if (currentTab == Tab.BOT) {
            drawEmptyCard(context, panelLeft, panelTop, panelWidth,
                    "BOT MODULES",
                    "Ready for /mine when you want to build it.");
        } else if (currentTab == Tab.PVP) {
            drawEmptyCard(context, panelLeft, panelTop, panelWidth,
                    "PVP MODULES",
                    "This tab is ready for your combat modules.");
        }

        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("Tip: use KEYBINDS to change every shortcut."),
                panelLeft + 154,
                panelTop + 313,
                0xFF778292
        );

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawEmptyCard(
            DrawContext context,
            int panelLeft,
            int panelTop,
            int panelWidth,
            String heading,
            String body
    ) {
        int left = panelLeft + 24;
        int right = panelLeft + panelWidth - 24;
        int top = panelTop + 125;
        int bottom = top + 78;

        context.fill(left, top, right, bottom, 0xAA171D26);
        context.fill(left, top, left + 3, bottom, 0xFF35E8FF);

        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal(heading).formatted(Formatting.AQUA),
                left + 14,
                top + 17,
                0xFFFFFF
        );

        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal(body),
                left + 14,
                top + 39,
                0xFF98A3B3
        );
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
