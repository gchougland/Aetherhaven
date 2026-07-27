package com.hexvane.aetherhaven.questboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.town.HiredGuardRecord;
import com.hexvane.aetherhaven.town.TownRecord;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("questboard")
final class TownRankCapacityTest {
    private static final QuestBoardCatalog CATALOG = QuestBoardCatalog.empty();

    @Test
    void maxFollowersScalesWithRankIndex() {
        assertEquals(1, TownRankCapacity.maxFollowers(0));
        assertEquals(2, TownRankCapacity.maxFollowers(1));
        assertEquals(8, TownRankCapacity.maxFollowers(7));
    }

    @Test
    void maxHiredGuardsScalesWithRankIndex() {
        assertEquals(2, TownRankCapacity.maxHiredGuards(0));
        assertEquals(4, TownRankCapacity.maxHiredGuards(1));
        assertEquals(16, TownRankCapacity.maxHiredGuards(7));
    }

    @Test
    void canHireGuardRespectsPersistedCount() {
        TownRecord town = new TownRecord();
        town.setQuestBoardRankXp(0);

        assertTrue(TownRankCapacity.canHireGuard(town, CATALOG));

        town.getHiredGuardRecords().add(new HiredGuardRecord("a", UUID.randomUUID(), "guard_knight", false));
        assertTrue(TownRankCapacity.canHireGuard(town, CATALOG));

        town.getHiredGuardRecords().add(new HiredGuardRecord("b", UUID.randomUUID(), "guard_knight", false));
        assertFalse(TownRankCapacity.canHireGuard(town, CATALOG));
    }
}
