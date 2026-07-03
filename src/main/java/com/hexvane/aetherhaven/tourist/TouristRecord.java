package com.hexvane.aetherhaven.tourist;

import com.google.gson.annotations.SerializedName;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Persisted active or invited tourist row on a town. */
public final class TouristRecord {
    @SerializedName("characterId")
    private String characterId = "";

    @SerializedName("entityUuid")
    private String entityUuid = "";

    @SerializedName("portalId")
    private String portalId = "";

    @SerializedName("invitedToStay")
    private boolean invitedToStay;

    @SerializedName("citizen")
    private boolean citizen;

    /** Dawn-aligned visit day ({@link com.hexvane.aetherhaven.reputation.VillagerReputationService#currentGameEpochDay}) when this tourist arrived. */
    @SerializedName("spawnEpochDay")
    private long spawnEpochDay;

    /** Inclusive game hour (0–23) when this tourist should leave; rolled between 19 and 22 at spawn. */
    @SerializedName("leaveHour")
    private int leaveHour;

    public TouristRecord() {}

    public TouristRecord(
        @Nonnull String characterId,
        @Nonnull UUID entityUuid,
        @Nonnull UUID portalId,
        boolean invitedToStay,
        boolean citizen,
        long spawnEpochDay,
        int leaveHour
    ) {
        this.characterId = characterId;
        this.entityUuid = entityUuid.toString();
        this.portalId = portalId.toString();
        this.invitedToStay = invitedToStay;
        this.citizen = citizen;
        this.spawnEpochDay = spawnEpochDay;
        this.leaveHour = leaveHour;
    }

    public TouristRecord(
        @Nonnull String characterId,
        @Nonnull UUID entityUuid,
        @Nonnull UUID portalId,
        boolean invitedToStay,
        boolean citizen,
        long spawnEpochDay
    ) {
        this(characterId, entityUuid, portalId, invitedToStay, citizen, spawnEpochDay, 0);
    }

    public TouristRecord(
        @Nonnull String characterId,
        @Nonnull UUID entityUuid,
        @Nonnull UUID portalId,
        boolean invitedToStay,
        boolean citizen
    ) {
        this(characterId, entityUuid, portalId, invitedToStay, citizen, 0L);
    }

    @Nonnull
    public String getCharacterId() {
        return characterId != null ? characterId : "";
    }

    @Nullable
    public UUID getEntityUuid() {
        if (entityUuid == null || entityUuid.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(entityUuid.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void setEntityUuid(@Nonnull UUID uuid) {
        this.entityUuid = uuid.toString();
    }

    @Nullable
    public UUID getPortalId() {
        if (portalId == null || portalId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(portalId.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void setPortalId(@Nullable UUID portalId) {
        this.portalId = portalId != null ? portalId.toString() : "";
    }

    public boolean isInvitedToStay() {
        return invitedToStay;
    }

    public void setInvitedToStay(boolean invitedToStay) {
        this.invitedToStay = invitedToStay;
    }

    public boolean isCitizen() {
        return citizen;
    }

    public void setCitizen(boolean citizen) {
        this.citizen = citizen;
    }

    public long getSpawnEpochDay() {
        return spawnEpochDay;
    }

    public void setSpawnEpochDay(long spawnEpochDay) {
        this.spawnEpochDay = spawnEpochDay;
    }

    public int getLeaveHour() {
        return leaveHour;
    }

    public void setLeaveHour(int leaveHour) {
        this.leaveHour = leaveHour;
    }
}
