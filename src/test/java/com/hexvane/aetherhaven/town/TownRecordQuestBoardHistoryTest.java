package com.hexvane.aetherhaven.town;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
class TownRecordQuestBoardHistoryTest {

    @Test
    void recordsAndQueriesCompleteWithinDays() {
        TownRecord town = new TownRecord();
        UUID player = UUID.randomUUID();
        town.recordQuestBoardComplete(player, 100L, "Aetherhaven_Elder_Lyren", "foundation_stones");
        assertTrue(town.wasQuestBoardCompleteWithin(player, 100L, 3));
        assertTrue(town.wasQuestBoardCompleteWithin(player, 102L, 3));
        assertFalse(town.wasQuestBoardCompleteWithin(player, 103L, 3));
    }

    @Test
    void completeHistoryFiltersByGiverAndEntry() {
        TownRecord town = new TownRecord();
        UUID player = UUID.randomUUID();
        town.recordQuestBoardComplete(player, 100L, "Aetherhaven_Elder_Lyren", "foundation_stones");
        assertTrue(
            town.wasQuestBoardCompleteWithin(player, 100L, 3, "Aetherhaven_Elder_Lyren", "foundation_stones")
        );
        assertFalse(
            town.wasQuestBoardCompleteWithin(player, 100L, 3, "Aetherhaven_Miner", "foundation_stones")
        );
        assertFalse(
            town.wasQuestBoardCompleteWithin(player, 100L, 3, "Aetherhaven_Elder_Lyren", "village_timber")
        );
    }

    @Test
    void recordsAndQueriesFailWithinDays() {
        TownRecord town = new TownRecord();
        UUID player = UUID.randomUUID();
        town.recordQuestBoardFail(player, 50L);
        assertTrue(town.wasQuestBoardFailWithin(player, 52L, 3));
        assertFalse(town.wasQuestBoardFailWithin(player, 53L, 3));
        assertFalse(town.wasQuestBoardCompleteWithin(player, 52L, 3));
    }

    @Test
    void missingHistoryReturnsFalse() {
        TownRecord town = new TownRecord();
        UUID player = UUID.randomUUID();
        assertFalse(town.wasQuestBoardCompleteWithin(player, 10L, 3));
        assertFalse(town.wasQuestBoardFailWithin(player, 10L, 3));
    }
}
