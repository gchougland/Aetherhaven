package com.hexvane.aetherhaven.quest.data;

import com.google.gson.annotations.SerializedName;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class QuestPrerequisites {
    static final QuestPrerequisites EMPTY = new QuestPrerequisites();

    @SerializedName("completedQuestIds")
    @Nullable
    private List<String> completedQuestIds;

    @SerializedName("anyCompletedQuestIds")
    @Nullable
    private List<String> anyCompletedQuestIds;

    @Nonnull
    public List<String> completedQuestIdsOrEmpty() {
        return completedQuestIds != null ? completedQuestIds : Collections.emptyList();
    }

    @Nonnull
    public List<String> anyCompletedQuestIdsOrEmpty() {
        return anyCompletedQuestIds != null ? anyCompletedQuestIds : Collections.emptyList();
    }
}
