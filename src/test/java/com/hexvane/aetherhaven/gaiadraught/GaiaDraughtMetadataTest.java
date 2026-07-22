package com.hexvane.aetherhaven.gaiadraught;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("entity")
class GaiaDraughtMetadataTest {
    @Test
    void freshProgress_startsAtBaseCapacityAndTierZero() {
        GaiaDraughtState s = GaiaDraughtState.createFresh();
        s.setUnlocked(true);
        assertEquals(GaiaDraughtState.DEFAULT_CAPACITY, s.getCapacity());
        assertEquals(0, s.getHealTier());
    }

    @Test
    void consumeCharge_clampsAtZero() {
        GaiaDraughtState s = GaiaDraughtState.createFresh();
        s.setUnlocked(true);
        s.setCharges(1);
        s.setCharges(s.getCharges() - 1);
        assertEquals(0, s.getCharges());
    }

    @Test
    void shardUpgrade_increasesCapacityUpToMax() {
        GaiaDraughtState s = GaiaDraughtState.createFresh();
        s.setUnlocked(true);
        for (int i = 0; i < GaiaDraughtState.MAX_UPGRADES_PER_TYPE; i++) {
            assertTrue(s.tryApplyShardCapacityUpgrade());
        }
        assertEquals(GaiaDraughtState.MAX_FLASK_CAPACITY, s.getCapacity());
        assertFalse(s.tryApplyShardCapacityUpgrade());
    }

    @Test
    void catalystUpgrade_raisesHealTierUpToMax() {
        GaiaDraughtState s = GaiaDraughtState.createFresh();
        s.setUnlocked(true);
        for (int i = 0; i < GaiaDraughtState.MAX_UPGRADES_PER_TYPE; i++) {
            assertTrue(s.tryApplyCatalystHealTierUpgrade());
        }
        assertEquals(GaiaDraughtState.MAX_HEAL_TIER, s.getHealTier());
        assertFalse(s.tryApplyCatalystHealTierUpgrade());
    }

    @Test
    void storedUpgrades_roundTripCounts() {
        GaiaDraughtState s = GaiaDraughtState.fromStoredUpgrades(2, 3, 2);
        assertEquals(4, s.getCapacity());
        assertEquals(2, s.getHealTier());
        assertEquals(3, s.getShardUpgradeCount());
        assertEquals(2, s.getCatalystUpgradeCount());
    }
}
