package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.production.ProductionCatalog;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
                t.migrateInnFieldsIfNeeded();
                t.migrateTownSocialFieldsIfNeeded();
                byTownId.put(t.getTownId(), t);
            }
            dedupeDisplayNamesAfterLoad();
            LOGGER.atInfo().log("Aetherhaven loaded %s towns for world %s from %s", byTownId.size(), world.getName(), saveFile);
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load towns for world %s", world.getName());
        }
    }

    /**
     * After {@link #loadFromDisk()}, applies per-output storage caps from the production catalog and persists if
     * anything changed.
     */
    public void clampAllPlotProductionToCatalog(@Nonnull ProductionCatalog catalog) {
        boolean any = false;
        for (TownRecord t : byTownId.values()) {
            if (t.clampPlotProductionToCatalog(catalog)) {
                any = true;
            }
        }
        if (any) {
            saveToDisk();
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

    /** Removes a town from this world's index and saves. @return true if a town was removed */
    public boolean removeTown(@Nonnull UUID townId) {
        TownRecord removed = byTownId.remove(townId);
        if (removed == null) {
            return false;
        }
        saveToDisk();
        return true;
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

    /**
     * Town this player belongs to in this world: as owner or as a listed member (one affiliation per world).
     */
    @Nullable
    public TownRecord findTownForPlayerInWorld(@Nonnull UUID playerUuid) {
        for (TownRecord t : byTownId.values()) {
            if (!world.getName().equals(t.getWorldName())) {
                continue;
            }
            if (t.getOwnerUuid().equals(playerUuid)) {
                return t;
            }
            if (t.isMemberPlayer(playerUuid)) {
                return t;
            }
        }
        return null;
    }

    /** True if the player is owner or member of any town in this world. */
    public boolean isPlayerAffiliatedInWorld(@Nonnull UUID playerUuid) {
        return findTownForPlayerInWorld(playerUuid) != null;
    }

    @Nullable
    public TownRecord findTownOwningPlot(@Nonnull UUID plotId) {
        for (TownRecord t : byTownId.values()) {
            if (t.findPlotById(plotId) != null) {
                return t;
            }
        }
        return null;
    }

    @Nullable
    public TownRecord findTownWithPendingInviteFor(@Nonnull UUID inviteeUuid) {
        for (TownRecord t : byTownId.values()) {
            if (!world.getName().equals(t.getWorldName())) {
                continue;
            }
            if (t.findPendingInvite(inviteeUuid) != null) {
                return t;
            }
        }
        return null;
    }

    @Nullable
    public TownRecord findTownByDisplayName(@Nonnull String displayName) {
        String want = displayName.trim();
        if (want.isEmpty()) {
            return null;
        }
        String lower = want.toLowerCase(Locale.ROOT);
        for (TownRecord t : byTownId.values()) {
            if (!world.getName().equals(t.getWorldName())) {
                continue;
            }
            if (t.getDisplayName().toLowerCase(Locale.ROOT).equals(lower)) {
                return t;
            }
        }
        return null;
    }

    public boolean isDisplayNameAvailable(@Nonnull String displayName, @Nullable UUID excludeTownId) {
        String want = displayName.trim();
        if (want.isEmpty()) {
            return false;
        }
        String lower = want.toLowerCase(Locale.ROOT);
        for (TownRecord t : byTownId.values()) {
            if (!world.getName().equals(t.getWorldName())) {
                continue;
            }
            if (excludeTownId != null && t.getTownId().equals(excludeTownId)) {
                continue;
            }
            if (t.getDisplayName().toLowerCase(Locale.ROOT).equals(lower)) {
                return false;
            }
        }
        return true;
    }

    private void ensureDisplayNameUnique(@Nonnull TownRecord record) {
        if (!isDisplayNameAvailable(record.getDisplayName(), record.getTownId())) {
            throw new IllegalArgumentException("That town name is already used in this world.");
        }
    }

    /**
     * If saves ever contain two towns with the same display name (case-insensitive), rename duplicates so
     * {@link #findTownByDisplayName} and invite/command targeting stay unambiguous.
     */
    private void dedupeDisplayNamesAfterLoad() {
        List<TownRecord> inWorld = new ArrayList<>();
        for (TownRecord t : byTownId.values()) {
            if (world.getName().equals(t.getWorldName())) {
                inWorld.add(t);
            }
        }
        Map<String, List<TownRecord>> byLower = new HashMap<>();
        for (TownRecord t : inWorld) {
            String key = t.getDisplayName().toLowerCase(Locale.ROOT);
            byLower.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
        }
        boolean changed = false;
        for (List<TownRecord> group : byLower.values()) {
            if (group.size() <= 1) {
                continue;
            }
            group.sort(Comparator.comparing(TownRecord::getTownId));
            for (int i = 1; i < group.size(); i++) {
                TownRecord t = group.get(i);
                String base = t.getDisplayName().trim();
                if (base.isEmpty()) {
                    base = "Town";
                }
                boolean renamed = false;
                for (int suffix = 2; suffix < 1_000_000; suffix++) {
                    String candidate = base + " (" + suffix + ")";
                    if (isDisplayNameAvailable(candidate, t.getTownId())) {
                        LOGGER.atWarning().log(
                            "Renamed duplicate town display name to \"%s\" (town id %s) in world %s",
                            candidate,
                            t.getTownId(),
                            world.getName()
                        );
                        t.setDisplayName(candidate);
                        changed = true;
                        renamed = true;
                        break;
                    }
                }
                if (!renamed) {
                    String fallback = "Town " + t.getTownId();
                    LOGGER.atSevere().log(
                        "Could not find a free duplicate suffix for town in world %s; using unique id-based name",
                        world.getName()
                    );
                    t.setDisplayName(fallback);
                    changed = true;
                }
            }
        }
        if (changed) {
            saveToDisk();
        }
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

    /**
     * First town in this world whose territory contains the block column. Used when multiple towns could exist per
     * world (rare); deterministic order follows {@link #allTowns()}.
     */
    @Nullable
    public TownRecord findTownContainingBlock(@Nonnull String worldName, int blockX, int blockZ) {
        for (TownRecord t : allTowns()) {
            if (!worldName.equals(t.getWorldName())) {
                continue;
            }
            if (isInsideTerritory(t, blockX, blockZ)) {
                return t;
            }
        }
        return null;
    }

    /**
     * True if every registered plot footprint would still lie inside the territory when the charter were moved to
     * {@code charterBlockX}/{@code charterBlockZ} (territory is the chunk-radius square centered on the charter).
     */
    public boolean allPlotFootprintsFitTerritoryWithCharterAt(@Nonnull TownRecord town, int charterBlockX, int charterBlockZ) {
        int ccx = ChunkUtil.chunkCoordinate(charterBlockX);
        int ccz = ChunkUtil.chunkCoordinate(charterBlockZ);
        int r = town.getTerritoryChunkRadius();
        for (PlotInstance plot : town.getPlotInstances()) {
            PlotFootprintRecord fp = plot.toFootprint();
            for (int x = fp.getMinX(); x <= fp.getMaxX(); x++) {
                for (int z = fp.getMinZ(); z <= fp.getMaxZ(); z++) {
                    int bx = ChunkUtil.chunkCoordinate(x);
                    int bz = ChunkUtil.chunkCoordinate(z);
                    if (Math.abs(bx - ccx) > r || Math.abs(bz - ccz) > r) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public void putTown(@Nonnull TownRecord record) {
        record.migrateTownSocialFieldsIfNeeded();
        record.migrateFounderMonumentCountIfNeeded();
        ensureDisplayNameUnique(record);
        byTownId.put(record.getTownId(), record);
        saveToDisk();
    }

    public void updateTown(@Nonnull TownRecord record) {
        record.migrateTownSocialFieldsIfNeeded();
        record.migrateFounderMonumentCountIfNeeded();
        ensureDisplayNameUnique(record);
        byTownId.put(record.getTownId(), record);
        saveToDisk();
    }

    /** Rename without throwing; caller shows message on failure. */
    public boolean trySetDisplayName(@Nonnull TownRecord record, @Nonnull String newName) {
        String trimmed = newName.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        String old = record.getDisplayName();
        if (trimmed.toLowerCase(Locale.ROOT).equals(old.toLowerCase(Locale.ROOT))) {
            if (!trimmed.equals(old)) {
                record.setDisplayName(trimmed);
                updateTown(record);
            }
            return true;
        }
        record.setDisplayName(trimmed);
        if (!isDisplayNameAvailable(record.getDisplayName(), record.getTownId())) {
            record.setDisplayName(old);
            return false;
        }
        updateTown(record);
        return true;
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
