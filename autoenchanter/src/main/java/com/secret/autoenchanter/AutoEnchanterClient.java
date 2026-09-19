package com.secret.autoenchanter;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AutoEnchanterClient implements ClientModInitializer {
    private static KeyBinding openKey;
    private static PendingRun pendingRun;

    public record EnchantChoice(String id, String label, int maxLevel) {}

    private static final List<EnchantChoice> BOOK = List.of(
            e("protection", "Protection", 4),
            e("fire_protection", "Fire Protection", 4),
            e("blast_protection", "Blast Protection", 4),
            e("projectile_protection", "Projectile Protection", 4),
            e("feather_falling", "Feather Falling", 4),
            e("respiration", "Respiration", 3),
            e("aqua_affinity", "Aqua Affinity", 1),
            e("thorns", "Thorns", 3),
            e("depth_strider", "Depth Strider", 3),
            e("sharpness", "Sharpness", 5),
            e("smite", "Smite", 5),
            e("bane_of_arthropods", "Bane of Arthropods", 5),
            e("knockback", "Knockback", 2),
            e("fire_aspect", "Fire Aspect", 2),
            e("looting", "Looting", 3),
            e("sweeping_edge", "Sweeping Edge", 3),
            e("efficiency", "Efficiency", 5),
            e("silk_touch", "Silk Touch", 1),
            e("unbreaking", "Unbreaking", 3),
            e("fortune", "Fortune", 3),
            e("power", "Power", 5),
            e("punch", "Punch", 2),
            e("flame", "Flame", 1),
            e("infinity", "Infinity", 1),
            e("luck_of_the_sea", "Luck of the Sea", 3),
            e("lure", "Lure", 3),
            e("impaling", "Impaling", 5),
            e("loyalty", "Loyalty", 3),
            e("riptide", "Riptide", 3),
            e("channeling", "Channeling", 1),
            e("multishot", "Multishot", 1),
            e("quick_charge", "Quick Charge", 3),
            e("piercing", "Piercing", 4),
            e("density", "Density", 5),
            e("breach", "Breach", 4),
            e("wind_burst", "Wind Burst", 3)
    );

    private static EnchantChoice e(String id, String label, int max) {
        return new EnchantChoice("minecraft:" + id, label, max);
    }

    @Override
    public void onInitializeClient() {
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autoenchanter.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F10,
                KeyBinding.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                while (openKey.wasPressed()) {
                    openBuilder(client);
                }
                if (pendingRun != null) {
                    tickPending(client);
                }
            } catch (Throwable ignored) {
                // Never let an AutoEnchanter tick exception take down the whole client.
                pendingRun = null;
            }
        });
    }

    private static void openBuilder(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            message(client, "Join a world first.");
            return;
        }

        ItemStack held = client.player.getMainHandStack();
        if (held.isEmpty()) {
            message(client, "Hold the item or book you want to enchant, then press F10.");
            return;
        }

        String itemId = Registries.ITEM.getId(held.getItem()).toString();
        List<EnchantChoice> choices = choicesFor(itemId);
        if (choices.isEmpty()) {
            message(client, "That held item is not supported by AutoEnchanter yet: " + itemId);
            return;
        }

        client.setScreen(new AutoEnchanterScreen(client.currentScreen, itemId, choices));
    }

    static void startAuto(MinecraftClient client, String itemId, List<AutoEnchanterScreen.SelectedEnchant> selected) {
        if (selected.isEmpty()) {
            message(client, "Select at least one enchantment.");
            return;
        }

        StringBuilder command = new StringBuilder("cenchant ").append(itemId);
        for (AutoEnchanterScreen.SelectedEnchant enchant : selected) {
            command.append(" with ").append(enchant.id()).append(" ").append(enchant.level());
        }

        pendingRun = new PendingRun(command.toString());
        client.setScreen(null);
        message(client, "Auto sequence started for " + itemId + ".");
        message(client, "Keep a stack of disposable items in your inventory for RNG throws.");
    }

    private static void tickPending(MinecraftClient client) {
        if (pendingRun == null) return;
        if (client.player == null || client.getNetworkHandler() == null) {
            pendingRun = null;
            return;
        }

        pendingRun.ticks++;

        if (pendingRun.ticks == 2) {
            runClientCommand(client, "cconfig clientcommands enchantingPrediction set true");
            message(client, "Enchant prediction enabled.");
        } else if (pendingRun.ticks == 10) {
            runClientCommand(client, "ccrackrng");
            message(client, "Cracking player RNG automatically...");
        } else if (pendingRun.ticks == 610) {
            runClientCommand(client, pendingRun.cenchantCommand);
            message(client, "Searching for your selected enchant combination with the bundled 1.21.11 engine...");
            pendingRun = null;
        }
    }

    private static void runClientCommand(MinecraftClient client, String command) {
        if (client.getNetworkHandler() != null) {
            client.getNetworkHandler().sendChatCommand(command);
        }
    }

    static List<EnchantChoice> choicesFor(String itemId) {
        String id = itemId.toLowerCase(Locale.ROOT);
        if (id.equals("minecraft:book")) return BOOK;

        if (id.endsWith("_sword")) return pick("sharpness","smite","bane_of_arthropods","knockback","fire_aspect","looting","sweeping_edge","unbreaking");
        if (id.endsWith("_axe")) return pick("sharpness","smite","bane_of_arthropods","efficiency","silk_touch","unbreaking","fortune");
        if (id.endsWith("_pickaxe") || id.endsWith("_shovel") || id.endsWith("_hoe"))
            return pick("efficiency","silk_touch","unbreaking","fortune");

        if (id.endsWith("_helmet"))
            return pick("protection","fire_protection","blast_protection","projectile_protection","respiration","aqua_affinity","thorns","unbreaking");
        if (id.endsWith("_chestplate"))
            return pick("protection","fire_protection","blast_protection","projectile_protection","thorns","unbreaking");
        if (id.endsWith("_leggings"))
            return pick("protection","fire_protection","blast_protection","projectile_protection","thorns","unbreaking");
        if (id.endsWith("_boots"))
            return pick("protection","fire_protection","blast_protection","projectile_protection","feather_falling","depth_strider","thorns","unbreaking");

        if (id.equals("minecraft:bow")) return pick("power","punch","flame","infinity","unbreaking");
        if (id.equals("minecraft:crossbow")) return pick("multishot","quick_charge","piercing","unbreaking");
        if (id.equals("minecraft:trident")) return pick("impaling","loyalty","riptide","channeling","unbreaking");
        if (id.equals("minecraft:fishing_rod")) return pick("luck_of_the_sea","lure","unbreaking");
        if (id.equals("minecraft:mace")) return pick("density","breach","wind_burst","smite","bane_of_arthropods","unbreaking");

        return List.of();
    }

    private static List<EnchantChoice> pick(String... ids) {
        List<EnchantChoice> out = new ArrayList<>();
        for (String wanted : ids) {
            for (EnchantChoice choice : BOOK) {
                if (choice.id().equals("minecraft:" + wanted)) {
                    out.add(choice);
                    break;
                }
            }
        }
        return out;
    }

    static boolean conflicts(String a, String b) {
        String x = shortId(a);
        String y = shortId(b);
        if (x.equals(y)) return false;

        if (inSameGroup(x, y, "protection","fire_protection","blast_protection","projectile_protection")) return true;
        if (inSameGroup(x, y, "sharpness","smite","bane_of_arthropods")) return true;
        if (inSameGroup(x, y, "silk_touch","fortune")) return true;
        if (inSameGroup(x, y, "multishot","piercing")) return true;
        if (inSameGroup(x, y, "depth_strider","frost_walker")) return true;

        if ((x.equals("riptide") && (y.equals("loyalty") || y.equals("channeling")))
                || (y.equals("riptide") && (x.equals("loyalty") || x.equals("channeling")))) return true;

        return false;
    }

    private static boolean inSameGroup(String x, String y, String... group) {
        boolean hasX = false;
        boolean hasY = false;
        for (String value : group) {
            if (value.equals(x)) hasX = true;
            if (value.equals(y)) hasY = true;
        }
        return hasX && hasY;
    }

    private static String shortId(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    static void message(MinecraftClient client, String text) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal("[AutoEnchanter] " + text), false);
        }
    }

    private static final class PendingRun {
        final String cenchantCommand;
        int ticks;

        PendingRun(String cenchantCommand) {
            this.cenchantCommand = cenchantCommand;
        }
    }
}
