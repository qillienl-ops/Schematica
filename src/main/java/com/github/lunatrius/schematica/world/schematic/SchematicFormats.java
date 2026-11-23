package com.github.lunatrius.schematica.world.schematic;

import com.github.lunatrius.schematica.Schematica;
import com.github.lunatrius.schematica.api.ISchematic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes the two formats the original Schematica supported:
 * MCEdit's "Alpha" .schematic and vanilla structure .nbt files.
 *
 * The Alpha writer uses a palette of full {@link BlockState} strings, because
 * numeric block IDs no longer exist in modern Minecraft. Legacy 1.12.2
 * SchematicaMapping tables are still understood when reading.
 */
public final class SchematicFormats {
    public static final String FORMAT_ALPHA = "Alpha";
    public static final String FORMAT_STRUCTURE = "Structure";

    public static final String TAG_MATERIALS = "Materials";
    public static final String TAG_WIDTH = "Width";
    public static final String TAG_HEIGHT = "Height";
    public static final String TAG_LENGTH = "Length";
    public static final String TAG_BLOCKS = "Blocks";
    public static final String TAG_DATA = "Data";
    public static final String TAG_PALETTE = "Palette";
    public static final String TAG_MAPPING = "SchematicaMapping";
    public static final String TAG_TILE_ENTITIES = "TileEntities";
    public static final String TAG_ENTITIES = "Entities";
    public static final String TAG_ICON = "Icon";
    public static final String TAG_AUTHOR = "Author";

    private SchematicFormats() {}

    public static ISchematic read(Path file) throws IOException {
        CompoundTag root;
        try (InputStream in = Files.newInputStream(file)) {
            root = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
        } catch (IOException compressedFailure) {
            try (java.io.DataInputStream in = new java.io.DataInputStream(Files.newInputStream(file))) {
                root = NbtIo.read(in, NbtAccounter.unlimitedHeap());
            }
        }

        if (root == null) {
            throw new IOException("Empty NBT file: " + file);
        }
        if (root.contains(TAG_MATERIALS)) {
            return readAlpha(root);
        }
        if (root.contains("palette") || root.contains("blocks")) {
            return readStructure(root);
        }
        throw new IOException("Unrecognised schematic format in " + file);
    }

    public static void write(Path file, ISchematic schematic, String format) throws IOException {
        CompoundTag root = new CompoundTag();
        if (FORMAT_STRUCTURE.equalsIgnoreCase(format)) {
            writeStructure(root, schematic);
        } else {
            writeAlpha(root, schematic);
        }

        try (OutputStream out = Files.newOutputStream(file)) {
            NbtIo.writeCompressed(root, out);
        }
    }

    // ------------------------------------------------------------------
    // Alpha (.schematic)
    // ------------------------------------------------------------------

    private static ISchematic readAlpha(CompoundTag root) {
        int width = root.getShortOr(TAG_WIDTH, (short) 0);
        int height = root.getShortOr(TAG_HEIGHT, (short) 0);
        int length = root.getShortOr(TAG_LENGTH, (short) 0);
        Schematic schematic = new Schematic(width, height, length);

        List<String> palette = new ArrayList<>();
        for (Tag tag : root.getListOrEmpty(TAG_PALETTE)) {
            palette.add(tag.asString().orElse(""));
        }

        // Legacy SchematicaMapping: block name -> numeric ID. Used as a
        // fallback for files written by 1.12.2 that have no full-state palette.
        Map<Integer, String> legacyNames = new LinkedHashMap<>();
        CompoundTag mapping = root.getCompoundOrEmpty(TAG_MAPPING);
        for (String name : mapping.keySet()) {
            mapping.getShort(name).ifPresent(id -> legacyNames.put((int) id, name));
        }

        byte[] blocks = root.getByteArray(TAG_BLOCKS).orElse(new byte[0]);
        byte[] metadata = root.getByteArray(TAG_DATA).orElse(new byte[0]);
        // 1.12.2 Schematica writes "AddBlocks"; MCEdit writes "Add".
        byte[] add = root.getByteArray("AddBlocks").orElseGet(() -> root.getByteArray("Add").orElse(new byte[0]));

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < length; z++) {
                    int index = x + (y * length + z) * width;
                    if (index >= blocks.length) {
                        continue;
                    }
                    int id = blocks[index] & 0xFF;
                    if (add.length > 0) {
                        int nib = index / 2;
                        if (nib < add.length) {
                            int high = (add[nib] >> 4) & 0xF;
                            int low = add[nib] & 0xF;
                            id |= (index % 2 == 0 ? high : low) << 8;
                        }
                    }
                    int meta = index < metadata.length ? metadata[index] & 0xF : 0;

                    BlockState state = null;
                    if (!palette.isEmpty() && id >= 0 && id < palette.size()) {
                        // Files written by this port store the complete state string.
                        state = parseState(palette.get(id));
                    }
                    if (state == null) {
                        String name = legacyNames.get(id);
                        if (name != null) {
                            state = stateFromNameAndMeta(name, meta);
                        }
                    }
                    if (state != null) {
                        schematic.setBlockState(new BlockPos(x, y, z), state);
                    }
                }
            }
        }

        readBlockEntities(root, schematic);
        for (Tag tag : root.getListOrEmpty(TAG_ENTITIES)) {
            if (tag instanceof CompoundTag compound) {
                schematic.getEntities().add(compound.copy());
            }
        }
        schematic.setAuthor(root.getStringOr(TAG_AUTHOR, ""));
        schematic.setIcon(parseIcon(root.getStringOr(TAG_ICON, "")));

        return schematic;
    }

    private static void writeAlpha(CompoundTag root, ISchematic schematic) {
        root.putString(TAG_MATERIALS, FORMAT_ALPHA);
        root.putShort(TAG_WIDTH, (short) schematic.getWidth());
        root.putShort(TAG_HEIGHT, (short) schematic.getHeight());
        root.putShort(TAG_LENGTH, (short) schematic.getLength());

        int size = schematic.getWidth() * schematic.getHeight() * schematic.getLength();
        byte[] blocks = new byte[size];
        byte[] data = new byte[size];
        Map<String, Integer> palette = new LinkedHashMap<>();
        CompoundTag mapping = new CompoundTag();

        for (int x = 0; x < schematic.getWidth(); x++) {
            for (int y = 0; y < schematic.getHeight(); y++) {
                for (int z = 0; z < schematic.getLength(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = schematic.getBlockState(pos);
                    String serialized = serializeState(state);
                    Integer id = palette.get(serialized);
                    if (id == null) {
                        id = palette.size();
                        palette.put(serialized, id);
                        Block block = state.getBlock();
                        mapping.putShort(BuiltInRegistries.BLOCK.getKey(block).toString(), id.shortValue());
                    }
                    blocks[x + (y * schematic.getLength() + z) * schematic.getWidth()] = id.byteValue();
                }
            }
        }

        ListTag paletteTag = new ListTag();
        for (String entry : palette.keySet()) {
            paletteTag.add(StringTag.valueOf(entry));
        }

        root.putByteArray(TAG_BLOCKS, blocks);
        root.putByteArray(TAG_DATA, data);
        root.put(TAG_PALETTE, paletteTag);
        root.put(TAG_MAPPING, mapping);
        root.put(TAG_TILE_ENTITIES, writeBlockEntities(schematic));
        ListTag entities = new ListTag();
        for (CompoundTag entity : schematic.getEntities()) {
            entities.add(entity.copy());
        }
        root.put(TAG_ENTITIES, entities);
        root.putString(TAG_AUTHOR, schematic.getAuthor());
        root.putString(TAG_ICON, serializeIcon(schematic.getIcon()));
    }

    // ------------------------------------------------------------------
    // Vanilla structure (.nbt)
    // ------------------------------------------------------------------

    private static ISchematic readStructure(CompoundTag root) {
        int[] size = root.getIntArray("size").orElse(new int[0]);
        if (size.length != 3) {
            throw new IllegalArgumentException("Structure is missing its size");
        }
        Schematic schematic = new Schematic(size[0], size[1], size[2]);

        List<BlockState> palette = new ArrayList<>();
        for (Tag tag : root.getListOrEmpty("palette")) {
            CompoundTag entry = (CompoundTag) tag;
            BlockState state = stateFromName(entry.getStringOr("Name", ""));
            if (state == null) {
                state = Blocks.AIR.defaultBlockState();
            }
            CompoundTag props = entry.getCompoundOrEmpty("Properties");
            for (String key : props.keySet()) {
                Property<?> property = state.getBlock().getStateDefinition().getProperty(key);
                if (property != null) {
                    state = setProperty(state, property, props.getStringOr(key, ""));
                }
            }
            palette.add(state);
        }

        for (Tag tag : root.getListOrEmpty("blocks")) {
            CompoundTag entry = (CompoundTag) tag;
            int[] pos = entry.getIntArray("pos").orElse(new int[0]);
            int stateIndex = entry.getIntOr("state", -1);
            if (pos.length != 3 || stateIndex < 0 || stateIndex >= palette.size()) {
                continue;
            }
            BlockPos blockPos = new BlockPos(pos[0], pos[1], pos[2]);
            schematic.setBlockState(blockPos, palette.get(stateIndex));
            if (entry.contains("nbt")) {
                CompoundTag blockEntity = entry.getCompoundOrEmpty("nbt").copy();
                blockEntity.putInt("x", pos[0]);
                blockEntity.putInt("y", pos[1]);
                blockEntity.putInt("z", pos[2]);
                schematic.setBlockEntity(blockPos, blockEntity);
            }
        }

        schematic.setAuthor(root.getStringOr("Author", ""));
        for (Tag tag : root.getListOrEmpty("entities")) {
            if (tag instanceof CompoundTag compound) {
                schematic.getEntities().add(compound.copy());
            }
        }
        return schematic;
    }

    private static void writeStructure(CompoundTag root, ISchematic schematic) {
        root.putIntArray("size", new int[]{schematic.getWidth(), schematic.getHeight(), schematic.getLength()});
        root.putString("Author", schematic.getAuthor());

        Map<String, Integer> palette = new LinkedHashMap<>();
        ListTag paletteTag = new ListTag();
        ListTag blocksTag = new ListTag();

        for (int x = 0; x < schematic.getWidth(); x++) {
            for (int y = 0; y < schematic.getHeight(); y++) {
                for (int z = 0; z < schematic.getLength(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = schematic.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }

                    String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                    Integer stateIndex = palette.get(name);
                    if (stateIndex == null) {
                        stateIndex = palette.size();
                        palette.put(name, stateIndex);
                        paletteTag.add(serializeStateTag(state));
                    }

                    CompoundTag block = new CompoundTag();
                    block.putIntArray("pos", new int[]{x, y, z});
                    block.putInt("state", stateIndex);
                    CompoundTag blockEntity = schematic.getBlockEntity(pos);
                    if (blockEntity != null) {
                        CompoundTag nbt = blockEntity.copy();
                        nbt.remove("x");
                        nbt.remove("y");
                        nbt.remove("z");
                        block.put("nbt", nbt);
                    }
                    blocksTag.add(block);
                }
            }
        }

        root.put("palette", paletteTag);
        root.put("blocks", blocksTag);

        ListTag entities = new ListTag();
        for (CompoundTag entity : schematic.getEntities()) {
            entities.add(entity.copy());
        }
        root.put("entities", entities);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static void readBlockEntities(CompoundTag root, Schematic schematic) {
        for (Tag tag : root.getListOrEmpty(TAG_TILE_ENTITIES)) {
            CompoundTag blockEntity = ((CompoundTag) tag).copy();
            BlockPos pos = new BlockPos(
                    blockEntity.getIntOr("x", 0), blockEntity.getIntOr("y", 0), blockEntity.getIntOr("z", 0));
            schematic.setBlockEntity(pos, blockEntity);
        }
    }

    private static ListTag writeBlockEntities(ISchematic schematic) {
        ListTag list = new ListTag();
        if (!(schematic instanceof Schematic concrete)) {
            return list;
        }
        for (Map.Entry<BlockPos, CompoundTag> entry : concrete.blockEntities()) {
            CompoundTag tag = entry.getValue().copy();
            tag.putInt("x", entry.getKey().getX());
            tag.putInt("y", entry.getKey().getY());
            tag.putInt("z", entry.getKey().getZ());
            list.add(tag);
        }
        return list;
    }

    private static final String[] DYE_COLORS = {
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
    };
    private static final String[] LOG_TYPES = {"oak", "spruce", "birch", "jungle"};
    private static final String[] PLANK_TYPES = {
            "oak", "spruce", "birch", "jungle", "acacia", "dark_oak"
    };
    private static final String[] STONE_VARIANTS = {
            "minecraft:stone", "minecraft:granite", "minecraft:smooth_granite",
            "minecraft:diorite", "minecraft:smooth_diorite", "minecraft:andesite",
            "minecraft:smooth_andesite"
    };

    /**
     * 1.12.2 stored one numeric block ID plus 4 bits of metadata per block.
     * The mapping table only gives us the block name; this restores the most
     * common metadata families (dye colors, wood, stone) to modern states.
     */
    private static BlockState stateFromNameAndMeta(String name, int meta) {
        String base = name.startsWith("minecraft:") ? name.substring("minecraft:".length()) : name;
        int color = meta & 0xF;
        String resolved = null;

        switch (base) {
            case "wool" -> resolved = "minecraft:" + DYE_COLORS[color] + "_wool";
            case "stained_glass" -> resolved = "minecraft:" + DYE_COLORS[color] + "_stained_glass";
            case "stained_glass_pane" -> resolved = "minecraft:" + DYE_COLORS[color] + "_stained_glass_pane";
            case "stained_hardened_clay" -> resolved = "minecraft:" + DYE_COLORS[color] + "_terracotta";
            case "hardened_clay" -> resolved = "minecraft:terracotta";
            case "carpet" -> resolved = "minecraft:" + DYE_COLORS[color] + "_carpet";
            case "concrete" -> resolved = "minecraft:" + DYE_COLORS[color] + "_concrete";
            case "concrete_powder" -> resolved = "minecraft:" + DYE_COLORS[color] + "_concrete_powder";
            case "log" -> resolved = "minecraft:" + LOG_TYPES[color & 3] + "_log";
            case "log2" -> resolved = "minecraft:" + (color < 2 ? "acacia" : "dark_oak") + "_log";
            case "planks" -> resolved = "minecraft:" + PLANK_TYPES[color % PLANK_TYPES.length] + "_planks";
            case "stone" -> resolved = STONE_VARIANTS[color % STONE_VARIANTS.length];
            case "quartz_block" -> resolved = switch (color) {
                case 1 -> "minecraft:chiseled_quartz_block";
                case 2 -> "minecraft:quartz_pillar";
                default -> "minecraft:quartz_block";
            };
            default -> { }
        }

        BlockState state = stateFromName(resolved != null ? resolved : name);
        if (state != null && (base.equals("log") || base.equals("log2"))) {
            Property<?> axis = state.getBlock().getStateDefinition().getProperty("axis");
            if (axis != null) {
                int axisMeta = (meta >> 2) & 3;
                state = setProperty(state, axis, axisMeta == 1 ? "x" : axisMeta == 2 ? "z" : "y");
            }
        }
        return state;
    }

    private static BlockState stateFromName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        try {
            Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(name));
            return block == null ? null : block.defaultBlockState();
        } catch (Exception e) {
            Schematica.LOGGER.warn("Unknown block in schematic: {}", name);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState setProperty(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(v -> state.setValue(property, v)).orElse(state);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String valueName(Property.Value<?> value) {
        return ((Property) value.property()).getName(value.value());
    }

    private static String serializeState(BlockState state) {
        StringBuilder builder = new StringBuilder(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        List<Property.Value<?>> values = state.getValues().toList();
        if (!values.isEmpty()) {
            builder.append('[');
            for (int i = 0; i < values.size(); i++) {
                if (i > 0) {
                    builder.append(',');
                }
                Property.Value<?> value = values.get(i);
                builder.append(value.property().getName()).append('=').append(valueName(value));
            }
            builder.append(']');
        }
        return builder.toString();
    }

    private static CompoundTag serializeStateTag(BlockState state) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        CompoundTag properties = new CompoundTag();
        for (Property.Value<?> value : state.getValues().toList()) {
            properties.putString(value.property().getName(), valueName(value));
        }
        tag.put("Properties", properties);
        return tag;
    }

    public static BlockState parseState(String serialized) {
        if (serialized == null || serialized.isEmpty()) {
            return null;
        }
        int bracket = serialized.indexOf('[');
        String name = bracket < 0 ? serialized : serialized.substring(0, bracket);
        BlockState state = stateFromName(name);
        if (state == null || bracket < 0) {
            return state;
        }
        String properties = serialized.substring(bracket + 1).replace("]", "");
        for (String pair : properties.split(",")) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length != 2) {
                continue;
            }
            Property<?> property = state.getBlock().getStateDefinition().getProperty(keyValue[0]);
            if (property != null) {
                state = setProperty(state, property, keyValue[1]);
            }
        }
        return state;
    }

    private static String serializeIcon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static ItemStack parseIcon(String name) {
        if (name == null || name.isEmpty()) {
            return ItemStack.EMPTY;
        }
        try {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(name));
            return item == null ? ItemStack.EMPTY : new ItemStack(item);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }
}
