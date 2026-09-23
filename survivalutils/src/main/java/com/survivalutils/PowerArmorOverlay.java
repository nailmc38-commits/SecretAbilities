package com.survivalutils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

public final class PowerArmorOverlay {
    private PowerArmorOverlay() {}

    public static void render(DrawContext ctx, MinecraftClient client) {
        if (client == null || client.player == null || client.textRenderer == null) return;
        if (!SurvMegaState.SETTINGS.exo) return;

        ItemStack helmet = client.player.getEquippedStack(EquipmentSlot.HEAD);
        if (helmet == null || helmet.isEmpty()) return;

        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();

        int steel = 0xAD252C2F;
        int steelDark = 0xC511171A;
        int trim = 0xFF65777B;
        int glow = 0xFF61D9EA;
        int glass = 0x4C8FBBC2;

        // Top mechanical brow. Keep the middle mostly clear for the old helmet HUD.
        ctx.fill(0, 0, w / 4, 7, steel);
        ctx.fill(3 * w / 4, 0, w, 7, steel);
        ctx.fill(0, 7, w / 7, 13, steelDark);
        ctx.fill(6 * w / 7, 7, w, 13, steelDark);
        ctx.fill(0, 0, w / 5, 2, trim);
        ctx.fill(4 * w / 5, 0, w, 2, trim);

        // Thick side helmet structure.
        ctx.fill(0, 0, 8, h, steel);
        ctx.fill(w - 8, 0, w, h, steel);
        ctx.fill(8, h / 6, 13, 5 * h / 6, steelDark);
        ctx.fill(w - 13, h / 6, w - 8, 5 * h / 6, steelDark);

        // Angled-looking cheek sections built from stepped plates.
        for (int i = 0; i < 5; i++) {
            int yy = h / 2 + i * 6;
            ctx.fill(8 + i * 3, yy, 24 + i * 3, yy + 4, steel);
            ctx.fill(w - 24 - i * 3, yy, w - 8 - i * 3, yy + 4, steel);
        }

        // Bottom jaw brackets.
        ctx.fill(0, h - 11, w / 5, h, steel);
        ctx.fill(4 * w / 5, h - 11, w, h, steel);
        ctx.fill(w / 5 - 1, h - 7, w / 5 + 32, h - 4, trim);
        ctx.fill(4 * w / 5 - 32, h - 7, 4 * w / 5 + 1, h - 4, trim);

        // Subtle visor-glass side tint only; center stays clear.
        ctx.fill(13, 14, 18, h - 14, glass);
        ctx.fill(w - 18, 14, w - 13, h - 14, glass);

        // Compact EXO identity, placed low so it does not replace the old HUD.
        int integrity = SurvMegaState.armorIntegrity(client.player);
        String status = "EXO " + SurvMegaState.armorName(client.player) + " // " + integrity + "%";
        int statusW = client.textRenderer.getWidth(status);
        int sx = (w - statusW) / 2;
        int sy = h - 18;
        ctx.fill(sx - 6, sy - 3, sx + statusW + 6, sy + 10, 0xB20C1214);
        ctx.fill(sx - 6, sy + 10, sx + statusW + 6, sy + 12, glow);
        ctx.drawTextWithShadow(client.textRenderer, status, sx, sy,
                integrity <= 25 ? 0xFFFF6A6A : 0xFFD8F8FF);

        // Small armor-piece strip on lower-left.
        String pieces = "H " + piece(client.player.getEquippedStack(EquipmentSlot.HEAD))
                + "  C " + piece(client.player.getEquippedStack(EquipmentSlot.CHEST))
                + "  L " + piece(client.player.getEquippedStack(EquipmentSlot.LEGS))
                + "  B " + piece(client.player.getEquippedStack(EquipmentSlot.FEET));
        ctx.drawTextWithShadow(client.textRenderer, pieces, 18, h - 28, 0xFFA9C3C8);

        if (SurvMegaState.SETTINGS.visorDamage && SurvMegaState.visorDamage() > 0.01) {
            renderCracks(ctx, SurvMegaState.visorDamage());
        }
    }

    private static String piece(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "--";
        if (!stack.isDamageable()) return "100";
        return Integer.toString(InventoryUtil.durabilityPercent(stack));
    }

    private static void renderCracks(DrawContext ctx, double damage) {
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();

        int visible = Math.max(1, (int)Math.ceil(damage * 6.0));
        int primary = 0xB9D9F7FF;
        int highlight = 0xE6F6FEFF;

        int[][] starts = {
                {2, h / 4},
                {w - 3, h / 3},
                {w / 5, 2},
                {4 * w / 5, 3},
                {w / 8, h - 3},
                {7 * w / 8, h - 3}
        };

        int[][] ends = {
                {w / 3, h / 2 - 28},
                {2 * w / 3, h / 2 + 22},
                {w / 2 - 46, h / 3},
                {w / 2 + 52, 2 * h / 3},
                {w / 3, 2 * h / 3},
                {2 * w / 3, h / 3}
        };

        for (int i = 0; i < visible; i++) {
            int[] a = starts[i % starts.length];
            int[] b = ends[i % ends.length];
            crack(ctx, a[0], a[1], b[0], b[1], primary, damage > 0.65 ? 2 : 1);

            int mx = (a[0] + b[0]) / 2;
            int my = (a[1] + b[1]) / 2;
            crack(ctx, mx, my,
                    mx + (i % 2 == 0 ? 30 : -28),
                    my + (i % 3 == 0 ? 34 : -25),
                    highlight, 1);
            crack(ctx, mx, my,
                    mx + (i % 2 == 0 ? -18 : 22),
                    my + 28,
                    primary, 1);
        }

        // Heavy damage darkens the outer visor instead of covering the whole screen.
        if (damage > 0.45) {
            int alpha = Math.min(50, (int)(damage * 55));
            int shade = (alpha << 24) | 0x00131B1E;
            ctx.fill(0, 0, 24, h, shade);
            ctx.fill(w - 24, 0, w, h, shade);
        }
    }

    private static void crack(DrawContext ctx, int x1, int y1, int x2, int y2, int color, int thickness) {
        int segments = 8;
        int px = x1;
        int py = y1;

        for (int i = 1; i <= segments; i++) {
            double f = i / (double)segments;
            int jitter = ((i * 17 + x1 + y1) % 9) - 4;
            int nx = (int)Math.round(x1 + (x2 - x1) * f + (i % 2 == 0 ? jitter : -jitter));
            int ny = (int)Math.round(y1 + (y2 - y1) * f + (i % 3 == 0 ? jitter : 0));
            line(ctx, px, py, nx, ny, color, thickness);
            px = nx;
            py = ny;
        }
    }

    private static void line(DrawContext ctx, int x1, int y1, int x2, int y2, int color, int thickness) {
        int steps = Math.max(1, Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)));
        for (int i = 0; i <= steps; i++) {
            double f = i / (double)steps;
            int x = (int)Math.round(x1 + (x2 - x1) * f);
            int y = (int)Math.round(y1 + (y2 - y1) * f);
            ctx.fill(x, y, x + thickness, y + thickness, color);
        }
    }
}
