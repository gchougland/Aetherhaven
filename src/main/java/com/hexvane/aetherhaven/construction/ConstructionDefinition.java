package com.hexvane.aetherhaven.construction;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.google.gson.annotations.SerializedName;
import com.hexvane.aetherhaven.poi.BuildingPoisDefinition;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.PrefabRotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
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

    /**
     * Prefab-local offset from the plot sign cell to prefab buffer (0,0,0), same axes as unrotated prefab blocks.
     * At build time this is rotated by the sign's yaw before adding to the sign world position.
     */
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

    /**
     * Gold coins paid from the town treasury when construction starts (plot sign UI). Inn and town hall typically use 0;
     * other buildings contribute pacing via shared funds after the hall exists.
     */
    @SerializedName("treasuryGoldCoinCost")
    private long treasuryGoldCoinCost;

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

    /** Prefab-local spawn cells for inn visitors (max two in Week 4); optional. */
    @SerializedName("visitorSpawnLocals")
    @Nullable
    private int[][] visitorSpawnLocals;

    /** Prefab-local position of the treasury block (town-shared gold storage); optional. */
    @SerializedName("treasuryLocalPos")
    @Nullable
    private int[] treasuryLocalPos;

    /** Prefab-local POI anchors for autonomy; listed in each construction JSON under {@code Server/Aetherhaven/Buildings/}. */
    @SerializedName("pois")
    private List<BuildingPoisDefinition.PoiRow> pois = new ArrayList<>();

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

    /**
     * World position of prefab buffer (0,0,0) / anchor for a plot sign block at {@code plotSignBlockWorldPos}.
     * The sign voxel is {@link AetherhavenConstants#PLOT_SIGN_BLOCK_Y_ABOVE_LOGICAL_ANCHOR} above the logical cell used
     * with {@link #plotAnchorOffset} so the sign can sit higher without shifting the built prefab.
     * {@link #plotAnchorOffset} is in prefab-local axes; it must be rotated by {@code placementYaw} before adding
     * to the logical anchor (same convention as {@link com.hexvane.aetherhaven.construction.PrefabLocalOffset}).
     */
    @Nonnull
    public Vector3i resolvePrefabAnchorWorld(@Nonnull Vector3i plotSignBlockWorldPos, @Nonnull Rotation placementYaw) {
        Vector3i logical = new Vector3i(
            plotSignBlockWorldPos.x,
            plotSignBlockWorldPos.y - AetherhavenConstants.PLOT_SIGN_BLOCK_Y_ABOVE_LOGICAL_ANCHOR,
            plotSignBlockWorldPos.z
        );
        int[] o = getPlotAnchorOffset();
        Vector3i off = new Vector3i(o[0], o[1], o[2]);
        PrefabRotation.fromRotation(placementYaw).rotate(off);
        return new Vector3i(logical.x + off.x, logical.y + off.y, logical.z + off.z);
    }

    /** Same as {@link #resolvePrefabAnchorWorld(Vector3i, Rotation)} with {@link Rotation#None} (offset not rotated). */
    @Nonnull
    public Vector3i resolvePrefabAnchorWorld(@Nonnull Vector3i signPos) {
        return resolvePrefabAnchorWorld(signPos, Rotation.None);
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

    public long getTreasuryGoldCoinCost() {
        return Math.max(0L, treasuryGoldCoinCost);
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

    /** @return up to two prefab-local visitor spawn positions, or null */
    @Nullable
    public int[][] getVisitorSpawnLocals() {
        return visitorSpawnLocals;
    }

    /** @return prefab-local x,y,z of treasury block, or null */
    @Nullable
    public int[] getTreasuryLocalPos() {
        return treasuryLocalPos != null && treasuryLocalPos.length == 3 ? treasuryLocalPos : null;
    }

    @Nonnull
    public List<BuildingPoisDefinition.PoiRow> getPois() {
        return pois != null && !pois.isEmpty() ? pois : Collections.emptyList();
    }
}
