package com.hexvane.aetherhaven.construction.assembly;

import com.hexvane.aetherhaven.construction.ConstructionPasteOps;
import com.hexvane.aetherhaven.construction.ConstructionPasteOps.PendingBlock;
import org.joml.Vector3d;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.LocalCachedChunkAccessor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Cached obstructed footprint cells for one clearing-phase job (avoids full-footprint scans each preview tick). */
public final class PlotAssemblyClearingRuntime {
    private final ArrayList<Vector3i> obstructedWorldCells = new ArrayList<>();
    private final Map<Long, Vector3i> byPackedCoord = new HashMap<>();
    @Nullable
    private LocalCachedChunkAccessor chunkAccessor;
    private static final int PRUNE_INTERVAL_TICKS = 5;
    private int ticksSinceFullPrune;

    @Nonnull
    private LocalCachedChunkAccessor chunkAccessor(@Nonnull World world, @Nonnull PlotAssemblyJob job) {
        LocalCachedChunkAccessor acc = chunkAccessor;
        if (acc == null) {
            acc = ConstructionPasteOps.createAccessor(world, job.anchor(), job.buffer());
            chunkAccessor = acc;
        }
        return acc;
    }

    @Nonnull
    public static PlotAssemblyClearingRuntime empty() {
        return new PlotAssemblyClearingRuntime();
    }

    @Nonnull
    public static PlotAssemblyClearingRuntime scanLoadedFootprint(@Nonnull World world, @Nonnull PlotAssemblyJob job) {
        PlotAssemblyClearingRuntime rt = new PlotAssemblyClearingRuntime();
        LocalCachedChunkAccessor chunkAccessor =
            ConstructionPasteOps.createAccessor(world, job.anchor(), job.buffer());
        Vector3i anchor = job.anchor();
        List<PendingBlock> footprint = job.footprintCells();
        for (int i = 0; i < footprint.size(); i++) {
            PendingBlock pb = footprint.get(i);
            int wx = anchor.x + pb.x();
            int wy = anchor.y + pb.y();
            int wz = anchor.z + pb.z();
            Vector3i cell = new Vector3i(wx, wy, wz);
            if (AssemblyObstructionUtil.isObstructedFootprintCell(world, job, cell, chunkAccessor)) {
                rt.addCell(cell);
            }
        }
        rt.obstructedWorldCells.sort(
            Comparator
                .comparingInt((Vector3i v) -> v.x)
                .thenComparingInt(v -> v.y)
                .thenComparingInt(v -> v.z)
        );
        return rt;
    }

    private void addCell(@Nonnull Vector3i cell) {
        long key = packBlock(cell.x, cell.y, cell.z);
        if (byPackedCoord.containsKey(key)) {
            return;
        }
        byPackedCoord.put(key, cell);
        obstructedWorldCells.add(cell);
    }

    private void removeCellInternal(int wx, int wy, int wz) {
        long key = packBlock(wx, wy, wz);
        if (byPackedCoord.remove(key) == null) {
            return;
        }
        for (int i = 0; i < obstructedWorldCells.size(); i++) {
            Vector3i c = obstructedWorldCells.get(i);
            if (c.x == wx && c.y == wy && c.z == wz) {
                obstructedWorldCells.remove(i);
                return;
            }
        }
    }

    public void removeCell(int wx, int wy, int wz) {
        removeCellInternal(wx, wy, wz);
    }

    /** Drops cached cells that no longer hold a solid block (e.g. broken manually without the staff). */
    public void pruneStale(@Nonnull World world, @Nonnull PlotAssemblyJob job) {
        LocalCachedChunkAccessor acc = chunkAccessor(world, job);
        for (int i = obstructedWorldCells.size() - 1; i >= 0; i--) {
            Vector3i cell = obstructedWorldCells.get(i);
            if (!AssemblyObstructionUtil.isObstructedFootprintCell(world, job, cell, acc)) {
                removeCellInternal(cell.x, cell.y, cell.z);
            }
        }
    }

    /** Full-footprint stale scan, throttled to avoid block reads every world tick. */
    public void pruneStaleIfDue(@Nonnull World world, @Nonnull PlotAssemblyJob job) {
        if (++ticksSinceFullPrune < PRUNE_INTERVAL_TICKS) {
            return;
        }
        ticksSinceFullPrune = 0;
        pruneStale(world, job);
    }

    @Nonnull
    public LocalCachedChunkAccessor getOrCreateChunkAccessor(@Nonnull World world, @Nonnull PlotAssemblyJob job) {
        return chunkAccessor(world, job);
    }

    /** Hot path: copy cached obstructed cells without stale pruning. */
    public void appendObstructedCells(@Nonnull ArrayList<Vector3i> out) {
        out.addAll(obstructedWorldCells);
    }

    /**
     * Scans only footprint cells belonging to {@code flatSection} and merges obstructed world cells into this runtime.
     */
    public void scanSectionFootprintMerge(
        @Nonnull World world,
        @Nonnull PlotAssemblyJob job,
        @Nonnull AssemblySectionMapper sectionMapper,
        int flatSection
    ) {
        LocalCachedChunkAccessor acc = chunkAccessor(world, job);
        Vector3i anchor = job.anchor();
        List<PendingBlock> footprint = job.footprintCells();
        for (int i = 0; i < footprint.size(); i++) {
            PendingBlock pb = footprint.get(i);
            if (!sectionMapper.isCellInSection(pb, flatSection)) {
                continue;
            }
            int wx = anchor.x + pb.x();
            int wy = anchor.y + pb.y();
            int wz = anchor.z + pb.z();
            Vector3i cell = new Vector3i(wx, wy, wz);
            if (AssemblyObstructionUtil.isObstructedFootprintCell(world, job, cell, acc)) {
                addCell(cell);
            }
        }
        obstructedWorldCells.sort(
            Comparator
                .comparingInt((Vector3i v) -> v.x)
                .thenComparingInt(v -> v.y)
                .thenComparingInt(v -> v.z)
        );
    }

    public void appendAllObstructedCells(
        @Nonnull World world,
        @Nonnull PlotAssemblyJob job,
        @Nonnull ArrayList<Vector3i> out
    ) {
        pruneStale(world, job);
        out.addAll(obstructedWorldCells);
    }

    public boolean isEmpty() {
        return obstructedWorldCells.isEmpty();
    }

    public boolean containsWorldCell(int wx, int wy, int wz) {
        return byPackedCoord.containsKey(packBlock(wx, wy, wz));
    }

    /** Every obstructed footprint cell in range (solids such as {@code Soil_Grass} and plants such as {@code Plant_Grass}). */
    public void appendVisibleWithinRangeSq(
        @Nonnull World world,
        @Nonnull PlotAssemblyJob job,
        @Nonnull Vector3d observerPos,
        double rangeSq,
        @Nonnull List<Vector3i> out
    ) {
        appendLiveObstructedWithinRangeSq(world, job, observerPos, rangeSq, out);
    }

    public void appendVisibleNearChebyshev(
        @Nonnull World world,
        @Nonnull PlotAssemblyJob job,
        @Nonnull Vector3i centerWorld,
        int radius,
        @Nonnull ArrayList<Vector3i> out
    ) {
        pruneStale(world, job);
        LocalCachedChunkAccessor acc = chunkAccessor(world, job);
        int cx = centerWorld.x;
        int cy = centerWorld.y;
        int cz = centerWorld.z;
        for (int i = 0; i < obstructedWorldCells.size(); i++) {
            Vector3i cell = obstructedWorldCells.get(i);
            if (!AssemblyObstructionUtil.isObstructedFootprintCell(world, job, cell, acc)) {
                continue;
            }
            int dx = Math.abs(cell.x - cx);
            int dy = Math.abs(cell.y - cy);
            int dz = Math.abs(cell.z - cz);
            if (Math.max(Math.max(dx, dy), dz) <= radius) {
                out.add(cell);
            }
        }
    }

    public void appendAllVisibleObstructedCells(
        @Nonnull World world,
        @Nonnull PlotAssemblyJob job,
        @Nonnull ArrayList<Vector3i> out
    ) {
        pruneStale(world, job);
        appendLiveObstructed(world, job, out);
    }

    private void appendLiveObstructedWithinRangeSq(
        @Nonnull World world,
        @Nonnull PlotAssemblyJob job,
        @Nonnull Vector3d observerPos,
        double rangeSq,
        @Nonnull List<Vector3i> out
    ) {
        pruneStale(world, job);
        LocalCachedChunkAccessor acc = chunkAccessor(world, job);
        double ox = observerPos.x();
        double oy = observerPos.y();
        double oz = observerPos.z();
        for (int i = 0; i < obstructedWorldCells.size(); i++) {
            Vector3i cell = obstructedWorldCells.get(i);
            if (!AssemblyObstructionUtil.isObstructedFootprintCell(world, job, cell, acc)) {
                continue;
            }
            double cellCx = cell.x + 0.5;
            double cellCy = cell.y + 0.5;
            double cellCz = cell.z + 0.5;
            double dx = cellCx - ox;
            double dy = cellCy - oy;
            double dz = cellCz - oz;
            if (dx * dx + dy * dy + dz * dz <= rangeSq) {
                out.add(cell);
            }
        }
    }

    private void appendLiveObstructed(
        @Nonnull World world,
        @Nonnull PlotAssemblyJob job,
        @Nonnull ArrayList<Vector3i> out
    ) {
        LocalCachedChunkAccessor acc = chunkAccessor(world, job);
        for (int i = 0; i < obstructedWorldCells.size(); i++) {
            Vector3i cell = obstructedWorldCells.get(i);
            if (AssemblyObstructionUtil.isObstructedFootprintCell(world, job, cell, acc)) {
                out.add(cell);
            }
        }
    }

    private static long packBlock(int x, int y, int z) {
        return ((long) x & 0x3FFFFFL) << 42 | ((long) y & 0xFFFL) << 30 | ((long) z & 0x3FFFFFL);
    }
}

