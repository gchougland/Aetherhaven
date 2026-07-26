package com.hexvane.aetherhaven.community;

import com.google.gson.annotations.SerializedName;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** One owned marketplace building row for the building editor picker. */
public final class CommunityMySubmissionEntry {
    @SerializedName("kind")
    private String kind;

    @SerializedName("id")
    private String id;

    @SerializedName("proposedId")
    private String proposedId;

    @SerializedName("submissionId")
    private String submissionId;

    @SerializedName("displayName")
    private String displayName;

    @SerializedName("status")
    private String status;

    @SerializedName("version")
    private String version;

    /** Set when a pending row replaces an approved row for the same catalog id. */
    private transient boolean liveVersionExists;

    @Nonnull
    public String getKind() {
        return kind != null ? kind : "";
    }

    /** Catalog id used for editing and uploads. */
    @Nonnull
    public String catalogId() {
        if ("approved".equalsIgnoreCase(getKind())) {
            return getId();
        }
        String proposed = getProposedId();
        return !proposed.isBlank() ? proposed : getId();
    }

    @Nonnull
    public String getId() {
        return id != null ? id : "";
    }

    @Nonnull
    public String getProposedId() {
        return proposedId != null ? proposedId : "";
    }

    @Nonnull
    public String getSubmissionId() {
        return submissionId != null ? submissionId : "";
    }

    @Nonnull
    public String getDisplayName() {
        return displayName != null && !displayName.isBlank() ? displayName : catalogId();
    }

    @Nonnull
    public String getStatus() {
        return status != null ? status : "";
    }

    @Nonnull
    public String getVersion() {
        return version != null ? version : "1";
    }

    public boolean isApproved() {
        return "approved".equalsIgnoreCase(getKind()) || "approved".equalsIgnoreCase(getStatus());
    }

    public boolean isPending() {
        return "pending".equalsIgnoreCase(getKind()) || "pending".equalsIgnoreCase(getStatus());
    }

    public boolean isRejected() {
        return "rejected".equalsIgnoreCase(getKind()) || "rejected".equalsIgnoreCase(getStatus());
    }

    public boolean isUpdateWaiting() {
        return isPending() && liveVersionExists;
    }

    public void setLiveVersionExists(boolean liveVersionExists) {
        this.liveVersionExists = liveVersionExists;
    }

    public boolean hasLiveVersion() {
        return isApproved() || liveVersionExists;
    }

    @Nullable
    public String ownerDownloadSubmissionId() {
        if (isApproved()) {
            return null;
        }
        String submissionId = getSubmissionId();
        return submissionId.isBlank() ? null : submissionId;
    }
}
