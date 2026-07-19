package com.hexvane.aetherhaven.worldnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("worldnpc")
final class WorldNpcPlayerProgressTest {
    @Test
    void tracksActiveAndCompletedQuests() {
        WorldNpcPlayerProgress progress = new WorldNpcPlayerProgress();
        progress.setPlayerUuid(UUID.randomUUID());
        progress.addActiveQuest("q_world_demo");
        assertTrue(progress.hasQuestActive("q_world_demo"));
        progress.markQuestCompleted("q_world_demo");
        assertFalse(progress.hasQuestActive("q_world_demo"));
        assertTrue(progress.hasQuestCompleted("q_world_demo"));
    }

    @Test
    void findsBoardProfileForInstance() {
        WorldNpcPlayerProgress progress = new WorldNpcPlayerProgress();
        progress.setPlayerUuid(UUID.randomUUID());
        var slot = new com.hexvane.aetherhaven.questboard.QuestBoardSlotRecord();
        slot.setInstanceId("inst-1");
        progress.boardSlots("hub_default").add(slot);
        assertEquals("hub_default", progress.findBoardProfileIdForInstance("inst-1"));
    }
}
