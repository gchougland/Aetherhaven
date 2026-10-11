package com.hexvane.aetherhaven.autonomy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class WorkTravelRecoveryTest {
    private static final Vector3d STAND = new Vector3d(100.5, 40, 100.5);
    private static final long STEP = 250L;

    /** Walks the clock forward the way a service polls, standing at one spot, and reports the first stalled time. */
    private static long firstStall(WorkTravelRecovery.Watch watch, Vector3d at, long from, long until) {
        for (long now = from; now <= until; now += STEP) {
            if (WorkTravelRecovery.stalled(watch, at, STAND, now)) {
                return now;
            }
        }
        return -1;
    }

    @Test
    void aWorkerStandingStillOnTheWayIsNoticedAfterAFewSeconds() {
        var watch = new WorkTravelRecovery.Watch();
        Vector3d far = new Vector3d(60.5, 64, 100.5);
        assertTrue(firstStall(watch, far, 0, WorkTravelRecovery.BRING_AFTER_MS - STEP) < 0, "Not before the wait is over");
        watch = new WorkTravelRecovery.Watch();
        long when = firstStall(watch, far, 1000, 1000 + WorkTravelRecovery.BRING_AFTER_MS + STEP);
        assertTrue(when >= 1000 + WorkTravelRecovery.BRING_AFTER_MS, "Stalled at " + when);
    }

    @Test
    void walkingOrClimbingIsProgressButShufflingIsNot() {
        var watch = new WorkTravelRecovery.Watch();
        Vector3d start = new Vector3d(60.5, 64, 100.5);
        WorkTravelRecovery.stalled(watch, start, STAND, 0);
        // A step sideways and back, no closer: the wait carries on.
        long now = 0;
        boolean stalled = false;
        for (int i = 1; now < WorkTravelRecovery.BRING_AFTER_MS + STEP; i++) {
            now += STEP;
            stalled = WorkTravelRecovery.stalled(watch, new Vector3d(60.5, 64, 100.5 + (i % 2) * 0.8), STAND, now);
        }
        assertTrue(stalled, "Shuffling on the spot is still stuck");

        // Walking steadily towards the stand never trips it.
        watch = new WorkTravelRecovery.Watch();
        for (int i = 0; i < 80; i++) {
            assertFalse(WorkTravelRecovery.stalled(watch, new Vector3d(60.5 + i * 0.5, 64, 100.5), STAND, i * STEP));
        }
        // Going down a stair right above the stand only changes height. That counts too.
        watch = new WorkTravelRecovery.Watch();
        for (int i = 0; i < 40; i++) {
            assertFalse(WorkTravelRecovery.stalled(watch, new Vector3d(100.5, 60 - i * 0.5, 100.5), STAND, i * STEP));
        }
    }

    @Test
    void aNewJourneyOrAPauseForWorkStartsANewWait() {
        var watch = new WorkTravelRecovery.Watch();
        Vector3d far = new Vector3d(60.5, 64, 100.5);
        assertTrue(firstStall(watch, far, 0, WorkTravelRecovery.BRING_AFTER_MS + STEP) > 0);
        watch.brought = true;
        // A different stand: a new journey, and they may be brought again.
        assertFalse(WorkTravelRecovery.stalled(watch, far, new Vector3d(80.5, 64, 90.5), WorkTravelRecovery.BRING_AFTER_MS + 2 * STEP));
        assertFalse(watch.brought);
        // The same stand after doing something else for a while: also a new wait.
        var again = new WorkTravelRecovery.Watch();
        WorkTravelRecovery.stalled(again, far, STAND, 0);
        long later = WorkTravelRecovery.STALE_MS + WorkTravelRecovery.BRING_AFTER_MS + 1;
        assertFalse(WorkTravelRecovery.stalled(again, far, STAND, later), "Time spent working is not time spent stuck");
    }

    @Test
    void nobodyIsBroughtIntoWaterUnloadedGroundOrThinAir() {
        assertTrue(WorkTravelRecovery.safeStand(true, true, true, true));
        assertFalse(WorkTravelRecovery.safeStand(false, true, true, true));
        assertFalse(WorkTravelRecovery.safeStand(true, false, true, true));
        assertFalse(WorkTravelRecovery.safeStand(true, true, false, true));
        assertFalse(WorkTravelRecovery.safeStand(true, true, true, false));
    }
}
