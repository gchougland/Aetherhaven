package com.hexvane.aetherhaven.questboard;

import com.google.gson.annotations.SerializedName;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Last town quest board completion for one player (for dialogue follow up). */
public final class QuestBoardPlayerCompleteRecord {
    @SerializedName("dawnDay")
    private long dawnDay;

    @Nullable
    @SerializedName("giverRoleId")
    private String giverRoleId;

    @Nullable
    @SerializedName("configEntryId")
    private String configEntryId;

    public QuestBoardPlayerCompleteRecord() {}

    public QuestBoardPlayerCompleteRecord(
        long dawnDay, @Nullable String giverRoleId, @Nullable String configEntryId
    ) {
        this.dawnDay = dawnDay;
        this.giverRoleId = giverRoleId;
        this.configEntryId = configEntryId;
    }

    public long getDawnDay() {
        return dawnDay;
    }

    @Nullable
    public String getGiverRoleId() {
        return giverRoleId;
    }

    @Nullable
    public String getConfigEntryId() {
        return configEntryId;
    }

    public boolean matchesFilters(@Nullable String wantedGiverRoleId, @Nullable String wantedConfigEntryId) {
        if (wantedGiverRoleId != null && !wantedGiverRoleId.isBlank()) {
            String got = giverRoleId != null ? giverRoleId.trim() : "";
            if (got.isEmpty() || !got.equalsIgnoreCase(wantedGiverRoleId.trim())) {
                return false;
            }
        }
        if (wantedConfigEntryId != null && !wantedConfigEntryId.isBlank()) {
            String got = configEntryId != null ? configEntryId.trim() : "";
            if (got.isEmpty() || !got.equalsIgnoreCase(wantedConfigEntryId.trim())) {
                return false;
            }
        }
        return true;
    }
}
