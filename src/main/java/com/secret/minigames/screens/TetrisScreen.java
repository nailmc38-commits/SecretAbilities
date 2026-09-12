package com.secret.minigames.screens;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

public final class TetrisScreen extends Screen {
    private static final int W = 10, H = 20;
    private static final int[][][] SHAPES = {
            {{0,1},{1,1},{2,1},{3,1}},
            {{0,0},{0,1},{1,1},{2,1}},
            {{2,0},{0,1},{1,1},{2,1}},
            {{1,0},{2,0},{1,1},{2,1}},
            {{1,0},{2,0},{0,1},{1,1}},
            {{1,0},{0,1},{1,1},{2,1}},
            {{0,0},{1,0},{1,1},{2,1}}
    };
    private static final int[] COLORS = {
            0xFF4DE5F1, 0xFF4F72FF, 0xFFFFA54D, 0xFFFFE35B, 0xFF63DB72, 0xFFBD62E9, 0xFFEC5C5C
    };

    private final Screen parent;
    private final int[][] board = new int[H][W];
    private final Random random = new Random();
    private int type, nextType, holdType = -1, rot, px, py;
    private int score, lines, level = 1;
    private double fallTimer;
    private boolean canHold = true;
    private boolean gameOver;
    private boolean paused;

    public TetrisScreen(Screen parent) {
        super(Text.literal("Block Drop"));
        this.parent = parent;
        reset();
    }

    private void reset() {
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) board[y][x] = 0;
        score = 0;
        lines = 0;
        level = 1;
        fallTimer = 0;
        holdType = -1;
        nextType = random.nextInt(7);
        gameOver = false;
        paused = false;
        spawn();
    }

    private void spawn() {
        type = nextType;
        nextType = random.nextInt(7);
        rot = 0;
        px = 3;
        py = -1;
        canHold = true;
        if (!fits(type, rot, px, py)) gameOver = true;
    }

    @Override
    protected void init() {
        fallTimer = 0;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (!paused && !gameOver) {
            fallTimer += delta / 20.0;
            double interval = Math.max(0.07, 0.62 - (level - 1) * 0.045);
            if (fallTimer >= interval) {
                fallTimer = 0;
                stepDown();
            }
        }

        ctx.fill(0, 0, width, height, 0xFF090D14);
        ctx.fill(0, 0, width, 4, 0xFF4DE5F1);
        drawBoard(ctx);
        drawSidePanels(ctx);
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawBoard(DrawContext ctx) {
        int cell = Math.max(9, Math.min((height - 62) / H, (width - 180) / W));
        int bw = cell * W, bh = cell * H;
        int ox = width / 2 - bw / 2;
        int oy = 45 + Math.max(0, (height - 50 - bh) / 2);

        ctx.fill(ox - 5, oy - 5, ox + bw + 5, oy + bh + 5, 0xFF27313B);
        ctx.fill(ox, oy, ox + bw, oy + bh, 0xFF101721);

        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int c = board[y][x];
                if (c != 0) drawCell(ctx, ox, oy, cell, x, y, COLORS[c - 1], false);
                else {
                    ctx.fill(ox + x * cell, oy + y * cell, ox + (x + 1) * cell, oy + (y + 1) * cell, 0x11000000);
                }
            }
        }

        if (!gameOver) {
            int ghostY = py;
            while (fits(type, rot, px, ghostY + 1)) ghostY++;
            for (int[] p : cells(type, rot)) {
                int x = px + p[0], y = ghostY + p[1];
                if (y >= 0) drawCell(ctx, ox, oy, cell, x, y, 0x665A6773, true);
            }
            for (int[] p : cells(type, rot)) {
                int x = px + p[0], y = py + p[1];
                if (y >= 0) drawCell(ctx, ox, oy, cell, x, y, COLORS[type], false);
            }
        }

        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("BLOCK DROP").formatted(Formatting.AQUA, Formatting.BOLD), width / 2, 11, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Level " + level + "  •  Lines " + lines + "  •  Score " + score).formatted(Formatting.GRAY),
                width / 2, 25, 0xFFFFFF);

        if (gameOver || paused) {
            int w = 190;
            ctx.fill(width / 2 - w / 2, height / 2 - 28, width / 2 + w / 2, height / 2 + 28, 0xE8000000);
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(gameOver ? "GAME OVER" : "PAUSED").formatted(gameOver ? Formatting.RED : Formatting.YELLOW, Formatting.BOLD),
                    width / 2, height / 2 - 10, 0xFFFFFF);
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(gameOver ? "R to restart" : "P to continue"), width / 2, height / 2 + 8, 0xFFFFFF);
        }
    }

    private void drawSidePanels(DrawContext ctx) {
        int cell = Math.max(9, Math.min((height - 62) / H, (width - 180) / W));
        int bw = cell * W;
        int ox = width / 2 - bw / 2;
        int oy = 54;
        int panelW = Math.min(108, Math.max(78, ox - 18));

        int lx = Math.max(8, ox - panelW - 10);
        ctx.fill(lx, oy, lx + panelW, oy + 86, 0xBB151D27);
        ctx.drawTextWithShadow(textRenderer, Text.literal("HOLD").formatted(Formatting.GRAY), lx + 7, oy + 7, 0xFFFFFF);
        if (holdType >= 0) drawPreview(ctx, holdType, lx + panelW / 2, oy + 43, 11);

        int rx = Math.min(width - panelW - 8, ox + bw + 10);
        ctx.fill(rx, oy, rx + panelW, oy + 86, 0xBB151D27);
        ctx.drawTextWithShadow(textRenderer, Text.literal("NEXT").formatted(Formatting.GRAY), rx + 7, oy + 7, 0xFFFFFF);
        drawPreview(ctx, nextType, rx + panelW / 2, oy + 43, 11);

        int helpY = oy + 100;
        ctx.fill(lx, helpY, rx + panelW, helpY + 48, 0x99151D27);
        ctx.drawTextWithShadow(textRenderer, Text.literal("← → / A D move   ↑ / W rotate   ↓ soft drop"), lx + 6, helpY + 7, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Space hard drop   C hold   P pause   R restart"), lx + 6, helpY + 20, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer, Text.literal("F9 / ESC returns to the Game Hub").formatted(Formatting.GRAY), lx + 6, helpY + 33, 0xFFFFFF);
    }

    private void drawPreview(DrawContext ctx, int piece, int cx, int cy, int size) {
        for (int[] p : cells(piece, 0)) {
            int x = cx + (p[0] - 1) * size;
            int y = cy + (p[1] - 1) * size;
            ctx.fill(x, y, x + size - 1, y + size - 1, COLORS[piece]);
            ctx.fill(x + 2, y + 2, x + size - 2, y + 4, 0x33FFFFFF);
        }
    }

    private void drawCell(DrawContext ctx, int ox, int oy, int cell, int x, int y, int color, boolean ghost) {
        if (x < 0 || x >= W || y < 0 || y >= H) return;
        int l = ox + x * cell, t = oy + y * cell;
        ctx.fill(l + 1, t + 1, l + cell - 1, t + cell - 1, color);
        if (!ghost) {
            ctx.fill(l + 2, t + 2, l + cell - 2, t + 4, 0x33FFFFFF);
            ctx.fill(l + 2, t + cell - 4, l + cell - 2, t + cell - 2, 0x33000000);
        }
    }

    private int[][] cells(int piece, int rotation) {
        int[][] base = SHAPES[piece];
        int[][] out = new int[4][2];
        for (int i = 0; i < 4; i++) {
            int x = base[i][0], y = base[i][1];
            for (int r = 0; r < rotation; r++) {
                int tx = x;
                x = 3 - y;
                y = tx;
            }
            out[i][0] = x;
            out[i][1] = y;
        }
        return out;
    }

    private boolean fits(int piece, int rotation, int x0, int y0) {
        for (int[] p : cells(piece, rotation)) {
            int x = x0 + p[0], y = y0 + p[1];
            if (x < 0 || x >= W || y >= H) return false;
            if (y >= 0 && board[y][x] != 0) return false;
        }
        return true;
    }

    private void move(int dx) {
        if (fits(type, rot, px + dx, py)) px += dx;
    }

    private void rotate() {
        int nr = (rot + 1) & 3;
        if (fits(type, nr, px, py)) rot = nr;
        else if (fits(type, nr, px - 1, py)) { px--; rot = nr; }
        else if (fits(type, nr, px + 1, py)) { px++; rot = nr; }
    }

    private void stepDown() {
        if (fits(type, rot, px, py + 1)) py++;
        else lockPiece();
    }

    private void hardDrop() {
        int dropped = 0;
        while (fits(type, rot, px, py + 1)) { py++; dropped++; }
        score += dropped * 2;
        lockPiece();
    }

    private void softDrop() {
        if (fits(type, rot, px, py + 1)) { py++; score++; }
        else lockPiece();
    }

    private void hold() {
        if (!canHold) return;
        if (holdType < 0) {
            holdType = type;
            spawn();
        } else {
            int temp = type;
            type = holdType;
            holdType = temp;
            rot = 0;
            px = 3;
            py = -1;
            if (!fits(type, rot, px, py)) gameOver = true;
        }
        canHold = false;
    }

    private void lockPiece() {
        for (int[] p : cells(type, rot)) {
            int x = px + p[0], y = py + p[1];
            if (y < 0) { gameOver = true; return; }
            board[y][x] = type + 1;
        }
        clearLines();
        spawn();
    }

    private void clearLines() {
        int cleared = 0;
        for (int y = H - 1; y >= 0; y--) {
            boolean full = true;
            for (int x = 0; x < W; x++) if (board[y][x] == 0) { full = false; break; }
            if (!full) continue;
            cleared++;
            for (int yy = y; yy > 0; yy--) System.arraycopy(board[yy - 1], 0, board[yy], 0, W);
            for (int x = 0; x < W; x++) board[0][x] = 0;
            y++;
        }
        if (cleared > 0) {
            int[] base = {0, 100, 300, 500, 800};
            score += base[cleared] * level;
            lines += cleared;
            level = 1 + lines / 10;
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int k = input.key();
        if (k == GLFW.GLFW_KEY_ESCAPE || k == GLFW.GLFW_KEY_F9) {
            if (client != null) client.setScreen(parent);
            return true;
        }
        if (k == GLFW.GLFW_KEY_R) { reset(); return true; }
        if (k == GLFW.GLFW_KEY_P) { paused = !paused; return true; }
        if (gameOver || paused) return true;
        if (k == GLFW.GLFW_KEY_LEFT || k == GLFW.GLFW_KEY_A) move(-1);
        if (k == GLFW.GLFW_KEY_RIGHT || k == GLFW.GLFW_KEY_D) move(1);
        if (k == GLFW.GLFW_KEY_UP || k == GLFW.GLFW_KEY_W) rotate();
        if (k == GLFW.GLFW_KEY_DOWN || k == GLFW.GLFW_KEY_S) softDrop();
        if (k == GLFW.GLFW_KEY_SPACE) hardDrop();
        if (k == GLFW.GLFW_KEY_C) hold();
        return super.keyPressed(input);
    }

    @Override
    public boolean shouldPause() { return false; }
}
