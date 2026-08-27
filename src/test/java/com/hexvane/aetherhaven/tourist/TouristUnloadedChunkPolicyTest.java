package com.hexvane.aetherhaven.tourist;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.entity.EntityPresenceUtil;
import com.hexvane.aetherhaven.entity.EntityPresenceUtil.EntityPresence;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("tourist")
class TouristUnloadedChunkPolicyTest {

    @Test
    void leaveWindowKeepsRecordWhenEntityIsUnknownUnloaded() {
        assertFalse(
            EntityPresenceUtil.shouldFinalizeTouristLeaveForMissingEntity(EntityPresence.UNKNOWN_UNLOADED)
        );
    }

    @Test
    void reconcileKeepsRecordWhenEntityIsUnknownUnloadedEvenIfReleaseMissing() {
        assertFalse(
            EntityPresenceUtil.shouldReleaseMissingTouristRecord(
                EntityPresence.UNKNOWN_UNLOADED,
                true,
                true
            )
        );
    }

    @Test
    void reconcileKeepsRecordWhenTownNpcChunksUnloaded() {
        assertFalse(
            EntityPresenceUtil.shouldReleaseMissingTouristRecord(
                EntityPresence.LOADED_GONE,
                true,
                false
            )
        );
    }

    @Test
    void spawnPipelineFailureConsumesPlannedSlot() {
        assertTrue(
            TouristPortalTickService.shouldConsumePlannedSpawnSlot(
                TouristPortalTickService.PlannedSpawnAttemptOutcome.SPAWN_FAILED
            )
        );
    }

    @Test
    void successfulSpawnConsumesPlannedSlot() {
        assertTrue(
            TouristPortalTickService.shouldConsumePlannedSpawnSlot(
                TouristPortalTickService.PlannedSpawnAttemptOutcome.SUCCESS
            )
        );
    }

    @Test
    void noCharacterAvailableDoesNotConsumePlannedSlot() {
        assertFalse(
            TouristPortalTickService.shouldConsumePlannedSpawnSlot(
                TouristPortalTickService.PlannedSpawnAttemptOutcome.NO_CHARACTER
            )
        );
    }

    @Test
    void deferredPortalDoesNotConsumePlannedSlot() {
        assertFalse(
            TouristPortalTickService.shouldConsumePlannedSpawnSlot(
                TouristPortalTickService.PlannedSpawnAttemptOutcome.DEFERRED
            )
        );
    }
}
