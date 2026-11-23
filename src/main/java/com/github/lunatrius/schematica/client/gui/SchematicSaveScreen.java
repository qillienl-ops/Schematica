package com.github.lunatrius.schematica.client.gui;

import com.github.lunatrius.schematica.client.SchematicState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Legacy-style save GUI with red/blue points, a selection guide and format choice. */
public class SchematicSaveScreen extends Screen {
    private static final String[] FORMATS = {"Alpha", "Structure"};

    private final EditBox[] fields = new EditBox[6];
    private EditBox nameField;
    private int format;
    private String status = "";

    public SchematicSaveScreen() {
        super(Component.translatable("gui.schematica.save.title"));
    }

    @Override
    protected void init() {
        super.init();
        int left = this.width / 2 - 150;
        int top = 58;
        SchematicState state = SchematicState.instance();
        BlockPos fallback = state.position();
        BlockPos a = state.pointA() == null ? fallback : state.pointA();
        BlockPos b = state.pointB() == null ? fallback.offset(7, 5, 7) : state.pointB();
        int[] values = {a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ()};

        String[] labels = {"Red X", "Red Y", "Red Z", "Blue X", "Blue Y", "Blue Z"};
        for (int i = 0; i < 6; i++) {
            int column = i % 3;
            int row = i / 3;
            this.addRenderableWidget(Button.builder(Component.literal(labels[i]), button -> {
                if (Minecraft.getInstance().player != null) {
                    BlockPos pos = Minecraft.getInstance().player.blockPosition();
                    this.fields[row * 3].setValue(String.valueOf(pos.getX()));
                    this.fields[row * 3 + 1].setValue(String.valueOf(pos.getY()));
                    this.fields[row * 3 + 2].setValue(String.valueOf(pos.getZ()));
                }
            }).bounds(left + column * 100, top + row * 46, 96, 18).build());

            this.fields[i] = new EditBox(this.font, left + column * 100, top + row * 46 + 20,
                    96, 20, Component.literal(labels[i]));
            this.fields[i].setValue(String.valueOf(values[i]));
            this.addRenderableWidget(this.fields[i]);
        }

        int bottom = top + 100;
        this.nameField = new EditBox(this.font, left, bottom, 196, 20,
                Component.translatable("gui.schematica.save.name"));
        this.nameField.setMaxLength(128);
        this.nameField.setValue("schematic-" + System.currentTimeMillis() / 1000);
        this.addRenderableWidget(this.nameField);

        this.addRenderableWidget(Button.builder(Component.literal("Save"), button -> save())
                .bounds(left + 200, bottom, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Guide: "
                        + (state.showGuide() ? "ON" : "OFF")), button -> {
                    state.toggleGuide();
                    button.setMessage(Component.literal("Guide: "
                            + (state.showGuide() ? "ON" : "OFF")));
                }).bounds(left, bottom + 26, 96, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Format: " + FORMATS[this.format]),
                button -> {
                    this.format = (this.format + 1) % FORMATS.length;
                    button.setMessage(Component.literal("Format: " + FORMATS[this.format]));
                }).bounds(left + 102, bottom + 26, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.schematica.load.cancel"),
                button -> this.onClose()).bounds(left + 208, bottom + 26, 92, 20).build());
    }

    private void save() {
        try {
            int[] coords = new int[6];
            for (int i = 0; i < 6; i++) {
                coords[i] = Integer.parseInt(this.fields[i].getValue().trim());
            }
            String name = this.nameField.getValue().trim().replace(' ', '_');
            if (name.isEmpty()) {
                this.status = "Enter a file name";
                return;
            }
            String command = "schematica save " + name + " "
                    + coords[0] + " " + coords[1] + " " + coords[2] + " "
                    + coords[3] + " " + coords[4] + " " + coords[5]
                    + " " + FORMATS[this.format].toLowerCase();
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.connection.sendCommand(command);
            }
            this.onClose();
        } catch (NumberFormatException e) {
            this.status = "Coordinates must be whole numbers";
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.title, this.width / 2, 14, 0xFFFFFF);
        graphics.text(this.font, Component.translatable("gui.schematica.save.hint"),
                this.width / 2 - 150, 40, 0xAAAAAA);
        if (!this.status.isEmpty()) {
            graphics.text(this.font, this.status, this.width / 2 - 150, this.height - 30, 0xFF5555);
        }
    }
}
