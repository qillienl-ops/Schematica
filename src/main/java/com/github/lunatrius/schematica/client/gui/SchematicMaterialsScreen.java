package com.github.lunatrius.schematica.client.gui;

import com.github.lunatrius.schematica.client.SchematicState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Legacy-style materials list with sorting and a text dump. */
public class SchematicMaterialsScreen extends Screen {
    private static final String[] SORT_TYPES = {"nameAsc", "nameDesc", "amountAsc", "amountDesc"};
    private static final String[] SORT_LABELS = {"name ^", "name v", "amount ^", "amount v"};
    private static final int ROWS_PER_PAGE = 12;

    private List<Map.Entry<ItemStack, Integer>> materials = List.of();
    private int sortIndex;
    private int page;
    private String status = "";

    public SchematicMaterialsScreen() {
        super(Component.translatable("gui.schematica.materials.title"));
        String configured = SchematicState.instance().sortLabel();
        for (int i = 0; i < SORT_TYPES.length; i++) {
            if (SORT_TYPES[i].equals(configured)) {
                this.sortIndex = i;
            }
        }
    }

    @Override
    protected void init() {
        super.init();
        materials();
        int left = this.width / 2 - 150;
        int bottom = this.height - 34;
        this.addRenderableWidget(Button.builder(Component.literal("Sort: " + SORT_LABELS[this.sortIndex]),
                button -> {
                    this.sortIndex = (this.sortIndex + 1) % SORT_TYPES.length;
                    SchematicState.instance().setSortType(SORT_TYPES[this.sortIndex]);
                    materials();
                    button.setMessage(Component.literal("Sort: " + SORT_LABELS[this.sortIndex]));
                }).bounds(left, bottom, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.schematica.materials.saveToFile"),
                button -> dump()).bounds(left + 104, bottom, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.schematica.load.cancel"),
                button -> this.onClose()).bounds(left + 208, bottom, 92, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("<"),
                button -> { this.page = Math.max(0, this.page - 1); })
                .bounds(left + 300, bottom, 22, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(">"),
                button -> { if ((this.page + 1) * ROWS_PER_PAGE < this.materials.size()) this.page++; })
                .bounds(left + 324, bottom, 22, 20).build());
    }

    private void materials() {
        this.materials = SchematicState.instance().materials();
    }

    private void dump() {
        try {
            Path dir = Path.of("dumps");
            Files.createDirectories(dir);
            StringBuilder out = new StringBuilder("Material,Amount\n");
            for (Map.Entry<ItemStack, Integer> entry : this.materials) {
                out.append(entry.getKey().getHoverName().getString().replace(',', ' '))
                        .append(',').append(entry.getValue()).append('\n');
            }
            Files.writeString(dir.resolve("schematica-materials.txt"),
                    out.toString(), StandardCharsets.UTF_8);
            this.status = "Saved dumps/schematica-materials.txt";
        } catch (IOException e) {
            this.status = e.getMessage() == null ? e.toString() : e.getMessage();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.title, this.width / 2, 14, 0xFFFFFF);
        graphics.text(this.font, Component.translatable("gui.schematica.materials.material"),
                this.width / 2 - 150, 38, 0xFFFFFF);
        graphics.text(this.font, Component.translatable("gui.schematica.materials.amount"),
                this.width / 2 + 60, 38, 0xFFFFFF);

        int start = this.page * ROWS_PER_PAGE;
        for (int row = 0; row < ROWS_PER_PAGE && start + row < this.materials.size(); row++) {
            Map.Entry<ItemStack, Integer> entry = this.materials.get(start + row);
            int y = 56 + row * 18;
            graphics.item(entry.getKey(), this.width / 2 - 150, y - 2);
            graphics.text(this.font, entry.getKey().getHoverName(),
                    this.width / 2 - 126, y, 0xFFFFFF);
            graphics.text(this.font, String.valueOf(entry.getValue()),
                    this.width / 2 + 60, y, 0xFFFFFF);
            int available = countInInventory(entry.getKey());
            graphics.text(this.font, Component.translatable(available >= entry.getValue()
                            ? "gui.schematica.materials.available" : "gui.schematica.materials.missing"),
                    this.width / 2 + 110, y, available >= entry.getValue() ? 0x55FF55 : 0xFF5555);
        }
        if (!this.status.isEmpty()) {
            graphics.text(this.font, this.status, this.width / 2 - 150, this.height - 56, 0x55FF55);
        }
    }

    private int countInInventory(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return 0;
        }
        int count = 0;
        var inventory = mc.player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack held = inventory.getItem(i);
            if (!held.isEmpty() && ItemStack.isSameItemSameComponents(held, stack)) {
                count += held.getCount();
            }
        }
        return count;
    }
}
