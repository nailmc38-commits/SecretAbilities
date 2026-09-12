package com.secret.minigames.screens;

import com.secret.minigames.GameHubClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

public final class GameHubScreen extends Screen {
    private enum HubTab { GAMES, PVP }

    private HubTab tab = HubTab.GAMES;
    private int page;

    public GameHubScreen() {
        super(Text.literal("Mini Game Hub"));
    }

    @Override
    protected void init() {
        buildPage();
    }

    private void buildPage() {
        this.clearChildren();

        int tabW = 112;
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("GAMES").formatted(tab == HubTab.GAMES ? Formatting.AQUA : Formatting.GRAY),
                b -> { tab = HubTab.GAMES; buildPage(); }
        ).dimensions(this.width / 2 - tabW - 4, 42, tabW, 20).build());
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("PvP PRACTICE").formatted(tab == HubTab.PVP ? Formatting.LIGHT_PURPLE : Formatting.GRAY),
                b -> { tab = HubTab.PVP; buildPage(); }
        ).dimensions(this.width / 2 + 4, 42, tabW, 20).build());

        if (tab == HubTab.PVP) {
            buildPvpTab();
            return;
        }

        int cardW = Math.min(255, Math.max(135, (this.width - 44) / 2));
        int gap = 8;
        int totalW = cardW * 2 + gap;
        int left = (this.width - totalW) / 2;
        int top = 69;
        int h = 22;
        int rowGap = 4;

        if (page == 0) {
            addGame(left, top, cardW, h, "MINI MINECRAFT: SURVIVAL+", Formatting.GREEN,
                    () -> new MiniMinecraftScreen(this, GameHubClient.MINI_MINECRAFT));
            addGame(left + cardW + gap, top, cardW, h, "COZY TRAILS", Formatting.LIGHT_PURPLE,
                    () -> new CozyTrailsScreen(this));

            addGame(left, top + (h + rowGap), cardW, h, "BLOCK DROP DELUXE", Formatting.AQUA,
                    () -> new TetrisScreen(this));
            addGame(left + cardW + gap, top + (h + rowGap), cardW, h, "NEON MAZE 3D", Formatting.LIGHT_PURPLE,
                    () -> new Maze3DScreen(this));

            addGame(left, top + (h + rowGap) * 2, cardW, h, "WASTELAND TERMINAL", Formatting.GOLD,
                    () -> new TextWastelandScreen(this));
            addArcade(left + cardW + gap, top + (h + rowGap) * 2, cardW, h, "SNAKE", Formatting.GREEN, ArcadeGameScreen.Game.SNAKE);

            addArcade(left, top + (h + rowGap) * 3, cardW, h, "PONG", Formatting.WHITE, ArcadeGameScreen.Game.PONG);
            addArcade(left + cardW + gap, top + (h + rowGap) * 3, cardW, h, "BREAKOUT", Formatting.YELLOW, ArcadeGameScreen.Game.BREAKOUT);

            addArcade(left, top + (h + rowGap) * 4, cardW, h, "ASTEROIDS", Formatting.GRAY, ArcadeGameScreen.Game.ASTEROIDS);
            addArcade(left + cardW + gap, top + (h + rowGap) * 4, cardW, h, "SPACE INVADERS", Formatting.GREEN, ArcadeGameScreen.Game.INVADERS);
        } else {
            addArcade(left, top, cardW, h, "FLAPPY BLOCK", Formatting.YELLOW, ArcadeGameScreen.Game.FLAPPY);
            addArcade(left + cardW + gap, top, cardW, h, "2048", Formatting.GOLD, ArcadeGameScreen.Game.GAME_2048);

            addArcade(left, top + (h + rowGap), cardW, h, "MINESWEEPER", Formatting.RED, ArcadeGameScreen.Game.MINESWEEPER);
            addArcade(left + cardW + gap, top + (h + rowGap), cardW, h, "MEMORY MATCH", Formatting.LIGHT_PURPLE, ArcadeGameScreen.Game.MEMORY);

            addArcade(left, top + (h + rowGap) * 2, cardW, h, "SOKOBAN", Formatting.YELLOW, ArcadeGameScreen.Game.SOKOBAN);
            addArcade(left + cardW + gap, top + (h + rowGap) * 2, cardW, h, "FROGGER", Formatting.GREEN, ArcadeGameScreen.Game.FROGGER);

            addArcade(left, top + (h + rowGap) * 3, cardW, h, "PLATFORMER", Formatting.AQUA, ArcadeGameScreen.Game.PLATFORMER);
            addArcade(left + cardW + gap, top + (h + rowGap) * 3, cardW, h, "NEON RACING", Formatting.AQUA, ArcadeGameScreen.Game.RACING);

            addArcade(left, top + (h + rowGap) * 4, cardW, h, "TOWER DEFENSE", Formatting.GOLD, ArcadeGameScreen.Game.TOWER_DEFENSE);
            addArcade(left + cardW + gap, top + (h + rowGap) * 4, cardW, h, "DUNGEON CRAWLER", Formatting.RED, ArcadeGameScreen.Game.DUNGEON);
        }

        int navY = top + (h + rowGap) * 5 + 5;
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(page == 0 ? "MORE GAMES →" : "← FEATURED").formatted(Formatting.AQUA),
                b -> { page = 1 - page; buildPage(); }
        ).dimensions(this.width / 2 - 121, navY, 116, 21).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("CLOSE"), b -> this.close())
                .dimensions(this.width / 2 + 5, navY, 116, 21).build());
    }

    private void buildPvpTab() {
        int w = Math.min(430, this.width - 30);
        int left = this.width / 2 - w / 2;
        int top = 77;

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("ANCHOR PvP PRACTICE").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD),
                b -> {
                    if (this.client != null) this.client.setScreen(new AnchorPracticeScreen(this));
                }
        ).dimensions(left, top, w, 28).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("CLOSE"), b -> this.close())
                .dimensions(this.width / 2 - 58, top + 111, 116, 21).build());
    }

    private void addArcade(int x, int y, int w, int h, String name, Formatting color, ArcadeGameScreen.Game game) {
        addGame(x, y, w, h, name, color, () -> new ArcadeGameScreen(this, game));
    }

    private void addGame(int x, int y, int w, int h, String name, Formatting color, ScreenFactory factory) {
        this.addDrawableChild(ButtonWidget.builder(Text.literal(name).formatted(color), b -> {
            if (this.client != null) this.client.setScreen(factory.create());
        }).dimensions(x, y, w, h).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xFF080C12);
        context.fill(0, 0, this.width, 4, 0xFF55E0DC);
        context.fill(0, 4, this.width, 36, 0xFF131923);

        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("MINI GAME HUB").formatted(Formatting.AQUA, Formatting.BOLD),
                this.width / 2, 9, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("20 games + dedicated PvP practice • Fabric 1.21.11 • F9 closes hub").formatted(Formatting.GRAY),
                this.width / 2, 22, 0xFFFFFF);

        if (tab == HubTab.PVP) {
            int boxW = Math.min(430, this.width - 30);
            int left = this.width / 2 - boxW / 2;
            int top = 112;
            context.fill(left, top, left + boxW, top + 66, 0xAA15121C);
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("ANCHOR PRACTICE ONLY").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD),
                    this.width / 2, top + 8, 0xFFFFFF);
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("Z = select anchor • X = select glowstone • RMB = place / charge / explode"),
                    this.width / 2, top + 23, 0xFFFFFF);
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("Walk around freely • explosions destroy terrain • you take ZERO self-damage").formatted(Formatting.GRAY),
                    this.width / 2, top + 37, 0xFFFFFF);
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("R resets the practice arena").formatted(Formatting.DARK_GRAY),
                    this.width / 2, top + 51, 0xFFFFFF);
        } else {
            int footer = Math.min(this.height - 13, 229);
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal(page == 0
                            ? "Featured: Survival+, Cozy Trails, Block Drop Deluxe, 3D Maze, Wasteland + arcade"
                            : "Puzzle • action • racing • strategy • dungeon games").formatted(Formatting.DARK_GRAY),
                    this.width / 2, footer, 0xFFFFFF);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_F9 || input.key() == GLFW.GLFW_KEY_ESCAPE) {
            this.close();
            return true;
        }
        if (tab == HubTab.GAMES && input.key() == GLFW.GLFW_KEY_RIGHT) { page = 1; buildPage(); return true; }
        if (tab == HubTab.GAMES && input.key() == GLFW.GLFW_KEY_LEFT) { page = 0; buildPage(); return true; }
        return super.keyPressed(input);
    }

    @Override
    public boolean shouldPause() { return false; }

    private interface ScreenFactory { Screen create(); }
}
