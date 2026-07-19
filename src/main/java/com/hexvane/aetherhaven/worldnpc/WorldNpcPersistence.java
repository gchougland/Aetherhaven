package com.hexvane.aetherhaven.worldnpc;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.world.PersistentWorldSupport;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import javax.annotation.Nonnull;

public final class WorldNpcPersistence {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    public static final String PLACEMENTS_FILE = "world_npcs.json";
    public static final String ROUTES_FILE = "world_npc_routes.json";
    public static final String PLAYERS_FILE = "world_npc_players.json";

    private WorldNpcPersistence() {}

    @Nonnull
    private static Path worldDir(@Nonnull AetherhavenPlugin plugin, @Nonnull String worldName) {
        return TownManager.pluginData(plugin)
            .resolve("worlds")
            .resolve(sanitizeWorldDirName(worldName));
    }

    @Nonnull
    static String sanitizeWorldDirName(@Nonnull String worldName) {
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

    public static void load(@Nonnull World world, @Nonnull AetherhavenPlugin plugin, @Nonnull WorldNpcRegistry registry) {
        if (!PersistentWorldSupport.shouldPersistWorldData(world)) {
            registry.replaceAll(java.util.List.of(), java.util.List.of(), java.util.List.of());
            return;
        }
        Path dir = worldDir(plugin, world.getName());
        try {
            WorldNpcsWorldFile placements = WorldNpcsWorldFile.readOrEmpty(dir.resolve(PLACEMENTS_FILE));
            WorldNpcRoutesWorldFile routes = WorldNpcRoutesWorldFile.readOrEmpty(dir.resolve(ROUTES_FILE));
            WorldNpcPlayersWorldFile players = WorldNpcPlayersWorldFile.readOrEmpty(dir.resolve(PLAYERS_FILE));
            registry.replaceAll(placements.getPlacements(), routes.getRoutes(), players.getPlayers());
            LOGGER.atInfo().log(
                "Aetherhaven loaded %s world NPC placements, %s routes, %s player rows for world %s",
                registry.allPlacements().size(),
                registry.allRoutes().size(),
                registry.playerCount(),
                world.getName()
            );
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load world NPCs for world %s", world.getName());
        }
    }

    public static void save(@Nonnull World world, @Nonnull AetherhavenPlugin plugin, @Nonnull WorldNpcRegistry registry) {
        if (!PersistentWorldSupport.shouldPersistWorldData(world)) {
            return;
        }
        Path dir = worldDir(plugin, world.getName());
        try {
            WorldNpcsWorldFile placements = new WorldNpcsWorldFile();
            placements.getPlacements().addAll(registry.allPlacements());
            placements.writeAtomic(dir.resolve(PLACEMENTS_FILE));

            WorldNpcRoutesWorldFile routes = new WorldNpcRoutesWorldFile();
            routes.getRoutes().addAll(registry.allRoutes());
            routes.writeAtomic(dir.resolve(ROUTES_FILE));

            WorldNpcPlayersWorldFile players = new WorldNpcPlayersWorldFile();
            for (UUID uuid : registry.allPlayerUuids()) {
                WorldNpcPlayerProgress p = registry.findPlayerProgress(uuid);
                if (p != null) {
                    players.getPlayers().add(p);
                }
            }
            players.writeAtomic(dir.resolve(PLAYERS_FILE));
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to save world NPCs for world %s", world.getName());
        }
    }
}
