package com.survos;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class LegacyBridge {
    private Class<?> quickClass;
    private Method quickTick;
    private Method quickQueue;
    private Method quickFindRecipe;
    private Field quickPendingItem;
    private Field quickPendingId;
    private Field quickActiveContainerId;
    private Field quickScreenTicks;
    private Field quickWaitTicks;
    private Field quickResultSeenTicks;
    private Field quickCraftClickAttempts;
    private Field quickStage;
    private Class<?> quickStageClass;

    private String craftQueueItem = "";
    private int craftRemaining;
    private boolean craftQueueActive;
    private boolean craftWaitingForFinish;
    private int craftBeforeCount;

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
            quickClass = Class.forName("dev.quickcraft.QuickCraftClient");
            quickTick = quickClass.getDeclaredMethod("tick", MinecraftClient.class);
            quickTick.setAccessible(true);

            quickQueue = quickClass.getDeclaredMethod(
                    "queueCraft",
                    MinecraftClient.class,
                    FabricClientCommandSource.class,
                    String.class
            );
            quickQueue.setAccessible(true);

            quickFindRecipe = quickClass.getDeclaredMethod(
                    "findCraftableRecipe",
                    MinecraftClient.class,
                    Item.class
            );
            quickFindRecipe.setAccessible(true);

            quickPendingItem = field(quickClass, "pendingItem");
            quickPendingId = field(quickClass, "pendingId");
            quickActiveContainerId = field(quickClass, "activeContainerId");
            quickScreenTicks = field(quickClass, "screenTicks");
            quickWaitTicks = field(quickClass, "waitTicks");
            quickResultSeenTicks = field(quickClass, "resultSeenTicks");
            quickCraftClickAttempts = field(quickClass, "craftClickAttempts");
            quickStage = field(quickClass, "stage");
            quickStageClass = Class.forName("dev.quickcraft.QuickCraftClient$Stage");

            quickAvailable = true;
        } catch (Throwable ignored) {
            quickAvailable = false;
        }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private void initTradeCycler() {
        try {
            Class<?> cfg = Class.forName("com.secret.tradecycler.TradeCyclerConfig");
            Method load = cfg.getDeclaredMethod("load");
            load.setAccessible(true);
            tradeConfig = load.invoke(null);

            Class<?> client = Class.forName("com.secret.tradecycler.TradeCyclerClient");
            Field controllerField = client.getDeclaredField("CONTROLLER");
            controllerField.setAccessible(true);
            tradeController = controllerField.get(null);

            Field configField = client.getDeclaredField("config");
            configField.setAccessible(true);
            configField.set(null, tradeConfig);

            tradeTick = tradeController.getClass().getDeclaredMethod("tick", MinecraftClient.class);
            tradeTick.setAccessible(true);

            tradeToggle = tradeController.getClass().getDeclaredMethod("toggle", MinecraftClient.class);
            tradeToggle.setAccessible(true);

            tradeScreenClass = Class.forName("com.secret.tradecycler.TradeCyclerScreen");
            tradeAvailable = true;
        } catch (Throwable ignored) {
            tradeAvailable = false;
        }
    }

    private void initAutoEnchanter() {
        try {
            Class<?> c = Class.forName("com.secret.autoenchanter.AutoEnchanterClient");
            Object instance = c.getDeclaredConstructor().newInstance();
            c.getMethod("onInitializeClient").invoke(instance);

            enchantOpenBuilder = c.getDeclaredMethod("openBuilder", MinecraftClient.class);
            enchantOpenBuilder.setAccessible(true);

            enchantAvailable = true;
        } catch (Throwable ignored) {
            enchantAvailable = false;
        }
    }

    public void tick(MinecraftClient client) {
        try {
            if (quickTick != null) quickTick.invoke(null, client);
        } catch (Throwable ignored) {}

        driveCraftQueue(client);

        try {
            if (tradeTick != null && tradeController != null)
                tradeTick.invoke(tradeController, client);
        } catch (Throwable ignored) {}
    }

    public int queueCraft(
            MinecraftClient client,
            FabricClientCommandSource source,
            String item
    ) {
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

    public boolean queueCraftDirect(MinecraftClient client, String item, int count, boolean max) {
        if (!quickAvailable || client == null || item == null || item.isBlank()) return false;

        craftQueueItem = normalizeItem(item);
        craftRemaining = max ? -1 : Math.max(1, count);
        craftQueueActive = true;
        craftWaitingForFinish = false;
        craftBeforeCount = countItem(client, craftQueueItem);

        SurvOsClient.notice(
                max
                        ? "Craft max queued: " + craftQueueItem
                        : "Craft x" + craftRemaining + " queued: " + craftQueueItem
        );

        return true;
    }

    public void cancelCraftQueue() {
        craftQueueActive = false;
        craftWaitingForFinish = false;
        craftQueueItem = "";
        craftRemaining = 0;
    }

    public String craftQueueStatus() {
        if (!craftQueueActive) return "IDLE";
        return craftRemaining < 0
                ? "MAX " + craftQueueItem
                : craftRemaining + "x " + craftQueueItem;
    }

    private void driveCraftQueue(MinecraftClient client) {
        if (!craftQueueActive || !quickAvailable || client == null || client.player == null) return;

        try {
            Object pending = quickPendingItem.get(null);
            if (pending != null) return;

            if (craftWaitingForFinish) {
                int after = countItem(client, craftQueueItem);
                if (after <= craftBeforeCount) {
                    craftQueueActive = false;
                    craftWaitingForFinish = false;
                    SurvOsClient.notice("Craft queue stopped: recipe or materials unavailable.");
                    return;
                }

                craftWaitingForFinish = false;
                craftBeforeCount = after;

                if (craftRemaining > 0) {
                    craftRemaining--;
                    if (craftRemaining == 0) {
                        craftQueueActive = false;
                        SurvOsClient.notice("Craft queue complete.");
                        return;
                    }
                }
            }

            if (!startDirectCraft(client, craftQueueItem)) {
                craftQueueActive = false;
                SurvOsClient.notice("Craft queue stopped: invalid item.");
                return;
            }

            craftBeforeCount = countItem(client, craftQueueItem);
            craftWaitingForFinish = true;
        } catch (Throwable t) {
            craftQueueActive = false;
            craftWaitingForFinish = false;
            SurvOsClient.notice("Craft queue error.");
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean startDirectCraft(MinecraftClient client, String raw) throws Exception {
        Identifier id = Identifier.tryParse(normalizeItem(raw));
        if (id == null || !Registries.ITEM.containsId(id)) return false;

        Item item = Registries.ITEM.get(id);
        quickPendingItem.set(null, item);
        quickPendingId.set(null, id);
        quickActiveContainerId.setInt(null, -1);
        quickScreenTicks.setInt(null, 0);
        quickWaitTicks.setInt(null, 0);
        quickResultSeenTicks.setInt(null, 0);
        quickCraftClickAttempts.setInt(null, 0);

        Object waiting = Enum.valueOf((Class<? extends Enum>) quickStageClass.asSubclass(Enum.class), "WAITING_FOR_TABLE");
        quickStage.set(null, waiting);
        return true;
    }

    public boolean canCraftNow(MinecraftClient client, String raw) {
        if (!quickAvailable || client == null) return false;
        try {
            Identifier id = Identifier.tryParse(normalizeItem(raw));
            if (id == null || !Registries.ITEM.containsId(id)) return false;
            Item item = Registries.ITEM.get(id);
            return quickFindRecipe.invoke(null, client, item) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String normalizeItem(String raw) {
        String s = raw.trim().toLowerCase().replace(' ', '_');
        return s.contains(":") ? s : "minecraft:" + s;
    }

    private static int countItem(MinecraftClient client, String raw) {
        if (client.player == null) return 0;
        String wanted = normalizeItem(raw);
        int total = 0;
        for (int i = 0; i < client.player.getInventory().size(); i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (!stack.isEmpty() && Registries.ITEM.getId(stack.getItem()).toString().equals(wanted)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public void toggleVillager(MinecraftClient client) {
        try {
            if (tradeToggle != null && tradeController != null) {
                tradeToggle.invoke(tradeController, client);
            } else {
                SurvOsClient.notice("Villager engine unavailable");
            }
        } catch (Throwable t) {
            SurvOsClient.notice("Villager engine error");
        }
    }

    public void openVillagerSettings(MinecraftClient client, Screen parent) {
        if (!tradeAvailable || tradeScreenClass == null || tradeConfig == null) {
            SurvOsClient.notice("Villager settings unavailable");
            return;
        }

        try {
            Constructor<?> ctor = tradeScreenClass.getDeclaredConstructors()[0];
            ctor.setAccessible(true);
            client.setScreen((Screen) ctor.newInstance(parent, tradeConfig));
        } catch (Throwable t) {
            SurvOsClient.notice("Could not open villager settings");
        }
    }

    public void openEnchantBuilder(MinecraftClient client) {
        try {
            if (enchantOpenBuilder != null) {
                enchantOpenBuilder.invoke(null, client);
            } else {
                SurvOsClient.notice("Enchant engine unavailable");
            }
        } catch (Throwable t) {
            SurvOsClient.notice("Enchant engine error");
        }
    }

    public boolean quickAvailable() { return quickAvailable; }
    public boolean tradeAvailable() { return tradeAvailable; }
    public boolean enchantAvailable() { return enchantAvailable; }
}
