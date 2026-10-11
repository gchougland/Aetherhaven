package com.hexvane.aetherhaven.quest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.questboard.QuestBoardSlotRecord;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuestMineProgressTest {
    @Test
    void oreInAnyRockCountsButOtherBlocksDoNot() {
        var iron = List.of("Ore_Iron");
        assertTrue(QuestMineProgressSystem.matches("Ore_Iron_Stone", iron));
        assertTrue(QuestMineProgressSystem.matches("Ore_Iron_Basalt_Cracked", iron));
        assertFalse(QuestMineProgressSystem.matches("Ore_Gold_Stone", iron));
        assertFalse(QuestMineProgressSystem.matches("Rock_Stone", iron));
        assertFalse(QuestMineProgressSystem.matches(null, iron));
        assertFalse(QuestMineProgressSystem.matches("Ore_Iron_Stone", List.of()));
    }

    @Test
    void aMiningContractIsAHuntWithAnOreTargetAndSurvivesSaving() {
        var slot = new QuestBoardSlotRecord();
        slot.setQuestType("hunt");
        assertFalse(slot.isMineQuest());
        slot.setMineBlockIdPrefixesAny(List.of("Ore_Iron"));
        var saved = new Gson().fromJson(new Gson().toJson(slot), QuestBoardSlotRecord.class);
        assertTrue(saved.isHuntQuest());
        assertTrue(saved.isMineQuest());
    }

    @Test
    void theMinerAndBlacksmithPostMiningContractsForRealOre() throws Exception {
        var board = JsonParser.parseString(Files.readString(Path.of("subplugin-assets/Quests/Server/Aetherhaven/quest_board.json"))).getAsJsonObject();
        int mining = 0;
        for (String giver : List.of("Aetherhaven_Miner", "Aetherhaven_Blacksmith")) {
            for (var entry : board.getAsJsonObject("villagers").getAsJsonObject(giver).getAsJsonArray("huntEntries")) {
                var set = entry.getAsJsonObject().getAsJsonArray("killSets").get(0).getAsJsonObject();
                assertTrue(set.has("blockIdPrefixesAny"), entry.getAsJsonObject().get("id") + " must be a mining contract");
                for (var prefix : set.getAsJsonArray("blockIdPrefixesAny")) assertTrue(prefix.getAsString().startsWith("Ore_"));
                mining++;
            }
        }
        assertTrue(mining >= 7);
    }
}
