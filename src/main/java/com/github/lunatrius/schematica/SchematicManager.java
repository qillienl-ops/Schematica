package com.github.lunatrius.schematica;

import com.github.lunatrius.core.util.FileUtils;
import com.github.lunatrius.schematica.api.ISchematic;
import com.github.lunatrius.schematica.world.schematic.SchematicFormats;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Filesystem access to the schematics directory. */
public final class SchematicManager {
    private SchematicManager() {}

    public static Path directory() {
        String configured = Config.SCHEMATIC_DIRECTORY.get();
        Path path = Path.of(configured);
        return path.isAbsolute() ? path : Path.of("").toAbsolutePath().resolve(path);
    }

    public static Path resolve(String name) throws IOException {
        String safe = FileUtils.safeFileName(name);
        if (safe == null || safe.isEmpty() || safe.contains("..")
                || safe.contains("/") || safe.contains("\\")) {
            throw new IOException("Invalid schematic name: " + name);
        }
        Path directory = directory();
        Files.createDirectories(directory);
        return directory.resolve(safe);
    }

    public static List<String> list() {
        List<String> names = new ArrayList<>();
        Path directory = directory();
        if (!Files.isDirectory(directory)) {
            return names;
        }
        try (var stream = Files.list(directory)) {
            stream.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".schematic") || n.endsWith(".nbt"))
                    .sorted(Comparator.naturalOrder())
                    .forEach(names::add);
        } catch (IOException e) {
            Schematica.LOGGER.warn("Could not list schematics in {}", directory, e);
        }
        return names;
    }

    /** Resolves a name, adding the default extension when the user omitted it. */
    private static Path existingPath(String name) throws IOException {
        Path path = resolve(name);
        if (Files.exists(path) || name.endsWith(".schematic") || name.endsWith(".nbt")) {
            return path;
        }
        Path schematic = resolve(name + ".schematic");
        if (Files.exists(schematic)) {
            return schematic;
        }
        Path structure = resolve(name + ".nbt");
        return Files.exists(structure) ? structure : path;
    }

    public static ISchematic load(String name) throws IOException {
        return SchematicFormats.read(existingPath(name));
    }

    public static void save(String name, ISchematic schematic) throws IOException {
        SchematicFormats.write(resolve(name), schematic, SchematicFormats.FORMAT_ALPHA);
    }

    public static boolean delete(String name) throws IOException {
        return Files.deleteIfExists(existingPath(name));
    }

    public static String sizeDescription(String name) {
        try {
            return FileUtils.humanReadableByteCount(Files.size(resolve(name)));
        } catch (IOException e) {
            return "?";
        }
    }
}
