package com.github.lunatrius.schematica.world;

import com.github.lunatrius.schematica.api.ISchematic;
import com.github.lunatrius.schematica.world.schematic.Schematic;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Copying schematics to and from a real world. */
public final class SchematicWorld {
    private SchematicWorld() {}

    public static Schematic capture(Level level, BlockPos first, BlockPos second) {
        BlockPos min = new BlockPos(
                Math.min(first.getX(), second.getX()),
                Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ()));
        BlockPos max = new BlockPos(
                Math.max(first.getX(), second.getX()),
                Math.max(first.getY(), second.getY()),
                Math.max(first.getZ(), second.getZ()));

        int width = max.getX() - min.getX() + 1;
        int height = max.getY() - min.getY() + 1;
        int length = max.getZ() - min.getZ() + 1;
        Schematic schematic = new Schematic(width, height, length);

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < length; z++) {
                    BlockPos worldPos = min.offset(x, y, z);
                    BlockPos localPos = new BlockPos(x, y, z);
                    schematic.setBlockState(localPos, level.getBlockState(worldPos));

                    BlockEntity blockEntity = level.getBlockEntity(worldPos);
                    if (blockEntity != null) {
                        CompoundTag tag = blockEntity.saveWithFullMetadata(level.registryAccess());
                        tag.putInt("x", x);
                        tag.putInt("y", y);
                        tag.putInt("z", z);
                        schematic.setBlockEntity(localPos, tag);
                    }
                }
            }
        }

        return schematic;
    }

    /** Places a schematic into a level. Returns the number of placed blocks. */
    public static int paste(ISchematic schematic, Level level, BlockPos origin) {
        int placed = 0;
        for (int x = 0; x < schematic.getWidth(); x++) {
            for (int y = 0; y < schematic.getHeight(); y++) {
                for (int z = 0; z < schematic.getLength(); z++) {
                    BlockPos localPos = new BlockPos(x, y, z);
                    BlockState state = schematic.getBlockState(localPos);
                    if (state.isAir()) {
                        continue;
                    }
                    BlockPos worldPos = origin.offset(x, y, z);
                    if (level.setBlock(worldPos, state, 3)) {
                        placed++;
                    }
                    CompoundTag tag = schematic.getBlockEntity(localPos);
                    if (tag != null) {
                        CompoundTag copy = tag.copy();
                        copy.putInt("x", worldPos.getX());
                        copy.putInt("y", worldPos.getY());
                        copy.putInt("z", worldPos.getZ());
                        BlockEntity.loadStatic(worldPos, state, copy, level.registryAccess());
                    }
                }
            }
        }
        return placed;
    }
}
