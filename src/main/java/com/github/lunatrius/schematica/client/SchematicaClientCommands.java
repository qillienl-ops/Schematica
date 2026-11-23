package com.github.lunatrius.schematica.client;

import com.github.lunatrius.schematica.api.ISchematic;
import com.github.lunatrius.schematica.world.schematic.SchematicFormats;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * The 1.12.2 {@code /schematicaReplace} command was client-side: it edited the
 * loaded SchematicWorld in place. The other commands are server-side, but this
 * one must stay on the client because that is where the loaded schematic lives.
 */
public final class SchematicaClientCommands {
    private SchematicaClientCommands() {}

    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("schematicaReplace")
                .then(Commands.argument("from", StringArgumentType.string())
                        .then(Commands.argument("to", StringArgumentType.string())
                                .executes(ctx -> replace(
                                        StringArgumentType.getString(ctx, "from"),
                                        StringArgumentType.getString(ctx, "to"))))));
    }

    private record Pattern(BlockState state, Map<String, String> properties) { }

    private static int replace(String fromText, String toText) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        SchematicState state = SchematicState.instance();
        if (!state.isLoaded() || mc.player == null) {
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal("No schematic loaded"));
            }
            return 0;
        }

        Pattern from = parsePattern(fromText);
        BlockState to = SchematicFormats.parseState(toText);
        if (from == null || to == null) {
            mc.player.sendSystemMessage(Component.literal("Unknown block state"));
            return 0;
        }

        ISchematic schematic = state.schematic();
        int count = 0;
        for (int x = 0; x < schematic.getWidth(); x++) {
            for (int y = 0; y < schematic.getHeight(); y++) {
                for (int z = 0; z < schematic.getLength(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState current = schematic.getBlockState(pos);
                    if (matches(current, from)) {
                        schematic.setBlockState(pos, to);
                        count++;
                    }
                }
            }
        }
        com.github.lunatrius.schematica.client.printer.ClientPrinter.refresh();
        mc.player.sendSystemMessage(Component.literal("Replaced " + count + " blocks"));
        return count;
    }

    /** Only properties written explicitly in the pattern act as filters. */
    private static Pattern parsePattern(String text) {
        BlockState state = SchematicFormats.parseState(text);
        if (state == null) {
            return null;
        }
        Map<String, String> properties = new HashMap<>();
        int bracket = text.indexOf('[');
        if (bracket >= 0 && text.endsWith("]")) {
            for (String pair : text.substring(bracket + 1, text.length() - 1).split(",")) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2) {
                    properties.put(keyValue[0].trim(), keyValue[1].trim());
                }
            }
        }
        return new Pattern(state, properties);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean matches(BlockState current, Pattern pattern) {
        if (current.getBlock() != pattern.state().getBlock()) {
            return false;
        }
        for (Map.Entry<String, String> entry : pattern.properties().entrySet()) {
            Property property = current.getBlock().getStateDefinition().getProperty(entry.getKey());
            if (property == null) {
                return false;
            }
            var parsed = property.getValue(entry.getValue());
            if (parsed.isEmpty() || !current.getValue(property).equals(parsed.get())) {
                return false;
            }
        }
        return true;
    }
}
