package com.hexvane.aetherhaven.poi;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.world.PersistentWorldSupport;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import java.io.IOException;
import java.nio.file.Path;
import javax.annotation.Nonnull;

/** Loads and saves {@link PoiRegistry} to {@code worlds/<sanitized>/pois.json}. */
public final class PoiPersistence {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private PoiPersistence() {}

    @Nonnull
    private static Path poisFile(@Nonnull AetherhavenPlugin plugin, @Nonnull String worldName) {
        return TownManager.pluginData(plugin)
            .resolve("worlds")
            .resolve(sanitizeWorldDirName(worldName))
            .resolve("pois.json");
    }

    @Nonnull
    private static String sanitizeWorldDirName(@Nonnull String worldName) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < worldName.length(); i++) {
            char c = worldName.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return sb.isEmpty() ? "world" : sb.toString();
    }

    public static void load(@Nonnull World world, @Nonnull AetherhavenPlugin plugin, @Nonnull PoiRegistry registry) {
        if (!PersistentWorldSupport.shouldPersistWorldData(world)) {
            registry.replaceAll(java.util.List.of());
            return;
        }
        Path path = poisFile(plugin, world.getName());
        try {
            PoiWorldFile file = PoiWorldFile.readOrEmpty(path);
            java.util.List<PoiEntry> loaded = PoiWorldFile.toEntries(file);
            java.util.List<PoiEntry> migrated = ShopBrowsePoiMigration.migrate(loaded);
            registry.replaceAll(migrated);
            if (migrated != loaded) {
                save(world, plugin, registry);
                LOGGER.atInfo().log(
                    "Aetherhaven migrated shop browse POIs for world %s (%s -> %s entries)",
                    world.getName(),
                    loaded.size(),
                    migrated.size()
                );
            }
            LOGGER.atInfo().log("Aetherhaven loaded %s POIs for world %s from %s", registry.allEntries().size(), world.getName(), path);
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load POIs for world %s", world.getName());
        }
    }

    public static void save(@Nonnull World world, @Nonnull AetherhavenPlugin plugin, @Nonnull PoiRegistry registry) {
        if (!PersistentWorldSupport.shouldPersistWorldData(world)) {
            return;
        }
        Path path = poisFile(plugin, world.getName());
        try {
            PoiWorldFile file = PoiWorldFile.fromEntries(registry.allPersistentEntries());
            file.writeAtomic(path);
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to save POIs for world %s", world.getName());
        }
    }
}
