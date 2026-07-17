package com.hexvane.aetherhaven.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
final class PlayerTownJournalStateHudTest {
    @Test
    void pinsAtMostThreeUniqueQuestsInOrder() {
        PlayerTownJournalState state = new PlayerTownJournalState();

        assertTrue(state.pinQuest("story:first"));
        assertTrue(state.pinQuest("board:second"));
        assertTrue(state.pinQuest("story:third"));
        assertFalse(state.pinQuest("story:first"));
        assertFalse(state.pinQuest("story:fourth"));
        assertEquals(
            java.util.List.of("story:first", "board:second", "story:third"),
            state.getPinnedQuestIds()
        );
    }

    @Test
    void removesOnlyPinsThatAreNoLongerActive() {
        PlayerTownJournalState state = new PlayerTownJournalState();
        state.pinQuest("one");
        state.pinQuest("two");
        state.pinQuest("three");

        assertTrue(state.retainPinnedQuests(Set.of("one", "three")));
        assertEquals(java.util.List.of("one", "three"), state.getPinnedQuestIds());
    }

    @Test
    void completedQuestsDoNotConsumePinCapacity() {
        PlayerTownJournalState state = new PlayerTownJournalState();
        state.pinQuest("completed");
        state.pinQuest("active");
        state.pinQuest("also_completed");

        assertEquals(1, state.activePinnedQuestCount(Set.of("active", "new_quest")));
        state.retainPinnedQuests(Set.of("active", "new_quest"));
        assertTrue(state.pinQuest("new_quest"));
    }

    @Test
    void resetRestoresSafeDefaultPanels() {
        PlayerTownJournalState state = new PlayerTownJournalState();
        state.setHudPreferences(false, false, false, false, "CUSTOM", 9000, -4, "BOTTOM_LEFT", 50, 60);

        state.resetHudPreferences();

        assertTrue(state.isHudShowTime());
        assertTrue(state.isHudShowDate());
        assertTrue(state.isHudShowGold());
        assertTrue(state.isHudShowQuests());
        assertEquals("TOP_RIGHT", state.getHudStatusPlacement());
        assertEquals(0, state.getHudStatusX());
        assertEquals(0, state.getHudStatusY());
        assertEquals("TOP_RIGHT", state.getHudQuestPlacement());
        assertEquals(0, state.getHudQuestX());
        assertEquals(164, state.getHudQuestY());
        assertEquals(0f, state.getHudBackgroundOpacity());
    }

    @Test
    void backgroundOpacityIsClamped() {
        PlayerTownJournalState state = new PlayerTownJournalState();

        state.setHudBackgroundOpacity(0.45f);
        assertEquals(0.45f, state.getHudBackgroundOpacity());
        state.setHudBackgroundOpacity(2f);
        assertEquals(1f, state.getHudBackgroundOpacity());
        state.setHudBackgroundOpacity(-1f);
        assertEquals(0f, state.getHudBackgroundOpacity());
    }
}
