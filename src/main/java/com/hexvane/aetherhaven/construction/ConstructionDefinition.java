package com.hexvane.aetherhaven.construction;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
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

    /**
     * Exact staff-drawn plot box in prefab-local space (relative to prefab buffer / plot-sign anchor), inclusive
     * min/max. When set, plot footprint uses this volume instead of shrinking to solid voxels. Omitted on older
     * buildings that still use solid-fit.
     */
    @SerializedName("boundsLocal")
    @Nullable
    private BoundsLocalJson boundsLocal;

    /** If set, player must carry this item to select this construction in the placement tool UI. */
    @SerializedName("plotTokenItemId")
    @Nullable
    private String plotTokenItemId;

    @SerializedName("rotationYaw")
    private String rotationYaw = "None";

    /**
     * Prefab-local cardinal that is the front of this build ({@code North}/{@code East}/{@code South}/{@code West}).
     * Used for default placement facing the player and for icon / screenshot framing. Default North ({@code -Z}).
     */
    @SerializedName("frontFacing")
    @Nullable
    private String frontFacing;

    @SerializedName("requiredVillagerId")
    @Nullable
    private String requiredVillagerId;

    /**
     * Unused. Kept so older construction JSON with {@code assemblyDeferredBlockIds} still loads.
     * Support blocks (trunks, leaves, etc.) place during assembly with deco support instead.
     */
    @SerializedName("assemblyDeferredBlockIds")
    private List<String> assemblyDeferredBlockIds = Collections.emptyList();

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
     * External asset packs required to place/use this build (from marketplace building JSON). Empty or omitted means
     * no extra mods.
     */
    @SerializedName("requiredMods")
    @Nullable
    private List<com.hexvane.aetherhaven.community.CommunityRequiredMods.RequiredMod> requiredMods;

    /**
     * When set, this variant counts as one or more canonical constructions for quests, production keys, workplace
     * matching, and schedule resolution ({@code plotInstance.constructionId} stays this definition's {@link #id}).
     * JSON may be a string or an array of strings.
     */
    @SerializedName("countsAsConstructionId")
    @Nullable
    private JsonElement countsAsConstructionId;

    /**
     * When true on the <em>canonical</em> gameplay definition (and variants inheriting via {@link #countsAsConstructionId}),
     * schedule segments that target this construction pick randomly among all complete matching plots (see catalog helper).
     */
    @SerializedName("scheduleSharedUtilityPick")
    private boolean scheduleSharedUtilityPick;

    /** When true, portal tourists may visit this plot. */
    @SerializedName("touristDestination")
    private boolean touristDestination;

    /**
     * When true, assembly keeps existing world water in prefab air cells only (docks and piers). Editor empty still clears water.
     */
    @SerializedName("preserveWater")
    private boolean preserveWater;

    /**
     * Max villagers assignable on a house plot (town records shelf slots). Used for {@code plot_house} gameplay
     * constructions; omitted or {@code 0} means {@code 1}. Clamped to {@code 1..8}.
     */
    @SerializedName("maxHomeResidents")
    private int maxHomeResidents = 1;

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

    /** Prefab-local position of the inn bell block; optional. */
    @SerializedName("innBellLocalPos")
    @Nullable
    private int[] innBellLocalPos;

    /** Prefab-local position of the workplace production storage wardrobe; optional. */
    @SerializedName("productionStorageLocalPos")
    @Nullable
    private int[] productionStorageLocalPos;

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

    /** When true, the town journal plot list omits this construction. */
    @SerializedName("excludeFromTownJournal")
    private boolean excludeFromTownJournal;

    /**
     * Kept so already placed plots still count as houses or workplaces after the prefab was removed from the mod.
     * Not craftable or placeable.
     */
    @SerializedName("legacyPlotSupport")
    private boolean legacyPlotSupport;

    /** Town wall segment: journal excluded, overlap rules differ, completion moves to {@link com.hexvane.aetherhaven.town.WallSegmentRecord}. */
    @SerializedName("wallSegment")
    private boolean wallSegment;

    /** Role and connection points used by the wall wand to join this piece flush to its neighbours. */
    @SerializedName("wallPiece")
    @Nullable
    private com.hexvane.aetherhaven.wall.WallPieceDefinition wallPiece;

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

    public void setPrefabPath(@Nullable String prefabPath) {
        this.prefabPath = prefabPath;
    }

    public int[] getPlotAnchorOffset() {
        return plotAnchorOffset != null && plotAnchorOffset.length == 3 ? plotAnchorOffset : new int[] {0, 0, 0};
    }

    /**
     * Inclusive prefab-local min corner of the authored plot box, or null when the building has no stored bounds
     * (legacy solid-fit footprint).
     */
    @Nullable
    public Vector3i getBoundsLocalMin() {
        return boundsLocal != null ? boundsLocal.minOrNull() : null;
    }

    /**
     * Inclusive prefab-local max corner of the authored plot box, or null when the building has no stored bounds
     * (legacy solid-fit footprint).
     */
    @Nullable
    public Vector3i getBoundsLocalMax() {
        return boundsLocal != null ? boundsLocal.maxOrNull() : null;
    }

    /** True when {@link #getBoundsLocalMin()} and {@link #getBoundsLocalMax()} are both present. */
    public boolean hasBoundsLocal() {
        return getBoundsLocalMin() != null && getBoundsLocalMax() != null;
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

    /**
     * Inverse of {@link #resolvePrefabAnchorWorld(Vector3i, Rotation)}: session preview sign pose that reproduces
     * {@code prefabAnchorWorld} (used when opening move mode from a stored building pose).
     */
    @Nonnull
    public Vector3i resolvePreviewSignAnchorWorld(@Nonnull Vector3i prefabAnchorWorld, @Nonnull Rotation placementYaw) {
        int[] o = getPlotAnchorOffset();
        Vector3i off = new Vector3i(o[0], o[1], o[2]);
        PrefabRotation.fromRotation(placementYaw).rotate(off);
        return new Vector3i(
            prefabAnchorWorld.x - off.x,
            prefabAnchorWorld.y - off.y + AetherhavenConstants.PLOT_SIGN_BLOCK_Y_ABOVE_LOGICAL_ANCHOR,
            prefabAnchorWorld.z - off.z
        );
    }

    @Nullable
    public String getPlotTokenItemId() {
        return plotTokenItemId;
    }

    public String getRotationYaw() {
        return rotationYaw != null ? rotationYaw : "None";
    }

    /** Prefab-local front cardinal; missing or blank values default to North. */
    @Nonnull
    public String getFrontFacing() {
        return com.hexvane.aetherhaven.placement.FrontFacing.normalize(frontFacing);
    }

    public void setFrontFacing(@Nullable String frontFacing) {
        this.frontFacing = com.hexvane.aetherhaven.placement.FrontFacing.normalize(frontFacing);
    }

    @Nullable
    public String getRequiredVillagerId() {
        return requiredVillagerId;
    }

    public List<MaterialRequirement> getMaterials() {
        return materials != null ? materials : Collections.emptyList();
    }

    /** Unused; see {@link #assemblyDeferredBlockIds}. */
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

    public long getTreasuryGoldCoinCost() {
        return Math.max(0L, treasuryGoldCoinCost);
    }

    public double getSelfBuildGameDays() {
        return selfBuildGameDays > 0.0 ? selfBuildGameDays : 3.0;
    }

    /** House assignment slots; {@code 1} when unset. Clamped to {@code 1..8}. */
    public int getMaxHomeResidents() {
        if (maxHomeResidents <= 0) {
            return 1;
        }
        return Math.min(8, maxHomeResidents);
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

    @Nonnull
    public List<com.hexvane.aetherhaven.community.CommunityRequiredMods.RequiredMod> getRequiredMods() {
        return requiredMods != null ? requiredMods : List.of();
    }

    /**
     * Primary gameplay construction id: first {@link #getCountsAsConstructionIds()} entry if any, otherwise {@link #getId}.
     */
    @Nonnull
    public String getGameplayConstructionId() {
        List<String> aliases = getCountsAsConstructionIds();
        if (!aliases.isEmpty()) {
            return aliases.get(0);
        }
        return id != null ? id : "";
    }

    /**
     * Alias targets declared on this definition (legacy string or array). Empty when this definition is canonical.
     */
    @Nonnull
    public List<String> getCountsAsConstructionIds() {
        if (countsAsConstructionId == null || countsAsConstructionId.isJsonNull()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        if (countsAsConstructionId.isJsonPrimitive() && countsAsConstructionId.getAsJsonPrimitive().isString()) {
            String s = countsAsConstructionId.getAsString();
            if (s != null && !s.isBlank()) {
                out.add(s.trim());
            }
            return List.copyOf(out);
        }
        if (countsAsConstructionId.isJsonArray()) {
            JsonArray arr = countsAsConstructionId.getAsJsonArray();
            for (JsonElement el : arr) {
                if (el == null || !el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()) {
                    continue;
                }
                String s = el.getAsString();
                if (s != null && !s.isBlank() && !out.contains(s.trim())) {
                    out.add(s.trim());
                }
            }
        }
        return List.copyOf(out);
    }

    /** First alias, or null when none (legacy single-value callers). */
    @Nullable
    public String getCountsAsConstructionIdRaw() {
        List<String> aliases = getCountsAsConstructionIds();
        return aliases.isEmpty() ? null : aliases.get(0);
    }

    public void setCountsAsConstructionIds(@Nullable List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            this.countsAsConstructionId = null;
            return;
        }
        List<String> cleaned = new ArrayList<>();
        for (String idVal : ids) {
            if (idVal != null && !idVal.isBlank() && !cleaned.contains(idVal.trim())) {
                cleaned.add(idVal.trim());
            }
        }
        if (cleaned.isEmpty()) {
            this.countsAsConstructionId = null;
            return;
        }
        if (cleaned.size() == 1) {
            this.countsAsConstructionId = new com.google.gson.JsonPrimitive(cleaned.get(0));
            return;
        }
        JsonArray arr = new JsonArray();
        for (String s : cleaned) {
            arr.add(s);
        }
        this.countsAsConstructionId = arr;
    }

    /** True when this definition participates in multi-plot random schedule targeting (inn, park, Gaia altar, …). */
    public boolean isScheduleSharedUtilityPick() {
        return scheduleSharedUtilityPick;
    }

    public boolean isTouristDestination() {
        return touristDestination;
    }

    public boolean isPreserveWater() {
        return preserveWater;
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

    /** @return prefab-local x,y,z of inn bell block, or null */
    @Nullable
    public int[] getInnBellLocalPos() {
        return innBellLocalPos != null && innBellLocalPos.length == 3 ? innBellLocalPos : null;
    }

    /** @return prefab-local x,y,z of workplace production storage wardrobe, or null */
    @Nullable
    public int[] getProductionStorageLocalPos() {
        return productionStorageLocalPos != null && productionStorageLocalPos.length == 3 ? productionStorageLocalPos : null;
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

    public boolean isExcludeFromTownJournal() {
        return excludeFromTownJournal;
    }

    public boolean isLegacyPlotSupport() {
        return legacyPlotSupport;
    }

    public boolean isWallSegment() {
        return wallSegment;
    }

    @Nullable
    public com.hexvane.aetherhaven.wall.WallPieceDefinition getWallPiece() {
        return wallPiece;
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

    private static final class BoundsLocalJson {
        @SerializedName("min")
        @Nullable
        private int[] min;

        @SerializedName("max")
        @Nullable
        private int[] max;

        @Nullable
        Vector3i minOrNull() {
            return min != null && min.length == 3 ? new Vector3i(min[0], min[1], min[2]) : null;
        }

        @Nullable
        Vector3i maxOrNull() {
            return max != null && max.length == 3 ? new Vector3i(max[0], max[1], max[2]) : null;
        }
    }
}
