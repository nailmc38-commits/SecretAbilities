package com.survos;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import java.util.List;

public final class SurvScreen extends Screen {
    public enum Tab { DASHBOARD, HUD, CRAFT, VILLAGER, ENCHANT, AUTOMATION, VOICE, INVENTORY, PROFILES, ADVANCED }
    private Tab tab;
    private TextFieldWidget textField;

    public SurvScreen(Tab tab) {
        super(Text.literal("SURV // OS"));
        this.tab = tab == null ? Tab.DASHBOARD : tab;
    }

    @Override protected void init() { rebuild(); }

    private void rebuild() {
        clearChildren();
        int navY = 39;
        int available = Math.max(320, width - 20);
        int bw = Math.max(55, Math.min(82, (available - 18) / 5));
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            int row = i / 5, col = i % 5;
            int total = bw * 5 + 4 * 4;
            int x = (width - total) / 2 + col * (bw + 4);
            int y = navY + row * 23;
            Tab t = tabs[i];
            addDrawableChild(ButtonWidget.builder(
                    Text.literal(shortName(t)).formatted(t == tab ? Formatting.AQUA : Formatting.GRAY),
                    b -> { tab = t; rebuild(); }).dimensions(x, y, bw, 20).build());
        }
        int top = 92;
        switch (tab) {
            case DASHBOARD -> dashboard(top); case HUD -> hud(top); case CRAFT -> craft(top);
            case VILLAGER -> villager(top); case ENCHANT -> enchant(top); case AUTOMATION -> automation(top);
            case VOICE -> voice(top); case INVENTORY -> inventory(top); case PROFILES -> profiles(top);
            case ADVANCED -> advanced(top);
        }
    }

    private void dashboard(int y) {
        int w = Math.min(400, width - 30), x = width / 2 - w / 2;
        addButton(x, y, w, "PROFILE // " + SurvOsClient.CONFIG.profile, b -> { tab = Tab.PROFILES; rebuild(); });
        addButton(x, y + 26, w, "AUTOMATION // " + SurvOsClient.AUTOMATION.mode() + " // " + SurvOsClient.AUTOMATION.state(), b -> { tab = Tab.AUTOMATION; rebuild(); });
        addButton(x, y + 52, w, "VOICE // " + SurvOsClient.VOICE.status(), b -> { tab = Tab.VOICE; rebuild(); });
        addButton(x, y + 78, w, "CRAFT // " + online(SurvOsClient.LEGACY.quickAvailable()), b -> { tab = Tab.CRAFT; rebuild(); });
        addButton(x, y + 104, w, "VILLAGER // " + online(SurvOsClient.LEGACY.tradeAvailable()), b -> { tab = Tab.VILLAGER; rebuild(); });
        addButton(x, y + 130, w, "ENCHANT // " + online(SurvOsClient.LEGACY.enchantAvailable()), b -> { tab = Tab.ENCHANT; rebuild(); });
    }

    private void hud(int y) {
        int x = width / 2 - 190;
        addToggle(x, y, "HUD", () -> SurvOsClient.CONFIG.hudEnabled, v -> SurvOsClient.CONFIG.hudEnabled = v);
        addToggle(x + 194, y, "Compact", () -> SurvOsClient.CONFIG.compactHud, v -> SurvOsClient.CONFIG.compactHud = v);
        addToggle(x, y + 25, "Health", () -> SurvOsClient.CONFIG.showHealth, v -> SurvOsClient.CONFIG.showHealth = v);
        addToggle(x + 194, y + 25, "Hunger", () -> SurvOsClient.CONFIG.showHunger, v -> SurvOsClient.CONFIG.showHunger = v);
        addToggle(x, y + 50, "Armor", () -> SurvOsClient.CONFIG.showArmor, v -> SurvOsClient.CONFIG.showArmor = v);
        addToggle(x + 194, y + 50, "XP", () -> SurvOsClient.CONFIG.showXp, v -> SurvOsClient.CONFIG.showXp = v);
        addToggle(x, y + 75, "Coordinates", () -> SurvOsClient.CONFIG.showCoords, v -> SurvOsClient.CONFIG.showCoords = v);
        addToggle(x + 194, y + 75, "Day/Night", () -> SurvOsClient.CONFIG.showDayNight, v -> SurvOsClient.CONFIG.showDayNight = v);
        addToggle(x, y + 100, "Durability", () -> SurvOsClient.CONFIG.showDurability, v -> SurvOsClient.CONFIG.showDurability = v);
        addToggle(x + 194, y + 100, "Inventory", () -> SurvOsClient.CONFIG.showInventory, v -> SurvOsClient.CONFIG.showInventory = v);
        addToggle(x, y + 125, "Hostiles", () -> SurvOsClient.CONFIG.showHostiles, v -> SurvOsClient.CONFIG.showHostiles = v);
        addToggle(x + 194, y + 125, "Voice", () -> SurvOsClient.CONFIG.showVoice, v -> SurvOsClient.CONFIG.showVoice = v);
    }

    private void craft(int y) {
        int w = Math.min(410, width - 30), x = width / 2 - w / 2;
        textField = new TextFieldWidget(textRenderer, x, y, w, 20, Text.literal("minecraft:item"));
        textField.setPlaceholder(Text.literal("item id, e.g. torch"));
        addDrawableChild(textField);
        addButton(x, y + 27, w, "CRAFT ENGINE // USE /craft <item>", b -> SurvOsClient.notice("Use /craft <item> while a crafting table is open"));
        addButton(x, y + 54, w, "Engine: " + online(SurvOsClient.LEGACY.quickAvailable()), b -> {});
    }

    private void villager(int y) {
        int w = Math.min(410, width - 30), x = width / 2 - w / 2;
        addButton(x, y, w, "OPEN VILLAGER TARGET SETTINGS", b -> SurvOsClient.LEGACY.openVillagerSettings(client, this));
        addButton(x, y + 28, w, "START / STOP CYCLER", b -> SurvOsClient.LEGACY.toggleVillager(client));
        addButton(x, y + 56, w, "Engine: " + online(SurvOsClient.LEGACY.tradeAvailable()), b -> {});
    }

    private void enchant(int y) {
        int w = Math.min(410, width - 30), x = width / 2 - w / 2;
        addButton(x, y, w, "OPEN ENCHANT LAB (hold item/book)", b -> SurvOsClient.LEGACY.openEnchantBuilder(client));
        addButton(x, y + 28, w, "Engine: " + online(SurvOsClient.LEGACY.enchantAvailable()), b -> {});
    }

    private void automation(int y) {
        int x = width / 2 - 190;
        addButton(x, y, 186, "MOB GRIND", b -> SurvOsClient.AUTOMATION.start(AutomationManager.Mode.MOB_GRIND, client));
        addButton(x + 194, y, 186, "MINING", b -> SurvOsClient.AUTOMATION.start(AutomationManager.Mode.MINING, client));
        addButton(x, y + 26, 186, "TREE FARM", b -> SurvOsClient.AUTOMATION.start(AutomationManager.Mode.TREE_FARM, client));
        addButton(x + 194, y + 26, 186, "CROP FARM", b -> SurvOsClient.AUTOMATION.start(AutomationManager.Mode.CROP_FARM, client));
        addButton(x, y + 52, 186, "FISHING", b -> SurvOsClient.AUTOMATION.start(AutomationManager.Mode.FISHING, client));
        addButton(x + 194, y + 52, 186, "ANIMAL FARM", b -> SurvOsClient.AUTOMATION.start(AutomationManager.Mode.ANIMAL_FARM, client));
        addButton(x, y + 78, 186, "PAUSE", b -> SurvOsClient.AUTOMATION.pause(client));
        addButton(x + 194, y + 78, 186, "RESUME", b -> SurvOsClient.AUTOMATION.resume());
        addToggle(x, y + 108, "Avoid Creepers", () -> SurvOsClient.CONFIG.avoidCreepers, v -> SurvOsClient.CONFIG.avoidCreepers = v);
        addToggle(x + 194, y + 108, "Collect Loot", () -> SurvOsClient.CONFIG.collectLoot, v -> SurvOsClient.CONFIG.collectLoot = v);
        addToggle(x, y + 133, "Debug State", () -> SurvOsClient.CONFIG.debugAutomation, v -> SurvOsClient.CONFIG.debugAutomation = v);
    }

    private void voice(int y) {
        int w = Math.min(410, width - 30), x = width / 2 - w / 2;
        addButton(x, y, w, SurvOsClient.VOICE.isRunning() ? "VOICE: LISTENING // CLICK TO STOP" : "VOICE: OFF // CLICK TO START",
                b -> { SurvOsClient.VOICE.toggle(SurvOsClient.CONFIG); rebuild(); });
        addToggle(x, y + 28, "Wake word required", () -> SurvOsClient.CONFIG.requireWakeWord, v -> SurvOsClient.CONFIG.requireWakeWord = v);
        addToggle(x + 194, y + 28, "Auto start", () -> SurvOsClient.CONFIG.voiceAutoStart, v -> SurvOsClient.CONFIG.voiceAutoStart = v);
        List<String> mics = SurvOsClient.VOICE.microphones();
        addButton(x, y + 56, w, "MIC // " + SurvOsClient.CONFIG.microphone, b -> {
            int i = mics.indexOf(SurvOsClient.CONFIG.microphone);
            SurvOsClient.CONFIG.microphone = mics.get((i + 1 + mics.size()) % mics.size());
            SurvOsClient.CONFIG.save();
            if (SurvOsClient.VOICE.isRunning()) { SurvOsClient.VOICE.stop(); SurvOsClient.VOICE.start(SurvOsClient.CONFIG); }
            rebuild();
        });
        addButton(x, y + 84, w, "Last heard // " + trim(SurvOsClient.VOICE.lastHeard(), 45), b -> {});
    }

    private void inventory(int y) {
        int w = Math.min(410, width - 30), x = width / 2 - w / 2;
        addButton(x, y, w, "SMART HOTBAR // rules module", b -> {});
        addButton(x, y + 28, w, "LOADOUTS // MINING • COMBAT • BUILDING", b -> {});
        addButton(x, y + 56, w, "DURABILITY PROTECTION // " + SurvOsClient.CONFIG.durabilityStopPercent + "%", b -> {
            SurvOsClient.CONFIG.durabilityStopPercent += 5;
            if (SurvOsClient.CONFIG.durabilityStopPercent > 25) SurvOsClient.CONFIG.durabilityStopPercent = 5;
            SurvOsClient.CONFIG.save(); rebuild();
        });
    }

    private void profiles(int y) {
        int x = width / 2 - 190;
        String[] p = {"SURVIVAL","MINING","COMBAT","GRINDING","NETHER","BASE","BUILDING"};
        for (int i = 0; i < p.length; i++) {
            int col = i % 2, row = i / 2;
            String profile = p[i];
            addButton(x + col * 194, y + row * 27, 186, profile, b -> {
                SurvOsClient.CONFIG.applyProfile(profile); SurvOsClient.notice("Profile: " + profile); rebuild();
            });
        }
    }

    private void advanced(int y) {
        int w = Math.min(410, width - 30), x = width / 2 - w / 2;
        addToggle(x, y, "Smart alerts", () -> SurvOsClient.CONFIG.smartAlerts, v -> SurvOsClient.CONFIG.smartAlerts = v);
        addToggle(x + 194, y, "Low-health safety", () -> SurvOsClient.CONFIG.lowHealthSafety, v -> SurvOsClient.CONFIG.lowHealthSafety = v);
        addButton(x, y + 30, w, "Emergency backup key: F7 (changeable in Controls)", b -> {});
    }

    private void addButton(int x, int y, int w, String text, ButtonWidget.PressAction action) {
        addDrawableChild(ButtonWidget.builder(Text.literal(text), action).dimensions(x, y, w, 21).build());
    }
    private interface BoolGet { boolean get(); }
    private interface BoolSet { void set(boolean v); }
    private void addToggle(int x, int y, String name, BoolGet get, BoolSet set) {
        addButton(x, y, 186, name + " // " + (get.get() ? "ON" : "OFF"), b -> {
            set.set(!get.get()); SurvOsClient.CONFIG.save(); rebuild();
        });
    }

    @Override public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xF0080D12);
        ctx.fill(0, 0, width, 4, 0xFF58E6E6);
        ctx.fill(0, 4, width, 34, 0xEE101820);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("SURV // OS").formatted(Formatting.AQUA, Formatting.BOLD), width / 2, 9, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("SURVIVAL COMMAND SYSTEM  •  " + SurvOsClient.CONFIG.profile + "  •  F9").formatted(Formatting.DARK_GRAY), width / 2, 22, 0xFFFFFF);
        if (tab == Tab.DASHBOARD) {
            int yy = Math.min(height - 35, 266);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Say “Surv, status” • “Surv, start mob grinder” • “Surv, stop”").formatted(Formatting.GRAY), width / 2, yy, 0xFFFFFF);
        }
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_F9 || input.key() == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        return super.keyPressed(input);
    }
    @Override public boolean shouldPause() { return false; }

    private static String shortName(Tab t) {
        return switch (t) {
            case DASHBOARD -> "HOME"; case AUTOMATION -> "AUTO"; case INVENTORY -> "INV";
            case VILLAGER -> "VILLY"; case ADVANCED -> "ADV"; default -> t.name();
        };
    }
    private static String online(boolean ok) { return ok ? "ONLINE" : "OFFLINE"; }
    private static String trim(String s, int n) {
        if (s == null || s.isBlank()) return "—";
        return s.length() <= n ? s : s.substring(0, n - 1) + "…";
    }
}
