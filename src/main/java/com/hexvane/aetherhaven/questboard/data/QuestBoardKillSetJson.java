package com.hexvane.aetherhaven.questboard.data;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class QuestBoardKillSetJson {
    @SerializedName("weight")
    private int weight;

    @SerializedName("killCount")
    private int killCount;

    @SerializedName("entityTagsAny")
    @Nullable
    private List<String> entityTagsAny;

    public int weight() {
        return Math.max(1, weight);
    }

    public int killCount() {
        return Math.max(1, killCount);
    }

    /** Mining contracts: block ids that count when a player breaks them, matched by prefix (for example {@code Ore_Iron_}). */
    @SerializedName("blockIdPrefixesAny")
    @Nullable
    private List<String> blockIdPrefixesAny;

    @Nonnull
    public List<String> blockIdPrefixesAnyOrEmpty() {
        return blockIdPrefixesAny != null ? blockIdPrefixesAny : List.of();
    }

    @Nonnull
    public List<String> entityTagsAnyOrEmpty() {
        return entityTagsAny != null ? entityTagsAny : List.of();
    }
}
