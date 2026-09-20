package com.survos;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class LegacyBridge {
    private Method quickTick;
    private Method quickQueue;
    private Object tradeController;
    private Method tradeTick;
    private Method tradeToggle;
    private Object tradeConfig;
    private Class<?> tradeScreenClass;
    private Method enchantOpenBuilder;
    private boolean quickAvailable;
    private boolean tradeAvailable;
    private boolean enchantAvailable;

    public void initialize() {
        initQuickCraft();
        initTradeCycler();
        initAutoEnchanter();
    }

    private void initQuickCraft() {
        try {
            Class<?> c = Class.forName("dev.quickcraft.QuickCraftClient");
            quickTick = c.getDeclaredMethod("tick", MinecraftClient.class); quickTick.setAccessible(true);
            quickQueue = c.getDeclaredMethod("queueCraft", MinecraftClient.class, FabricClientCommandSource.class, String.class);
            quickQueue.setAccessible(true);
            quickAvailable = true;
        } catch (Throwable ignored) {}
    }

    private void initTradeCycler() {
        try {
            Class<?> cfg = Class.forName("com.secret.tradecycler.TradeCyclerConfig");
            Method load = cfg.getDeclaredMethod("load"); load.setAccessible(true);
            tradeConfig = load.invoke(null);
            Class<?> client = Class.forName("com.secret.tradecycler.TradeCyclerClient");
            Field controllerField = client.getDeclaredField("CONTROLLER"); controllerField.setAccessible(true);
            tradeController = controllerField.get(null);
            Field configField = client.getDeclaredField("config"); configField.setAccessible(true);
            configField.set(null, tradeConfig);
            tradeTick = tradeController.getClass().getDeclaredMethod("tick", MinecraftClient.class); tradeTick.setAccessible(true);
            tradeToggle = tradeController.getClass().getDeclaredMethod("toggle", MinecraftClient.class); tradeToggle.setAccessible(true);
            tradeScreenClass = Class.forName("com.secret.tradecycler.TradeCyclerScreen");
            tradeAvailable = true;
        } catch (Throwable ignored) {}
    }

    private void initAutoEnchanter() {
        try {
            Class<?> c = Class.forName("com.secret.autoenchanter.AutoEnchanterClient");
            Object instance = c.getDeclaredConstructor().newInstance();
            c.getMethod("onInitializeClient").invoke(instance);
            enchantOpenBuilder = c.getDeclaredMethod("openBuilder", MinecraftClient.class);
            enchantOpenBuilder.setAccessible(true);
            enchantAvailable = true;
        } catch (Throwable ignored) {}
    }

    public void tick(MinecraftClient client) {
        try { if (quickTick != null) quickTick.invoke(null, client); } catch (Throwable ignored) {}
        try { if (tradeTick != null && tradeController != null) tradeTick.invoke(tradeController, client); } catch (Throwable ignored) {}
    }

    public int queueCraft(MinecraftClient client, FabricClientCommandSource source, String item) {
        if (!quickAvailable || quickQueue == null) {
            source.sendError(net.minecraft.text.Text.literal("SURV: crafting engine unavailable."));
            return 0;
        }
        try {
            Object out = quickQueue.invoke(null, client, source, item);
            return out instanceof Integer i ? i : 1;
        } catch (Throwable t) {
            source.sendError(net.minecraft.text.Text.literal("SURV: crafting engine error."));
            return 0;
        }
    }

    public void toggleVillager(MinecraftClient client) {
        try {
            if (tradeToggle != null && tradeController != null) tradeToggle.invoke(tradeController, client);
            else SurvOsClient.notice("Villager engine unavailable");
        } catch (Throwable t) { SurvOsClient.notice("Villager engine error"); }
    }

    public void openVillagerSettings(MinecraftClient client, Screen parent) {
        if (!tradeAvailable || tradeScreenClass == null || tradeConfig == null) {
            SurvOsClient.notice("Villager settings unavailable"); return;
        }
        try {
            Constructor<?> ctor = tradeScreenClass.getDeclaredConstructors()[0]; ctor.setAccessible(true);
            client.setScreen((Screen)ctor.newInstance(parent, tradeConfig));
        } catch (Throwable t) { SurvOsClient.notice("Could not open villager settings"); }
    }

    public void openEnchantBuilder(MinecraftClient client) {
        try {
            if (enchantOpenBuilder != null) enchantOpenBuilder.invoke(null, client);
            else SurvOsClient.notice("Enchant engine unavailable");
        } catch (Throwable t) { SurvOsClient.notice("Enchant engine error"); }
    }

    public boolean quickAvailable() { return quickAvailable; }
    public boolean tradeAvailable() { return tradeAvailable; }
    public boolean enchantAvailable() { return enchantAvailable; }
}
