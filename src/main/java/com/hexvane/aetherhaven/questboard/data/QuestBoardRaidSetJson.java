package com.hexvane.aetherhaven.questboard.data;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class QuestBoardRaidSetJson {
    @SerializedName("weight")
    private int weight;

    @SerializedName("mobCountsByRank")
    @Nullable
    private Map<String, Integer> mobCountsByRank;

    @SerializedName("mobPool")
    @Nullable
    private List<QuestBoardRaidMobPoolEntryJson> mobPool;

    @SerializedName("guaranteedMobs")
    @Nullable
    private List<QuestBoardRaidGuaranteedMobJson> guaranteedMobs;

    public int weight() {
        return Math.max(1, weight);
    }

    @Nonnull
    public Map<String, Integer> mobCountsByRankOrEmpty() {
        return mobCountsByRank != null ? mobCountsByRank : Map.of();
    }

    @Nonnull
    public List<QuestBoardRaidMobPoolEntryJson> mobPoolOrEmpty() {
        return mobPool != null ? mobPool : List.of();
    }

    @Nonnull
    public List<QuestBoardRaidGuaranteedMobJson> guaranteedMobsOrEmpty() {
        return guaranteedMobs != null ? guaranteedMobs : List.of();
    }
}
