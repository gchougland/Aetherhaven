package com.hexvane.aetherhaven.questboard.data;

import com.google.gson.annotations.SerializedName;
import javax.annotation.Nullable;

public final class QuestBoardRaidGuaranteedMobJson {
    @SerializedName("roleId")
    @Nullable
    private String roleId;

    @SerializedName("count")
    private int count;

    @Nullable
    public String roleId() {
        return roleId;
    }

    public int count() {
        return Math.max(1, count);
    }
}
