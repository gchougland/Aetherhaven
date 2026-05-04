package com.hexvane.aetherhaven.quest.data;

import com.google.gson.annotations.SerializedName;
import javax.annotation.Nullable;

public final class QuestReward {
    @SerializedName("kind")
    @Nullable
    private String kind;

    @SerializedName("amount")
    private int amount;

    @SerializedName("npcRoleId")
    @Nullable
    private String npcRoleId;

    @SerializedName("grantTo")
    @Nullable
    private String grantTo;

    @SerializedName("itemId")
    @Nullable
    private String itemId;

    @SerializedName("count")
    private int count;

    @SerializedName("currencyId")
    @Nullable
    private String currencyId;

    @SerializedName("target")
    @Nullable
    private String target;

    @SerializedName("unlockId")
    @Nullable
    private String unlockId;

    /** Output item id whose crafting recipe is learned ({@code kind: learn_recipe}). */
    @SerializedName("recipeItemId")
    @Nullable
    private String recipeItemId;

    @Nullable
    public String kind() {
        return kind;
    }

    public int amount() {
        return amount;
    }

    @Nullable
    public String npcRoleId() {
        return npcRoleId;
    }

    @Nullable
    public String grantTo() {
        return grantTo;
    }

    @Nullable
    public String itemId() {
        return itemId;
    }

    public int count() {
        return count <= 0 ? 1 : count;
    }

    @Nullable
    public String currencyId() {
        return currencyId;
    }

    @Nullable
    public String target() {
        return target;
    }

    @Nullable
    public String unlockId() {
        return unlockId;
    }

    @Nullable
    public String recipeItemId() {
        return recipeItemId;
    }
}
