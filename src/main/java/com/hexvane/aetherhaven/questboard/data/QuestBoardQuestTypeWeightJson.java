package com.hexvane.aetherhaven.questboard.data;

import com.google.gson.annotations.SerializedName;

public final class QuestBoardQuestTypeWeightJson {
    @SerializedName("weight")
    private int weight;

    /** Multiplier applied to gold coin item rewards when this quest type is rolled (defaults to 1). */
    @SerializedName("goldCoinMultiplier")
    private double goldCoinMultiplier = 1.0;

    public int weight() {
        return Math.max(1, weight);
    }

    public double goldCoinMultiplier() {
        return goldCoinMultiplier > 0.0 ? goldCoinMultiplier : 1.0;
    }
}
