package com.hexvane.aetherhaven.town;

import com.google.gson.annotations.SerializedName;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Persisted hired guard row (not yet a tax paying citizen until housed). */
public final class HiredGuardRecord {
    @SerializedName("characterId")
    private String characterId = "";

    @SerializedName("entityUuid")
    private String entityUuid = "";

    @SerializedName("equipmentProfileId")
    private String equipmentProfileId = "";

    @SerializedName("citizen")
    private boolean citizen;

    private boolean collectMobDrops;
    private java.util.List<String> spoils = new java.util.ArrayList<>();
    public boolean collectsMobDrops(){return collectMobDrops;}
    public void setCollectMobDrops(boolean enabled){collectMobDrops=enabled;}
    public java.util.List<String> spoils(){if(spoils==null)spoils=new java.util.ArrayList<>();return spoils;}

    public HiredGuardRecord() {}

    public HiredGuardRecord(
        @Nonnull String characterId,
        @Nonnull UUID entityUuid,
        @Nonnull String equipmentProfileId,
        boolean citizen
    ) {
        this.characterId = characterId;
        this.entityUuid = entityUuid.toString();
        this.equipmentProfileId = equipmentProfileId;
        this.citizen = citizen;
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

    @Nonnull
    public String getEquipmentProfileId() {
        return equipmentProfileId != null ? equipmentProfileId : "";
    }

    public void setEquipmentProfileId(String profile) { equipmentProfileId=profile; }

    public boolean isCitizen() {
        return citizen;
    }

    public void setCitizen(boolean citizen) {
        this.citizen = citizen;
    }
}
