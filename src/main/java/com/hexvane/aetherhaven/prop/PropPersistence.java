package com.hexvane.aetherhaven.prop;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.world.PersistentWorldSupport;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.annotation.Nonnull;

/** Loads and saves {@link PropRegistry} to {@code worlds/<sanitized>/props.json}. */
public final class PropPersistence {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private PropPersistence() {}

    @Nonnull
    private static Path propsFile(@Nonnull AetherhavenPlugin plugin, @Nonnull String worldName) {
        return TownManager.pluginData(plugin)
            .resolve("worlds")
            .resolve(sanitizeWorldDirName(worldName))
            .resolve("props.json");
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

    public static void load(@Nonnull World world, @Nonnull AetherhavenPlugin plugin, @Nonnull PropRegistry registry) {
        if (!PersistentWorldSupport.shouldPersistWorldData(world)) {
            registry.replaceAll(List.of());
            return;
        }
        Path path = propsFile(plugin, world.getName());
        try {
            PropWorldFile file = PropWorldFile.readOrEmpty(path);
            List<PropInstance> loaded = PropWorldFile.toInstances(file);
            registry.replaceAll(loaded);
            LOGGER.atInfo().log("Aetherhaven loaded %s prop(s) for world %s from %s", loaded.size(), world.getName(), path);
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load props for world %s", world.getName());
        }
    }

    public static void save(@Nonnull World world, @Nonnull AetherhavenPlugin plugin, @Nonnull PropRegistry registry) {
        if (!PersistentWorldSupport.shouldPersistWorldData(world)) {
            return;
        }
        Path path = propsFile(plugin, world.getName());
        try {
            PropWorldFile file = PropWorldFile.fromInstances(registry.all());
            file.writeAtomic(path);
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to save props for world %s", world.getName());
        }
    }
}
