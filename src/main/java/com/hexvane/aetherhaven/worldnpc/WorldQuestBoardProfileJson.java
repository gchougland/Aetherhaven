package com.hexvane.aetherhaven.worldnpc;

import com.google.gson.annotations.SerializedName;
import com.hexvane.aetherhaven.questboard.data.QuestBoardRankTierJson;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** JSON profile for a per-player world quest board opened from a hub NPC. */
public final class WorldQuestBoardProfileJson {
    @SerializedName("schemaVersion")
    private int schemaVersion = 1;

    @SerializedName("profileId")
    @Nullable
    private String profileId;

    @SerializedName("slotCount")
    private int slotCount = 3;

    @SerializedName("ranks")
    @Nullable
    private List<QuestBoardRankTierJson> ranks;

    @SerializedName("pool")
    @Nullable
    private List<WorldQuestBoardPoolEntryJson> pool;

    public int schemaVersion() {
        return schemaVersion;
    }

    @Nonnull
    public String profileIdOrEmpty() {
        return profileId != null ? profileId.trim() : "";
    }

    public void setProfileId(@Nonnull String profileId) {
        this.profileId = profileId;
    }

    public int slotCount() {
        return Math.max(1, slotCount);
    }

    @Nonnull
    public List<QuestBoardRankTierJson> ranksOrEmpty() {
        return ranks != null ? ranks : List.of();
    }

    @Nonnull
    public List<WorldQuestBoardPoolEntryJson> poolOrEmpty() {
        if (pool == null) {
            pool = new ArrayList<>();
        }
        return pool;
    }
}
