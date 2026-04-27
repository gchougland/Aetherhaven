package com.hexvane.aetherhaven.pathtool;

import com.google.gson.annotations.SerializedName;
import com.hypixel.hytale.math.vector.Vector3d;
import javax.annotation.Nonnull;

/** Serializable path-nav waypoint (world-space). */
public final class PathNavPoint {
    @SerializedName("x")
    public double x;
    @SerializedName("y")
    public double y;
    @SerializedName("z")
    public double z;

    public PathNavPoint() {}

    public PathNavPoint(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Nonnull
    public Vector3d toVector() {
        return new Vector3d(x, y, z);
    }
}
