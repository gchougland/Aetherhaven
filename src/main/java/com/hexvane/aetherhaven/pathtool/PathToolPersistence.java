package com.hexvane.aetherhaven.pathtool;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.TownManager;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import java.io.IOException;
import java.nio.file.Path;
import javax.annotation.Nonnull;

public final class PathToolPersistence {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private PathToolPersistence() {}

    @Nonnull
    private static Path pathFile(@Nonnull AetherhavenPlugin plugin, @Nonnull String worldName) {
        return TownManager.pluginData(plugin)
            .resolve("worlds")
            .resolve(sanitizeWorldDirName(worldName))
            .resolve("path_commits.json");
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

    public static void load(@Nonnull World world, @Nonnull AetherhavenPlugin plugin, @Nonnull PathToolRegistry registry) {
        Path p = pathFile(plugin, world.getName());
        try {
            PathToolWorldFile f = PathToolWorldFile.readOrEmpty(p);
            registry.replaceAll(f.getPaths());
            LOGGER.atInfo().log("Aetherhaven loaded %s path commits for world %s from %s", registry.all().size(), world.getName(), p);
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load path commits for world %s", world.getName());
        }
    }

    public static void save(@Nonnull World world, @Nonnull AetherhavenPlugin plugin, @Nonnull PathToolRegistry registry) {
        Path p = pathFile(plugin, world.getName());
        try {
            PathToolWorldFile f = new PathToolWorldFile();
            f.getPaths().addAll(registry.all());
            f.writeAtomic(p);
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to save path commits for world %s", world.getName());
        }
    }
}
