package com.hexvane.aetherhaven.festival.snowball;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.protocol.MovementStates;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
final class SnowballVillagerMovementStatesTest {
    @Test
    void crouchedSnapshotUsesIdlePose() {
        MovementStates states = new MovementStates();
        states.idle = false;
        states.walking = true;

        SnowballVillagerSystem.applySnowballFightMovementStates(states, true);

        assertTrue(states.crouching);
        assertTrue(states.forcedCrouching);
        assertTrue(states.idle);
        assertTrue(states.horizontalIdle);
        assertFalse(states.walking);
        assertFalse(states.running);
        assertFalse(states.sprinting);
        assertFalse(states.jumping);
        assertFalse(states.falling);
    }

    @Test
    void standingSnapshotClearsCrouch() {
        MovementStates states = new MovementStates();
        states.crouching = true;
        states.forcedCrouching = true;
        states.idle = false;

        SnowballVillagerSystem.applySnowballFightMovementStates(states, false);

        assertFalse(states.crouching);
        assertFalse(states.forcedCrouching);
        assertTrue(states.idle);
        assertTrue(states.horizontalIdle);
    }
}
