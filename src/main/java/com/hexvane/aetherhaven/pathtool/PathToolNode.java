package com.hexvane.aetherhaven.pathtool;

import com.hypixel.hytale.math.vector.Vector3d;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

@Immutable
public final class PathToolNode {
    @Nonnull
    private final UUID id;
    private final double x;
    private final double y;
    private final double z;
    private final double yawDeg;

    public PathToolNode(@Nonnull UUID id, @Nonnull Vector3d pos, double yawDeg) {
        this.id = id;
        this.x = pos.getX();
        this.y = pos.getY();
        this.z = pos.getZ();
        this.yawDeg = normalizeYaw360(yawDeg);
    }

    @Nonnull
    public UUID getId() {
        return id;
    }

    @Nonnull
    public Vector3d getPosition() {
        return new Vector3d(x, y, z);
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public double getYawDeg() {
        return yawDeg;
    }

    @Nonnull
    public PathToolNode withPosition(@Nonnull Vector3d p) {
        return new PathToolNode(id, p, yawDeg);
    }

    @Nonnull
    public PathToolNode withYaw(double yaw) {
        return new PathToolNode(id, new Vector3d(x, y, z), yaw);
    }

    @Nonnull
    private static double normalizeYaw360(double a) {
        double r = a % 360.0;
        if (r < 0) {
            r += 360.0;
        }
        return r;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, x, y, z, yawDeg);
    }

    @Override
    public boolean equals(@Nonnull Object o) {
        if (!(o instanceof PathToolNode other)) {
            return false;
        }
        return id.equals(other.id)
            && Double.compare(x, other.x) == 0
            && Double.compare(y, other.y) == 0
            && Double.compare(z, other.z) == 0
            && Double.compare(yawDeg, other.yawDeg) == 0;
    }
}
