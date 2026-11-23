package com.github.lunatrius.schematica;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * Config keys, defaults and ranges mirror the original 1.12.2
 * {@code ConfigurationHandler} so existing configs behave the same way.
 */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue DUMP_BLOCK_LIST = BUILDER
            .comment("Dump the block list to dumps/schematica-blocks.txt (requires a restart).")
            .define("debug.dumpBlockList", false);

    public static final ModConfigSpec.BooleanValue SHOW_DEBUG_INFO = BUILDER
            .comment("Show Schematica lines in the F3 debug overlay.")
            .define("debug.showDebugInfo", true);

    public static final ModConfigSpec.BooleanValue ALPHA_ENABLED = BUILDER
            .comment("Render ghost blocks with translucent alpha.")
            .define("render.alphaEnabled", false);

    public static final ModConfigSpec.DoubleValue ALPHA = BUILDER
            .comment("Opacity of the schematic overlay (1.0 = opaque).")
            .defineInRange("render.alpha", 1.0D, 0.0D, 1.0D);

    public static final ModConfigSpec.BooleanValue TEXTURED_GHOSTS = BUILDER
            .comment("Draw schematic ghosts with real block models instead of plain colored cubes.")
            .define("render.texturedGhosts", true);

    public static final ModConfigSpec.BooleanValue HIGHLIGHT = BUILDER
            .comment("Highlight blocks that differ from the schematic.")
            .define("render.highlight", true);

    public static final ModConfigSpec.BooleanValue HIGHLIGHT_AIR = BUILDER
            .comment("Highlight blocks that should be air according to the schematic.")
            .define("render.highlightAir", true);

    public static final ModConfigSpec.DoubleValue BLOCK_DELTA = BUILDER
            .comment("Small offset used to avoid z-fighting on highlight boxes.")
            .defineInRange("render.blockDelta", 0.005D, 0.0D, 0.2D);

    public static final ModConfigSpec.IntValue RENDER_DISTANCE = BUILDER
            .comment("How many blocks around the player schematic ghosts are drawn.")
            .defineInRange("render.renderDistance", 8, 2, 16);

    public static final ModConfigSpec.IntValue PLACE_DELAY = BUILDER
            .comment("Ticks between printer placement attempts (0 = as fast as possible).")
            .defineInRange("printer.placeDelay", 1, 0, 20);

    public static final ModConfigSpec.IntValue TIMEOUT = BUILDER
            .comment("Ticks before the printer gives up on a block.")
            .defineInRange("printer.timeout", 10, 0, 100);

    public static final ModConfigSpec.IntValue PLACE_DISTANCE = BUILDER
            .comment("How far from the player the printer looks for blocks to place.")
            .defineInRange("printer.placeDistance", 5, 1, 5);

    public static final ModConfigSpec.BooleanValue PLACE_INSTANTLY = BUILDER
            .comment("Allow more than one placement per tick.")
            .define("printer.placeInstantly", false);

    public static final ModConfigSpec.BooleanValue DESTROY_BLOCKS = BUILDER
            .comment("Allow the printer to remove wrong blocks (creative mode only).")
            .define("printer.destroyBlocks", false);

    public static final ModConfigSpec.BooleanValue DESTROY_INSTANTLY = BUILDER
            .comment("Continue placing in the same tick after destroying a block.")
            .define("printer.destroyInstantly", false);

    public static final ModConfigSpec.BooleanValue PLACE_ADJACENT = BUILDER
            .comment("Require an adjacent solid block when placing.")
            .define("printer.placeAdjacent", true);

    public static final ModConfigSpec.BooleanValue SWAP_SLOT_0 = swapSlot(0, false);
    public static final ModConfigSpec.BooleanValue SWAP_SLOT_1 = swapSlot(1, false);
    public static final ModConfigSpec.BooleanValue SWAP_SLOT_2 = swapSlot(2, false);
    public static final ModConfigSpec.BooleanValue SWAP_SLOT_3 = swapSlot(3, false);
    public static final ModConfigSpec.BooleanValue SWAP_SLOT_4 = swapSlot(4, false);
    public static final ModConfigSpec.BooleanValue SWAP_SLOT_5 = swapSlot(5, true);
    public static final ModConfigSpec.BooleanValue SWAP_SLOT_6 = swapSlot(6, true);
    public static final ModConfigSpec.BooleanValue SWAP_SLOT_7 = swapSlot(7, true);
    public static final ModConfigSpec.BooleanValue SWAP_SLOT_8 = swapSlot(8, true);

    public static final ModConfigSpec.BooleanValue[] SWAP_SLOTS = {
            SWAP_SLOT_0, SWAP_SLOT_1, SWAP_SLOT_2, SWAP_SLOT_3, SWAP_SLOT_4,
            SWAP_SLOT_5, SWAP_SLOT_6, SWAP_SLOT_7, SWAP_SLOT_8
    };

    public static final ModConfigSpec.ConfigValue<String> SCHEMATIC_DIRECTORY = BUILDER
            .comment("Directory used to store schematics (relative to the game directory).")
            .define("general.schematicDirectory", "schematics");

    public static final ModConfigSpec.ConfigValue<List<? extends String>> EXTRA_AIR_BLOCKS = BUILDER
            .comment("Blocks that should be treated as air by the renderer.")
            .defineListAllowEmpty("general.extraAirBlocks", List.of(), entry -> entry instanceof String);

    public static final ModConfigSpec.ConfigValue<String> SORT_TYPE = BUILDER
            .comment("Default sort order of the materials screen.")
            .define("general.sortType", "");

    public static final ModConfigSpec.BooleanValue PRINTER_ENABLED = BUILDER
            .comment("Allow the printer to place blocks.")
            .define("server.printerEnabled", true);

    public static final ModConfigSpec.BooleanValue SAVE_ENABLED = BUILDER
            .comment("Allow saving schematics from the world.")
            .define("server.saveEnabled", true);

    public static final ModConfigSpec.BooleanValue LOAD_ENABLED = BUILDER
            .comment("Allow loading schematics into the world.")
            .define("server.loadEnabled", true);

    public static final ModConfigSpec.IntValue PLAYER_QUOTA_KILOBYTES = BUILDER
            .comment("Per-player server schematic quota in kilobytes.")
            .defineInRange("server.playerQuotaKilobytes", 8192, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private static ModConfigSpec.BooleanValue swapSlot(int slot, boolean defaultValue) {
        return BUILDER.comment("Allow the printer to use hotbar slot " + (slot + 1) + ".")
                .define("printer.swapslots.swapSlot" + slot, defaultValue);
    }

    private Config() {}
}
