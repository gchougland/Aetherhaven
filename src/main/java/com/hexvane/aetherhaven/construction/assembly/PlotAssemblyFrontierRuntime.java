package com.hexvane.aetherhaven.construction.assembly;

import com.hexvane.aetherhaven.construction.ConstructionPasteOps;
import com.hexvane.aetherhaven.construction.ConstructionPasteOps.PendingBlock;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.LocalCachedChunkAccessor;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Mutable incremental frontier for one assembly job: O(1) neighbor updates per placement instead of recomputing from all
 * placed cells. Paired with {@link PlotAssemblyJob} in {@link AssemblyWorldRegistry}.
 */
public final class PlotAssemblyFrontierRuntime {
    private static final int[] NB_DX = {1, -1, 0, 0, 0, 0};
    private static final int[] NB_DY = {0, 0, 1, -1, 0, 0};
    private static final int[] NB_DZ = {0, 0, 0, 0, 1, -1};

    /** Unbiased prefab-local coords must fit 21 bits after bias (±~1M). */
    private static final int PREFAB_COORD_BIAS = 1 << 20;

    private final Long2IntOpenHashMap prefabCoordToIndex;
    private final IntOpenHashSet frontier;
    private final int minPrefabX;
    private final int maxPrefabX;
    private final int minPrefabY;
    private final int maxPrefabY;
    private final int minPrefabZ;
    private final int maxPrefabZ;

    @Nullable
    private LocalCachedChunkAccessor cachedChunkAccessor;

    private PlotAssemblyFrontierRuntime(
        @Nonnull Long2IntOpenHashMap prefabCoordToIndex,
        @Nonnull IntOpenHashSet frontier,
        int minPrefabX,
        int maxPrefabX,
        int minPrefabY,
        int maxPrefabY,
        int minPrefabZ,
        int maxPrefabZ
    ) {
        this.prefabCoordToIndex = prefabCoordToIndex;
        this.frontier = frontier;
        this.minPrefabX = minPrefabX;
        this.maxPrefabX = maxPrefabX;
        this.minPrefabY = minPrefabY;
        this.maxPrefabY = maxPrefabY;
        this.minPrefabZ = minPrefabZ;
        this.maxPrefabZ = maxPrefabZ;
    }

    /**
     * Builds the prefab coord map and frontier consistent with {@link PlotAssemblyFrontier#frontierIndices} for the
     * current placement progress on {@code plot}.
     */
    @Nonnull
    public static PlotAssemblyFrontierRuntime create(@Nonnull List<PendingBlock> pending, @Nonnull PlotInstance plot) {
        Long2IntOpenHashMap map = new Long2IntOpenHashMap();
        map.defaultReturnValue(-1);
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < pending.size(); i++) {
            PendingBlock pb = pending.get(i);
            int x = pb.x();
            int y = pb.y();
            int z = pb.z();
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
            long key = packPrefabCell(x, y, z);
            if (!map.containsKey(key)) {
                map.put(key, i);
            }
        }
        IntOpenHashSet f = new IntOpenHashSet();
        PlotAssemblyFrontierRuntime rt =
            new PlotAssemblyFrontierRuntime(map, f, minX, maxX, minY, maxY, minZ, maxZ);
        rt.rebuildFrontierFromPlot(pending, plot);
        return rt;
    }

    /** Full recompute (e.g. defensive recovery); matches {@link PlotAssemblyFrontier#frontierIndices}. */
    public void rebuildFrontierFromPlot(@Nonnull List<PendingBlock> pending, @Nonnull PlotInstance plot) {
        frontier.clear();
        IntOpenHashSet placed = new IntOpenHashSet();
        plot.fillAssemblyPlacedSet(placed, pending.size());
        var full = PlotAssemblyFrontier.frontierIndices(pending, placed);
        for (int i = 0; i < full.size(); i++) {
            frontier.add(full.getInt(i));
        }
    }

    public int minPrefabX() {
        return minPrefabX;
    }

    public int maxPrefabX() {
        return maxPrefabX;
    }

    public int minPrefabY() {
        return minPrefabY;
    }

    public int maxPrefabY() {
        return maxPrefabY;
    }

    public int minPrefabZ() {
        return minPrefabZ;
    }

    public int maxPrefabZ() {
        return maxPrefabZ;
    }

    public boolean frontierContains(int placementIndex) {
        return frontier.contains(placementIndex);
    }

    /** Deterministic passive choice: smallest prefab sequence index on the frontier. */
    public int smallestPlacementIndex() {
        if (frontier.isEmpty()) {
            return -1;
        }
        int best = Integer.MAX_VALUE;
        IntIterator it = frontier.iterator();
        while (it.hasNext()) {
            int v = it.nextInt();
            if (v < best) {
                best = v;
            }
        }
        return best;
    }

    @Nonnull
    public IntIterator frontierIterator() {
        return frontier.iterator();
    }

    public void appendFrontierWorldCells(@Nonnull Vector3i anchor, @Nonnull List<PendingBlock> pending, @Nonnull List<Vector3i> out) {
        IntIterator it = frontier.iterator();
        while (it.hasNext()) {
            int pi = it.nextInt();
            PendingBlock pb = pending.get(pi);
            out.add(new Vector3i(anchor.x + pb.x(), anchor.y + pb.y(), anchor.z + pb.z()));
        }
    }

    /**
     * @return pending index if {@code cellWorld} is an unplaced frontier cell, else {@code -1}.
     */
    public int resolveFrontierPlacementIndex(
        @Nonnull Vector3i anchor,
        @Nonnull List<PendingBlock> pending,
        @Nonnull Vector3i cellWorld
    ) {
        int rx = cellWorld.x - anchor.x;
        int ry = cellWorld.y - anchor.y;
        int rz = cellWorld.z - anchor.z;
        int idx = prefabCoordToIndex.get(packPrefabCell(rx, ry, rz));
        if (idx < 0 || !frontier.contains(idx)) {
            return -1;
        }
        return idx;
    }

    /**
     * After {@link PlotInstance#addAssemblyPlacedIndex} has been called for {@code placedIndex}, expands the frontier
     * incrementally.
     */
    public void onBlockPlaced(int placedIndex, @Nonnull List<PendingBlock> pending, @Nonnull PlotInstance plot) {
        frontier.remove(placedIndex);
        IntOpenHashSet placed = new IntOpenHashSet();
        plot.fillAssemblyPlacedSet(placed, pending.size());
        PendingBlock pb = pending.get(placedIndex);
        for (int n = 0; n < 6; n++) {
            int nx = pb.x() + NB_DX[n];
            int ny = pb.y() + NB_DY[n];
            int nz = pb.z() + NB_DZ[n];
            int ni = prefabCoordToIndex.get(packPrefabCell(nx, ny, nz));
            if (ni >= 0 && !placed.contains(ni)) {
                frontier.add(ni);
            }
        }
        if (frontier.isEmpty() && placed.size() < pending.size()) {
            seedDisconnectedMinYLayer(pending, placed);
        }
    }

    private void seedDisconnectedMinYLayer(@Nonnull List<PendingBlock> pending, @Nonnull IntSet placed) {
        int minYUnplaced = Integer.MAX_VALUE;
        int n = pending.size();
        for (int i = 0; i < n; i++) {
            if (placed.contains(i)) {
                continue;
            }
            minYUnplaced = Math.min(minYUnplaced, pending.get(i).y());
        }
        if (minYUnplaced == Integer.MAX_VALUE) {
            return;
        }
        for (int i = 0; i < n; i++) {
            if (!placed.contains(i) && pending.get(i).y() == minYUnplaced) {
                frontier.add(i);
            }
        }
    }

    public int indexAtPrefabCoord(int rx, int ry, int rz) {
        return prefabCoordToIndex.get(packPrefabCell(rx, ry, rz));
    }

    /** Clears cached chunks so the next placement pass re-queries the world (stale refs after unload). */
    public void clearChunkAccessor() {
        this.cachedChunkAccessor = null;
    }

    @Nonnull
    public LocalCachedChunkAccessor getOrCreateChunkAccessor(
        @Nonnull World world,
        @Nonnull Vector3i anchor,
        @Nonnull IPrefabBuffer buffer
    ) {
        if (cachedChunkAccessor == null) {
            cachedChunkAccessor = ConstructionPasteOps.createAccessor(world, anchor, buffer);
        }
        return cachedChunkAccessor;
    }

    private static long packPrefabCell(int x, int y, int z) {
        long px = (long) x + PREFAB_COORD_BIAS;
        long py = (long) y + PREFAB_COORD_BIAS;
        long pz = (long) z + PREFAB_COORD_BIAS;
        if (px != (px & 0x1FFFFFL) || py != (py & 0x1FFFFFL) || pz != (pz & 0x1FFFFFL)) {
            throw new IllegalStateException(
                "Prefab cell (" + x + "," + y + "," + z + ") out of incremental assembly packing range; prefab too large."
            );
        }
        return px | (py << 21) | (pz << 42);
    }
}
