package com.hexvane.aetherhaven.worldnpc;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Durable world placement for an undying hub/world NPC. */
public final class WorldNpcPlacementRecord {
    @SerializedName("placementId")
    @Nullable
    private String placementId;

    @SerializedName("npcRoleId")
    @Nullable
    private String npcRoleId;

    /** Optional nametag / list label override for this placement. */
    @SerializedName("displayName")
    @Nullable
    private String displayName;

    /** Optional portrait filename under {@code Icons/ModelsGenerated/}. */
    @SerializedName("portraitIcon")
    @Nullable
    private String portraitIcon;

    @SerializedName("x")
    private double x;

    @SerializedName("y")
    private double y;

    @SerializedName("z")
    private double z;

    @SerializedName("yawDegrees")
    private float yawDegrees;

    @SerializedName("scheduleMode")
    @Nullable
    private String scheduleMode;

    @SerializedName("stations")
    @Nullable
    private List<WorldNpcStationRecord> stations;

    @SerializedName("routeId")
    @Nullable
    private String routeId;

    @SerializedName("shopEnabled")
    private boolean shopEnabled;

    @SerializedName("boardProfileId")
    @Nullable
    private String boardProfileId;

    @SerializedName("entityUuid")
    @Nullable
    private String entityUuid;

    @Nonnull
    public String placementIdOrEmpty() {
        return placementId != null ? placementId.trim() : "";
    }

    public void setPlacementId(@Nonnull String placementId) {
        this.placementId = placementId.trim();
    }

    @Nonnull
    public String npcRoleIdOrEmpty() {
        return npcRoleId != null ? npcRoleId.trim() : "";
    }

    public void setNpcRoleId(@Nonnull String npcRoleId) {
        this.npcRoleId = npcRoleId.trim();
    }

    @Nonnull
    public String displayNameOrEmpty() {
        return displayName != null ? displayName.trim() : "";
    }

    public void setDisplayName(@Nullable String displayName) {
        this.displayName = displayName != null && !displayName.isBlank() ? displayName.trim() : null;
    }

    @Nonnull
    public String portraitIconOrEmpty() {
        return portraitIcon != null ? portraitIcon.trim() : "";
    }

    public void setPortraitIcon(@Nullable String portraitIcon) {
        this.portraitIcon = portraitIcon != null && !portraitIcon.isBlank() ? portraitIcon.trim() : null;
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

    public void setPose(double x, double y, double z, float yawDegrees) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yawDegrees = yawDegrees;
    }

    @Nonnull
    public WorldNpcScheduleMode scheduleModeOrDefault() {
        return WorldNpcScheduleMode.fromString(scheduleMode);
    }

    public void setScheduleMode(@Nonnull WorldNpcScheduleMode mode) {
        this.scheduleMode = mode.wireName();
    }

    @Nonnull
    public List<WorldNpcStationRecord> stationsOrEmpty() {
        if (stations == null) {
            stations = new ArrayList<>();
        }
        return stations;
    }

    @Nonnull
    public String routeIdOrEmpty() {
        return routeId != null ? routeId.trim() : "";
    }

    public void setRouteId(@Nullable String routeId) {
        this.routeId = routeId;
    }

    public boolean isShopEnabled() {
        return shopEnabled;
    }

    public void setShopEnabled(boolean shopEnabled) {
        this.shopEnabled = shopEnabled;
    }

    @Nonnull
    public String boardProfileIdOrEmpty() {
        return boardProfileId != null ? boardProfileId.trim() : "";
    }

    public void setBoardProfileId(@Nullable String boardProfileId) {
        this.boardProfileId = boardProfileId;
    }

    @Nullable
    public UUID entityUuidOrNull() {
        if (entityUuid == null || entityUuid.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(entityUuid.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void setEntityUuid(@Nullable UUID uuid) {
        this.entityUuid = uuid != null ? uuid.toString() : null;
    }
}
