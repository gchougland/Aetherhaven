package com.hexvane.aetherhaven.quest;

import com.hexvane.aetherhaven.AetherhavenConstants;
import java.util.Map;
import javax.annotation.Nonnull;

/** Player-facing quest titles (not internal ids). */
public final class QuestCatalog {
    private static final Map<String, String> DISPLAY_NAMES =
        Map.of(AetherhavenConstants.QUEST_BUILD_INN, "Build the Inn");

    private QuestCatalog() {}

    @Nonnull
    public static String displayName(@Nonnull String questId) {
        String trimmed = questId.trim();
        return DISPLAY_NAMES.getOrDefault(trimmed, trimmed);
    }
}
