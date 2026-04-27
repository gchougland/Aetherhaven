package com.hexvane.aetherhaven.autonomy.pathnav;

import com.hexvane.aetherhaven.autonomy.VillagerBlockUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * Post-processes graph waypoints for vanilla {@code Seek} + Leash: spline/path Y can differ from the surface the
 * pathfinder can use. Start-of-route reprojection (foot on the route polyline) is done in {@link PathNavGraphService}.
 */
public final class PathNavTravelWaypoints {
    private static final double HORIZ_DEDUP = 0.2;
    private static final double EPS_FINAL = 0.3;

    private PathNavTravelWaypoints() {}

    /**
     * @param fromNpc feet position when travel starts
     * @param finalTarget same as {@link com.hexvane.aetherhaven.autonomy.VillagerAutonomyState} travel / POI leash
     */
    @Nonnull
    public static List<Vector3d> prepareForSeek(
        @Nonnull World world,
        @Nonnull Vector3d fromNpc,
        @Nonnull List<Vector3d> path,
        @Nonnull Vector3d finalTarget,
        int npcFeetYBlock
    ) {
        if (path.isEmpty()) {
            return path;
        }
        ArrayList<Vector3d> w = new ArrayList<>(path.size());
        w.addAll(path);
        for (int i = 0; i < w.size(); i++) {
            Vector3d p = w.get(i);
            boolean isFinal = horiz(p, finalTarget) < EPS_FINAL && Math.abs(p.getY() - finalTarget.getY()) < 1.0;
            if (isFinal) {
                w.set(i, new Vector3d(finalTarget.getX(), finalTarget.getY(), finalTarget.getZ()));
            } else {
                int bx = (int) Math.floor(p.getX());
                int bz = (int) Math.floor(p.getZ());
                int yHint = (int) Math.floor(p.getY());
                int standY = VillagerBlockUtil.findStandYForNav(world, bx, bz, yHint, npcFeetYBlock, null);
                if (standY != Integer.MIN_VALUE) {
                    w.set(
                        i,
                        new Vector3d(p.getX(), standY + 0.02, p.getZ())
                    );
                }
            }
        }
        for (int i = 1; i < w.size(); ) {
            if (horiz(w.get(i - 1), w.get(i)) < HORIZ_DEDUP) {
                w.remove(i);
            } else {
                i++;
            }
        }
        return w;
    }

    private static double horiz(@Nonnull Vector3d a, @Nonnull Vector3d b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
