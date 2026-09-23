package com.survivalutils;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class SurvivalUtilsClient implements ClientModInitializer {
    public static final SurvivalConfig CONFIG = new SurvivalConfig();
    public static final SurvivalStats STATS = new SurvivalStats();
    public static final WarningManager WARNINGS = new WarningManager();
    public static final AutoWaterClutch AUTO_CLUTCH = new AutoWaterClutch();

    private static KeyBinding menuKey;

    private record Line(String text, int color) {}

    @Override
    public void onInitializeClient() {
        CONFIG.load();
        ExoLinkData.load();
        SeedCrackerShortcut.register();

        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.survivalutils.menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F9,
                KeyBinding.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(SurvivalUtilsClient::tick);

        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.of("survivalutils", "hud"),
                SurvivalUtilsClient::renderHud
        );
    }

    private static void tick(MinecraftClient client) {
        while (menuKey.wasPressed()) {
            if (client.currentScreen instanceof ExoLinkScreen) {
                client.setScreen(null);
            } else {
                client.setScreen(new ExoLinkScreen());
            }
        }

        STATS.tick(client);
        AUTO_CLUTCH.tick(client);
        ThreatMemoryManager.tick(client);
        CombatAdvisor.tick(client);
        PackManager.tick(client);
        VisorDamageSystem.tick(client);
        WARNINGS.tick(client);
    }

    private static void renderHud(DrawContext ctx, RenderTickCounter counter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.textRenderer == null) return;

        if (ExoLinkData.SETTINGS.exoHud) {
            ExoHudRenderer.render(ctx, client, WARNINGS.active());
            return;
        }

        if (CONFIG.isEnabled(Feature.MAIN_HUD)) renderMainHud(ctx, client);
        if (CONFIG.isEnabled(Feature.STATS_PANEL)) renderStatsHud(ctx, client);
        if (CONFIG.isEnabled(Feature.HELMET_OVERLAY)) renderHelmetHud(ctx, client);
        renderWarningBanner(ctx, client);
    }

    private static void renderMainHud(DrawContext ctx, MinecraftClient client) {
        var p = client.player;
        List<Line> lines = new ArrayList<>();

        if (CONFIG.isEnabled(Feature.HEALTH_HUD)) {
            lines.add(new Line(
                    String.format(Locale.ROOT, "HP %.1f/%.1f", p.getHealth(), p.getMaxHealth()),
                    p.getHealth() <= 8f ? 0xFFFF5D5D : 0xFF7CFFB2));
        }

        if (CONFIG.isEnabled(Feature.HUNGER_HUD)) {
            lines.add(new Line("FOOD " + p.getHungerManager().getFoodLevel() + "/20", 0xFFFFD36A));
        }

        if (CONFIG.isEnabled(Feature.ARMOR_HUD)) {
            lines.add(new Line("ARMOR " + p.getArmor() + "/20", 0xFF8BE9FD));
        }

        if (CONFIG.isEnabled(Feature.ARMOR_DURABILITY_HUD)) {
            lines.add(new Line(armorDurabilityLine(p), 0xFF9FD7FF));
        }

        if (CONFIG.isEnabled(Feature.XP_HUD)) {
            lines.add(new Line("XP " + p.experienceLevel, 0xFFC7FF77));
        }

        if (CONFIG.isEnabled(Feature.COORDS_HUD)) {
            lines.add(new Line("XYZ " + p.getBlockX() + " " + p.getBlockY() + " " + p.getBlockZ(), 0xFFD7E3EA));
        }

        if (CONFIG.isEnabled(Feature.DIRECTION_HUD)) {
            Direction facing = Direction.fromHorizontalDegrees(p.getYaw());
            lines.add(new Line("DIR " + facing.asString().toUpperCase(Locale.ROOT), 0xFFAEDBFF));
        }

        if (CONFIG.isEnabled(Feature.FALL_DISTANCE_HUD)) {
            double fall = p.fallDistance;
            int fallColor = fall >= 8f ? 0xFFFF5D5D : (fall >= 4f ? 0xFFFFC15A : 0xFF9FB4C0);
            lines.add(new Line(
                    String.format(Locale.ROOT, "FALL %.1fm // %s", fall, AUTO_CLUTCH.status()),
                    fallColor));
        }

        if (CONFIG.isEnabled(Feature.BIOME_HUD)) {
            String biome = client.world.getBiome(p.getBlockPos())
                    .getKey()
                    .map(k -> k.getValue().getPath())
                    .orElse("unknown");
            lines.add(new Line("BIOME " + biome, 0xFF9FD7B7));
        }

        if (CONFIG.isEnabled(Feature.DIMENSION_HUD)) {
            lines.add(new Line("DIM " + client.world.getRegistryKey().getValue().getPath(), 0xFF9FAEC0));
        }

        if (CONFIG.isEnabled(Feature.LIGHT_HUD)) {
            lines.add(new Line("LIGHT " + client.world.getLightLevel(p.getBlockPos()), 0xFFFFE58A));
        }

        if (CONFIG.isEnabled(Feature.DAY_NIGHT_HUD)) {
            lines.add(new Line(dayNight(client), 0xFF7DE3FF));
        }

        if (CONFIG.isEnabled(Feature.WEATHER_HUD)) {
            String weather = client.world.isThundering()
                    ? "THUNDER"
                    : (client.world.isRaining() ? "RAIN" : "CLEAR");
            lines.add(new Line("WEATHER " + weather, 0xFF8EC6FF));
        }

        if (CONFIG.isEnabled(Feature.EFFECTS_HUD)) {
            lines.add(new Line("EFFECTS " + p.getStatusEffects().size(), 0xFFD9A7FF));
        }

        ItemStack held = p.getMainHandStack();

        if (CONFIG.isEnabled(Feature.HELD_ITEM_HUD)) {
            lines.add(new Line(
                    "HELD " + (held.isEmpty() ? "empty" : held.getName().getString() + " x" + held.getCount()),
                    0xFFC6D3DB));
        }

        if (CONFIG.isEnabled(Feature.DURABILITY_HUD) && !held.isEmpty() && held.isDamageable()) {
            int dura = InventoryUtil.durabilityPercent(held);
            lines.add(new Line("DURA " + dura + "%", dura <= 10 ? 0xFFFF5D5D : 0xFFB7C7D6));
        }

        if (CONFIG.isEnabled(Feature.INVENTORY_SPACE)) {
            int free = InventoryUtil.freeSlots(p);
            lines.add(new Line("INV " + free + " free", free <= 2 ? 0xFFFF6B6B : 0xFFB7C7D6));
        }

        if (CONFIG.isEnabled(Feature.TOTEM_COUNT)) {
            lines.add(new Line("TOTEMS " + InventoryUtil.count(p, "totem_of_undying"), 0xFFFFD98A));
        }

        if (CONFIG.isEnabled(Feature.FOOD_COUNT)) {
            lines.add(new Line("FOOD ITEMS " + InventoryUtil.foodCount(p), 0xFFFFC96A));
        }

        if (CONFIG.isEnabled(Feature.TORCH_COUNT)) {
            lines.add(new Line("TORCHES " + InventoryUtil.count(p, "torch"), 0xFFFFD982));
        }

        if (CONFIG.isEnabled(Feature.ARROW_COUNT)) {
            lines.add(new Line("ARROWS " + InventoryUtil.count(p, "arrow"), 0xFFD8E0E6));
        }

        if (CONFIG.isEnabled(Feature.ROCKET_COUNT)) {
            lines.add(new Line("ROCKETS " + InventoryUtil.count(p, "firework_rocket"), 0xFFB3D9FF));
        }

        if (CONFIG.isEnabled(Feature.PEARL_COUNT)) {
            lines.add(new Line("PEARLS " + InventoryUtil.count(p, "ender_pearl"), 0xFFBFA5FF));
        }

        ThreatAnalyzer.Snapshot threat = ThreatAnalyzer.analyze(client);

        if (CONFIG.isEnabled(Feature.THREAT_HUD)) {
            lines.add(new Line(threat.line(), threat.color()));
            if (!threat.reasons().isEmpty()) {
                lines.add(new Line(threat.reasonLine(), threat.color()));
            }
        }

        if (CONFIG.isEnabled(Feature.READINESS_HUD)) {
            int readyColor = threat.readiness() >= 75
                    ? 0xFF74F0A6
                    : (threat.readiness() >= 45 ? 0xFFFFC65A : 0xFFFF6666);
            lines.add(new Line(
                    "READINESS " + threat.readiness() + "% // " + threat.recommendation(),
                    readyColor));
        }

        if (CONFIG.isEnabled(Feature.NEAREST_HOSTILE_HUD)) {
            if (threat.nearestType() == null || threat.nearestType().isBlank()) {
                lines.add(new Line("NEAREST HOSTILE none", 0xFF75F0A4));
            } else {
                lines.add(new Line(
                        "NEAREST " + threat.nearestType() + " "
                                + String.format(Locale.ROOT, "%.1fm", threat.nearestDistance()),
                        threat.color()));
            }
        }

        if (CONFIG.isEnabled(Feature.SAFE_SLEEP_HUD)) {
            long t = Math.floorMod(client.world.getTimeOfDay(), 24000L);
            boolean night = t >= 12542L && t < 23460L;
            String sleep;
            int sleepColor;

            if (!InventoryUtil.hasBed(p)) {
                sleep = "SLEEP // NO BED";
                sleepColor = 0xFFFFA97A;
            } else if (!night) {
                sleep = "SLEEP // DAYTIME";
                sleepColor = 0xFF9FB4C0;
            } else if (threat.hostileCount() > 0) {
                sleep = "SLEEP // HOSTILES NEAR";
                sleepColor = 0xFFFF6B6B;
            } else {
                sleep = "SLEEP // READY";
                sleepColor = 0xFF74F0A6;
            }

            lines.add(new Line(sleep, sleepColor));
        }

        if (CONFIG.isEnabled(Feature.PLAYER_RADAR)) {
            var nearby = client.world.getPlayers().stream()
                    .filter(other -> other != p
                            && other.squaredDistanceTo(p) <= CONFIG.playerRadarRadius * CONFIG.playerRadarRadius)
                    .sorted(Comparator.comparingDouble(p::squaredDistanceTo))
                    .toList();

            lines.add(new Line("PLAYERS NEAR " + nearby.size(), 0xFF9DD0FF));

            int shown = 0;
            for (var other : nearby) {
                if (shown++ >= 3) break;

                double distance = Math.sqrt(p.squaredDistanceTo(other));
                boolean spectator = false;

                if (client.getNetworkHandler() != null) {
                    var info = client.getNetworkHandler().getPlayerListEntry(other.getUuid());
                    spectator = info != null && info.getGameMode() == GameMode.SPECTATOR;
                }

                lines.add(new Line(
                        "  " + other.getGameProfile().name()
                                + " " + String.format(Locale.ROOT, "%.1fm", distance)
                                + (spectator ? " [SPEC]" : ""),
                        spectator ? 0xFFFF8C8C : 0xFFB6DFFF));
            }
        }

        if (CONFIG.isEnabled(Feature.FPS_HUD)) {
            lines.add(new Line("FPS " + client.getCurrentFps(), 0xFF9AF0D2));
        }

        if (CONFIG.isEnabled(Feature.PING_HUD) && client.getNetworkHandler() != null) {
            var entry = client.getNetworkHandler().getPlayerListEntry(p.getUuid());
            if (entry != null) {
                lines.add(new Line("PING " + entry.getLatency() + "ms", 0xFF9FB4C0));
            }
        }

        if (CONFIG.isEnabled(Feature.PICKAXE_DURABILITY)) {
            ItemStack pick = InventoryUtil.findBestPickaxe(p);
            if (!pick.isEmpty()) {
                lines.add(new Line(
                        "PICK " + InventoryUtil.durabilityPercent(pick) + "%",
                        0xFF9FC6D8));
            }
        }

        if (CONFIG.isEnabled(Feature.ORE_SUMMARY)) {
            lines.add(new Line(
                    "ORE D" + InventoryUtil.count(p, "diamond")
                            + " I" + InventoryUtil.count(p, "raw_iron")
                            + " G" + InventoryUtil.count(p, "raw_gold")
                            + " C" + InventoryUtil.count(p, "coal"),
                    0xFFBFE8FF));
        }

        if (CONFIG.isEnabled(Feature.ALTITUDE_HINT)) {
            lines.add(new Line(altitudeHint(client), 0xFFAFBDD0));
        }

        if (CONFIG.isEnabled(Feature.DIAMOND_Y_HINT)
                && client.world.getRegistryKey().getValue().getPath().contains("overworld")) {
            lines.add(new Line(
                    p.getBlockY() <= -50 ? "DIAMOND DEPTH // GOOD" : "DIAMOND DEPTH // GO LOWER",
                    0xFF7BE7FF));
        }

        if (CONFIG.isEnabled(Feature.ANCIENT_DEBRIS_Y_HINT)
                && client.world.getRegistryKey().getValue().getPath().contains("nether")) {
            lines.add(new Line(
                    p.getBlockY() >= 13 && p.getBlockY() <= 17
                            ? "DEBRIS DEPTH // GOOD"
                            : "DEBRIS DEPTH // TARGET Y15",
                    0xFFFF9F7A));
        }

        if (CONFIG.isEnabled(Feature.NETHER_COORD_CONVERTER)) {
            String dim = client.world.getRegistryKey().getValue().getPath();
            if (dim.contains("nether")) {
                lines.add(new Line(
                        "OW ≈ " + p.getBlockX() * 8 + " / " + p.getBlockZ() * 8,
                        0xFFFFA06B));
            } else if (dim.contains("overworld")) {
                lines.add(new Line(
                        "NETHER ≈ " + Math.floorDiv(p.getBlockX(), 8) + " / " + Math.floorDiv(p.getBlockZ(), 8),
                        0xFFFFA06B));
            }
        }

        if (CONFIG.isEnabled(Feature.BED_ALERT) && !InventoryUtil.hasBed(p)) {
            lines.add(new Line("BED none", 0xFFFFA97A));
        }

        if (CONFIG.isEnabled(Feature.WATER_BUCKET_ALERT) && !InventoryUtil.hasWaterBucket(p)) {
            lines.add(new Line("WATER BUCKET none", 0xFF8ACBFF));
        }

        if (CONFIG.isEnabled(Feature.AUTO_WATER_CLUTCH)) {
            String clutch = AUTO_CLUTCH.status();
            int clutchColor = switch (clutch) {
                case "ARMED" -> 0xFF79F2B1;
                case "TRIGGERING" -> 0xFF62E8FF;
                case "NO WATER" -> 0xFFFF6B6B;
                default -> 0xFF9FB4C0;
            };
            lines.add(new Line("CLUTCH " + clutch, clutchColor));
        }

        if (CONFIG.isEnabled(Feature.SHIELD_DURABILITY)) {
            ItemStack shield = InventoryUtil.findShield(p);
            if (!shield.isEmpty()) {
                lines.add(new Line("SHIELD " + InventoryUtil.durabilityPercent(shield) + "%", 0xFF9ED2FF));
            }
        }

        int max = Math.min(CONFIG.maxMainLines, lines.size());
        if (max > 0) {
            renderPanel(
                    ctx,
                    client,
                    CONFIG.hudRight,
                    7,
                    "SURVIVAL // HUD",
                    lines.subList(0, max),
                    0xFF4CE8E2);
        }
    }

    private static void renderStatsHud(DrawContext ctx, MinecraftClient client) {
        List<Line> lines = new ArrayList<>();
        var p = client.player;

        if (CONFIG.isEnabled(Feature.SESSION_TIMER)) {
            long s = STATS.sessionSeconds();
            lines.add(new Line("SESSION " + (s / 60) + "m " + (s % 60) + "s", 0xFFB9C6D0));
        }

        if (CONFIG.isEnabled(Feature.JOURNEY_DISTANCE)) {
            lines.add(new Line(
                    String.format(Locale.ROOT, "DIST %.0fm", STATS.journeyDistance()),
                    0xFF89E7FF));
        }

        if (CONFIG.isEnabled(Feature.SPEED_HUD)) {
            lines.add(new Line(
                    String.format(Locale.ROOT, "SPEED %.1f b/s", STATS.speedBlocksPerSecond()),
                    0xFF82F0CE));
        }

        if (CONFIG.isEnabled(Feature.WORLD_DAY_COUNTER)) {
            lines.add(new Line("WORLD DAY " + (client.world.getTimeOfDay() / 24000L), 0xFFFFD885));
        }

        if (CONFIG.isEnabled(Feature.CHUNK_HUD)) {
            lines.add(new Line(
                    "CHUNK " + Math.floorDiv(p.getBlockX(), 16) + " " + Math.floorDiv(p.getBlockZ(), 16),
                    0xFFB8CAD8));
        }

        if (CONFIG.isEnabled(Feature.MEMORY_USAGE)) {
            Runtime rt = Runtime.getRuntime();
            long used = (rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L);
            long max = rt.maxMemory() / (1024L * 1024L);
            lines.add(new Line("JAVA " + used + "/" + max + " MB", 0xFFC4B2FF));
        }

        if (!lines.isEmpty()) {
            renderPanel(ctx, client, false, Math.max(96, ctx.getScaledWindowHeight() / 3),
                    "SURVIVAL // STATS", lines, 0xFF87BFFF);
        }
    }

    private static void renderHelmetHud(DrawContext ctx, MinecraftClient client) {
        var p = client.player;
        ItemStack helmet = InventoryUtil.helmet(p);
        if (helmet.isEmpty()) return;

        List<Line> lines = new ArrayList<>();

        if (CONFIG.isEnabled(Feature.HELMET_DURABILITY)) {
            String helmetText = "HELM " + helmet.getName().getString();
            if (helmet.isDamageable()) helmetText += " " + InventoryUtil.durabilityPercent(helmet) + "%";
            lines.add(new Line(helmetText, 0xFF8EEAFF));

            ItemStack held = p.getMainHandStack();
            if (!held.isEmpty() && held.isDamageable()) {
                lines.add(new Line("TOOL " + InventoryUtil.durabilityPercent(held) + "%", 0xFFB7C7D6));
            }
        }

        if (CONFIG.isEnabled(Feature.HELMET_THREAT)) {
            ThreatAnalyzer.Snapshot threat = ThreatAnalyzer.analyze(client);
            lines.add(new Line(
                    "THREAT " + threat.level() + " // " + threat.score() + " // READY " + threat.readiness() + "%",
                    threat.color()));

            if (threat.nearestType() != null && !threat.nearestType().isBlank()) {
                lines.add(new Line(
                        "NEAREST " + threat.nearestType()
                                + " " + String.format(Locale.ROOT, "%.1fm", threat.nearestDistance()),
                        threat.color()));
            }
        }

        if (CONFIG.isEnabled(Feature.HELMET_COORDS)) {
            lines.add(new Line("NAV " + p.getBlockX() + " " + p.getBlockY() + " " + p.getBlockZ(), 0xFFC9E6F2));
        }

        if (CONFIG.isEnabled(Feature.HELMET_TIME)) {
            lines.add(new Line(dayNight(client), 0xFF84D7FF));
        }

        if (CONFIG.isEnabled(Feature.HELMET_PLAYERS)) {
            long players = client.world.getPlayers().stream()
                    .filter(other -> other != p && other.squaredDistanceTo(p) <= 32 * 32)
                    .count();
            lines.add(new Line("PLAYER SCAN " + players, 0xFF9DD0FF));
        }

        if (lines.isEmpty()) return;

        int width = 0;
        for (Line line : lines) width = Math.max(width, client.textRenderer.getWidth(line.text));

        int boxW = Math.max(190, width + 12);
        int boxH = lines.size() * 11 + 22;
        int left = (ctx.getScaledWindowWidth() - boxW) / 2;
        int top = 7;
        int right = left + boxW;

        ctx.fill(left, top, right, top + boxH, 0x8A050B10);
        ctx.fill(left, top, right, top + 2, 0xFF4CE8E2);
        ctx.fill(left, top, left + 2, top + boxH, 0x554CE8E2);
        ctx.fill(right - 2, top, right, top + boxH, 0x554CE8E2);

        ctx.drawCenteredTextWithShadow(
                client.textRenderer,
                "HELM // SURVIVAL LINK",
                (left + right) / 2,
                top + 5,
                0xFF4CE8E2);

        int y = top + 17;
        for (Line line : lines) {
            ctx.drawCenteredTextWithShadow(
                    client.textRenderer,
                    line.text,
                    (left + right) / 2,
                    y,
                    line.color);
            y += 11;
        }
    }

    private static void renderWarningBanner(DrawContext ctx, MinecraftClient client) {
        if (!CONFIG.warningBanner) return;

        WarningManager.Warning warning = WARNINGS.active();
        if (warning == null) return;

        int color = switch (warning.severity()) {
            case INFO -> 0xFFFFE27A;
            case CAUTION -> 0xFFFFB45D;
            case DANGER -> 0xFFFF4E4E;
            case CRITICAL -> 0xFFFF2424;
        };

        int screenW = ctx.getScaledWindowWidth();
        int textW = client.textRenderer.getWidth(warning.text());
        int width = Math.min(screenW - 20, textW + 24);
        int left = (screenW - width) / 2;

        int helmetOffset = CONFIG.isEnabled(Feature.HELMET_OVERLAY)
                && !InventoryUtil.helmet(client.player).isEmpty() ? 96 : 10;
        int top = helmetOffset;

        boolean danger = warning.severity() == WarningManager.Severity.DANGER;
        int height = danger ? 28 : 23;
        int bg = danger ? 0xE20B0E12 : 0xD2080D12;

        ctx.fill(left, top, left + width, top + height, bg);
        ctx.fill(left, top, left + width, top + 3, color);
        ctx.fill(left, top, left + 3, top + height, color);
        ctx.fill(left + width - 3, top, left + width, top + height, color);

        if (danger) {
            long phase = (System.currentTimeMillis() / 180L) & 1L;
            if (phase == 0L) {
                ctx.fill(0, 0, 3, ctx.getScaledWindowHeight(), 0x88FF3030);
                ctx.fill(screenW - 3, 0, screenW, ctx.getScaledWindowHeight(), 0x88FF3030);
            }
            ctx.drawCenteredTextWithShadow(
                    client.textRenderer,
                    "⚠ DANGER",
                    screenW / 2,
                    top + 5,
                    color);
            ctx.drawCenteredTextWithShadow(
                    client.textRenderer,
                    warning.text(),
                    screenW / 2,
                    top + 16,
                    0xFFFFFFFF);
        } else {
            ctx.drawCenteredTextWithShadow(
                    client.textRenderer,
                    warning.text(),
                    screenW / 2,
                    top + 8,
                    color);
        }
    }

    private static String armorDurabilityLine(net.minecraft.entity.player.PlayerEntity player) {
        ItemStack head = player.getEquippedStack(EquipmentSlot.HEAD);
        ItemStack chest = player.getEquippedStack(EquipmentSlot.CHEST);
        ItemStack legs = player.getEquippedStack(EquipmentSlot.LEGS);
        ItemStack feet = player.getEquippedStack(EquipmentSlot.FEET);

        return "ARMOR DURA H" + pieceDurability(head)
                + " C" + pieceDurability(chest)
                + " L" + pieceDurability(legs)
                + " B" + pieceDurability(feet);
    }

    private static String pieceDurability(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "--";
        if (!stack.isDamageable()) return "100";
        return Integer.toString(InventoryUtil.durabilityPercent(stack));
    }

    private static String dayNight(MinecraftClient client) {
        long t = Math.floorMod(client.world.getTimeOfDay(), 24000L);
        boolean night = t >= 13000L && t < 23000L;

        long remainingTicks = night
                ? 23000L - t
                : (t < 13000L ? 13000L - t : 24000L - t + 13000L);

        long seconds = Math.max(0L, remainingTicks / 20L);
        return (night ? "NIGHT " : "DAY ") + (seconds / 60) + "m " + (seconds % 60) + "s";
    }

    private static String altitudeHint(MinecraftClient client) {
        int y = client.player.getBlockY();
        String dim = client.world.getRegistryKey().getValue().getPath();

        if (dim.contains("nether")) {
            if (y >= 13 && y <= 17) return "Y " + y + " // common debris band";
            return "Y " + y + " // Nether";
        }

        if (dim.contains("overworld")) {
            if (y <= -50) return "Y " + y + " // deep mining";
            if (y >= 120) return "Y " + y + " // high altitude";
            return "Y " + y + " // overworld";
        }

        return "Y " + y;
    }

    private static void renderPanel(
            DrawContext ctx,
            MinecraftClient client,
            boolean rightSide,
            int y,
            String title,
            List<Line> lines,
            int accent
    ) {
        if (lines == null || lines.isEmpty()) return;

        int lineH = 11;
        int pad = 4;
        int widest = client.textRenderer.getWidth(title);

        for (Line line : lines) widest = Math.max(widest, client.textRenderer.getWidth(line.text));

        int boxW = widest + pad * 2;
        int boxH = lines.size() * lineH + pad * 2 + 10;
        int left = rightSide ? ctx.getScaledWindowWidth() - boxW - 7 : 7;
        int right = left + boxW;

        ctx.fill(left, y, right, y + boxH, 0xB5091118);
        ctx.fill(left, y, right, y + 2, accent);
        ctx.drawText(client.textRenderer, title, left + pad, y + 5, accent, true);

        int ty = y + 16;
        for (Line line : lines) {
            ctx.drawText(client.textRenderer, line.text, left + pad, ty, line.color, true);
            ty += lineH;
        }
    }
}
