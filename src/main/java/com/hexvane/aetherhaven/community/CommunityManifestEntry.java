package com.hexvane.aetherhaven.community;

import com.google.gson.annotations.SerializedName;
import com.hexvane.aetherhaven.construction.MaterialRequirement;
import java.util.List;
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

    @Nullable
    public String getStyleId() {
        return styleId;
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

    @Nonnull
    public String prefabPathKey() {
        return getId() + ".prefab.json";
    }
}
