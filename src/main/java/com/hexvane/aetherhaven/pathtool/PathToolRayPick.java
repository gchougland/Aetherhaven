package com.hexvane.aetherhaven.pathtool;

import com.hypixel.hytale.math.vector.Vector3d;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Picks a path control node the player is looking at (ray vs sphere), not the nearest to the
 * target block (which caused false hits off crosshair).
 */
public final class PathToolRayPick {
    private PathToolRayPick() {}

    /**
     * @param origin    eye position
     * @param direction view direction (need not be normalized; will be normalized)
     * @return the node on the closest positive hit along the ray, or null
     */
    @Nullable
    public static PathToolNode pickNode(
        @Nonnull Vector3d origin,
        @Nonnull Vector3d direction,
        double maxDistance,
        @Nonnull List<PathToolNode> nodes,
        double nodeRadius
    ) {
        double dx = direction.getX();
        double dy = direction.getY();
        double dz = direction.getZ();
        double dLen = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dLen < 1.0e-6) {
            return null;
        }
        dx /= dLen;
        dy /= dLen;
        dz /= dLen;
        double r2 = nodeRadius * nodeRadius;
        double bestT = Double.POSITIVE_INFINITY;
        PathToolNode best = null;
        for (PathToolNode n : nodes) {
            double lx = origin.getX() - n.getX();
            double ly = origin.getY() - n.getY();
            double lz = origin.getZ() - n.getZ();
            double b = 2.0 * (dx * lx + dy * ly + dz * lz);
            double c = lx * lx + ly * ly + lz * lz - r2;
            double disc = b * b - 4.0 * c;
            if (disc < 0.0) {
                continue;
            }
            double s = Math.sqrt(disc);
            double t0 = 0.5 * (-b - s);
            double t1 = 0.5 * (-b + s);
            for (int i = 0; i < 2; i++) {
                double t = i == 0 ? t0 : t1;
                if (t > 0.0 && t <= maxDistance + 1.0e-3 && t < bestT) {
                    bestT = t;
                    best = n;
                }
            }
        }
        return best;
    }
}
