package com.hexvane.aetherhaven.quest;

import static com.hexvane.aetherhaven.quest.WorkplaceQuestFixture.QUEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.quest.data.QuestDefinition;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.ui.PlayerTownJournalState;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The quest tracker pin of a quest that asks for one. The town has the quest; each member has their own pins. So every
 * member gets the pin once, a member who takes it off is left alone, and a full tracker makes room.
 */
@Tag("town")
class QuestAutoPinTest {
    private final QuestDefinition quest = WorkplaceQuestFixture.shipped();
    private final QuestCatalog quests = WorkplaceQuestFixture.catalog(quest, plain("q_build_inn"), plain("q_farm_plot"), plain("q_bard"));

    private static QuestDefinition plain(String id) {
        return new Gson().fromJson("{\"schemaVersion\": 1, \"id\": \"" + id + "\"}", QuestDefinition.class);
    }

    private TownRecord townWithQuest() {
        var town = WorkplaceQuestFixture.townFor(TownsfolkWorkType.SMELTING);
        town.addActiveQuest(QUEST);
        return town;
    }

    @Test
    void theQuestIsPinnedWithThePlayerDoingNothing() {
        var town = townWithQuest();
        var player = new PlayerTownJournalState();
        assertFalse(player.isQuestPinned(QUEST));
        assertTrue(QuestAutoPin.wants(player, town, quests));
        var pinned = QuestAutoPin.apply(player, town, quests, id -> false);
        assertEquals(List.of(new QuestAutoPin.Pinned(QUEST, true, null)), pinned);
        assertEquals(List.of(QUEST), player.getPinnedQuestIds());
        assertFalse(QuestAutoPin.wants(player, town, quests));
    }

    @Test
    void everyMemberOfTheTownGetsTheirOwnPinWheneverTheyArrive() {
        var town = townWithQuest();
        var founder = new PlayerTownJournalState();
        var member = new PlayerTownJournalState();
        assertEquals(1, QuestAutoPin.apply(founder, town, quests, id -> false).size());
        // The second member was away when the quest started. Steps were done meanwhile. They still get the pin.
        town.completeQuestObjective(QUEST, "unlock");
        assertEquals(1, QuestAutoPin.apply(member, town, quests, id -> false).size());
        assertTrue(founder.isQuestPinned(QUEST));
        assertTrue(member.isQuestPinned(QUEST));
    }

    @Test
    void aPlayerWhoUnpinsItIsNotPinnedAgain() {
        var town = townWithQuest();
        var player = new PlayerTownJournalState();
        QuestAutoPin.apply(player, town, quests, id -> false);
        assertTrue(player.unpinQuest(QUEST));
        assertTrue(QuestAutoPin.apply(player, town, quests, id -> false).isEmpty(), "Their choice stands");
        assertFalse(player.isQuestPinned(QUEST));
        // The mark is part of what is saved with the player.
        var copy = (PlayerTownJournalState) player.clone();
        assertFalse(QuestAutoPin.wants(copy, town, quests));
        assertTrue(copy.wasQuestAutoPinned(QuestAutoPin.key(town.getTownId(), QUEST)));
    }

    @Test
    void aFullTrackerGivesUpAPinThatIsNotShowingBeforeOneThatIs() {
        var town = townWithQuest();
        var player = new PlayerTownJournalState();
        assertTrue(player.pinQuest("q_build_inn"));
        assertTrue(player.pinQuest("q_farm_plot"));
        assertTrue(player.pinQuest("q_bard"));
        assertFalse(player.pinQuest(QUEST), "Three pins is the limit for a pin made by hand");
        // The farm plot quest was finished long ago: its pin is left over and draws nothing.
        var showing = Set.of("q_build_inn", "q_bard");
        var pinned = QuestAutoPin.apply(player, town, quests, showing::contains);
        assertEquals(List.of(new QuestAutoPin.Pinned(QUEST, true, "q_farm_plot")), pinned);
        assertEquals(List.of("q_build_inn", QUEST, "q_bard"), player.getPinnedQuestIds(), "The others keep their rows");
    }

    @Test
    void aFullTrackerOfLiveQuestsGivesUpItsLastRowAndSaysWhich() {
        var town = townWithQuest();
        var player = new PlayerTownJournalState();
        player.pinQuest("q_build_inn");
        player.pinQuest("q_farm_plot");
        player.pinQuest("q_bard");
        var pinned = QuestAutoPin.apply(player, town, quests, id -> true);
        assertEquals("q_bard", pinned.getFirst().gaveUp(), "The player is told which pin made room");
        assertEquals(List.of("q_build_inn", "q_farm_plot", QUEST), player.getPinnedQuestIds());
        assertEquals(PlayerTownJournalState.MAX_PINNED_QUESTS, player.getPinnedQuestIds().size());
    }

    @Test
    void aQuestThePlayerPinnedThemselvesIsOnlyMarked() {
        var town = townWithQuest();
        var player = new PlayerTownJournalState();
        player.pinQuest(QUEST);
        var pinned = QuestAutoPin.apply(player, town, quests, id -> true);
        assertEquals(List.of(new QuestAutoPin.Pinned(QUEST, false, null)), pinned);
        assertEquals(List.of(QUEST), player.getPinnedQuestIds());
    }

    @Test
    void questsThatDoNotAskForAPinAreLeftToThePlayer() {
        var town = WorkplaceQuestFixture.emptyTown();
        town.addActiveQuest("q_build_inn");
        var player = new PlayerTownJournalState();
        assertFalse(QuestAutoPin.wants(player, town, quests));
        assertTrue(QuestAutoPin.apply(player, town, quests, id -> false).isEmpty());
        assertTrue(player.getPinnedQuestIds().isEmpty());
    }

    @Test
    void theSameQuestInASecondTownIsPinnedForThatTownToo() {
        var first = townWithQuest();
        var second = townWithQuest();
        var player = new PlayerTownJournalState();
        QuestAutoPin.apply(player, first, quests, id -> false);
        // The first town finishes it and the journal clears the pin, as it does for any finished quest.
        first.completeQuest(QUEST);
        player.retainPinnedQuests(Set.of());
        assertFalse(player.isQuestPinned(QUEST));
        assertEquals(1, QuestAutoPin.apply(player, second, quests, id -> false).size());
        assertTrue(player.isQuestPinned(QUEST));
        assertNull(player.pinQuestMakingRoom(QUEST, id -> true), "Pinning what is pinned changes nothing");
        assertNull(player.pinQuestMakingRoom("  ", id -> true));
    }

    @Test
    void theMarksAPlayerCarriesAreBounded() {
        var player = new PlayerTownJournalState();
        for (int i = 0; i < 200; i++) assertTrue(player.markQuestAutoPinned("town" + i + "|quest"));
        assertFalse(player.wasQuestAutoPinned("town0|quest"), "The oldest marks go");
        assertTrue(player.wasQuestAutoPinned("town199|quest"));
        assertFalse(player.markQuestAutoPinned("town199|quest"));
    }
}
