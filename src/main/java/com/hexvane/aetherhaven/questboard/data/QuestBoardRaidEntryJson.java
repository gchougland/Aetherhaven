package com.hexvane.aetherhaven.questboard.data;

import com.google.gson.annotations.SerializedName;
import com.hexvane.aetherhaven.quest.data.QuestReward;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class QuestBoardRaidEntryJson {
    @SerializedName("id")
    @Nullable
    private String id;

    @SerializedName("rank")
    @Nullable
    private String rank;

    @SerializedName("minRank")
    @Nullable
    private String minRank;

    @SerializedName("maxRank")
    @Nullable
    private String maxRank;

    @SerializedName("weight")
    private int weight;

    @SerializedName("daysLimit")
    private int daysLimit;

    @SerializedName("rankXpReward")
    private int rankXpReward;

    @SerializedName("titleLangKey")
    @Nullable
    private String titleLangKey;

    @SerializedName("descriptionLangKey")
    @Nullable
    private String descriptionLangKey;

    @SerializedName("targetLabelLangKey")
    @Nullable
    private String targetLabelLangKey;

    @SerializedName("raidSets")
    @Nullable
    private List<QuestBoardRaidSetJson> raidSets;

    @SerializedName("rewards")
    @Nullable
    private List<QuestReward> rewards;

    /**
     * Makes this raid a rescue: the attackers march on a temporary camp where a townsfolk is held, and finishing the
     * contract brings that townsfolk into town. Each entry is a prefab path under {@code Server/Prefabs}.
     */
    @SerializedName("rescueCampPrefabs")
    @Nullable
    private List<String> rescueCampPrefabs;

    @Nonnull
    public List<String> rescueCampPrefabsOrEmpty() {
        return rescueCampPrefabs != null ? rescueCampPrefabs : List.of();
    }

    public boolean isRescue() {
        return rescueCampPrefabs != null && !rescueCampPrefabs.isEmpty();
    }

    @Nullable
    public String id() {
        return id;
    }

    @Nullable
    public String rank() {
        return rank;
    }

    @Nullable
    public String minRank() {
        return minRank;
    }

    @Nullable
    public String maxRank() {
        return maxRank;
    }

    public int weight() {
        return Math.max(1, weight);
    }

    public int daysLimit() {
        return Math.max(1, daysLimit);
    }

    public int rankXpReward() {
        return rankXpReward;
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
    public String targetLabelLangKey() {
        return targetLabelLangKey;
    }

    @Nonnull
    public List<QuestBoardRaidSetJson> raidSetsOrEmpty() {
        return raidSets != null ? raidSets : List.of();
    }

    @Nonnull
    public List<QuestReward> rewardsOrEmpty() {
        return rewards != null ? rewards : List.of();
    }
}
