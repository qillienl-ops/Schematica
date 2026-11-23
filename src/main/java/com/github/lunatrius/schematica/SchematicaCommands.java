package com.github.lunatrius.schematica;

import com.github.lunatrius.schematica.api.ISchematic;
import com.github.lunatrius.schematica.world.SchematicWorld;
import com.github.lunatrius.schematica.world.schematic.Schematic;
import com.github.lunatrius.schematica.world.schematic.SchematicFormats;
import com.mojang.brigadier.Command;
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

        root.then(Commands.literal("list").executes(ctx -> {
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
        }));

        root.then(Commands.literal("save")
                .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.string())
                        .then(coordinates(ctx -> {
                            String name = stringArg(ctx, "name");
                            String fileName = name.endsWith(".schematic") ? name : name + ".schematic";
                            BlockPos first = new BlockPos(intArg(ctx, "x1"), intArg(ctx, "y1"), intArg(ctx, "z1"));
                            BlockPos second = new BlockPos(intArg(ctx, "x2"), intArg(ctx, "y2"), intArg(ctx, "z2"));
                            ServerLevel level = ctx.getSource().getLevel();
                            Schematic schematic = SchematicWorld.capture(level, first, second);
                            try {
                                SchematicManager.save(fileName, schematic);
                            } catch (Exception e) {
                                fail(ctx, "Save failed: " + e.getMessage());
                                return 0;
                            }
                            send(ctx, "Saved " + schematic.getWidth() + "x" + schematic.getHeight() + "x"
                                    + schematic.getLength() + " schematic (" + schematic.countNonAir() + " blocks) as "
                                    + fileName);
                            return 1;
                        }))));

        root.then(Commands.literal("paste")
                .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.string())
                        .then(position(0, ctx -> {
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
                        }))));

        root.then(Commands.literal("print")
                .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.string())
                        .then(position(0, ctx -> {
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
                        }))));

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

        root.then(Commands.literal("delete")
                .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.string())
                        .executes(ctx -> {
                            try {
                                boolean deleted = SchematicManager.delete(stringArg(ctx, "name"));
                                send(ctx, deleted ? "Deleted" : "Not found");
                            } catch (Exception e) {
                                fail(ctx, "Delete failed: " + e.getMessage());
                                return 0;
                            }
                            return 1;
                        })));

        root.then(Commands.literal("selftest").executes(ctx -> {
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
        }));

        event.getDispatcher().register(root);
    }

    private static final String[] COORDS = {"x1", "y1", "z1", "x2", "y2", "z2"};
    private static final String[] POS = {"x", "y", "z"};

    private static ArgumentBuilder<CommandSourceStack, ?> coordinates(Command<CommandSourceStack> command) {
        return chain(COORDS, 0, command);
    }

    private static ArgumentBuilder<CommandSourceStack, ?> position(int index, Command<CommandSourceStack> command) {
        return chain(POS, index, command);
    }

    private static ArgumentBuilder<CommandSourceStack, ?> chain(String[] names, int index, Command<CommandSourceStack> command) {
        if (index == names.length - 1) {
            return Commands.argument(names[index], com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                    .executes(command);
        }
        return Commands.argument(names[index], com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                .then(chain(names, index + 1, command));
    }

    private static int intArg(CommandContext<CommandSourceStack> ctx, String name) {
        return com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, name);
    }

    private static String stringArg(CommandContext<CommandSourceStack> ctx, String name) {
        return com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, name);
    }

    private static void send(CommandContext<CommandSourceStack> ctx, String message) {
        ctx.getSource().sendSuccess(() -> Component.literal(message), false);
    }

    private static void fail(CommandContext<CommandSourceStack> ctx, String message) {
        ctx.getSource().sendFailure(Component.literal(message));
    }
}
