package com.hexvane.aetherhaven.autonomy.pathnav;

import com.hexvane.aetherhaven.autonomy.VillagerBlockUtil;
import org.joml.Vector3d;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * Post-processes graph waypoints for vanilla {@code Seek} + Leash: spline/path Y can differ from the surface the
 * pathfinder can use. Start-of-route reprojection (foot on the route polyline) is done in {@link PathNavGraphService}.
 */
public final class PathNavTravelWaypoints {
    private static final double EPS_FINAL = 0.3;
    /** When the last graph node is farther than this from the goal, omit the goal from waypoints (direct Seek after path). */
    public static final double MAX_ON_PATH_FINAL_LEG = 3.0;
    /** Drop a terminal path node when the cut toward the POI is sharper than this (cosine). */
    private static final double SHARP_APPROACH_DOT = 0.55;
    private static final double SHARP_APPROACH_MAX_LEG = 5.5;
    private static final double COLLINEAR_DOT = 0.985;

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
        return prepareForSeek(world, fromNpc, path, finalTarget, npcFeetYBlock, 2.5);
    }

    /**
     * @param minWaypointSpacing minimum horizontal spacing between leash waypoints (typically {@link
     *     com.hexvane.aetherhaven.config.AetherhavenPluginConfig#getPathNavNodeSpacing()})
     */
    @Nonnull
    public static List<Vector3d> prepareForSeek(
        @Nonnull World world,
        @Nonnull Vector3d fromNpc,
        @Nonnull List<Vector3d> path,
        @Nonnull Vector3d finalTarget,
        int npcFeetYBlock,
        double minWaypointSpacing
    ) {
        if (path.isEmpty()) {
            return path;
        }
        double spacing = Math.max(1.5, minWaypointSpacing);
        ArrayList<Vector3d> w = new ArrayList<>(path.size());
        w.addAll(path);
        for (int i = 0; i < w.size(); i++) {
            Vector3d p = w.get(i);
            boolean isFinal = horiz(p, finalTarget) < EPS_FINAL && Math.abs(p.y() - finalTarget.y()) < 1.0;
            if (isFinal) {
                w.set(i, new Vector3d(finalTarget.x(), finalTarget.y(), finalTarget.z()));
            } else {
                int bx = (int) Math.floor(p.x());
                int bz = (int) Math.floor(p.z());
                int yHint = (int) Math.floor(p.y());
                int standY = VillagerBlockUtil.findStandYForNav(world, bx, bz, yHint, npcFeetYBlock, null);
                if (standY != Integer.MIN_VALUE) {
                    w.set(i, new Vector3d(p.x(), standY + 0.02, p.z()));
                }
            }
        }
        trimSharpApproachToTarget(w, finalTarget);
        removeCollinearWaypoints(w);
        dedupeBySpacing(w, spacing);
        return w;
    }

    /**
     * When the last placed-path node sits beside a doorway but the POI leash is inside at a sharp angle, drop that
     * terminal node so the NPC walks from the previous path point toward the goal instead of cutting the corner.
     */
    private static void trimSharpApproachToTarget(@Nonnull ArrayList<Vector3d> w, @Nonnull Vector3d finalTarget) {
        if (w.size() < 2) {
            return;
        }
        Vector3d last = w.get(w.size() - 1);
        if (horiz(last, finalTarget) < EPS_FINAL) {
            return;
        }
        Vector3d prev = w.get(w.size() - 2);
        double leg = horiz(last, finalTarget);
        if (leg > SHARP_APPROACH_MAX_LEG) {
            return;
        }
        double dot = directionDot(prev, last, last, finalTarget);
        if (dot < SHARP_APPROACH_DOT) {
            w.remove(w.size() - 1);
        }
    }

    private static void removeCollinearWaypoints(@Nonnull ArrayList<Vector3d> w) {
        for (int i = 1; i + 1 < w.size(); ) {
            Vector3d a = w.get(i - 1);
            Vector3d b = w.get(i);
            Vector3d c = w.get(i + 1);
            double ab = horiz(a, b);
            double bc = horiz(b, c);
            if (ab < 0.15 || bc < 0.15) {
                i++;
                continue;
            }
            double dot = directionDot(a, b, b, c);
            if (dot >= COLLINEAR_DOT) {
                w.remove(i);
            } else {
                i++;
            }
        }
    }

    private static void dedupeBySpacing(@Nonnull ArrayList<Vector3d> w, double spacing) {
        for (int i = 1; i < w.size(); ) {
            if (horiz(w.get(i - 1), w.get(i)) < spacing) {
                w.remove(i);
            } else {
                i++;
            }
        }
    }

    private static double directionDot(
        @Nonnull Vector3d fromA,
        @Nonnull Vector3d toA,
        @Nonnull Vector3d fromB,
        @Nonnull Vector3d toB
    ) {
        double ax = toA.x() - fromA.x();
        double az = toA.z() - fromA.z();
        double bx = toB.x() - fromB.x();
        double bz = toB.z() - fromB.z();
        double al = Math.hypot(ax, az);
        double bl = Math.hypot(bx, bz);
        if (al < 1.0e-4 || bl < 1.0e-4) {
            return 1.0;
        }
        return (ax / al) * (bx / bl) + (az / al) * (bz / bl);
    }

    private static double horiz(@Nonnull Vector3d a, @Nonnull Vector3d b) {
        double dx = a.x() - b.x();
        double dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
