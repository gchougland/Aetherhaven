package com.hexvane.aetherhaven.dialogue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.construction.MaterialRequirement;
import com.hexvane.aetherhaven.dialogue.data.DialogueChoiceDefinition;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
final class DialogueChoiceItemRequirementsTest {
    private final Gson gson = new Gson();

    @Test
    void resolvesExplicitItemRequirements() {
        DialogueChoiceDefinition choice =
            gson.fromJson(
                """
                {
                  "text": "Turn in",
                  "itemRequirements": [
                    {"itemId": "Food_Pie_Apple", "count": 2}
                  ]
                }
                """,
                DialogueChoiceDefinition.class
            );
        assertEquals(1, DialogueChoiceItemRequirements.resolve(choice, null, null, null, null).size());
        assertEquals(
            "Food_Pie_Apple",
            DialogueChoiceItemRequirements.resolve(choice, null, null, null, null).get(0).getItemId()
        );
    }

    @Test
    void detectsTouristMoveInChoiceFromIconAndAction() {
        DialogueChoiceDefinition byIcon = new DialogueChoiceDefinition();
        byIcon.setIcon("move_in_item");
        assertTrue(DialogueChoiceItemRequirements.isTouristMoveInChoice(byIcon));

        DialogueChoiceDefinition byAction =
            gson.fromJson(
                """
                {"text":"Give","actions":[{"type":"deliver_tourist_move_in_items"}]}
                """,
                DialogueChoiceDefinition.class
            );
        assertTrue(DialogueChoiceItemRequirements.isTouristMoveInChoice(byAction));
    }

    @Test
    void normalizeListStripsInvalidRows() {
        var list =
            DialogueChoiceItemRequirements.resolve(
                java.util.List.of(
                    MaterialRequirement.ofItem("Rock_Stone", 1),
                    MaterialRequirement.ofResourceType("Wood", 3)
                )
            );
        assertEquals(1, list.size());
    }
}
