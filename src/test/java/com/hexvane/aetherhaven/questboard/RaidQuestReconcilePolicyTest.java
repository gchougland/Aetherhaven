package com.hexvane.aetherhaven.questboard;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.entity.EntityPresenceUtil.EntityPresence;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("raid")
class RaidQuestReconcilePolicyTest {

    @Test
    void unloadedChunkDoesNotDropRaidMob() {
        assertFalse(RaidQuestReconcile.shouldDropRaidMob(EntityPresence.UNKNOWN_UNLOADED));
    }

    @Test
    void liveMobIsNotDropped() {
        assertFalse(RaidQuestReconcile.shouldDropRaidMob(EntityPresence.LOADED_LIVE));
    }

    @Test
    void confirmedAbsentMobIsDropped() {
        assertTrue(RaidQuestReconcile.shouldDropRaidMob(EntityPresence.LOADED_GONE));
    }
}
