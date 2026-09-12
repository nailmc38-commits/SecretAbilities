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
    private boolean prevLeft;
    private boolean prevRight;
    private int mouseWarmup = 3;
    private int miningX = Integer.MIN_VALUE, miningY, miningZ;
    private double miningProgress;
    private String toast = "Survive • explore • craft • build";
    private double toastTime = 3.0;
    private int targetedAnimal = -1;

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
        game.survivalTick(dt, motion[0], motion[1], motion[2]);
        targetedAnimal = findTargetAnimal();
        updateActions(dt);
        drawWorld(context);
        drawAnimals(context);
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
        if (this.client == null) return new boolean[]{false, false, false};
        long win = this.client.getWindow().getHandle();
        boolean swimming = game.isWaterAt(game.playerX, game.playerY + 0.5, game.playerZ);
        double f = (down(win, GLFW.GLFW_KEY_W) ? 1 : 0) - (down(win, GLFW.GLFW_KEY_S) ? 1 : 0);
        double s = (down(win, GLFW.GLFW_KEY_D) ? 1 : 0) - (down(win, GLFW.GLFW_KEY_A) ? 1 : 0);
        double len = Math.sqrt(f * f + s * s);
        boolean moving = len > 0;
        boolean sprinting = moving && !swimming && down(win, GLFW.GLFW_KEY_LEFT_CONTROL) && game.hunger > 2;
        if (moving) {
            f /= len;
            s /= len;
            double speed = swimming ? 2.35 : (sprinting ? 5.25 : 3.65);
            double sin = Math.sin(game.yaw), cos = Math.cos(game.yaw);
            move((sin * f + cos * s) * speed * dt, (cos * f - sin * s) * speed * dt);
        }

        boolean jump = down(win, GLFW.GLFW_KEY_SPACE);
        boolean grounded = collides(game.playerX, game.playerY - 0.07, game.playerZ);
        if (swimming) {
            if (jump) game.velocityY = 2.6;
            game.velocityY -= 3.0 * dt;
        } else {
            // Strong enough to clear a full 1-block obstacle with room to spare.
            if (jump && !prevJump && grounded && game.hunger > 0) game.velocityY = 6.6;
            game.velocityY -= 13.0 * dt;
        }
        prevJump = jump;

        double nextY = game.playerY + game.velocityY * dt;
        if (!collides(game.playerX, nextY, game.playerZ)) {
            game.playerY = nextY;
        } else {
            double impact = game.velocityY;
            if (!swimming && impact < -8.0) {
                int damage = Math.max(1, (int) Math.floor((-impact - 7.0) * 1.15));
                game.damage(damage);
                say("Fall damage: -" + damage + " HP");
            }
            if (game.velocityY < 0) {
                double y = game.playerY;
                for (int i = 0; i < 28 && collides(game.playerX, y - 0.015, game.playerZ); i++) y += 0.015;
                game.playerY = y;
            }
            game.velocityY = 0;
        }

        if (game.playerY < -3) {
            game.damage(8);
            game.respawn();
        }
        return new boolean[]{moving, sprinting, swimming};
    }

    private void move(double dx, double dz) {
        boolean grounded = collides(game.playerX, game.playerY - 0.06, game.playerZ);

        if (!collides(game.playerX + dx, game.playerY, game.playerZ)) {
            game.playerX += dx;
        } else if (grounded && !collides(game.playerX + dx, game.playerY + 0.55, game.playerZ)) {
            game.playerY += 0.55;
            game.playerX += dx;
        }

        if (!collides(game.playerX, game.playerY, game.playerZ + dz)) {
            game.playerZ += dz;
        } else if (grounded && !collides(game.playerX, game.playerY + 0.55, game.playerZ + dz)) {
            game.playerY += 0.55;
            game.playerZ += dz;
        }
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

        if (left && !prevLeft && targetedAnimal >= 0) {
            say(game.attackAnimal(targetedAnimal));
            miningX = Integer.MIN_VALUE;
            miningProgress = 0;
        } else if (left && hit != null && hit.y > 0 && game.block(hit.x, hit.y, hit.z) != MiniMinecraftGame.WATER) {
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
                    && (game.block(x, y, z) == MiniMinecraftGame.AIR || game.block(x, y, z) == MiniMinecraftGame.WATER)
                    && !intersectsPlayer(x, y, z)
                    && game.inventory(game.selectedBlock()) > 0) {
                byte before = game.block(x, y, z);
                byte b = game.selectedBlock();
                game.setBlock(x, y, z, b);
                if (!game.consumeSelected()) game.setBlock(x, y, z, before);
            }
        }
        prevLeft = left;
        prevRight = right;
    }

    private boolean intersectsPlayer(int x, int y, int z) {
        return x + 1 > game.playerX - RADIUS && x < game.playerX + RADIUS
                && y + 1 > game.playerY && y < game.playerY + HEIGHT
                && z + 1 > game.playerZ - RADIUS && z < game.playerZ + RADIUS;
    }

    private int findTargetAnimal() {
        double[] look = direction();
        int best = -1;
        double bestScore = -1;
        for (int i = 0; i < MiniMinecraftGame.MAX_ANIMALS; i++) {
            if (!game.animalAlive[i]) continue;
            double dx = game.animalX[i] - game.playerX;
            double dy = game.animalY[i] + 0.55 - (game.playerY + EYE);
            double dz = game.animalZ[i] - game.playerZ;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > 4.8 || dist < 0.2) continue;
            double dot = (dx * look[0] + dy * look[1] + dz * look[2]) / dist;
            if (dot < 0.985) continue;
            Hit obstruction = ray(game.playerX, game.playerY + EYE, game.playerZ, dx / dist, dy / dist, dz / dist, dist - 0.25);
            if (obstruction != null) continue;
            double score = dot * 5.0 - dist * 0.04;
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    private void drawWorld(DrawContext ctx) {
        int pixel = Math.max(5, Math.min(7, this.width / 250));
        double aspect = (double) this.width / Math.max(1, this.height);
        double fov = Math.tan(Math.toRadians(72) / 2.0);
        double light = game.daylight();
        int skyTop = mix(0xFF071022, 0xFF5C9BE8, light);
        int skyBottom = mix(0xFF1B2130, 0xFFC5DFF5, light);

        for (int y = 0; y < this.height; y += pixel) {
            double t = (double) y / this.height;
            ctx.fill(0, y, this.width, Math.min(this.height, y + pixel), mix(skyTop, skyBottom, t));
        }

        if (game.isNight()) {
            for (int i = 0; i < 54; i++) {
                int sx = Math.floorMod(hash(i, 17, 91), Math.max(1, this.width));
                int sy = 5 + Math.floorMod(hash(i, 63, 11), Math.max(1, this.height / 2));
                if ((i & 3) == 0) ctx.fill(sx, sy, sx + 2, sy + 2, 0xFFE8F3FF);
                else ctx.fill(sx, sy, sx + 1, sy + 1, 0xFFBFCDE3);
            }
        } else {
            int sunX = (int) (this.width * (0.12 + game.dayTime * 0.76));
            int sunY = 45 + (int) (Math.sin(game.dayTime * Math.PI) * -28);
            ctx.fill(sunX - 7, sunY - 7, sunX + 8, sunY + 8, 0xFFFFE98A);
        }

        double cloudShift = (System.currentTimeMillis() / 90L) % Math.max(1, this.width + 120);
        for (int i = 0; i < 5; i++) {
            int x = (int) ((i * 210 + cloudShift) % (this.width + 140)) - 70;
            int y = 35 + (i % 3) * 25;
            ctx.fill(x, y, x + 54, y + 8, 0x55FFFFFF);
            ctx.fill(x + 12, y - 5, x + 40, y + 11, 0x44FFFFFF);
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
                double cameraX = nx * fov * aspect, cameraY = ny * fov;
                double dx = fx + rx * cameraX + ux * cameraY;
                double dy = fy + uy * cameraY;
                double dz = fz + rz * cameraX + uz * cameraY;
                double inv = 1.0 / Math.sqrt(dx * dx + dy * dy + dz * dz);
                Hit h = ray(game.playerX, game.playerY + EYE, game.playerZ, dx * inv, dy * inv, dz * inv, 22.0);
                if (h == null) continue;
                byte block = game.block(h.x, h.y, h.z);
                int base = texturedColor(block, h.x, h.y, h.z);
                double face = h.face == 1 ? 1.0 : (h.face == 0 ? 0.76 : 0.87);
                double fog = clamp(1.0 - h.dist / 32.0, 0.38, 1.0);
                double blockLight = block == MiniMinecraftGame.TORCH ? 1.0 : Math.max(0.32, light);
                int c = shade(base, face * fog * blockLight);
                ctx.fill(px, py, Math.min(this.width, px + pixel), Math.min(this.height, py + pixel), c);
            }
        }
    }

    private void drawAnimals(DrawContext ctx) {
        double aspect = (double) width / Math.max(1, height);
        double fov = Math.tan(Math.toRadians(72) / 2.0);
        double sp = Math.sin(game.pitch), cp = Math.cos(game.pitch);
        double sy = Math.sin(game.yaw), cy = Math.cos(game.yaw);
        double fx = sy * cp, fy = sp, fz = cy * cp;
        double rx = cy, rz = -sy;
        double ux = -sy * sp, uy = cp, uz = -cy * sp;

        for (int i = 0; i < MiniMinecraftGame.MAX_ANIMALS; i++) {
            if (!game.animalAlive[i]) continue;
            double vx = game.animalX[i] - game.playerX;
            double vy = game.animalY[i] + 0.55 - (game.playerY + EYE);
            double vz = game.animalZ[i] - game.playerZ;
            double forward = vx * fx + vy * fy + vz * fz;
            if (forward <= 0.25 || forward > 18.0) continue;
            double lateral = vx * rx + vz * rz;
            double vertical = vx * ux + vy * uy + vz * uz;
            double sxNorm = lateral / (forward * fov * aspect);
            double syNorm = vertical / (forward * fov);
            if (Math.abs(sxNorm) > 1.15 || Math.abs(syNorm) > 1.15) continue;

            int sx = width / 2 + (int) (sxNorm * width / 2.0);
            int syScreen = height / 2 - (int) (syNorm * height / 2.0);
            int size = (int) clamp(82.0 / forward, 8, 45);
            int body = animalColor(game.animalType[i]);
            int dark = shade(body, 0.68);
            ctx.fill(sx - size, syScreen - size / 2, sx + size, syScreen + size / 2, body);
            ctx.fill(sx + size / 2, syScreen - size * 3 / 4, sx + size + size / 2, syScreen, body);
            ctx.fill(sx - size + 2, syScreen + size / 2, sx - size / 2 + 2, syScreen + size, dark);
            ctx.fill(sx + size / 2 - 2, syScreen + size / 2, sx + size - 2, syScreen + size, dark);
            ctx.fill(sx + size, syScreen - size / 2, sx + size + 2, syScreen - size / 2 + 2, 0xFF111111);

            if (i == targetedAnimal) {
                String label = MiniMinecraftGame.ANIMAL_NAMES[game.animalType[i]] + "  HP " + game.animalHp[i];
                ctx.fill(sx - 38, syScreen - size - 20, sx + 38, syScreen - size - 7, 0xB0000000);
                ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(label), sx, syScreen - size - 18, 0xFFFFFFFF);
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
            ctx.fill(cx - 28, cy + 11, cx + 28, cy + 15, 0xCC111111);
            ctx.fill(cx - 27, cy + 12, cx - 27 + (int) (54 * p), cy + 14, 0xFFFFFFFF);
        }

        ctx.fill(5, 5, 208, 42, 0x88000000);
        ctx.drawTextWithShadow(this.textRenderer,
                Text.literal("MINI MINECRAFT • SURVIVAL").formatted(Formatting.GREEN, Formatting.BOLD), 10, 9, 0xFFFFFF);
        ctx.drawTextWithShadow(this.textRenderer,
                Text.literal("WASD • Space • Ctrl • E craft • F eat").formatted(Formatting.WHITE), 10, 21, 0xFFFFFF);
        ctx.drawTextWithShadow(this.textRenderer,
                Text.literal("LMB mine/attack • RMB place • 1-9").formatted(Formatting.GRAY), 10, 32, 0xFFFFFF);

        ctx.fill(this.width - 174, 5, this.width - 5, 42, 0x88000000);
        ctx.drawTextWithShadow(textRenderer,
                Text.literal("Food: apple " + game.apples + "  raw " + game.rawMeat + "  cooked " + game.cookedMeat),
                this.width - 169, 10, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer,
                Text.literal("Coal " + game.coal + "  Iron " + game.ironOre + "/" + game.ironIngots),
                this.width - 169, 21, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer,
                Text.literal(game.toolSummary()).formatted(Formatting.GRAY),
                this.width - 169, 32, 0xFFFFFF);

        int statY = this.height - 58;
        drawMeter(ctx, cx - 112, statY, game.health, 20, 0xFFE84949, "HP");
        drawMeter(ctx, cx + 7, statY, game.hunger, 20, 0xFFE6A23C, "FOOD");
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Day " + (game.daysSurvived + 1) + (game.isNight() ? " • Night" : " • Daytime")),
                cx, statY - 10, 0xFFFFFF);

        int slot = 24, total = MiniMinecraftGame.HOTBAR.length * slot, left = cx - total / 2, top = this.height - 32;
        for (int i = 0; i < MiniMinecraftGame.HOTBAR.length; i++) {
            byte b = MiniMinecraftGame.HOTBAR[i];
            int x = left + i * slot;
            ctx.fill(x, top, x + 22, top + 22, i == game.selectedHotbar ? 0xFFFFFFFF : 0xC8333333);
            ctx.fill(x + 2, top + 2, x + 20, top + 20, 0xEE111111);
            ctx.fill(x + 5, top + 5, x + 17, top + 17, color(b));
            ctx.drawTextWithShadow(this.textRenderer, Text.literal(Integer.toString(i + 1)), x + 2, top + 2, 0xFFFFFF);
            String count = Integer.toString(game.inventory(b));
            ctx.drawTextWithShadow(this.textRenderer, Text.literal(count), x + 20 - this.textRenderer.getWidth(count), top + 12, 0xFFFFFF);
        }
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(game.selectedName()), cx, top - 10, 0xFFFFFF);

        if (toastTime > 0) {
            int w = this.textRenderer.getWidth(toast) + 14;
            ctx.fill(cx - w / 2, 49, cx + w / 2, 66, 0xB0000000);
            ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(toast), cx, 54, 0xFFFFFF);
        }
    }

    private void drawMeter(DrawContext ctx, int x, int y, int value, int max, int color, String label) {
        ctx.fill(x, y, x + 105, y + 9, 0xCC111111);
        int fill = (int) (101 * clamp(value / (double) max, 0, 1));
        ctx.fill(x + 2, y + 2, x + 2 + fill, y + 7, color);
        ctx.drawTextWithShadow(this.textRenderer, Text.literal(label + " " + value), x + 3, y, 0xFFFFFFFF);
    }

    private Hit ray(double ox, double oy, double oz, double dx, double dy, double dz, double max) {
        double step = 0.08;
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
        if (message == null || message.isBlank()) return;
        toast = message;
        toastTime = 2.1;
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
        if (k == GLFW.GLFW_KEY_E) {
            if (this.client != null) this.client.setScreen(new MiniCraftingScreen(this, game));
            return true;
        }
        if (k == GLFW.GLFW_KEY_F) {
            say(game.eatBestFood());
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
            case MiniMinecraftGame.STONE -> 0xFF777B80;
            case MiniMinecraftGame.WOOD -> 0xFF79512E;
            case MiniMinecraftGame.LEAVES -> 0xFF3A8535;
            case MiniMinecraftGame.COBBLE -> 0xFF666B70;
            case MiniMinecraftGame.SAND -> 0xFFD8C27A;
            case MiniMinecraftGame.PLANKS -> 0xFFB88652;
            case MiniMinecraftGame.TORCH -> 0xFFFFC94A;
            case MiniMinecraftGame.WATER -> 0xFF397CC6;
            case MiniMinecraftGame.COAL_ORE -> 0xFF454A4F;
            case MiniMinecraftGame.IRON_ORE -> 0xFF9B7964;
            case MiniMinecraftGame.CRAFTING_TABLE -> 0xFF9A6537;
            case MiniMinecraftGame.FURNACE -> 0xFF53575C;
            default -> 0xFF000000;
        };
    }

    private static int texturedColor(byte b, int x, int y, int z) {
        int base = color(b);
        int h = hash(x, y, z);
        double variation = 0.88 + (Math.floorMod(h, 7) / 6.0) * 0.18;
        if (b == MiniMinecraftGame.GRASS && (h & 7) == 0) variation *= 1.08;
        if (b == MiniMinecraftGame.STONE && (h & 15) == 0) variation *= 0.78;
        if (b == MiniMinecraftGame.WATER) variation = 0.88 + Math.floorMod(h, 5) * 0.025;
        return shade(base, variation);
    }

    private static int animalColor(int type) {
        return switch (type) {
            case 0 -> 0xFFF1A2AE;
            case 1 -> 0xFF8B6649;
            case 2 -> 0xFFF2E5BC;
            default -> 0xFFE7E7E2;
        };
    }

    private static int shade(int c, double f) {
        int r = (int) (((c >> 16) & 255) * f);
        int g = (int) (((c >> 8) & 255) * f);
        int b = (int) ((c & 255) * f);
        return 0xFF000000 | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
    }

    private static int mix(int a, int b, double t) {
        t = clamp(t, 0, 1);
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    private static int hash(int a, int b, int c) {
        int h = a * 73428767 ^ b * 912931 ^ c * 19349663;
        h ^= h >>> 13;
        h *= 1274126177;
        return h ^ (h >>> 16);
    }

    private record Hit(int x, int y, int z, int px, int py, int pz, double dist, int face) {}
}
