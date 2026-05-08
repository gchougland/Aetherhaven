package com.hexvane.aetherhaven.floatinggift;

import com.hexvane.aetherhaven.AetherhavenPlugin;
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
                return "{\"entries\":[]}";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static void ensureDefaultLootFile(@Nonnull AetherhavenPlugin plugin) {
        Path path = lootPath(plugin);
        if (Files.isRegularFile(path)) {
            return;
        }
        try {
            Files.createDirectories(plugin.getDataDirectory());
            Files.writeString(path, readDefaultJson(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to write default %s", FILE_NAME);
        }
    }

    @Nonnull
    public static FloatingGiftLootTable loadTable(@Nonnull AetherhavenPlugin plugin) {
        try {
            return FloatingGiftLootTable.loadFromFile(lootPath(plugin), readDefaultJson());
        } catch (IOException e) {
            try {
                return FloatingGiftLootTable.parseJson(readDefaultJson());
            } catch (IOException e2) {
                return FloatingGiftLootTable.empty();
            }
        }
    }
}
