package com.survivalutils;

import com.secret.autoenchanter.AutoEnchanterClient;
import com.secret.tradecycler.TradeCyclerClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class SurvMegaHubScreen extends Screen {
    private static final List<String> TABS = List.of(
            "DASHBOARD",
            "HUD",
            "VISOR",
            "WARNINGS",
            "INTEL",
            "AUTOMATION",
            "MOB CONTROL",
            "TOOLS",
            "LIBRARY",
            "SETTINGS"
    );

    private String tab;
    private int sidebarScroll;
    private int bookPage;

    public SurvMegaHubScreen() {
        this("DASHBOARD");
    }

    public SurvMegaHubScreen(String tab) {
        super(Text.literal("SURV // OS"));
        this.tab = TABS.contains(tab) ? tab : "DASHBOARD";
    }

    @Override
    protected void init() {
        SurvMegaState.ensureLoaded();
        clearChildren();

        int l = panelLeft();
        int r = panelRight();
        int sideW = 130;
        int top = 49;
        int visible = Math.max(7, (height - 74) / 28);
        sidebarScroll = Math.max(0, Math.min(sidebarScroll, Math.max(0, TABS.size() - visible)));

        for (int i = 0; i < visible && i + sidebarScroll < TABS.size(); i++) {
            String name = TABS.get(i + sidebarScroll);
            boolean active = name.equals(tab);
            addDrawableChild(ButtonWidget.builder(
                    Text.literal((active ? "◆ " : "  ") + name),
                    b -> {
                        tab = name;
                        rebuild();
                    }
            ).dimensions(l + 10, top + i * 28, sideW - 18, 22).build());
        }

        int x = l + sideW + 16;
        int y = 63;
        int w = r - x - 14;

        switch (tab) {
            case "DASHBOARD" -> buildDashboard(x, y, w);
            case "HUD" -> buildHud(x, y, w);
            case "VISOR" -> buildVisor(x, y, w);
            case "WARNINGS" -> buildWarnings(x, y, w);
            case "INTEL" -> buildIntel(x, y, w);
            case "AUTOMATION" -> buildAutomation(x, y, w);
            case "MOB CONTROL" -> buildMobControl(x, y, w);
            case "TOOLS" -> buildTools(x, y, w);
            case "LIBRARY" -> buildLibrary(x, y, w);
            case "SETTINGS" -> buildSettings(x, y, w);
        }
    }

    private void buildDashboard(int x, int y, int w) {
        int half = (w - 8) / 2;
        button(x, y, half, "OPEN HUD SETTINGS", () -> client.setScreen(new SurvivalUtilsScreen()));
        button(x + half + 8, y, half, "SAVE", () -> {
            SurvMegaState.save();
            SurvivalUtilsClient.CONFIG.save();
        });

        y += 34;
        toggle(x, y, half, "EXO VISOR", () -> SurvMegaState.SETTINGS.exo,
                v -> SurvMegaState.SETTINGS.exo = v);
        toggle(x + half + 8, y, half, "IDENTIFY", () -> SurvMegaState.SETTINGS.identify,
                v -> SurvMegaState.SETTINGS.identify = v);

        y += 34;
        toggle(x, y, half, "TRACKER", () -> SurvMegaState.SETTINGS.tracker,
                v -> SurvMegaState.SETTINGS.tracker = v);
        toggle(x + half + 8, y, half, "MOB CONTROL", () -> SurvMegaState.SETTINGS.mobControl,
                v -> SurvMegaState.SETTINGS.mobControl = v);
    }

    private void buildHud(int x, int y, int w) {
        int half = (w - 8) / 2;

        button(x, y, half,
                "MAIN HUD  " + onOff(SurvivalUtilsClient.CONFIG.isEnabled(Feature.MAIN_HUD)),
                () -> {
                    SurvivalUtilsClient.CONFIG.toggle(Feature.MAIN_HUD);
                    rebuild();
                });

        button(x + half + 8, y, half,
                "LEFT STATS  " + onOff(SurvivalUtilsClient.CONFIG.isEnabled(Feature.STATS_PANEL)),
                () -> {
                    SurvivalUtilsClient.CONFIG.toggle(Feature.STATS_PANEL);
                    rebuild();
                });

        y += 34;
        button(x, y, half,
                "HELMET HUD  " + onOff(SurvivalUtilsClient.CONFIG.isEnabled(Feature.HELMET_OVERLAY)),
                () -> {
                    SurvivalUtilsClient.CONFIG.toggle(Feature.HELMET_OVERLAY);
                    rebuild();
                });

        button(x + half + 8, y, half,
                "HUD SIDE  " + (SurvivalUtilsClient.CONFIG.hudRight ? "RIGHT" : "LEFT"),
                () -> {
                    SurvivalUtilsClient.CONFIG.hudRight = !SurvivalUtilsClient.CONFIG.hudRight;
                    SurvivalUtilsClient.CONFIG.save();
                    rebuild();
                });

        y += 34;
        button(x, y, w, "DETAILED ORIGINAL HUD SETTINGS",
                () -> client.setScreen(new SurvivalUtilsScreen()));
    }

    private void buildVisor(int x, int y, int w) {
        int half = (w - 8) / 2;
        toggle(x, y, half, "POWER ARMOR VISOR", () -> SurvMegaState.SETTINGS.exo,
                v -> SurvMegaState.SETTINGS.exo = v);
        toggle(x + half + 8, y, half, "DAMAGE CRACKS", () -> SurvMegaState.SETTINGS.visorDamage,
                v -> SurvMegaState.SETTINGS.visorDamage = v);

        y += 34;
        toggle(x, y, half, "IDENTIFY CARDS", () -> SurvMegaState.SETTINGS.identify,
                v -> SurvMegaState.SETTINGS.identify = v);
        button(x + half + 8, y, half, "CLEAR VISOR DAMAGE", SurvMegaState::clearVisor);
    }

    private void buildWarnings(int x, int y, int w) {
        int half = (w - 8) / 2;

        button(x, y, half,
                "BANNER  " + onOff(SurvivalUtilsClient.CONFIG.warningBanner),
                () -> {
                    SurvivalUtilsClient.CONFIG.warningBanner = !SurvivalUtilsClient.CONFIG.warningBanner;
                    SurvivalUtilsClient.CONFIG.save();
                    rebuild();
                });

        button(x + half + 8, y, half,
                "ACTION BAR  " + onOff(SurvivalUtilsClient.CONFIG.warningActionbar),
                () -> {
                    SurvivalUtilsClient.CONFIG.warningActionbar = !SurvivalUtilsClient.CONFIG.warningActionbar;
                    SurvivalUtilsClient.CONFIG.save();
                    rebuild();
                });

        y += 34;
        button(x, y, half, "ENABLE CORE WARNINGS", () -> {
            SurvivalUtilsClient.CONFIG.setCategory(Feature.Category.ALERTS, true);
            SurvivalUtilsClient.CONFIG.set(Feature.CREEPER_ALERT, true);
            SurvivalUtilsClient.CONFIG.set(Feature.SPECTATOR_ALERT, true);
            SurvivalUtilsClient.CONFIG.set(Feature.PLAYER_PROXIMITY_ALERT, true);
            SurvivalUtilsClient.CONFIG.set(Feature.NO_TOTEM_ALERT, true);
            rebuild();
        });

        button(x + half + 8, y, half, "WARNING SETTINGS",
                () -> client.setScreen(new SurvivalUtilsScreen()));
    }

    private void buildIntel(int x, int y, int w) {
        int half = (w - 8) / 2;
        toggle(x, y, half, "PLAYER TRACKER", () -> SurvMegaState.SETTINGS.tracker,
                v -> SurvMegaState.SETTINGS.tracker = v);
        toggle(x + half + 8, y, half, "SATELLITE", () -> SurvMegaState.SETTINGS.satellite,
                v -> SurvMegaState.SETTINGS.satellite = v);

        y += 34;
        button(x, y, half, "OPEN ECHO 3D", () -> client.setScreen(new Echo3DScreen(this)));
        button(x + half + 8, y, half, "CLEAR TRACKER HISTORY", SurvMegaState::clearTracker);
    }

    private void buildAutomation(int x, int y, int w) {
        int half = (w - 8) / 2;

        toggle(x, y, half, "MACROS", () -> SurvMegaState.SETTINGS.macros,
                v -> SurvMegaState.SETTINGS.macros = v);
        toggle(x + half + 8, y, half, "COMBAT MUSIC", () -> SurvMegaState.SETTINGS.aura,
                v -> SurvMegaState.SETTINGS.aura = v);

        y += 34;
        button(x, y, half,
                SurvMegaState.macroRecording() ? "STOP RECORDING" : "RECORD MACRO",
                () -> {
                    if (SurvMegaState.macroRecording()) SurvMegaState.stopMacro(client);
                    else SurvMegaState.startMacroRecording();
                    rebuild();
                });

        button(x + half + 8, y, half,
                SurvMegaState.macroPlaying() ? "STOP PLAYBACK" : "PLAY MACRO",
                () -> {
                    if (SurvMegaState.macroPlaying()) SurvMegaState.stopMacro(client);
                    else SurvMegaState.playMacro();
                    rebuild();
                });

        y += 34;
        toggle(x, y, half, "TRAP ASSIST", () -> SurvMegaState.SETTINGS.trap,
                v -> SurvMegaState.SETTINGS.trap = v);
        toggle(x + half + 8, y, half, "SURV SCRIPT", () -> SurvMegaState.SETTINGS.scripts,
                v -> SurvMegaState.SETTINGS.scripts = v);
    }

    private void buildMobControl(int x, int y, int w) {
        int half = (w - 8) / 2;

        button(x, y, half, "SELECT LOOKED-AT TAME",
                () -> MobControlManager.toggleLookedAt(client));
        button(x + half + 8, y, half, "SELECT MY TAMES // 32m",
                () -> MobControlManager.selectNearby(client));

        y += 34;
        button(x, y, half, "ORDER // HOLD",
                () -> MobControlManager.order(client, MobControlManager.Order.HOLD));
        button(x + half + 8, y, half, "ORDER // FOLLOW",
                () -> MobControlManager.order(client, MobControlManager.Order.FOLLOW));

        y += 34;
        button(x, y, w, "CLEAR SQUAD",
                () -> MobControlManager.clear(client));
    }

    private void buildTools(int x, int y, int w) {
        int half = (w - 8) / 2;

        button(x, y, half, "AUTO ENCHANTER",
                () -> AutoEnchanterClient.openBuilder(client));

        button(x + half + 8, y, half, "VILLAGER CYCLER SETTINGS",
                () -> TradeCyclerClient.openSettings(client, this));

        y += 34;
        button(x, y, half, "START / STOP VILLAGER CYCLER",
                () -> {
                    TradeCyclerClient.toggle(client);
                    rebuild();
                });

        button(x + half + 8, y, half, "SEED CRACKER",
                () -> SeedCrackerShortcut.runNow());

        y += 34;
        button(x, y, w, "ORIGINAL SURVIVAL UTILITIES",
                () -> client.setScreen(new SurvivalUtilsScreen()));
    }

    private void buildLibrary(int x, int y, int w) {
        int half = (w - 8) / 2;
        button(x, y, half, "◀ PAGE", () -> {
            bookPage = Math.max(0, bookPage - 1);
        });
        button(x + half + 8, y, half, "PAGE ▶", () -> bookPage++);
    }

    private void buildSettings(int x, int y, int w) {
        int half = (w - 8) / 2;

        button(x, y, half, "SAVE ALL", () -> {
            SurvMegaState.save();
            SurvivalUtilsClient.CONFIG.save();
        });

        button(x + half + 8, y, half, "CLEAR EVENT LOG", SurvMegaState::clearLogs);

        y += 34;
        button(x, y, half, "RELOAD SURV SCRIPTS", () -> {
            SurvMegaState.reloadScripts();
            rebuild();
        });

        button(x + half + 8, y, half, "RESET VISOR DAMAGE", SurvMegaState::clearVisor);
    }

    private interface BoolGet { boolean get(); }
    private interface BoolSet { void set(boolean value); }

    private void toggle(int x, int y, int w, String label, BoolGet get, BoolSet set) {
        button(x, y, w, label + "  " + onOff(get.get()), () -> {
            set.set(!get.get());
            SurvMegaState.save();
            rebuild();
        });
    }

    private void button(int x, int y, int w, String label, Runnable action) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> action.run())
                .dimensions(x, y, w, 24)
                .build());
    }

    private String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }

    private void rebuild() {
        clearChildren();
        init();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int l = panelLeft();
        if (mouseX >= l && mouseX <= l + 130) {
            int visible = Math.max(7, (height - 74) / 28);
            int max = Math.max(0, TABS.size() - visible);
            int old = sidebarScroll;
            sidebarScroll = Math.max(0, Math.min(max, sidebarScroll - (int)Math.round(vertical)));
            if (old != sidebarScroll) {
                rebuild();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int l = panelLeft();
        int r = panelRight();

        ctx.fill(0, 0, width, height, 0xF205080B);
        ctx.fill(l, 10, r, height - 10, 0xF20B1115);
        ctx.fill(l, 10, r, 13, 0xFF58D9E7);
        ctx.fill(l + 130, 43, l + 131, height - 18, 0x554C9EAA);

        ctx.drawTextWithShadow(
                textRenderer,
                Text.literal("SURV // OS").formatted(Formatting.AQUA, Formatting.BOLD),
                l + 14, 20, 0xFFFFFFFF);

        String header = tab + "  //  CLEAN BUILD";
        ctx.drawTextWithShadow(textRenderer, header, r - textRenderer.getWidth(header) - 14, 21, 0xFF8DA4AD);

        drawContent(ctx, l + 146, 178, r - l - 160);
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawContent(DrawContext ctx, int x, int y, int w) {
        switch (tab) {
            case "DASHBOARD" -> {
                line(ctx, x, y, "Original HUD preserved. New systems layer around it.", 0xFF9FB4C0); y += 15;
                line(ctx, x, y, "Advisor // " + SurvMegaState.recommendation(), 0xFF70E6F0); y += 15;
                line(ctx, x, y, "Warnings // prioritized + persistent while danger exists", 0xFFFFC96B); y += 15;
                line(ctx, x, y, "Mob squad // " + MobControlManager.selectedCount() + " selected", 0xFFB8DDE7);
            }
            case "HUD" -> {
                line(ctx, x, y, "This is the same Survival Utils HUD layout you had before.", 0xFF9FB4C0); y += 15;
                line(ctx, x, y, "The clean build does not replace it with Mega panels.", 0xFFB8DDE7);
            }
            case "VISOR" -> {
                if (client != null && client.player != null) {
                    line(ctx, x, y, "Armor // " + SurvMegaState.armorName(client.player), 0xFF7CE1EE); y += 15;
                    line(ctx, x, y, "Integrity // " + SurvMegaState.armorIntegrity(client.player) + "%", 0xFFB8DDE7); y += 15;
                    line(ctx, x, y, "Visor damage // " + (int)Math.round(SurvMegaState.visorDamage() * 100) + "%", 0xFFD5EEF4); y += 15;
                }
                line(ctx, x, y, "Cracks repair after 5 minutes without damage when armor is above 50%.", 0xFF8DA4AD);
            }
            case "WARNINGS" -> {
                var warning = SurvivalUtilsClient.WARNINGS.active();
                if (warning == null) {
                    line(ctx, x, y, "ACTIVE // NONE", 0xFF6FE5A1);
                } else {
                    line(ctx, x, y, "ACTIVE // " + warning.severity(), 0xFFFFB86B); y += 15;
                    wrap(ctx, x, y, w, warning.text(), 0xFFE8F1F4);
                }
            }
            case "INTEL" -> {
                line(ctx, x, y, "Tracker remembers only player positions your client actually observed.", 0xFF9FB4C0); y += 15;
                line(ctx, x, y, "ECHO is the 3D loaded-world viewer, not a minigame.", 0xFFB8DDE7); y += 15;
                line(ctx, x, y, "Tracked players // " + SurvMegaState.tracked().size(), 0xFF7CE1EE);
            }
            case "AUTOMATION" -> {
                line(ctx, x, y, "Macros // " + SurvMegaState.macroFrames() + " recorded frames", 0xFF7CE1EE); y += 15;
                line(ctx, x, y, "Trap assist // progressive web/box placement", 0xFFB8DDE7); y += 15;
                line(ctx, x, y, "SURV Script files live in config/surv-os/scripts", 0xFF8DA4AD);
            }
            case "MOB CONTROL" -> {
                line(ctx, x, y, "Selected squad // " + MobControlManager.selectedCount(), 0xFF7CE1EE); y += 15;
                line(ctx, x, y, "Order // " + MobControlManager.queuedOrder(), 0xFFB8DDE7); y += 15;
                line(ctx, x, y, "Real control works on tameable mobs you own.", 0xFF9FB4C0); y += 15;
                line(ctx, x, y, "HOLD makes reachable squad members sit; FOLLOW makes them stand and use vanilla follow AI.", 0xFF8DA4AD);
            }
            case "TOOLS" -> {
                line(ctx, x, y, "AutoEnchanter // " + AutoEnchanterClient.status(), 0xFF7CE1EE); y += 15;
                line(ctx, x, y, "Villager Cycler // " + TradeCyclerClient.status(), 0xFF7CE1EE); y += 15;
                line(ctx, x, y, "These are integrated source modules, not hidden nested helper mods.", 0xFF9FB4C0);
            }
            case "LIBRARY" -> drawBook(ctx, x, y, w);
            case "SETTINGS" -> {
                line(ctx, x, y, "Stored data is intentionally easy to clear.", 0xFF9FB4C0); y += 15;
                line(ctx, x, y, "No game saves exist in this build.", 0xFFB8DDE7);
            }
        }
    }

    private void drawBook(DrawContext ctx, int x, int y, int w) {
        List<String> lines = SurvMegaState.readBook("under_the_bridge.txt");
        int per = Math.max(8, (height - y - 35) / 13);
        int pages = Math.max(1, (lines.size() + per - 1) / per);
        bookPage = Math.max(0, Math.min(bookPage, pages - 1));
        int start = bookPage * per;
        int end = Math.min(lines.size(), start + per);

        for (int i = start; i < end; i++) {
            String s = lines.get(i);
            int color = s.startsWith("CHAPTER") || s.equals("UNDER THE BRIDGE")
                    ? 0xFF7CE1EE : 0xFFD9E7EC;
            line(ctx, x, y, s, color);
            y += 13;
        }
        line(ctx, x, y + 5, "Page " + (bookPage + 1) + " / " + pages, 0xFF718894);
    }

    private void line(DrawContext ctx, int x, int y, String s, int color) {
        if (y > height - 24) return;
        ctx.drawTextWithShadow(textRenderer, s, x, y, color);
    }

    private void wrap(DrawContext ctx, int x, int y, int w, String s, int color) {
        int max = Math.max(24, w / 7);
        String remaining = s;
        int yy = y;
        while (remaining.length() > max && yy < height - 24) {
            int cut = remaining.lastIndexOf(' ', max);
            if (cut < 10) cut = max;
            line(ctx, x, yy, remaining.substring(0, cut), color);
            remaining = remaining.substring(cut).trim();
            yy += 13;
        }
        line(ctx, x, yy, remaining, color);
    }

    private int panelLeft() {
        return Math.max(6, (width - Math.min(780, width - 12)) / 2);
    }

    private int panelRight() {
        return Math.min(width - 6, panelLeft() + Math.min(780, width - 12));
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_F9) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
