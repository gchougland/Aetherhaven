package com.hexvane.aetherhaven.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Whose blow fails {@code /ah test mobs}: the town's own guards and townsfolk, and nobody else's. */
@Tag("entity")
class AetherhavenMobsTestCommandTest {
    @Test
    void guardsAndTownsfolkAreTheTown() {
        for (String role : List.of("Aetherhaven_Guard_Knight", "Aetherhaven_Guard_Archer", "Aetherhaven_Blacksmith"))
            assertTrue(AetherhavenMobsTestCommand.isTownRole(role), role);
    }

    @Test
    void raidAttackersAndOtherCreaturesAreNotTheTown() {
        for (String role : List.of("Aetherhaven_Raid_Skeleton_Soldier_Wander", "Bear_Grizzly", "Trork_Warrior", "Tamed_Sheep"))
            assertFalse(AetherhavenMobsTestCommand.isTownRole(role), role);
        assertFalse(AetherhavenMobsTestCommand.isTownRole(null));
    }
}
