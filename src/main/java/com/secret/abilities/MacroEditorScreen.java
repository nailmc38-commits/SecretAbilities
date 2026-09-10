package com.secret.abilities;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public class MacroEditorScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget nameField;
    private TextFieldWidget keyField;
    private TextFieldWidget scriptField;
    private int selected = -1;

    public MacroEditorScreen(Screen parent) {
        super(Text.literal("Void Client Macro Editor"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        this.clearChildren();

        int panelWidth = Math.min(760, this.width - 24);
        int left = (this.width - panelWidth) / 2;
        int top = Math.max(18, (this.height - 390) / 2);

        nameField = new TextFieldWidget(this.textRenderer, left + 238, top + 82, panelWidth - 262, 22, Text.literal("Macro name"));
        nameField.setMaxLength(64);
        keyField = new TextFieldWidget(this.textRenderer, left + 238, top + 125, 120, 22, Text.literal("Key"));
        keyField.setMaxLength(12);
        scriptField = new TextFieldWidget(this.textRenderer, left + 238, top + 168, panelWidth - 262, 22, Text.literal("Macro script"));
        scriptField.setMaxLength(2048);

        this.addDrawableChild(nameField);
        this.addDrawableChild(keyField);
        this.addDrawableChild(scriptField);

        List<MacroManager.Macro> macros = MacroManager.all();
        int shown = Math.min(8, macros.size());
        for (int i = 0; i < shown; i++) {
            int index = i;
            MacroManager.Macro macro = macros.get(i);
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal((selected == i ? "> " : "") + macro.name),
                    button -> {
                        selected = index;
                        rebuild();
                    }
            ).dimensions(left + 20, top + 62 + i * 31, 190, 24).build());
        }

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("NEW"),
                button -> {
                    selected = -1;
                    nameField.setText("");
                    keyField.setText("NONE");
                    scriptField.setText("");
                }
        ).dimensions(left + 238, top + 211, 86, 24).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("SAVE NEW").formatted(Formatting.AQUA),
                button -> {
                    MacroManager.add(nameField.getText(), keyField.getText(), scriptField.getText());
                    selected = MacroManager.all().size() - 1;
                    rebuild();
                }
        ).dimensions(left + 330, top + 211, 105, 24).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("UPDATE"),
                button -> {
                    if (selected >= 0) {
                        MacroManager.update(selected, nameField.getText(), keyField.getText(), scriptField.getText());
                        rebuild();
                    }
                }
        ).dimensions(left + 441, top + 211, 90, 24).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("RUN"),
                button -> {
                    if (selected >= 0 && this.client != null) {
                        MacroManager.start(this.client, MacroManager.all().get(selected));
                    }
                }
        ).dimensions(left + 537, top + 211, 70, 24).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("DELETE").formatted(Formatting.RED),
                button -> {
                    if (selected >= 0) {
                        MacroManager.delete(selected);
                        selected = -1;
                        rebuild();
                    }
                }
        ).dimensions(left + 613, top + 211, 80, 24).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("BACK"),
                button -> {
                    if (this.client != null) this.client.setScreen(parent);
                }
        ).dimensions(left + panelWidth - 110, top + 346, 90, 24).build());

        if (selected >= 0 && selected < MacroManager.all().size()) {
            MacroManager.Macro macro = MacroManager.all().get(selected);
            nameField.setText(macro.name);
            keyField.setText(macro.key);
            scriptField.setText(macro.script);
        } else {
            keyField.setText("NONE");
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        int panelWidth = Math.min(760, this.width - 24);
        int panelHeight = 390;
        int left = (this.width - panelWidth) / 2;
        int top = Math.max(18, (this.height - panelHeight) / 2);

        context.fill(left + 4, top + 5, left + panelWidth + 4, top + panelHeight + 5, 0x70000000);
        context.fill(left, top, left + panelWidth, top + panelHeight, 0xF011151C);
        context.fill(left, top, left + panelWidth, top + 3, 0xFF35E8FF);
        context.fill(left, top, left + panelWidth, top + 42, 0xFF171D26);

        context.drawTextWithShadow(this.textRenderer,
                Text.literal("VOID CLIENT / MACRO EDITOR").formatted(Formatting.AQUA),
                left + 20, top + 16, 0xFFFFFF);

        context.drawTextWithShadow(this.textRenderer, Text.literal("Saved Macros"), left + 20, top + 48, 0xFF9BA7B7);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Name"), left + 238, top + 69, 0xFF9BA7B7);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Keybind (ex: G, F8, SPACE)"), left + 238, top + 112, 0xFF9BA7B7);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Actions (separate steps with ;)"), left + 238, top + 155, 0xFF9BA7B7);

        context.drawTextWithShadow(this.textRenderer,
                Text.literal("cmd home ; wait 250 ; slot 2 ; mouse 30 -10 ; click right"),
                left + 238, top + 250, 0xFF93A0B0);
        context.drawTextWithShadow(this.textRenderer,
                Text.literal("forward 20 ; sneak 10 ; attack 2 ; use 20 ; jump"),
                left + 238, top + 265, 0xFF93A0B0);
        context.drawTextWithShadow(this.textRenderer,
                Text.literal("toggle X-Ray ; chat hello ; repeat 3 ; stop"),
                left + 238, top + 280, 0xFF93A0B0);

        context.drawTextWithShadow(this.textRenderer,
                Text.literal("Running: " + MacroManager.runningName()).formatted(Formatting.GRAY),
                left + 238, top + 306, 0xFFFFFF);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
