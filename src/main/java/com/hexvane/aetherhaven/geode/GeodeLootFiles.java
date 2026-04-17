package com.hexvane.aetherhaven.geode;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.annotation.Nonnull;

public final class GeodeLootFiles {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    public static final String GEODE_LOOT_FILE_NAME = "geode_loot.json";
    private static final String DEFAULT_RESOURCE = "/defaults/geode_loot.json";

    private GeodeLootFiles() {}

    @Nonnull
    public static Path lootPath(@Nonnull AetherhavenPlugin plugin) {
        return plugin.getDataDirectory().resolve(GEODE_LOOT_FILE_NAME);
    }

    @Nonnull
    public static String readDefaultJson() throws IOException {
        try (InputStream in = GeodeLootFiles.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (in == null) {
                return "{\"entries\":[]}";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Writes the embedded default next to {@code config.json} if the file is missing. */
    public static void ensureDefaultLootFile(@Nonnull AetherhavenPlugin plugin) {
        Path path = lootPath(plugin);
        if (Files.isRegularFile(path)) {
            return;
        }
        try {
            Files.createDirectories(plugin.getDataDirectory());
            String json = readDefaultJson();
            Files.writeString(path, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to write default %s", GEODE_LOOT_FILE_NAME);
        }
    }

    @Nonnull
    public static GeodeLootTable loadTable(@Nonnull AetherhavenPlugin plugin) {
        try {
            return GeodeLootTable.loadFromFile(lootPath(plugin), readDefaultJson());
        } catch (IOException e) {
            try {
                return GeodeLootTable.parseJson(readDefaultJson());
            } catch (IOException e2) {
                return GeodeLootTable.empty();
            }
        }
    }
}
