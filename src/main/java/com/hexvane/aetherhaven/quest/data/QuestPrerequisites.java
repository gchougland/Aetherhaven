package com.hexvane.aetherhaven.quest.data;

import com.google.gson.annotations.SerializedName;
import com.hexvane.aetherhaven.town.TownRecord;
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

    /** True when every required quest is completed on the town and the {@code any} group (if non-empty) has one hit. */
    public boolean satisfiedBy(@Nonnull TownRecord town) {
        for (String id : completedQuestIdsOrEmpty()) {
            if (id == null || id.isBlank()) {
                continue;
            }
            if (!town.hasQuestCompleted(id.trim())) {
                return false;
            }
        }
        List<String> any = anyCompletedQuestIdsOrEmpty();
        if (any.isEmpty()) {
            return true;
        }
        for (String id : any) {
            if (id != null && !id.isBlank() && town.hasQuestCompleted(id.trim())) {
                return true;
            }
        }
        return false;
    }
}
