package com.hexvane.aetherhaven.quest.data;

import com.google.gson.annotations.SerializedName;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class QuestLifecycle {
    static final QuestLifecycle EMPTY = new QuestLifecycle();

    @SerializedName("onStart")
    @Nullable
    private List<QuestEffectEntry> onStart;

    @SerializedName("onComplete")
    @Nullable
    private List<QuestEffectEntry> onComplete;

    @SerializedName("onAbandon")
    @Nullable
    private List<QuestEffectEntry> onAbandon;

    @Nonnull
    public List<QuestEffectEntry> onStartOrEmpty() {
        return onStart != null ? onStart : Collections.emptyList();
    }

    @Nonnull
    public List<QuestEffectEntry> onCompleteOrEmpty() {
        return onComplete != null ? onComplete : Collections.emptyList();
    }

    @Nonnull
    public List<QuestEffectEntry> onAbandonOrEmpty() {
        return onAbandon != null ? onAbandon : Collections.emptyList();
    }
}
