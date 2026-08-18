package com.hexvane.aetherhaven.community;

import com.google.gson.annotations.SerializedName;
import com.hexvane.aetherhaven.construction.MaterialRequirement;
import com.hexvane.aetherhaven.plot.PlotBuildingTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** One approved building in the remote manifest (metadata only). */
public final class CommunityManifestEntry {
    @SerializedName("id")
    private String id;

    @SerializedName("displayName")
    private String displayName;

    @SerializedName("creatorUuid")
    private String creatorUuid;

    @SerializedName("creatorName")
    private String creatorName;

    @SerializedName("styleId")
    private String styleId;

    @SerializedName("tags")
    private List<String> tags;

    @SerializedName("decorationPlot")
    private boolean decorationPlot;

    /** One piece of a wall style. Wall entries are grouped by {@link #styleId} into a single bench card. */
    @SerializedName("wallSegment")
    private boolean wallSegment;

    /** Which job the piece does in its style: segment, gate, tower_end, tower_straight, tower_corner. */
    @SerializedName("wallPieceRole")
    private String wallPieceRole;

    /** Marketplace festival look: own prefab and spots, no calendar day. */
    @SerializedName("festivalVariant")
    private boolean festivalVariant;

    /** Base holiday this look counts as. */
    @SerializedName("countsAsFestivalId")
    private String countsAsFestivalId;

    /** String or array of core construction ids this variant counts as. */
    @SerializedName("countsAsConstructionId")
    private com.google.gson.JsonElement countsAsConstructionId;

    @SerializedName("blockIdVersion")
    private int blockIdVersion;

    @SerializedName("prefabBytes")
    private long prefabBytes;

    @SerializedName("version")
    private String version;

    @SerializedName("compatible")
    private boolean compatible = true;

    @SerializedName("iconUrl")
    private String iconUrl;

    @SerializedName("buildingUrl")
    private String buildingUrl;

    @SerializedName("prefabUrl")
    private String prefabUrl;

    @SerializedName("upvoteCount")
    private int upvoteCount;

    @SerializedName("downloadCount")
    private int downloadCount;

    @SerializedName("approvedAt")
    private String approvedAt;

    @SerializedName("requiredMods")
    private List<CommunityRequiredMods.RequiredMod> requiredMods;

    @SerializedName("description")
    private String description;

    @SerializedName("treasuryGoldCoinCost")
    private long treasuryGoldCoinCost;

    @SerializedName("materials")
    private List<MaterialRequirement> materials;

    @SerializedName("userHasFavorited")
    private boolean userHasFavorited;

    @Nonnull
    public String getId() {
        return id != null ? id : "";
    }

    @Nonnull
    public String getDisplayName() {
        return displayName != null && !displayName.isBlank() ? displayName : getId();
    }

    @Nonnull
    public String getCreatorName() {
        return creatorName != null && !creatorName.isBlank() ? creatorName : "Unknown";
    }

    @Nonnull
    public String getCreatorUuid() {
        return creatorUuid != null ? creatorUuid.trim().toLowerCase(java.util.Locale.ROOT) : "";
    }

    @Nullable
    public String getStyleId() {
        return styleId;
    }

    public boolean isDecorationPlot() {
        return decorationPlot || getId().toLowerCase(Locale.ROOT).startsWith("plot_decoration");
    }

    /** Core building ids this community build counts as (variant targets). */
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
            for (com.google.gson.JsonElement el : countsAsConstructionId.getAsJsonArray()) {
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

    public boolean isWallSegment() {
        return wallSegment;
    }

    public boolean isFestivalVariant() {
        return festivalVariant || (countsAsFestivalId != null && !countsAsFestivalId.isBlank());
    }

    @Nullable
    public String getCountsAsFestivalId() {
        return countsAsFestivalId != null && !countsAsFestivalId.isBlank() ? countsAsFestivalId.trim() : null;
    }

    /** Role inside the wall style, or null on entries that are not wall pieces. */
    @Nullable
    public com.hexvane.aetherhaven.wall.WallPieceRole getWallPieceRole() {
        return com.hexvane.aetherhaven.wall.WallPieceRole.fromSerialized(wallPieceRole);
    }

    /** Type filter keys: walls, decoration, or core countsAs / self id. */
    @Nonnull
    public Set<String> getTypeIds() {
        return PlotBuildingTypes.typeIdsOf(
            isDecorationPlot(),
            isWallSegment(),
            isFestivalVariant(),
            getCountsAsConstructionIds(),
            getId()
        );
    }

    public int getBlockIdVersion() {
        return blockIdVersion;
    }

    public long getPrefabBytes() {
        return prefabBytes;
    }

    @Nonnull
    public String getVersion() {
        return version != null ? version : "1";
    }

    public boolean isCompatible() {
        return compatible;
    }

    @Nullable
    public String getIconUrl() {
        return iconUrl;
    }

    @Nullable
    public String getBuildingUrl() {
        return buildingUrl;
    }

    @Nullable
    public String getPrefabUrl() {
        return prefabUrl;
    }

    public int getUpvoteCount() {
        return upvoteCount;
    }

    public int getDownloadCount() {
        return downloadCount;
    }

    /** ISO-8601 approval time from the marketplace API; may be blank on older entries. */
    @Nonnull
    public String getApprovedAt() {
        return approvedAt != null ? approvedAt : "";
    }

    @Nonnull
    public List<CommunityRequiredMods.RequiredMod> getRequiredMods() {
        return requiredMods != null ? requiredMods : List.of();
    }

    @Nonnull
    public String getDescription() {
        return description != null ? description : "";
    }

    public long getTreasuryGoldCoinCost() {
        return Math.max(0L, treasuryGoldCoinCost);
    }

    @Nonnull
    public List<MaterialRequirement> getMaterials() {
        return materials != null ? materials : List.of();
    }

    public boolean isUserHasFavorited() {
        return userHasFavorited;
    }

    @Nonnull
    public String prefabPathKey() {
        if (isFestivalVariant()) {
            return com.hexvane.aetherhaven.festival.CustomFestivalPaths.prefabPathKey(getId());
        }
        return getId() + ".prefab.json";
    }
}
