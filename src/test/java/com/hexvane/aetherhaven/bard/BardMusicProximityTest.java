package com.hexvane.aetherhaven.bard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("bard")
class BardMusicProximityTest {

    @Test
    void nearestMusic_enterRadiusExcludesPlayerAt42() {
        BardActivePerformancesResource resource = new BardActivePerformancesResource();
        resource.putSnapshot(0.0, 0.0, 0.0, 11);

        assertEquals(0, resource.nearestMusic(42.0, 0.0, 0.0, false).musicContainerIndex());
    }

    @Test
    void nearestMusic_leaveRadiusKeepsListenerAt42() {
        BardActivePerformancesResource resource = new BardActivePerformancesResource();
        resource.putSnapshot(0.0, 0.0, 0.0, 11);

        assertEquals(11, resource.nearestMusic(42.0, 0.0, 0.0, true).musicContainerIndex());
    }

    @Test
    void nearestMusic_leaveRadiusDropsListenerAt45() {
        BardActivePerformancesResource resource = new BardActivePerformancesResource();
        resource.putSnapshot(0.0, 0.0, 0.0, 11);

        assertEquals(0, resource.nearestMusic(45.0, 0.0, 0.0, true).musicContainerIndex());
    }

    @Test
    void musicOrigin_copyKeepsStagePositionAcrossShuffleAdvance() {
        BardPerformanceComponent first =
            new BardPerformanceComponent("a", 1L, 11, BardPlaybackMode.SHUFFLE, new String[] {"b"});
        first.setMusicOrigin(4.0, 5.0, 6.0);

        BardPerformanceComponent next =
            new BardPerformanceComponent("b", 2L, 12, BardPlaybackMode.SHUFFLE, new String[0]);
        next.copyMusicOriginFrom(first);

        assertEquals(4.0, next.getOriginX());
        assertEquals(5.0, next.getOriginY());
        assertEquals(6.0, next.getOriginZ());
        assertTrue(next.hasMusicOrigin());
    }
}
