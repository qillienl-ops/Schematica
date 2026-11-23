package com.github.lunatrius.schematica;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue RENDER_DISTANCE = BUILDER
            .comment("How many blocks around the player schematic ghosts are drawn.")
            .defineInRange("renderDistance", 16, 2, 64);

    public static final ModConfigSpec.DoubleValue ALPHA = BUILDER
            .comment("Opacity of the schematic overlay (1.0 = opaque).")
            .defineInRange("alpha", 0.5D, 0.0D, 1.0D);

    public static final ModConfigSpec.BooleanValue HIGHLIGHT = BUILDER
            .comment("Highlight blocks that differ from the schematic.")
            .define("highlight", true);

    public static final ModConfigSpec.BooleanValue PRINTER_ENABLED = BUILDER
            .comment("Allow the printer to place blocks.")
            .define("printerEnabled", true);

    public static final ModConfigSpec.BooleanValue SAVE_ENABLED = BUILDER
            .comment("Allow saving schematics from the world.")
            .define("saveEnabled", true);

    public static final ModConfigSpec.BooleanValue LOAD_ENABLED = BUILDER
            .comment("Allow loading schematics into the world.")
            .define("loadEnabled", true);

    public static final ModConfigSpec.IntValue PLACE_DELAY = BUILDER
            .comment("Ticks between printer placement attempts (0 = as fast as possible).")
            .defineInRange("placeDelay", 1, 0, 20);

    public static final ModConfigSpec.ConfigValue<String> SCHEMATIC_DIRECTORY = BUILDER
            .comment("Directory used to store schematics (relative to the game directory).")
            .define("schematicDirectory", "schematics");

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {}
}
