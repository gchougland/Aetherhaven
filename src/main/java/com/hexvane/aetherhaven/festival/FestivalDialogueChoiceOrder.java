package com.hexvane.aetherhaven.festival;

import com.hexvane.aetherhaven.dialogue.data.DialogueChoiceDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Keeps festival talk at the top of villager choice lists, ahead of everyday gift, follow, and goodbye rows.
 */
public final class FestivalDialogueChoiceOrder {
    private FestivalDialogueChoiceOrder() {}

    public static boolean isFestivalChoice(@Nullable DialogueChoiceDefinition choice) {
        if (choice == null) {
            return false;
        }
        String id = choice.getId();
        if (id != null) {
            String n = id.trim().toLowerCase(Locale.ROOT);
            if (n.startsWith("market_")
                || n.startsWith("wintertide_")
                || n.startsWith("snowball_")
                || n.startsWith("carnival_")
                || n.startsWith("hallows_")
                || n.startsWith("tree_climb")
                || n.startsWith("pig_race")
                || n.startsWith("new_life")
                || n.startsWith("lettuce_")) {
                return true;
            }
        }
        String text = choice.getText();
        return text != null && text.contains(".dialogue.festival.");
    }

    public static void insertAtTop(
        @Nonnull List<DialogueChoiceDefinition> choices,
        @Nonnull DialogueChoiceDefinition choice
    ) {
        int i = 0;
        while (i < choices.size() && isFestivalChoice(choices.get(i))) {
            i++;
        }
        choices.add(i, choice);
    }

    public static void promoteToTop(@Nonnull List<DialogueChoiceDefinition> choices) {
        List<DialogueChoiceDefinition> festival = new ArrayList<>();
        List<DialogueChoiceDefinition> rest = new ArrayList<>();
        for (DialogueChoiceDefinition choice : choices) {
            if (isFestivalChoice(choice)) {
                festival.add(choice);
            } else {
                rest.add(choice);
            }
        }
        choices.clear();
        choices.addAll(festival);
        choices.addAll(rest);
    }
}
