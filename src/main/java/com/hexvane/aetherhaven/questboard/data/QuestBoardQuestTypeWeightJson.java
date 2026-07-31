package com.hexvane.aetherhaven.questboard.data;

import com.google.gson.annotations.SerializedName;

public final class QuestBoardQuestTypeWeightJson {
    @SerializedName("weight")
    private int weight;

    public int weight() {
        return Math.max(1, weight);
    }
}
