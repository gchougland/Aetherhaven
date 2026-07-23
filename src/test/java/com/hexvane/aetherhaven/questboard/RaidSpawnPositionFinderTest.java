package com.hexvane.aetherhaven.questboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("raid")
class RaidSpawnPositionFinderTest {

    @Test
    void orderDirectionsPrefersLockedWaveDirection() {
        List<RaidApproachDirection> ordered = RaidSpawnPositionFinder.orderDirections(
            RaidApproachDirection.EAST,
            RaidApproachDirection.NORTH,
            new Random(1)
        );
        assertEquals(RaidApproachDirection.NORTH, ordered.get(0));
        assertEquals(4, ordered.size());
    }

    @Test
    void orderDirectionsTriesPreferredBeforeOthers() {
        List<RaidApproachDirection> ordered = RaidSpawnPositionFinder.orderDirections(
            RaidApproachDirection.EAST,
            null,
            new Random(1)
        );
        assertEquals(RaidApproachDirection.EAST, ordered.get(0));
        assertTrue(ordered.contains(RaidApproachDirection.NORTH));
    }
}
