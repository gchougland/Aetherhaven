package com.hexvane.aetherhaven.community;

import com.google.gson.annotations.SerializedName;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** One building awaiting moderator approval on the community marketplace. */
public final class CommunityPendingEntry {
    @SerializedName("submissionId")
    private String submissionId;

    @SerializedName("proposedId")
    private String proposedId;

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

    @SerializedName("submittedAt")
    private String submittedAt;

    @SerializedName("status")
    private String status;

    @Nonnull
    public String getSubmissionId() {
        return submissionId != null ? submissionId : "";
    }

    @Nonnull
    public String getProposedId() {
        return proposedId != null ? proposedId : "";
    }

    @Nonnull
    public String getDisplayName() {
        return displayName != null && !displayName.isBlank() ? displayName : getSubmissionId();
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

    @Nullable
    public String getSubmittedAt() {
        return submittedAt;
    }

    @Nonnull
    public String getStatus() {
        return status != null ? status : "pending";
    }

    /** Synthetic construction id for moderation preview icons (avoids catalog id collisions). */
    @Nonnull
    public String iconConstructionId() {
        return CommunityModerationService.iconConstructionId(getSubmissionId());
    }

    @Nonnull
    public String prefabPathKey() {
        return getSubmissionId() + ".prefab.json";
    }

    @Nonnull
    public String moderationPrefabUrl(@Nonnull String apiBaseUrl) {
        return apiBaseUrl + "/api/v1/moderation/submissions/" + getSubmissionId() + "/prefab.json";
    }

    @Nonnull
    public String moderationIconUrl(@Nonnull String apiBaseUrl) {
        return apiBaseUrl + "/api/v1/moderation/submissions/" + getSubmissionId() + "/icon.png";
    }

    @Nonnull
    public String moderationBuildingUrl(@Nonnull String apiBaseUrl) {
        return apiBaseUrl + "/api/v1/moderation/submissions/" + getSubmissionId() + "/building.json";
    }
}
