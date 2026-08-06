package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.world.PersistentWorldSupport;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.production.ProductionCatalog;
import com.hexvane.aetherhaven.production.WorkplaceUnlockCatalog;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import it.unimi.dsi.fastutil.longs.LongSet;
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
    private long lastLoadedFromDiskMs;
    private long lastSavedToDiskMs;
    private boolean dirty;

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
        if (!PersistentWorldSupport.shouldPersistWorldData(world)) {
            byTownId.clear();
            return;
        }
        try {
            TownWorldFile file = readTownFileWithBackupFallback(saveFile);
            Map<UUID, TownRecord> loaded = new LinkedHashMap<>();
            for (TownRecord t : file.getTowns()) {
                t.migrateLegacyPlotFootprintsIfNeeded();
                t.migrateInnFieldsIfNeeded();
                t.migrateTownSocialFieldsIfNeeded();
                t.migrateTerritoryClaimsIfNeeded();
                loaded.put(t.getTownId(), t);
            }
            byTownId.clear();
            byTownId.putAll(loaded);
            dedupeDisplayNamesAfterLoad();
            lastLoadedFromDiskMs = System.currentTimeMillis();
            LOGGER.atInfo().log("Aetherhaven loaded %s towns for world %s from %s", byTownId.size(), world.getName(), saveFile);
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load towns for world %s; keeping in-memory data", world.getName());
        }
    }

    @Nonnull
    private static TownWorldFile readTownFileWithBackupFallback(@Nonnull Path path) throws IOException {
        try {
            return TownWorldFile.readOrEmpty(path);
        } catch (IOException primary) {
            Path bak = path.resolveSibling("towns.json.bak");
            if (Files.isRegularFile(bak)) {
                LOGGER.atWarning().withCause(primary).log("Falling back to towns.json.bak for %s", path);
                return TownWorldFile.readOrEmpty(bak);
            }
            throw primary;
        }
    }

    public long getLastLoadedFromDiskMs() {
        return lastLoadedFromDiskMs;
    }

    public long getLastSavedToDiskMs() {
        return lastSavedToDiskMs;
    }

    public long getSaveFileLastModifiedMs() {
        try {
            if (Files.isRegularFile(saveFile)) {
                return Files.getLastModifiedTime(saveFile).toMillis();
            }
        } catch (IOException ignored) {
        }
        return 0L;
    }

    public int countAllPlotInstances() {
        int n = 0;
        for (TownRecord t : byTownId.values()) {
            n += t.getPlotInstances().size();
        }
        return n;
    }

    /**
     * After {@link #loadFromDisk()}, applies per-output storage caps from the production catalog and persists if
     * anything changed.
     */
    public void clampAllPlotProductionToCatalog(
        @Nonnull ProductionCatalog catalog,
        @Nonnull WorkplaceUnlockCatalog unlockCatalog,
        @Nonnull ConstructionCatalog constructionCatalog
    ) {
        boolean any = false;
        for (TownRecord t : byTownId.values()) {
            if (t.clampPlotProductionToCatalog(catalog, unlockCatalog, constructionCatalog)) {
                any = true;
            }
        }
        if (any) {
            TownSaveCoordinator.requestSave(this);
        }
    }

    public void saveToDisk() {
        TownSaveCoordinator.flushSync(this);
    }

    @Nonnull
    String getWorldName() {
        return world.getName();
    }

    boolean shouldPersist() {
        return PersistentWorldSupport.shouldPersistWorldData(world);
    }

    @Nonnull
    Path getSaveFilePath() {
        return saveFile;
    }

    @Nonnull
    List<TownRecord> snapshotTownsForSave() {
        return new ArrayList<>(byTownId.values());
    }

    void notifySavedToDisk(long savedAtMs) {
        lastSavedToDiskMs = savedAtMs;
        dirty = false;
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
        TownSaveCoordinator.requestSave(this);
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
     * First town this player belongs to in this world (owner or member). Prefer {@link TownPlayerResolution}
     * when the player may have multiple affiliations.
     */
    @Nullable
    public TownRecord findTownForPlayerInWorld(@Nonnull UUID playerUuid) {
        List<TownRecord> all = findAllTownsForPlayerInWorld(playerUuid);
        return all.isEmpty() ? null : all.get(0);
    }

    /** All towns in this world where the player is owner or listed member, stable display-name order. */
    @Nonnull
    public List<TownRecord> findAllTownsForPlayerInWorld(@Nonnull UUID playerUuid) {
        List<TownRecord> out = new ArrayList<>();
        for (TownRecord t : byTownId.values()) {
            if (!world.getName().equals(t.getWorldName())) {
                continue;
            }
            if (t.getOwnerUuid().equals(playerUuid) || t.isMemberPlayer(playerUuid)) {
                out.add(t);
            }
        }
        out.sort(TownPlayerResolution.affiliatedTownDisplayOrder());
        return out;
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

    /** Town UUID string or case-insensitive display name in this world. */
    @Nullable
    public TownRecord findTownByIdOrDisplayName(@Nonnull String raw) {
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            UUID id = UUID.fromString(trimmed);
            TownRecord byId = getTown(id);
            if (byId != null && world.getName().equals(byId.getWorldName())) {
                return byId;
            }
            return null;
        } catch (IllegalArgumentException ignored) {
            return findTownByDisplayName(trimmed);
        }
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
            TownSaveCoordinator.requestSave(this);
        }
    }

    @Nullable
    public TownRecord findTownContainingChunk(int chunkX, int chunkZ, @Nonnull UUID ownerUuid) {
        TownRecord owned = findTownForOwnerInWorld(ownerUuid);
        if (owned == null) {
            return null;
        }
        if (!TownTerritoryClaims.contains(owned, chunkX, chunkZ)) {
            return null;
        }
        return owned;
    }

    /** True if block position is inside this town's claimed chunks. */
    public boolean isInsideTerritory(@Nonnull TownRecord town, int blockX, int blockZ) {
        return TownTerritoryClaims.containsBlock(town, blockX, blockZ);
    }

    /**
     * Returns another town whose claimed chunks intersect the starter square for a charter at
     * {@code charterBlockX}/{@code charterBlockZ} with {@code charterTerritoryRadiusChunks}, or null if clear.
     */
    @Nullable
    public TownRecord findTerritoryOverlapAtCharter(
        @Nonnull String worldName,
        int charterBlockX,
        int charterBlockZ,
        int charterTerritoryRadiusChunks,
        @Nullable UUID excludeTownId
    ) {
        LongSet candidate =
            TownTerritoryClaims.buildStarterChunkIndexSet(charterBlockX, charterBlockZ, charterTerritoryRadiusChunks);
        for (TownRecord other : allTowns()) {
            if (!worldName.equals(other.getWorldName())) {
                continue;
            }
            if (TownTerritoryClaims.intersects(candidate, other, excludeTownId)) {
                return other;
            }
        }
        return null;
    }

    /**
     * After shifting this town's claims by {@code deltaChunkX}/{@code deltaChunkZ}, would any chunk overlap another
     * town?
     */
    @Nullable
    public TownRecord findTerritoryOverlapAfterClaimShift(
        @Nonnull TownRecord town,
        int deltaChunkX,
        int deltaChunkZ
    ) {
        TownTerritoryClaims.migrateIfNeeded(town);
        LongSet shifted = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        for (ClaimedTerritoryChunkRecord c : town.getClaimedTerritoryChunks()) {
            shifted.add(ChunkUtil.indexChunk(c.getChunkX() + deltaChunkX, c.getChunkZ() + deltaChunkZ));
        }
        for (TownRecord other : allTowns()) {
            if (!town.getWorldName().equals(other.getWorldName())) {
                continue;
            }
            if (TownTerritoryClaims.intersects(shifted, other, town.getTownId())) {
                return other;
            }
        }
        return null;
    }

    /**
     * First town in this world whose claimed chunks contain the block column. Deterministic order follows
     * {@link #allTowns()}.
     */
    @Nullable
    public TownRecord findTownContainingBlock(@Nonnull String worldName, int blockX, int blockZ) {
        return TownTerritoryClaims.findTownContainingBlock(allTowns(), worldName, blockX, blockZ);
    }

    /**
     * True if every registered plot footprint block would remain inside claims after shifting claims by
     * {@code deltaChunkX}/{@code deltaChunkZ} with the charter (plots stay fixed in the world).
     */
    public boolean allPlotFootprintsFitAfterClaimShift(@Nonnull TownRecord town, int deltaChunkX, int deltaChunkZ) {
        TownTerritoryClaims.migrateIfNeeded(town);
        for (PlotInstance plot : town.getPlotInstances()) {
            PlotFootprintRecord fp = plot.toFootprint();
            for (int x = fp.getMinX(); x <= fp.getMaxX(); x++) {
                for (int z = fp.getMinZ(); z <= fp.getMaxZ(); z++) {
                    int bx = ChunkUtil.chunkCoordinate(x);
                    int bz = ChunkUtil.chunkCoordinate(z);
                    if (!TownTerritoryClaims.contains(town, bx - deltaChunkX, bz - deltaChunkZ)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** @deprecated use {@link #allPlotFootprintsFitAfterClaimShift} with charter chunk delta */
    @Deprecated
    public boolean allPlotFootprintsFitTerritoryWithCharterAt(@Nonnull TownRecord town, int charterBlockX, int charterBlockZ) {
        int deltaCx = ChunkUtil.chunkCoordinate(charterBlockX) - ChunkUtil.chunkCoordinate(town.getCharterX());
        int deltaCz = ChunkUtil.chunkCoordinate(charterBlockZ) - ChunkUtil.chunkCoordinate(town.getCharterZ());
        return allPlotFootprintsFitAfterClaimShift(town, deltaCx, deltaCz);
    }

    public void putTown(@Nonnull TownRecord record) {
        record.migrateTownSocialFieldsIfNeeded();
        record.migrateFounderMonumentCountIfNeeded();
        ensureDisplayNameUnique(record);
        byTownId.put(record.getTownId(), record);
        dirty = true;
        TownSaveCoordinator.requestSave(this);
    }

    public void updateTown(@Nonnull TownRecord record) {
        markDirty(record);
        TownSaveCoordinator.requestSave(this);
    }

    /** Updates in-memory town data without writing to disk (call {@link #flushDirtyToDisk()} when batching). */
    public void markDirty(@Nonnull TownRecord record) {
        record.migrateTownSocialFieldsIfNeeded();
        record.migrateFounderMonumentCountIfNeeded();
        ensureDisplayNameUnique(record);
        byTownId.put(record.getTownId(), record);
        dirty = true;
    }

    /** Persists when {@link #markDirty} was used since the last flush or immediate {@link #updateTown}. */
    public void flushDirtyToDisk() {
        if (dirty) {
            TownSaveCoordinator.flushSync(this);
        }
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
