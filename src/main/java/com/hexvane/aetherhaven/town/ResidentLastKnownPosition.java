package com.hexvane.aetherhaven.town;

import com.google.gson.annotations.SerializedName;
import java.util.UUID;
import javax.annotation.Nonnull;

/** Last known world position for a town resident entity uuid. */
public final class ResidentLastKnownPosition {
    @SerializedName("entityUuid")
    private String entityUuid = "";

    @SerializedName("x")
    private double x;

    @SerializedName("y")
    private double y;

    @SerializedName("z")
    private double z;

    @SerializedName("updatedAtEpochMs")
    private long updatedAtEpochMs;

    public ResidentLastKnownPosition() {}

    public ResidentLastKnownPosition(@Nonnull UUID entityUuid, double x, double y, double z, long updatedAtEpochMs) {
        this.entityUuid = entityUuid.toString();
        this.x = x;
        this.y = y;
        this.z = z;
        this.updatedAtEpochMs = updatedAtEpochMs;
    }

    @Nonnull
    public UUID getEntityUuid() {
        try {
            return UUID.fromString(entityUuid.trim());
        } catch (Exception e) {
            return new UUID(0L, 0L);
        }
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

    public long getUpdatedAtEpochMs() {
        return updatedAtEpochMs;
    }

    public void setPosition(double x, double y, double z, long updatedAtEpochMs) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.updatedAtEpochMs = updatedAtEpochMs;
    }
}
