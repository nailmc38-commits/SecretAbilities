package com.secret.abilities;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class AbilityScreen extends Screen {
    private enum Tab {
        MOVEMENT("Movement"),
        VISION("Vision"),
        HUD("HUD"),
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

    private Tab currentTab = Tab.MOVEMENT;
    private int selectedStructure = 0;

    public AbilityScreen() {
        super(Text.literal("Secret Abilities"));
    }

    private static Text statusText(String name, boolean enabled) {
        return Text.literal(name + ": " + (enabled ? "ON" : "OFF"));
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        this.clearChildren();

        int tabWidth = 90;
        int tabGap = 4;
        int totalWidth = (tabWidth * Tab.values().length) + (tabGap * (Tab.values().length - 1));
        int startX = (this.width - totalWidth) / 2;
        int tabY = 42;

        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Tab tab = tabs[i];
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal((currentTab == tab ? "> " : "") + tab.label),
                    button -> {
                        currentTab = tab;
                        rebuild();
                    }
            ).dimensions(startX + i * (tabWidth + tabGap), tabY, tabWidth, 22).build());
        }

        int buttonWidth = 240;
        int buttonHeight = 24;
        int x = (this.width - buttonWidth) / 2;
        int y = 90;

        switch (currentTab) {
            case MOVEMENT -> addMovementTab(x, y, buttonWidth, buttonHeight);
            case VISION -> addVisionTab(x, y, buttonWidth, buttonHeight);
            case HUD -> addHudTab(x, y, buttonWidth, buttonHeight);
            case WORLD -> addWorldTab(x, y, buttonWidth, buttonHeight);
        }

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Close"),
                button -> this.close()
        ).dimensions((this.width - 120) / 2, this.height - 38, 120, 22).build());
    }

    private void addMovementTab(int x, int y, int width, int height) {
        this.addDrawableChild(ButtonWidget.builder(
                statusText("Walk on Water", ModState.walkOnWater),
                button -> {
                    ModState.walkOnWater = !ModState.walkOnWater;
                    button.setMessage(statusText("Walk on Water", ModState.walkOnWater));
                }
        ).dimensions(x, y, width, height).build());
    }

    private void addVisionTab(int x, int y, int width, int height) {
        this.addDrawableChild(ButtonWidget.builder(
                statusText("X-Ray", ModState.xray),
                button -> {
                    ModState.xray = !ModState.xray;
                    button.setMessage(statusText("X-Ray", ModState.xray));
                    SecretAbilitiesClient.refreshXray();
                }
        ).dimensions(x, y, width, height).build());

        this.addDrawableChild(ButtonWidget.builder(
                statusText("Player ESP", ModState.playerEsp),
                button -> {
                    ModState.playerEsp = !ModState.playerEsp;
                    button.setMessage(statusText("Player ESP", ModState.playerEsp));
                }
        ).dimensions(x, y + 32, width, height).build());
    }

    private void addHudTab(int x, int y, int width, int height) {
        this.addDrawableChild(ButtonWidget.builder(
                statusText("FPS / Ping HUD", ModState.statsHud),
                button -> {
                    ModState.statsHud = !ModState.statsHud;
                    button.setMessage(statusText("FPS / Ping HUD", ModState.statsHud));
                }
        ).dimensions(x, y, width, height).build());

        this.addDrawableChild(ButtonWidget.builder(
                statusText("Waypoint HUD", ModState.waypointHud),
                button -> {
                    ModState.waypointHud = !ModState.waypointHud;
                    button.setMessage(statusText("Waypoint HUD", ModState.waypointHud));
                }
        ).dimensions(x, y + 32, width, height).build());
    }

    private void addWorldTab(int x, int y, int width, int height) {
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Structure: " + STRUCTURE_NAMES[selectedStructure]),
                button -> {
                    selectedStructure = (selectedStructure + 1) % STRUCTURE_NAMES.length;
                    button.setMessage(Text.literal("Structure: " + STRUCTURE_NAMES[selectedStructure]));
                }
        ).dimensions(x, y, width, height).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Find Nearest Structure"),
                button -> {
                    if (this.client != null && this.client.getNetworkHandler() != null) {
                        this.client.getNetworkHandler().sendChatCommand(
                                "locate structure " + STRUCTURE_IDS[selectedStructure]
                        );
                        this.close();
                    }
                }
        ).dimensions(x, y + 32, width, height).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Add Waypoint Here (" + WaypointManager.count() + " saved)"),
                button -> {
                    if (this.client != null) {
                        WaypointManager.Waypoint waypoint = WaypointManager.addCurrent(this.client);
                        if (waypoint != null) {
                            button.setMessage(Text.literal("Saved " + waypoint.name()));
                        }
                    }
                }
        ).dimensions(x, y + 76, width, height).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Clear All Waypoints"),
                button -> {
                    WaypointManager.clear();
                    button.setMessage(Text.literal("Waypoints Cleared"));
                }
        ).dimensions(x, y + 108, width, height).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(
                this.textRenderer,
                this.title,
                this.width / 2,
                16,
                0xFFFFFF
        );

        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("F9 menu"),
                this.width / 2,
                28,
                0xAAAAAA
        );

        if (currentTab == Tab.WORLD) {
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal("Structure Finder uses /locate and needs permission."),
                    this.width / 2,
                    220,
                    0xAAAAAA
            );
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal("Nether/End structures must be searched in that dimension."),
                    this.width / 2,
                    232,
                    0x888888
            );
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
