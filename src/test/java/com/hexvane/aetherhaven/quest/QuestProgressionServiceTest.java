package com.hexvane.aetherhaven.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.quest.data.QuestDefinition;
import com.hexvane.aetherhaven.quest.data.QuestObjective;
import com.hexvane.aetherhaven.town.TownRecord;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
final class QuestProgressionServiceTest {
    private static final String QUEST_ID = "ordered_test";
    private final Gson gson = new Gson();

    @Test
    void currentObjectiveFollowsAssetOrderAndPersistedProgress() {
        QuestDefinition def = definition();
        TownRecord town = new TownRecord();
        town.initQuestObjectiveProgress(QUEST_ID, def.trackableObjectiveIds());

        assertEquals("token", current(def, town).id());
        assertTrue(town.completeQuestObjective(QUEST_ID, "token"));
        assertFalse(town.completeQuestObjective(QUEST_ID, "token"), "completion must be idempotent");
        assertEquals("build", current(def, town).id());

        town.completeQuestObjective(QUEST_ID, "build");
        assertEquals("assign", current(def, town).id());
        town.completeQuestObjective(QUEST_ID, "assign");
        assertEquals("talk", current(def, town).id());
        assertFalse(QuestProgressionService.allObjectivesComplete(def, town, QUEST_ID));

        town.completeQuestObjective(QUEST_ID, "talk");
        assertNull(QuestProgressionService.currentObjective(def, town, QUEST_ID));
        assertTrue(QuestProgressionService.allObjectivesComplete(def, town, QUEST_ID));
    }

    @Test
    void killObjectiveAdvancesOnlyWhenRequiredCountIsReached() {
        QuestDefinition def = gson.fromJson(
            """
            {
              "schemaVersion": 1,
              "id": "ordered_test",
              "objectives": [
                {"id": "kills", "kind": "entity_kills", "text": "Defeat threats.", "killCount": 2,
                 "entityTagsAny": ["Hostile"]},
                {"id": "talk", "kind": "dialogue_turn_in", "text": "Return."}
              ]
            }
            """,
            QuestDefinition.class
        );
        TownRecord town = new TownRecord();
        town.initQuestObjectiveProgress(QUEST_ID, def.trackableObjectiveIds());
        town.initQuestKillProgress(QUEST_ID, def.entityKillObjectiveIds());

        assertEquals("kills", current(def, town).id());
        town.setQuestKillCount(QUEST_ID, "kills", 1);
        assertEquals("kills", current(def, town).id());
        town.setQuestKillCount(QUEST_ID, "kills", 2);
        assertEquals("talk", current(def, town).id());
    }

    @Test
    void progressSnapshotIsDefensiveAndMissingOldSaveStateIsSafe() {
        TownRecord town = new TownRecord();
        assertFalse(town.isQuestObjectiveComplete(QUEST_ID, "build"));
        assertTrue(town.getQuestObjectiveProgressSnapshot(QUEST_ID).isEmpty());

        town.completeQuestObjective(QUEST_ID, "build");
        var snapshot = town.getQuestObjectiveProgressSnapshot(QUEST_ID);
        assertEquals(Boolean.TRUE, snapshot.get("build"));
        org.junit.jupiter.api.Assertions.assertThrows(
            UnsupportedOperationException.class,
            () -> snapshot.put("talk", Boolean.TRUE)
        );
    }

    @Test
    void journalChecklistIncludesEveryStepAndCompletionMarkers() {
        QuestDefinition def = definition();
        QuestCatalog catalog = QuestCatalog.of(Map.of(QUEST_ID, def));
        TownRecord town = new TownRecord();
        town.initQuestObjectiveProgress(QUEST_ID, def.trackableObjectiveIds());
        town.completeQuestObjective(QUEST_ID, "token");

        assertTrue(catalog.hasObjectives(QUEST_ID));
        assertEquals(4, def.objectivesOrEmpty().size());
        assertEquals("[x] ", QuestCatalog.objectiveStatusMarker(
            QuestProgressionService.isObjectiveComplete(town, QUEST_ID, def.objectivesOrEmpty().get(0))
        ));
        assertEquals("[ ] ", QuestCatalog.objectiveStatusMarker(
            QuestProgressionService.isObjectiveComplete(town, QUEST_ID, def.objectivesOrEmpty().get(1))
        ));
    }

    private QuestDefinition definition() {
        return gson.fromJson(
            """
            {
              "schemaVersion": 1,
              "id": "ordered_test",
              "objectives": [
                {"id": "token", "kind": "plot_token_received", "text": "Take token."},
                {"id": "build", "kind": "construction_built", "constructionId": "plot_house", "text": "Build."},
                {"id": "assign", "kind": "assign_house_resident", "text": "Assign."},
                {"id": "talk", "kind": "dialogue_turn_in", "text": "Return."}
              ]
            }
            """,
            QuestDefinition.class
        );
    }

    private static QuestObjective current(QuestDefinition def, TownRecord town) {
        QuestObjective objective = QuestProgressionService.currentObjective(def, town, QUEST_ID);
        if (objective == null) {
            throw new AssertionError("Expected a current objective");
        }
        return objective;
    }
}
