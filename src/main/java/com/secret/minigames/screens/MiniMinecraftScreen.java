package com.secret.minigames.screens;

import com.secret.minigames.games.MiniMinecraftGame;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

public final class MiniMinecraftScreen extends Screen {
    private static final double RADIUS = 0.28;
    private static final double HEIGHT = 1.75;
    private static final double EYE = 1.62;

    private final Screen parent;
    private final MiniMinecraftGame game;
    private long lastNanos;
    private boolean prevJump;
    private boolean prevRight;
    private int mouseWarmup = 3;
    private int miningX = Integer.MIN_VALUE, miningY, miningZ;
    private double miningProgress;
    private String toast = "Survive. Gather. Build.";
    private double toastTime = 3.0;

    public MiniMinecraftScreen(Screen parent, MiniMinecraftGame game) {
        super(Text.literal("Mini Minecraft Survival"));
        this.parent = parent;
        this.game = game;
    }

    @Override
    protected void init() {
        lastNanos = System.nanoTime();
        mouseWarmup = 3;
        centerMouse();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        double dt = Math.min(0.05, Math.max(0.001, (now - lastNanos) / 1_000_000_000.0));
        lastNanos = now;
        toastTime = Math.max(0, toastTime - dt);

        updateLook();
        boolean[] motion = updateMovement(dt);
        game.survivalTick(dt, motion[0], motion[1]);
        updateActions(dt);
        drawWorld(context);
        drawHud(context);
        super.render(context, mouseX, mouseY, delta);
    }

    private void updateLook() {
        if (this.client == null) return;
        double cx = this.client.getWindow().getWidth() / 2.0;
        double cy = this.client.getWindow().getHeight() / 2.0;
        double mx = this.client.mouse.getX();
        double my = this.client.mouse.getY();
        if (mouseWarmup-- <= 0) {
            game.yaw += (mx - cx) * 0.0028;
            game.pitch -= (my - cy) * 0.0028;
            game.pitch = clamp(game.pitch, -1.35, 1.35);
        }
        centerMouse();
    }

    private void centerMouse() {
        if (this.client == null) return;
        GLFW.glfwSetCursorPos(
                this.client.getWindow().getHandle(),
                this.client.getWindow().getWidth() / 2.0,
                this.client.getWindow().getHeight() / 2.0
        );
    }

    private boolean[] updateMovement(double dt) {
        if (this.client == null) return new boolean[]{false, false};
        long win = this.client.getWindow().getHandle();
        double f = (down(win, GLFW.GLFW_KEY_W) ? 1 : 0) - (down(win, GLFW.GLFW_KEY_S) ? 1 : 0);
        double s = (down(win, GLFW.GLFW_KEY_D) ? 1 : 0) - (down(win, GLFW.GLFW_KEY_A) ? 1 : 0);
        double len = Math.sqrt(f * f + s * s);
        boolean moving = len > 0;
        boolean sprinting = moving && down(win, GLFW.GLFW_KEY_LEFT_CONTROL) && game.hunger > 2;
        if (moving) {
            f /= len;
            s /= len;
            double speed = sprinting ? 5.0 : 3.5;
            double sin = Math.sin(game.yaw), cos = Math.cos(game.yaw);
            move((sin * f + cos * s) * speed * dt, (cos * f - sin * s) * speed * dt);
        }

        boolean jump = down(win, GLFW.GLFW_KEY_SPACE);
        boolean grounded = collides(game.playerX, game.playerY - 0.07, game.playerZ);
        if (jump && !prevJump && grounded && game.hunger > 0) game.velocityY = 5.0;
        prevJump = jump;

        game.velocityY -= 13.0 * dt;
        double nextY = game.playerY + game.velocityY * dt;
        if (!collides(game.playerX, nextY, game.playerZ)) {
            game.playerY = nextY;
        } else {
            double impact = game.velocityY;
            if (impact < -8.0) {
                int damage = Math.max(1, (int) Math.floor((-impact - 7.0) * 1.2));
                game.damage(damage);
                say("Fall damage: -" + damage + " HP");
            }
            if (game.velocityY < 0) {
                double y = game.playerY;
                for (int i = 0; i < 20 && collides(game.playerX, y - 0.02, game.playerZ); i++) y += 0.02;
                game.playerY = y;
            }
            game.velocityY = 0;
        }
        if (game.playerY < -3) {
            game.damage(8);
            game.respawn();
        }
        return new boolean[]{moving, sprinting};
    }

    private void move(double dx, double dz) {
        if (!collides(game.playerX + dx, game.playerY, game.playerZ)) game.playerX += dx;
        if (!collides(game.playerX, game.playerY, game.playerZ + dz)) game.playerZ += dz;
    }

    private boolean collides(double px, double py, double pz) {
        int minX = floor(px - RADIUS), maxX = floor(px + RADIUS);
        int minY = floor(py), maxY = floor(py + HEIGHT - 0.01);
        int minZ = floor(pz - RADIUS), maxZ = floor(pz + RADIUS);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (game.collisionSolid(x, y, z)) return true;
                }
            }
        }
        return false;
    }

    private void updateActions(double dt) {
        if (this.client == null) return;
        long win = this.client.getWindow().getHandle();
        boolean left = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean right = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        double[] d = direction();
        Hit hit = ray(game.playerX, game.playerY + EYE, game.playerZ, d[0], d[1], d[2], 6.0);

        if (left && hit != null && hit.y > 0) {
            if (miningX != hit.x || miningY != hit.y || miningZ != hit.z) {
                miningX = hit.x;
                miningY = hit.y;
                miningZ = hit.z;
                miningProgress = 0;
            }
            byte old = game.block(hit.x, hit.y, hit.z);
            miningProgress += dt;
            if (miningProgress >= game.miningSeconds(old)) {
                game.setBlock(hit.x, hit.y, hit.z, MiniMinecraftGame.AIR);
                game.addDrop(old);
                say("Collected " + MiniMinecraftGame.BLOCK_NAMES[old]);
                miningX = Integer.MIN_VALUE;
                miningProgress = 0;
            }
        } else {
            miningX = Integer.MIN_VALUE;
            miningProgress = 0;
        }

        if (right && !prevRight && hit != null) {
            int x = hit.px, y = hit.py, z = hit.pz;
            if (game.inside(x, y, z)
                    && game.block(x, y, z) == MiniMinecraftGame.AIR
                    && !intersectsPlayer(x, y, z)
                    && game.inventory(game.selectedBlock()) > 0) {
                byte b = game.selectedBlock();
                game.setBlock(x, y, z, b);
                if (!game.consumeSelected()) game.setBlock(x, y, z, MiniMinecraftGame.AIR);
            }
        }
        prevRight = right;
    }

    private boolean intersectsPlayer(int x, int y, int z) {
        return x + 1 > game.playerX - RADIUS && x < game.playerX + RADIUS
                && y + 1 > game.playerY && y < game.playerY + HEIGHT
                && z + 1 > game.playerZ - RADIUS && z < game.playerZ + RADIUS;
    }

    private void drawWorld(DrawContext ctx) {
        int pixel = Math.max(6, Math.min(10, this.width / 150));
        double aspect = (double) this.width / Math.max(1, this.height);
        double fov = Math.tan(Math.toRadians(70) / 2.0);
        double light = game.daylight();
        int skyTop = mix(0xFF11162A, 0xFF6FA8FF, light);
        int skyBottom = mix(0xFF24283B, 0xFFD6E8FF, light);

        for (int y = 0; y < this.height; y += pixel) {
            double t = (double) y / this.height;
            ctx.fill(0, y, this.width, Math.min(this.height, y + pixel), mix(skyTop, skyBottom, t));
        }

        double sp = Math.sin(game.pitch), cp = Math.cos(game.pitch);
        double sy = Math.sin(game.yaw), cy = Math.cos(game.yaw);
        double fx = sy * cp, fy = sp, fz = cy * cp;
        double rx = cy, rz = -sy;
        double ux = -sy * sp, uy = cp, uz = -cy * sp;

        for (int py = 0; py < this.height; py += pixel) {
            double ny = 1.0 - ((py + pixel * 0.5) / this.height) * 2.0;
            for (int px = 0; px < this.width; px += pixel) {
                double nx = ((px + pixel * 0.5) / this.width) * 2.0 - 1.0;
                double cx = nx * fov * aspect, cyy = ny * fov;
                double dx = fx + rx * cx + ux * cyy;
                double dy = fy + uy * cyy;
                double dz = fz + rz * cx + uz * cyy;
                double inv = 1.0 / Math.sqrt(dx * dx + dy * dy + dz * dz);
                Hit h = ray(game.playerX, game.playerY + EYE, game.playerZ, dx * inv, dy * inv, dz * inv, 19.0);
                if (h == null) continue;
                byte block = game.block(h.x, h.y, h.z);
                int base = color(block);
                double face = h.face == 1 ? 1.0 : (h.face == 0 ? 0.78 : 0.88);
                double fog = clamp(1.0 - h.dist / 28.0, 0.42, 1.0);
                double blockLight = block == MiniMinecraftGame.TORCH ? 1.0 : Math.max(0.38, light);
                int c = shade(base, face * fog * blockLight);
                ctx.fill(px, py, Math.min(this.width, px + pixel), Math.min(this.height, py + pixel), c);
            }
        }
    }

    private void drawHud(DrawContext ctx) {
        int cx = this.width / 2, cy = this.height / 2;
        ctx.fill(cx - 5, cy, cx - 1, cy + 1, 0xFFFFFFFF);
        ctx.fill(cx + 2, cy, cx + 6, cy + 1, 0xFFFFFFFF);
        ctx.fill(cx, cy - 5, cx + 1, cy - 1, 0xFFFFFFFF);
        ctx.fill(cx, cy + 2, cx + 1, cy + 6, 0xFFFFFFFF);

        if (miningX != Integer.MIN_VALUE) {
            byte b = game.block(miningX, miningY, miningZ);
            double p = clamp(miningProgress / Math.max(0.01, game.miningSeconds(b)), 0, 1);
            ctx.fill(cx - 31, cy + 12, cx + 31, cy + 17, 0xCC111111);
            ctx.fill(cx - 30, cy + 13, cx - 30 + (int) (60 * p), cy + 16, 0xFFFFFFFF);
        }

        ctx.fill(5, 5, 248, 52, 0x99000000);
        ctx.drawTextWithShadow(this.textRenderer, Text.literal("MINI MINECRAFT: SURVIVAL").formatted(Formatting.GREEN, Formatting.BOLD), 11, 10, 0xFFFFFF);
        ctx.drawTextWithShadow(this.textRenderer, Text.literal("WASD • Mouse • Space • Ctrl sprint").formatted(Formatting.WHITE), 11, 23, 0xFFFFFF);
        ctx.drawTextWithShadow(this.textRenderer, Text.literal("LMB mine • RMB place • 1-9 slots • F apple • C/V craft").formatted(Formatting.GRAY), 11, 36, 0xFFFFFF);

        int statY = this.height - 68;
        drawMeter(ctx, cx - 128, statY, game.health, 20, 0xFFE84949, "HP");
        drawMeter(ctx, cx + 12, statY, game.hunger, 20, 0xFFE6A23C, "FOOD");
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Apples: " + game.apples + "  •  Day " + (game.daysSurvived + 1)),
                cx, statY - 12, 0xFFFFFF);

        int slot = 28, total = MiniMinecraftGame.HOTBAR.length * slot, left = cx - total / 2, top = this.height - 39;
        for (int i = 0; i < MiniMinecraftGame.HOTBAR.length; i++) {
            byte b = MiniMinecraftGame.HOTBAR[i];
            int x = left + i * slot;
            ctx.fill(x, top, x + 26, top + 26, i == game.selectedHotbar ? 0xFFFFFFFF : 0xCC333333);
            ctx.fill(x + 2, top + 2, x + 24, top + 24, 0xEE111111);
            ctx.fill(x + 6, top + 6, x + 20, top + 20, color(b));
            ctx.drawTextWithShadow(this.textRenderer, Text.literal(Integer.toString(i + 1)), x + 3, top + 3, 0xFFFFFF);
            String count = Integer.toString(game.inventory(b));
            ctx.drawTextWithShadow(this.textRenderer, Text.literal(count), x + 23 - this.textRenderer.getWidth(count), top + 15, 0xFFFFFF);
        }
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(game.selectedName()), cx, top - 11, 0xFFFFFF);

        if (toastTime > 0) {
            int w = this.textRenderer.getWidth(toast) + 16;
            ctx.fill(cx - w / 2, 57, cx + w / 2, 77, 0xBB000000);
            ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(toast), cx, 63, 0xFFFFFF);
        }
    }

    private void drawMeter(DrawContext ctx, int x, int y, int value, int max, int color, String label) {
        ctx.fill(x, y, x + 116, y + 11, 0xCC111111);
        int fill = (int) (112 * clamp(value / (double) max, 0, 1));
        ctx.fill(x + 2, y + 2, x + 2 + fill, y + 9, color);
        ctx.drawTextWithShadow(this.textRenderer, Text.literal(label + " " + value + "/" + max), x + 3, y + 2, 0xFFFFFFFF);
    }

    private Hit ray(double ox, double oy, double oz, double dx, double dy, double dz, double max) {
        double step = 0.06;
        int px = floor(ox), py = floor(oy), pz = floor(oz);
        for (double d = 0; d <= max; d += step) {
            int x = floor(ox + dx * d), y = floor(oy + dy * d), z = floor(oz + dz * d);
            if (game.inside(x, y, z) && game.block(x, y, z) != MiniMinecraftGame.AIR) {
                int face = y != py ? 1 : (x != px ? 0 : 2);
                return new Hit(x, y, z, px, py, pz, d, face);
            }
            px = x;
            py = y;
            pz = z;
        }
        return null;
    }

    private double[] direction() {
        double cp = Math.cos(game.pitch);
        return new double[]{Math.sin(game.yaw) * cp, Math.sin(game.pitch), Math.cos(game.yaw) * cp};
    }

    private void say(String message) {
        toast = message;
        toastTime = 2.0;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int k = input.key();
        if (k == GLFW.GLFW_KEY_ESCAPE || k == GLFW.GLFW_KEY_F9) {
            if (this.client != null) this.client.setScreen(parent);
            return true;
        }
        if (k >= GLFW.GLFW_KEY_1 && k <= GLFW.GLFW_KEY_9) {
            game.selectedHotbar = k - GLFW.GLFW_KEY_1;
            return true;
        }
        if (k == GLFW.GLFW_KEY_F) {
            say(game.eatApple() ? "Ate an apple." : "No apple needed/available.");
            return true;
        }
        if (k == GLFW.GLFW_KEY_C) {
            say(game.craftPlanks() ? "Crafted 4 planks." : "Need 1 wood.");
            return true;
        }
        if (k == GLFW.GLFW_KEY_V) {
            say(game.craftTorches() ? "Crafted 4 torches." : "Need 1 wood + 1 cobble.");
            return true;
        }
        if (k == GLFW.GLFW_KEY_R) {
            game.newWorld();
            say("New survival world generated.");
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static boolean down(long win, int key) {
        return GLFW.glfwGetKey(win, key) == GLFW.GLFW_PRESS;
    }

    private static int floor(double v) {
        return (int) Math.floor(v);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static int color(byte b) {
        return switch (b) {
            case MiniMinecraftGame.GRASS -> 0xFF5EAD45;
            case MiniMinecraftGame.DIRT -> 0xFF8A5B32;
            case MiniMinecraftGame.STONE -> 0xFF777777;
            case MiniMinecraftGame.WOOD -> 0xFF79512E;
            case MiniMinecraftGame.LEAVES -> 0xFF3A8535;
            case MiniMinecraftGame.COBBLE -> 0xFF66686A;
            case MiniMinecraftGame.SAND -> 0xFFD8C27A;
            case MiniMinecraftGame.PLANKS -> 0xFFB88652;
            case MiniMinecraftGame.TORCH -> 0xFFFFC94A;
            default -> 0xFF000000;
        };
    }

    private static int shade(int c, double f) {
        int r = (int) (((c >> 16) & 255) * f);
        int g = (int) (((c >> 8) & 255) * f);
        int b = (int) ((c & 255) * f);
        return 0xFF000000 | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
    }

    private static int mix(int a, int b, double t) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    private record Hit(int x, int y, int z, int px, int py, int pz, double dist, int face) {}
}
