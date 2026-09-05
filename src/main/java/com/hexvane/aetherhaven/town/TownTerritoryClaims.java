package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hypixel.hytale.math.util.ChunkUtil;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Chunk claim geometry, migration, expansion rules, and overlap checks for {@link TownRecord}. */
public final class TownTerritoryClaims {
    /** Each expansion purchase claims a square of this many chunks per side (4 chunks total). */
    public static final int CLAIM_BLOCK_CHUNK_SIZE = 2;

    private TownTerritoryClaims() {}

    public static void migrateIfNeeded(@Nonnull TownRecord town) {
        List<ClaimedTerritoryChunkRecord> list = town.getClaimedTerritoryChunksMutable();
        if (!list.isEmpty()) {
            return;
        }
        list.addAll(buildStarterChunkRecords(town.getCharterX(), town.getCharterZ(), town.getTerritoryChunkRadius()));
    }

    @Nonnull
    public static List<ClaimedTerritoryChunkRecord> buildStarterChunkRecords(int charterBlockX, int charterBlockZ, int radiusChunks) {
        int ccx = ChunkUtil.chunkCoordinate(charterBlockX);
        int ccz = ChunkUtil.chunkCoordinate(charterBlockZ);
        int r = Math.max(1, radiusChunks);
        List<ClaimedTerritoryChunkRecord> out = new ArrayList<>(starterChunkCount(r));
        // Even side length (2×radius) per axis so 2×2 expansion blocks align with starter edges.
        for (int dx = -r; dx < r; dx++) {
            for (int dz = -r; dz < r; dz++) {
                out.add(ClaimedTerritoryChunkRecord.of(ccx + dx, ccz + dz));
            }
        }
        return out;
    }

    @Nonnull
    public static LongSet buildStarterChunkIndexSet(int charterBlockX, int charterBlockZ, int radiusChunks) {
        LongSet set = new LongOpenHashSet();
        int ccx = ChunkUtil.chunkCoordinate(charterBlockX);
        int ccz = ChunkUtil.chunkCoordinate(charterBlockZ);
        int r = Math.max(1, radiusChunks);
        for (int dx = -r; dx < r; dx++) {
            for (int dz = -r; dz < r; dz++) {
                set.add(ChunkUtil.indexChunk(ccx + dx, ccz + dz));
            }
        }
        return set;
    }

    @Nonnull
    public static LongSet toChunkIndexSet(@Nonnull TownRecord town) {
        migrateIfNeeded(town);
        LongSet set = new LongOpenHashSet(town.getClaimedTerritoryChunks().size());
        for (ClaimedTerritoryChunkRecord c : town.getClaimedTerritoryChunks()) {
            set.add(ChunkUtil.indexChunk(c.getChunkX(), c.getChunkZ()));
        }
        return set;
    }

    /** Starter territory is a square of {@code 2×radius} chunks per side (even, for 2×2 expansion alignment). */
    public static int starterSideChunks(int radiusChunks) {
        return Math.max(2, Math.max(1, radiusChunks) * 2);
    }

    public static int starterChunkCount(int radiusChunks) {
        int side = starterSideChunks(radiusChunks);
        return side * side;
    }

    public static boolean contains(@Nonnull TownRecord town, int chunkX, int chunkZ) {
        migrateIfNeeded(town);
        long key = ChunkUtil.indexChunk(chunkX, chunkZ);
        for (ClaimedTerritoryChunkRecord c : town.getClaimedTerritoryChunks()) {
            if (ChunkUtil.indexChunk(c.getChunkX(), c.getChunkZ()) == key) {
                return true;
            }
        }
        return false;
    }

    public static boolean containsBlock(@Nonnull TownRecord town, int blockX, int blockZ) {
        return contains(town, ChunkUtil.chunkCoordinate(blockX), ChunkUtil.chunkCoordinate(blockZ));
    }

    public static int countExpansionChunks(@Nonnull TownRecord town) {
        migrateIfNeeded(town);
        LongSet starter =
            buildStarterChunkIndexSet(town.getCharterX(), town.getCharterZ(), town.getTerritoryChunkRadius());
        LongSet owned = toChunkIndexSet(town);
        int extra = 0;
        for (long key : owned) {
            if (!starter.contains(key)) {
                extra++;
            }
        }
        return extra;
    }

    public static int charterChunkX(@Nonnull TownRecord town) {
        return ChunkUtil.chunkCoordinate(town.getCharterX());
    }

    public static int charterChunkZ(@Nonnull TownRecord town) {
        return ChunkUtil.chunkCoordinate(town.getCharterZ());
    }

    public static int minClaimChunkX(@Nonnull TownRecord town) {
        migrateIfNeeded(town);
        int min = Integer.MAX_VALUE;
        for (ClaimedTerritoryChunkRecord c : town.getClaimedTerritoryChunks()) {
            min = Math.min(min, c.getChunkX());
        }
        return min == Integer.MAX_VALUE ? charterChunkX(town) : min;
    }

    public static int maxClaimChunkX(@Nonnull TownRecord town) {
        migrateIfNeeded(town);
        int max = Integer.MIN_VALUE;
        for (ClaimedTerritoryChunkRecord c : town.getClaimedTerritoryChunks()) {
            max = Math.max(max, c.getChunkX());
        }
        return max == Integer.MIN_VALUE ? charterChunkX(town) : max;
    }

    public static int minClaimChunkZ(@Nonnull TownRecord town) {
        migrateIfNeeded(town);
        int min = Integer.MAX_VALUE;
        for (ClaimedTerritoryChunkRecord c : town.getClaimedTerritoryChunks()) {
            min = Math.min(min, c.getChunkZ());
        }
        return min == Integer.MAX_VALUE ? charterChunkZ(town) : min;
    }

    public static int maxClaimChunkZ(@Nonnull TownRecord town) {
        migrateIfNeeded(town);
        int max = Integer.MIN_VALUE;
        for (ClaimedTerritoryChunkRecord c : town.getClaimedTerritoryChunks()) {
            max = Math.max(max, c.getChunkZ());
        }
        return max == Integer.MIN_VALUE ? charterChunkZ(town) : max;
    }

    /**
     * When starter territory span on an axis is odd (e.g. 13 chunks), shift the 2×2 claim map grid by one chunk on
     * +X/+Z so outward blocks (east/south) sit flush against the town instead of skipping a column.
     */
    public static int claimGridAxisOffset(int claimSpanChunks) {
        return (claimSpanChunks & 1) == 1 ? 1 : 0;
    }

    /** Min-chunk anchor for expansion map cell (0,0). Cell ({@code gridSize - 1}) reaches the first east/south 2×2 block when starter span is odd. */
    public static int expansionMapGridOriginX(@Nonnull TownRecord town) {
        int min = minClaimChunkX(town);
        int max = maxClaimChunkX(town);
        return min + claimGridAxisOffset(max - min + 1);
    }

    public static int expansionMapGridOriginZ(@Nonnull TownRecord town) {
        int min = minClaimChunkZ(town);
        int max = maxClaimChunkZ(town);
        return min + claimGridAxisOffset(max - min + 1);
    }

    /**
     * @deprecated Use {@link #expansionMapGridOriginX} / literal block anchors. Charter-aligned snap leaves a gap east/south of odd-width starter territory.
     */
    @Deprecated
    public static int snapClaimBlockAnchor(int chunkCoord, int charterChunkCoord) {
        int delta = chunkCoord - charterChunkCoord;
        int aligned = Math.floorDiv(delta, CLAIM_BLOCK_CHUNK_SIZE) * CLAIM_BLOCK_CHUNK_SIZE;
        return charterChunkCoord + aligned;
    }

    /** Number of 2×2 land purchases beyond the starter territory (starter radius not counted). */
    public static int countExpansionClaimBlocks(@Nonnull TownRecord town) {
        int chunks = countExpansionChunks(town);
        int blockArea = CLAIM_BLOCK_CHUNK_SIZE * CLAIM_BLOCK_CHUNK_SIZE;
        return chunks / blockArea;
    }

    public static boolean expansionClaimLimitReached(@Nonnull TownRecord town, @Nonnull AetherhavenPluginConfig cfg) {
        if (!cfg.isTerritoryExpansionClaimLimitEnabled()) {
            return false;
        }
        return countExpansionClaimBlocks(town) >= cfg.getMaxTerritoryExpansionClaimBlocks();
    }

    public static long nextClaimBlockCostGold(@Nonnull TownRecord town, @Nonnull AetherhavenPluginConfig cfg) {
        int purchased = countExpansionClaimBlocks(town);
        return cfg.getTerritoryExpansionFirstClaimCostGold()
            + cfg.getTerritoryExpansionClaimCostIncrementGold() * (long) purchased;
    }

    /** Price of the most recent expand step (or first claim cost when none purchased yet). */
    public static long lastClaimBlockCostGold(@Nonnull TownRecord town, @Nonnull AetherhavenPluginConfig cfg) {
        int purchased = countExpansionClaimBlocks(town);
        int step = Math.max(0, purchased - 1);
        return cfg.getTerritoryExpansionFirstClaimCostGold()
            + cfg.getTerritoryExpansionClaimCostIncrementGold() * (long) step;
    }

    /** Half of {@link #lastClaimBlockCostGold}, floored. */
    public static long sellClaimBlockRefundGold(@Nonnull TownRecord town, @Nonnull AetherhavenPluginConfig cfg) {
        return lastClaimBlockCostGold(town, cfg) / 2L;
    }

    /** Why a 2×2 claim block cannot be sold, or {@code null} if it can. */
    public enum SellClaimBlockReject {
        NOT_OWNED,
        HAS_BUILDINGS,
        CHARTER_OUTSIDE,
        WOULD_SPLIT
    }

    public static void initializeStarterClaims(@Nonnull TownRecord town) {
        town.getClaimedTerritoryChunksMutable().clear();
        town.getClaimedTerritoryChunksMutable().addAll(
            buildStarterChunkRecords(town.getCharterX(), town.getCharterZ(), town.getTerritoryChunkRadius())
        );
    }

    public static void shiftAllClaims(@Nonnull TownRecord town, int deltaChunkX, int deltaChunkZ) {
        migrateIfNeeded(town);
        if (deltaChunkX == 0 && deltaChunkZ == 0) {
            return;
        }
        List<ClaimedTerritoryChunkRecord> list = town.getClaimedTerritoryChunksMutable();
        List<ClaimedTerritoryChunkRecord> shifted = new ArrayList<>(list.size());
        for (ClaimedTerritoryChunkRecord c : list) {
            shifted.add(ClaimedTerritoryChunkRecord.of(c.getChunkX() + deltaChunkX, c.getChunkZ() + deltaChunkZ));
        }
        list.clear();
        list.addAll(shifted);
    }

    public static boolean intersects(
        @Nonnull LongSet candidateChunks,
        @Nonnull TownRecord other,
        @Nullable UUID excludeTownId
    ) {
        if (excludeTownId != null && excludeTownId.equals(other.getTownId())) {
            return false;
        }
        LongSet otherSet = toChunkIndexSet(other);
        for (long key : candidateChunks) {
            if (otherSet.contains(key)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public static TownRecord findTownOwningChunk(
        @Nonnull List<TownRecord> towns,
        @Nonnull String worldName,
        int chunkX,
        int chunkZ,
        @Nullable UUID excludeTownId
    ) {
        long key = ChunkUtil.indexChunk(chunkX, chunkZ);
        for (TownRecord t : towns) {
            if (!worldName.equals(t.getWorldName())) {
                continue;
            }
            if (excludeTownId != null && excludeTownId.equals(t.getTownId())) {
                continue;
            }
            migrateIfNeeded(t);
            for (ClaimedTerritoryChunkRecord c : t.getClaimedTerritoryChunks()) {
                if (ChunkUtil.indexChunk(c.getChunkX(), c.getChunkZ()) == key) {
                    return t;
                }
            }
        }
        return null;
    }

    @Nullable
    public static TownRecord findTownContainingBlock(
        @Nonnull List<TownRecord> towns,
        @Nonnull String worldName,
        int blockX,
        int blockZ
    ) {
        return findTownOwningChunk(
            towns,
            worldName,
            ChunkUtil.chunkCoordinate(blockX),
            ChunkUtil.chunkCoordinate(blockZ),
            null
        );
    }

    public static boolean isAdjacentToClaim(@Nonnull TownRecord town, int chunkX, int chunkZ) {
        migrateIfNeeded(town);
        return contains(town, chunkX - 1, chunkZ)
            || contains(town, chunkX + 1, chunkZ)
            || contains(town, chunkX, chunkZ - 1)
            || contains(town, chunkX, chunkZ + 1);
    }

    public static boolean canClaimChunk(
        @Nonnull TownRecord town,
        int chunkX,
        int chunkZ,
        @Nonnull List<TownRecord> allTownsInWorld
    ) {
        return canClaimBlock(town, chunkX, chunkZ, allTownsInWorld);
    }

    public static boolean canClaimBlock(
        @Nonnull TownRecord town,
        int anchorChunkX,
        int anchorChunkZ,
        @Nonnull List<TownRecord> allTownsInWorld,
        @Nonnull AetherhavenPluginConfig cfg
    ) {
        if (expansionClaimLimitReached(town, cfg)) {
            return false;
        }
        return canClaimBlockGeometry(town, anchorChunkX, anchorChunkZ, allTownsInWorld);
    }

    public static boolean canClaimBlock(
        @Nonnull TownRecord town,
        int anchorChunkX,
        int anchorChunkZ,
        @Nonnull List<TownRecord> allTownsInWorld
    ) {
        return canClaimBlockGeometry(town, anchorChunkX, anchorChunkZ, allTownsInWorld);
    }

    private static boolean canClaimBlockGeometry(
        @Nonnull TownRecord town,
        int anchorChunkX,
        int anchorChunkZ,
        @Nonnull List<TownRecord> allTownsInWorld
    ) {
        migrateIfNeeded(town);
        int ax = anchorChunkX;
        int az = anchorChunkZ;
        boolean touchesTown = false;
        for (int dx = 0; dx < CLAIM_BLOCK_CHUNK_SIZE; dx++) {
            for (int dz = 0; dz < CLAIM_BLOCK_CHUNK_SIZE; dz++) {
                int cx = ax + dx;
                int cz = az + dz;
                if (contains(town, cx, cz)) {
                    return false;
                }
                if (findTownOwningChunk(allTownsInWorld, town.getWorldName(), cx, cz, town.getTownId()) != null) {
                    return false;
                }
                if (isAdjacentToClaim(town, cx, cz)) {
                    touchesTown = true;
                }
            }
        }
        return touchesTown;
    }

    public static boolean addClaim(@Nonnull TownRecord town, int chunkX, int chunkZ) {
        return addClaimBlock(town, chunkX, chunkZ);
    }

    public static boolean addClaimBlock(@Nonnull TownRecord town, int anchorChunkX, int anchorChunkZ) {
        int ax = anchorChunkX;
        int az = anchorChunkZ;
        for (int dx = 0; dx < CLAIM_BLOCK_CHUNK_SIZE; dx++) {
            for (int dz = 0; dz < CLAIM_BLOCK_CHUNK_SIZE; dz++) {
                int cx = ax + dx;
                int cz = az + dz;
                if (contains(town, cx, cz)) {
                    return false;
                }
            }
        }
        for (int dx = 0; dx < CLAIM_BLOCK_CHUNK_SIZE; dx++) {
            for (int dz = 0; dz < CLAIM_BLOCK_CHUNK_SIZE; dz++) {
                town.getClaimedTerritoryChunksMutable().add(
                    ClaimedTerritoryChunkRecord.of(ax + dx, az + dz)
                );
            }
        }
        return true;
    }

    public static boolean isClaimBlockFullyOwned(@Nonnull TownRecord town, int anchorChunkX, int anchorChunkZ) {
        migrateIfNeeded(town);
        for (int dx = 0; dx < CLAIM_BLOCK_CHUNK_SIZE; dx++) {
            for (int dz = 0; dz < CLAIM_BLOCK_CHUNK_SIZE; dz++) {
                if (!contains(town, anchorChunkX + dx, anchorChunkZ + dz)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Nullable
    public static SellClaimBlockReject reasonCannotSellClaimBlock(
        @Nonnull TownRecord town,
        int anchorChunkX,
        int anchorChunkZ
    ) {
        migrateIfNeeded(town);
        if (!isClaimBlockFullyOwned(town, anchorChunkX, anchorChunkZ)) {
            return SellClaimBlockReject.NOT_OWNED;
        }
        LongSet remaining = toChunkIndexSet(town);
        for (int dx = 0; dx < CLAIM_BLOCK_CHUNK_SIZE; dx++) {
            for (int dz = 0; dz < CLAIM_BLOCK_CHUNK_SIZE; dz++) {
                remaining.remove(ChunkUtil.indexChunk(anchorChunkX + dx, anchorChunkZ + dz));
            }
        }
        if (remaining.isEmpty()) {
            return SellClaimBlockReject.CHARTER_OUTSIDE;
        }
        long charterKey = ChunkUtil.indexChunk(charterChunkX(town), charterChunkZ(town));
        if (!remaining.contains(charterKey)) {
            return SellClaimBlockReject.CHARTER_OUTSIDE;
        }
        if (!allPlotFootprintsInsideChunkSet(town, remaining)) {
            return SellClaimBlockReject.HAS_BUILDINGS;
        }
        if (!isOrthogonallyConnected(remaining)) {
            return SellClaimBlockReject.WOULD_SPLIT;
        }
        return null;
    }

    public static boolean canSellClaimBlock(@Nonnull TownRecord town, int anchorChunkX, int anchorChunkZ) {
        return reasonCannotSellClaimBlock(town, anchorChunkX, anchorChunkZ) == null;
    }

    /**
     * Removes a fully owned 2×2 claim block when {@link #canSellClaimBlock} allows it.
     *
     * @return {@code true} if the block was removed
     */
    public static boolean removeClaimBlock(@Nonnull TownRecord town, int anchorChunkX, int anchorChunkZ) {
        if (!canSellClaimBlock(town, anchorChunkX, anchorChunkZ)) {
            return false;
        }
        LongSet removeKeys = new LongOpenHashSet(CLAIM_BLOCK_CHUNK_SIZE * CLAIM_BLOCK_CHUNK_SIZE);
        for (int dx = 0; dx < CLAIM_BLOCK_CHUNK_SIZE; dx++) {
            for (int dz = 0; dz < CLAIM_BLOCK_CHUNK_SIZE; dz++) {
                removeKeys.add(ChunkUtil.indexChunk(anchorChunkX + dx, anchorChunkZ + dz));
            }
        }
        town.getClaimedTerritoryChunksMutable()
            .removeIf(c -> removeKeys.contains(ChunkUtil.indexChunk(c.getChunkX(), c.getChunkZ())));
        return true;
    }

    private static boolean allPlotFootprintsInsideChunkSet(@Nonnull TownRecord town, @Nonnull LongSet chunks) {
        for (PlotInstance plot : town.getPlotInstances()) {
            PlotFootprintRecord fp = plot.toFootprint();
            for (int x = fp.getMinX(); x <= fp.getMaxX(); x++) {
                for (int z = fp.getMinZ(); z <= fp.getMaxZ(); z++) {
                    long key = ChunkUtil.indexChunk(ChunkUtil.chunkCoordinate(x), ChunkUtil.chunkCoordinate(z));
                    if (!chunks.contains(key)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** True when every claimed chunk is reachable from every other via orthogonal adjacency. */
    static boolean isOrthogonallyConnected(@Nonnull LongSet chunks) {
        if (chunks.isEmpty()) {
            return false;
        }
        long start = chunks.iterator().nextLong();
        LongOpenHashSet visited = new LongOpenHashSet(chunks.size());
        ArrayDeque<Long> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            long key = queue.removeFirst();
            int cx = ChunkUtil.xOfChunkIndex(key);
            int cz = ChunkUtil.zOfChunkIndex(key);
            tryEnqueueNeighbor(chunks, visited, queue, cx - 1, cz);
            tryEnqueueNeighbor(chunks, visited, queue, cx + 1, cz);
            tryEnqueueNeighbor(chunks, visited, queue, cx, cz - 1);
            tryEnqueueNeighbor(chunks, visited, queue, cx, cz + 1);
        }
        return visited.size() == chunks.size();
    }

    private static void tryEnqueueNeighbor(
        @Nonnull LongSet chunks,
        @Nonnull LongOpenHashSet visited,
        @Nonnull ArrayDeque<Long> queue,
        int chunkX,
        int chunkZ
    ) {
        long key = ChunkUtil.indexChunk(chunkX, chunkZ);
        if (chunks.contains(key) && visited.add(key)) {
            queue.add(key);
        }
    }

    /**
     * Inclusive block bounds of all claimed chunks (min/max block coords).
     */
    public static void claimBlockBounds(@Nonnull TownRecord town, @Nonnull int[] outMinMax) {
        migrateIfNeeded(town);
        List<ClaimedTerritoryChunkRecord> claims = town.getClaimedTerritoryChunks();
        if (claims.isEmpty()) {
            outMinMax[0] = town.getCharterX();
            outMinMax[1] = town.getCharterZ();
            outMinMax[2] = town.getCharterX();
            outMinMax[3] = town.getCharterZ();
            return;
        }
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (ClaimedTerritoryChunkRecord c : claims) {
            int bx0 = ChunkUtil.minBlock(c.getChunkX());
            int bz0 = ChunkUtil.minBlock(c.getChunkZ());
            int bx1 = ChunkUtil.maxBlock(c.getChunkX());
            int bz1 = ChunkUtil.maxBlock(c.getChunkZ());
            minX = Math.min(minX, bx0);
            minZ = Math.min(minZ, bz0);
            maxX = Math.max(maxX, bx1);
            maxZ = Math.max(maxZ, bz1);
        }
        outMinMax[0] = minX;
        outMinMax[1] = minZ;
        outMinMax[2] = maxX;
        outMinMax[3] = maxZ;
    }

    public static boolean allPlotFootprintsInsideClaims(@Nonnull TownRecord town) {
        migrateIfNeeded(town);
        for (PlotInstance plot : town.getPlotInstances()) {
            PlotFootprintRecord fp = plot.toFootprint();
            for (int x = fp.getMinX(); x <= fp.getMaxX(); x++) {
                for (int z = fp.getMinZ(); z <= fp.getMaxZ(); z++) {
                    if (!containsBlock(town, x, z)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public static int maxCharterToClaimEdgeBlocks(@Nonnull TownRecord town) {
        int[] mm = new int[4];
        claimBlockBounds(town, mm);
        int cx = town.getCharterX();
        int cz = town.getCharterZ();
        int max = 0;
        max = Math.max(max, Math.abs(mm[0] - cx));
        max = Math.max(max, Math.abs(mm[2] - cx));
        max = Math.max(max, Math.abs(mm[1] - cz));
        max = Math.max(max, Math.abs(mm[3] - cz));
        return max;
    }

    /**
     * Blocks from charter to the outer claim border along one cardinal axis ({@code axisX}/{@code axisZ} is -1, 0, or
     * 1). Used for raid spawns so approach distance follows the border on that side, not the farthest border town-wide.
     */
    public static int charterToClaimBorderAlong(@Nonnull TownRecord town, int axisX, int axisZ) {
        int[] mm = new int[4];
        claimBlockBounds(town, mm);
        int cx = town.getCharterX();
        int cz = town.getCharterZ();
        if (axisX > 0) {
            return Math.max(0, mm[2] - cx);
        }
        if (axisX < 0) {
            return Math.max(0, cx - mm[0]);
        }
        if (axisZ > 0) {
            return Math.max(0, mm[3] - cz);
        }
        if (axisZ < 0) {
            return Math.max(0, cz - mm[1]);
        }
        return 0;
    }
}
