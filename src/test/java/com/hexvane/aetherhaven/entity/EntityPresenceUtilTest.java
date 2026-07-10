package com.hexvane.aetherhaven.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.entity.EntityPresenceUtil.EntityPresence;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("entity")
class EntityPresenceUtilTest {

    @Test
    void unknownUnloadedDoesNotReleaseMissingTouristRecord() {
        assertFalse(
            EntityPresenceUtil.shouldReleaseMissingTouristRecord(
                EntityPresence.UNKNOWN_UNLOADED,
                true,
                true
            )
        );
    }

    @Test
    void unknownUnloadedDoesNotFinalizeTouristLeave() {
        assertFalse(EntityPresenceUtil.shouldFinalizeTouristLeaveForMissingEntity(EntityPresence.UNKNOWN_UNLOADED));
    }

    @Test
    void confirmedAbsentReleasesMissingTouristWhenTownChunksLoaded() {
        assertTrue(
            EntityPresenceUtil.shouldReleaseMissingTouristRecord(
                EntityPresence.LOADED_GONE,
                true,
                true
            )
        );
    }

    @Test
    void confirmedAbsentDoesNotReleaseWhenTownChunksUnloaded() {
        assertFalse(
            EntityPresenceUtil.shouldReleaseMissingTouristRecord(
                EntityPresence.LOADED_GONE,
                true,
                false
            )
        );
    }

    @Test
    void confirmedAbsentFinalizesTouristLeave() {
        assertTrue(EntityPresenceUtil.shouldFinalizeTouristLeaveForMissingEntity(EntityPresence.LOADED_GONE));
    }

    @Test
    void loadedLiveDoesNotReleaseMissingTouristRecord() {
        assertFalse(
            EntityPresenceUtil.shouldReleaseMissingTouristRecord(
                EntityPresence.LOADED_LIVE,
                true,
                true
            )
        );
    }
}
