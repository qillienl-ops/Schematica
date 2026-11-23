package com.github.lunatrius.schematica.api;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Public schematic API. Kept close to the original 1.12.2 API, but backed by
 * modern {@link BlockState} instances instead of numeric block IDs + metadata.
 */
public interface ISchematic {
    BlockState getBlockState(BlockPos pos);

    boolean setBlockState(BlockPos pos, BlockState state);

    CompoundTag getBlockEntity(BlockPos pos);

    void setBlockEntity(BlockPos pos, CompoundTag tag);

    List<CompoundTag> getEntities();

    int getWidth();

    int getHeight();

    int getLength();

    String getAuthor();

    void setAuthor(String author);

    ItemStack getIcon();

    void setIcon(ItemStack icon);
}
