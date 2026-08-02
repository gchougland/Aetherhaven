package com.hexvane.aetherhaven.quest;

import com.hexvane.aetherhaven.AetherhavenConstants;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Journal row ids for save wide player quests ({@code category: "player"}). */
public final class PlayerQuestIds {
    private PlayerQuestIds() {}

    @Nonnull
    public static String playerRow(@Nonnull String questId) {
        return AetherhavenConstants.PLAYER_QUEST_JOURNAL_PREFIX + questId.trim();
    }

    public static boolean isPlayerQuestRow(@Nullable String rowId) {
        return rowId != null && rowId.startsWith(AetherhavenConstants.PLAYER_QUEST_JOURNAL_PREFIX);
    }

    @Nullable
    public static String parsePlayerQuestId(@Nullable String rowId) {
        if (!isPlayerQuestRow(rowId)) {
            return null;
        }
        String id = rowId.substring(AetherhavenConstants.PLAYER_QUEST_JOURNAL_PREFIX.length()).trim();
        return id.isEmpty() ? null : id;
    }
}
