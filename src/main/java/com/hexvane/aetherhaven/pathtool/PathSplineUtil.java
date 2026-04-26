package com.hexvane.aetherhaven.pathtool;

import com.hypixel.hytale.math.vector.Vector3d;
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
            double dist = p0.distanceTo(p3);
            double handle = Math.max(0.5, dist * HANDLE_FRAC);
            Vector3d p1 = new Vector3d(p0.getX() + tanA.getX() * handle, p0.getY() + tanA.getY() * handle, p0.getZ() + tanA.getZ() * handle);
            Vector3d p2 = new Vector3d(p3.getX() - tanB.getX() * handle, p3.getY() - tanB.getY() * handle, p3.getZ() - tanB.getZ() * handle);
            int steps = (int) Math.ceil(dist * sps);
            steps = Math.max(4, Math.min(256, steps));
            for (int k = 0; k < steps; k++) {
                double t = k / (double) steps;
                // skip duplicate join points except first segment's t=0
                if (i > 0 && k == 0) {
                    continue;
                }
                Vector3d pos = bezier(t, p0, p1, p2, p3);
                Vector3d d = bezierDeriv(t, p0, p1, p2, p3);
                d.setY(0.0);
                if (len2(d) < 1.0e-6) {
                    d.setX(tanA.getX());
                    d.setY(0.0);
                    d.setZ(tanA.getZ());
                } else {
                    d.normalize();
                }
                // UP × d: two-arg cross writes into the third vector; do not assign return value (it is UP, not the product).
                Vector3d right = new Vector3d();
                UP.cross(d, right);
                if (len2(right) < 1.0e-6) {
                    right.setX(1.0);
                    right.setY(0.0);
                    right.setZ(0.0);
                } else {
                    right.normalize();
                }
                out.add(new PathSample(pos, d, right));
            }
        }
        // last endpoint
        PathToolNode last = nodes.get(nodes.size() - 1);
        Vector3d tan = forwardHorizontal(last.getYawDeg());
        Vector3d r = new Vector3d();
        UP.cross(tan, r);
        if (len2(r) < 1.0e-6) {
            r.setX(1.0);
            r.setY(0.0);
            r.setZ(0.0);
        } else {
            r.normalize();
        }
        out.add(new PathSample(last.getPosition(), tan, r));
        return out;
    }

    /**
     * Yaw in degrees (same basis as {@link #forwardHorizontal}) from a world-space direction, using only XZ.
     */
    public static double yawDegFromLookDirection(@Nonnull Vector3d dir) {
        double x = dir.getX();
        double z = dir.getZ();
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
        return v.getX() * v.getX() + v.getY() * v.getY() + v.getZ() * v.getZ();
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
        double x = uuu * p0.getX() + 3 * uu * t * p1.getX() + 3 * u * tt * p2.getX() + ttt * p3.getX();
        double y = uuu * p0.getY() + 3 * uu * t * p1.getY() + 3 * u * tt * p2.getY() + ttt * p3.getY();
        double z = uuu * p0.getZ() + 3 * uu * t * p1.getZ() + 3 * u * tt * p2.getZ() + ttt * p3.getZ();
        return new Vector3d(x, y, z);
    }

    @Nonnull
    private static Vector3d bezierDeriv(double t, @Nonnull Vector3d p0, @Nonnull Vector3d p1, @Nonnull Vector3d p2, @Nonnull Vector3d p3) {
        double u = 1.0 - t;
        double uu = u * u;
        double tt = t * t;
        double x =
            3 * uu * (p1.getX() - p0.getX())
                + 6 * u * t * (p2.getX() - p1.getX())
                + 3 * tt * (p3.getX() - p2.getX());
        double y =
            3 * uu * (p1.getY() - p0.getY())
                + 6 * u * t * (p2.getY() - p1.getY())
                + 3 * tt * (p3.getY() - p2.getY());
        double z =
            3 * uu * (p1.getZ() - p0.getZ())
                + 6 * u * t * (p2.getZ() - p1.getZ())
                + 3 * tt * (p3.getZ() - p2.getZ());
        return new Vector3d(x, y, z);
    }
}
