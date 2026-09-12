package com.secret.minigames.screens;

import com.secret.minigames.games.MiniMinecraftGame;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

public final class MiniCraftingScreen extends Screen {
    private final Screen parent;
    private final MiniMinecraftGame game;
    private String status = "Choose a recipe.";

    public MiniCraftingScreen(Screen parent, MiniMinecraftGame game) {
        super(Text.literal("Mini Minecraft Crafting"));
        this.parent = parent;
        this.game = game;
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        this.clearChildren();
        int w = Math.min(270, Math.max(205, this.width / 3));
        int x = this.width / 2 - w - 6;
        int x2 = this.width / 2 + 6;
        int y = 63;
        int h = 22;
        int gap = 5;

        recipe(x, y, w, h, "4 Planks  ←  1 Wood", game::craftPlanks, "Crafted 4 planks.", "Need 1 wood.");
        recipe(x2, y, w, h, "4 Sticks  ←  2 Planks", game::craftSticks, "Crafted 4 sticks.", "Need 2 planks.");
        recipe(x, y + (h + gap), w, h, "Crafting Table  ←  4 Planks", game::craftTable, "Crafted a crafting table.", "Need 4 planks.");
        recipe(x2, y + (h + gap), w, h, "Furnace  ←  8 Cobble", game::craftFurnace, "Crafted a furnace.", "Need 8 cobblestone.");
        recipe(x, y + (h + gap) * 2, w, h, "4 Torches  ←  Coal + Stick", game::craftTorches, "Crafted 4 torches.", "Need 1 coal + 1 stick.");
        recipe(x2, y + (h + gap) * 2, w, h, "Stone Pickaxe", game::craftStonePickaxe, "Crafted stone pickaxe.", "Need 3 cobble + 2 sticks.");
        recipe(x, y + (h + gap) * 3, w, h, "Stone Sword", game::craftStoneSword, "Crafted stone sword.", "Need 2 cobble + 1 stick.");
        recipe(x2, y + (h + gap) * 3, w, h, "Stone Axe", game::craftStoneAxe, "Crafted stone axe.", "Need 3 cobble + 2 sticks.");
        recipe(x, y + (h + gap) * 4, w, h, "Smelt Iron  ←  Ore + Coal", game::smeltIron, "Smelted 1 iron ingot.", "Need furnace + iron ore + coal.");
        recipe(x2, y + (h + gap) * 4, w, h, "Cook Meat  ←  Meat + Coal", game::cookMeat, "Cooked 1 meat.", "Need furnace + raw meat + coal.");
        recipe(x, y + (h + gap) * 5, w, h, "Iron Pickaxe", game::craftIronPickaxe, "Crafted iron pickaxe.", "Need 3 iron ingots + 2 sticks.");
        recipe(x2, y + (h + gap) * 5, w, h, "Iron Sword", game::craftIronSword, "Crafted iron sword.", "Need 2 iron ingots + 1 stick.");

        this.addDrawableChild(ButtonWidget.builder(Text.literal("BACK TO SURVIVAL").formatted(Formatting.GREEN), b -> {
            if (this.client != null) this.client.setScreen(parent);
        }).dimensions(this.width / 2 - 85, y + (h + gap) * 6 + 8, 170, 23).build());
    }

    private void recipe(int x, int y, int w, int h, String label, CraftAction action, String yes, String no) {
        this.addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> {
            status = action.craft() ? yes : no;
            rebuild();
        }).dimensions(x, y, w, h).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xF00D1218);
        context.fill(0, 0, this.width, 4, 0xFF69E0A1);
        context.fill(0, 4, this.width, 48, 0xFF17211C);

        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("MINI MINECRAFT • INVENTORY & CRAFTING").formatted(Formatting.GREEN, Formatting.BOLD),
                this.width / 2, 13, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("E / ESC returns to survival").formatted(Formatting.GRAY),
                this.width / 2, 29, 0xFFFFFF);

        int bottom = Math.min(this.height - 33, 245);
        context.fill(18, bottom, this.width - 18, bottom + 24, 0xAA000000);
        String inv = "Wood " + game.inventory(MiniMinecraftGame.WOOD)
                + "  Planks " + game.inventory(MiniMinecraftGame.PLANKS)
                + "  Cobble " + game.inventory(MiniMinecraftGame.COBBLE)
                + "  Sticks " + game.sticks
                + "  Coal " + game.coal
                + "  Iron Ore " + game.ironOre
                + "  Ingots " + game.ironIngots
                + "  Raw Meat " + game.rawMeat
                + "  Cooked " + game.cookedMeat;
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(inv), this.width / 2, bottom + 5, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal(game.toolSummary() + "   •   " + status).formatted(Formatting.GRAY),
                this.width / 2, bottom + 15, 0xFFFFFF);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE || input.key() == GLFW.GLFW_KEY_E || input.key() == GLFW.GLFW_KEY_F9) {
            if (this.client != null) this.client.setScreen(parent);
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private interface CraftAction {
        boolean craft();
    }
}
