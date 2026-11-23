package com.github.lunatrius.schematica;

import com.github.lunatrius.schematica.api.ISchematic;
import com.github.lunatrius.schematica.world.SchematicWorld;
import com.github.lunatrius.schematica.world.schematic.Schematic;
import com.github.lunatrius.schematica.world.schematic.SchematicFormats;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Server-side commands, so they work in singleplayer and on dedicated servers. */
public final class SchematicaCommands {
    private SchematicaCommands() {}

    public static void register(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("schematica")
                .executes(ctx -> {
                    send(ctx, "Schematica 2.0.0 — list | save | paste | print | printer stop | delete | selftest");
                    return 1;
                });

        root.then(Commands.literal("list").executes(SchematicaCommands::list));
        root.then(Commands.literal("save").then(saveArguments()));
        root.then(Commands.literal("paste").then(pasteArguments()));
        root.then(Commands.literal("print").then(printArguments()));
        root.then(Commands.literal("printer")
                .then(Commands.literal("stop").executes(ctx -> {
                    SchematicPrinter.stop();
                    send(ctx, "Printer stopped");
                    return 1;
                }))
                .then(Commands.literal("status").executes(ctx -> {
                    send(ctx, SchematicPrinter.status());
                    return 1;
                })));
        root.then(Commands.literal("delete").then(deleteArguments()));
        root.then(Commands.literal("blockat").then(blockAtArguments()));
        root.then(Commands.literal("inspect").then(inspectArguments()));
        root.then(Commands.literal("selftest").executes(SchematicaCommands::selfTest));

        // Legacy 1.12.2 command names as aliases.
        event.getDispatcher().register(Commands.literal("schematicaList")
                .executes(SchematicaCommands::list));
        event.getDispatcher().register(Commands.literal("schematicaSave").then(saveArguments()));
        event.getDispatcher().register(Commands.literal("schematicaRemove").then(deleteArguments()));
        event.getDispatcher().register(Commands.literal("schematicaDownload")
                .then(Commands.argument("name", StringArgumentType.string())
                        .executes(ctx -> {
                            String name = stringArg(ctx, "name");
                            boolean exists = SchematicManager.list().stream()
                                    .anyMatch(entry -> entry.equalsIgnoreCase(name)
                                            || entry.equalsIgnoreCase(name + ".schematic")
                                            || entry.equalsIgnoreCase(name + ".nbt"));
                            send(ctx, exists
                                    ? "Schematic " + name + " is in your schematics folder"
                                    : "Schematic not found: " + name);
                            return exists ? 1 : 0;
                        })));

        event.getDispatcher().register(root);
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        List<String> names = SchematicManager.list();
        if (names.isEmpty()) {
            send(ctx, "No schematics found in " + SchematicManager.directory());
        } else {
            StringBuilder message = new StringBuilder("Schematics (" + names.size() + "): ");
            for (String name : names) {
                message.append(name).append(" [").append(SchematicManager.sizeDescription(name)).append("] ");
            }
            send(ctx, message.toString());
        }
        return names.size();
    }

    private static ArgumentBuilder<CommandSourceStack, ?> saveArguments() {
        return Commands.argument("name", StringArgumentType.string()).then(saveCoordinates(ctx -> {
            String name = stringArg(ctx, "name");
            String format = optionalStringArg(ctx, "format", "alpha");
            boolean structure = "structure".equalsIgnoreCase(format);
            String extension = structure ? ".nbt" : ".schematic";
            String fileName = name.endsWith(extension) ? name : name + extension;
            BlockPos first = new BlockPos(intArg(ctx, "x1"), intArg(ctx, "y1"), intArg(ctx, "z1"));
            BlockPos second = new BlockPos(intArg(ctx, "x2"), intArg(ctx, "y2"), intArg(ctx, "z2"));
            ServerLevel level = ctx.getSource().getLevel();
            Schematic schematic = SchematicWorld.capture(level, first, second);
            try {
                SchematicManager.save(fileName, schematic,
                        structure ? SchematicFormats.FORMAT_STRUCTURE : SchematicFormats.FORMAT_ALPHA);
            } catch (Exception e) {
                fail(ctx, "Save failed: " + e.getMessage());
                return 0;
            }
            send(ctx, "Saved " + schematic.getWidth() + "x" + schematic.getHeight() + "x"
                    + schematic.getLength() + " schematic (" + schematic.countNonAir() + " blocks) as "
                    + fileName);
            return 1;
        }));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> pasteArguments() {
        return Commands.argument("name", StringArgumentType.string()).then(position(0, ctx -> {
            try {
                ISchematic schematic = SchematicManager.load(stringArg(ctx, "name"));
                BlockPos origin = new BlockPos(intArg(ctx, "x"), intArg(ctx, "y"), intArg(ctx, "z"));
                int placed = SchematicWorld.paste(schematic, ctx.getSource().getLevel(), origin);
                send(ctx, "Placed " + placed + " blocks");
            } catch (Exception e) {
                fail(ctx, "Load/paste failed: " + e.getMessage());
                return 0;
            }
            return 1;
        }));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> printArguments() {
        return Commands.argument("name", StringArgumentType.string()).then(position(0, ctx -> {
            try {
                ISchematic schematic = SchematicManager.load(stringArg(ctx, "name"));
                BlockPos origin = new BlockPos(intArg(ctx, "x"), intArg(ctx, "y"), intArg(ctx, "z"));
                SchematicPrinter.start(ctx.getSource().getLevel(), schematic, origin);
                send(ctx, "Printer started: " + SchematicPrinter.status());
            } catch (Exception e) {
                fail(ctx, "Load/print failed: " + e.getMessage());
                return 0;
            }
            return 1;
        }));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> deleteArguments() {
        return Commands.argument("name", StringArgumentType.string()).executes(ctx -> {
            try {
                boolean deleted = SchematicManager.delete(stringArg(ctx, "name"));
                send(ctx, deleted ? "Deleted" : "Not found");
            } catch (Exception e) {
                fail(ctx, "Delete failed: " + e.getMessage());
                return 0;
            }
            return 1;
        });
    }

    private static ArgumentBuilder<CommandSourceStack, ?> blockAtArguments() {
        return Commands.argument("x", IntegerArgumentType.integer())
                .then(Commands.argument("y", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                .executes(ctx -> {
                                    ServerLevel level = ctx.getSource().getLevel();
                                    BlockPos pos = new BlockPos(intArg(ctx, "x"), intArg(ctx, "y"), intArg(ctx, "z"));
                                    level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
                                    send(ctx, "Block at " + pos + " = "
                                            + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(
                                            level.getBlockState(pos).getBlock()));
                                    return 1;
                                })));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> inspectArguments() {
        return Commands.argument("name", StringArgumentType.string()).executes(ctx -> {
            try {
                ISchematic schematic = SchematicManager.load(stringArg(ctx, "name"));
                int nonAir = schematic instanceof Schematic concrete ? concrete.countNonAir() : -1;
                send(ctx, "Inspect " + schematic.getWidth() + "x" + schematic.getHeight() + "x"
                        + schematic.getLength() + " nonAir=" + nonAir
                        + " icon=" + schematic.getIcon().getItem()
                        + " author=" + schematic.getAuthor());
            } catch (Exception e) {
                fail(ctx, "Inspect failed: " + e.getMessage());
                return 0;
            }
            return 1;
        });
    }

    private static int selfTest(CommandContext<CommandSourceStack> ctx) {
        try {
            Schematic source = new Schematic(2, 2, 2);
            source.setBlockState(new BlockPos(0, 0, 0), Blocks.STONE.defaultBlockState());
            source.setBlockState(new BlockPos(1, 1, 1), Blocks.GLOWSTONE.defaultBlockState());

            Path temp = Files.createTempFile("schematica-selftest-", ".schematic");
            try {
                SchematicFormats.write(temp, source, SchematicFormats.FORMAT_ALPHA);
                ISchematic result = SchematicFormats.read(temp);
                boolean ok = result.getWidth() == 2 && result.getHeight() == 2 && result.getLength() == 2
                        && result.getBlockState(new BlockPos(0, 0, 0)).getBlock() == Blocks.STONE
                        && result.getBlockState(new BlockPos(1, 1, 1)).getBlock() == Blocks.GLOWSTONE;
                if (!ok) {
                    fail(ctx, "Schematic round-trip FAILED");
                    return 0;
                }
            } finally {
                Files.deleteIfExists(temp);
            }
            send(ctx, "Schematic round-trip OK");
        } catch (Exception e) {
            Schematica.LOGGER.error("Self test failed", e);
            fail(ctx, "Self test failed: " + e.getMessage());
            return 0;
        }
        return 1;
    }

    private static final String[] COORDS = {"x1", "y1", "z1", "x2", "y2", "z2"};
    private static final String[] POS = {"x", "y", "z"};

    private static ArgumentBuilder<CommandSourceStack, ?> saveCoordinates(Command<CommandSourceStack> command) {
        return chainWithFormat(COORDS, 0, command);
    }

    private static ArgumentBuilder<CommandSourceStack, ?> position(int index, Command<CommandSourceStack> command) {
        return chain(POS, index, command);
    }

    private static ArgumentBuilder<CommandSourceStack, ?> chain(
            String[] names, int index, Command<CommandSourceStack> command) {
        if (index == names.length - 1) {
            return Commands.argument(names[index], IntegerArgumentType.integer()).executes(command);
        }
        return Commands.argument(names[index], IntegerArgumentType.integer())
                .then(chain(names, index + 1, command));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> chainWithFormat(
            String[] names, int index, Command<CommandSourceStack> command) {
        if (index == names.length - 1) {
            return Commands.argument(names[index], IntegerArgumentType.integer())
                    .executes(command)
                    .then(Commands.argument("format", StringArgumentType.word()).executes(command));
        }
        return Commands.argument(names[index], IntegerArgumentType.integer())
                .then(chainWithFormat(names, index + 1, command));
    }

    private static int intArg(CommandContext<CommandSourceStack> ctx, String name) {
        return IntegerArgumentType.getInteger(ctx, name);
    }

    private static String stringArg(CommandContext<CommandSourceStack> ctx, String name) {
        return StringArgumentType.getString(ctx, name);
    }

    private static String optionalStringArg(CommandContext<CommandSourceStack> ctx,
                                            String name, String fallback) {
        try {
            return StringArgumentType.getString(ctx, name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static void send(CommandContext<CommandSourceStack> ctx, String message) {
        ctx.getSource().sendSuccess(() -> Component.literal(message), false);
    }

    private static void fail(CommandContext<CommandSourceStack> ctx, String message) {
        ctx.getSource().sendFailure(Component.literal(message));
    }
}
