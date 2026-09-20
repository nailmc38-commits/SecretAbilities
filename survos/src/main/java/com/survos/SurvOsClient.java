package com.survos;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SurvOsClient implements ClientModInitializer {
    public static final SurvConfig CONFIG = SurvConfig.load();
    public static final AutomationManager AUTOMATION = new AutomationManager();
    public static final VoiceService VOICE = new VoiceService();
    public static final LegacyBridge LEGACY = new LegacyBridge();
    private static KeyBinding menuKey, voiceKey, emergencyKey;
    private static long armedUntil;
    private static int ticks;
    private static boolean voiceStarted;

    @Override public void onInitializeClient() {
        LEGACY.initialize();
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.survos.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F9, KeyBinding.Category.MISC));
        voiceKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.survos.voice", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F8, KeyBinding.Category.MISC));
        emergencyKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.survos.emergency", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F7, KeyBinding.Category.MISC));
        VOICE.setListener(SurvOsClient::onVoiceHeard);
        ClientTickEvents.END_CLIENT_TICK.register(SurvOsClient::tick);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.of("survos", "main_hud"), SurvOsClient::renderHud);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("surv").executes(ctx -> {
                MinecraftClient.getInstance().setScreen(new SurvScreen(SurvScreen.Tab.DASHBOARD)); return 1;
            }));
            dispatcher.register(ClientCommandManager.literal("craft")
                    .then(ClientCommandManager.argument("item", StringArgumentType.greedyString())
                            .executes(ctx -> LEGACY.queueCraft(MinecraftClient.getInstance(), ctx.getSource(), StringArgumentType.getString(ctx, "item")))));
        });
    }

    private static void tick(MinecraftClient client) {
        while (menuKey.wasPressed()) {
            if (client.currentScreen instanceof SurvScreen) client.setScreen(null);
            else client.setScreen(new SurvScreen(SurvScreen.Tab.DASHBOARD));
        }
        while (voiceKey.wasPressed()) {
            VOICE.toggle(CONFIG); notice(VOICE.isRunning() ? "Voice listening" : "Voice off");
        }
        while (emergencyKey.wasPressed()) AUTOMATION.stop(client, "Emergency stop");
        LEGACY.tick(client);
        if (client.player == null || client.world == null) { voiceStarted = false; return; }
        ticks++;
        if (!voiceStarted && ticks > 40 && CONFIG.voiceEnabled && CONFIG.voiceAutoStart) {
            voiceStarted = true; VOICE.start(CONFIG);
        }
        AUTOMATION.tick(client, CONFIG);
        if (CONFIG.smartAlerts && ticks % 100 == 0) {
            ItemStack held = client.player.getMainHandStack();
            if (!held.isEmpty() && held.isDamageable()) {
                int left = held.getMaxDamage() - held.getDamage();
                int pct = (int)Math.round(left * 100.0 / Math.max(1, held.getMaxDamage()));
                if (pct <= 10) notice("Warning: " + held.getName().getString() + " at " + pct + "%");
            }
        }
    }

    private static void onVoiceHeard(String raw) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> handleIntent(client, raw));
    }

    private static void handleIntent(MinecraftClient client, String raw) {
        if (raw == null) return;
        String q = raw.toLowerCase(Locale.ROOT).trim();
        boolean wake = q.contains("surv") || q.startsWith("serve ") || q.startsWith("sir ");
        if (wake) {
            q = q.replaceFirst("(?i)\\b(surv|serve|sir)\\b[ ,.:;-]*", "").trim();
            armedUntil = System.currentTimeMillis() + 6000L;
        } else if (CONFIG.requireWakeWord && System.currentTimeMillis() > armedUntil) return;
        if (q.isBlank()) { armedUntil = System.currentTimeMillis() + 6000L; notice("Listening…"); return; }

        if (containsAny(q, "stop", "cancel everything", "cancel automation")) { AUTOMATION.stop(client, "Voice command"); return; }
        if (q.contains("pause")) { AUTOMATION.pause(client); return; }
        if (q.contains("resume") || q.contains("continue")) { AUTOMATION.resume(); return; }
        if (q.contains("mob") && (q.contains("grind") || q.contains("fight") || q.contains("combat"))) { AUTOMATION.start(AutomationManager.Mode.MOB_GRIND, client); return; }
        if (q.contains("mine") || q.contains("mining")) { AUTOMATION.start(AutomationManager.Mode.MINING, client); return; }
        if (q.contains("tree") || q.contains("wood")) { AUTOMATION.start(AutomationManager.Mode.TREE_FARM, client); return; }
        if (q.contains("crop") || q.contains("farm crops")) { AUTOMATION.start(AutomationManager.Mode.CROP_FARM, client); return; }
        if (q.contains("fish")) { AUTOMATION.start(AutomationManager.Mode.FISHING, client); return; }
        if (q.contains("animal")) { AUTOMATION.start(AutomationManager.Mode.ANIMAL_FARM, client); return; }
        if (q.contains("villager") || q.contains("villy")) {
            if (q.contains("start") || q.contains("cycle")) LEGACY.toggleVillager(client);
            else client.setScreen(new SurvScreen(SurvScreen.Tab.VILLAGER));
            return;
        }
        if (q.contains("enchant")) { LEGACY.openEnchantBuilder(client); return; }
        if (q.contains("craft")) { client.setScreen(new SurvScreen(SurvScreen.Tab.CRAFT)); notice("Craft panel opened"); return; }
        if (q.contains("profile")) {
            for (String p : new String[]{"mining","combat","grinding","nether","base","building","survival"}) {
                if (q.contains(p)) { CONFIG.applyProfile(p); notice("Profile: " + p.toUpperCase()); return; }
            }
        }
        if (q.contains("hide durability")) { CONFIG.showDurability = false; CONFIG.save(); notice("Durability hidden"); return; }
        if (q.contains("show durability")) { CONFIG.showDurability = true; CONFIG.save(); notice("Durability shown"); return; }
        if (q.contains("open settings") || q.contains("command center") || q.contains("open surv")) { client.setScreen(new SurvScreen(SurvScreen.Tab.DASHBOARD)); return; }
        if (containsAny(q, "status", "how am i", "systems")) { notice(statusLine(client)); return; }
        if (q.contains("coordinate") || q.contains("position")) {
            if (client.player != null) notice("XYZ " + client.player.getBlockX() + " / " + client.player.getBlockY() + " / " + client.player.getBlockZ()); return;
        }
        if (q.contains("health")) { if (client.player != null) notice(String.format("Health %.1f / %.1f", client.player.getHealth(), client.player.getMaxHealth())); return; }
        if (q.contains("armor")) { if (client.player != null) notice("Armor " + client.player.getArmor() + " / 20"); return; }
        if (q.contains("dimension")) { if (client.world != null) notice("Dimension " + client.world.getRegistryKey().getValue()); return; }
        if (q.contains("night") || q.contains("day")) { if (client.world != null) notice(dayNight(client)); return; }
        notice("I heard: " + raw);
    }

    private static boolean containsAny(String q, String... terms) { for (String t : terms) if (q.contains(t)) return true; return false; }

    public static void notice(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) client.player.sendMessage(
                Text.literal("SURV // ").formatted(Formatting.AQUA).append(Text.literal(message).formatted(Formatting.WHITE)), true);
    }

    private static String statusLine(MinecraftClient client) {
        if (client.player == null) return "No player";
        return String.format("HP %.1f | Food %d | Armor %d | XP %d | %s",
                client.player.getHealth(), client.player.getHungerManager().getFoodLevel(), client.player.getArmor(),
                client.player.experienceLevel, AUTOMATION.mode());
    }

    private static String dayNight(MinecraftClient client) {
        long t = Math.floorMod(client.world.getTimeOfDay(), 24000L);
        boolean day = t < 13000L;
        long left = day ? 13000L - t : 24000L - t;
        int sec = (int)Math.round(left / 20.0);
        return (day ? "Day" : "Night") + " // " + (sec / 60) + "m " + (sec % 60) + "s remaining";
    }

    private static void renderHud(DrawContext ctx, RenderTickCounter counter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!CONFIG.hudEnabled || client.player == null || client.world == null || client.textRenderer == null) return;
        List<Line> lines = new ArrayList<>();
        var p = client.player;
        if (CONFIG.showHealth) lines.add(new Line(String.format("HP %.1f/%.1f", p.getHealth(), p.getMaxHealth()), p.getHealth() <= CONFIG.retreatHealth ? 0xFFFF5D5D : 0xFF7CFFB2));
        if (CONFIG.showHunger) lines.add(new Line("FOOD " + p.getHungerManager().getFoodLevel() + "/20", 0xFFFFD36A));
        if (CONFIG.showArmor) lines.add(new Line("ARMOR " + p.getArmor() + "/20", 0xFF8BE9FD));
        if (CONFIG.showXp) lines.add(new Line("XP LVL " + p.experienceLevel, 0xFFC7FF77));
        if (CONFIG.showCoords) lines.add(new Line("XYZ " + p.getBlockX() + " " + p.getBlockY() + " " + p.getBlockZ(), 0xFFD7E3EA));
        if (CONFIG.showDimension) lines.add(new Line("DIM " + client.world.getRegistryKey().getValue().getPath(), 0xFF9FAEC0));
        if (CONFIG.showDayNight) lines.add(new Line(dayNight(client), 0xFF7DE3FF));
        if (CONFIG.showDurability) {
            ItemStack held = p.getMainHandStack();
            if (!held.isEmpty() && held.isDamageable()) {
                int left = held.getMaxDamage() - held.getDamage();
                int pct = (int)Math.round(left * 100.0 / Math.max(1, held.getMaxDamage()));
                lines.add(new Line("DURA " + pct + "% // " + held.getName().getString(), pct <= 10 ? 0xFFFF5D5D : 0xFFB7C7D6));
            }
        }
        if (CONFIG.showInventory) {
            int free = 0;
            for (int i = 0; i < p.getInventory().size(); i++) if (p.getInventory().getStack(i).isEmpty()) free++;
            lines.add(new Line("INV " + free + " free", free <= 2 ? 0xFFFF6B6B : 0xFFB7C7D6));
        }
        if (CONFIG.showHostiles) {
            int hostile = client.world.getEntitiesByClass(HostileEntity.class, p.getBoundingBox().expand(16), e -> e.isAlive()).size();
            lines.add(new Line("THREAT " + (hostile == 0 ? "CLEAR" : hostile + " HOSTILE"), hostile == 0 ? 0xFF76F7A8 : 0xFFFF7878));
        }
        if (CONFIG.showAutomation) lines.add(new Line("AUTO " + AUTOMATION.mode() + " // " + AUTOMATION.state(), AUTOMATION.active() ? 0xFF64F2FF : 0xFF66727D));
        if (CONFIG.showVoice) lines.add(new Line("VOICE " + VOICE.status(), VOICE.isRunning() ? 0xFF63FFF2 : 0xFF66727D));

        int count = Math.min(CONFIG.maxHudLines, lines.size());
        int y = 7, lineH = 11, pad = 4, widest = 0;
        for (int i = 0; i < count; i++) widest = Math.max(widest, client.textRenderer.getWidth(lines.get(i).text));
        int boxW = widest + pad * 2, boxH = count * lineH + pad * 2 + 10;
        int left = CONFIG.hudRight ? ctx.getScaledWindowWidth() - boxW - 7 : 7;
        int right = left + boxW;
        ctx.fill(left, y, right, y + boxH, 0xB5091118);
        ctx.fill(left, y, right, y + 2, 0xEE55E8E2);
        ctx.drawText(client.textRenderer, "SURV // " + CONFIG.profile, left + pad, y + 5, 0xFF55E8E2, true);
        int ty = y + 16;
        for (int i = 0; i < count; i++) {
            Line l = lines.get(i);
            ctx.drawText(client.textRenderer, l.text, left + pad, ty, l.color, true);
            ty += lineH;
        }
        if (CONFIG.debugAutomation && AUTOMATION.active()) ctx.drawText(client.textRenderer, AUTOMATION.reason(), left + pad, y + boxH + 3, 0xFF9FAEC0, true);
    }

    private record Line(String text, int color) {}
}
