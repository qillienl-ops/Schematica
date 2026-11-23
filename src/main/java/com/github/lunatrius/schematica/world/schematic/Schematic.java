package com.github.lunatrius.schematica.world.schematic;

import com.github.lunatrius.schematica.api.ISchematic;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory schematic. The original 1.12.2 implementation stored numeric block
 * IDs and metadata bytes; since the flattening those no longer exist, this
 * implementation stores actual {@link BlockState} values.
 */
public class Schematic implements ISchematic {
    private final int width;
    private final int height;
    private final int length;
    private final BlockState[] states;

    private final Map<BlockPos, CompoundTag> blockEntities = new HashMap<>();
    private final List<CompoundTag> entities = new ArrayList<>();

    private String author = "";
    private ItemStack icon = ItemStack.EMPTY;

    public Schematic(int width, int height, int length) {
        if (width <= 0 || height <= 0 || length <= 0) {
            throw new IllegalArgumentException("Schematic dimensions must be positive");
        }

        this.width = width;
        this.height = height;
        this.length = length;
        this.states = new BlockState[width * height * length];
        Arrays.fill(this.states, Blocks.AIR.defaultBlockState());
    }

    private int index(BlockPos pos) {
        return pos.getX() + (pos.getY() * this.length + pos.getZ()) * this.width;
    }

    public boolean isValid(BlockPos pos) {
        return pos.getX() >= 0 && pos.getY() >= 0 && pos.getZ() >= 0
                && pos.getX() < this.width && pos.getY() < this.height && pos.getZ() < this.length;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        if (!isValid(pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        BlockState state = this.states[index(pos)];
        return state == null ? Blocks.AIR.defaultBlockState() : state;
    }

    @Override
    public boolean setBlockState(BlockPos pos, BlockState state) {
        if (!isValid(pos)) {
            return false;
        }
        this.states[index(pos)] = state == null ? Blocks.AIR.defaultBlockState() : state;
        return true;
    }

    @Override
    public CompoundTag getBlockEntity(BlockPos pos) {
        return this.blockEntities.get(pos);
    }

    @Override
    public void setBlockEntity(BlockPos pos, CompoundTag tag) {
        this.blockEntities.remove(pos);
        if (tag != null) {
            this.blockEntities.put(pos.immutable(), tag.copy());
        }
    }

    @Override
    public List<CompoundTag> getEntities() {
        return this.entities;
    }

    @Override
    public int getWidth() {
        return this.width;
    }

    @Override
    public int getHeight() {
        return this.height;
    }

    @Override
    public int getLength() {
        return this.length;
    }

    @Override
    public String getAuthor() {
        return this.author;
    }

    @Override
    public void setAuthor(String author) {
        this.author = author == null ? "" : author;
    }

    @Override
    public ItemStack getIcon() {
        return this.icon;
    }

    @Override
    public void setIcon(ItemStack icon) {
        this.icon = icon == null ? ItemStack.EMPTY : icon.copy();
    }

    /** Number of non-air blocks; handy for commands and self tests. */
    public int countNonAir() {
        int count = 0;
        BlockState air = Blocks.AIR.defaultBlockState();
        for (BlockState state : this.states) {
            if (state != null && state != air && !state.isAir()) {
                count++;
            }
        }
        return count;
    }

    public Iterable<Map.Entry<BlockPos, CompoundTag>> blockEntities() {
        return this.blockEntities.entrySet();
    }
}
