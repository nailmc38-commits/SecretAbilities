package com.secret.minigames.screens;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public final class CrystalPracticeScreen extends Screen {
    private static final int W = 48, H = 22, D = 48;
    private static final byte AIR = 0, GRASS = 1, DIRT = 2, STONE = 3, OBSIDIAN = 4;
    private static final double RADIUS = 0.28, HEIGHT = 1.75, EYE = 1.62;

    private enum Item { OBSIDIAN, CRYSTAL }

    private final Screen parent;
    private final byte[][][] blocks = new byte[W][H][D];
    private final List<Crystal> crystals = new ArrayList<>();

    private double x = W / 2.0 + 0.5, y, z = D / 2.0 + 6.5;
    private double velocityY, yaw = Math.PI, pitch = -0.10;
    private boolean prevJump, prevRight, prevLeft;
    private int mouseWarmup = 3;
    private long lastNanos;
    private Item selected = Item.OBSIDIAN;
    private int obsidianPlaced, crystalsPlaced, crystalsBroken;
    private String status = "R obsidian • Left Alt crystal • RMB place • LMB break";
    private double statusTime = 5.0;

    public CrystalPracticeScreen(Screen parent) {
        super(Text.literal("Crystal PvP Practice"));
        this.parent = parent;
        resetArena();
    }

    @Override
    protected void init() {
        lastNanos = System.nanoTime();
        mouseWarmup = 3;
        centerMouse();
    }

    private void resetArena() {
        crystals.clear();
        for (int xx = 0; xx < W; xx++) for (int yy = 0; yy < H; yy++) Arrays.fill(blocks[xx][yy], AIR);
        for (int xx = 0; xx < W; xx++) {
            for (int zz = 0; zz < D; zz++) {
                blocks[xx][0][zz] = OBSIDIAN;
                blocks[xx][1][zz] = STONE;
                blocks[xx][2][zz] = DIRT;
                blocks[xx][3][zz] = GRASS;
            }
        }
        for (int gx = 6; gx < W - 5; gx += 7) {
            for (int gz = 6; gz < D - 5; gz += 7) {
                blocks[gx][3][gz] = OBSIDIAN;
                if (((gx + gz) / 7) % 3 == 0) blocks[gx][4][gz] = OBSIDIAN;
            }
        }
        x = W / 2.0 + 0.5;
        z = D / 2.0 + 6.5;
        y = highestSolid((int) x, (int) z) + 1.01;
        velocityY = 0;
        selected = Item.OBSIDIAN;
        obsidianPlaced = crystalsPlaced = crystalsBroken = 0;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        double dt = Math.min(0.05, Math.max(0.001, (now - lastNanos) / 1_000_000_000.0));
        lastNanos = now;
        statusTime = Math.max(0, statusTime - dt);
        updateLook();
        updateMovement(dt);
        updateUse();
        drawWorld(ctx);
        drawHud(ctx);
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void updateLook() {
        if (client == null) return;
        double cx = client.getWindow().getWidth() / 2.0;
        double cy = client.getWindow().getHeight() / 2.0;
        double mx = client.mouse.getX(), my = client.mouse.getY();
        if (mouseWarmup-- <= 0) {
            yaw += (mx - cx) * 0.0028;
            pitch -= (my - cy) * 0.0028;
            pitch = clamp(pitch, -1.35, 1.35);
        }
        centerMouse();
    }

    private void centerMouse() {
        if (client == null) return;
        GLFW.glfwSetCursorPos(client.getWindow().getHandle(), client.getWindow().getWidth() / 2.0, client.getWindow().getHeight() / 2.0);
    }

    private void updateMovement(double dt) {
        if (client == null) return;
        long win = client.getWindow().getHandle();
        double f = (down(win, GLFW.GLFW_KEY_W) ? 1 : 0) - (down(win, GLFW.GLFW_KEY_S) ? 1 : 0);
        double s = (down(win, GLFW.GLFW_KEY_D) ? 1 : 0) - (down(win, GLFW.GLFW_KEY_A) ? 1 : 0);
        double len = Math.sqrt(f * f + s * s);
        if (len > 0) {
            f /= len; s /= len;
            double speed = down(win, GLFW.GLFW_KEY_LEFT_CONTROL) ? 5.2 : 3.8;
            double sin = Math.sin(yaw), cos = Math.cos(yaw);
            move((sin * f + cos * s) * speed * dt, (cos * f - sin * s) * speed * dt);
        }

        boolean jump = down(win, GLFW.GLFW_KEY_SPACE);
        boolean grounded = collides(x, y - 0.07, z);
        if (jump && !prevJump && grounded) velocityY = 6.4;
        prevJump = jump;
        velocityY -= 13.0 * dt;
        double ny = y + velocityY * dt;
        if (!collides(x, ny, z)) y = ny;
        else {
            if (velocityY < 0) {
                double ty = y;
                for (int i = 0; i < 28 && collides(x, ty - 0.015, z); i++) ty += 0.015;
                y = ty;
            }
            velocityY = 0;
        }
        if (y < -3) resetPlayer();
    }

    private void move(double dx, double dz) {
        boolean grounded = collides(x, y - 0.06, z);
        if (!collides(x + dx, y, z)) x += dx;
        else if (grounded && !collides(x + dx, y + 0.62, z)) { y += 0.62; x += dx; }
        if (!collides(x, y, z + dz)) z += dz;
        else if (grounded && !collides(x, y + 0.62, z + dz)) { y += 0.62; z += dz; }
    }

    private void resetPlayer() {
        x = W / 2.0 + 0.5;
        z = D / 2.0 + 6.5;
        y = highestSolid((int) x, (int) z) + 1.01;
        velocityY = 0;
    }

    private void updateUse() {
        if (client == null) return;
        long win = client.getWindow().getHandle();
        boolean right = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        boolean left = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (right && !prevRight) placeSelected();
        if (left && !prevLeft) breakCrystal();
        prevRight = right;
        prevLeft = left;
    }

    private void placeSelected() {
        double[] d = direction();
        Hit hit = ray(x, y + EYE, z, d[0], d[1], d[2], 6.0);
        if (hit == null) { say("Aim at terrain."); return; }

        if (selected == Item.OBSIDIAN) {
            int px = hit.px, py = hit.py, pz = hit.pz;
            if (!inside(px, py, pz) || blocks[px][py][pz] != AIR || intersectsPlayer(px, py, pz) || crystalAt(px + 0.5, py + 0.5, pz + 0.5) != null) {
                say("No room for obsidian.");
                return;
            }
            blocks[px][py][pz] = OBSIDIAN;
            obsidianPlaced++;
            say("Obsidian placed. Left Alt → crystal.");
            return;
        }

        int bx = hit.x, by = hit.y, bz = hit.z;
        if (blocks[bx][by][bz] != OBSIDIAN) { say("Crystals need obsidian."); return; }
        int cy = by + 1;
        if (!inside(bx, cy, bz) || blocks[bx][cy][bz] != AIR || blocks[bx][Math.min(H - 1, cy + 1)][bz] != AIR) {
            say("Not enough space above obsidian.");
            return;
        }
        if (crystalAt(bx + 0.5, cy + 0.55, bz + 0.5) != null || playerNear(bx + 0.5, cy + 0.55, bz + 0.5, 1.0)) {
            say("Crystal placement blocked.");
            return;
        }
        crystals.add(new Crystal(bx + 0.5, cy + 0.55, bz + 0.5));
        crystalsPlaced++;
        say("Crystal placed. LMB to break.");
    }

    private void breakCrystal() {
        double[] d = direction();
        Crystal target = aimedCrystal(d[0], d[1], d[2], 6.0);
        if (target == null) return;
        crystals.remove(target);
        crystalsBroken++;
        explode(target.x, target.y, target.z);
        say("Crystal popped • no self-damage.");
    }

    private Crystal aimedCrystal(double dx, double dy, double dz, double max) {
        Crystal best = null;
        double bestAlong = max + 1;
        double ox = x, oy = y + EYE, oz = z;
        for (Crystal c : crystals) {
            double vx = c.x - ox, vy = c.y - oy, vz = c.z - oz;
            double along = vx * dx + vy * dy + vz * dz;
            if (along < 0 || along > max) continue;
            double px = ox + dx * along, py = oy + dy * along, pz = oz + dz * along;
            double dist = distance(px, py, pz, c.x, c.y, c.z);
            if (dist < 0.48 && along < bestAlong) { best = c; bestAlong = along; }
        }
        return best;
    }

    private void explode(double cx, double cy, double cz) {
        double radius = 3.45;
        int r = 4;
        for (int xx = floor(cx) - r; xx <= floor(cx) + r; xx++) {
            for (int yy = floor(cy) - r; yy <= floor(cy) + r; yy++) {
                for (int zz = floor(cz) - r; zz <= floor(cz) + r; zz++) {
                    if (!inside(xx, yy, zz)) continue;
                    byte b = blocks[xx][yy][zz];
                    if (b == AIR || b == OBSIDIAN) continue;
                    double dist = distance(xx + 0.5, yy + 0.5, zz + 0.5, cx, cy, cz);
                    if (dist > radius) continue;
                    double strength = 1.0 - dist / radius;
                    int noise = Math.floorMod(hash(xx, yy, zz), 100);
                    if (strength > 0.38 || noise < (int) (strength * 100)) blocks[xx][yy][zz] = AIR;
                }
            }
        }
        Iterator<Crystal> it = crystals.iterator();
        while (it.hasNext()) {
            Crystal c = it.next();
            if (distance(c.x, c.y, c.z, cx, cy, cz) < 3.0) it.remove();
        }
    }

    private void drawWorld(DrawContext ctx) {
        int pixel = Math.max(5, Math.min(7, width / 250));
        double aspect = (double) width / Math.max(1, height);
        double fov = Math.tan(Math.toRadians(72) / 2.0);
        ctx.fill(0, 0, width, height, 0xFF80B6E6);
        ctx.fill(0, height / 2, width, height, 0xFFBFD5E8);

        double sp = Math.sin(pitch), cp = Math.cos(pitch);
        double sy = Math.sin(yaw), cy = Math.cos(yaw);
        double fx = sy * cp, fy = sp, fz = cy * cp;
        double rx = cy, rz = -sy;
        double ux = -sy * sp, uy = cp, uz = -cy * sp;

        for (int py = 0; py < height; py += pixel) {
            double ny = 1.0 - ((py + pixel * 0.5) / height) * 2.0;
            for (int px = 0; px < width; px += pixel) {
                double nx = ((px + pixel * 0.5) / width) * 2.0 - 1.0;
                double sx = nx * fov * aspect, syy = ny * fov;
                double dx = fx + rx * sx + ux * syy;
                double dy = fy + uy * syy;
                double dz = fz + rz * sx + uz * syy;
                double inv = 1.0 / Math.sqrt(dx * dx + dy * dy + dz * dz);
                Hit h = ray(x, y + EYE, z, dx * inv, dy * inv, dz * inv, 22.0);
                double blockDist = h == null ? Double.MAX_VALUE : h.dist;
                Crystal c = nearestCrystalAlongRay(x, y + EYE, z, dx * inv, dy * inv, dz * inv, 22.0);
                double crystalDist = c == null ? Double.MAX_VALUE : projectedDistance(x, y + EYE, z, dx * inv, dy * inv, dz * inv, c);
                if (c != null && crystalDist < blockDist && crystalDistanceToRay(x, y + EYE, z, dx * inv, dy * inv, dz * inv, c) < 0.42) {
                    int col = ((px / pixel + py / pixel) & 1) == 0 ? 0xFFFF7DDF : 0xFFF6F0FF;
                    ctx.fill(px, py, Math.min(width, px + pixel), Math.min(height, py + pixel), col);
                } else if (h != null) {
                    byte b = blocks[h.x][h.y][h.z];
                    int base = blockColor(b, h.x, h.y, h.z);
                    double face = h.face == 1 ? 1.0 : (h.face == 0 ? 0.76 : 0.87);
                    double fog = clamp(1.0 - h.dist / 30.0, 0.42, 1.0);
                    ctx.fill(px, py, Math.min(width, px + pixel), Math.min(height, py + pixel), shade(base, face * fog));
                }
            }
        }
    }

    private Crystal nearestCrystalAlongRay(double ox, double oy, double oz, double dx, double dy, double dz, double max) {
        Crystal best = null;
        double bestD = max + 1;
        for (Crystal c : crystals) {
            double along = projectedDistance(ox, oy, oz, dx, dy, dz, c);
            if (along >= 0 && along <= max && along < bestD && crystalDistanceToRay(ox, oy, oz, dx, dy, dz, c) < 0.52) {
                best = c; bestD = along;
            }
        }
        return best;
    }

    private double projectedDistance(double ox, double oy, double oz, double dx, double dy, double dz, Crystal c) {
        return (c.x - ox) * dx + (c.y - oy) * dy + (c.z - oz) * dz;
    }

    private double crystalDistanceToRay(double ox, double oy, double oz, double dx, double dy, double dz, Crystal c) {
        double t = projectedDistance(ox, oy, oz, dx, dy, dz, c);
        return distance(ox + dx * t, oy + dy * t, oz + dz * t, c.x, c.y, c.z);
    }

    private void drawHud(DrawContext ctx) {
        int cx = width / 2, cy = height / 2;
        ctx.fill(cx - 5, cy, cx - 1, cy + 1, 0xFFFFFFFF);
        ctx.fill(cx + 2, cy, cx + 6, cy + 1, 0xFFFFFFFF);
        ctx.fill(cx, cy - 5, cx + 1, cy - 1, 0xFFFFFFFF);
        ctx.fill(cx, cy + 2, cx + 1, cy + 6, 0xFFFFFFFF);

        ctx.fill(6, 6, 244, 45, 0xAA000000);
        ctx.drawTextWithShadow(textRenderer, Text.literal("CRYSTAL PvP PRACTICE").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), 11, 10, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer, Text.literal("WASD • Space • Ctrl sprint • RMB place • LMB break"), 11, 22, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer, Text.literal("R Obsidian   Left Alt Crystal   Backspace Reset").formatted(Formatting.GRAY), 11, 34, 0xFFFFFF);

        ctx.fill(width - 184, 6, width - 6, 45, 0xAA000000);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Selected: " + (selected == Item.OBSIDIAN ? "OBSIDIAN" : "END CRYSTAL")), width - 178, 10, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Obby " + obsidianPlaced + " • Crystals " + crystalsPlaced + " • Pops " + crystalsBroken), width - 178, 22, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Self damage: OFF").formatted(Formatting.GREEN), width - 178, 34, 0xFFFFFF);

        int slotY = height - 34;
        drawSlot(ctx, cx - 70, slotY, 64, "R", "Obsidian", 0xFF261D33, selected == Item.OBSIDIAN);
        drawSlot(ctx, cx + 6, slotY, 64, "ALT", "Crystal", 0xFFFF79DB, selected == Item.CRYSTAL);

        if (statusTime > 0) {
            int w = textRenderer.getWidth(status) + 14;
            ctx.fill(cx - w / 2, 52, cx + w / 2, 69, 0xB0000000);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(status), cx, 57, 0xFFFFFF);
        }
    }

    private void drawSlot(DrawContext ctx, int sx, int sy, int sw, String key, String label, int color, boolean active) {
        ctx.fill(sx, sy, sx + sw, sy + 27, active ? 0xFFFFFFFF : 0xCC3A3A3A);
        ctx.fill(sx + 2, sy + 2, sx + sw - 2, sy + 25, 0xEE111111);
        ctx.fill(sx + 6, sy + 6, sx + 18, sy + 18, color);
        ctx.drawTextWithShadow(textRenderer, Text.literal(key), sx + 21, sy + 5, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer, Text.literal(label), sx + 21, sy + 15, 0xFFBBBBBB);
    }

    private Hit ray(double ox, double oy, double oz, double dx, double dy, double dz, double max) {
        double step = 0.07;
        int px = floor(ox), py = floor(oy), pz = floor(oz);
        for (double d = 0; d <= max; d += step) {
            int bx = floor(ox + dx * d), by = floor(oy + dy * d), bz = floor(oz + dz * d);
            if (inside(bx, by, bz) && blocks[bx][by][bz] != AIR) {
                int face = by != py ? 1 : (bx != px ? 0 : 2);
                return new Hit(bx, by, bz, px, py, pz, d, face);
            }
            px = bx; py = by; pz = bz;
        }
        return null;
    }

    private double[] direction() {
        double cp = Math.cos(pitch);
        return new double[]{Math.sin(yaw) * cp, Math.sin(pitch), Math.cos(yaw) * cp};
    }

    private boolean collides(double px, double py, double pz) {
        int minX = floor(px - RADIUS), maxX = floor(px + RADIUS);
        int minY = floor(py), maxY = floor(py + HEIGHT - 0.01);
        int minZ = floor(pz - RADIUS), maxZ = floor(pz + RADIUS);
        for (int bx = minX; bx <= maxX; bx++) for (int by = minY; by <= maxY; by++) for (int bz = minZ; bz <= maxZ; bz++) {
            if (solid(bx, by, bz)) return true;
        }
        return false;
    }

    private boolean solid(int bx, int by, int bz) {
        if (by < 0) return true;
        if (by >= H) return false;
        if (bx < 0 || bx >= W || bz < 0 || bz >= D) return true;
        return blocks[bx][by][bz] != AIR;
    }

    private boolean intersectsPlayer(int bx, int by, int bz) {
        return bx + 1 > x - RADIUS && bx < x + RADIUS && by + 1 > y && by < y + HEIGHT && bz + 1 > z - RADIUS && bz < z + RADIUS;
    }

    private boolean playerNear(double px, double py, double pz, double r) {
        return distance(px, py, pz, x, y + HEIGHT * 0.5, z) < r;
    }

    private Crystal crystalAt(double cx, double cy, double cz) {
        for (Crystal c : crystals) if (distance(c.x, c.y, c.z, cx, cy, cz) < 0.65) return c;
        return null;
    }

    private int highestSolid(int bx, int bz) {
        for (int by = H - 1; by >= 0; by--) if (inside(bx, by, bz) && blocks[bx][by][bz] != AIR) return by;
        return 0;
    }

    private boolean inside(int bx, int by, int bz) { return bx >= 0 && bx < W && by >= 0 && by < H && bz >= 0 && bz < D; }

    private void say(String s) { status = s; statusTime = 2.2; }

    @Override
    public boolean keyPressed(KeyInput input) {
        int k = input.key();
        if (k == GLFW.GLFW_KEY_ESCAPE || k == GLFW.GLFW_KEY_F9) { if (client != null) client.setScreen(parent); return true; }
        if (k == GLFW.GLFW_KEY_R) { selected = Item.OBSIDIAN; say("Obsidian selected."); return true; }
        if (k == GLFW.GLFW_KEY_LEFT_ALT) { selected = Item.CRYSTAL; say("End Crystal selected."); return true; }
        if (k == GLFW.GLFW_KEY_BACKSPACE) { resetArena(); say("Arena reset."); return true; }
        return super.keyPressed(input);
    }

    @Override public boolean shouldPause() { return false; }

    private int blockColor(byte b, int bx, int by, int bz) {
        int n = Math.floorMod(hash(bx, by, bz), 18) - 9;
        int base = switch (b) {
            case GRASS -> 0xFF62A84A;
            case DIRT -> 0xFF83572F;
            case STONE -> 0xFF74777A;
            case OBSIDIAN -> 0xFF241B31;
            default -> 0xFF000000;
        };
        return vary(base, n);
    }

    private static int hash(int a, int b, int c) { int h = a * 734287 + b * 912931 + c * 438289; h ^= h >>> 13; return h; }
    private static int vary(int color, int delta) { int r=clamp255(((color>>16)&255)+delta),g=clamp255(((color>>8)&255)+delta),b=clamp255((color&255)+delta);return 0xFF000000|(r<<16)|(g<<8)|b; }
    private static int shade(int color,double f){int r=clamp255((int)(((color>>16)&255)*f)),g=clamp255((int)(((color>>8)&255)*f)),b=clamp255((int)((color&255)*f));return 0xFF000000|(r<<16)|(g<<8)|b;}
    private static int clamp255(int v){return Math.max(0,Math.min(255,v));}
    private static double clamp(double v,double a,double b){return Math.max(a,Math.min(b,v));}
    private static int floor(double v){return (int)Math.floor(v);}
    private static boolean down(long win,int key){return GLFW.glfwGetKey(win,key)==GLFW.GLFW_PRESS;}
    private static double distance(double ax,double ay,double az,double bx,double by,double bz){double dx=ax-bx,dy=ay-by,dz=az-bz;return Math.sqrt(dx*dx+dy*dy+dz*dz);}

    private record Hit(int x,int y,int z,int px,int py,int pz,double dist,int face){}
    private record Crystal(double x,double y,double z){}
}
