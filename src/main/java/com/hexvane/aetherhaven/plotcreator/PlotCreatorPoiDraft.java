package com.hexvane.aetherhaven.plotcreator;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Mutable POI row for the plot creator (written to building JSON). */
public final class PlotCreatorPoiDraft {
    @SerializedName("localX")
    private int localX;

    @SerializedName("localY")
    private int localY;

    @SerializedName("localZ")
    private int localZ;

    @SerializedName("tags")
    private List<String> tags = new ArrayList<>();

    @SerializedName("capacity")
    private int capacity = 1;

    @Nullable
    @SerializedName("blockTypeId")
    private String blockTypeId;

    @SerializedName("interactionKind")
    private String interactionKind = "NONE";

    @Nullable
    @SerializedName("interactionTargetLocalX")
    private Integer interactionTargetLocalX;

    @Nullable
    @SerializedName("interactionTargetLocalY")
    private Integer interactionTargetLocalY;

    @Nullable
    @SerializedName("interactionTargetLocalZ")
    private Integer interactionTargetLocalZ;

    @Nullable
    @SerializedName("workResidentKind")
    private String workResidentKind;

    @Nullable
    @SerializedName("interactionTargetYawDegrees")
    private Float interactionTargetYawDegrees;

    @Nullable
    @SerializedName("equipmentProfileId")
    private String equipmentProfileId;

    public int getLocalX() {
        return localX;
    }

    public int getLocalY() {
        return localY;
    }

    public int getLocalZ() {
        return localZ;
    }

    public void setLocal(int x, int y, int z) {
        this.localX = x;
        this.localY = y;
        this.localZ = z;
    }

    @Nonnull
    public List<String> getTags() {
        return tags;
    }

    public void setTags(@Nonnull List<String> tags) {
        this.tags = new ArrayList<>(tags);
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    @Nullable
    public String getBlockTypeId() {
        return blockTypeId;
    }

    public void setBlockTypeId(@Nullable String blockTypeId) {
        this.blockTypeId = blockTypeId;
    }

    @Nonnull
    public String getInteractionKind() {
        return interactionKind;
    }

    public void setInteractionKind(@Nonnull String interactionKind) {
        this.interactionKind = interactionKind;
    }

    public void setInteractionTargetLocal(@Nullable Integer x, @Nullable Integer y, @Nullable Integer z) {
        this.interactionTargetLocalX = x;
        this.interactionTargetLocalY = y;
        this.interactionTargetLocalZ = z;
    }

    @Nullable
    public Integer getInteractionTargetLocalX() {
        return interactionTargetLocalX;
    }

    @Nullable
    public Integer getInteractionTargetLocalY() {
        return interactionTargetLocalY;
    }

    @Nullable
    public Integer getInteractionTargetLocalZ() {
        return interactionTargetLocalZ;
    }

    @Nullable
    public String getWorkResidentKind() {
        return workResidentKind != null && !workResidentKind.isBlank() ? workResidentKind.trim() : null;
    }

    public void setWorkResidentKind(@Nullable String workResidentKind) {
        this.workResidentKind =
            workResidentKind != null && !workResidentKind.isBlank() ? workResidentKind.trim() : null;
    }

    @Nullable
    public Float getInteractionTargetYawDegrees() {
        return interactionTargetYawDegrees;
    }

    public void setInteractionTargetYawDegrees(@Nullable Float interactionTargetYawDegrees) {
        this.interactionTargetYawDegrees = interactionTargetYawDegrees;
    }

    @Nullable
    public String getEquipmentProfileId() {
        return equipmentProfileId != null && !equipmentProfileId.isBlank() ? equipmentProfileId.trim() : null;
    }

    public void setEquipmentProfileId(@Nullable String equipmentProfileId) {
        this.equipmentProfileId =
            equipmentProfileId != null && !equipmentProfileId.isBlank() ? equipmentProfileId.trim() : null;
    }
}
