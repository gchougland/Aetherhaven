package com.hexvane.aetherhaven.construction.assembly;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * Collects world-space frontier cells for active assembly jobs, deduped and filtered by distance from an observer.
 * Shared by {@link PlotAssemblyPreviewSystem} and {@link BuildingStaffFrontierTracerInteraction}.
 */
public final class AssemblyFrontierWorldCells {
    public static final double DEFAULT_RANGE = 96.0;
    private static final double DEFAULT_RANGE_SQ = DEFAULT_RANGE * DEFAULT_RANGE;

    private AssemblyFrontierWorldCells() {}

    /**
     * Fills {@code out} with frontier cells within {@link #DEFAULT_RANGE} blocks of {@code observerPos}, sorted by
     * {@code (x, y, z)} for stable signatures. Duplicates across plots are skipped.
     */
    public static void collectWithinDefaultRange(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Vector3d observerPos,
        @Nonnull List<Vector3i> out
    ) {
        collectWithinRangeSq(world, plugin, observerPos, DEFAULT_RANGE_SQ, out);
    }

    public static void collectWithinRangeSq(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Vector3d observerPos,
        double rangeSq,
        @Nonnull List<Vector3i> out
    ) {
        out.clear();
        List<PlotAssemblyJob> jobs = new ArrayList<>(AssemblyWorldRegistry.jobs(world));
        jobs.sort(Comparator.comparing(PlotAssemblyJob::plotId));
        ArrayList<Vector3i> frontierScratch = new ArrayList<>(256);
        double ox = observerPos.getX();
        double oy = observerPos.getY();
        double oz = observerPos.getZ();
        for (PlotAssemblyJob job : jobs) {
            TownRecord town = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).findTownOwningPlot(job.plotId());
            if (town == null) {
                continue;
            }
            PlotInstance plot = town.findPlotById(job.plotId());
            if (plot == null || plot.getState() != PlotInstanceState.ASSEMBLING) {
                continue;
            }
            frontierScratch.clear();
            PlotAssemblyService.appendFrontierWorldCells(job, plot, frontierScratch);
            for (int fi = 0; fi < frontierScratch.size(); fi++) {
                Vector3i cell = frontierScratch.get(fi);
                double cx = cell.x + 0.5;
                double cy = cell.y + 0.5;
                double cz = cell.z + 0.5;
                double dx = cx - ox;
                double dy = cy - oy;
                double dz = cz - oz;
                if (dx * dx + dy * dy + dz * dz > rangeSq) {
                    continue;
                }
                boolean duplicate = false;
                for (Vector3i c : out) {
                    if (c.equals(cell)) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) {
                    out.add(cell);
                }
            }
        }
        out.sort(
            Comparator
                .comparingInt((Vector3i v) -> v.x)
                .thenComparingInt(v -> v.y)
                .thenComparingInt(v -> v.z)
        );
    }
}
