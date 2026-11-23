package com.github.lunatrius.schematica.client;

import com.github.lunatrius.schematica.Config;
import com.github.lunatrius.schematica.SchematicManager;
import com.github.lunatrius.schematica.api.ISchematic;
import com.github.lunatrius.schematica.world.SchematicWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client side state of the loaded schematic. This is the equivalent of the
 * original {@code ClientProxy.schematic} plus the layer/flip/rotate/point
 * state that used to live in the old client handlers.
 */
public final class SchematicState {
    public enum LayerMode {
        ALL("ALL"),
        LAYERS("Layers"),
        ALL_BELOW("All below");

        private final String label;

        LayerMode(String label) {
            this.label = label;
        }

        public String label() {
            return this.label;
        }

        public LayerMode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private static final SchematicState INSTANCE = new SchematicState();

    private ISchematic schematic;
    private String name = "";
    private BlockPos position = BlockPos.ZERO;
    private LayerMode layerMode = LayerMode.ALL;
    private int renderingLayer;
    private boolean hidden;
    private boolean showGuide = true;
    private BlockPos pointA;
    private BlockPos pointB;
    private int flipIndex = -1;
    private int rotateIndex = -1;

    private SchematicState() {}

    public static SchematicState instance() {
        return INSTANCE;
    }

    public boolean isLoaded() {
        return this.schematic != null;
    }

    public ISchematic schematic() {
        return this.schematic;
    }

    public String name() {
        return this.name;
    }

    public BlockPos position() {
        return this.position;
    }

    public void setPosition(BlockPos position) {
        this.position = position;
        com.github.lunatrius.schematica.client.printer.ClientPrinter.refresh();
    }

    public LayerMode layerMode() {
        return this.layerMode;
    }

    public void setLayerMode(LayerMode layerMode) {
        this.layerMode = layerMode;
        com.github.lunatrius.schematica.client.printer.ClientPrinter.refresh();
    }

    public int renderingLayer() {
        return this.renderingLayer;
    }

    public void setRenderingLayer(int layer) {
        if (this.schematic == null) {
            return;
        }
        this.renderingLayer = Math.max(0, Math.min(layer, this.schematic.getHeight() - 1));
        com.github.lunatrius.schematica.client.printer.ClientPrinter.refresh();
    }

    public boolean isHidden() {
        return this.hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public boolean showGuide() {
        return this.showGuide;
    }

    public void toggleGuide() {
        this.showGuide = !this.showGuide;
    }

    public BlockPos pointA() {
        return this.pointA;
    }

    public BlockPos pointB() {
        return this.pointB;
    }

    public void setPointA(BlockPos pos) {
        this.pointA = pos;
    }

    public void setPointB(BlockPos pos) {
        this.pointB = pos;
    }

    public boolean load(String fileName) throws IOException {
        setLoaded(SchematicManager.load(fileName), fileName);
        return true;
    }

    /** Loads a schematic by absolute path, used by the legacy-style file browser. */
    public boolean loadFrom(java.nio.file.Path path, String displayName) throws IOException {
        setLoaded(com.github.lunatrius.schematica.world.schematic.SchematicFormats.read(path), displayName);
        return true;
    }

    private void setLoaded(ISchematic loaded, String displayName) {
        if (Config.DUMP_BLOCK_LIST.get()) {
            this.dumpBlockList(loaded, displayName);
        }
        this.schematic = loaded;
        this.name = displayName;
        this.position = BlockPos.ZERO;
        this.layerMode = LayerMode.ALL;
        this.renderingLayer = 0;
        this.hidden = false;
        this.flipIndex = -1;
        this.rotateIndex = -1;
        this.pointA = BlockPos.ZERO;
        this.pointB = new BlockPos(loaded.getWidth() - 1, loaded.getHeight() - 1, loaded.getLength() - 1);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            this.position = mc.player.blockPosition();
        }
        com.github.lunatrius.schematica.client.printer.ClientPrinter.refresh();
    }

    public void unload() {
        this.schematic = null;
        this.name = "";
        this.pointA = null;
        this.pointB = null;
        com.github.lunatrius.schematica.client.printer.ClientPrinter.refresh();
    }

    public void moveHere(BlockPos pos) {
        this.position = pos;
        com.github.lunatrius.schematica.client.printer.ClientPrinter.refresh();
    }

    public String flipLabel() {
        return this.flipIndex < 0 ? "None" : Direction.values()[this.flipIndex].getName();
    }

    public void cycleFlip(boolean forced) {
        Direction[] values = Direction.values();
        this.flipIndex = this.flipIndex < 0 ? 0 : (this.flipIndex + (forced ? 1 : 2)) % values.length;
        com.github.lunatrius.schematica.client.printer.ClientPrinter.refresh();
    }

    public String rotateLabel() {
        return this.rotateIndex < 0 ? "None" : Direction.values()[this.rotateIndex].getName();
    }

    public void cycleRotate(boolean forced) {
        Direction[] values = Direction.values();
        this.rotateIndex = this.rotateIndex < 0 ? 0 : (this.rotateIndex + (forced ? 1 : 2)) % values.length;
        com.github.lunatrius.schematica.client.printer.ClientPrinter.refresh();
    }

    private BlockPos localFromWorld(BlockPos worldPos) {
        int dx = worldPos.getX() - this.position.getX();
        int dy = worldPos.getY() - this.position.getY();
        int dz = worldPos.getZ() - this.position.getZ();

        if (this.rotateIndex >= 0) {
            Direction.Axis axis = Direction.values()[this.rotateIndex].getAxis();
            for (int quarter = 0; quarter < (forcedQuarter(this.rotateIndex)); quarter++) {
                if (axis == Direction.Axis.Y) {
                    int oldX = dx;
                    dx = -dz;
                    dz = oldX;
                } else if (axis == Direction.Axis.X) {
                    int oldY = dy;
                    dy = -dz;
                    dz = oldY;
                } else {
                    int oldX = dx;
                    dx = -dy;
                    dy = oldX;
                }
            }
        }
        if (this.flipIndex >= 0) {
            Direction.Axis axis = Direction.values()[this.flipIndex].getAxis();
            if (axis == Direction.Axis.X) {
                dx = this.schematic.getWidth() - 1 - dx;
            } else if (axis == Direction.Axis.Y) {
                dy = this.schematic.getHeight() - 1 - dy;
            } else {
                dz = this.schematic.getLength() - 1 - dz;
            }
        }
        return new BlockPos(dx, dy, dz);
    }

    private int forcedQuarter(int index) {
        return 1 + (index / 2);
    }

    /** Block state shown at a world position, taking layers and transforms into account. */
    public BlockState renderStateAt(BlockPos worldPos) {
        if (this.schematic == null || this.hidden) {
            return Blocks.AIR.defaultBlockState();
        }
        BlockPos local = localFromWorld(worldPos);
        if (local.getX() < 0 || local.getY() < 0 || local.getZ() < 0
                || local.getX() >= this.schematic.getWidth()
                || local.getY() >= this.schematic.getHeight()
                || local.getZ() >= this.schematic.getLength()) {
            return Blocks.AIR.defaultBlockState();
        }
        if (this.layerMode == LayerMode.LAYERS && local.getY() != this.renderingLayer) {
            return Blocks.AIR.defaultBlockState();
        }
        if (this.layerMode == LayerMode.ALL_BELOW && local.getY() > this.renderingLayer) {
            return Blocks.AIR.defaultBlockState();
        }
        return this.schematic.getBlockState(local);
    }

    public boolean isExtraAir(BlockState state) {
        if (state == null || state.isAir()) {
            return true;
        }
        String name = state.getBlock().getDescriptionId().replace("block.", "minecraft:");
        for (String entry : Config.EXTRA_AIR_BLOCKS.get()) {
            if (name.equals(entry) || name.endsWith(":" + entry)) {
                return true;
            }
        }
        return false;
    }

    public List<Map.Entry<ItemStack, Integer>> materials() {
        Map<ItemStack, Integer> counts = new LinkedHashMap<>();
        if (this.schematic == null) {
            return List.of();
        }
        for (int x = 0; x < this.schematic.getWidth(); x++) {
            for (int y = 0; y < this.schematic.getHeight(); y++) {
                for (int z = 0; z < this.schematic.getLength(); z++) {
                    BlockState state = this.schematic.getBlockState(new BlockPos(x, y, z));
                    if (isExtraAir(state)) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(x, y, z);
                    ItemStack stack = com.github.lunatrius.schematica.client.printer.ClientPrinter
                            .requiredItem(state, pos, Minecraft.getInstance().player);
                    if (stack.isEmpty()) {
                        continue;
                    }
                    counts.merge(stack, 1, Integer::sum);
                }
            }
        }
        List<Map.Entry<ItemStack, Integer>> result = new ArrayList<>(counts.entrySet());
        // Legacy stores "" for the default and ItemStackSortType.fromString("")
        // resolves to NAME_ASC, so an empty value must still sort by name.
        String sort = Config.SORT_TYPE.get();
        if (sort == null || sort.isEmpty()) {
            sort = "nameAsc";
        }
        Comparator<Map.Entry<ItemStack, Integer>> comparator = null;
        if ("nameAsc".equals(sort) || "nameDesc".equals(sort)) {
            comparator = Comparator.comparing(entry -> entry.getKey().getHoverName().getString());
        } else if ("amountAsc".equals(sort) || "amountDesc".equals(sort)) {
            comparator = Comparator.comparingInt(Map.Entry::getValue);
        }
        if (comparator != null) {
            result.sort(sort.endsWith("Desc") ? comparator.reversed() : comparator);
        }
        return result;
    }

    public void setSortType(String sortType) {
        Config.SORT_TYPE.set(sortType);
    }

    public String sortLabel() {
        return Config.SORT_TYPE.get();
    }

    /** Legacy debug.dumpBlockList: one line per distinct state, with its count. */
    private void dumpBlockList(ISchematic schematic, String displayName) {
        try {
            java.nio.file.Path dir = java.nio.file.Path.of("dumps");
            java.nio.file.Files.createDirectories(dir);
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (int x = 0; x < schematic.getWidth(); x++) {
                for (int y = 0; y < schematic.getHeight(); y++) {
                    for (int z = 0; z < schematic.getLength(); z++) {
                        BlockState state = schematic.getBlockState(new BlockPos(x, y, z));
                        if (isExtraAir(state)) {
                            continue;
                        }
                        counts.merge(String.valueOf(state), 1, Integer::sum);
                    }
                }
            }
            StringBuilder out = new StringBuilder("# ").append(displayName).append('\n');
            counts.forEach((name, count) -> out.append(name).append(',').append(count).append('\n'));
            java.nio.file.Files.writeString(dir.resolve("schematica-blocks.txt"),
                    out.toString(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            com.github.lunatrius.schematica.Schematica.LOGGER.warn(
                    "Could not dump block list for {}", displayName, e);
        }
    }

    public boolean captureSelection(Level level, String fileName) throws IOException {
        if (this.pointA == null || this.pointB == null) {
            return false;
        }
        SchematicManager.save(fileName, SchematicWorld.capture(level,
                this.pointA.offset(this.position), this.pointB.offset(this.position)));
        return true;
    }
}
