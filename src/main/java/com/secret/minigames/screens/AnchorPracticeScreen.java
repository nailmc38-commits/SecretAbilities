package com.secret.minigames.screens;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;

public final class AnchorPracticeScreen extends Screen {
    private static final int W = 48, H = 22, D = 48;
    private static final byte AIR = 0, GRASS = 1, DIRT = 2, STONE = 3, OBSIDIAN = 4, ANCHOR = 5, CHARGED = 6;
    private static final double RADIUS = 0.28, HEIGHT = 1.75, EYE = 1.62;

    private enum Item { ANCHOR, GLOWSTONE }

    private final Screen parent;
    private final byte[][][] blocks = new byte[W][H][D];
    private double x = W / 2.0 + 0.5, y, z = D / 2.0 + 6.5;
    private double velocityY, yaw = Math.PI, pitch = -0.10;
    private boolean prevJump, prevRight;
    private int mouseWarmup = 3;
    private long lastNanos;
    private Item selected = Item.ANCHOR;
    private int anchorsPlaced, explosions;
    private String status = "Z anchor • X glowstone • RMB use";
    private double statusTime = 4.0;

    public AnchorPracticeScreen(Screen parent) {
        super(Text.literal("Anchor PvP Practice"));
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
        for (int xx = 0; xx < W; xx++) for (int yy = 0; yy < H; yy++) Arrays.fill(blocks[xx][yy], AIR);
        for (int xx = 0; xx < W; xx++) {
            for (int zz = 0; zz < D; zz++) {
                blocks[xx][0][zz] = OBSIDIAN;
                blocks[xx][1][zz] = STONE;
                blocks[xx][2][zz] = DIRT;
                blocks[xx][3][zz] = GRASS;
            }
        }
        // Practice obsidian pads / pillars scattered around the arena.
        for (int gx = 7; gx < W - 6; gx += 8) {
            for (int gz = 7; gz < D - 6; gz += 8) {
                blocks[gx][4][gz] = OBSIDIAN;
                if (((gx + gz) / 8) % 2 == 0) blocks[gx][5][gz] = OBSIDIAN;
                for (int ox = -1; ox <= 1; ox++) for (int oz = -1; oz <= 1; oz++) {
                    if (inside(gx + ox, 3, gz + oz)) blocks[gx + ox][3][gz + oz] = OBSIDIAN;
                }
            }
        }
        x = W / 2.0 + 0.5;
        z = D / 2.0 + 6.5;
        y = highestSolid((int) x, (int) z) + 1.01;
        velocityY = 0;
        anchorsPlaced = 0;
        explosions = 0;
        selected = Item.ANCHOR;
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
            double speed = down(win, GLFW.GLFW_KEY_LEFT_CONTROL) ? 5.1 : 3.7;
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
        if (y < -3) {
            x = W / 2.0 + 0.5; z = D / 2.0 + 6.5; y = highestSolid((int) x, (int) z) + 1.01; velocityY = 0;
        }
    }

    private void move(double dx, double dz) {
        boolean grounded = collides(x, y - 0.06, z);
        if (!collides(x + dx, y, z)) x += dx;
        else if (grounded && !collides(x + dx, y + 0.55, z)) { y += 0.55; x += dx; }
        if (!collides(x, y, z + dz)) z += dz;
        else if (grounded && !collides(x, y + 0.55, z + dz)) { y += 0.55; z += dz; }
    }

    private void updateUse() {
        if (client == null) return;
        long win = client.getWindow().getHandle();
        boolean right = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        if (right && !prevRight) useItem();
        prevRight = right;
    }

    private void useItem() {
        double[] d = direction();
        Hit hit = ray(x, y + EYE, z, d[0], d[1], d[2], 6.0);
        if (hit == null) { say("Aim at the arena terrain."); return; }

        if (selected == Item.ANCHOR) {
            int px = hit.px, py = hit.py, pz = hit.pz;
            if (!inside(px, py, pz) || blocks[px][py][pz] != AIR || intersectsPlayer(px, py, pz)) {
                say("No room to place the anchor.");
                return;
            }
            blocks[px][py][pz] = ANCHOR;
            anchorsPlaced++;
            say("Anchor placed. X → glowstone.");
            return;
        }

        byte target = blocks[hit.x][hit.y][hit.z];
        if (target == ANCHOR) {
            blocks[hit.x][hit.y][hit.z] = CHARGED;
            say("Anchor charged. RMB again to explode.");
        } else if (target == CHARGED) {
            explode(hit.x, hit.y, hit.z);
            explosions++;
            say("BOOM • No self-damage • Terrain destroyed.");
        } else {
            say("Glowstone only works on an anchor.");
        }
    }

    private void explode(int cx, int cy, int cz) {
        blocks[cx][cy][cz] = AIR;
        double radius = 3.25;
        int r = 4;
        for (int xx = cx - r; xx <= cx + r; xx++) {
            for (int yy = cy - r; yy <= cy + r; yy++) {
                for (int zz = cz - r; zz <= cz + r; zz++) {
                    if (!inside(xx, yy, zz)) continue;
                    byte b = blocks[xx][yy][zz];
                    if (b == AIR) continue;
                    double dx = xx + 0.5 - (cx + 0.5), dy = yy + 0.5 - (cy + 0.5), dz = zz + 0.5 - (cz + 0.5);
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (dist > radius) continue;
                    // Obsidian pads survive so you always have reusable practice surfaces.
                    if (b == OBSIDIAN) continue;
                    double strength = 1.0 - dist / radius;
                    int noise = Math.floorMod(hash(xx, yy, zz), 100);
                    if (strength > 0.42 || noise < (int) (strength * 100)) blocks[xx][yy][zz] = AIR;
                }
            }
        }
        // Deliberately no damage / knockback to the practice player.
    }

    private void drawWorld(DrawContext ctx) {
        int pixel = Math.max(5, Math.min(7, width / 250));
        double aspect = (double) width / Math.max(1, height);
        double fov = Math.tan(Math.toRadians(72) / 2.0);
        ctx.fill(0, 0, width, height, 0xFF83B9E8);
        ctx.fill(0, height / 2, width, height, 0xFFBFD6E8);

        double sp = Math.sin(pitch), cp = Math.cos(pitch);
        double sy = Math.sin(yaw), cy = Math.cos(yaw);
        double fx = sy * cp, fy = sp, fz = cy * cp;
        double rx = cy, rz = -sy;
        double ux = -sy * sp, uy = cp, uz = -cy * sp;

        for (int py = 0; py < height; py += pixel) {
            double ny = 1.0 - ((py + pixel * 0.5) / height) * 2.0;
            for (int px = 0; px < width; px += pixel) {
                double nx = ((px + pixel * 0.5) / width) * 2.0 - 1.0;
                double cx = nx * fov * aspect, cyy = ny * fov;
                double dx = fx + rx * cx + ux * cyy;
                double dy = fy + uy * cyy;
                double dz = fz + rz * cx + uz * cyy;
                double inv = 1.0 / Math.sqrt(dx * dx + dy * dy + dz * dz);
                Hit h = ray(x, y + EYE, z, dx * inv, dy * inv, dz * inv, 22.0);
                if (h == null) continue;
                byte b = blocks[h.x][h.y][h.z];
                int base = blockColor(b, h.x, h.y, h.z);
                double face = h.face == 1 ? 1.0 : (h.face == 0 ? 0.76 : 0.87);
                double fog = clamp(1.0 - h.dist / 30.0, 0.42, 1.0);
                ctx.fill(px, py, Math.min(width, px + pixel), Math.min(height, py + pixel), shade(base, face * fog));
            }
        }
    }

    private void drawHud(DrawContext ctx) {
        int cx = width / 2, cy = height / 2;
        ctx.fill(cx - 5, cy, cx - 1, cy + 1, 0xFFFFFFFF);
        ctx.fill(cx + 2, cy, cx + 6, cy + 1, 0xFFFFFFFF);
        ctx.fill(cx, cy - 5, cx + 1, cy - 1, 0xFFFFFFFF);
        ctx.fill(cx, cy + 2, cx + 1, cy + 6, 0xFFFFFFFF);

        ctx.fill(6, 6, 230, 45, 0xAA000000);
        ctx.drawTextWithShadow(textRenderer, Text.literal("ANCHOR PvP PRACTICE").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), 11, 10, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer, Text.literal("WASD • Space • Ctrl sprint • RMB use"), 11, 22, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Z Anchor   X Glowstone   R Reset arena").formatted(Formatting.GRAY), 11, 34, 0xFFFFFF);

        ctx.fill(width - 164, 6, width - 6, 45, 0xAA000000);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Selected: " + (selected == Item.ANCHOR ? "ANCHOR" : "GLOWSTONE")), width - 158, 10, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Placed " + anchorsPlaced + " • Explosions " + explosions), width - 158, 22, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Self damage: OFF").formatted(Formatting.GREEN), width - 158, 34, 0xFFFFFF);

        int slotY = height - 34;
        drawSlot(ctx, cx - 55, slotY, 50, "Z", "Anchor", 0xFF64334E, selected == Item.ANCHOR);
        drawSlot(ctx, cx + 5, slotY, 50, "X", "Glow", 0xFFFFD452, selected == Item.GLOWSTONE);

        if (statusTime > 0) {
            int w = textRenderer.getWidth(status) + 14;
            ctx.fill(cx - w / 2, 52, cx + w / 2, 69, 0xB0000000);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(status), cx, 57, 0xFFFFFF);
        }
    }

    private void drawSlot(DrawContext ctx, int x, int y, int w, String key, String label, int color, boolean active) {
        ctx.fill(x, y, x + w, y + 27, active ? 0xFFFFFFFF : 0xCC3A3A3A);
        ctx.fill(x + 2, y + 2, x + w - 2, y + 25, 0xEE111111);
        ctx.fill(x + 6, y + 6, x + 18, y + 18, color);
        ctx.drawTextWithShadow(textRenderer, Text.literal(key), x + 21, y + 5, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer, Text.literal(label), x + 21, y + 15, 0xFFBBBBBB);
    }

    private Hit ray(double ox, double oy, double oz, double dx, double dy, double dz, double max) {
        double step = 0.07;
        int px = floor(ox), py = floor(oy), pz = floor(oz);
        for (double d = 0; d <= max; d += step) {
            int xx = floor(ox + dx * d), yy = floor(oy + dy * d), zz = floor(oz + dz * d);
            if (inside(xx, yy, zz) && blocks[xx][yy][zz] != AIR) {
                int face = yy != py ? 1 : (xx != px ? 0 : 2);
                return new Hit(xx, yy, zz, px, py, pz, d, face);
            }
            px = xx; py = yy; pz = zz;
        }
        return null;
    }

    private double[] direction() {
        double cp = Math.cos(pitch);
        return new double[]{Math.sin(yaw) * cp, Math.sin(pitch), Math.cos(yaw) * cp};
    }

    private boolean collides(double px, double py, double pz) {
        int minX=floor(px-RADIUS), maxX=floor(px+RADIUS), minY=floor(py), maxY=floor(py+HEIGHT-0.01), minZ=floor(pz-RADIUS), maxZ=floor(pz+RADIUS);
        for (int xx=minX;xx<=maxX;xx++) for (int yy=minY;yy<=maxY;yy++) for (int zz=minZ;zz<=maxZ;zz++) if (solid(xx,yy,zz)) return true;
        return false;
    }

    private boolean intersectsPlayer(int bx, int by, int bz) {
        return bx + 1 > x - RADIUS && bx < x + RADIUS && by + 1 > y && by < y + HEIGHT && bz + 1 > z - RADIUS && bz < z + RADIUS;
    }

    private boolean solid(int xx, int yy, int zz) {
        if (yy < 0) return true;
        if (yy >= H) return false;
        if (xx < 0 || xx >= W || zz < 0 || zz >= D) return true;
        return blocks[xx][yy][zz] != AIR;
    }

    private boolean inside(int xx, int yy, int zz) { return xx>=0&&xx<W&&yy>=0&&yy<H&&zz>=0&&zz<D; }
    private int highestSolid(int xx, int zz) { for (int yy=H-1;yy>=0;yy--) if (blocks[xx][yy][zz]!=AIR) return yy; return 0; }

    private int blockColor(byte b, int xx, int yy, int zz) {
        int base = switch (b) {
            case GRASS -> 0xFF5FA94A;
            case DIRT -> 0xFF865932;
            case STONE -> 0xFF777B80;
            case OBSIDIAN -> 0xFF2C1D3B;
            case ANCHOR -> 0xFF58364F;
            case CHARGED -> 0xFFFFC944;
            default -> 0xFF000000;
        };
        return shade(base, 0.88 + Math.floorMod(hash(xx, yy, zz), 8) * 0.025);
    }

    private void say(String text) { status = text; statusTime = 2.3; }

    @Override
    public boolean keyPressed(KeyInput input) {
        int k = input.key();
        if (k == GLFW.GLFW_KEY_ESCAPE || k == GLFW.GLFW_KEY_F9) { if (client != null) client.setScreen(parent); return true; }
        if (k == GLFW.GLFW_KEY_Z) { selected = Item.ANCHOR; say("Anchor selected • RMB to place."); return true; }
        if (k == GLFW.GLFW_KEY_X) { selected = Item.GLOWSTONE; say("Glowstone selected • RMB anchor to charge/explode."); return true; }
        if (k == GLFW.GLFW_KEY_R) { resetArena(); say("Practice arena reset."); return true; }
        return super.keyPressed(input);
    }

    @Override public boolean shouldPause() { return false; }
    private static boolean down(long win,int key){return GLFW.glfwGetKey(win,key)==GLFW.GLFW_PRESS;}
    private static int floor(double v){return (int)Math.floor(v);}
    private static double clamp(double v,double a,double b){return Math.max(a,Math.min(b,v));}
    private static int shade(int c,double f){int r=(int)(((c>>16)&255)*f),g=(int)(((c>>8)&255)*f),b=(int)((c&255)*f);return 0xFF000000|(Math.min(255,r)<<16)|(Math.min(255,g)<<8)|Math.min(255,b);}
    private static int hash(int a,int b,int c){int h=a*73428767^b*912931^c*19349663;h^=h>>>13;h*=1274126177;return h^(h>>>16);}
    private record Hit(int x,int y,int z,int px,int py,int pz,double dist,int face){}
}
