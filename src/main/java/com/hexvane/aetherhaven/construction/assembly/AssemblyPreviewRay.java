package com.hexvane.aetherhaven.construction.assembly;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.math.vector.Transform;
import java.util.ArrayList;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Penetrating view ray against assembly frontier preview cells (no preview block in the world).
 */
public final class AssemblyPreviewRay {
    private AssemblyPreviewRay() {}

    /**
     * @return world cell of a frontier prefab block whose unit cube the ray hits; when several frontier cells are
     *         hit, prefers the closest hit along the ray (smallest entry distance {@code t}).
     */
    @Nullable
    public static Vector3i findPenetratingPreviewCellHit(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        double maxDistance,
        @Nonnull Store<EntityStore> store
    ) {
        Transform look = TargetUtil.getLook(playerRef, store);
        Vector3d o = look.getPosition();
        Vector3d d = look.getDirection();
        double dx = d.getX();
        double dy = d.getY();
        double dz = d.getZ();
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0e-6) {
            return null;
        }
        dx /= len;
        dy /= len;
        dz /= len;
        double bestT = Double.POSITIVE_INFINITY;
        Vector3i bestCell = null;
        ArrayList<Vector3i> frontierCells = new ArrayList<>(128);
        for (PlotAssemblyJob job : AssemblyWorldRegistry.jobs(world)) {
            TownRecord town = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).findTownOwningPlot(job.plotId());
            if (town == null) {
                continue;
            }
            PlotInstance plot = town.findPlotById(job.plotId());
            if (plot == null || plot.getState() != PlotInstanceState.ASSEMBLING) {
                continue;
            }
            frontierCells.clear();
            PlotAssemblyService.appendFrontierWorldCells(job, plot, frontierCells);
            for (int i = 0; i < frontierCells.size(); i++) {
                Vector3i cell = frontierCells.get(i);
                Double t = rayEnterUnitCube(o.getX(), o.getY(), o.getZ(), dx, dy, dz, cell.x, cell.y, cell.z, maxDistance);
                if (t != null && t < bestT) {
                    bestT = t;
                    bestCell = cell;
                }
            }
        }
        return bestCell;
    }

    /**
     * Slab method: entry distance along normalized ray for unit cube [cx,cx+1]³, clamped to {@code maxDistance}.
     */
    @Nullable
    private static Double rayEnterUnitCube(
        double ox,
        double oy,
        double oz,
        double dx,
        double dy,
        double dz,
        int cx,
        int cy,
        int cz,
        double maxDistance
    ) {
        double tMin = 0.0;
        double tMax = maxDistance;
        double[][] slab = {{cx, cx + 1.0}, {cy, cy + 1.0}, {cz, cz + 1.0}};
        double[] o = {ox, oy, oz};
        double[] dir = {dx, dy, dz};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(dir[i]) < 1.0e-9) {
                if (o[i] < slab[i][0] || o[i] > slab[i][1]) {
                    return null;
                }
            } else {
                double inv = 1.0 / dir[i];
                double t1 = (slab[i][0] - o[i]) * inv;
                double t2 = (slab[i][1] - o[i]) * inv;
                if (t1 > t2) {
                    double s = t1;
                    t1 = t2;
                    t2 = s;
                }
                tMin = Math.max(tMin, t1);
                tMax = Math.min(tMax, t2);
                if (tMin > tMax) {
                    return null;
                }
            }
        }
        if (tMax < 0.0) {
            return null;
        }
        double hit = tMin >= 0.0 ? tMin : tMax;
        if (hit < 0.0 || hit > maxDistance) {
            return null;
        }
        return hit;
    }
}
