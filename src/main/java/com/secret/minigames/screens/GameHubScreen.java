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
        int cardW = Math.min(300, Math.max(150, (this.width - 58) / 2));
        int gap = 10;
        int totalW = cardW * 2 + gap;
        int left = (this.width - totalW) / 2;
        int top = 62;
        int h = 32;
        int rowGap = 7;

        if (page == 0) {
            addGame(left, top, cardW, h, "MINI MINECRAFT: SURVIVAL", Formatting.GREEN,
                    () -> new MiniMinecraftScreen(this, GameHubClient.MINI_MINECRAFT));
            addGame(left + cardW + gap, top, cardW, h, "BLOCK DROP / TETRIS", Formatting.AQUA,
                    () -> new TetrisScreen(this));
            addGame(left, top + (h + rowGap), cardW, h, "NEON MAZE 3D", Formatting.LIGHT_PURPLE,
                    () -> new Maze3DScreen(this));
            addGame(left + cardW + gap, top + (h + rowGap), cardW, h, "WASTELAND TERMINAL", Formatting.GOLD,
                    () -> new TextWastelandScreen(this));
            addArcade(left, top + (h + rowGap) * 2, cardW, h, "SNAKE", Formatting.GREEN, ArcadeGameScreen.Game.SNAKE);
            addArcade(left + cardW + gap, top + (h + rowGap) * 2, cardW, h, "PONG", Formatting.WHITE, ArcadeGameScreen.Game.PONG);
            addArcade(left, top + (h + rowGap) * 3, cardW, h, "BREAKOUT", Formatting.YELLOW, ArcadeGameScreen.Game.BREAKOUT);
            addArcade(left + cardW + gap, top + (h + rowGap) * 3, cardW, h, "ASTEROIDS", Formatting.GRAY, ArcadeGameScreen.Game.ASTEROIDS);
            addArcade(left, top + (h + rowGap) * 4, cardW, h, "SPACE INVADERS", Formatting.GREEN, ArcadeGameScreen.Game.INVADERS);
            addArcade(left + cardW + gap, top + (h + rowGap) * 4, cardW, h, "FLAPPY BLOCK", Formatting.YELLOW, ArcadeGameScreen.Game.FLAPPY);
        } else {
            addArcade(left, top, cardW, h, "2048", Formatting.GOLD, ArcadeGameScreen.Game.GAME_2048);
            addArcade(left + cardW + gap, top, cardW, h, "MINESWEEPER", Formatting.RED, ArcadeGameScreen.Game.MINESWEEPER);
            addArcade(left, top + (h + rowGap), cardW, h, "MEMORY MATCH", Formatting.LIGHT_PURPLE, ArcadeGameScreen.Game.MEMORY);
            addArcade(left + cardW + gap, top + (h + rowGap), cardW, h, "SOKOBAN", Formatting.YELLOW, ArcadeGameScreen.Game.SOKOBAN);
            addArcade(left, top + (h + rowGap) * 2, cardW, h, "FROGGER", Formatting.GREEN, ArcadeGameScreen.Game.FROGGER);
            addArcade(left + cardW + gap, top + (h + rowGap) * 2, cardW, h, "PLATFORMER", Formatting.AQUA, ArcadeGameScreen.Game.PLATFORMER);
            addArcade(left, top + (h + rowGap) * 3, cardW, h, "NEON RACING", Formatting.AQUA, ArcadeGameScreen.Game.RACING);
            addArcade(left + cardW + gap, top + (h + rowGap) * 3, cardW, h, "TOWER DEFENSE", Formatting.GOLD, ArcadeGameScreen.Game.TOWER_DEFENSE);
            addArcade(left, top + (h + rowGap) * 4, cardW, h, "DUNGEON CRAWLER", Formatting.RED, ArcadeGameScreen.Game.DUNGEON);
        }

        int navY = top + (h + rowGap) * 5 + 8;
        this.addDrawableChild(ButtonWidget.builder(Text.literal(page == 0 ? "PAGE 2 →" : "← PAGE 1").formatted(Formatting.AQUA), b -> {
            page = 1 - page;
            buildPage();
        }).dimensions(this.width / 2 - 145, navY, 140, 24).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("CLOSE"), b -> this.close())
                .dimensions(this.width / 2 + 5, navY, 140, 24).build());
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
        context.fill(0, 0, this.width, this.height, 0xFF0A0E15);
        context.fill(0, 0, this.width, 5, 0xFF5CE1E6);
        context.fill(0, 5, this.width, 50, 0xFF151B25);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("MINI GAME HUB").formatted(Formatting.AQUA, Formatting.BOLD),
                this.width / 2, 14, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("19 playable games • Page " + (page + 1) + "/2 • Fabric 1.21.11 • F9 closes hub").formatted(Formatting.GRAY),
                this.width / 2, 31, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal(page == 0 ? "FEATURED + ARCADE" : "PUZZLE + ACTION + STRATEGY").formatted(Formatting.DARK_GRAY),
                this.width / 2, 49, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_F9 || input.key() == GLFW.GLFW_KEY_ESCAPE) {
            this.close();
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_RIGHT) { page = 1; buildPage(); return true; }
        if (input.key() == GLFW.GLFW_KEY_LEFT) { page = 0; buildPage(); return true; }
        return super.keyPressed(input);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private interface ScreenFactory {
        Screen create();
    }
}
