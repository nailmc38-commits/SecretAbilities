package com.secret.abilities;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class AbilityScreen extends Screen {
    public AbilityScreen() {
        super(Text.literal("Secret Abilities"));
    }

    private static Text statusText(String name, boolean enabled) {
        return Text.literal(name + ": " + (enabled ? "ON" : "OFF"));
    }

    @Override
    protected void init() {
        int buttonWidth = 210;
        int buttonHeight = 24;
        int x = (this.width - buttonWidth) / 2;
        int y = this.height / 2 - 35;

        this.addDrawableChild(ButtonWidget.builder(
                statusText("Walk on Water", ModState.walkOnWater),
                button -> {
                    ModState.walkOnWater = !ModState.walkOnWater;
                    button.setMessage(statusText("Walk on Water", ModState.walkOnWater));
                }
        ).dimensions(x, y, buttonWidth, buttonHeight).build());

        this.addDrawableChild(ButtonWidget.builder(
                statusText("X-Ray", ModState.xray),
                button -> {
                    ModState.xray = !ModState.xray;
                    button.setMessage(statusText("X-Ray", ModState.xray));
                    SecretAbilitiesClient.refreshXray();
                }
        ).dimensions(x, y + 32, buttonWidth, buttonHeight).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Close"),
                button -> this.close()
        ).dimensions(x, y + 72, buttonWidth, buttonHeight).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 72, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Press F9 anytime to open this menu"), this.width / 2, this.height / 2 - 57, 0xAAAAAA);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
