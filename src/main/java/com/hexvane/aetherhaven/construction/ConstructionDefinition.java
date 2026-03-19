package com.hexvane.aetherhaven.construction;

import com.google.gson.annotations.SerializedName;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

public final class ConstructionDefinition {
    @SerializedName("id")
    private String id;

    @SerializedName("displayName")
    private String displayName;

    @SerializedName("description")
    @Nullable
    private String description;

    @SerializedName("prefabPath")
    private String prefabPath;

    @SerializedName("plotAnchorOffset")
    private int[] plotAnchorOffset = new int[] {0, 0, 0};

    @SerializedName("rotationYaw")
    private String rotationYaw = "None";

    @SerializedName("requiredVillagerId")
    @Nullable
    private String requiredVillagerId;

    @SerializedName("materials")
    private List<MaterialRequirement> materials = Collections.emptyList();

    @SerializedName("tier")
    @Nullable
    private Integer tier;

    @SerializedName("upgradesTo")
    @Nullable
    private String upgradesTo;

    @SerializedName("styleId")
    @Nullable
    private String styleId;

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName != null ? displayName : id;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    public String getPrefabPath() {
        return prefabPath;
    }

    public int[] getPlotAnchorOffset() {
        return plotAnchorOffset != null && plotAnchorOffset.length == 3 ? plotAnchorOffset : new int[] {0, 0, 0};
    }

    public String getRotationYaw() {
        return rotationYaw != null ? rotationYaw : "None";
    }

    @Nullable
    public String getRequiredVillagerId() {
        return requiredVillagerId;
    }

    public List<MaterialRequirement> getMaterials() {
        return materials != null ? materials : Collections.emptyList();
    }

    @Nullable
    public Integer getTier() {
        return tier;
    }

    @Nullable
    public String getUpgradesTo() {
        return upgradesTo;
    }

    @Nullable
    public String getStyleId() {
        return styleId;
    }
}
