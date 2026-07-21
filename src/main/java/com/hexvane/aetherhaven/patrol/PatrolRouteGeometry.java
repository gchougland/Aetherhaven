package com.hexvane.aetherhaven.patrol;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Horizontal distance from a position to a patrol polyline. */
public final class PatrolRouteGeometry {
    private PatrolRouteGeometry() {}

    /** Squared horizontal distance to the nearest point on the route, or {@link Double#MAX_VALUE} if invalid. */
    public static double minHorizontalDistanceSqToRoute(@Nonnull Vector3d pos, @Nullable PatrolRouteRecord route) {
        if (route == null || route.nodes == null || route.nodes.isEmpty()) {
            return Double.MAX_VALUE;
        }
        double min = Double.MAX_VALUE;
        int count = route.nodes.size();
        for (PatrolRouteNode n : route.nodes) {
            if (n == null) {
                continue;
            }
            double dx = pos.x - n.x;
            double dz = pos.z - n.z;
            min = Math.min(min, dx * dx + dz * dz);
        }
        if (count < 2) {
            return min;
        }
        int segmentCount = route.isClosedLoop() ? count : count - 1;
        for (int i = 0; i < segmentCount; i++) {
            PatrolRouteNode a = route.nodes.get(i);
            PatrolRouteNode b = route.nodes.get((i + 1) % count);
            if (a == null || b == null) {
                continue;
            }
            min = Math.min(min, distanceSqPointToSegment(pos.x, pos.z, a.x, a.z, b.x, b.z));
        }
        return min;
    }

    private static double distanceSqPointToSegment(
        double px,
        double pz,
        double ax,
        double az,
        double bx,
        double bz
    ) {
        double abx = bx - ax;
        double abz = bz - az;
        double lenSq = abx * abx + abz * abz;
        if (lenSq < 1.0e-8) {
            double dx = px - ax;
            double dz = pz - az;
            return dx * dx + dz * dz;
        }
        double t = ((px - ax) * abx + (pz - az) * abz) / lenSq;
        t = Math.max(0.0, Math.min(1.0, t));
        double cx = ax + t * abx;
        double cz = az + t * abz;
        double dx = px - cx;
        double dz = pz - cz;
        return dx * dx + dz * dz;
    }
}
