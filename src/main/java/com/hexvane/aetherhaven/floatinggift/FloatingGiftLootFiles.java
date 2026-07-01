package com.hexvane.aetherhaven.floatinggift;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.annotation.Nonnull;

public final class FloatingGiftLootFiles {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    public static final String FILE_NAME = "floating_gift_loot.json";
    public static final int CURRENT_LOOT_FILE_VERSION = 2;
    private static final String DEFAULT_RESOURCE = "/defaults/floating_gift_loot.json";

    private FloatingGiftLootFiles() {}

    @Nonnull
    public static Path lootPath(@Nonnull AetherhavenPlugin plugin) {
        return plugin.getDataDirectory().resolve(FILE_NAME);
    }

    @Nonnull
    public static String readDefaultJson() throws IOException {
        try (InputStream in = FloatingGiftLootFiles.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (in == null) {
                LOGGER
                    .atWarning()
                    .log(
                        "Missing bundled default %s on classpath; using empty loot table stub",
                        DEFAULT_RESOURCE
                    );
                return "{\"version\":" + CURRENT_LOOT_FILE_VERSION + ",\"entries\":[]}";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Writes the bundled default when the file is missing, or replaces it when {@code version} is absent or lower than
     * {@link #CURRENT_LOOT_FILE_VERSION}.
     */
    public static void ensureDefaultLootFile(@Nonnull AetherhavenPlugin plugin) {
        Path path = lootPath(plugin);
        try {
            Files.createDirectories(plugin.getDataDirectory());
            String bundled = readDefaultJson();
            boolean existed = Files.isRegularFile(path);
            if (!existed || needsRegeneration(path)) {
                Files.writeString(path, bundled, StandardCharsets.UTF_8);
                if (existed) {
                    LOGGER
                        .atInfo()
                        .log("Regenerated %s to version %d", FILE_NAME, CURRENT_LOOT_FILE_VERSION);
                }
            }
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to ensure %s", FILE_NAME);
        }
    }

    private static boolean needsRegeneration(@Nonnull Path path) throws IOException {
        String onDisk = Files.readString(path, StandardCharsets.UTF_8);
        return readVersion(onDisk) < CURRENT_LOOT_FILE_VERSION;
    }

    /** Missing or invalid version is treated as 0 (needs upgrade). */
    static int readVersion(@Nonnull String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                return 0;
            }
            JsonObject obj = root.getAsJsonObject();
            if (!obj.has("version")) {
                return 0;
            }
            return obj.get("version").getAsInt();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    @Nonnull
    public static FloatingGiftLootTable loadTable(@Nonnull AetherhavenPlugin plugin) {
        return loadBundle(plugin).tableFor(FloatingGiftType.REGULAR);
    }

    @Nonnull
    public static FloatingGiftLootBundle loadBundle(@Nonnull AetherhavenPlugin plugin) {
        FloatingGiftLootBundle parsed;
        try {
            parsed = FloatingGiftLootBundle.loadFromFile(lootPath(plugin), readDefaultJson());
        } catch (IOException e) {
            try {
                parsed = FloatingGiftLootBundle.parseJson(readDefaultJson());
            } catch (IOException e2) {
                parsed = FloatingGiftLootBundle.empty();
            }
        }
        ConstructionCatalog catalog = plugin.getConstructionCatalog();
        FloatingGiftLootTable regular =
            FloatingGiftBlueprintLoot.mergeIntoRegularTable(
                parsed.tableFor(FloatingGiftType.REGULAR),
                catalog,
                parsed.getRegularPlotBlueprintWeight()
            );
        return parsed.withRegularTable(regular);
    }
}
