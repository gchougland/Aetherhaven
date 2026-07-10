package com.hexvane.aetherhaven.questboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.questboard.data.QuestBoardDefinitionJson;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("crossmod")
class QuestBoardMergerTest {

    private final Gson gson = new Gson();

    @Test
    void mergesVillagerEntriesById() {
        QuestBoardDefinitionJson base = gson.fromJson(
            """
            {
              "schemaVersion": 1,
              "slotCount": 3,
              "villagers": {
                "Aetherhaven_Merchant": {
                  "fetchEntries": [
                    {
                      "id": "bread",
                      "weight": 5,
                      "daysLimit": 2,
                      "titleLangKey": "bread.title"
                    }
                  ]
                }
              }
            }
            """,
            QuestBoardDefinitionJson.class
        );
        QuestBoardDefinitionJson overlay = gson.fromJson(
            """
            {
              "schemaVersion": 1,
              "villagers": {
                "Aetherhaven_Merchant": {
                  "fetchEntries": [
                    {
                      "id": "bread",
                      "weight": 9,
                      "daysLimit": 3,
                      "titleLangKey": "bread.updated"
                    },
                    {
                      "id": "fish",
                      "weight": 4,
                      "daysLimit": 2,
                      "titleLangKey": "fish.title"
                    }
                  ]
                },
                "Aetherhaven_Miner": {
                  "huntEntries": [
                    {
                      "id": "wolves",
                      "weight": 3,
                      "daysLimit": 2,
                      "titleLangKey": "wolves.title"
                    }
                  ]
                }
              }
            }
            """,
            QuestBoardDefinitionJson.class
        );

        QuestBoardDefinitionJson merged = QuestBoardMerger.merge(base, overlay);
        assertEquals(3, merged.slotCount());
        assertNotNull(merged.villagersOrEmpty().get("Aetherhaven_Merchant"));
        assertEquals(2, merged.villagersOrEmpty().get("Aetherhaven_Merchant").fetchEntriesOrEmpty().size());
        assertEquals("bread.updated", merged.villagersOrEmpty().get("Aetherhaven_Merchant").fetchEntriesOrEmpty().get(0).titleLangKey());
        assertEquals(9, merged.villagersOrEmpty().get("Aetherhaven_Merchant").fetchEntriesOrEmpty().get(0).weight());
        assertEquals("fish", merged.villagersOrEmpty().get("Aetherhaven_Merchant").fetchEntriesOrEmpty().get(1).id());
        assertEquals(1, merged.villagersOrEmpty().get("Aetherhaven_Miner").huntEntriesOrEmpty().size());
    }
}
