package com.hexvane.aetherhaven.worldnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("worldnpc")
final class WorldQuestIdsTest {
    @Test
    void worldRowsRoundTrip() {
        assertEquals("world:q_demo", WorldQuestIds.worldRow("q_demo"));
        assertTrue(WorldQuestIds.isWorldQuestRow("world:q_demo"));
        assertEquals("q_demo", WorldQuestIds.parseWorldQuestId("world:q_demo"));
        assertNull(WorldQuestIds.parseWorldQuestId("wboard:x"));
    }

    @Test
    void boardRowsRoundTrip() {
        assertEquals("wboard:inst", WorldQuestIds.boardRow("inst"));
        assertTrue(WorldQuestIds.isWorldBoardRow("wboard:inst"));
        assertEquals("inst", WorldQuestIds.parseWorldBoardInstanceId("wboard:inst"));
        assertFalse(WorldQuestIds.isWorldBoardRow("world:q"));
    }
}
