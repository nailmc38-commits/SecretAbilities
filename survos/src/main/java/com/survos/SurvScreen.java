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

public final class SurvScreen extends Screen {
    public enum Tab { DASHBOARD, AI, AUTOMATION, TOOLS, SETTINGS }

    private Tab tab;
    private TextFieldWidget primaryField;
    private TextFieldWidget secondaryField;

    public SurvScreen(Tab tab) {
        super(Text.literal("SURV // OS"));
        this.tab = tab == null ? Tab.DASHBOARD : tab;
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        clearChildren();
        primaryField = null;
        secondaryField = null;

        int panelW = Math.min(520, width - 28);
        int x = (width - panelW) / 2;
        int navY = 44;
        int gap = 5;
        int bw = (panelW - gap * 4) / 5;

        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            addButton(
                    x + i * (bw + gap),
                    navY,
                    bw,
                    t == Tab.DASHBOARD ? "HOME" : t.name(),
                    () -> {
                        tab = t;
                        rebuild();
                    },
                    t == tab
            );
        }

        int top = 78;
        switch (tab) {
            case DASHBOARD -> dashboard(x, top, panelW);
            case AI -> ai(x, top, panelW);
            case AUTOMATION -> automation(x, top, panelW);
            case TOOLS -> tools(x, top, panelW);
            case SETTINGS -> settings(x, top, panelW);
        }
    }

    private void dashboard(int x, int y, int w) {
        primaryField = field(x + 12, y + 40, w - 102, "Ask SURV or tell it what to do...");
        addButton(x + w - 84, y + 40, 72, "SEND", () -> {
            String q = primaryField.getText().trim();
            if (!q.isBlank()) SurvOsClient.askAi(q, false);
        }, true);

        addButton(x + 12, y + 78, (w - 30) / 2,
                "VOICE  " + (SurvOsClient.VOICE.isRunning() ? "LISTENING" : "OFF"),
                this::toggleVoice, SurvOsClient.VOICE.isRunning());

        addButton(x + 18 + (w - 30) / 2, y + 78, (w - 30) / 2,
                "AI  " + SurvOsClient.AI.status(),
                () -> {
                    if (SurvOsClient.AI.ready()) SurvOsClient.AI.stop();
                    else SurvOsClient.AI.ensureStarted();
                    rebuild();
                }, SurvOsClient.AI.ready());

        int third = (w - 36) / 3;
        addButton(x + 12, y + 116, third, "MINE",
                () -> SurvOsClient.AUTOMATION.start(AutomationManager.Mode.MINING, client), false);
        addButton(x + 18 + third, y + 116, third, "MOB GRIND",
                () -> SurvOsClient.AUTOMATION.start(AutomationManager.Mode.MOB_GRIND, client), false);
        addButton(x + 24 + third * 2, y + 116, third, "STOP",
                () -> {
                    SurvOsClient.AUTOMATION.stop(client, "User stop");
                    SurvOsClient.CONTROL.stop(client);
                }, true);

        addButton(x + 12, y + 154, w - 24,
                "AUTO  " + SurvOsClient.AUTOMATION.mode() + "  •  " + SurvOsClient.AUTOMATION.state(),
                () -> { tab = Tab.AUTOMATION; rebuild(); }, false);

        addButton(x + 12, y + 184, w - 24,
                "LAST  " + trim(SurvOsClient.AI.lastReply(), 64),
                () -> { tab = Tab.AI; rebuild(); }, false);
    }

    private void ai(int x, int y, int w) {
        primaryField = field(x + 12, y + 38, w - 104, "Talk to SURV...");
        addButton(x + w - 84, y + 38, 72, "ASK",
                () -> {
                    String q = primaryField.getText().trim();
                    if (!q.isBlank()) SurvOsClient.askAi(q, false);
                }, true);

        addButton(x + 12, y + 76, w - 24,
                "STATUS  " + SurvOsClient.AI.status(),
                () -> {
                    if (SurvOsClient.AI.ready()) SurvOsClient.AI.stop();
                    else SurvOsClient.AI.ensureStarted();
                    rebuild();
                }, SurvOsClient.AI.ready());

        addButton(x + 12, y + 108, w - 24,
                "VOICE  " + SurvOsClient.TTS.status() + "  •  " + SurvOsClient.CONFIG.voiceStyle,
                () -> {
                    SurvOsClient.CONFIG.voiceStyle = SurvOsClient.TTS.cycleStyle();
                    SurvOsClient.TTS.setStyle(SurvOsClient.CONFIG.voiceStyle);
                    SurvOsClient.CONFIG.save();
                    rebuild();
                }, false);

        addToggle(x + 12, y + 140, (w - 30) / 2, "Speak replies",
                () -> SurvOsClient.CONFIG.aiSpeakReplies,
                v -> {
                    SurvOsClient.CONFIG.aiSpeakReplies = v;
                    SurvOsClient.TTS.setEnabled(v && SurvOsClient.CONFIG.ttsEnabled);
                });

        addToggle(x + 18 + (w - 30) / 2, y + 140, (w - 30) / 2, "Wake word",
                () -> SurvOsClient.CONFIG.requireWakeWord,
                v -> SurvOsClient.CONFIG.requireWakeWord = v);

        addButton(x + 12, y + 178, w - 24,
                "RECENT  " + trim(SurvOsClient.recentCommands().toString(), 68),
                () -> {}, false);

        addButton(x + 12, y + 210, w - 24,
                "REPLY  " + trim(SurvOsClient.AI.lastReply(), 68),
                () -> {}, false);
    }

    private void automation(int x, int y, int w) {
        primaryField = field(x + 12, y + 34, w - 132, "Goal item (iron, diamonds, logs...)");
        secondaryField = field(x + w - 114, y + 34, 102, "Count");

        addButton(x + 12, y + 66, (w - 30) / 2, "SET GOAL", () -> {
            SurvOsClient.AUTOMATION.setGoal(
                    primaryField.getText().trim(),
                    parseInt(secondaryField.getText(), 0));
        }, false);

        addButton(x + 18 + (w - 30) / 2, y + 66, (w - 30) / 2, "RETURN START",
                () -> SurvOsClient.AUTOMATION.returnToTaskStart(client), false);

        int third = (w - 36) / 3;
        addButton(x + 12, y + 100, third, "MINING",
                () -> SurvOsClient.AUTOMATION.start(AutomationManager.Mode.MINING, client), false);
        addButton(x + 18 + third, y + 100, third, "MOBS",
                () -> SurvOsClient.AUTOMATION.start(AutomationManager.Mode.MOB_GRIND, client), false);
        addButton(x + 24 + third * 2, y + 100, third, "TREES",
                () -> SurvOsClient.AUTOMATION.start(AutomationManager.Mode.TREE_FARM, client), false);

        addButton(x + 12, y + 134, third, "CROPS",
                () -> SurvOsClient.AUTOMATION.start(AutomationManager.Mode.CROP_FARM, client), false);
        addButton(x + 18 + third, y + 134, third, "FISH",
                () -> SurvOsClient.AUTOMATION.start(AutomationManager.Mode.FISHING, client), false);
        addButton(x + 24 + third * 2, y + 134, third, "ANIMALS",
                () -> SurvOsClient.AUTOMATION.start(AutomationManager.Mode.ANIMAL_FARM, client), false);

        addButton(x + 12, y + 172, (w - 36) / 3, "PAUSE",
                () -> SurvOsClient.AUTOMATION.pause(client), false);
        addButton(x + 18 + (w - 36) / 3, y + 172, (w - 36) / 3, "RESUME",
                () -> SurvOsClient.AUTOMATION.resume(), false);
        addButton(x + 24 + ((w - 36) / 3) * 2, y + 172, (w - 36) / 3, "STOP",
                () -> {
                    SurvOsClient.AUTOMATION.stop(client, "User stop");
                    SurvOsClient.CONTROL.stop(client);
                }, true);

        addToggle(x + 12, y + 208, (w - 30) / 2, "Avoid creepers",
                () -> SurvOsClient.CONFIG.avoidCreepers,
                v -> SurvOsClient.CONFIG.avoidCreepers = v);

        addToggle(x + 18 + (w - 30) / 2, y + 208, (w - 30) / 2, "Collect loot",
                () -> SurvOsClient.CONFIG.collectLoot,
                v -> SurvOsClient.CONFIG.collectLoot = v);
    }

    private void tools(int x, int y, int w) {
        int half = (w - 30) / 2;

        TextFieldWidget craftField = field(x + 12, y + 34, half - 72, "Craft item");
        TextFieldWidget countField = field(x + half - 54, y + 34, 60, "Qty");

        addButton(x + half + 12, y + 34, (half - 6) / 2, "CRAFT X",
                () -> {
                    String item = craftField.getText().trim();
                    if (!item.isBlank()) SurvOsClient.LEGACY.queueCraftDirect(
                            client, item, parseInt(countField.getText(), 1), false);
                }, false);

        addButton(x + half + 18 + (half - 6) / 2, y + 34, (half - 6) / 2, "MAX",
                () -> {
                    String item = craftField.getText().trim();
                    if (!item.isBlank()) SurvOsClient.LEGACY.queueCraftDirect(client, item, 1, true);
                }, false);

        addButton(x + 12, y + 72, half, "VILLAGER SETTINGS",
                () -> SurvOsClient.LEGACY.openVillagerSettings(client, this), false);
        addButton(x + half + 18, y + 72, half, "START / STOP CYCLER",
                () -> SurvOsClient.LEGACY.toggleVillager(client), false);

        addButton(x + 12, y + 108, half, "ENCHANT LAB",
                () -> SurvOsClient.LEGACY.openEnchantBuilder(client), false);
        addButton(x + half + 18, y + 108, half, "APPLY COMBAT LOADOUT",
                () -> InventoryManager.applyLoadout(client, "COMBAT"), false);

        TextFieldWidget waypointField = field(x + 12, y + 148, w - 148, "Waypoint name");
        addButton(x + w - 130, y + 148, 56, "SAVE",
                () -> {
                    String n = waypointField.getText().trim();
                    if (!n.isBlank()) SurvOsClient.MEMORY.setWaypoint(client, n);
                }, false);

        addButton(x + w - 68, y + 148, 56, "GO",
                () -> {
                    String n = waypointField.getText().trim();
                    if (!n.isBlank()) SurvOsClient.AUTOMATION.goToWaypoint(client, n);
                }, false);

        addButton(x + 12, y + 186, w - 24,
                "KNOWN  " + trim(SurvOsClient.MEMORY.waypointNames().toString(), 68),
                () -> {}, false);
    }

    private void settings(int x, int y, int w) {
        int half = (w - 30) / 2;

        addButton(x + 12, y + 34, w - 24,
                "MIC  " + SurvOsClient.CONFIG.microphone,
                this::cycleMic, false);

        addToggle(x + 12, y + 70, half, "Always listening",
                () -> SurvOsClient.CONFIG.voiceEnabled && SurvOsClient.VOICE.isRunning(),
                v -> {
                    SurvOsClient.CONFIG.voiceEnabled = v;
                    SurvOsClient.CONFIG.voiceAutoStart = v;
                    if (v) SurvOsClient.VOICE.start(SurvOsClient.CONFIG);
                    else SurvOsClient.VOICE.stop();
                });

        addToggle(x + half + 18, y + 70, half, "HUD",
                () -> SurvOsClient.CONFIG.hudEnabled,
                v -> SurvOsClient.CONFIG.hudEnabled = v);

        addButton(x + 12, y + 106, half,
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

        addButton(x + half + 18, y + 106, half,
                "HUD THEME  " + SurvOsClient.CONFIG.hudTheme,
                () -> {
                    SurvOsClient.CONFIG.hudTheme = switch (SurvOsClient.CONFIG.hudTheme) {
                        case "CYAN" -> "AMBER";
                        case "AMBER" -> "GREEN";
                        case "GREEN" -> "MONO";
                        default -> "CYAN";
                    };
                    SurvOsClient.CONFIG.save();
                    rebuild();
                }, false);

        addToggle(x + 12, y + 142, half, "Smart alerts",
                () -> SurvOsClient.CONFIG.smartAlerts,
                v -> SurvOsClient.CONFIG.smartAlerts = v);

        addToggle(x + half + 18, y + 142, half, "Auto hotbar",
                () -> SurvOsClient.CONFIG.autoHotbar,
                v -> SurvOsClient.CONFIG.autoHotbar = v);

        addButton(x + 12, y + 178, half, "BACKUP SETTINGS",
                () -> SurvOsClient.notice(
                        SurvOsClient.CONFIG.backup() ? "Settings backed up." : "Backup failed."), false);

        addButton(x + half + 18, y + 178, half, "SELF TEST",
                () -> SurvOsClient.notice(SurvOsClient.statusLine(client)), false);

        addButton(x + 12, y + 214, w - 24,
                "F9 MENU  •  F8 VOICE ON/OFF  •  F7 EMERGENCY STOP",
                () -> {}, false);
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

    private TextFieldWidget field(int x, int y, int w, String placeholder) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, 22, Text.literal(placeholder));
        f.setPlaceholder(Text.literal(placeholder));
        f.setMaxLength(120);
        addDrawableChild(f);
        return f;
    }

    private void addButton(int x, int y, int w, String text, Runnable action, boolean strong) {
        addDrawableChild(new SurvButton(x, y, w, 24, Text.literal(text), action, strong));
    }

    private interface BoolGet { boolean get(); }
    private interface BoolSet { void set(boolean value); }

    private void addToggle(int x, int y, int w, String name, BoolGet get, BoolSet set) {
        addButton(x, y, w, name + "  " + (get.get() ? "ON" : "OFF"), () -> {
            set.set(!get.get());
            SurvOsClient.CONFIG.save();
            rebuild();
        }, get.get());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int panelW = Math.min(548, width - 12);
        int left = (width - panelW) / 2;
        int right = left + panelW;

        ctx.fill(0, 0, width, height, 0xF305080C);
        ctx.fill(left, 10, right, Math.min(height - 10, 330), 0xD90A1118);
        ctx.fill(left, 10, right, 12, 0xFF4CE8E2);
        ctx.fill(left, 34, right, 35, 0x554CE8E2);

        ctx.drawTextWithShadow(
                textRenderer,
                Text.literal("SURV // OS").formatted(Formatting.AQUA, Formatting.BOLD),
                left + 14, 18, 0xFFEAFBFF);

        String status = "VOICE " + (SurvOsClient.VOICE.isRunning() ? "ON" : "OFF")
                + "  •  AI " + SurvOsClient.AI.status()
                + "  •  " + SurvOsClient.CONFIG.profile;
        int sw = textRenderer.getWidth(status);
        ctx.drawTextWithShadow(textRenderer, status, right - sw - 14, 19, 0xFF8496A3);

        int contentY = 76;
        ctx.fill(left + 8, contentY, right - 8, Math.min(height - 18, 322), 0x69070D12);

        ctx.drawTextWithShadow(
                textRenderer,
                switch (tab) {
                    case DASHBOARD -> "COMMAND CENTER";
                    case AI -> "SURV AI";
                    case AUTOMATION -> "AUTOMATION";
                    case TOOLS -> "SURVIVAL TOOLS";
                    case SETTINGS -> "SETTINGS";
                },
                left + 14, contentY + 8, 0xFF9FB4C0);

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
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
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
        }

        @Override
        public void appendClickableNarrations(NarrationMessageBuilder builder) {
            appendDefaultNarrations(builder);
        }
    }
}
