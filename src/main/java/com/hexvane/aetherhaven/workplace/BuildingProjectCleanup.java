package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownRecord;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

/** Retires finished projects. BuildingOrders also guards credit when native chunk loading drains tasks early. */
public final class BuildingProjectCleanup {
    private BuildingProjectCleanup() {}

    /** The executor must queue onto the owning world thread, never run inline inside ConstructionCompleter. */
    public static void afterCompletion(Executor worldThread, TownRecord town, UUID plot, Runnable onChanged) {
        worldThread.execute(() -> {
            if (remove(town, Set.of(plot))) onChanged.run();
        });
    }

    /** Repair known completed entries from saved queues; unavailable projects are retained for player review. */
    public static boolean reconcile(TownRecord town) {
        var completed = new HashSet<UUID>();
        for (var plot : town.getPlotInstances())
            if (plot.getState() == PlotInstanceState.COMPLETE) completed.add(plot.getPlotId());
        for (var wall : town.getWallSegments()) completed.add(wall.getSegmentId());
        return remove(town, completed);
    }

    private static boolean remove(TownRecord town, Set<UUID> completed) {
        if (completed.isEmpty()) return false;
        boolean changed = false;
        for (var site : town.getApprenticeWorkplaces().sites()) {
            // A yard can be redesignated while retaining its orders. Do not instantiate plans on other sites.
            var plan = site.existingBuilding();
            if (plan != null) changed |= plan.removeCompleted(completed);
        }
        return changed;
    }
}
