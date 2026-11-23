package com.github.lunatrius.schematica.client.gui;

import com.github.lunatrius.schematica.client.SchematicState;
import com.github.lunatrius.schematica.client.printer.ClientPrinter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/** Legacy-style control GUI: position, layers, hide, flip/rotate, materials and printer. */
public class SchematicControlScreen extends Screen {
    private final EditBox[] position = new EditBox[3];
    private EditBox layerField;
    private String status = "";

    public SchematicControlScreen() {
        super(Component.translatable("gui.schematica.control.title"));
    }

    @Override
    protected void init() {
        super.init();
        int left = this.width / 2 - 150;
        int top = 56;
        SchematicState state = SchematicState.instance();
        BlockPos pos = state.position();
        String[] labels = {"X", "Y", "Z"};
        int[] values = {pos.getX(), pos.getY(), pos.getZ()};
        for (int i = 0; i < 3; i++) {
            this.addRenderableWidget(Button.builder(Component.literal(labels[i]), button -> applyPosition())
                    .bounds(left + i * 100, top, 96, 18).build());
            this.position[i] = new EditBox(this.font, left + i * 100, top + 20, 96, 20,
                    Component.literal(labels[i]));
            this.position[i].setValue(String.valueOf(values[i]));
            this.addRenderableWidget(this.position[i]);
        }

        int row = top + 52;
        this.addRenderableWidget(button("gui.schematica.control.unload", left, row, 96,
                () -> { state.unload(); this.onClose(); }));
        this.addRenderableWidget(layerButton(left + 102, row, state));
        this.layerField = new EditBox(this.font, left + 204, row, 96, 20,
                Component.translatable("gui.schematica.control.layer"));
        this.layerField.setValue(String.valueOf(state.renderingLayer()));
        this.addRenderableWidget(this.layerField);

        row += 26;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.schematica.control.hide"), button -> {
            state.setHidden(!state.isHidden());
            button.setMessage(Component.translatable(state.isHidden()
                    ? "gui.schematica.control.show" : "gui.schematica.control.hide"));
        }).bounds(left, row, 96, 20).build());
        this.addRenderableWidget(button("gui.schematica.control.moveHere", left + 102, row, 96,
                () -> {
                    applyPosition();
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        state.moveHere(mc.player.blockPosition());
                        this.position[0].setValue(String.valueOf(state.position().getX()));
                        this.position[1].setValue(String.valueOf(state.position().getY()));
                        this.position[2].setValue(String.valueOf(state.position().getZ()));
                    }
                }));
        this.addRenderableWidget(Button.builder(Component.literal("Flip: " + state.flipLabel()), button -> {
            state.cycleFlip(hasShiftDown());
            button.setMessage(Component.literal("Flip: " + state.flipLabel()));
        }).bounds(left + 204, row, 96, 20).build());

        row += 26;
        this.addRenderableWidget(Button.builder(Component.literal("Rotate: " + state.rotateLabel()), button -> {
            state.cycleRotate(hasShiftDown());
            button.setMessage(Component.literal("Rotate: " + state.rotateLabel()));
        }).bounds(left, row, 96, 20).build());
        this.addRenderableWidget(button("gui.schematica.control.materials", left + 102, row, 96,
                () -> Minecraft.getInstance().gui.setScreen(new SchematicMaterialsScreen())));
        this.addRenderableWidget(Button.builder(Component.literal("Printer: "
                        + (ClientPrinter.isActive() ? "ON" : "OFF")), button -> {
                    ClientPrinter.toggle();
                    button.setMessage(Component.literal("Printer: "
                            + (ClientPrinter.isActive() ? "ON" : "OFF")));
                }).bounds(left + 204, row, 96, 20).build());

        this.addRenderableWidget(button("gui.schematica.load.cancel", left, row + 28, 96,
                this::onClose));
    }

    private Button layerButton(int x, int y, SchematicState state) {
        return Button.builder(Component.literal("Layer: " + state.layerMode().label()), button -> {
            state.setLayerMode(state.layerMode().next());
            button.setMessage(Component.literal("Layer: " + state.layerMode().label()));
        }).bounds(x, y, 96, 20).build();
    }

    private Button button(String key, int x, int y, int width, Runnable action) {
        return Button.builder(Component.translatable(key), button -> action.run())
                .bounds(x, y, width, 20).build();
    }

    private void applyPosition() {
        try {
            int x = Integer.parseInt(this.position[0].getValue().trim());
            int y = Integer.parseInt(this.position[1].getValue().trim());
            int z = Integer.parseInt(this.position[2].getValue().trim());
            SchematicState.instance().setPosition(new BlockPos(x, y, z));
            try {
                SchematicState.instance().setRenderingLayer(Integer.parseInt(this.layerField.getValue().trim()));
            } catch (NumberFormatException ignored) {
                // keep the current layer
            }
        } catch (NumberFormatException e) {
            this.status = "Position must be whole numbers";
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.title, this.width / 2, 14, 0xFFFFFF);
        if (!this.status.isEmpty()) {
            graphics.text(this.font, this.status, this.width / 2 - 150, this.height - 30, 0xFF5555);
        }
    }

    private static boolean hasShiftDown() {
        return net.neoforged.neoforge.client.settings.KeyModifier.SHIFT.isActive(
                net.neoforged.neoforge.client.settings.KeyConflictContext.IN_GAME);
    }
}
