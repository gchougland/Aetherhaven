package com.hexvane.aetherhaven.rts;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("entity")
class RtsPrimaryDragTrackerTest {
    private final UUID playerId = UUID.randomUUID();
    private final UUID otherPlayerId = UUID.randomUUID();

    @SuppressWarnings("unchecked")
    private static <T> Map<UUID, T> state(String name) throws ReflectiveOperationException {
        Field field = RtsPrimaryDragTracker.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<UUID, T>) field.get(null);
    }

    @AfterEach
    void cleanup() throws ReflectiveOperationException {
        for (String name : new String[]{"lastPulseMs", "pulseCount", "lastRawScreen"}) {
            state(name).remove(playerId);
            state(name).remove(otherPlayerId);
        }
    }

    @Test
    void cameraOnlyDragReleasesWithoutAPrimaryPulseCount() throws Exception {
        RtsPrimaryDragTracker.noteScreenMotion(playerId, 0.2f, 0.4f);
        long last = RtsPrimaryDragTrackerTest.<Long>state("lastPulseMs").get(playerId);
        assertFalse(state("pulseCount").containsKey(playerId));

        var release = RtsPrimaryDragTracker.pollPendingRelease(playerId, last + 180);

        assertNotNull(release);
        assertEquals(0, release.pulses());
        assertEquals(180, release.idleMs());
        assertFalse(state("lastPulseMs").containsKey(playerId));
        assertFalse(state("lastRawScreen").containsKey(playerId));
        assertNull(RtsPrimaryDragTracker.pollPendingRelease(playerId, last + 1000));
    }

    @Test
    void primaryPulseCountIsPreservedUntilTheIdleThreshold() throws Exception {
        state("lastPulseMs").put(playerId, 1000L);
        state("pulseCount").put(playerId, 7);
        assertNull(RtsPrimaryDragTracker.pollPendingRelease(playerId, 1179L));
        assertEquals(7, state("pulseCount").get(playerId));

        var release = RtsPrimaryDragTracker.pollPendingRelease(playerId, 1180L);

        assertNotNull(release);
        assertEquals(7, release.pulses());
        assertFalse(state("pulseCount").containsKey(playerId));
    }

    @Test
    void absentTimerDoesNotReleaseOrConsumeOtherState() throws Exception {
        state("pulseCount").put(playerId, 3);
        assertNull(RtsPrimaryDragTracker.pollPendingRelease(playerId, Long.MAX_VALUE));
        assertEquals(3, state("pulseCount").get(playerId));
    }

    @Test
    void stationaryCameraDoesNotExtendIdleTimer() throws Exception {
        RtsPrimaryDragTracker.noteScreenMotion(playerId, 0.2f, 0.4f);
        state("lastPulseMs").put(playerId, 1000L);
        RtsPrimaryDragTracker.noteScreenMotion(playerId, 0.2f, 0.4f);
        assertNotNull(RtsPrimaryDragTracker.pollPendingRelease(playerId, 1180L));
    }

    @Test
    void movingCameraExtendsIdleTimer() throws Exception {
        RtsPrimaryDragTracker.noteScreenMotion(playerId, 0.2f, 0.4f);
        state("lastPulseMs").put(playerId, 1000L);
        RtsPrimaryDragTracker.noteScreenMotion(playerId, 0.8f, 0.9f);
        long last = RtsPrimaryDragTrackerTest.<Long>state("lastPulseMs").get(playerId);
        assertTrue(last > 1000L);
        assertNull(RtsPrimaryDragTracker.pollPendingRelease(playerId, last + 179));
        assertNotNull(RtsPrimaryDragTracker.pollPendingRelease(playerId, last + 180));
    }

    @Test
    void nextDragCanStartAtSameScreenPointAfterRelease() throws Exception {
        for (int cycle = 0; cycle < 100; cycle++) {
            RtsPrimaryDragTracker.noteScreenMotion(playerId, 0.2f, 0.4f);
            long last = RtsPrimaryDragTrackerTest.<Long>state("lastPulseMs").get(playerId);
            assertNotNull(RtsPrimaryDragTracker.pollPendingRelease(playerId, last + 180));
        }
    }

    @Test
    void releasingOnePlayerPreservesAnotherPlayersPendingDrag() throws Exception {
        state("lastPulseMs").put(playerId, 1000L);
        state("lastPulseMs").put(otherPlayerId, 1000L);
        state("pulseCount").put(otherPlayerId, 4);
        assertEquals(0, RtsPrimaryDragTracker.pollPendingRelease(playerId, 1180L).pulses());
        assertEquals(1000L, state("lastPulseMs").get(otherPlayerId));
        assertEquals(4, RtsPrimaryDragTracker.pollPendingRelease(otherPlayerId, 1180L).pulses());
    }
}
