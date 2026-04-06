package com.hexvane.aetherhaven.quest.data;

import com.google.gson.annotations.SerializedName;
import javax.annotation.Nullable;

public final class QuestRepeat {
    static final QuestRepeat NONE = new QuestRepeat();

    @SerializedName("mode")
    @Nullable
    private String mode;

    @Nullable
    public String mode() {
        return mode != null ? mode : "none";
    }
}
