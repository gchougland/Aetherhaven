package com.hexvane.aetherhaven.pathtool;

import org.joml.Vector3d;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * Chains cubic Beziers between control points; per-node yaw sets tangent handles for the spline shape.
 */
public final class PathSplineUtil {
    private static final Vector3d UP = new Vector3d(0.0, 1.0, 0.0);
    private static final double HANDLE_FRAC = 0.35;

    private PathSplineUtil() {}

    @Nonnull
    public static List<PathSample> sample(
        @Nonnull List<PathToolNode> nodes,
        int samplesPerBlockSegment
    ) {
        List<PathSample> out = new ArrayList<>();
        if (nodes.size() < 2) {
            return out;
        }
        int sps = Math.max(1, samplesPerBlockSegment);
        for (int i = 0; i < nodes.size() - 1; i++) {
            PathToolNode a = nodes.get(i);
            PathToolNode b = nodes.get(i + 1);
            Vector3d p0 = a.getPosition();
            Vector3d p3 = b.getPosition();
            Vector3d tanA = forwardHorizontal(a.getYawDeg());
            Vector3d tanB = forwardHorizontal(b.getYawDeg());
            double dist = p0.distance(p3);
            double handle = Math.max(0.5, dist * HANDLE_FRAC);
            Vector3d p1 = new Vector3d(p0.x() + tanA.x() * handle, p0.y() + tanA.y() * handle, p0.z() + tanA.z() * handle);
            Vector3d p2 = new Vector3d(p3.x() - tanB.x() * handle, p3.y() - tanB.y() * handle, p3.z() - tanB.z() * handle);
            double yawDelta = Math.abs(shortestYawDelta(a.getYawDeg(), b.getYawDeg()));
            double curveFactor = 1.0 + Math.min(1.5, yawDelta / 90.0);
            int steps = (int) Math.ceil(dist * sps * curveFactor);
            steps = Math.max(4, Math.min(256, steps));
            for (int k = 0; k <= steps; k++) {
                double t = k / (double) steps;
                // skip duplicate join points except first segment's t=0
                if (i > 0 && k == 0) {
                    continue;
                }
                Vector3d pos = bezier(t, p0, p1, p2, p3);
                Vector3d d = bezierDeriv(t, p0, p1, p2, p3);
                d.y = 0.0;
                if (len2(d) < 1.0e-6) {
                    d.x = tanA.x();
                    d.y = 0.0;
                    d.z = tanA.z();
                } else {
                    d.normalize();
                }
                // UP × d: two-arg cross writes into the third vector; do not assign return value (it is UP, not the product).
                Vector3d right = new Vector3d();
                UP.cross(d, right);
                if (len2(right) < 1.0e-6) {
                    right.x = 1.0;
                    right.y = 0.0;
                    right.z = 0.0;
                } else {
                    right.normalize();
                }
                out.add(new PathSample(pos, d, right));
            }
        }
        return out;
    }

    /**
     * Yaw in degrees (same basis as {@link #forwardHorizontal}) from a world-space direction, using only XZ.
     */
    public static double yawDegFromLookDirection(@Nonnull Vector3d dir) {
        double x = dir.x();
        double z = dir.z();
        double h = Math.hypot(x, z);
        if (h < 1.0e-6) {
            return 0.0;
        }
        return Math.toDegrees(Math.atan2(x, z));
    }

    @Nonnull
    public static Vector3d forwardHorizontal(double yawDeg) {
        double r = Math.toRadians(yawDeg);
        // +Z at yaw 0
        double x = Math.sin(r);
        double z = Math.cos(r);
        Vector3d v = new Vector3d(x, 0.0, z);
        if (len2(v) < 1.0e-6) {
            return new Vector3d(0.0, 0.0, 1.0);
        }
        v.normalize();
        return v;
    }

    private static double len2(@Nonnull Vector3d v) {
        return v.x() * v.x() + v.y() * v.y() + v.z() * v.z();
    }

    /** Smallest signed yaw difference in degrees, in [-180, 180]. */
    private static double shortestYawDelta(double yawA, double yawB) {
        double d = yawB - yawA;
        while (d > 180.0) {
            d -= 360.0;
        }
        while (d < -180.0) {
            d += 360.0;
        }
        return d;
    }

    @Nonnull
    public static final class PathSample {
        @Nonnull
        public final Vector3d position;
        @Nonnull
        public final Vector3d forward;
        @Nonnull
        public final Vector3d right;

        public PathSample(@Nonnull Vector3d position, @Nonnull Vector3d forward, @Nonnull Vector3d right) {
            this.position = position;
            this.forward = forward;
            this.right = right;
        }
    }

    @Nonnull
    private static Vector3d bezier(double t, @Nonnull Vector3d p0, @Nonnull Vector3d p1, @Nonnull Vector3d p2, @Nonnull Vector3d p3) {
        double u = 1.0 - t;
        double uu = u * u;
        double uuu = uu * u;
        double tt = t * t;
        double ttt = tt * t;
        double x = uuu * p0.x() + 3 * uu * t * p1.x() + 3 * u * tt * p2.x() + ttt * p3.x();
        double y = uuu * p0.y() + 3 * uu * t * p1.y() + 3 * u * tt * p2.y() + ttt * p3.y();
        double z = uuu * p0.z() + 3 * uu * t * p1.z() + 3 * u * tt * p2.z() + ttt * p3.z();
        return new Vector3d(x, y, z);
    }

    @Nonnull
    private static Vector3d bezierDeriv(double t, @Nonnull Vector3d p0, @Nonnull Vector3d p1, @Nonnull Vector3d p2, @Nonnull Vector3d p3) {
        double u = 1.0 - t;
        double uu = u * u;
        double tt = t * t;
        double x =
            3 * uu * (p1.x() - p0.x())
                + 6 * u * t * (p2.x() - p1.x())
                + 3 * tt * (p3.x() - p2.x());
        double y =
            3 * uu * (p1.y() - p0.y())
                + 6 * u * t * (p2.y() - p1.y())
                + 3 * tt * (p3.y() - p2.y());
        double z =
            3 * uu * (p1.z() - p0.z())
                + 6 * u * t * (p2.z() - p1.z())
                + 3 * tt * (p3.z() - p2.z());
        return new Vector3d(x, y, z);
    }
}
