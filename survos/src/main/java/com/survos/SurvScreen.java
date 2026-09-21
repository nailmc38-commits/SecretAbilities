package com.survos;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.AbstractInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;

public final class SurvScreen extends Screen {
    public enum Tab { DASHBOARD, AI, TAKEOVER, AUTOMATION, TOOLS, HUD, REFILL, SETTINGS }

    private Tab tab;
    private int scrollOffset;
    private static final int CONTENT_TOP = 86;
    private int contentBottom;

    public SurvScreen(Tab tab) {
        super(Text.literal("SURV // OS"));
        this.tab = tab == null ? Tab.DASHBOARD : tab;
    }

    @Override
    protected void init() {
        contentBottom = Math.max(CONTENT_TOP + 120, height - 18);
        rebuild();
    }

    private void rebuild() {
        clearChildren();

        int panelW = Math.min(540, width - 28);
        int x = (width - panelW) / 2;
        int gap = 5;
        Tab[] tabs = Tab.values();
        int bw = (panelW - gap * (tabs.length - 1)) / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            addFixedButton(
                    x + i * (bw + gap),
                    46,
                    bw,
                    t == Tab.DASHBOARD ? "HOME" : t.name(),
                    () -> {
                        tab = t;
                        scrollOffset = 0;
                        rebuild();
                    },
                    t == tab
            );
        }

        int y = CONTENT_TOP + 14 - scrollOffset;
        switch (tab) {
            case DASHBOARD -> dashboard(x, y, panelW);
            case AI -> ai(x, y, panelW);
            case TAKEOVER -> takeover(x, y, panelW);
            case AUTOMATION -> automation(x, y, panelW);
            case TOOLS -> tools(x, y, panelW);
            case HUD -> hud(x, y, panelW);
            case REFILL -> refill(x, y, panelW);
            case SETTINGS -> settings(x, y, panelW);
        }
    }

    private void dashboard(int x, int y, int w) {
        TextFieldWidget ask = scrollingField(x + 12, y, w - 104, "Tell SURV what to do...", "");
        addScrollingButton(x + w - 84, y, 72, "SEND", () -> {
            if (ask != null && !ask.getText().isBlank()) SurvOsClient.askAi(ask.getText().trim(), false);
        }, true);

        addScrollingButton(x + 12, y + 36, (w - 30) / 2,
                "VOICE  " + (SurvOsClient.VOICE.isRunning() ? "LISTENING" : "OFF"),
                this::toggleVoice, SurvOsClient.VOICE.isRunning());

        addScrollingButton(x + 18 + (w - 30) / 2, y + 36, (w - 30) / 2,
                "AI  " + SurvOsClient.AI.status(),
                () -> {
                    if (SurvOsClient.AI.ready()) SurvOsClient.AI.stop();
                    else SurvOsClient.AI.ensureStarted();
                    rebuild();
                }, SurvOsClient.AI.ready());

        addScrollingButton(x + 12, y + 72, w - 24,
                (SurvOsClient.PLAY.enabled() ? "STOP PLAY  //  " : "PLAY THE GAME  //  ")
                        + SurvOsClient.PLAY.status(),
                () -> {
                    if (SurvOsClient.PLAY.enabled()) SurvOsClient.PLAY.stop(client);
                    else SurvOsClient.PLAY.start(client);
                    rebuild();
                }, SurvOsClient.PLAY.enabled());

        int third = (w - 36) / 3;
        addScrollingButton(x + 12, y + 108, third, "MINE",
                () -> SurvOsClient.AUTOMATION.start(AutomationManager.Mode.MINING, client), false);
        addScrollingButton(x + 18 + third, y + 108, third, "MOB GRIND",
                () -> SurvOsClient.AUTOMATION.start(AutomationManager.Mode.MOB_GRIND, client), false);
        addScrollingButton(x + 24 + third * 2, y + 108, third, "STOP ALL",
                this::stopAll, true);

        addScrollingButton(x + 12, y + 144, w - 24,
                "AUTO  " + SurvOsClient.AUTOMATION.mode() + "  •  " + SurvOsClient.AUTOMATION.state()
                        + "  •  " + SurvOsClient.AUTOMATION.reason(),
                () -> { tab = Tab.AUTOMATION; scrollOffset = 0; rebuild(); }, false);

        addScrollingButton(x + 12, y + 180, w - 24,
                "MEMORY  " + SurvOsClient.CHAT_MEMORY.noteCount() + " notes  •  "
                        + SurvOsClient.CHAT_MEMORY.turnCount() + " chat turns",
                () -> { tab = Tab.AI; scrollOffset = 0; rebuild(); }, false);

        addScrollingButton(x + 12, y + 216, w - 24,
                "LAST  " + trim(SurvOsClient.AI.lastReply(), 72),
                () -> {}, false);
    }

    private void ai(int x, int y, int w) {
        TextFieldWidget ask = scrollingField(x + 12, y, w - 104, "Talk normally — no wake word needed...", "");
        addScrollingButton(x + w - 84, y, 72, "ASK", () -> {
            if (ask != null && !ask.getText().isBlank()) SurvOsClient.askAi(ask.getText().trim(), false);
        }, true);

        addScrollingButton(x + 12, y + 36, w - 24,
                "AI  " + SurvOsClient.AI.status() + "  •  " + SurvOsClient.AI.backendName()
                        + "  •  VOICE " + SurvOsClient.TTS.status(),
                () -> {
                    SurvOsClient.AI.ensureStarted();
                    SurvOsClient.TTS.setEnabled(true);
                    rebuild();
                }, SurvOsClient.AI.ready());

        addToggle(x + 12, y + 72, (w - 30) / 2, "Always listening",
                () -> SurvOsClient.CONFIG.voiceEnabled && SurvOsClient.VOICE.isRunning(),
                v -> {
                    SurvOsClient.CONFIG.voiceEnabled = v;
                    SurvOsClient.CONFIG.voiceAutoStart = v;
                    if (v) SurvOsClient.VOICE.start(SurvOsClient.CONFIG);
                    else SurvOsClient.VOICE.stop();
                });

        addToggle(x + 18 + (w - 30) / 2, y + 72, (w - 30) / 2, "Wake word required",
                () -> SurvOsClient.CONFIG.requireWakeWord,
                v -> SurvOsClient.CONFIG.requireWakeWord = v);

        addToggle(x + 12, y + 108, (w - 30) / 2, "Speak replies",
                () -> SurvOsClient.CONFIG.aiSpeakReplies,
                v -> {
                    SurvOsClient.CONFIG.aiSpeakReplies = v;
                    SurvOsClient.TTS.setEnabled(v && SurvOsClient.CONFIG.ttsEnabled);
                });

        addToggle(x + 18 + (w - 30) / 2, y + 108, (w - 30) / 2, "Persistent memory",
                () -> SurvOsClient.CONFIG.memoryEnabled,
                v -> SurvOsClient.CONFIG.memoryEnabled = v);

        addScrollingButton(x + 12, y + 144, w - 24,
                "MIC  " + SurvOsClient.VOICE.status()
                        + "  •  LEVEL " + Math.round(SurvOsClient.VOICE.micLevel() * 100f) + "%"
                        + "  •  LAST " + trim(SurvOsClient.VOICE.lastHeard(), 28),
                () -> {
                    SurvOsClient.VOICE.stop();
                    SurvOsClient.VOICE.start(SurvOsClient.CONFIG);
                    rebuild();
                }, SurvOsClient.VOICE.isRunning());

        addScrollingButton(x + 12, y + 180, w - 24,
                "VOICE STYLE  " + SurvOsClient.CONFIG.voiceStyle,
                () -> {
                    SurvOsClient.CONFIG.voiceStyle = SurvOsClient.TTS.cycleStyle();
                    SurvOsClient.TTS.setStyle(SurvOsClient.CONFIG.voiceStyle);
                    SurvOsClient.CONFIG.save();
                    rebuild();
                }, false);

        TextFieldWidget remember = scrollingField(x + 12, y + 216, w - 104, "Remember this...");
        addScrollingButton(x + w - 84, y + 216, 72, "SAVE", () -> {
            if (remember != null && !remember.getText().isBlank()) {
                SurvOsClient.CHAT_MEMORY.remember(remember.getText());
                SurvOsClient.notice("Remembered.");
            }
        }, false);

        addScrollingButton(x + 12, y + 252, w - 24,
                "RECENT  " + trim(SurvOsClient.recentCommands().toString(), 74),
                () -> {}, false);

        addScrollingButton(x + 12, y + 288, w - 24,
                "REPLY  " + trim(SurvOsClient.AI.lastReply(), 74),
                () -> {}, false);
    }

    private void takeover(int x, int y, int w) {
        addScrollingButton(x + 12, y, w - 24,
                SurvOsClient.PLAY.enabled()
                        ? "RELEASE CONTROL  //  PLAY " + SurvOsClient.PLAY.phase()
                        : "TAKE OVER  //  SURV PLAYS FOR ME",
                () -> {
                    if (SurvOsClient.PLAY.enabled()) SurvOsClient.PLAY.stop(client);
                    else SurvOsClient.PLAY.start(client);
                    rebuild();
                }, true);

        addScrollingButton(x + 12, y + 38, w - 24,
                "STATUS  " + SurvOsClient.PLAY.status()
                        + "  •  AUTO " + SurvOsClient.AUTOMATION.mode()
                        + " / " + SurvOsClient.AUTOMATION.state(),
                () -> {}, false);

        TextFieldWidget order = scrollingField(
                x + 12, y + 76, w - 104,
                "Tell SURV exactly what to do while it has control...");
        addScrollingButton(x + w - 84, y + 76, 72, "DO IT",
                () -> {
                    if (order != null && !order.getText().isBlank()) {
                        SurvOsClient.askAi(order.getText().trim(), false);
                    }
                }, true);

        int half = (w - 30) / 2;
        addScrollingButton(x + 12, y + 114, half, "COMBAT LOADOUT",
                () -> InventoryManager.applyLoadout(client, "COMBAT"), false);
        addScrollingButton(x + half + 18, y + 114, half, "GO HOME",
                () -> SurvOsClient.AUTOMATION.goToWaypoint(client, "home"), false);

        addScrollingButton(x + 12, y + 150, half, "GET WOOD",
                () -> {
                    SurvOsClient.AUTOMATION.setGoal("log", 32);
                    SurvOsClient.AUTOMATION.start(AutomationManager.Mode.TREE_FARM, client);
                }, false);
        addScrollingButton(x + half + 18, y + 150, half, "MINE",
                () -> SurvOsClient.AUTOMATION.start(AutomationManager.Mode.MINING, client), false);

        addScrollingButton(x + 12, y + 186, half, "DEFEND / GRIND",
                () -> SurvOsClient.AUTOMATION.start(AutomationManager.Mode.MOB_GRIND, client), false);
        addScrollingButton(x + half + 18, y + 186, half, "RETURN START",
                () -> SurvOsClient.AUTOMATION.returnToTaskStart(client), false);

        addToggle(x + 12, y + 224, half, "Play safety",
                () -> SurvOsClient.CONFIG.playSafety,
                v -> SurvOsClient.CONFIG.playSafety = v);
        addToggle(x + half + 18, y + 224, half, "Collect loot",
                () -> SurvOsClient.CONFIG.collectLoot,
                v -> SurvOsClient.CONFIG.collectLoot = v);

        addScrollingButton(x + 12, y + 260, w - 24,
                "MIC " + SurvOsClient.VOICE.status()
                        + "  •  LEVEL " + Math.round(SurvOsClient.VOICE.micLevel() * 100f) + "%"
                        + "  •  " + trim(SurvOsClient.VOICE.lastHeard(), 42),
                () -> {
                    SurvOsClient.VOICE.stop();
                    SurvOsClient.VOICE.start(SurvOsClient.CONFIG);
                    rebuild();
                }, SurvOsClient.VOICE.isRunning());

        addScrollingButton(x + 12, y + 296, w - 24,
                "F7 = IMMEDIATELY RELEASE SURV CONTROL",
                this::stopAll, true);
    }

    private void automation(int x, int y, int w) {
        TextFieldWidget goal = scrollingField(x + 12, y, w - 132, "Goal item (iron, diamond, logs...)");
        TextFieldWidget count = scrollingField(x + w - 114, y, 102, "Count");
        addScrollingButton(x + 12, y + 36, (w - 30) / 2, "SET GOAL", () -> {
            if (goal != null) SurvOsClient.AUTOMATION.setGoal(goal.getText().trim(), parseInt(count == null ? "" : count.getText(), 0));
        }, false);
        addScrollingButton(x + 18 + (w - 30) / 2, y + 36, (w - 30) / 2, "RETURN START",
                () -> SurvOsClient.AUTOMATION.returnToTaskStart(client), false);

        int third = (w - 36) / 3;
        addScrollingButton(x + 12, y + 72, third, "MINING",
                () -> SurvOsClient.AUTOMATION.start(AutomationManager.Mode.MINING, client), false);
        addScrollingButton(x + 18 + third, y + 72, third, "MOBS",
                () -> SurvOsClient.AUTOMATION.start(AutomationManager.Mode.MOB_GRIND, client), false);
        addScrollingButton(x + 24 + third * 2, y + 72, third, "TREES",
                () -> SurvOsClient.AUTOMATION.start(AutomationManager.Mode.TREE_FARM, client), false);

        addScrollingButton(x + 12, y + 108, third, "CROPS",
                () -> SurvOsClient.AUTOMATION.start(AutomationManager.Mode.CROP_FARM, client), false);
        addScrollingButton(x + 18 + third, y + 108, third, "FISH",
                () -> SurvOsClient.AUTOMATION.start(AutomationManager.Mode.FISHING, client), false);
        addScrollingButton(x + 24 + third * 2, y + 108, third, "ANIMALS",
                () -> SurvOsClient.AUTOMATION.start(AutomationManager.Mode.ANIMAL_FARM, client), false);

        TextFieldWidget xField = scrollingField(x + 12, y + 150, (w - 48) / 3, "X");
        TextFieldWidget yField = scrollingField(x + 18 + (w - 48) / 3, y + 150, (w - 48) / 3, "Y");
        TextFieldWidget zField = scrollingField(x + 24 + ((w - 48) / 3) * 2, y + 150, (w - 48) / 3, "Z");
        addScrollingButton(x + 12, y + 186, w - 24, "NAVIGATE TO COORDINATES", () -> {
            if (client.player == null) return;
            int tx = parseInt(xField == null ? "" : xField.getText(), client.player.getBlockX());
            int ty = parseInt(yField == null ? "" : yField.getText(), client.player.getBlockY());
            int tz = parseInt(zField == null ? "" : zField.getText(), client.player.getBlockZ());
            SurvOsClient.AUTOMATION.navigateTo(client, tx, ty, tz);
        }, false);

        addScrollingButton(x + 12, y + 222, (w - 36) / 3, "PAUSE",
                () -> SurvOsClient.AUTOMATION.pause(client), false);
        addScrollingButton(x + 18 + (w - 36) / 3, y + 222, (w - 36) / 3, "RESUME",
                () -> SurvOsClient.AUTOMATION.resume(), false);
        addScrollingButton(x + 24 + ((w - 36) / 3) * 2, y + 222, (w - 36) / 3, "STOP",
                this::stopAll, true);

        addToggle(x + 12, y + 258, (w - 30) / 2, "Avoid creepers",
                () -> SurvOsClient.CONFIG.avoidCreepers,
                v -> SurvOsClient.CONFIG.avoidCreepers = v);

        addToggle(x + 18 + (w - 30) / 2, y + 258, (w - 30) / 2, "Collect loot",
                () -> SurvOsClient.CONFIG.collectLoot,
                v -> SurvOsClient.CONFIG.collectLoot = v);
    }

    private void tools(int x, int y, int w) {
        int half = (w - 30) / 2;

        TextFieldWidget craft = scrollingField(x + 12, y, half - 72, "Craft item");
        TextFieldWidget qty = scrollingField(x + half - 54, y, 60, "Qty");
        addScrollingButton(x + half + 12, y, (half - 6) / 2, "CRAFT X",
                () -> {
                    if (craft != null && !craft.getText().isBlank())
                        SurvOsClient.LEGACY.queueCraftDirect(client, craft.getText().trim(), parseInt(qty == null ? "" : qty.getText(), 1), false);
                }, false);
        addScrollingButton(x + half + 18 + (half - 6) / 2, y, (half - 6) / 2, "MAX",
                () -> {
                    if (craft != null && !craft.getText().isBlank())
                        SurvOsClient.LEGACY.queueCraftDirect(client, craft.getText().trim(), 1, true);
                }, false);

        addScrollingButton(x + 12, y + 36, half, "VILLAGER SETTINGS",
                () -> SurvOsClient.LEGACY.openVillagerSettings(client, this), false);
        addScrollingButton(x + half + 18, y + 36, half, "START / STOP CYCLER",
                () -> SurvOsClient.LEGACY.toggleVillager(client), false);

        addScrollingButton(x + 12, y + 72, half, "ENCHANT LAB",
                () -> SurvOsClient.LEGACY.openEnchantBuilder(client), false);
        addScrollingButton(x + half + 18, y + 72, half, "APPLY COMBAT LOADOUT",
                () -> InventoryManager.applyLoadout(client, "COMBAT"), false);

        TextFieldWidget wp = scrollingField(x + 12, y + 114, w - 148, "Waypoint / route name");
        addScrollingButton(x + w - 130, y + 114, 56, "SAVE",
                () -> { if (wp != null && !wp.getText().isBlank()) SurvOsClient.MEMORY.setWaypoint(client, wp.getText()); }, false);
        addScrollingButton(x + w - 68, y + 114, 56, "GO",
                () -> { if (wp != null && !wp.getText().isBlank()) SurvOsClient.AUTOMATION.goToWaypoint(client, wp.getText()); }, false);

        addScrollingButton(x + 12, y + 150, half, "START ROUTE RECORD",
                () -> { if (wp != null && !wp.getText().isBlank()) SurvOsClient.MEMORY.startRoute(wp.getText()); }, false);
        addScrollingButton(x + half + 18, y + 150, half, "STOP + SAVE ROUTE",
                () -> SurvOsClient.notice("Saved " + SurvOsClient.MEMORY.stopRoute() + " points."), false);

        addScrollingButton(x + 12, y + 186, half, "PLAY SAVED ROUTE",
                () -> { if (wp != null && !wp.getText().isBlank()) SurvOsClient.AUTOMATION.playRoute(client, wp.getText()); }, false);
        addScrollingButton(x + half + 18, y + 186, half, "MINING LOADOUT",
                () -> InventoryManager.applyLoadout(client, "MINING"), false);

        addScrollingButton(x + 12, y + 222, w - 24,
                "WAYPOINTS  " + trim(SurvOsClient.MEMORY.waypointDetails(), 72),
                () -> {}, false);

        addScrollingButton(x + 12, y + 252, w - 24,
                "SEEDCRACKERX 2.15.6  //  /seedcracker gui",
                () -> SurvOsClient.notice("SeedCrackerX is bundled. Use /seedcracker gui."),
                true);

        int row = y + 298;
        addScrollingButton(x + 12, row, half, "SET HOME HERE",
                () -> SurvOsClient.MEMORY.setWaypoint(client, "home"), true);
        addScrollingButton(x + half + 18, row, half, "GO HOME",
                () -> SurvOsClient.AUTOMATION.goToWaypoint(client, "home"), true);

        row += 38;
        TextFieldWidget hx = scrollingField(x + 12, row, (w - 48) / 3, "Home X");
        TextFieldWidget hy = scrollingField(x + 18 + (w - 48) / 3, row, (w - 48) / 3, "Home Y");
        TextFieldWidget hz = scrollingField(x + 24 + ((w - 48) / 3) * 2, row, (w - 48) / 3, "Home Z");

        row += 34;
        addScrollingButton(x + 12, row, w - 24, "SAVE HOME COORDS",
                () -> {
                    if (client.player == null) return;
                    int vx = parseInt(hx == null ? "" : hx.getText(), client.player.getBlockX());
                    int vy = parseInt(hy == null ? "" : hy.getText(), client.player.getBlockY());
                    int vz = parseInt(hz == null ? "" : hz.getText(), client.player.getBlockZ());
                    if (SurvOsClient.MEMORY.setWaypointAtCurrentDimension(client, "home", vx, vy, vz)) {
                        SurvOsClient.notice("Home saved at " + vx + ", " + vy + ", " + vz);
                    }
                }, false);

        row += 42;
        TextFieldWidget locName = scrollingField(x + 12, row, half, "Location name");
        TextFieldWidget lx = scrollingField(x + half + 18, row, (half - 12) / 3, "X");
        TextFieldWidget ly = scrollingField(x + half + 22 + (half - 12) / 3, row, (half - 12) / 3, "Y");
        TextFieldWidget lz = scrollingField(x + half + 26 + ((half - 12) / 3) * 2, row, (half - 12) / 3, "Z");

        row += 34;
        addScrollingButton(x + 12, row, half, "SAVE NAMED LOCATION",
                () -> {
                    if (client.player == null || locName == null || locName.getText().isBlank()) return;
                    int vx = parseInt(lx == null ? "" : lx.getText(), client.player.getBlockX());
                    int vy = parseInt(ly == null ? "" : ly.getText(), client.player.getBlockY());
                    int vz = parseInt(lz == null ? "" : lz.getText(), client.player.getBlockZ());
                    if (SurvOsClient.MEMORY.setWaypointAtCurrentDimension(
                            client, locName.getText().trim(), vx, vy, vz)) {
                        SurvOsClient.notice("Saved " + locName.getText().trim());
                    }
                }, false);

        addScrollingButton(x + half + 18, row, half, "GO NAMED LOCATION",
                () -> {
                    if (locName != null && !locName.getText().isBlank()) {
                        SurvOsClient.AUTOMATION.goToWaypoint(client, locName.getText().trim());
                    }
                }, false);
    }

    private void hud(int x, int y, int w) {
        int half = (w - 30) / 2;
        int row = y;

        addToggle(x + 12, row, half, "Main HUD",
                () -> SurvOsClient.CONFIG.hudEnabled,
                v -> SurvOsClient.CONFIG.hudEnabled = v);
        addToggle(x + half + 18, row, half, "Main HUD right",
                () -> SurvOsClient.CONFIG.hudRight,
                v -> SurvOsClient.CONFIG.hudRight = v);
        row += 34;

        addToggle(x + 12, row, half, "Health",
                () -> SurvOsClient.CONFIG.showHealth,
                v -> SurvOsClient.CONFIG.showHealth = v);
        addToggle(x + half + 18, row, half, "Hunger",
                () -> SurvOsClient.CONFIG.showHunger,
                v -> SurvOsClient.CONFIG.showHunger = v);
        row += 34;

        addToggle(x + 12, row, half, "Food count",
                () -> SurvOsClient.CONFIG.showFoodCount,
                v -> SurvOsClient.CONFIG.showFoodCount = v);
        addToggle(x + half + 18, row, half, "Armor",
                () -> SurvOsClient.CONFIG.showArmor,
                v -> SurvOsClient.CONFIG.showArmor = v);
        row += 34;

        addToggle(x + 12, row, half, "XP",
                () -> SurvOsClient.CONFIG.showXp,
                v -> SurvOsClient.CONFIG.showXp = v);
        addToggle(x + half + 18, row, half, "Coordinates",
                () -> SurvOsClient.CONFIG.showCoords,
                v -> SurvOsClient.CONFIG.showCoords = v);
        row += 34;

        addToggle(x + 12, row, half, "Dimension",
                () -> SurvOsClient.CONFIG.showDimension,
                v -> SurvOsClient.CONFIG.showDimension = v);
        addToggle(x + half + 18, row, half, "Biome",
                () -> SurvOsClient.CONFIG.showBiome,
                v -> SurvOsClient.CONFIG.showBiome = v);
        row += 34;

        addToggle(x + 12, row, half, "Light",
                () -> SurvOsClient.CONFIG.showLight,
                v -> SurvOsClient.CONFIG.showLight = v);
        addToggle(x + half + 18, row, half, "Day / night",
                () -> SurvOsClient.CONFIG.showDayNight,
                v -> SurvOsClient.CONFIG.showDayNight = v);
        row += 34;

        addToggle(x + 12, row, half, "Weather",
                () -> SurvOsClient.CONFIG.showWeather,
                v -> SurvOsClient.CONFIG.showWeather = v);
        addToggle(x + half + 18, row, half, "Effects",
                () -> SurvOsClient.CONFIG.showEffects,
                v -> SurvOsClient.CONFIG.showEffects = v);
        row += 34;

        addToggle(x + 12, row, half, "Held item",
                () -> SurvOsClient.CONFIG.showHeldItem,
                v -> SurvOsClient.CONFIG.showHeldItem = v);
        addToggle(x + half + 18, row, half, "Durability",
                () -> SurvOsClient.CONFIG.showDurability,
                v -> SurvOsClient.CONFIG.showDurability = v);
        row += 34;

        addToggle(x + 12, row, half, "Inventory free",
                () -> SurvOsClient.CONFIG.showInventory,
                v -> SurvOsClient.CONFIG.showInventory = v);
        addToggle(x + half + 18, row, half, "Totems",
                () -> SurvOsClient.CONFIG.showTotems,
                v -> SurvOsClient.CONFIG.showTotems = v);
        row += 34;

        addToggle(x + 12, row, half, "Arrows",
                () -> SurvOsClient.CONFIG.showArrows,
                v -> SurvOsClient.CONFIG.showArrows = v);
        addToggle(x + half + 18, row, half, "Threat system",
                () -> SurvOsClient.CONFIG.showHostiles,
                v -> SurvOsClient.CONFIG.showHostiles = v);
        row += 34;

        addToggle(x + 12, row, half, "Threat reasons",
                () -> SurvOsClient.CONFIG.showThreatReasons,
                v -> SurvOsClient.CONFIG.showThreatReasons = v);
        addToggle(x + half + 18, row, half, "Readiness score",
                () -> SurvOsClient.CONFIG.showReadiness,
                v -> SurvOsClient.CONFIG.showReadiness = v);
        row += 34;

        addToggle(x + 12, row, half, "Nearest threat",
                () -> SurvOsClient.CONFIG.showNearestThreat,
                v -> SurvOsClient.CONFIG.showNearestThreat = v);
        addToggle(x + half + 18, row, half, "Threat voice warnings",
                () -> SurvOsClient.CONFIG.threatVoiceWarnings,
                v -> SurvOsClient.CONFIG.threatVoiceWarnings = v);
        row += 34;

        addToggle(x + 12, row, half, "Nearby players",
                () -> SurvOsClient.CONFIG.showNearbyPlayers,
                v -> SurvOsClient.CONFIG.showNearbyPlayers = v);
        addToggle(x + half + 18, row, half, "FPS",
                () -> SurvOsClient.CONFIG.showFps,
                v -> SurvOsClient.CONFIG.showFps = v);
        row += 34;

        addToggle(x + 12, row, half, "Ping",
                () -> SurvOsClient.CONFIG.showPing,
                v -> SurvOsClient.CONFIG.showPing = v);
        addToggle(x + half + 18, row, half, "Automation",
                () -> SurvOsClient.CONFIG.showAutomation,
                v -> SurvOsClient.CONFIG.showAutomation = v);
        row += 34;

        addToggle(x + 12, row, half, "Voice / AI",
                () -> SurvOsClient.CONFIG.showVoice,
                v -> SurvOsClient.CONFIG.showVoice = v);
        addToggle(x + half + 18, row, half, "Memory",
                () -> SurvOsClient.CONFIG.showMemory,
                v -> SurvOsClient.CONFIG.showMemory = v);
        row += 42;

        addScrollingButton(x + 12, row, w - 24, "LEFT STATS HUD", () -> {}, true);
        row += 34;

        addToggle(x + 12, row, half, "Stats HUD",
                () -> SurvOsClient.CONFIG.statsHudEnabled,
                v -> SurvOsClient.CONFIG.statsHudEnabled = v);
        addToggle(x + half + 18, row, half, "Session time",
                () -> SurvOsClient.CONFIG.statsShowSessionTime,
                v -> SurvOsClient.CONFIG.statsShowSessionTime = v);
        row += 34;

        addToggle(x + 12, row, half, "Distance",
                () -> SurvOsClient.CONFIG.statsShowDistance,
                v -> SurvOsClient.CONFIG.statsShowDistance = v);
        addToggle(x + half + 18, row, half, "Blocks mined",
                () -> SurvOsClient.CONFIG.statsShowBlocksMined,
                v -> SurvOsClient.CONFIG.statsShowBlocksMined = v);
        row += 34;

        addToggle(x + 12, row, half, "Mob hits",
                () -> SurvOsClient.CONFIG.statsShowMobHits,
                v -> SurvOsClient.CONFIG.statsShowMobHits = v);
        addToggle(x + half + 18, row, half, "Task",
                () -> SurvOsClient.CONFIG.statsShowCurrentTask,
                v -> SurvOsClient.CONFIG.statsShowCurrentTask = v);
        row += 34;

        addToggle(x + 12, row, half, "Goal rate / ETA",
                () -> SurvOsClient.CONFIG.statsShowGoalRate,
                v -> SurvOsClient.CONFIG.statsShowGoalRate = v);
        addToggle(x + half + 18, row, half, "Free slots",
                () -> SurvOsClient.CONFIG.statsShowInventoryFree,
                v -> SurvOsClient.CONFIG.statsShowInventoryFree = v);
        row += 34;

        addToggle(x + 12, row, half, "PLAY phase",
                () -> SurvOsClient.CONFIG.statsShowPlayPhase,
                v -> SurvOsClient.CONFIG.statsShowPlayPhase = v);
        row += 42;

        addScrollingButton(x + 12, row, w - 24, "HELMET HUD  //  only while helmet equipped", () -> {}, true);
        row += 34;

        addToggle(x + 12, row, half, "Helmet HUD",
                () -> SurvOsClient.CONFIG.helmetHudEnabled,
                v -> SurvOsClient.CONFIG.helmetHudEnabled = v);
        addToggle(x + half + 18, row, half, "Helmet info",
                () -> SurvOsClient.CONFIG.helmetShowHelmet,
                v -> SurvOsClient.CONFIG.helmetShowHelmet = v);
        row += 34;

        addToggle(x + 12, row, half, "Helmet threat scan",
                () -> SurvOsClient.CONFIG.helmetShowThreat,
                v -> SurvOsClient.CONFIG.helmetShowThreat = v);
        addToggle(x + half + 18, row, half, "Helmet coords",
                () -> SurvOsClient.CONFIG.helmetShowCoords,
                v -> SurvOsClient.CONFIG.helmetShowCoords = v);
        row += 34;

        addToggle(x + 12, row, half, "Helmet task",
                () -> SurvOsClient.CONFIG.helmetShowTask,
                v -> SurvOsClient.CONFIG.helmetShowTask = v);
        addToggle(x + half + 18, row, half, "Helmet durability",
                () -> SurvOsClient.CONFIG.helmetShowDurability,
                v -> SurvOsClient.CONFIG.helmetShowDurability = v);
        row += 34;

        addToggle(x + 12, row, half, "Helmet voice link",
                () -> SurvOsClient.CONFIG.helmetShowVoice,
                v -> SurvOsClient.CONFIG.helmetShowVoice = v);
        addToggle(x + half + 18, row, half, "Helmet time",
                () -> SurvOsClient.CONFIG.helmetShowTime,
                v -> SurvOsClient.CONFIG.helmetShowTime = v);
    }

    private void refill(int x, int y, int w) {
        int half = (w - 30) / 2;
        int row = y;

        addToggle(x + 12, row, w - 24, "AUTO REFILL MASTER",
                () -> SurvOsClient.CONFIG.autoHotbar,
                v -> SurvOsClient.CONFIG.autoHotbar = v);
        row += 42;

        SurvOsClient.CONFIG.sanitize();

        for (int slot = 0; slot < 9; slot++) {
            final int i = slot;
            addToggle(x + 12, row, half, "Slot " + (slot + 1) + " refill",
                    () -> SurvOsClient.CONFIG.autoRefillSlots[i],
                    v -> SurvOsClient.CONFIG.autoRefillSlots[i] = v);

            TextFieldWidget item = scrollingField(
                    x + half + 18,
                    row,
                    half,
                    "item keyword",
                    SurvOsClient.CONFIG.autoRefillItems[i]);

            if (item != null) {
                item.setChangedListener(v -> {
                    SurvOsClient.CONFIG.autoRefillItems[i] = v.trim();
                    SurvOsClient.CONFIG.save();
                });
            }

            row += 34;
        }

        row += 8;
        addScrollingButton(
                x + 12,
                row,
                w - 24,
                "SAFE MODE // only fills EMPTY enabled slots — never replaces your item",
                () -> {},
                true);
    }

    private void settings(int x, int y, int w) {
        int half = (w - 30) / 2;

        addScrollingButton(x + 12, y, w - 24,
                "MIC  " + SurvOsClient.CONFIG.microphone,
                this::cycleMic, false);

        addToggle(x + 12, y + 36, half, "Always listening",
                () -> SurvOsClient.CONFIG.voiceEnabled && SurvOsClient.VOICE.isRunning(),
                v -> {
                    SurvOsClient.CONFIG.voiceEnabled = v;
                    SurvOsClient.CONFIG.voiceAutoStart = v;
                    if (v) SurvOsClient.VOICE.start(SurvOsClient.CONFIG);
                    else SurvOsClient.VOICE.stop();
                });
        addToggle(x + half + 18, y + 36, half, "HUD",
                () -> SurvOsClient.CONFIG.hudEnabled,
                v -> SurvOsClient.CONFIG.hudEnabled = v);

        addToggle(x + 12, y + 72, half, "Smart alerts",
                () -> SurvOsClient.CONFIG.smartAlerts,
                v -> SurvOsClient.CONFIG.smartAlerts = v);
        addToggle(x + half + 18, y + 72, half, "Spectator warning",
                () -> SurvOsClient.CONFIG.spectatorWarnings,
                v -> SurvOsClient.CONFIG.spectatorWarnings = v);

        addScrollingButton(x + 12, y + 108, half,
                "PROFILE  " + SurvOsClient.CONFIG.profile,
                () -> {
                    String p = switch (SurvOsClient.CONFIG.profile) {
                        case "SURVIVAL" -> "MINING";
                        case "MINING" -> "COMBAT";
                        case "COMBAT" -> "NETHER";
                        case "NETHER" -> "BASE";
                        case "BASE" -> "BUILDING";
                        default -> "SURVIVAL";
                    };
                    SurvOsClient.CONFIG.applyProfile(p);
                    rebuild();
                }, false);

        addScrollingButton(x + half + 18, y + 108, half,
                "HUD THEME  " + SurvOsClient.CONFIG.hudTheme,
                () -> {
                    SurvOsClient.CONFIG.hudTheme = switch (SurvOsClient.CONFIG.hudTheme) {
                        case "CYAN" -> "AMBER";
                        case "AMBER" -> "GREEN";
                        case "GREEN" -> "RED";
                        case "RED" -> "MONO";
                        default -> "CYAN";
                    };
                    SurvOsClient.CONFIG.save();
                    rebuild();
                }, false);

        addToggle(x + 12, y + 144, half, "Auto dimension profiles",
                () -> SurvOsClient.CONFIG.autoDimensionProfiles,
                v -> SurvOsClient.CONFIG.autoDimensionProfiles = v);
        addToggle(x + half + 18, y + 144, half, "Death waypoint",
                () -> SurvOsClient.CONFIG.autoDeathWaypoint,
                v -> SurvOsClient.CONFIG.autoDeathWaypoint = v);

        addToggle(x + 12, y + 178, half, "Fire warning",
                () -> SurvOsClient.CONFIG.alertFire,
                v -> SurvOsClient.CONFIG.alertFire = v);
        addToggle(x + half + 18, y + 178, half, "Low-air warning",
                () -> SurvOsClient.CONFIG.alertLowAir,
                v -> SurvOsClient.CONFIG.alertLowAir = v);

        addToggle(x + 12, y + 212, half, "No-totem warning",
                () -> SurvOsClient.CONFIG.alertNoTotem,
                v -> SurvOsClient.CONFIG.alertNoTotem = v);
        addToggle(x + half + 18, y + 212, half, "Low-armor warning",
                () -> SurvOsClient.CONFIG.alertLowArmor,
                v -> SurvOsClient.CONFIG.alertLowArmor = v);

        addScrollingButton(x + 12, y + 254, w - 24, "COMBAT HOTBAR  //  editable below", () -> {}, true);

        int row = y + 290;
        combatField(x + 12, row, half, "1  Sword", SurvOsClient.CONFIG.combatSlot1, v -> SurvOsClient.CONFIG.combatSlot1 = v);
        combatField(x + half + 18, row, half, "2  Pearl", SurvOsClient.CONFIG.combatSlot2, v -> SurvOsClient.CONFIG.combatSlot2 = v);
        row += 34;
        combatField(x + 12, row, half, "3  Golden apple", SurvOsClient.CONFIG.combatSlot3, v -> SurvOsClient.CONFIG.combatSlot3 = v);
        combatField(x + half + 18, row, half, "4  Obsidian", SurvOsClient.CONFIG.combatSlot4, v -> SurvOsClient.CONFIG.combatSlot4 = v);
        row += 34;
        combatField(x + 12, row, half, "5  Crystal", SurvOsClient.CONFIG.combatSlot5, v -> SurvOsClient.CONFIG.combatSlot5 = v);
        combatField(x + half + 18, row, half, "6  Anchor", SurvOsClient.CONFIG.combatSlot6, v -> SurvOsClient.CONFIG.combatSlot6 = v);
        row += 34;
        combatField(x + 12, row, half, "6 fallback  Totem", SurvOsClient.CONFIG.combatSlot6Fallback, v -> SurvOsClient.CONFIG.combatSlot6Fallback = v);
        combatField(x + half + 18, row, half, "7  Glowstone", SurvOsClient.CONFIG.combatSlot7, v -> SurvOsClient.CONFIG.combatSlot7 = v);
        row += 34;
        combatField(x + 12, row, half, "7 fallback  Totem", SurvOsClient.CONFIG.combatSlot7Fallback, v -> SurvOsClient.CONFIG.combatSlot7Fallback = v);
        combatField(x + half + 18, row, half, "8  Totem", SurvOsClient.CONFIG.combatSlot8, v -> SurvOsClient.CONFIG.combatSlot8 = v);
        row += 34;
        combatField(x + 12, row, half, "9  Shield", SurvOsClient.CONFIG.combatSlot9, v -> SurvOsClient.CONFIG.combatSlot9 = v);

        addScrollingButton(x + half + 18, row, half, "APPLY COMBAT NOW",
                () -> InventoryManager.applyLoadout(client, "COMBAT"), true);

        row += 42;
        addScrollingButton(x + 12, row, half, "BACKUP SETTINGS",
                () -> SurvOsClient.notice(SurvOsClient.CONFIG.backup() ? "Settings backed up." : "Backup failed."), false);
        addScrollingButton(x + half + 18, row, half, "SELF TEST",
                () -> SurvOsClient.notice(SurvOsClient.statusLine(client)), false);

        row += 36;
        addScrollingButton(x + 12, row, w - 24,
                "F9 MENU  •  F8 VOICE ON/OFF  •  F7 RELEASE CONTROL",
                () -> {}, false);
    }

    private void combatField(int x, int y, int w, String label, String value, Consumer<String> setter) {
        TextFieldWidget field = scrollingField(x, y, w, label + "  [" + value + "]", value);
        if (field != null) {
            field.setChangedListener(v -> {
                setter.accept(v.trim());
                SurvOsClient.CONFIG.save();
            });
        }
    }

    private void stopAll() {
        if (SurvOsClient.PLAY.enabled()) SurvOsClient.PLAY.stop(client);
        SurvOsClient.AUTOMATION.stop(client, "User stop");
        SurvOsClient.CONTROL.stop(client);
    }

    private void toggleVoice() {
        if (SurvOsClient.VOICE.isRunning()) {
            SurvOsClient.VOICE.stop();
            SurvOsClient.CONFIG.voiceEnabled = false;
            SurvOsClient.CONFIG.voiceAutoStart = false;
        } else {
            SurvOsClient.CONFIG.voiceEnabled = true;
            SurvOsClient.CONFIG.voiceAutoStart = true;
            SurvOsClient.VOICE.start(SurvOsClient.CONFIG);
        }
        SurvOsClient.CONFIG.save();
        rebuild();
    }

    private void cycleMic() {
        List<String> mics = SurvOsClient.VOICE.microphones();
        if (mics.isEmpty()) return;
        int i = mics.indexOf(SurvOsClient.CONFIG.microphone);
        if (i < 0) i = 0;
        SurvOsClient.CONFIG.microphone = mics.get((i + 1) % mics.size());
        SurvOsClient.CONFIG.save();

        if (SurvOsClient.VOICE.isRunning()) {
            SurvOsClient.VOICE.stop();
            SurvOsClient.VOICE.start(SurvOsClient.CONFIG);
        }
        rebuild();
    }

    private TextFieldWidget scrollingField(int x, int y, int w, String placeholder) {
        return scrollingField(x, y, w, placeholder, "");
    }

    private TextFieldWidget scrollingField(int x, int y, int w, String placeholder, String value) {
        if (!inViewport(y, 22)) return null;
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, 22, Text.literal(placeholder));
        f.setPlaceholder(Text.literal(placeholder));
        f.setMaxLength(120);
        if (value != null && !value.isBlank()) f.setText(value);
        addDrawableChild(f);
        return f;
    }

    private void addFixedButton(int x, int y, int w, String text, Runnable action, boolean strong) {
        addDrawableChild(new SurvButton(x, y, w, 24, Text.literal(text), action, strong));
    }

    private void addScrollingButton(int x, int y, int w, String text, Runnable action, boolean strong) {
        if (!inViewport(y, 24)) return;
        addDrawableChild(new SurvButton(x, y, w, 24, Text.literal(text), action, strong));
    }

    private interface BoolGet { boolean get(); }
    private interface BoolSet { void set(boolean value); }

    private void addToggle(int x, int y, int w, String name, BoolGet get, BoolSet set) {
        addScrollingButton(x, y, w, name + "  " + (get.get() ? "ON" : "OFF"), () -> {
            set.set(!get.get());
            SurvOsClient.CONFIG.save();
            rebuild();
        }, get.get());
    }

    private boolean inViewport(int y, int h) {
        return y + h >= CONTENT_TOP && y <= contentBottom;
    }

    private int maxScroll() {
        int viewport = Math.max(120, contentBottom - CONTENT_TOP - 8);
        int content = switch (tab) {
            case SETTINGS -> 700;
            case HUD -> 1060;
            case REFILL -> 380;
            case AI -> 350;
            case TAKEOVER -> 350;
            case AUTOMATION -> 320;
            case TOOLS -> 540;
            case DASHBOARD -> 260;
        };
        return Math.max(0, content - viewport);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseY < CONTENT_TOP || mouseY > contentBottom) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        int old = scrollOffset;
        scrollOffset = Math.max(0, Math.min(maxScroll(), scrollOffset - (int)Math.round(verticalAmount * 28.0)));
        if (old != scrollOffset) {
            rebuild();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int panelW = Math.min(568, width - 12);
        int left = (width - panelW) / 2;
        int right = left + panelW;

        ctx.fill(0, 0, width, height, 0xF305080C);
        ctx.fill(left, 10, right, height - 8, 0xD90A1118);
        ctx.fill(left, 10, right, 12, 0xFF4CE8E2);
        ctx.fill(left, 38, right, 39, 0x554CE8E2);

        ctx.drawTextWithShadow(
                textRenderer,
                Text.literal("SURV // OS").formatted(Formatting.AQUA, Formatting.BOLD),
                left + 14, 18, 0xFFEAFBFF);

        String status = "VOICE " + (SurvOsClient.VOICE.isRunning() ? "ON" : "OFF")
                + "  •  AI " + SurvOsClient.AI.status()
                + "  •  PLAY " + (SurvOsClient.PLAY.enabled() ? SurvOsClient.PLAY.phase() : "OFF");
        int sw = textRenderer.getWidth(status);
        ctx.drawTextWithShadow(textRenderer, status, right - sw - 14, 19, 0xFF8496A3);

        ctx.fill(left + 8, CONTENT_TOP - 7, right - 8, contentBottom + 4, 0x69070D12);

        String title = switch (tab) {
            case DASHBOARD -> "COMMAND CENTER";
            case AI -> "SURV AI + MEMORY";
            case TAKEOVER -> "TAKE OVER";
            case AUTOMATION -> "AUTOMATION + NAVIGATION";
            case TOOLS -> "SURVIVAL TOOLS";
            case HUD -> "HUD MODULES  •  THREE LAYERS";
            case REFILL -> "AUTO REFILL  •  SLOT BY SLOT";
            case SETTINGS -> "SETTINGS  •  SCROLL FOR MORE";
        };
        ctx.drawTextWithShadow(textRenderer, title, left + 14, CONTENT_TOP - 3, 0xFF9FB4C0);

        if (maxScroll() > 0) {
            int trackTop = CONTENT_TOP + 12;
            int trackBottom = contentBottom - 8;
            int trackH = Math.max(20, trackBottom - trackTop);
            int thumbH = Math.max(24, (int)(trackH * (1.0 - (double)maxScroll() / (maxScroll() + trackH))));
            int thumbY = trackTop + (int)((trackH - thumbH) * (scrollOffset / (double)Math.max(1, maxScroll())));
            ctx.fill(right - 6, trackTop, right - 4, trackBottom, 0xFF1D2B34);
            ctx.fill(right - 7, thumbY, right - 3, thumbY + thumbH, 0xFF55E8E2);
        }

        super.render(ctx, mouseX, mouseY, delta);
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

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value.trim()); }
        catch (Exception e) { return fallback; }
    }

    private static String trim(String s, int n) {
        if (s == null || s.isBlank()) return "—";
        String oneLine = s.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= n ? oneLine : oneLine.substring(0, n - 1) + "…";
    }

    private static final class SurvButton extends PressableWidget {
        private final Runnable action;
        private final boolean strong;

        private SurvButton(int x, int y, int width, int height, Text text, Runnable action, boolean strong) {
            super(x, y, width, height, text);
            this.action = action == null ? () -> {} : action;
            this.strong = strong;
        }

        @Override
        public void onPress(AbstractInput input) {
            action.run();
        }

        @Override
        protected void drawIcon(DrawContext ctx, int mouseX, int mouseY, float deltaTicks) {
            int x = getX();
            int y = getY();
            int r = x + getWidth();
            int b = y + getHeight();

            int bg = strong
                    ? (isHovered() ? 0xE3245962 : 0xD9183A42)
                    : (isHovered() ? 0xE31B2C36 : 0xC9122029);
            int edge = strong ? 0xFF55EEE7 : (isHovered() ? 0xFF506D7C : 0xFF263B46);

            ctx.fill(x, y, r, b, bg);
            ctx.fill(x, y, r, y + 1, edge);
            ctx.fill(x, b - 1, r, b, edge);
            ctx.fill(x, y, x + 1, b, edge);
            ctx.fill(r - 1, y, r, b, edge);

            int textColor = active
                    ? (strong ? 0xFFF2FFFF : 0xFFE2EEF3)
                    : 0xFF66747C;

            ctx.drawCenteredTextWithShadow(
                    MinecraftClient.getInstance().textRenderer,
                    getMessage(),
                    x + getWidth() / 2,
                    y + (getHeight() - 8) / 2,
                    textColor
            );
        }

        @Override
        public void appendClickableNarrations(NarrationMessageBuilder builder) {
            appendDefaultNarrations(builder);
        }
    }
}
