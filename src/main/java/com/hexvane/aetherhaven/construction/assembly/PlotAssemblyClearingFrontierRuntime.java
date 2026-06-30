package com.hexvane.aetherhaven.construction.assembly;

import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.LocalCachedChunkAccessor;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/**
 * Cached clearing frontier world cells for one job; updated incrementally on each break instead of recomputing from all
 * obstructed cells.
 */
public final class PlotAssemblyClearingFrontierRuntime {
    private static final int[][] NEIGHBOR_OFFSETS = {
        {1, 0, 0},
        {-1, 0, 0},
        {0, 1, 0},
        {0, -1, 0},
        {0, 0, 1},
        {0, 0, -1},
    };

    private final LongOpenHashSet frontierWorld = new LongOpenHashSet();

    private PlotAssemblyClearingFrontierRuntime() {}

    @Nonnull
    public static PlotAssemblyClearingFrontierRuntime rebuild(
        @Nonnull World world,
        @Nonnull PlotAssemblyJob job,
        @Nonnull PlotAssemblyClearingRuntime clearingRt,
        @Nonnull LocalCachedChunkAccessor chunkAccessor,
        @Nullable AssemblySectionMapper sectionMapper,
        int activeSectionFlat
    ) {
        PlotAssemblyClearingFrontierRuntime rt = new PlotAssemblyClearingFrontierRuntime();
        ArrayList<Vector3i> obstructed = new ArrayList<>();
        clearingRt.appendObstructedCells(obstructed);
        ArrayList<Vector3i> live =
            sectionMapper == null
                ? AssemblyClearingFrontier.frontierWorldCellsLive(world, job, obstructed, chunkAccessor)
                : AssemblyClearingFrontier.frontierWorldCellsLive(
                    world, job, obstructed, chunkAccessor, sectionMapper, activeSectionFlat
                );
        for (int i = 0; i < live.size(); i++) {
            Vector3i c = live.get(i);
            rt.frontierWorld.add(packBlock(c.x, c.y, c.z));
        }
        return rt;
    }

    public boolean isEmpty() {
        return frontierWorld.isEmpty();
    }

    public boolean containsWorldCell(int wx, int wy, int wz) {
        return frontierWorld.contains(packBlock(wx, wy, wz));
    }

    public void appendFrontierWorldCells(@Nonnull List<Vector3i> out) {
        if (frontierWorld.isEmpty()) {
            return;
        }
        ArrayList<Vector3i> scratch = new ArrayList<>(frontierWorld.size());
        it.unimi.dsi.fastutil.longs.LongIterator it = frontierWorld.iterator();
        while (it.hasNext()) {
            scratch.add(unpackBlock(it.nextLong()));
        }
        scratch.sort(
            Comparator
                .comparingInt((Vector3i v) -> v.x)
                .thenComparingInt(v -> v.y)
                .thenComparingInt(v -> v.z)
        );
        out.addAll(scratch);
    }

    /** Deterministic passive/staff choice: lexicographically smallest frontier cell. */
    @Nullable
    public Vector3i firstWorldCell() {
        if (frontierWorld.isEmpty()) {
            return null;
        }
        int bestX = Integer.MAX_VALUE;
        int bestY = Integer.MAX_VALUE;
        int bestZ = Integer.MAX_VALUE;
        var it = frontierWorld.iterator();
        while (it.hasNext()) {
            Vector3i c = unpackBlock(it.nextLong());
            if (c.x < bestX || (c.x == bestX && c.y < bestY) || (c.x == bestX && c.y == bestY && c.z < bestZ)) {
                bestX = c.x;
                bestY = c.y;
                bestZ = c.z;
            }
        }
        if (bestX == Integer.MAX_VALUE) {
            return null;
        }
        return new Vector3i(bestX, bestY, bestZ);
    }

    /**
     * After {@link PlotAssemblyClearingRuntime#removeCell}, refresh frontier neighbors incrementally.
     */
    public void onCellCleared(
        @Nonnull World world,
        @Nonnull PlotAssemblyJob job,
        @Nonnull PlotAssemblyClearingRuntime clearingRt,
        @Nonnull LocalCachedChunkAccessor chunkAccessor,
        int wx,
        int wy,
        int wz,
        @Nullable AssemblySectionMapper sectionMapper,
        int activeSectionFlat
    ) {
        frontierWorld.remove(packBlock(wx, wy, wz));
        Vector3i anchor = job.anchor();
        for (int i = 0; i < NEIGHBOR_OFFSETS.length; i++) {
            int[] d = NEIGHBOR_OFFSETS[i];
            int nx = wx + d[0];
            int ny = wy + d[1];
            int nz = wz + d[2];
            if (sectionMapper != null && !sectionMapper.isWorldCellInSection(anchor, nx, ny, nz, activeSectionFlat)) {
                continue;
            }
            if (!clearingRt.containsWorldCell(nx, ny, nz)) {
                continue;
            }
            if (AssemblyClearingFrontier.isFrontierCellLive(world, job, nx, ny, nz, chunkAccessor)) {
                frontierWorld.add(packBlock(nx, ny, nz));
            }
        }
        if (frontierWorld.isEmpty() && !clearingRt.isEmpty()) {
            seedLowestObstructedLayer(world, job, clearingRt, chunkAccessor, sectionMapper, activeSectionFlat);
        }
    }

    private void seedLowestObstructedLayer(
        @Nonnull World world,
        @Nonnull PlotAssemblyJob job,
        @Nonnull PlotAssemblyClearingRuntime clearingRt,
        @Nonnull LocalCachedChunkAccessor chunkAccessor,
        @Nullable AssemblySectionMapper sectionMapper,
        int activeSectionFlat
    ) {
        ArrayList<Vector3i> obstructed = new ArrayList<>();
        clearingRt.appendObstructedCells(obstructed);
        ArrayList<Vector3i> scoped = obstructed;
        Vector3i anchor = job.anchor();
        if (sectionMapper != null) {
            scoped = new ArrayList<>();
            for (int i = 0; i < obstructed.size(); i++) {
                Vector3i cell = obstructed.get(i);
                if (sectionMapper.isWorldCellInSection(anchor, cell.x, cell.y, cell.z, activeSectionFlat)) {
                    scoped.add(cell);
                }
            }
        }
        if (scoped.isEmpty()) {
            return;
        }
        int minY = Integer.MAX_VALUE;
        for (int i = 0; i < scoped.size(); i++) {
            Vector3i cell = scoped.get(i);
            if (AssemblyObstructionUtil.isObstructedFootprintCell(world, job, cell, chunkAccessor)) {
                minY = Math.min(minY, cell.y);
            }
        }
        if (minY == Integer.MAX_VALUE) {
            return;
        }
        for (int i = 0; i < scoped.size(); i++) {
            Vector3i cell = scoped.get(i);
            if (cell.y == minY
                && AssemblyObstructionUtil.isObstructedFootprintCell(world, job, cell, chunkAccessor)) {
                frontierWorld.add(packBlock(cell.x, cell.y, cell.z));
            }
        }
    }

    private static long packBlock(int x, int y, int z) {
        return ((long) x & 0x3FFFFFL) << 42 | ((long) y & 0xFFFL) << 30 | ((long) z & 0x3FFFFFL);
    }

    @Nonnull
    private static Vector3i unpackBlock(long packed) {
        int z = (int) (packed & 0x3FFFFFL);
        int y = (int) ((packed >>> 30) & 0xFFF);
        int x = (int) ((packed >>> 42) & 0x3FFFFF);
        if (x >= 0x200000) {
            x -= 0x400000;
        }
        if (z >= 0x200000) {
            z -= 0x400000;
        }
        return new Vector3i(x, y, z);
    }
}
