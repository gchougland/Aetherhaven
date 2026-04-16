package com.hexvane.aetherhaven.town;

import com.google.gson.annotations.SerializedName;
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
}
