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
    public enum Tab {
        DASHBOARD, HUD, CRAFT, VILLAGER, ENCHANT,
        AUTOMATION, VOICE, AI, INVENTORY, WORLD,
        PROFILES, KEYBINDS, ADVANCED
    }

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

        int navY = 38;
        int cols = 5;
        int bw = Math.max(54, Math.min(82, (width - 42) / cols));
        Tab[] tabs = Tab.values();

        for (int i = 0; i < tabs.length; i++) {
            int row = i / cols;
            int col = i % cols;
            int total = bw * cols + 4 * (cols - 1);
            int x = (width - total) / 2 + col * (bw + 4);
            int y = navY + row * 23;
            Tab t = tabs[i];

            addDrawableChild(ButtonWidget.builder(
                    Text.literal(shortName(t)).formatted(t == tab ? Formatting.AQUA : Formatting.GRAY),
                    b -> {
                        tab = t;
                        rebuild();
                    }
            ).dimensions(x, y, bw, 20).build());
        }

        int top = navY + ((tabs.length - 1) / cols + 1) * 23 + 7;

        switch (tab) {
            case DASHBOARD -> dashboard(top);
            case HUD -> hud(top);
            case CRAFT -> craft(top);
            case VILLAGER -> villager(top);
            case ENCHANT -> enchant(top);
            case AUTOMATION -> automation(top);
            case VOICE -> voice(top);
            case AI -> ai(top);
            case INVENTORY -> inventory(top);
            case WORLD -> world(top);
            case PROFILES -> profiles(top);
            case KEYBINDS -> keybinds(top);
            case ADVANCED -> advanced(top);
        }
    }

    private void dashboard(int y) {
        int w = Math.min(420, width - 30);
        int x = width / 2 - w / 2;

        addButton(x, y, w,
                "AI // " + SurvOsClient.AI.status() + "   VOICE // " + SurvOsClient.VOICE.status(),
                b -> { tab = Tab.AI; rebuild(); });

        addButton(x, y + 25, w,
                "PROFILE // " + SurvOsClient.CONFIG.profile,
                b -> { tab = Tab.PROFILES; rebuild(); });

        addButton(x, y + 50, w,
                "AUTO // " + SurvOsClient.AUTOMATION.mode() + " // " + SurvOsClient.AUTOMATION.state(),
                b -> { tab = Tab.AUTOMATION; rebuild(); });

        addButton(x, y + 75, w,
                "CRAFT " + online(SurvOsClient.LEGACY.quickAvailable())
                        + "  •  VILLY " + online(SurvOsClient.LEGACY.tradeAvailable())
                        + "  •  ENCHANT " + online(SurvOsClient.LEGACY.enchantAvailable()),
                b -> {});

        addButton(x, y + 100, w,
                "WORLD MEMORY // " + SurvOsClient.MEMORY.waypointNames().size()
                        + " waypoints • " + SurvOsClient.MEMORY.routeNames().size() + " routes",
                b -> { tab = Tab.WORLD; rebuild(); });

        addButton(x, y + 125, w,
                "SESSION // " + SurvOsClient.STATS.summary(),
                b -> {});
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
        addToggle(x + 194, y + 125, "Voice/AI", () -> SurvOsClient.CONFIG.showVoice, v -> SurvOsClient.CONFIG.showVoice = v);
    }

    private void craft(int y) {
        int w = Math.min(420, width - 30);
        int x = width / 2 - w / 2;

        primaryField = field(x, y, w, "item id, e.g. torch");
        addButton(x, y + 27, w,
                "CRAFT ENGINE // /craft <item>",
                b -> {
                    String item = primaryField.getText().trim();
                    if (item.isBlank()) SurvOsClient.notice("Enter an item, then use /craft while a crafting table is open.");
                    else SurvOsClient.notice("Craft target: " + item + ". Use /craft " + item);
                });
        addButton(x, y + 54, w,
                "ENGINE // " + online(SurvOsClient.LEGACY.quickAvailable()),
                b -> {});
    }

    private void villager(int y) {
        int w = Math.min(420, width - 30);
        int x = width / 2 - w / 2;

        addButton(x, y, w, "OPEN VILLAGER TARGET SETTINGS",
                b -> SurvOsClient.LEGACY.openVillagerSettings(client, this));
        addButton(x, y + 28, w, "START / STOP CYCLER",
                b -> SurvOsClient.LEGACY.toggleVillager(client));
        addButton(x, y + 56, w,
                "ENGINE // " + online(SurvOsClient.LEGACY.tradeAvailable()),
                b -> {});
    }

    private void enchant(int y) {
        int w = Math.min(420, width - 30);
        int x = width / 2 - w / 2;

        addButton(x, y, w, "OPEN ENCHANT LAB // HOLD ITEM OR BOOK",
                b -> SurvOsClient.LEGACY.openEnchantBuilder(client));
        addButton(x, y + 28, w,
                "ENGINE // " + online(SurvOsClient.LEGACY.enchantAvailable()),
                b -> {});
    }

    private void automation(int y) {
        int x = width / 2 - 190;

        primaryField = field(x, y, 245, "goal item, e.g. iron");
        secondaryField = field(x + 251, y, 129, "count");

        addButton(x, y + 27, 186, "SET GOAL",
                b -> {
                    SurvOsClient.AUTOMATION.setGoal(
                            primaryField.getText().trim(),
                            parseInt(secondaryField.getText(), 0));
                    SurvOsClient.notice("Automation goal updated.");
                });

        addButton(x + 194, y + 27, 186, "CLEAR GOAL",
                b -> SurvOsClient.AUTOMATION.setGoal("", 0));

        addButton(x, y + 54, 186, "MOB GRIND",
                b -> SurvOsClient.AUTOMATION.start(AutomationManager.Mode.MOB_GRIND, client));
        addButton(x + 194, y + 54, 186, "MINING",
                b -> SurvOsClient.AUTOMATION.start(AutomationManager.Mode.MINING, client));

        addButton(x, y + 81, 186, "TREE FARM",
                b -> SurvOsClient.AUTOMATION.start(AutomationManager.Mode.TREE_FARM, client));
        addButton(x + 194, y + 81, 186, "CROP FARM",
                b -> SurvOsClient.AUTOMATION.start(AutomationManager.Mode.CROP_FARM, client));

        addButton(x, y + 108, 186, "FISHING",
                b -> SurvOsClient.AUTOMATION.start(AutomationManager.Mode.FISHING, client));
        addButton(x + 194, y + 108, 186, "ANIMAL FARM",
                b -> SurvOsClient.AUTOMATION.start(AutomationManager.Mode.ANIMAL_FARM, client));

        addButton(x, y + 135, 186, "PAUSE",
                b -> SurvOsClient.AUTOMATION.pause(client));
        addButton(x + 194, y + 135, 186, "RESUME",
                b -> SurvOsClient.AUTOMATION.resume());

        addToggle(x, y + 162, "Avoid Creepers",
                () -> SurvOsClient.CONFIG.avoidCreepers,
                v -> SurvOsClient.CONFIG.avoidCreepers = v);

        addToggle(x + 194, y + 162, "Collect Loot",
                () -> SurvOsClient.CONFIG.collectLoot,
                v -> SurvOsClient.CONFIG.collectLoot = v);
    }

    private void voice(int y) {
        int w = Math.min(420, width - 30);
        int x = width / 2 - w / 2;

        addButton(x, y, w,
                SurvOsClient.VOICE.isRunning()
                        ? "MIC // LISTENING — CLICK TO STOP"
                        : "MIC // OFF — CLICK TO START",
                b -> {
                    SurvOsClient.VOICE.toggle(SurvOsClient.CONFIG);
                    rebuild();
                });

        addButton(x, y + 27, w,
                "VOICE STYLE // " + SurvOsClient.CONFIG.voiceStyle,
                b -> {
                    SurvOsClient.CONFIG.voiceStyle = SurvOsClient.TTS.cycleStyle();
                    SurvOsClient.TTS.setStyle(SurvOsClient.CONFIG.voiceStyle);
                    SurvOsClient.CONFIG.save();
                    SurvOsClient.TTS.speak("Voice profile updated.", SurvOsClient.CONFIG.voiceStyle);
                    rebuild();
                });

        addToggle(x, y + 54, "Wake word required",
                () -> SurvOsClient.CONFIG.requireWakeWord,
                v -> SurvOsClient.CONFIG.requireWakeWord = v);

        addToggle(x + 194, y + 54, "Speak AI replies",
                () -> SurvOsClient.CONFIG.aiSpeakReplies,
                v -> {
                    SurvOsClient.CONFIG.aiSpeakReplies = v;
                    SurvOsClient.TTS.setEnabled(v && SurvOsClient.CONFIG.ttsEnabled);
                });

        List<String> mics = SurvOsClient.VOICE.microphones();
        addButton(x, y + 81, w,
                "MIC DEVICE // " + SurvOsClient.CONFIG.microphone,
                b -> {
                    int i = mics.indexOf(SurvOsClient.CONFIG.microphone);
                    if (i < 0) i = 0;
                    SurvOsClient.CONFIG.microphone = mics.get((i + 1) % mics.size());
                    SurvOsClient.CONFIG.save();
                    if (SurvOsClient.VOICE.isRunning()) {
                        SurvOsClient.VOICE.stop();
                        SurvOsClient.VOICE.start(SurvOsClient.CONFIG);
                    }
                    rebuild();
                });

        addButton(x, y + 108, w,
                "LAST HEARD // " + trim(SurvOsClient.VOICE.lastHeard(), 48),
                b -> {});
    }

    private void ai(int y) {
        int w = Math.min(420, width - 30);
        int x = width / 2 - w / 2;

        addButton(x, y, w,
                "LOCAL AI // " + SurvOsClient.AI.status(),
                b -> {
                    if (SurvOsClient.AI.ready()) SurvOsClient.AI.stop();
                    else SurvOsClient.AI.ensureStarted();
                    rebuild();
                });

        primaryField = field(x, y + 28, w, "ask SURV anything about your world...");
        addButton(x, y + 55, w, "ASK SURV",
                b -> {
                    String q = primaryField.getText().trim();
                    if (!q.isBlank()) SurvOsClient.askAi(q, false);
                });

        addButton(x, y + 82, w,
                "LAST REPLY // " + trim(SurvOsClient.AI.lastReply(), 48),
                b -> {});

        addToggle(x, y + 109, "AI enabled",
                () -> SurvOsClient.CONFIG.aiEnabled,
                v -> SurvOsClient.CONFIG.aiEnabled = v);

        addToggle(x + 194, y + 109, "Auto start AI",
                () -> SurvOsClient.CONFIG.aiAutoStart,
                v -> SurvOsClient.CONFIG.aiAutoStart = v);
    }

    private void inventory(int y) {
        int w = Math.min(420, width - 30);
        int x = width / 2 - w / 2;

        addButton(x, y, w, "APPLY MINING LOADOUT",
                b -> InventoryManager.applyLoadout(client, "MINING"));
        addButton(x, y + 27, w, "APPLY COMBAT LOADOUT",
                b -> InventoryManager.applyLoadout(client, "COMBAT"));
        addButton(x, y + 54, w, "APPLY BUILDING LOADOUT",
                b -> InventoryManager.applyLoadout(client, "BUILDING"));

        addButton(x, y + 81, w,
                "DURABILITY PROTECTION // " + SurvOsClient.CONFIG.durabilityStopPercent + "%",
                b -> {
                    SurvOsClient.CONFIG.durabilityStopPercent += 5;
                    if (SurvOsClient.CONFIG.durabilityStopPercent > 25)
                        SurvOsClient.CONFIG.durabilityStopPercent = 5;
                    SurvOsClient.CONFIG.save();
                    rebuild();
                });
    }

    private void world(int y) {
        int x = width / 2 - 190;

        primaryField = field(x, y, 245, "waypoint / route name");
        addButton(x + 251, y, 129, "SAVE WP",
                b -> {
                    String n = primaryField.getText().trim();
                    if (!n.isBlank()) SurvOsClient.MEMORY.setWaypoint(client, n);
                });

        addButton(x, y + 27, 186, "GO WAYPOINT",
                b -> {
                    String n = primaryField.getText().trim();
                    if (!n.isBlank() && !SurvOsClient.AUTOMATION.goToWaypoint(client, n))
                        SurvOsClient.notice("Waypoint not found.");
                });

        addButton(x + 194, y + 27, 186, "PLAY ROUTE",
                b -> {
                    String n = primaryField.getText().trim();
                    if (!n.isBlank() && !SurvOsClient.AUTOMATION.playRoute(client, n))
                        SurvOsClient.notice("Route not found.");
                });

        addButton(x, y + 54, 186, "START ROUTE RECORDING",
                b -> {
                    String n = primaryField.getText().trim();
                    if (!n.isBlank()) {
                        SurvOsClient.MEMORY.startRoute(n);
                        SurvOsClient.notice("Recording route " + n);
                    }
                });

        addButton(x + 194, y + 54, 186, "STOP + SAVE ROUTE",
                b -> SurvOsClient.notice("Saved " + SurvOsClient.MEMORY.stopRoute() + " route points."));

        secondaryField = field(x, y + 83, 245, "search remembered storage");
        addButton(x + 251, y + 83, 129, "FIND",
                b -> {
                    var found = SurvOsClient.MEMORY.containersWith(secondaryField.getText().trim());
                    SurvOsClient.notice(found.isEmpty() ? "Not found in remembered storage." : String.join(" • ", found));
                });

        addButton(x, y + 110, 380,
                "KNOWN // " + SurvOsClient.MEMORY.waypointNames() + " // ROUTES " + SurvOsClient.MEMORY.routeNames(),
                b -> {});
    }

    private void profiles(int y) {
        int x = width / 2 - 190;
        String[] p = {"SURVIVAL", "MINING", "COMBAT", "GRINDING", "NETHER", "BASE", "BUILDING"};

        for (int i = 0; i < p.length; i++) {
            int col = i % 2;
            int row = i / 2;
            String profile = p[i];

            addButton(x + col * 194, y + row * 27, 186, profile,
                    b -> {
                        SurvOsClient.CONFIG.applyProfile(profile);
                        SurvOsClient.notice("Profile: " + profile);
                        rebuild();
                    });
        }
    }

    private void keybinds(int y) {
        int w = Math.min(420, width - 30);
        int x = width / 2 - w / 2;

        addButton(x, y, w, "F9 // OPEN SURV COMMAND CENTER", b -> {});
        addButton(x, y + 27, w, "F8 // TOGGLE VOICE LISTENER", b -> {});
        addButton(x, y + 54, w, "F7 // EMERGENCY STOP AUTOMATION", b -> {});
        addButton(x, y + 81, w, "All can be rebound in Minecraft Controls.", b -> {});
    }

    private void advanced(int y) {
        int x = width / 2 - 190;

        addToggle(x, y, "Smart alerts",
                () -> SurvOsClient.CONFIG.smartAlerts,
                v -> SurvOsClient.CONFIG.smartAlerts = v);

        addToggle(x + 194, y, "Low-health safety",
                () -> SurvOsClient.CONFIG.lowHealthSafety,
                v -> SurvOsClient.CONFIG.lowHealthSafety = v);

        addButton(x, y + 28, 186, "RULE: HEALTH < 3 HEARTS",
                b -> SurvOsClient.RULES.add(
                        new RuleEngine.Rule(RuleEngine.Kind.HEALTH_BELOW, 3, null)));

        addButton(x + 194, y + 28, 186, "RULE: FREE SLOTS <= 2",
                b -> SurvOsClient.RULES.add(
                        new RuleEngine.Rule(RuleEngine.Kind.INVENTORY_FREE_AT_MOST, 2, null)));

        addButton(x, y + 55, 186, "RULE: DURA < 10%",
                b -> SurvOsClient.RULES.add(
                        new RuleEngine.Rule(RuleEngine.Kind.DURABILITY_BELOW, 10, null)));

        addButton(x + 194, y + 55, 186, "CLEAR RULES",
                b -> SurvOsClient.RULES.clear());

        addButton(x, y + 82, 380,
                "SELF TEST // AI " + SurvOsClient.AI.status()
                        + " • Voice " + SurvOsClient.VOICE.status()
                        + " • Craft " + online(SurvOsClient.LEGACY.quickAvailable())
                        + " • Villy " + online(SurvOsClient.LEGACY.tradeAvailable())
                        + " • Enchant " + online(SurvOsClient.LEGACY.enchantAvailable()),
                b -> SurvOsClient.notice(SurvOsClient.statusLine(client)));

        addButton(x, y + 109, 380,
                "RESET SESSION STATS",
                b -> {
                    SurvOsClient.STATS.reset();
                    SurvOsClient.notice("Session statistics reset.");
                });
    }

    private TextFieldWidget field(int x, int y, int w, String placeholder) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, 20, Text.literal(placeholder));
        f.setPlaceholder(Text.literal(placeholder));
        addDrawableChild(f);
        return f;
    }

    private void addButton(int x, int y, int w, String text, ButtonWidget.PressAction action) {
        addDrawableChild(ButtonWidget.builder(Text.literal(text), action)
                .dimensions(x, y, w, 21)
                .build());
    }

    private interface BoolGet { boolean get(); }
    private interface BoolSet { void set(boolean v); }

    private void addToggle(int x, int y, String name, BoolGet get, BoolSet set) {
        addButton(x, y, 186,
                name + " // " + (get.get() ? "ON" : "OFF"),
                b -> {
                    set.set(!get.get());
                    SurvOsClient.CONFIG.save();
                    rebuild();
                });
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xF0080D12);
        ctx.fill(0, 0, width, 4, 0xFF58E6E6);
        ctx.fill(0, 4, width, 34, 0xEE101820);

        ctx.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal("SURV // OS").formatted(Formatting.AQUA, Formatting.BOLD),
                width / 2, 9, 0xFFFFFF);

        ctx.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal("LOCAL SURVIVAL INTELLIGENCE  •  " + SurvOsClient.CONFIG.profile + "  •  F9")
                        .formatted(Formatting.DARK_GRAY),
                width / 2, 22, 0xFFFFFF);

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

    private static String shortName(Tab t) {
        return switch (t) {
            case DASHBOARD -> "HOME";
            case AUTOMATION -> "AUTO";
            case INVENTORY -> "INV";
            case VILLAGER -> "VILLY";
            case KEYBINDS -> "KEYS";
            case ADVANCED -> "ADV";
            default -> t.name();
        };
    }

    private static String online(boolean ok) {
        return ok ? "ONLINE" : "OFFLINE";
    }

    private static String trim(String s, int n) {
        if (s == null || s.isBlank()) return "—";
        return s.length() <= n ? s : s.substring(0, n - 1) + "…";
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
