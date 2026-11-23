package com.github.lunatrius.schematica.client.printer;

import com.github.lunatrius.schematica.Config;
import com.github.lunatrius.schematica.client.SchematicState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Player-mediated printer, matching the original 1.12.2 behavior: it uses the
 * player's reach, swaps the required item into an allowed hotbar slot and
 * places through the normal interaction pipeline.
 */
public final class ClientPrinter {
    private static boolean active;
    private static int tickCounter;
    private static final Map<BlockPos, Integer> BLACKLIST = new HashMap<>();

    private ClientPrinter() {}

    public static boolean isActive() {
        return active;
    }

    public static void toggle() {
        if (active) {
            stop();
        } else {
            start();
        }
    }

    public static void start() {
        if (!SchematicState.instance().isLoaded()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal("No schematic loaded"));
            }
            return;
        }
        active = true;
        tickCounter = 0;
        BLACKLIST.clear();
    }

    public static void stop() {
        active = false;
        BLACKLIST.clear();
    }

    public static void tick(Minecraft mc) {
        if (!active || mc.player == null || mc.level == null || !SchematicState.instance().isLoaded()) {
            return;
        }

        BLACKLIST.entrySet().removeIf(entry -> {
            int remaining = entry.getValue() - 1;
            entry.setValue(remaining);
            return remaining <= 0;
        });
        if (++tickCounter < Math.max(1, Config.PLACE_DELAY.get())) {
            return;
        }
        tickCounter = 0;

        int budget = Config.PLACE_INSTANTLY.get() ? 8 : 1;
        List<BlockPos> candidates = candidates(mc);
        int placed = 0;
        for (BlockPos pos : candidates) {
            if (placed >= budget) {
                break;
            }
            if (placeAt(mc, pos)) {
                placed++;
                BLACKLIST.remove(pos);
            } else {
                BLACKLIST.put(pos, Math.max(1, Config.TIMEOUT.get()));
            }
        }
    }

    private static List<BlockPos> candidates(Minecraft mc) {
        SchematicState state = SchematicState.instance();
        int distance = Config.PLACE_DISTANCE.get();
        BlockPos center = mc.player.blockPosition();
        List<BlockPos> result = new ArrayList<>();
        for (int x = center.getX() - distance; x <= center.getX() + distance; x++) {
            for (int z = center.getZ() - distance; z <= center.getZ() + distance; z++) {
                for (int y = center.getY() - distance; y <= center.getY() + distance; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (BLACKLIST.containsKey(pos)) {
                        continue;
                    }
                    BlockState expected = state.renderStateAt(pos);
                    if (state.isExtraAir(expected)) {
                        continue;
                    }
                    BlockState actual = mc.level.getBlockState(pos);
                    if (actual == expected) {
                        continue;
                    }
                    boolean replaceable = actual == null || actual.isAir() || actual.canBeReplaced();
                    if (!replaceable && !(Config.DESTROY_BLOCKS.get()
                            && mc.gameMode.getPlayerMode() == GameType.CREATIVE)) {
                        continue;
                    }
                    if (!mc.player.isWithinBlockInteractionRange(pos, mc.player.blockInteractionRange() - 0.1)) {
                        continue;
                    }
                    result.add(pos);
                }
            }
        }
        result.sort(Comparator.comparingDouble(pos ->
                pos.distSqr(center) + (center.getY() - pos.getY()) * 0.1));
        return result;
    }

    private static boolean placeAt(Minecraft mc, BlockPos pos) {
        SchematicState state = SchematicState.instance();
        BlockState expected = state.renderStateAt(pos);
        ItemStack required = new ItemStack(expected.getBlock().asItem());
        if (required.isEmpty() || required.getItem() instanceof BucketItem) {
            return false;
        }

        LocalPlayer player = mc.player;
        BlockState actual = mc.level.getBlockState(pos);
        if (!actual.isAir() && !actual.canBeReplaced() && Config.DESTROY_BLOCKS.get()
                && mc.gameMode.getPlayerMode() == GameType.CREATIVE) {
            if (!mc.gameMode.destroyBlock(pos)) {
                return false;
            }
            if (Config.DESTROY_INSTANTLY.get()) {
                actual = mc.level.getBlockState(pos);
            }
        }
        if (!actual.isAir() && !actual.canBeReplaced()) {
            return false;
        }

        var inventory = player.getInventory();
        int sourceSlot = -1;
        int hotbarSlot = -1;
        for (int slot = 0; slot < 9; slot++) {
            if (!Config.SWAP_SLOTS[slot].get()) {
                continue;
            }
            ItemStack held = inventory.getItem(slot);
            if (held.isEmpty() || ItemStack.isSameItemSameComponents(held, required)) {
                hotbarSlot = slot;
                sourceSlot = slot;
                break;
            }
        }
        if (hotbarSlot < 0) {
            for (int slot = 9; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, required)) {
                    sourceSlot = slot;
                    break;
                }
            }
            if (sourceSlot < 0) {
                return false;
            }
            for (int slot = 0; slot < 9; slot++) {
                if (Config.SWAP_SLOTS[slot].get()) {
                    hotbarSlot = slot;
                    break;
                }
            }
            if (hotbarSlot < 0) {
                return false;
            }
        }

        BlockHitResult hit = findHit(pos, mc);
        if (hit == null) {
            return false;
        }

        int previousSelected = inventory.getSelectedSlot();
        ItemStack previous = inventory.getItem(hotbarSlot).copy();
        ItemStack source = inventory.getItem(sourceSlot).copy();
        inventory.setItem(hotbarSlot, source);
        inventory.setSelectedSlot(hotbarSlot);

        boolean placed = false;
        try {
            placed = mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction();
        } finally {
            ItemStack remaining = inventory.getItem(hotbarSlot).copy();
            inventory.setItem(hotbarSlot, previous);
            if (sourceSlot >= 9) {
                inventory.setItem(sourceSlot, remaining);
            }
            inventory.setSelectedSlot(previousSelected);
        }
        return placed;
    }

    private static BlockHitResult findHit(BlockPos target, Minecraft mc) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = target.relative(direction.getOpposite());
            if (mc.level.getBlockState(neighbor).isSolid()) {
                return new BlockHitResult(
                        new net.minecraft.world.phys.Vec3(neighbor.getX() + 0.5
                                        + direction.getStepX() * 0.5,
                                neighbor.getY() + 0.5 + direction.getStepY() * 0.5,
                                neighbor.getZ() + 0.5 + direction.getStepZ() * 0.5),
                        direction, neighbor, false);
            }
        }
        if (!Config.PLACE_ADJACENT.get()) {
            return new BlockHitResult(
                    new net.minecraft.world.phys.Vec3(target.getX() + 0.5,
                            target.getY() + 0.5, target.getZ() + 0.5),
                    Direction.UP, target, false);
        }
        return null;
    }
}
