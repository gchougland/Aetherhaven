package com.hexvane.aetherhaven.worldnpc;

import com.google.gson.annotations.SerializedName;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** A timed world position for {@link WorldNpcScheduleMode#STATIONS}. */
public final class WorldNpcStationRecord {
    @SerializedName("stationId")
    @Nullable
    private String stationId;

    @SerializedName("x")
    private double x;

    @SerializedName("y")
    private double y;

    @SerializedName("z")
    private double z;

    @SerializedName("yawDegrees")
    private float yawDegrees;

    /** Inclusive game-time second of day when this station becomes active (0..86399). */
    @SerializedName("startSecondOfDay")
    private int startSecondOfDay;

    /** Exclusive game-time second of day when this station ends; wraps past midnight when less than start. */
    @SerializedName("endSecondOfDay")
    private int endSecondOfDay;

    @Nonnull
    public String stationIdOrEmpty() {
        return stationId != null ? stationId.trim() : "";
    }

    public void setStationId(@Nullable String stationId) {
        this.stationId = stationId;
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

    public float getYawDegrees() {
        return yawDegrees;
    }

    public void setPosition(double x, double y, double z, float yawDegrees) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yawDegrees = yawDegrees;
    }

    public int getStartSecondOfDay() {
        return startSecondOfDay;
    }

    public int getEndSecondOfDay() {
        return endSecondOfDay;
    }

    public void setTimeWindow(int startSecondOfDay, int endSecondOfDay) {
        this.startSecondOfDay = Math.floorMod(startSecondOfDay, 86400);
        this.endSecondOfDay = Math.floorMod(endSecondOfDay, 86400);
    }

    public boolean isActiveAtSecondOfDay(int secondOfDay) {
        int s = Math.floorMod(secondOfDay, 86400);
        int start = getStartSecondOfDay();
        int end = getEndSecondOfDay();
        if (start == end) {
            return true;
        }
        if (start < end) {
            return s >= start && s < end;
        }
        return s >= start || s < end;
    }
}
