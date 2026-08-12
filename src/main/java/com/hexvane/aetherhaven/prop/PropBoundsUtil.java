package com.hexvane.aetherhaven.prop;

import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * Padded prop bounding boxes and look-ray picking.
 *
 * <p>{@link #PROP_BOUNDS_PADDING} hardcodes the value the parent plugin will expose as
 * {@code AetherhavenConstants.PROP_BOUNDS_PADDING} once that constant is wired in.
 */
public final class PropBoundsUtil {
    public static final double PROP_BOUNDS_PADDING = 0.2;

    private PropBoundsUtil() {}

    /** Axis-aligned box with {@code padding} added on every side of {@code fp} (block-inclusive to world-space). */
    public record PaddedBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {}

    @Nonnull
    public static PaddedBox toPaddedBox(@Nonnull PlotFootprintRecord fp, double padding) {
        return new PaddedBox(
            fp.getMinX() - padding,
            fp.getMinY() - padding,
            fp.getMinZ() - padding,
            fp.getMaxX() + 1.0 + padding,
            fp.getMaxY() + 1.0 + padding,
            fp.getMaxZ() + 1.0 + padding
        );
    }

    /**
     * Nearest prop whose padded footprint the player's look ray crosses, within {@code maxDistance}. Resolves each
     * candidate's prefab to compute its footprint; fine for the handful of nearby props expected in normal play.
     */
    @Nullable
    public static PropInstance findPropAlongLookRay(
        @Nonnull PropRegistry registry,
        @Nonnull World world,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull PropCatalog catalog,
        double maxDistance
    ) {
        Transform look = TargetUtil.getLook(playerRef, store);
        Vector3d o = look.getPosition();
        Vector3d d = look.getDirection();
        double len = d.length();
        if (len < 1.0e-6) {
            return null;
        }
        double dx = d.x() / len;
        double dy = d.y() / len;
        double dz = d.z() / len;
        double ox = o.x();
        double oy = o.y();
        double oz = o.z();

        double bestT = Double.POSITIVE_INFINITY;
        PropInstance best = null;
        for (PropInstance instance : registry.all()) {
            IPrefabBuffer buffer = PropLookupUtil.resolveBuffer(catalog, instance);
            if (buffer == null) {
                continue;
            }
            PlotFootprintRecord fp = PropPrefabOps.footprint(instance.getAnchor(), instance.getYaw(), buffer);
            PaddedBox box = toPaddedBox(fp, PROP_BOUNDS_PADDING);
            Double t = rayEnterBox(ox, oy, oz, dx, dy, dz, box, maxDistance);
            if (t != null && t < bestT) {
                bestT = t;
                best = instance;
            }
        }
        return best;
    }

    /** Slab method entry distance along the normalized ray, or {@code null} when it misses / exceeds {@code maxDistance}. */
    @Nullable
    static Double rayEnterBox(
        double ox,
        double oy,
        double oz,
        double dx,
        double dy,
        double dz,
        @Nonnull PaddedBox box,
        double maxDistance
    ) {
        double tMin = 0.0;
        double tMax = maxDistance;
        double[][] slab = {
            {box.minX(), box.maxX()}, {box.minY(), box.maxY()}, {box.minZ(), box.maxZ()}
        };
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
