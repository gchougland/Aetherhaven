package com.hexvane.aetherhaven.worldnpc;

import com.hexvane.aetherhaven.AetherhavenConstants;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class WorldQuestIds {
    private WorldQuestIds() {}

    @Nonnull
    public static String worldRow(@Nonnull String questId) {
        return AetherhavenConstants.WORLD_QUEST_JOURNAL_PREFIX + questId.trim();
    }

    @Nonnull
    public static String boardRow(@Nonnull String instanceId) {
        return AetherhavenConstants.WORLD_BOARD_JOURNAL_PREFIX + instanceId.trim();
    }

    public static boolean isWorldQuestRow(@Nullable String rowId) {
        return rowId != null && rowId.startsWith(AetherhavenConstants.WORLD_QUEST_JOURNAL_PREFIX);
    }

    public static boolean isWorldBoardRow(@Nullable String rowId) {
        return rowId != null && rowId.startsWith(AetherhavenConstants.WORLD_BOARD_JOURNAL_PREFIX);
    }

    @Nullable
    public static String parseWorldQuestId(@Nullable String rowId) {
        if (!isWorldQuestRow(rowId)) {
            return null;
        }
        String id = rowId.substring(AetherhavenConstants.WORLD_QUEST_JOURNAL_PREFIX.length()).trim();
        return id.isEmpty() ? null : id;
    }

    @Nullable
    public static String parseWorldBoardInstanceId(@Nullable String rowId) {
        if (!isWorldBoardRow(rowId)) {
            return null;
        }
        String id = rowId.substring(AetherhavenConstants.WORLD_BOARD_JOURNAL_PREFIX.length()).trim();
        return id.isEmpty() ? null : id;
    }
}
