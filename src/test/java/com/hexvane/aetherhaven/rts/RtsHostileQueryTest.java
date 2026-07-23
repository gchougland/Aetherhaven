package com.hexvane.aetherhaven.rts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("entity")
class RtsHostileQueryTest {

    @Test
    void horizontalDistanceUsesXZOnly() {
        assertEquals(5.0, RtsHostileQuery.horizontalDistance(0, 0, 3, 4), 0.001);
        assertEquals(0.0, RtsHostileQuery.horizontalDistance(2, 2, 2, 2), 0.001);
    }

    @Test
    void ambientHostileRolePatternsMatchOrphans() {
        assertFalse(RtsHostileQuery.matchesAmbientHostileRoleName("Outlander_Brute"));
        assertTrue(RtsHostileQuery.matchesAmbientHostileRoleName("Golem_Firesteel"));
        assertTrue(RtsHostileQuery.matchesAmbientHostileRoleName("Spirit_Ember"));
        assertTrue(RtsHostileQuery.matchesAmbientHostileRoleName("Ghoul"));
        assertTrue(RtsHostileQuery.matchesAmbientHostileRoleName("Dungeon_Scarak_Fighter"));
        assertFalse(RtsHostileQuery.matchesAmbientHostileRoleName("Klops_Miner"));
    }

    @Test
    void engagedInExternalCombatRecognizesRoleStates() {
        assertTrue(RtsGuardCombatSupport.isEngagedInExternalCombat("Combat"));
        assertTrue(RtsGuardCombatSupport.isEngagedInExternalCombat("Attack.Melee"));
        assertTrue(RtsGuardCombatSupport.isEngagedInExternalCombat("Chase.Run"));
        assertFalse(RtsGuardCombatSupport.isEngagedInExternalCombat("Patrol"));
        assertFalse(RtsGuardCombatSupport.isEngagedInExternalCombat("Idle"));
    }
}
