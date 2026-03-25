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

    /** If set, player must carry this item to select this construction in the placement tool UI. */
    @SerializedName("plotTokenItemId")
    @Nullable
    private String plotTokenItemId;

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

    /** Prefab-local position (same space as prefab `blocks[].x/y/z`) of the management block voxel to stamp after build. */
    @SerializedName("managementBlockLocalPos")
    @Nullable
    private int[] managementBlockLocalPos;

    /** Prefab-local spawn cell for the innkeeper NPC (same space as prefab blocks); optional. */
    @SerializedName("innkeeperSpawnLocal")
    @Nullable
    private int[] innkeeperSpawnLocal;

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

    @Nullable
    public String getPlotTokenItemId() {
        return plotTokenItemId;
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

    /** @return prefab-local x,y,z of management block, or null if not configured */
    @Nullable
    public int[] getManagementBlockLocalPos() {
        return managementBlockLocalPos != null && managementBlockLocalPos.length == 3 ? managementBlockLocalPos : null;
    }

    /** @return prefab-local x,y,z for innkeeper spawn, or null */
    @Nullable
    public int[] getInnkeeperSpawnLocal() {
        return innkeeperSpawnLocal != null && innkeeperSpawnLocal.length == 3 ? innkeeperSpawnLocal : null;
    }
}
