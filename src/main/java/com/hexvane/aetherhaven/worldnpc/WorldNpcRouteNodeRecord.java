package com.hexvane.aetherhaven.worldnpc;

import com.google.gson.annotations.SerializedName;
import javax.annotation.Nullable;

/** One node on a world NPC patrol-style route. */
public final class WorldNpcRouteNodeRecord {
    @SerializedName("x")
    private double x;

    @SerializedName("y")
    private double y;

    @SerializedName("z")
    private double z;

    @SerializedName("yawDegrees")
    private float yawDegrees;

    /** Optional wait in game seconds at this node before continuing. */
    @SerializedName("waitSeconds")
    private int waitSeconds;

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYawDegrees() {
        return yawDegrees;
    }

    public int getWaitSeconds() {
        return Math.max(0, waitSeconds);
    }

    public void setPosition(double x, double y, double z, float yawDegrees) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yawDegrees = yawDegrees;
    }

    public void setWaitSeconds(int waitSeconds) {
        this.waitSeconds = Math.max(0, waitSeconds);
    }
}
