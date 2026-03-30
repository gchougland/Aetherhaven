package com.hexvane.aetherhaven.quest;

import com.hexvane.aetherhaven.AetherhavenConstants;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

/** Player-facing quest titles and journal text (not internal ids). */
public final class QuestCatalog {
    private static final Map<String, String> DISPLAY_NAMES = Map.ofEntries(
        Map.entry(AetherhavenConstants.QUEST_BUILD_INN, "Build the Inn"),
        Map.entry(AetherhavenConstants.QUEST_MERCHANT_STALL, "Build the Market Stall")
    );

    private static final Map<String, String> DESCRIPTIONS = Map.ofEntries(
        Map.entry(
            AetherhavenConstants.QUEST_BUILD_INN,
            "Lyren asked you to construct an inn so travelers have a place to rest."
        ),
        Map.entry(
            AetherhavenConstants.QUEST_MERCHANT_STALL,
            "Help the merchant open a stall, then speak to them at the stall to finish up."
        )
    );

    private static final Map<String, List<String>> OBJECTIVES = Map.ofEntries(
        Map.entry(
            AetherhavenConstants.QUEST_BUILD_INN,
            List.of(
                "Place the inn plot with your placement staff.",
                "Gather materials and complete construction at the plot sign."
            )
        ),
        Map.entry(
            AetherhavenConstants.QUEST_MERCHANT_STALL,
            List.of(
                "Receive the market stall plot token from the merchant.",
                "Place and build the market stall prefab.",
                "Talk to the merchant at the stall to complete the quest."
            )
        )
    );

    private QuestCatalog() {}

    @Nonnull
    public static String displayName(@Nonnull String questId) {
        String trimmed = questId.trim();
        return DISPLAY_NAMES.getOrDefault(trimmed, trimmed);
    }

    @Nonnull
    public static String description(@Nonnull String questId) {
        String trimmed = questId.trim();
        return DESCRIPTIONS.getOrDefault(trimmed, "No description for this quest yet.");
    }

    @Nonnull
    public static String objectivesText(@Nonnull String questId) {
        String trimmed = questId.trim();
        List<String> lines = OBJECTIVES.get(trimmed);
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(i + 1).append(". ").append(lines.get(i));
        }
        return sb.toString();
    }

    /** Full detail block for the quest journal (description + numbered objectives). */
    @Nonnull
    public static String detailBody(@Nonnull String questId) {
        String d = description(questId);
        String o = objectivesText(questId);
        if (o.isEmpty()) {
            return d;
        }
        return d + "\n\nObjectives:\n" + o;
    }
}
