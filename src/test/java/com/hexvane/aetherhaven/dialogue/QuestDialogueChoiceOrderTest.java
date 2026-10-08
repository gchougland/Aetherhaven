package com.hexvane.aetherhaven.dialogue;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.dialogue.data.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town") class QuestDialogueChoiceOrderTest {
    @Test void implicitGoodbyeStaysBelowCompletedChatterWithoutMovingServicesThatClose(){
        var choices=new ArrayList<>(List.of(new Gson().fromJson("{\"text\":\"goodbye\"}",DialogueChoiceDefinition.class),
            new Gson().fromJson("{\"text\":\"completed\",\"next\":\"park_done\",\"condition\":{\"type\":\"town_quest_completed\",\"questId\":\"park\"}}",DialogueChoiceDefinition.class),
            new Gson().fromJson("{\"text\":\"follow\",\"actions\":[{\"type\":\"follow_player\"},{\"type\":\"close\"}]}",DialogueChoiceDefinition.class)));
        QuestDialogueChoiceOrder.promote(choices,null);
        assertEquals(List.of("follow","completed","goodbye"),choices.stream().map(DialogueChoiceDefinition::getText).toList());
    }
    @Test void questBranchesPrecedeServicesWithoutFollowingBackLinksOrReorderingPeers(){
        var tree=new Gson().fromJson("""
            {"entry":"main_hub","nodes":{
              "main_hub":{"choices":[{"text":"profile","actions":[{"type":"open_core_mastery"}]},{"text":"shelter","next":"shelter"},{"text":"completed","condition":{"type":"town_quest_completed","questId":"q_park"},"next":"park_done"},{"text":"shop","next":"shop","condition":{"type":"town_quest_completed","questId":"q_shop"},"actions":[{"type":"open_shop"}]},{"text":"progress","next":"build_remind"},{"text":"goodbye","actions":[{"type":"close"}]}]},
              "shelter":{"choices":[{"actions":[{"type":"start_quest","id":"q_shelter"}]},{"next":"main_hub"}]},
              "shop":{"choices":[{"next":"shop"},{"next":"main_hub"}]}
            }}
            """,DialogueTreeDefinition.class);
        var choices=new ArrayList<>(tree.getNode("main_hub").getChoices());QuestDialogueChoiceOrder.promote(choices,tree);
        assertEquals(List.of("shelter","progress","profile","shop","completed","goodbye"),choices.stream().map(DialogueChoiceDefinition::getText).toList());
    }
}
