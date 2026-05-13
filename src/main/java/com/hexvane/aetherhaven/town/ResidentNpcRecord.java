package com.hexvane.aetherhaven.town;

import com.google.gson.annotations.SerializedName;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Persistent row for a town resident NPC so revival UI can list villagers whose entities are missing
 * (reputation keys use entity UUID; this stores the last known UUID per role).
 */
public final class ResidentNpcRecord {
    @SerializedName("npcRoleId")
    private String npcRoleId = "";

    @SerializedName("kind")
    private String kind = "";

    @Nullable
    @SerializedName("jobPlotId")
    private String jobPlotId;

    @SerializedName("lastEntityUuid")
    private String lastEntityUuid = "";

    /** Last hunger/energy/fun (0..100) saved while this entity was in a loaded simulation; used for tithe when unloaded. */
    @Nullable
    @SerializedName("lastKnownHunger")
    private Float lastKnownHunger;

    @Nullable
    @SerializedName("lastKnownEnergy")
    private Float lastKnownEnergy;

    @Nullable
    @SerializedName("lastKnownFun")
    private Float lastKnownFun;

    public ResidentNpcRecord() {}

    public ResidentNpcRecord(
        @Nonnull String npcRoleId,
        @Nonnull String kind,
        @Nullable UUID jobPlotId,
        @Nonnull UUID lastEntityUuid
    ) {
        this.npcRoleId = npcRoleId != null ? npcRoleId : "";
        this.kind = kind != null ? kind : "";
        this.jobPlotId = jobPlotId != null ? jobPlotId.toString() : null;
        this.lastEntityUuid = lastEntityUuid.toString();
    }

    @Nonnull
    public String getNpcRoleId() {
        return npcRoleId != null ? npcRoleId : "";
    }

    public void setNpcRoleId(@Nonnull String npcRoleId) {
        this.npcRoleId = npcRoleId != null ? npcRoleId : "";
    }

    @Nonnull
    public String getKind() {
        return kind != null ? kind : "";
    }

    public void setKind(@Nonnull String kind) {
        this.kind = kind != null ? kind : "";
    }

    @Nullable
    public UUID getJobPlotId() {
        if (jobPlotId == null || jobPlotId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(jobPlotId.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void setJobPlotId(@Nullable UUID jobPlotId) {
        this.jobPlotId = jobPlotId != null ? jobPlotId.toString() : null;
    }

    @Nonnull
    public UUID getLastEntityUuid() {
        try {
            return UUID.fromString(lastEntityUuid.trim());
        } catch (Exception e) {
            return new UUID(0L, 0L);
        }
    }

    public void setLastEntityUuid(@Nonnull UUID uuid) {
        this.lastEntityUuid = uuid.toString();
    }

    public boolean hasLastKnownNeeds() {
        return lastKnownHunger != null && lastKnownEnergy != null && lastKnownFun != null;
    }

    public float getLastKnownHunger() {
        return lastKnownHunger != null ? lastKnownHunger : 0f;
    }

    public float getLastKnownEnergy() {
        return lastKnownEnergy != null ? lastKnownEnergy : 0f;
    }

    public float getLastKnownFun() {
        return lastKnownFun != null ? lastKnownFun : 0f;
    }

    /**
     * Copies persisted needs snapshot (e.g. when replacing a roster row for the same entity uuid).
     */
    public void copyLastKnownNeedsFrom(@Nullable ResidentNpcRecord other) {
        if (other == null || !other.hasLastKnownNeeds()) {
            return;
        }
        this.lastKnownHunger = other.lastKnownHunger;
        this.lastKnownEnergy = other.lastKnownEnergy;
        this.lastKnownFun = other.lastKnownFun;
    }

    public void clearLastKnownNeeds() {
        this.lastKnownHunger = null;
        this.lastKnownEnergy = null;
        this.lastKnownFun = null;
    }

    /**
     * @return true if any value changed enough to warrant persisting the town
     */
    public boolean setLastKnownNeedsIfChanged(float hunger, float energy, float fun, float epsilon) {
        float h = clampNeed(hunger);
        float e = clampNeed(energy);
        float f = clampNeed(fun);
        if (!hasLastKnownNeeds()) {
            this.lastKnownHunger = h;
            this.lastKnownEnergy = e;
            this.lastKnownFun = f;
            return true;
        }
        if (Math.abs(lastKnownHunger - h) < epsilon
            && Math.abs(lastKnownEnergy - e) < epsilon
            && Math.abs(lastKnownFun - f) < epsilon) {
            return false;
        }
        this.lastKnownHunger = h;
        this.lastKnownEnergy = e;
        this.lastKnownFun = f;
        return true;
    }

    private static float clampNeed(float v) {
        return Math.max(0f, Math.min(VillagerNeeds.MAX, v));
    }
}
