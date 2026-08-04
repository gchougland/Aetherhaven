package com.hexvane.aetherhaven.town;

import com.google.gson.annotations.SerializedName;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nonnull;

/** One persisted town event for the town records shelf log. */
public final class TownLogEntry {
    @SerializedName("gameEpochDay")
    private long gameEpochDay;

    @SerializedName("messageKey")
    private String messageKey = "";

    @SerializedName("params")
    private Map<String, String> params;

    public TownLogEntry() {}

    public TownLogEntry(long gameEpochDay, @Nonnull String messageKey, @Nonnull Map<String, String> params) {
        this.gameEpochDay = gameEpochDay;
        this.messageKey = messageKey != null ? messageKey.trim() : "";
        this.params = params != null ? new LinkedHashMap<>(params) : new LinkedHashMap<>();
    }

    public long getGameEpochDay() {
        return gameEpochDay;
    }

    @Nonnull
    public String getMessageKey() {
        return messageKey != null ? messageKey : "";
    }

    @Nonnull
    public Map<String, String> getParams() {
        if (params == null) {
            params = new LinkedHashMap<>();
        }
        return params;
    }
}
