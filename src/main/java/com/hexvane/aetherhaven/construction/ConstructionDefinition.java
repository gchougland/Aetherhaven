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

    /**
     * In-game days over which passive assembly would place all prefab cells if the player never uses the building staff.
     * Larger prefabs with the same value place more slowly per block.
     */
    @SerializedName("selfBuildGameDays")
    private double selfBuildGameDays = 3.0;

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

    /**
     * Added to the placed plot footprint’s {@code minY} to get the lower bound for where NPCs may “stand” for
     * autonomy (feet in air, block below solid). 0 = foundation/footprint min; 1+ when the AABB’s bottom is below the
     * playable ground floor.
     */
    @SerializedName("autonomyNavFloorYAboveMinY")
    private int autonomyNavFloorYAboveMinY;

    /**
     * Do not use stand Y above (footprint {@code minY} + this value) when a plot bounds check applies. Tighten for
     * single-story builds if roof columns still path too high; loosen for very tall multi-floor prefabs.
     */
    @SerializedName("autonomyNavMaxStandYSpanAboveMinY")
    private int autonomyNavMaxStandYSpanAboveMinY;

    /**
     * Treat the top this many Y layers of the plot AABB (below {@code maxY}) as non-nav (roof/trim). 1 = exclude feet
     * on the topmost world layer of the building box; increase if flat roofs are still being targeted.
     */
    @SerializedName("autonomyNavRoofExclusionYBelowMaxY")
    private int autonomyNavRoofExclusionYBelowMaxY = 1;

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

    public double getSelfBuildGameDays() {
        return selfBuildGameDays > 0.0 ? selfBuildGameDays : 3.0;
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

    /** @see #autonomyNavFloorYAboveMinY */
    public int getAutonomyNavFloorYAboveMinY() {
        return autonomyNavFloorYAboveMinY;
    }

    /** Span above footprint minY for stand resolution; 0/negative = use mod default 32. */
    public int getAutonomyNavMaxStandYSpanAboveMinY() {
        return autonomyNavMaxStandYSpanAboveMinY > 0 ? autonomyNavMaxStandYSpanAboveMinY : 32;
    }

    /** 0/negative = use 1. */
    public int getAutonomyNavRoofExclusionYBelowMaxY() {
        return autonomyNavRoofExclusionYBelowMaxY > 0 ? autonomyNavRoofExclusionYBelowMaxY : 1;
    }
}
