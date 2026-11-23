package com.github.lunatrius.schematica.client.gui;

import com.github.lunatrius.schematica.SchematicManager;
import com.github.lunatrius.schematica.client.SchematicState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Legacy-style schematic file browser (numpad /). */
public class SchematicLoadScreen extends Screen {
    private static final int ROWS_PER_PAGE = 10;

    private Path directory;
    private List<Path> entries = new ArrayList<>();
    private Path selected;
    private int page;
    private EditBox nameField;
    private String status = "";

    public SchematicLoadScreen() {
        super(Component.translatable("gui.schematica.load.title.legacy"));
        this.directory = SchematicManager.directory();
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int top = 66;

        this.nameField = new EditBox(this.font, centerX - 150, 26, 300, 20,
                Component.translatable("gui.schematica.load.name"));
        this.nameField.setMaxLength(128);
        this.nameField.setValue(this.selected == null ? "" : this.selected.getFileName().toString());
        this.addRenderableWidget(this.nameField);

        refreshEntries();
        int start = this.page * ROWS_PER_PAGE;
        for (int row = 0; row < ROWS_PER_PAGE && start + row < this.entries.size(); row++) {
            Path entry = this.entries.get(start + row);
            String label = Files.isDirectory(entry)
                    ? "[" + entry.getFileName() + "]"
                    : entry.getFileName().toString();
            this.addRenderableWidget(Button.builder(Component.literal(label), button -> {
                if (Files.isDirectory(entry)) {
                    this.directory = this.directory.resolve(entry).normalize();
                    this.page = 0;
                    this.selected = null;
                    this.rebuildWidgets();
                } else {
                    this.selected = entry;
                    this.nameField.setValue(entry.getFileName().toString());
                }
            }).bounds(centerX - 150, top + row * 20, 300, 18).build());
        }

        int bottom = top + ROWS_PER_PAGE * 20 + 8;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.schematica.load.openFolder"),
                button -> clickUrlAction(this.minecraft, this, this.directory.toUri()))
                .bounds(centerX - 150, bottom, 96, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.schematica.load.prev"),
                button -> { this.page = Math.max(0, this.page - 1); this.rebuildWidgets(); })
                .bounds(centerX - 48, bottom, 44, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.schematica.load.next"),
                button -> {
                    if ((this.page + 1) * ROWS_PER_PAGE < this.entries.size()) {
                        this.page++;
                        this.rebuildWidgets();
                    }
                }).bounds(centerX, bottom, 44, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.schematica.load.done"),
                button -> loadSelected()).bounds(centerX + 52, bottom, 98, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.schematica.load.cancel"),
                button -> this.onClose()).bounds(centerX - 50, bottom + 26, 100, 20).build());
    }

    private void refreshEntries() {
        List<Path> found = new ArrayList<>();
        Path parent = this.directory.getParent();
        if (parent != null) {
            found.add(parent);
        }
        try (Stream<Path> stream = Files.list(this.directory)) {
            stream.filter(path -> Files.isDirectory(path) || isSchematic(path))
                    .sorted(Comparator.comparing(path -> !Files.isDirectory(path)))
                    .forEach(found::add);
        } catch (IOException e) {
            this.status = e.getMessage() == null ? e.toString() : e.getMessage();
        }
        this.entries = found;
    }

    private static boolean isSchematic(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".schematic") || name.endsWith(".nbt");
    }

    private void loadSelected() {
        Path path = this.selected;
        if (path == null) {
            String typed = this.nameField.getValue().trim();
            if (typed.isEmpty()) {
                return;
            }
            path = this.directory.resolve(typed);
        }
        if (!Files.isRegularFile(path)) {
            this.status = "Not a file: " + path.getFileName();
            return;
        }
        try {
            String display = this.directory.relativize(path).toString().replace('\\', '/');
            SchematicState state = SchematicState.instance();
            state.loadFrom(path, display);
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                com.github.lunatrius.schematica.api.ISchematic schematic = state.schematic();
                int blocks = schematic instanceof com.github.lunatrius.schematica.world.schematic.Schematic concrete
                        ? concrete.countNonAir() : -1;
                mc.player.sendSystemMessage(Component.literal("Loaded schematic: " + display
                        + " at " + state.position().toShortString()
                        + " (" + schematic.getWidth() + "x" + schematic.getHeight() + "x"
                        + schematic.getLength() + ", " + blocks + " blocks)"
                        + " — player at " + mc.player.blockPosition().toShortString()));
            }
            this.onClose();
        } catch (IOException e) {
            this.status = e.getMessage() == null ? e.toString() : e.getMessage();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
        graphics.text(this.font, Component.translatable("gui.schematica.load.hint.legacy"),
                this.width / 2 - 150, 50, 0xAAAAAA);
        graphics.text(this.font, this.directory.toString(), this.width / 2 - 150, this.height - 46, 0x888888);
        if (!this.status.isEmpty()) {
            graphics.text(this.font, this.status, this.width / 2 - 150, this.height - 32, 0xFF5555);
        }
    }
}
