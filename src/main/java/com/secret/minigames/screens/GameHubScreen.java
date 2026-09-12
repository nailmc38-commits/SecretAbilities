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
    public GameHubScreen() {
        super(Text.literal("Mini Game Hub"));
    }

    @Override
    protected void init() {
        int cardW = Math.min(270, Math.max(170, (this.width - 70) / 2));
        int gap = 14;
        int totalW = cardW * 2 + gap;
        int left = (this.width - totalW) / 2;
        int top = Math.max(70, this.height / 2 - 105);

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("MINI MINECRAFT 3D").formatted(Formatting.GREEN),
                b -> this.client.setScreen(new MiniMinecraftScreen(this, GameHubClient.MINI_MINECRAFT))
        ).dimensions(left, top, cardW, 42).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("BLOCK DROP / TETRIS").formatted(Formatting.AQUA),
                b -> this.client.setScreen(new TetrisScreen(this))
        ).dimensions(left + cardW + gap, top, cardW, 42).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("NEON MAZE 3D").formatted(Formatting.LIGHT_PURPLE),
                b -> this.client.setScreen(new Maze3DScreen(this))
        ).dimensions(left, top + 56, cardW, 42).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("WASTELAND: THIRD PERSON").formatted(Formatting.GOLD),
                b -> this.client.setScreen(new WastelandScreen(this))
        ).dimensions(left + cardW + gap, top + 56, cardW, 42).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("CLOSE"),
                b -> this.close()
        ).dimensions(this.width / 2 - 70, top + 124, 140, 24).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xFF10131A);
        context.fill(0, 0, this.width, 4, 0xFF5CE1E6);
        context.fill(0, 4, this.width, 52, 0xFF171C25);

        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("MINI GAME HUB").formatted(Formatting.AQUA, Formatting.BOLD),
                this.width / 2,
                18,
                0xFFFFFF
        );
        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("Fabric 1.21.11 • F9 closes the hub").formatted(Formatting.GRAY),
                this.width / 2,
                34,
                0xFFFFFF
        );

        int y = Math.max(70, this.height / 2 - 105) + 164;
        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("4 built-in games • no hacked-client modules").formatted(Formatting.DARK_GRAY),
                this.width / 2,
                y,
                0xFFFFFF
        );

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_F9) {
            this.close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
