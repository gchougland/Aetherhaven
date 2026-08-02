package com.hexvane.aetherhaven.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.quest.data.QuestDefinition;
import com.hexvane.aetherhaven.quest.data.QuestObjective;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("quest")
final class PlayerQuestProgressionServiceTest {
    private static final String QUEST_ID = AetherhavenConstants.QUEST_INTRO_AETHERHAVEN;
    private static final Gson GSON = new Gson();

    @Test
    void currentObjectiveFollowsAssetOrder() {
        QuestDefinition def = introDefinition();
        QuestCatalog catalog = catalog();
        PlayerQuestProgress progress = new PlayerQuestProgress();
        progress.addActiveQuest(QUEST_ID);
        progress.initQuestObjectiveProgress(QUEST_ID, def.trackableObjectiveIds());

        assertEquals("craft_desk", current(catalog, def, progress).id());
        assertTrue(progress.completeQuestObjective(QUEST_ID, "craft_desk"));
        assertEquals("craft_charter", current(catalog, def, progress).id());
        progress.completeQuestObjective(QUEST_ID, "craft_charter");
        assertEquals("place_charter", current(catalog, def, progress).id());
        progress.completeQuestObjective(QUEST_ID, "place_charter");
        assertNull(current(catalog, def, progress));
        assertTrue(PlayerQuestProgressionService.allObjectivesComplete(catalog, progress, QUEST_ID));
    }

    @Test
    void onItemCraftedAdvancesOnlyCurrentObjective() {
        QuestDefinition def = introDefinition();
        QuestCatalog catalog = catalog();
        PlayerQuestProgress progress = new PlayerQuestProgress();
        progress.addActiveQuest(QUEST_ID);
        progress.initQuestObjectiveProgress(QUEST_ID, def.trackableObjectiveIds());

        assertFalse(
            PlayerQuestProgressionService.onItemCrafted(catalog, progress, AetherhavenConstants.CHARTER_ITEM_ID)
        );
        assertTrue(
            PlayerQuestProgressionService.onItemCrafted(
                catalog,
                progress,
                "Aetherhaven_Town_Planning_Desk"
            )
        );
        assertEquals("craft_charter", current(catalog, def, progress).id());
    }

    @Test
    void onCharterPlacedCompletesFinalObjective() {
        QuestDefinition def = introDefinition();
        QuestCatalog catalog = catalog();
        PlayerQuestProgress progress = new PlayerQuestProgress();
        progress.addActiveQuest(QUEST_ID);
        progress.initQuestObjectiveProgress(QUEST_ID, def.trackableObjectiveIds());
        progress.completeQuestObjective(QUEST_ID, "craft_desk");
        progress.completeQuestObjective(QUEST_ID, "craft_charter");

        assertTrue(PlayerQuestProgressionService.onCharterPlaced(catalog, progress));
        assertTrue(PlayerQuestProgressionService.allObjectivesComplete(catalog, progress, QUEST_ID));
    }

    @Test
    void playerQuestRowPrefix() {
        assertEquals("player:q_intro_aetherhaven", PlayerQuestIds.playerRow(QUEST_ID));
        assertTrue(PlayerQuestIds.isPlayerQuestRow("player:q_intro_aetherhaven"));
        assertEquals(QUEST_ID, PlayerQuestIds.parsePlayerQuestId("player:q_intro_aetherhaven"));
    }

    private static QuestCatalog catalog() {
        return QuestCatalog.of(Map.of(QUEST_ID, introDefinition()));
    }

    private static QuestDefinition introDefinition() {
        return GSON.fromJson(
            """
            {
              "schemaVersion": 1,
              "id": "q_intro_aetherhaven",
              "category": "player",
              "objectives": [
                { "id": "craft_desk", "kind": "item_crafted", "itemId": "Aetherhaven_Town_Planning_Desk" },
                { "id": "craft_charter", "kind": "item_crafted", "itemId": "Aetherhaven_Charter" },
                { "id": "place_charter", "kind": "charter_placed" }
              ]
            }
            """,
            QuestDefinition.class
        );
    }

    private static QuestObjective current(
        QuestCatalog catalog,
        QuestDefinition def,
        PlayerQuestProgress progress
    ) {
        return PlayerQuestProgressionService.currentObjective(catalog, progress, def.idOrEmpty());
    }
}
