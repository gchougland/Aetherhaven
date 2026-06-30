package com.hexvane.aetherhaven.construction.assembly;

import org.joml.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.LocalCachedChunkAccessor;
import java.util.ArrayList;
import java.util.Comparator;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Growth frontier for the clearing phase: obstructed footprint cells with at least one 6-neighbor open to air (live
 * world check, not cache-only). Mirrors {@link PlotAssemblyFrontier} for destruction.
 */
public final class AssemblyClearingFrontier {
    private static final int[][] NEIGHBOR_OFFSETS = {
        {1, 0, 0},
        {-1, 0, 0},
        {0, 1, 0},
        {0, -1, 0},
        {0, 0, 1},
        {0, 0, -1},
    };

    private AssemblyClearingFrontier() {}

    /**
     * {@code true} when {@code (wx,wy,wz)} is still obstructed and touches open space (world air, outside footprint, or
     * a footprint cell that is no longer solid).
     */
    public static boolean isFrontierCellLive(
        @Nonnull World world,
        @Nonnull PlotAssemblyJob job,
        int wx,
        int wy,
        int wz,
        @Nonnull LocalCachedChunkAccessor chunkAccessor
    ) {
        Vector3i cell = new Vector3i(wx, wy, wz);
        if (!AssemblyObstructionUtil.isObstructedFootprintCell(world, job, cell, chunkAccessor)) {
            return false;
        }
        for (int i = 0; i < NEIGHBOR_OFFSETS.length; i++) {
            int[] d = NEIGHBOR_OFFSETS[i];
            int nx = wx + d[0];
            int ny = wy + d[1];
            int nz = wz + d[2];
            Vector3i neighbor = new Vector3i(nx, ny, nz);
            if (!AssemblyObstructionUtil.footprintContainsWorldCell(job, neighbor)) {
                return true;
            }
            if (!AssemblyObstructionUtil.blocksClearingExposureAt(world, nx, ny, nz, chunkAccessor)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Obstructed cells on the clearing frontier. If none qualify, falls back to the lowest-Y obstructed layer.
     */
    @Nonnull
    public static ArrayList<Vector3i> frontierWorldCellsLive(
        @Nonnull World world,
        @Nonnull PlotAssemblyJob job,
        @Nonnull ArrayList<Vector3i> obstructedWorldCells,
        @Nonnull LocalCachedChunkAccessor chunkAccessor
    ) {
        return frontierWorldCellsLive(world, job, obstructedWorldCells, chunkAccessor, null, 0);
    }

    /**
     * Like {@link #frontierWorldCellsLive(World, PlotAssemblyJob, ArrayList, LocalCachedChunkAccessor)} but only
     * considers obstructed cells in {@code activeSectionFlat} when {@code sectionMapper} is non-null.
     */
    @Nonnull
    public static ArrayList<Vector3i> frontierWorldCellsLive(
        @Nonnull World world,
        @Nonnull PlotAssemblyJob job,
        @Nonnull ArrayList<Vector3i> obstructedWorldCells,
        @Nonnull LocalCachedChunkAccessor chunkAccessor,
        @Nullable AssemblySectionMapper sectionMapper,
        int activeSectionFlat
    ) {
        ArrayList<Vector3i> scoped = obstructedWorldCells;
        if (sectionMapper != null) {
            scoped = new ArrayList<>();
            Vector3i anchor = job.anchor();
            for (int i = 0; i < obstructedWorldCells.size(); i++) {
                Vector3i cell = obstructedWorldCells.get(i);
                if (sectionMapper.isWorldCellInSection(anchor, cell.x, cell.y, cell.z, activeSectionFlat)) {
                    scoped.add(cell);
                }
            }
        }
        ArrayList<Vector3i> frontier = new ArrayList<>();
        ArrayList<Vector3i> stillObstructed = new ArrayList<>();
        for (int i = 0; i < scoped.size(); i++) {
            Vector3i cell = scoped.get(i);
            if (!AssemblyObstructionUtil.isObstructedFootprintCell(world, job, cell, chunkAccessor)) {
                continue;
            }
            stillObstructed.add(cell);
            if (isFrontierCellLive(world, job, cell.x, cell.y, cell.z, chunkAccessor)) {
                frontier.add(cell);
            }
        }
        if (!frontier.isEmpty() || stillObstructed.isEmpty()) {
            return frontier;
        }
        int minY = Integer.MAX_VALUE;
        for (int i = 0; i < stillObstructed.size(); i++) {
            minY = Math.min(minY, stillObstructed.get(i).y);
        }
        for (int i = 0; i < stillObstructed.size(); i++) {
            Vector3i cell = stillObstructed.get(i);
            if (cell.y == minY) {
                frontier.add(cell);
            }
        }
        frontier.sort(
            Comparator
                .comparingInt((Vector3i v) -> v.x)
                .thenComparingInt(v -> v.y)
                .thenComparingInt(v -> v.z)
        );
        return frontier;
    }
}
