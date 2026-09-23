package com.secret.autoenchanter;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.block.Blocks;
import net.minecraft.block.EnchantingTableBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

public final class AutoEnchanterClient implements ClientModInitializer {
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
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                if (pendingRun != null) tickPending(client);
            } catch (Throwable t) {
                message(client, "Automation stopped because of an internal error: " + t.getClass().getSimpleName());
                pendingRun = null;
            }
        });
    }

    public static void openBuilder(MinecraftClient client) {
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

        BlockPos tablePos = findNearestEnchantingTable(client, 6);
        if (tablePos == null) {
            message(client, "Stand within 6 blocks of the enchanting table, then press START AUTO again.");
            return;
        }

        int shelves = countUsableBookshelves(client, tablePos);
        EngineBridge.lockBookshelves(shelves);

        StringBuilder command = new StringBuilder("cenchant ").append(itemId);
        for (AutoEnchanterScreen.SelectedEnchant enchant : selected) {
            command.append(" with ").append(enchant.id()).append(" ").append(enchant.level());
        }

        pendingRun = new PendingRun(command.toString(), shelves);
        client.setScreen(null);
        message(client, "Auto sequence started for " + itemId + ".");
        message(client, "Locked search to your current table: " + shelves + " usable bookshelves.");
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
        } else if (pendingRun.ticks > 10 && !pendingRun.searchStarted && EngineBridge.isPlayerSeedCracked()) {
            EngineBridge.lockBookshelves(pendingRun.bookshelves);
            pendingRun.searchStarted = true;
            runClientCommand(client, pendingRun.cenchantCommand);
            message(client, "RNG cracked. Searching using exactly " + pendingRun.bookshelves + " bookshelves...");
            pendingRun = null;
        } else if (pendingRun.ticks > 1200) {
            message(client, "RNG crack timed out. Press F10 and START AUTO to retry.");
            pendingRun = null;
        }
    }

    private static void runClientCommand(MinecraftClient client, String command) {
        try {
            var dispatcher = ClientCommandManager.getActiveDispatcher();
            if (dispatcher == null || client.getNetworkHandler() == null) {
                message(client, "ClientCommands is not ready yet.");
                return;
            }
            FabricClientCommandSource source = (FabricClientCommandSource)(Object) client.getNetworkHandler().getCommandSource();
            dispatcher.execute(command, source);
        } catch (Exception e) {
            message(client, "Client command failed: /" + command + " (" + e.getClass().getSimpleName() + ")");
        }
    }

    public static String status() {
        return pendingRun == null ? "READY" : "RUNNING";
    }

    private static BlockPos findNearestEnchantingTable(MinecraftClient client, int radius) {
        if (client.world == null || client.player == null) return null;

        BlockPos center = client.player.getBlockPos();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = center.add(x, y, z);
                    if (!client.world.getBlockState(pos).isOf(Blocks.ENCHANTING_TABLE)) continue;

                    double distance = client.player.squaredDistanceTo(
                            pos.getX() + 0.5,
                            pos.getY() + 0.5,
                            pos.getZ() + 0.5
                    );
                    if (distance < bestDistance) {
                        best = pos.toImmutable();
                        bestDistance = distance;
                    }
                }
            }
        }

        return best;
    }

    private static int countUsableBookshelves(MinecraftClient client, BlockPos tablePos) {
        if (client.world == null) return 0;

        int power = 0;
        for (BlockPos offset : EnchantingTableBlock.POWER_PROVIDER_OFFSETS) {
            if (EnchantingTableBlock.canAccessPowerProvider(client.world, tablePos, offset)) {
                power++;
            }
        }
        return Math.min(15, power);
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
        final int bookshelves;
        int ticks;
        boolean searchStarted;

        PendingRun(String cenchantCommand, int bookshelves) {
            this.cenchantCommand = cenchantCommand;
            this.bookshelves = bookshelves;
        }
    }
}
