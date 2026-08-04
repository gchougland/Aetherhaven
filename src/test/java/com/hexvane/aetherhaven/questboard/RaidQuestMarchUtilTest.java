package com.hexvane.aetherhaven.questboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class RaidQuestMarchUtilTest {

    @Test
    void isCombatStateName_detectsCommonAggressiveStates() {
        assertTrue(RaidQuestMarchUtil.isCombatStateName("Combat.Default"));
        assertTrue(RaidQuestMarchUtil.isCombatStateName("Chase.Attack"));
        assertTrue(RaidQuestMarchUtil.isCombatStateName("Search.Default"));
        assertTrue(RaidQuestMarchUtil.isCombatStateName("Alerted.Default"));
        assertTrue(RaidQuestMarchUtil.isCombatStateName("Attack"));
    }

    @Test
    void isCombatStateName_ignoresMarchStates() {
        assertFalse(RaidQuestMarchUtil.isCombatStateName("ReturnHome"));
        assertFalse(RaidQuestMarchUtil.isCombatStateName("Leash"));
        assertFalse(RaidQuestMarchUtil.isCombatStateName("AetherhavenRaidMarch"));
        assertFalse(RaidQuestMarchUtil.isCombatStateName("Idle"));
    }

    @Test
    void computeNextWaypoint_stopsShortOfCharter() {
        Vector3d charter = new Vector3d(100, 72, 100);
        Vector3d far = new Vector3d(200, 64, 100);
        Vector3d next = RaidQuestMarchUtil.computeNextWaypoint(far, charter);
        double dist = Math.hypot(next.x - charter.x, next.z - charter.z);
        assertTrue(dist >= 10.0 - 0.01);
        assertEquals(64.0, next.y, 0.001);

        Vector3d close = new Vector3d(105, 64, 100);
        Vector3d standoff = RaidQuestMarchUtil.computeNextWaypoint(close, charter);
        assertTrue(Math.hypot(standoff.x - charter.x, standoff.z - charter.z) >= 9.5);
        assertEquals(64.0, standoff.y, 0.001);
    }
}
