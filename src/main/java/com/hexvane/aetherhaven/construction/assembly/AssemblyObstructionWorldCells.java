package com.hexvane.aetherhaven.construction.assembly;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownRecord;
import org.joml.Vector3d;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * Collects clearing frontier world cells for jobs in {@link PlotAssemblyPhase#CLEARING}, deduped and filtered by
 * distance from an observer. Shared by preview and tracer systems.
 */
public final class AssemblyObstructionWorldCells {
    public static final double DEFAULT_RANGE = AssemblyFrontierWorldCells.DEFAULT_RANGE;
    private static final double DEFAULT_RANGE_SQ = DEFAULT_RANGE * DEFAULT_RANGE;

    private AssemblyObstructionWorldCells() {}

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
        for (PlotAssemblyJob job : jobs) {
            if (AssemblyWorldRegistry.phase(world, job.plotId()) != PlotAssemblyPhase.CLEARING) {
                continue;
            }
            TownRecord town = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).findTownOwningPlot(job.plotId());
            if (town == null) {
                continue;
            }
            PlotInstance plot = town.findPlotById(job.plotId());
            if (plot == null || plot.getState() != PlotInstanceState.ASSEMBLING) {
                continue;
            }
            PlotAssemblyClearingRuntime clearingRt = AssemblyWorldRegistry.clearingRuntime(world, job.plotId());
            if (clearingRt == null || clearingRt.isEmpty()) {
                continue;
            }
            ArrayList<Vector3i> frontierScratch = new ArrayList<>(256);
            PlotAssemblyService.appendClearingFrontierWorldCells(world, job, plot, frontierScratch);
            double ox = observerPos.x();
            double oy = observerPos.y();
            double oz = observerPos.z();
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
                out.add(cell);
            }
        }
        out.sort(
            Comparator
                .comparingInt((Vector3i v) -> v.x)
                .thenComparingInt(v -> v.y)
                .thenComparingInt(v -> v.z)
        );
        dedupeSortedByBlockCoords(out);
    }

    private static void dedupeSortedByBlockCoords(@Nonnull List<Vector3i> sorted) {
        int w = 0;
        for (int r = 0; r < sorted.size(); r++) {
            Vector3i c = sorted.get(r);
            if (w == 0) {
                sorted.set(w++, c);
            } else {
                Vector3i p = sorted.get(w - 1);
                if (p.x != c.x || p.y != c.y || p.z != c.z) {
                    sorted.set(w++, c);
                }
            }
        }
        if (w < sorted.size()) {
            sorted.subList(w, sorted.size()).clear();
        }
    }
}
