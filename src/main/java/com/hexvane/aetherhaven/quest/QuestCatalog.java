package com.hexvane.aetherhaven.quest;

import com.hexvane.aetherhaven.AetherhavenConstants;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

/** Player-facing quest titles and journal text (not internal ids). */
public final class QuestCatalog {
    private static final Map<String, String> DISPLAY_NAMES = Map.ofEntries(
        Map.entry(AetherhavenConstants.QUEST_BUILD_INN, "Build the Inn"),
        Map.entry(AetherhavenConstants.QUEST_MERCHANT_STALL, "Build the Market Stall"),
        Map.entry(AetherhavenConstants.QUEST_FARM_PLOT, "Irienne's Farm Plot"),
        Map.entry(AetherhavenConstants.QUEST_HOUSE_ELDER, "Lyren's Home"),
        Map.entry(AetherhavenConstants.QUEST_HOUSE_INNKEEPER, "Corin's Home"),
        Map.entry(AetherhavenConstants.QUEST_HOUSE_MERCHANT, "Vex's Home"),
        Map.entry(AetherhavenConstants.QUEST_HOUSE_FARMER, "Irienne's Home"),
        Map.entry(AetherhavenConstants.QUEST_HOUSE_BLACKSMITH, "Garren's Home")
    );

    private static final Map<String, String> DESCRIPTIONS = Map.ofEntries(
        Map.entry(
            AetherhavenConstants.QUEST_BUILD_INN,
            "Lyren asked you to construct an inn so travelers have a place to rest."
        ),
        Map.entry(
            AetherhavenConstants.QUEST_MERCHANT_STALL,
            "Help the merchant open a stall, then speak to them at the stall to finish up."
        ),
        Map.entry(
            AetherhavenConstants.QUEST_FARM_PLOT,
            "Help Irienne set up a farm plot in town so she can move in and work the soil."
        ),
        Map.entry(
            AetherhavenConstants.QUEST_HOUSE_ELDER,
            "Lyren asked for a proper home in town. The same house footprint can serve anyone. She finishes the quest when you assign her at the management block."
        ),
        Map.entry(
            AetherhavenConstants.QUEST_HOUSE_INNKEEPER,
            "Corin wants a roof of his own beyond the inn's ledger. Build the house, then assign him as resident on its management block."
        ),
        Map.entry(
            AetherhavenConstants.QUEST_HOUSE_MERCHANT,
            "Vex wants a place to stow their boots after the stall closes. Build the house and register them as resident there."
        ),
        Map.entry(
            AetherhavenConstants.QUEST_HOUSE_FARMER,
            "Irienne would like a bed that isn't borrowed from the inn hall. Build the house and assign her as resident."
        ),
        Map.entry(
            AetherhavenConstants.QUEST_HOUSE_BLACKSMITH,
            "Garren wants a cot that doesn't smell like another traveler's pack. Build the house and assign him as resident."
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
        ),
        Map.entry(
            AetherhavenConstants.QUEST_FARM_PLOT,
            List.of(
                "Receive the farm plot token from Irienne.",
                "Place and build the farm plot prefab.",
                "Talk to Irienne at the farm to complete the quest."
            )
        ),
        Map.entry(
            AetherhavenConstants.QUEST_HOUSE_ELDER,
            List.of(
                "Receive the house plot token from Lyren.",
                "Place and build the house prefab.",
                "Assign Lyren as resident on the house management block."
            )
        ),
        Map.entry(
            AetherhavenConstants.QUEST_HOUSE_INNKEEPER,
            List.of(
                "Receive the house plot token from Corin.",
                "Place and build the house prefab.",
                "Assign Corin as resident on the house management block."
            )
        ),
        Map.entry(
            AetherhavenConstants.QUEST_HOUSE_MERCHANT,
            List.of(
                "Receive the house plot token from Vex.",
                "Place and build the house prefab.",
                "Assign Vex as resident on the house management block."
            )
        ),
        Map.entry(
            AetherhavenConstants.QUEST_HOUSE_FARMER,
            List.of(
                "Receive the house plot token from Irienne.",
                "Place and build the house prefab.",
                "Assign Irienne as resident on the house management block."
            )
        ),
        Map.entry(
            AetherhavenConstants.QUEST_HOUSE_BLACKSMITH,
            List.of(
                "Receive the house plot token from Garren.",
                "Place and build the house prefab.",
                "Assign Garren as resident on the house management block."
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
