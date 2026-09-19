package dev.quickcraft;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

import java.util.List;
import java.util.Locale;

public final class QuickCraftClient implements ClientModInitializer {
    private static Item pendingItem;
    private static Identifier pendingId;
    private static int activeContainerId = -1;
    private static int screenTicks = 0;
    private static int waitTicks = 0;
    private static Stage stage = Stage.WAITING_FOR_TABLE;

    private enum Stage {
        WAITING_FOR_TABLE,
        WAITING_FOR_RESULT,
        WAITING_FOR_CURSOR
    }

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("craft")
                    .then(ClientCommandManager.argument("item", StringArgumentType.word())
                            .suggests((context, builder) -> {
                                String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
                                for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
                                    String full = id.toString();
                                    String path = id.getPath();
                                    if (full.startsWith(remaining) || path.startsWith(remaining)) {
                                        builder.suggest(full);
                                    }
                                }
                                return builder.buildFuture();
                            })
                            .executes(context -> queueCraft(
                                    context.getSource().getClient(),
                                    context.getSource(),
                                    StringArgumentType.getString(context, "item")
                            ))));
        });

        ClientTickEvents.END_CLIENT_TICK.register(QuickCraftClient::tick);

        HudRenderCallback.EVENT.register(QuickCraftClient::renderTimeHud);
    }

    private static int queueCraft(
            Minecraft client,
            net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source,
            String raw
    ) {
        String normalized = raw.contains(":") ? raw : "minecraft:" + raw;
        Identifier id = Identifier.tryParse(normalized);

        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            source.sendError(Component.literal("Unknown item: " + raw));
            return 0;
        }

        Item item = BuiltInRegistries.ITEM.getValue(id);
        pendingItem = item;
        pendingId = id;
        activeContainerId = -1;
        screenTicks = 0;
        waitTicks = 0;
        stage = Stage.WAITING_FOR_TABLE;

        source.sendFeedback(Component.literal("QuickCraft armed: " + id + " — open a crafting table."));
        return 1;
    }

    private static void tick(Minecraft client) {
        if (pendingItem == null || client.player == null || client.level == null) {
            return;
        }

        if (!(client.screen instanceof CraftingScreen screen)) {
            activeContainerId = -1;
            screenTicks = 0;
            return;
        }

        AbstractContainerMenu menu = screen.getMenu();
        int containerId = menu.containerId;

        if (activeContainerId != containerId) {
            activeContainerId = containerId;
            screenTicks = 0;
            waitTicks = 0;
            stage = Stage.WAITING_FOR_TABLE;
        }

        screenTicks++;

        if (stage == Stage.WAITING_FOR_TABLE && screenTicks >= 1) {
            // Only a real crafting-table CraftingScreen gets here; inventory 2x2 crafting never does.
            if (!menu.getCarried().isEmpty()) {
                fail(client, "QuickCraft cancelled: empty your cursor first.");
                return;
            }

            RecipeDisplayEntry entry = findCraftableRecipe(client, pendingItem);
            if (entry == null) {
                fail(client, "QuickCraft couldn't find a craftable crafting-table recipe for " + pendingId + ".");
                return;
            }

            client.getConnection().send(new ServerboundPlaceRecipePacket(containerId, entry.id(), false));
            stage = Stage.WAITING_FOR_RESULT;
            waitTicks = 0;
            return;
        }

        if (stage == Stage.WAITING_FOR_RESULT) {
            waitTicks++;
            ItemStack result = menu.getSlot(0).getItem();

            if (!result.isEmpty() && result.is(pendingItem)) {
                client.gameMode.handleInventoryMouseClick(containerId, 0, 0, ClickType.PICKUP, client.player);
                stage = Stage.WAITING_FOR_CURSOR;
                waitTicks = 0;
                return;
            }

            if (waitTicks > 20) {
                fail(client, "QuickCraft timed out before the result appeared.");
            }
            return;
        }

        if (stage == Stage.WAITING_FOR_CURSOR) {
            waitTicks++;
            ItemStack carried = menu.getCarried();

            if (!carried.isEmpty()) {
                int destination = findInventoryDestination(menu, client, carried);
                if (destination >= 0) {
                    client.gameMode.handleInventoryMouseClick(containerId, destination, 0, ClickType.PICKUP, client.player);
                    success(client, carried);
                } else {
                    // Leave the crafted item on the cursor rather than dropping it.
                    success(client, carried);
                }
                return;
            }

            if (waitTicks > 20) {
                // If the server already placed the item somehow, don't repeat the command on a later table open.
                clearPending();
            }
        }
    }

    private static RecipeDisplayEntry findCraftableRecipe(Minecraft client, Item wanted) {
        ClientRecipeBook book = client.player.getRecipeBook();
        StackedItemContents contents = new StackedItemContents();

        for (int i = 0; i < client.player.getInventory().getContainerSize(); i++) {
            contents.accountSimpleStack(client.player.getInventory().getItem(i));
        }

        var displayContext = SlotDisplayContext.fromLevel(client.level);

        for (RecipeCollection collection : book.getCollections()) {
            for (RecipeDisplayEntry entry : collection.getRecipes()) {
                if (!(entry.display() instanceof ShapedCraftingRecipeDisplay)
                        && !(entry.display() instanceof ShapelessCraftingRecipeDisplay)) {
                    continue;
                }

                List<ItemStack> outputs = entry.resultItems(displayContext);
                boolean matches = outputs.stream().anyMatch(stack -> !stack.isEmpty() && stack.is(wanted));
                if (!matches) {
                    continue;
                }

                if (entry.canCraft(contents)) {
                    return entry;
                }
            }
        }

        return null;
    }

    private static int findInventoryDestination(AbstractContainerMenu menu, Minecraft client, ItemStack carried) {
        int empty = -1;

        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (slot.container != client.player.getInventory() || !slot.mayPlace(carried)) {
                continue;
            }

            ItemStack current = slot.getItem();
            if (current.isEmpty()) {
                if (empty < 0) {
                    empty = i;
                }
                continue;
            }

            if (ItemStack.isSameItemSameComponents(current, carried)
                    && current.getCount() + carried.getCount() <= slot.getMaxStackSize(carried)) {
                return i;
            }
        }

        return empty;
    }

    private static void success(Minecraft client, ItemStack crafted) {
        if (client.player != null) {
            client.player.displayClientMessage(
                    Component.literal("QuickCraft: crafted " + crafted.getCount() + "x " + pendingId),
                    true
            );
        }
        clearPending();
    }

    private static void fail(Minecraft client, String message) {
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal(message), false);
        }
        clearPending();
    }

    private static void clearPending() {
        pendingItem = null;
        pendingId = null;
        activeContainerId = -1;
        screenTicks = 0;
        waitTicks = 0;
        stage = Stage.WAITING_FOR_TABLE;
    }

    private static void renderTimeHud(GuiGraphics graphics, net.minecraft.client.DeltaTracker tickCounter) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.options.hideGui) {
            return;
        }

        long time = Math.floorMod(client.level.getDayTime(), 24000L);
        boolean day = time < 13000L;

        long ticksRemaining = day ? 13000L - time : 24000L - time;
        long seconds = Math.max(0L, Math.round(ticksRemaining / 20.0D));
        long minutes = seconds / 60L;
        long secs = seconds % 60L;

        String text = day
                ? String.format("Day • %dm %02ds left", minutes, secs)
                : String.format("Night • %dm %02ds until day", minutes, secs);

        int x = 6;
        int y = 6;
        int width = client.font.width(text) + 8;

        graphics.fill(x - 3, y - 3, x + width, y + 10, 0x90000000);
        graphics.drawString(client.font, text, x, y, 0xFFFFFFFF, true);
    }
}
