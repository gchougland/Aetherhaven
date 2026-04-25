package com.hexvane.aetherhaven.villager.data;

import com.google.gson.annotations.SerializedName;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Gson shape for reputation milestone rows inside villager definition JSON. */
public final class VillagerReputationMilestoneJson {
    @SerializedName("rewardId")
    private String rewardId;

    @SerializedName("minReputation")
    private int minReputation;

    @SerializedName("itemId")
    private String itemId = "";

    @SerializedName("itemCount")
    private int itemCount;

    @SerializedName("dialogueNodeId")
    private String dialogueNodeId = "";

    @SerializedName("learnRecipeItemId")
    @Nullable
    private String learnRecipeItemId;

    public String getRewardId() {
        return rewardId != null ? rewardId : "";
    }

    public int getMinReputation() {
        return minReputation;
    }

    @Nonnull
    public String getItemId() {
        return itemId != null ? itemId : "";
    }

    public int getItemCount() {
        return itemCount;
    }

    @Nonnull
    public String getDialogueNodeId() {
        return dialogueNodeId != null ? dialogueNodeId : "";
    }

    @Nullable
    public String getLearnRecipeItemId() {
        return learnRecipeItemId;
    }
}
