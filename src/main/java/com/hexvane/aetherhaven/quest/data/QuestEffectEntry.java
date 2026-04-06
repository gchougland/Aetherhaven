package com.hexvane.aetherhaven.quest.data;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class QuestEffectEntry {
    @SerializedName("effect")
    @Nullable
    private String effect;

    @SerializedName("params")
    @Nullable
    private JsonObject params;

    @Nullable
    public String effect() {
        return effect;
    }

    @Nonnull
    public JsonObject paramsOrEmpty() {
        return params != null ? params : new JsonObject();
    }
}
