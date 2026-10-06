package com.hexvane.aetherhaven.construction.assembly;

import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import java.util.List;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Ranks already permitted, live frontier cells without letting other plots steal the current building's guide. */
final class BuildingStaffTracerTargets {
    record Candidate(Vector3i cell, PlotFootprintRecord footprint, boolean clearing) {}

    private BuildingStaffTracerTargets() {}

    @Nullable
    static Vector3i select(List<Candidate> eligible, Vector3d feet, Vector3d tip, double range) {
        int feetX = (int) Math.floor(feet.x), feetY = (int) Math.floor(feet.y), feetZ = (int) Math.floor(feet.z);
        double rangeSq = range * range, bestSq = Double.POSITIVE_INFINITY;
        int bestPriority = Integer.MAX_VALUE;
        Vector3i best = null;
        for (Candidate candidate : eligible) {
            Vector3i cell = candidate.cell();
            double dx = cell.x + 0.5 - tip.x, dy = cell.y + 0.5 - tip.y, dz = cell.z + 0.5 - tip.z;
            double distanceSq = dx * dx + dy * dy + dz * dz;
            if (!(distanceSq <= rangeSq)) continue;
            boolean inside = candidate.footprint().containsBlock(feetX, feetY, feetZ);
            // Keep clearing ahead of placement within each group, after permission and frontier validation.
            int priority = (inside ? 0 : 2) + (candidate.clearing() ? 0 : 1);
            if (priority < bestPriority || (priority == bestPriority && distanceSq < bestSq)) {
                best = cell;
                bestSq = distanceSq;
                bestPriority = priority;
            }
        }
        return best;
    }
}
