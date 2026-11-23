package com.github.lunatrius.schematica.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Small GUI for starting the printer from a schematic in the schematics folder. */
public class SchematicLoadScreen extends Screen {
    private EditBox nameField;

    public SchematicLoadScreen() {
        super(Component.translatable("gui.schematica.load.title"));
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;

        this.nameField = new EditBox(this.font, centerX - 110, this.height / 2 - 30, 220, 20,
                Component.translatable("gui.schematica.load.name"));
        this.nameField.setMaxLength(128);
        this.nameField.setResponder(value -> {});
        this.addRenderableWidget(this.nameField);
        this.setInitialFocus(this.nameField);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.schematica.load.print"), button -> print())
                .bounds(centerX - 110, this.height / 2, 105, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> this.onClose())
                .bounds(centerX + 5, this.height / 2, 105, 20)
                .build());
    }

    private void print() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            this.onClose();
            return;
        }
        String name = this.nameField.getValue().trim().replace(' ', '_');
        if (!name.isEmpty()) {
            if (!name.endsWith(".schematic") && !name.endsWith(".nbt")) {
                name += ".schematic";
            }
            var pos = mc.player.blockPosition();
            mc.player.connection.sendCommand(
                    "schematica print " + name + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
        }
        this.onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.text(this.font, Component.translatable("gui.schematica.load.hint"),
                this.width / 2 - 110, this.height / 2 - 48, 0xFFFFFF);
    }
}
