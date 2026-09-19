package com.secret.tradecycler;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.VillagerProfession;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public final class TradeCyclerClient implements ClientModInitializer {
    private static final Controller CONTROLLER = new Controller();
    private static TradeCyclerConfig config;
    private static KeyBinding toggleKey;
    private static KeyBinding settingsKey;

    @Override
    public void onInitializeClient() {
        config = TradeCyclerConfig.load();

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tradecycler.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F8,
                KeyBinding.Category.MISC
        ));
        settingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tradecycler.settings",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F9,
                KeyBinding.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (settingsKey.wasPressed()) {
                client.setScreen(new TradeCyclerScreen(client.currentScreen, config));
            }
            while (toggleKey.wasPressed()) {
                CONTROLLER.toggle(client);
            }
            CONTROLLER.tick(client);
        });
    }

    private enum State {
        IDLE,
        WAIT_FOR_LIBRARIAN,
        OPEN_TRADE,
        WAIT_FOR_TRADE_SCREEN,
        BREAK_LECTERN,
        WAIT_FOR_BREAK,
        WAIT_FOR_UNEMPLOYED,
        WAIT_FOR_LECTERN_PICKUP,
        PLACE_LECTERN,
        WAIT_FOR_REASSIGN
    }

    private static final class Controller {
        private State state = State.IDLE;
        private int villagerId = -1;
        private BlockPos lecternPos;
        private int attempts;
        private int stateTicks;
        private int cooldown;
        private int originalSlot;

        void toggle(MinecraftClient client) {
            if (state != State.IDLE) {
                stop(client, "Stopped.", false);
                return;
            }
            start(client);
        }

        private void start(MinecraftClient client) {
            if (client.player == null || client.world == null || client.interactionManager == null) {
                message(client, "Join a world first.");
                return;
            }

            if (!(client.targetedEntity instanceof VillagerEntity villager)) {
                message(client, "Look directly at the villager, then press F8.");
                return;
            }

            BlockPos foundLectern = findNearestLectern(client, villager.getBlockPos(), 4);
            if (foundLectern == null) {
                message(client, "No lectern found within 4 blocks of that villager.");
                return;
            }

            if (client.player.squaredDistanceTo(foundLectern.getX() + 0.5, foundLectern.getY() + 0.5, foundLectern.getZ() + 0.5) > 36.0) {
                message(client, "Stand within 6 blocks of the lectern.");
                return;
            }

            if (!hasLecternInHotbar(client) && !hasEmptyHotbarSlot(client)) {
                message(client, "Free one hotbar slot first so the broken lectern can be picked up.");
                return;
            }

            RegistryEntry<?> profession = villager.getVillagerData().profession();
            if (!profession.matchesKey(VillagerProfession.LIBRARIAN) && !profession.matchesKey(VillagerProfession.NONE)) {
                message(client, "Target must be an unemployed villager or an unlocked librarian.");
                return;
            }

            villagerId = villager.getId();
            lecternPos = foundLectern.toImmutable();
            attempts = 0;
            stateTicks = 0;
            cooldown = 0;
            originalSlot = client.player.getInventory().getSelectedSlot();
            state = profession.matchesKey(VillagerProfession.LIBRARIAN) ? State.OPEN_TRADE : State.WAIT_FOR_LIBRARIAN;
            message(client, "Started: " + config.enchantment + " level " + config.minimumLevel + "+, max " + config.maxEmeraldPrice + " emeralds.");
        }

        void tick(MinecraftClient client) {
            if (state == State.IDLE) return;
            if (client.player == null || client.world == null || client.interactionManager == null) {
                stop(client, "Stopped because you left the world.", false);
                return;
            }

            VillagerEntity villager = getVillager(client);
            if (villager == null || !villager.isAlive()) {
                stop(client, "Villager is no longer available.", false);
                return;
            }

            if (client.player.squaredDistanceTo(villager) > 49.0
                    || client.player.squaredDistanceTo(lecternPos.getX() + 0.5, lecternPos.getY() + 0.5, lecternPos.getZ() + 0.5) > 49.0) {
                stop(client, "You moved too far away.", false);
                return;
            }

            if (cooldown > 0) {
                cooldown--;
                return;
            }

            stateTicks++;
            switch (state) {
                case WAIT_FOR_LIBRARIAN -> waitForInitialLibrarian(client, villager);
                case OPEN_TRADE -> openTrade(client, villager);
                case WAIT_FOR_TRADE_SCREEN -> waitForTradeScreen(client);
                case BREAK_LECTERN -> beginBreaking(client);
                case WAIT_FOR_BREAK -> continueBreaking(client);
                case WAIT_FOR_UNEMPLOYED -> waitForUnemployed(client, villager);
                case WAIT_FOR_LECTERN_PICKUP -> waitForLecternPickup(client);
                case PLACE_LECTERN -> placeLectern(client);
                case WAIT_FOR_REASSIGN -> waitForReassign(client, villager);
                default -> { }
            }
        }

        private void waitForInitialLibrarian(MinecraftClient client, VillagerEntity villager) {
            if (villager.getVillagerData().profession().matchesKey(VillagerProfession.LIBRARIAN)) {
                setState(State.OPEN_TRADE, config.delayTicks());
            } else if (stateTicks > 120) {
                stop(client, "Villager did not claim the nearby lectern.", false);
            }
        }

        private void openTrade(MinecraftClient client, VillagerEntity villager) {
            if (!villager.getVillagerData().profession().matchesKey(VillagerProfession.LIBRARIAN)) {
                setState(State.WAIT_FOR_LIBRARIAN, config.delayTicks());
                return;
            }

            client.interactionManager.interactEntity(client.player, villager, Hand.MAIN_HAND);
            client.player.swingHand(Hand.MAIN_HAND);
            setState(State.WAIT_FOR_TRADE_SCREEN, 2);
        }

        private void waitForTradeScreen(MinecraftClient client) {
            if (client.player.currentScreenHandler instanceof MerchantScreenHandler merchant && !merchant.getRecipes().isEmpty()) {
                attempts++;
                MatchResult result = inspectTrades(merchant);
                if (result.found) {
                    stop(client, "FOUND " + result.enchantmentName + " " + result.level + " for " + result.price + " emeralds after " + attempts + " attempts!", true);
                    return;
                }

                String current = result.seenBook == null ? "no enchanted book" : result.seenBook;
                message(client, "Attempt " + attempts + ": " + current + " - rerolling...");
                client.player.closeHandledScreen();
                setState(State.BREAK_LECTERN, config.delayTicks());
            } else if (stateTicks > 80) {
                stop(client, "Could not open/read the villager trades.", false);
            }
        }

        private MatchResult inspectTrades(MerchantScreenHandler merchant) {
            MatchResult result = new MatchResult();
            String target = config.enchantment.toLowerCase(Locale.ROOT);

            for (TradeOffer offer : merchant.getRecipes()) {
                ItemStack sell = offer.getSellItem();
                ItemEnchantmentsComponent enchants = EnchantmentHelper.getEnchantments(sell);
                if (enchants.isEmpty()) continue;

                int price = getEmeraldPrice(offer);
                for (Object2IntMap.Entry<RegistryEntry<Enchantment>> entry : enchants.getEnchantmentEntries()) {
                    String id = entry.getKey().getIdAsString().toLowerCase(Locale.ROOT);
                    int level = entry.getIntValue();
                    String shortId = id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
                    result.seenBook = shortId + " " + level + " @ " + price + "e";
                    if ((id.equals(target) || shortId.equals(target)) && level >= config.minimumLevel && price <= config.maxEmeraldPrice) {
                        result.found = true;
                        result.enchantmentName = shortId;
                        result.level = level;
                        result.price = price;
                        return result;
                    }
                }
            }
            return result;
        }

        private int getEmeraldPrice(TradeOffer offer) {
            ItemStack buy = offer.getDisplayedFirstBuyItem();
            return buy.isOf(Items.EMERALD) ? buy.getCount() : Integer.MAX_VALUE;
        }

        private void beginBreaking(MinecraftClient client) {
            if (!client.world.getBlockState(lecternPos).isOf(Blocks.LECTERN)) {
                stop(client, "Lectern is missing from its saved position.", false);
                return;
            }

            selectBreakTool(client);
            client.interactionManager.attackBlock(lecternPos, Direction.UP);
            client.player.swingHand(Hand.MAIN_HAND);
            setState(State.WAIT_FOR_BREAK, 0);
        }

        private void continueBreaking(MinecraftClient client) {
            if (!client.world.getBlockState(lecternPos).isOf(Blocks.LECTERN)) {
                client.interactionManager.cancelBlockBreaking();
                setState(State.WAIT_FOR_UNEMPLOYED, config.delayTicks());
                return;
            }

            client.interactionManager.updateBlockBreakingProgress(lecternPos, Direction.UP);
            if ((stateTicks & 3) == 0) client.player.swingHand(Hand.MAIN_HAND);
            if (stateTicks > 240) {
                stop(client, "Could not break the lectern. Check server protections/anti-cheat.", false);
            }
        }

        private void waitForUnemployed(MinecraftClient client, VillagerEntity villager) {
            if (villager.getVillagerData().profession().matchesKey(VillagerProfession.NONE)) {
                setState(State.WAIT_FOR_LECTERN_PICKUP, 2);
                return;
            }
            if (stateTicks > 100) {
                stop(client, "Villager stayed a librarian. It may already be trade-locked or linked to another lectern.", false);
            }
        }

        private void waitForLecternPickup(MinecraftClient client) {
            int slot = findLecternHotbarSlot(client);
            if (slot >= 0) {
                client.player.getInventory().setSelectedSlot(slot);
                setState(State.PLACE_LECTERN, config.delayTicks());
                return;
            }
            if (stateTicks > 80) {
                stop(client, "Lectern was not picked up into the hotbar. Stand closer and keep a hotbar slot free.", false);
            }
        }

        private void placeLectern(MinecraftClient client) {
            int slot = findLecternHotbarSlot(client);
            if (slot < 0) {
                setState(State.WAIT_FOR_LECTERN_PICKUP, 0);
                return;
            }
            client.player.getInventory().setSelectedSlot(slot);

            BlockPos support = lecternPos.down();
            Vec3d hitPos = new Vec3d(lecternPos.getX() + 0.5, lecternPos.getY(), lecternPos.getZ() + 0.5);
            BlockHitResult hit = new BlockHitResult(hitPos, Direction.UP, support, false);
            client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hit);
            client.player.swingHand(Hand.MAIN_HAND);
            setState(State.WAIT_FOR_REASSIGN, config.delayTicks());
        }

        private void waitForReassign(MinecraftClient client, VillagerEntity villager) {
            if (client.world.getBlockState(lecternPos).isOf(Blocks.LECTERN)
                    && villager.getVillagerData().profession().matchesKey(VillagerProfession.LIBRARIAN)) {
                setState(State.OPEN_TRADE, config.delayTicks());
                return;
            }

            if (stateTicks > 120) {
                if (!client.world.getBlockState(lecternPos).isOf(Blocks.LECTERN)) {
                    stop(client, "Could not place the lectern back. Make sure the original spot is clear and reachable.", false);
                } else {
                    stop(client, "Villager did not reclaim the lectern.", false);
                }
            }
        }

        private void selectBreakTool(MinecraftClient client) {
            for (int i = 0; i < 9; i++) {
                ItemStack stack = client.player.getInventory().getStack(i);
                if (stack.getItem() instanceof AxeItem) {
                    client.player.getInventory().setSelectedSlot(i);
                    return;
                }
            }
            if (originalSlot >= 0 && originalSlot < 9) client.player.getInventory().setSelectedSlot(originalSlot);
        }

        private void setState(State next, int delay) {
            state = next;
            stateTicks = 0;
            cooldown = Math.max(0, delay);
        }

        private void stop(MinecraftClient client, String reason, boolean success) {
            if (client.interactionManager != null) client.interactionManager.cancelBlockBreaking();
            if (client.player != null && originalSlot >= 0 && originalSlot < 9) {
                client.player.getInventory().setSelectedSlot(originalSlot);
            }
            state = State.IDLE;
            stateTicks = 0;
            cooldown = 0;
            message(client, (success ? "FOUND! " : "") + reason);
        }

        private VillagerEntity getVillager(MinecraftClient client) {
            Entity entity = client.world.getEntityById(villagerId);
            return entity instanceof VillagerEntity villager ? villager : null;
        }

        private BlockPos findNearestLectern(MinecraftClient client, BlockPos center, int radius) {
            BlockPos best = null;
            double bestDistance = Double.MAX_VALUE;
            for (int x = -radius; x <= radius; x++) {
                for (int y = -2; y <= 2; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        BlockPos pos = center.add(x, y, z);
                        if (!client.world.getBlockState(pos).isOf(Blocks.LECTERN)) continue;
                        double distance = center.getSquaredDistance(pos);
                        if (distance < bestDistance) {
                            best = pos;
                            bestDistance = distance;
                        }
                    }
                }
            }
            return best;
        }

        private boolean hasEmptyHotbarSlot(MinecraftClient client) {
            for (int i = 0; i < 9; i++) {
                if (client.player.getInventory().getStack(i).isEmpty()) return true;
            }
            return false;
        }

        private boolean hasLecternInHotbar(MinecraftClient client) {
            return findLecternHotbarSlot(client) >= 0;
        }

        private int findLecternHotbarSlot(MinecraftClient client) {
            for (int i = 0; i < 9; i++) {
                if (client.player.getInventory().getStack(i).isOf(Items.LECTERN)) return i;
            }
            return -1;
        }

        private void message(MinecraftClient client, String text) {
            if (client.player != null) client.player.sendMessage(Text.literal("[TradeCycler] " + text), true);
        }
    }

    private static final class MatchResult {
        boolean found;
        String enchantmentName;
        int level;
        int price;
        String seenBook;
    }
}
