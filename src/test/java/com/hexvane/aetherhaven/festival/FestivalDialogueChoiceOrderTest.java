package com.hexvane.aetherhaven.festival;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.dialogue.data.DialogueChoiceDefinition;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
final class FestivalDialogueChoiceOrderTest {
    @Test
    void marketAndWintertideChoicesSitAboveEverydayTalk() {
        List<DialogueChoiceDefinition> choices = new ArrayList<>();
        choices.add(choice("gift", "aetherhaven_dialogue_gift.aetherhaven.dialogue.gift.offer"));
        choices.add(choice("goodbye", "aetherhaven_dialogue_elder.aetherhaven.dialogue.aetherhaven_elder.main_hub.c_goodbye"));

        FestivalDialogueChoiceOrder.insertAtTop(
            choices,
            choice("market_how", "aetherhaven_dialogue_festival_market.aetherhaven.dialogue.festival.market.choice.how")
        );
        FestivalDialogueChoiceOrder.insertAtTop(
            choices,
            choice("market_shop", "aetherhaven_dialogue_festival_market.aetherhaven.dialogue.festival.market.choice.shop")
        );
        FestivalDialogueChoiceOrder.insertAtTop(
            choices,
            choice(
                "wintertide_gift",
                "aetherhaven_dialogue_festival_wintertide.aetherhaven.dialogue.festival.wintertide.choice.give"
            )
        );

        assertEquals(
            List.of("market_how", "market_shop", "wintertide_gift", "gift", "goodbye"),
            ids(choices)
        );
    }

    @Test
    void promoteMovesBuriedFestivalChoicesToTheTopWithoutReorderingThem() {
        List<DialogueChoiceDefinition> choices = new ArrayList<>();
        choices.add(choice("gift", "aetherhaven_dialogue_gift.aetherhaven.dialogue.gift.offer"));
        choices.add(
            choice("market_fill", "aetherhaven_dialogue_festival_market.aetherhaven.dialogue.festival.market.choice.fill")
        );
        choices.add(
            choice("market_start", "aetherhaven_dialogue_festival_market.aetherhaven.dialogue.festival.market.choice.start")
        );
        choices.add(choice("goodbye", "aetherhaven_dialogue_elder.aetherhaven.dialogue.aetherhaven_elder.main_hub.c_goodbye"));

        FestivalDialogueChoiceOrder.promoteToTop(choices);

        assertEquals(List.of("market_fill", "market_start", "gift", "goodbye"), ids(choices));
    }

    @Test
    void everydayGiftAndMerchantAboutAreNotFestivalChoices() {
        assertFalse(
            FestivalDialogueChoiceOrder.isFestivalChoice(
                choice("gift", "aetherhaven_dialogue_gift.aetherhaven.dialogue.gift.offer")
            )
        );
        assertFalse(
            FestivalDialogueChoiceOrder.isFestivalChoice(
                choice(
                    "about",
                    "aetherhaven_dialogue_festival_snowball_merchant.aetherhaven.dialogue.festival_snowball_merchant.main_hub.c_about"
                )
            )
        );
        assertTrue(
            FestivalDialogueChoiceOrder.isFestivalChoice(
                choice(
                    "market_shop",
                    "aetherhaven_dialogue_festival_market.aetherhaven.dialogue.festival.market.choice.shop"
                )
            )
        );
    }

    private static DialogueChoiceDefinition choice(String id, String text) {
        DialogueChoiceDefinition choice = new DialogueChoiceDefinition();
        choice.setId(id);
        choice.setText(text);
        return choice;
    }

    private static List<String> ids(List<DialogueChoiceDefinition> choices) {
        List<String> ids = new ArrayList<>();
        for (DialogueChoiceDefinition choice : choices) {
            ids.add(choice.getId());
        }
        return ids;
    }
}
