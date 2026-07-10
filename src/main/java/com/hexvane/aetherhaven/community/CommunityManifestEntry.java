package com.hexvane.aetherhaven.community;

import com.google.gson.annotations.SerializedName;
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

    @Nonnull
    public String prefabPathKey() {
        return getId() + ".prefab.json";
    }
}
