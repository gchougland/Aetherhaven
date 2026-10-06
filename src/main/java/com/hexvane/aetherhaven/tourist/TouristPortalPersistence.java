package com.hexvane.aetherhaven.tourist;

import com.google.gson.JsonParseException;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.world.PersistentWorldSupport;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import java.io.IOException;
import java.nio.file.Path;
import javax.annotation.Nonnull;

public final class TouristPortalPersistence {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    public static final String TOURIST_PORTALS_FILE_NAME = "tourist_portals.json";

    private TouristPortalPersistence() {}

    @Nonnull
    public static Path portalsFile(@Nonnull AetherhavenPlugin plugin, @Nonnull String worldName) {
        return TownManager.pluginData(plugin)
            .resolve("worlds")
            .resolve(sanitizeWorldDirName(worldName))
            .resolve(TOURIST_PORTALS_FILE_NAME);
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

    public static void load(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TouristPortalRegistry registry
    ) {
        if (!PersistentWorldSupport.shouldPersistWorldData(world)) {
            registry.replaceAll(java.util.List.of());
            return;
        }
        Path path = portalsFile(plugin, world.getName());
        load(path, world.getName(), registry);
    }

    static void load(@Nonnull Path path, @Nonnull String worldName, @Nonnull TouristPortalRegistry registry) {
        registry.setPersistenceBlocked(true);
        try {
            TouristPortalWorldFile file = TouristPortalWorldFile.readOrEmpty(path);
            registry.replaceAll(TouristPortalWorldFile.toRecords(file));
            registry.setPersistenceBlocked(false);
            LOGGER.atInfo().log(
                "Aetherhaven loaded %s tourist portal(s) for world %s",
                registry.allRecords().size(),
                worldName
            );
        } catch (IOException | JsonParseException e) {
            LOGGER.atSevere().withCause(e).log(
                "Failed to load tourist portals from %s for world %s. World loading will continue, but tourist portal saving is disabled to preserve the existing file. Repair or restore the file and restart the world.",
                path, worldName
            );
        }
    }

    public static void save(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TouristPortalRegistry registry
    ) {
        if (!PersistentWorldSupport.shouldPersistWorldData(world)) {
            return;
        }
        Path path = portalsFile(plugin, world.getName());
        save(path, world.getName(), registry);
    }

    static void save(@Nonnull Path path, @Nonnull String worldName, @Nonnull TouristPortalRegistry registry) {
        if (registry.isPersistenceBlocked()) {
            return;
        }
        try {
            TouristPortalWorldFile file = TouristPortalWorldFile.fromRecords(registry.allRecords());
            file.writeAtomic(path);
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to save tourist portals for world %s", worldName);
        }
    }
}
