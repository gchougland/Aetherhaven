package com.hexvane.aetherhaven.construction;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.google.gson.annotations.SerializedName;
import com.hexvane.aetherhaven.construction.assembly.AssemblySectionMapper;
import com.hexvane.aetherhaven.poi.BuildingPoisDefinition;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.PrefabRotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class ConstructionDefinition {
    @SerializedName("id")
    private String id;

    @SerializedName("displayName")
    private String displayName;

    @SerializedName("displayNameLangKey")
    @Nullable
    private String displayNameLangKey;

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

    /**
     * Prefab block type ids (same namespace as {@link BlockType#getId()}) excluded from the incremental assembly frontier:
     * placed in one batch immediately before {@link ConstructionPasteOps#finishFluidsAndEntities} (still after the main
     * shell/tree so structure assembles first). Empty = default (all non-air cells follow the normal frontier).
     */
    @SerializedName("assemblyDeferredBlockIds")
    private List<String> assemblyDeferredBlockIds = Collections.emptyList();

    /**
     * Split the main assembly volume into an {@code N×N×N} grid in prefab space ({@code N=1} disables). Finishing one
     * section unlocks the next; deferred blocks and entities are unchanged.
     */
    @SerializedName("assemblyPrefabSectionsPerAxis")
    private int assemblyPrefabSectionsPerAxis = 1;

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

    /**
     * When set, this variant counts as the canonical construction for quests, production keys, workplace matching, and
     * schedule resolution ({@code plotInstance.constructionId} stays this definition's {@link #id}).
     */
    @SerializedName("countsAsConstructionId")
    @Nullable
    private String countsAsConstructionId;

    /**
     * When true on the <em>canonical</em> gameplay definition (and variants inheriting via {@link #countsAsConstructionId}),
     * schedule segments that target this construction pick randomly among all complete matching plots (see catalog helper).
     */
    @SerializedName("scheduleSharedUtilityPick")
    private boolean scheduleSharedUtilityPick;

    /** When true, portal tourists may visit this plot. */
    @SerializedName("touristDestination")
    private boolean touristDestination;

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

    /** Prefab-local spawn cell for the guild master at the inn before the guild hall is built; optional. */
    @SerializedName("guildMasterSpawnLocal")
    @Nullable
    private int[] guildMasterSpawnLocal;

    /** Prefab-local spawn cells for guild hall adventurers (guard eligible townsfolk); optional. */
    @SerializedName("adventurerSpawnLocals")
    @Nullable
    private int[][] adventurerSpawnLocals;

    /** Prefab-local bard performance spot beside the quest board; optional fallback when no BARD POI in registry. */
    @SerializedName("bardWorkPoiLocal")
    @Nullable
    private int[] bardWorkPoiLocal;

    /** Prefab-local interaction facing for {@link #bardWorkPoiLocal}; optional. */
    @SerializedName("bardWorkPoiInteractionTargetLocal")
    @Nullable
    private int[] bardWorkPoiInteractionTargetLocal;

    /** Body yaw (radians) per {@link #adventurerSpawnLocals} entry, in prefab-local axes. */
    @SerializedName("adventurerSpawnYaws")
    @Nullable
    private float[] adventurerSpawnYaws;

    /** Prefab-local position of the treasury block (town-shared gold storage); optional. */
    @SerializedName("treasuryLocalPos")
    @Nullable
    private int[] treasuryLocalPos;

    /** Prefab-local position of the player shop safe block; optional. */
    @SerializedName("shopSafeLocalPos")
    @Nullable
    private int[] shopSafeLocalPos;

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

    /** When false, placement does not consume {@link #plotTokenItemId} from inventory (wall wand display tokens). */
    @SerializedName("consumesPlotToken")
    private boolean consumesPlotToken = true;

    /** When true, the plot crafting bench requires a per player unlock before this variant can be crafted. */
    @SerializedName("plotTokenLockedByDefault")
    private boolean plotTokenLockedByDefault;

    /**
     * When true, regular (white) balloon gifts may roll a plot blueprint unlock page for this construction id.
     * Independent of {@link #plotTokenLockedByDefault} (quest-only locked buildings omit this flag).
     */
    @SerializedName("floatingGiftBlueprint")
    private boolean floatingGiftBlueprint;

    /** When true, the town journal plot list omits this construction. */
    @SerializedName("excludeFromTownJournal")
    private boolean excludeFromTownJournal;

    /** Town wall segment: journal excluded, overlap rules differ, completion moves to {@link com.hexvane.aetherhaven.town.WallSegmentRecord}. */
    @SerializedName("wallSegment")
    private boolean wallSegment;

    /**
     * Decorative build: uses plot placement and assembly, but on completion is removed from town data and left as world
     * blocks only (no management block, journal entry, or villager systems).
     */
    @SerializedName("decorationPlot")
    private boolean decorationPlot;

    /**
     * Plot level tags for schedule and leisure targeting (e.g. amenity, nature, fun). Villagers can prefer plots whose
     * tags match personality leisure weights when multiple plots qualify.
     */
    @SerializedName("tags")
    private List<String> buildingTags = Collections.emptyList();

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName != null ? displayName : id;
    }

    @Nullable
    public String getDisplayNameLangKey() {
        return displayNameLangKey != null && !displayNameLangKey.isBlank() ? displayNameLangKey.trim() : null;
    }

    @Nonnull
    public Message displayNameMessage() {
        String key = getDisplayNameLangKey();
        if (key != null) {
            return Message.translation(key);
        }
        return Message.raw(getDisplayName());
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

    /** Block type ids deferred to the post-frontier batch assembly step; see {@link #assemblyDeferredBlockIds}. */
    @Nonnull
    public Set<String> getAssemblyDeferredBlockIds() {
        List<String> raw = assemblyDeferredBlockIds;
        if (raw == null || raw.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (String bid : raw) {
            if (bid != null && !bid.isBlank()) {
                ids.add(bid.trim());
            }
        }
        return Collections.unmodifiableSet(ids);
    }

    /** {@code 1} = no split; otherwise splits prefab bounds into an {@code N×N×N} assembly grid. */
    public int getAssemblyPrefabSectionsPerAxis() {
        return assemblyPrefabSectionsPerAxis <= 1 ? 1 : AssemblySectionMapper.clampAxisDivisions(assemblyPrefabSectionsPerAxis);
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

    /**
     * Canonical gameplay construction id: {@link #countsAsConstructionId} if set, otherwise {@link #getId}.
     */
    @Nonnull
    public String getGameplayConstructionId() {
        String alias = countsAsConstructionId;
        if (alias != null && !alias.isBlank()) {
            return alias.trim();
        }
        return id != null ? id : "";
    }

    @Nullable
    public String getCountsAsConstructionIdRaw() {
        return countsAsConstructionId;
    }

    /** True when this definition participates in multi-plot random schedule targeting (inn, park, Gaia altar, …). */
    public boolean isScheduleSharedUtilityPick() {
        return scheduleSharedUtilityPick;
    }

    public boolean isTouristDestination() {
        return touristDestination;
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

    /** @return prefab-local x,y,z for guild master spawn at inn, or null */
    @Nullable
    public int[] getGuildMasterSpawnLocal() {
        return guildMasterSpawnLocal != null && guildMasterSpawnLocal.length == 3 ? guildMasterSpawnLocal : null;
    }

    /** @return prefab-local guild hall adventurer spawn positions, or null */
    @Nullable
    public int[][] getAdventurerSpawnLocals() {
        return adventurerSpawnLocals;
    }

    /** @return prefab-local bard work spot, or null */
    @Nullable
    public int[] getBardWorkPoiLocal() {
        return bardWorkPoiLocal != null && bardWorkPoiLocal.length == 3 ? bardWorkPoiLocal : null;
    }

    /** @return prefab-local bard interaction target, or null */
    @Nullable
    public int[] getBardWorkPoiInteractionTargetLocal() {
        return bardWorkPoiInteractionTargetLocal != null && bardWorkPoiInteractionTargetLocal.length == 3
            ? bardWorkPoiInteractionTargetLocal
            : null;
    }

    /** @return prefab-local body yaw per adventurer spawn, or null */
    @Nullable
    public float[] getAdventurerSpawnYaws() {
        return adventurerSpawnYaws;
    }

    /** @return prefab-local x,y,z of treasury block, or null */
    @Nullable
    public int[] getTreasuryLocalPos() {
        return treasuryLocalPos != null && treasuryLocalPos.length == 3 ? treasuryLocalPos : null;
    }

    /** @return prefab-local x,y,z of player shop safe block, or null */
    @Nullable
    public int[] getShopSafeLocalPos() {
        return shopSafeLocalPos != null && shopSafeLocalPos.length == 3 ? shopSafeLocalPos : null;
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

    public boolean consumesPlotToken() {
        return consumesPlotToken;
    }

    public boolean isPlotTokenLockedByDefault() {
        return plotTokenLockedByDefault;
    }

    public boolean isFloatingGiftBlueprint() {
        return floatingGiftBlueprint;
    }

    public boolean isExcludeFromTownJournal() {
        return excludeFromTownJournal;
    }

    public boolean isWallSegment() {
        return wallSegment;
    }

    public boolean isDecorationPlot() {
        if (decorationPlot) {
            return true;
        }
        return id != null && id.startsWith("plot_decoration");
    }

    /** @return normalized plot level tags (lowercase trim); empty when unset */
    @Nonnull
    public Set<String> getBuildingTags() {
        List<String> raw = buildingTags;
        if (raw == null || raw.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String t : raw) {
            if (t != null && !t.isBlank()) {
                out.add(t.trim().toLowerCase(Locale.ROOT));
            }
        }
        return Collections.unmodifiableSet(out);
    }
}
