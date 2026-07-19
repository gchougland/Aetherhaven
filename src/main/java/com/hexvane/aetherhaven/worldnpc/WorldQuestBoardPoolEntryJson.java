package com.hexvane.aetherhaven.worldnpc;

import com.google.gson.annotations.SerializedName;
import com.hexvane.aetherhaven.quest.data.QuestReward;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class WorldQuestBoardPoolEntryJson {
    @SerializedName("id")
    @Nullable
    private String id;

    @SerializedName("weight")
    private int weight = 1;

    @SerializedName("titleLangKey")
    @Nullable
    private String titleLangKey;

    @SerializedName("descriptionLangKey")
    @Nullable
    private String descriptionLangKey;

    @SerializedName("minRank")
    @Nullable
    private String minRank;

    @SerializedName("maxRank")
    @Nullable
    private String maxRank;

    @SerializedName("rankXpReward")
    private int rankXpReward = 10;

    @SerializedName("daysLimit")
    private int daysLimit = 1;

    @SerializedName("questType")
    @Nullable
    private String questType;

    @SerializedName("rewards")
    @Nullable
    private List<QuestReward> rewards;

    @Nonnull
    public String idOrEmpty() {
        return id != null ? id.trim() : "";
    }

    public int weight() {
        return Math.max(1, weight);
    }

    @Nullable
    public String titleLangKey() {
        return titleLangKey;
    }

    @Nullable
    public String descriptionLangKey() {
        return descriptionLangKey;
    }

    @Nullable
    public String minRank() {
        return minRank;
    }

    @Nullable
    public String maxRank() {
        return maxRank;
    }

    public int rankXpReward() {
        return Math.max(0, rankXpReward);
    }

    public int daysLimit() {
        return Math.max(1, daysLimit);
    }

    @Nonnull
    public String questTypeOrFetch() {
        return questType != null && !questType.isBlank() ? questType.trim() : "fetch";
    }

    @Nonnull
    public List<QuestReward> rewardsOrEmpty() {
        return rewards != null ? rewards : List.of();
    }
}
