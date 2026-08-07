package com.hexvane.aetherhaven.pathtool;

import org.joml.Vector3d;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Ray pick against committed path undo cells or nav-only polylines. */
public final class PathToolRemovePick {
    private static final double CELL_PICK_RADIUS = 0.55;
    private static final double NAV_PICK_RADIUS = 0.45;

    private PathToolRemovePick() {}

    /**
     * @return id of the closest path whose undo cell is hit along the ray, or null
     */
    @Nullable
    public static UUID pickPathId(
        @Nonnull Vector3d origin,
        @Nonnull Vector3d direction,
        double maxDistance,
        @Nonnull List<PathCommitRecord> records
    ) {
        double dx = direction.x();
        double dy = direction.y();
        double dz = direction.z();
        double dLen = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dLen < 1.0e-6) {
            return null;
        }
        dx /= dLen;
        dy /= dLen;
        dz /= dLen;
        double bestT = Double.POSITIVE_INFINITY;
        UUID bestId = null;
        for (PathCommitRecord rec : records) {
            if (rec == null || rec.id == null) {
                continue;
            }
            UUID id;
            try {
                id = rec.getIdUuid();
            } catch (Exception e) {
                continue;
            }
            List<PathNavPoint> nav = PathToolNavPreviewUtil.navPointsForPreview(rec);
            if (nav.size() >= 1) {
                for (PathNavPoint p : nav) {
                    if (p == null) {
                        continue;
                    }
                    double pickY = PathDebugPreviewUtil.navNodeVisualCenterY(p.y, false);
                    @Nullable
                    Double t = rayHitSphere(origin, dx, dy, dz, maxDistance, p.x, pickY, p.z, NAV_PICK_RADIUS);
                    if (t != null && t < bestT) {
                        bestT = t;
                        bestId = id;
                    }
                }
            } else if (rec.undo != null && !rec.undo.isEmpty()) {
                for (PathToolUndoCell c : rec.undo) {
                    if (c == null) {
                        continue;
                    }
                    @Nullable
                    Double t = rayHitSphere(origin, dx, dy, dz, maxDistance, c.x + 0.5, c.y + 0.5, c.z + 0.5, CELL_PICK_RADIUS);
                    if (t != null && t < bestT) {
                        bestT = t;
                        bestId = id;
                    }
                }
            }
        }
        return bestId;
    }

    @Nullable
    private static Double rayHitSphere(
        @Nonnull Vector3d origin,
        double dx,
        double dy,
        double dz,
        double maxDistance,
        double cx,
        double cy,
        double cz,
        double radius
    ) {
        double r2 = radius * radius;
        double lx = origin.x() - cx;
        double ly = origin.y() - cy;
        double lz = origin.z() - cz;
        double b = 2.0 * (dx * lx + dy * ly + dz * lz);
        double cc = lx * lx + ly * ly + lz * lz - r2;
        double disc = b * b - 4.0 * cc;
        if (disc < 0.0) {
            return null;
        }
        double s = Math.sqrt(disc);
        double t0 = 0.5 * (-b - s);
        double t1 = 0.5 * (-b + s);
        for (int i = 0; i < 2; i++) {
            double t = i == 0 ? t0 : t1;
            if (t > 0.0 && t <= maxDistance + 1.0e-3) {
                return t;
            }
        }
        return null;
    }
}
