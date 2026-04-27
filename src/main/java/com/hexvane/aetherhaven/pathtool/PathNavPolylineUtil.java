package com.hexvane.aetherhaven.pathtool;

import com.hypixel.hytale.math.vector.Vector3d;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

/** Utilities for extracting equidistant centerline points from spline samples. */
public final class PathNavPolylineUtil {
    private PathNavPolylineUtil() {}

    @Nonnull
    public static List<PathNavPoint> resampleCenterline(
        @Nonnull List<PathSplineUtil.PathSample> samples,
        double spacingBlocks
    ) {
        List<PathNavPoint> out = new ArrayList<>();
        if (samples.isEmpty()) {
            return out;
        }
        double spacing = Math.max(0.25, spacingBlocks);
        Vector3d first = samples.get(0).position;
        out.add(new PathNavPoint(first.getX(), first.getY(), first.getZ()));

        Vector3d cursor = new Vector3d(first.getX(), first.getY(), first.getZ());
        double carry = 0.0;
        for (int i = 1; i < samples.size(); i++) {
            Vector3d segEnd = samples.get(i).position;
            Vector3d segStart = new Vector3d(cursor.getX(), cursor.getY(), cursor.getZ());
            double segDx = segEnd.getX() - segStart.getX();
            double segDy = segEnd.getY() - segStart.getY();
            double segDz = segEnd.getZ() - segStart.getZ();
            double segLen = Math.sqrt(segDx * segDx + segDy * segDy + segDz * segDz);
            if (segLen <= 1.0e-6) {
                cursor = segEnd;
                continue;
            }
            while (carry + segLen >= spacing) {
                double step = spacing - carry;
                double t = step / segLen;
                double nx = segStart.getX() + segDx * t;
                double ny = segStart.getY() + segDy * t;
                double nz = segStart.getZ() + segDz * t;
                out.add(new PathNavPoint(nx, ny, nz));
                segStart = new Vector3d(nx, ny, nz);
                segDx = segEnd.getX() - segStart.getX();
                segDy = segEnd.getY() - segStart.getY();
                segDz = segEnd.getZ() - segStart.getZ();
                segLen = Math.sqrt(segDx * segDx + segDy * segDy + segDz * segDz);
                carry = 0.0;
                if (segLen <= 1.0e-6) {
                    break;
                }
            }
            carry += segLen;
            cursor = segEnd;
        }

        Vector3d last = samples.get(samples.size() - 1).position;
        if (out.isEmpty() || distSq(out.get(out.size() - 1), last) > 1.0e-4) {
            out.add(new PathNavPoint(last.getX(), last.getY(), last.getZ()));
        }
        return out;
    }

    private static double distSq(@Nonnull PathNavPoint p, @Nonnull Vector3d v) {
        double dx = p.x - v.getX();
        double dy = p.y - v.getY();
        double dz = p.z - v.getZ();
        return dx * dx + dy * dy + dz * dz;
    }
}
