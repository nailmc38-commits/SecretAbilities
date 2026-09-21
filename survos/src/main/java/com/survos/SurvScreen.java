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
    public enum Tab { DASHBOARD, AI, AUTOMATION, TOOLS, SETTINGS }

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
        int bw = (panelW - gap * 4) / 5;

        Tab[] tabs = Tab.values();
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
            case AUTOMATION -> automation(x, y, panelW);
            case TOOLS -> tools(x, y, panelW);
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
                "AI  " + SurvOsClient.AI.status() + "  •  VOICE " + SurvOsClient.TTS.status(),
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
                "VOICE STYLE  " + SurvOsClient.CONFIG.voiceStyle,
                () -> {
                    SurvOsClient.CONFIG.voiceStyle = SurvOsClient.TTS.cycleStyle();
                    SurvOsClient.TTS.setStyle(SurvOsClient.CONFIG.voiceStyle);
                    SurvOsClient.CONFIG.save();
                    rebuild();
                }, false);

        TextFieldWidget remember = scrollingField(x + 12, y + 180, w - 104, "Remember this...");
        addScrollingButton(x + w - 84, y + 180, 72, "SAVE", () -> {
            if (remember != null && !remember.getText().isBlank()) {
                SurvOsClient.CHAT_MEMORY.remember(remember.getText());
                SurvOsClient.notice("Remembered.");
            }
        }, false);

        addScrollingButton(x + 12, y + 216, w - 24,
                "RECENT  " + trim(SurvOsClient.recentCommands().toString(), 74),
                () -> {}, false);

        addScrollingButton(x + 12, y + 252, w - 24,
                "REPLY  " + trim(SurvOsClient.AI.lastReply(), 74),
                () -> {}, false);
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
                "WAYPOINTS  " + trim(SurvOsClient.MEMORY.waypointNames().toString(), 72),
                () -> {}, false);
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
        addToggle(x + half + 18, y + 72, half, "Auto hotbar",
                () -> SurvOsClient.CONFIG.autoHotbar,
                v -> SurvOsClient.CONFIG.autoHotbar = v);

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

        addScrollingButton(x + 12, y + 186, w - 24, "COMBAT HOTBAR  //  editable below", () -> {}, true);

        int row = y + 222;
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
            case SETTINGS -> 620;
            case AI -> 300;
            case AUTOMATION -> 320;
            case TOOLS -> 270;
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
            case AUTOMATION -> "AUTOMATION + NAVIGATION";
            case TOOLS -> "SURVIVAL TOOLS";
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
