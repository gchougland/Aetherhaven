package com.hexvane.aetherhaven.shopspot;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.world.PersistentWorldSupport;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import java.io.IOException;
import java.nio.file.Path;
import javax.annotation.Nonnull;

public final class ShopSpotPersistence {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    public static final String SHOP_SPOTS_FILE_NAME = "shop_spots.json";

    private ShopSpotPersistence() {}

    @Nonnull
    public static Path spotsFile(@Nonnull AetherhavenPlugin plugin, @Nonnull String worldName) {
        return TownManager.pluginData(plugin)
            .resolve("worlds")
            .resolve(sanitizeWorldDirName(worldName))
            .resolve(SHOP_SPOTS_FILE_NAME);
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

    public static void load(@Nonnull World world, @Nonnull AetherhavenPlugin plugin, @Nonnull ShopSpotRegistry registry) {
        if (!PersistentWorldSupport.shouldPersistWorldData(world)) {
            registry.replaceAll(java.util.List.of());
            return;
        }
        Path path = spotsFile(plugin, world.getName());
        try {
            loadFrom(path, registry);
            LOGGER.atInfo().log("Aetherhaven loaded %s shop spots for world %s", registry.allRecords().size(), world.getName());
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load shop spots for world %s", world.getName());
        }
    }

    /** Everything a world load reads from its stall file, from any path. Stalls of other worlds are left out. */
    public static void loadFrom(@Nonnull Path path, @Nonnull ShopSpotRegistry registry) throws IOException {
        ShopSpotWorldFile file = ShopSpotWorldFile.readOrEmpty(path);
        registry.replaceAll(ShopSpotWorldFile.toRecords(file), ShopSpotWorldFile.toHeldReturns(file));
    }

    /** Everything a world save writes to its stall file, to any path. */
    public static void saveTo(@Nonnull Path path, @Nonnull ShopSpotRegistry registry) throws IOException {
        ShopSpotWorldFile.fromRegistry(registry).writeAtomic(path);
    }

    public static void save(@Nonnull World world, @Nonnull AetherhavenPlugin plugin, @Nonnull ShopSpotRegistry registry) {
        if (!PersistentWorldSupport.shouldPersistWorldData(world)) {
            return;
        }
        Path path = spotsFile(plugin, world.getName());
        try {
            saveTo(path, registry);
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to save shop spots for world %s", world.getName());
        }
    }
}
