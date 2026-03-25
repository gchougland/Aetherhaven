package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Per-world town index + persistence under the plugin data directory:
 * {@code worlds/<worldName>/towns.json}.
 */
public final class TownManager {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final World world;
    private final Path saveFile;
    private final Map<UUID, TownRecord> byTownId = new LinkedHashMap<>();

    public TownManager(@Nonnull World world, @Nonnull Path pluginDataDirectory) {
        this.world = world;
        String name = sanitizeWorldDirName(world.getName());
        this.saveFile = pluginDataDirectory.resolve("worlds").resolve(name).resolve("towns.json");
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

    public void loadFromDisk() {
        byTownId.clear();
        try {
            TownWorldFile file = TownWorldFile.readOrEmpty(saveFile);
            for (TownRecord t : file.getTowns()) {
                t.migrateLegacyPlotFootprintsIfNeeded();
                byTownId.put(t.getTownId(), t);
            }
            LOGGER.atInfo().log("Aetherhaven loaded %s towns for world %s from %s", byTownId.size(), world.getName(), saveFile);
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load towns for world %s", world.getName());
        }
    }

    public void saveToDisk() {
        try {
            TownWorldFile file = new TownWorldFile();
            file.getTowns().addAll(byTownId.values());
            file.writeAtomic(saveFile);
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to save towns for world %s", world.getName());
        }
    }

    @Nullable
    public TownRecord getTown(@Nonnull UUID townId) {
        return byTownId.get(townId);
    }

    @Nullable
    public TownRecord findTownForOwnerInWorld(@Nonnull UUID ownerUuid) {
        for (TownRecord t : byTownId.values()) {
            if (t.getOwnerUuid().equals(ownerUuid) && world.getName().equals(t.getWorldName())) {
                return t;
            }
        }
        return null;
    }

    @Nullable
    public TownRecord findTownContainingChunk(int chunkX, int chunkZ, @Nonnull UUID ownerUuid) {
        TownRecord owned = findTownForOwnerInWorld(ownerUuid);
        if (owned == null) {
            return null;
        }
        int cx = ChunkUtil.chunkCoordinate(owned.getCharterX());
        int cz = ChunkUtil.chunkCoordinate(owned.getCharterZ());
        int r = owned.getTerritoryChunkRadius();
        if (Math.abs(chunkX - cx) > r || Math.abs(chunkZ - cz) > r) {
            return null;
        }
        return owned;
    }

    /** True if block position is inside the town's axis-aligned chunk square around the charter. */
    public boolean isInsideTerritory(@Nonnull TownRecord town, int blockX, int blockZ) {
        int cx = ChunkUtil.chunkCoordinate(town.getCharterX());
        int cz = ChunkUtil.chunkCoordinate(town.getCharterZ());
        int bx = ChunkUtil.chunkCoordinate(blockX);
        int bz = ChunkUtil.chunkCoordinate(blockZ);
        int r = town.getTerritoryChunkRadius();
        return Math.abs(bx - cx) <= r && Math.abs(bz - cz) <= r;
    }

    public void putTown(@Nonnull TownRecord record) {
        byTownId.put(record.getTownId(), record);
        saveToDisk();
    }

    public void updateTown(@Nonnull TownRecord record) {
        byTownId.put(record.getTownId(), record);
        saveToDisk();
    }

    @Nonnull
    public List<TownRecord> allTowns() {
        return new ArrayList<>(byTownId.values());
    }

    public static int defaultTerritoryRadiusChunks(@Nonnull AetherhavenPluginConfig cfg) {
        return cfg.getDefaultTerritoryChunkRadius();
    }

    @Nonnull
    public static Path pluginData(@Nonnull AetherhavenPlugin plugin) {
        return plugin.getDataDirectory();
    }
}
