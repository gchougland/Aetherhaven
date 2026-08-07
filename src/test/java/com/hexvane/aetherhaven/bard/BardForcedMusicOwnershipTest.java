package com.hexvane.aetherhaven.bard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.bard.BardForcedMusicOwnership.Decision;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("bard")
class BardForcedMusicOwnershipTest {

    @Test
    void noBard_notListening_withZoneMusic_leavesTrackerAlone() {
        Decision decision = BardForcedMusicOwnership.decide(0, 42, false);

        assertFalse(decision.updateTracker());
        assertFalse(decision.markListening());
        assertFalse(decision.clearListening());
    }

    @Test
    void noBard_wasListening_clearsForcedMusic() {
        Decision decision = BardForcedMusicOwnership.decide(0, 7, true);

        assertTrue(decision.updateTracker());
        assertEquals(0, decision.containerIndex());
        assertFalse(decision.markListening());
        assertTrue(decision.clearListening());
    }

    @Test
    void bardNearby_appliesContainerAndMarksListening() {
        Decision decision = BardForcedMusicOwnership.decide(11, 0, false);

        assertTrue(decision.updateTracker());
        assertEquals(11, decision.containerIndex());
        assertTrue(decision.markListening());
        assertFalse(decision.clearListening());
    }

    @Test
    void alreadyMatchingBardMusic_noop() {
        Decision decision = BardForcedMusicOwnership.decide(11, 11, true);

        assertFalse(decision.updateTracker());
        assertFalse(decision.markListening());
        assertFalse(decision.clearListening());
    }

    @Test
    void alreadyCleared_butStaleListening_clearsListeningOnly() {
        Decision decision = BardForcedMusicOwnership.decide(0, 0, true);

        assertFalse(decision.updateTracker());
        assertFalse(decision.markListening());
        assertTrue(decision.clearListening());
    }

    @Test
    void bardOverridesZoneMusic() {
        Decision decision = BardForcedMusicOwnership.decide(11, 42, false);

        assertTrue(decision.updateTracker());
        assertEquals(11, decision.containerIndex());
        assertTrue(decision.markListening());
        assertFalse(decision.clearListening());
    }
}
