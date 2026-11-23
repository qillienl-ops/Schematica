package com.github.lunatrius.schematica;

import com.github.lunatrius.schematica.api.ISchematic;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Server-side printer. Places one layer of the loaded schematic per tick until
 * it is finished, so building stays responsive and can be stopped at any time.
 */
public final class SchematicPrinter {
    private static ISchematic schematic;
    private static ServerLevel level;
    private static BlockPos origin;
    private static int index;
    private static int placed;
    private static boolean running;

    private SchematicPrinter() {}

    public static synchronized void start(ServerLevel level, ISchematic schematic, BlockPos origin) {
        SchematicPrinter.level = level;
        SchematicPrinter.schematic = schematic;
        SchematicPrinter.origin = origin;
        SchematicPrinter.index = 0;
        SchematicPrinter.placed = 0;
        SchematicPrinter.running = true;
    }

    public static synchronized void stop() {
        running = false;
        schematic = null;
        level = null;
        origin = null;
    }

    public static synchronized boolean isRunning() {
        return running;
    }

    public static synchronized String status() {
        if (!running || schematic == null) {
            return "Printer idle";
        }
        int total = schematic.getWidth() * schematic.getHeight() * schematic.getLength();
        int percent = total == 0 ? 100 : (int) (index * 100L / total);
        return "Printing " + placed + " blocks (" + percent + "%) at " + origin;
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        tick();
    }

    public static synchronized void tick() {
        if (!running || level == null || schematic == null || origin == null) {
            return;
        }
        if (!Config.PRINTER_ENABLED.get()) {
            stop();
            return;
        }

        ISchematic current = schematic;
        ServerLevel currentLevel = level;
        int width = current.getWidth();
        int height = current.getHeight();
        int length = current.getLength();
        int total = width * height * length;
        int budget = Math.max(1, 256 / Math.max(1, Config.PLACE_DELAY.get()));

        try {
            while (index < total && budget-- > 0) {
                int x = index % width;
                int y = (index / width) % height;
                int z = index / (width * height);
                index++;

                BlockPos localPos = new BlockPos(x, y, z);
                BlockState state = current.getBlockState(localPos);
                if (state.isAir()) {
                    continue;
                }

                BlockPos worldPos = origin.offset(x, y, z);
                currentLevel.getChunk(worldPos.getX() >> 4, worldPos.getZ() >> 4);
                currentLevel.setBlock(worldPos, state, 3);
                placed++;
                CompoundTag tag = current.getBlockEntity(localPos);
                if (tag != null) {
                    CompoundTag copy = tag.copy();
                    copy.putInt("x", worldPos.getX());
                    copy.putInt("y", worldPos.getY());
                    copy.putInt("z", worldPos.getZ());
                    BlockEntity.loadStatic(worldPos, state, copy, currentLevel.registryAccess());
                }
            }
        } catch (RuntimeException e) {
            Schematica.LOGGER.error("Printer stopped because of an error", e);
            stop();
            return;
        }

        if (index >= total) {
            Schematica.LOGGER.info("Printer finished: {} blocks placed", placed);
            stop();
        }
    }
}
