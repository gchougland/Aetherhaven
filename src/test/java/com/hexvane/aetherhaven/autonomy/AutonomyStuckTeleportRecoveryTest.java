package com.hexvane.aetherhaven.autonomy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.tourist.TouristAutonomyState;
import org.joml.Vector3d;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("autonomy")
class AutonomyStuckTeleportRecoveryTest {

    @Test
    void stallTicksIncrementWhenNpcDoesNotMove() {
        TouristAutonomyState state = TouristAutonomyState.fresh(0L);
        Vector3d pos = new Vector3d(10.0, 64.0, 20.0);

        AutonomyStuckTeleportRecovery.updateStall(state, pos);
        assertEquals(0, state.getAutonomyStallTicks());

        for (int i = 0; i < 5; i++) {
            AutonomyStuckTeleportRecovery.updateStall(state, pos);
        }
        assertEquals(5, state.getAutonomyStallTicks());
        assertFalse(AutonomyStuckTeleportRecovery.isStallTeleportDue(state));
    }

    @Test
    void wigglingInsideAnchorRadiusStillAccumulatesStall() {
        TouristAutonomyState state = TouristAutonomyState.fresh(0L);
        Vector3d anchor = new Vector3d(5.0, 64.0, 5.0);
        AutonomyStuckTeleportRecovery.updateStall(state, anchor, 5.0, 100.0);

        for (int i = 0; i < 30; i++) {
            double wiggle = (i % 2 == 0) ? 0.4 : -0.35;
            AutonomyStuckTeleportRecovery.updateStall(
                state,
                new Vector3d(5.0 + wiggle, 64.0, 5.0),
                5.0,
                100.0
            );
        }
        assertEquals(30, state.getAutonomyStallTicks());
    }

    @Test
    void stallTicksResetAfterAnchorRadiusMovement() {
        TouristAutonomyState state = TouristAutonomyState.fresh(0L);
        Vector3d start = new Vector3d(0.0, 64.0, 0.0);
        AutonomyStuckTeleportRecovery.updateStall(state, start);
        for (int i = 0; i < 20; i++) {
            AutonomyStuckTeleportRecovery.updateStall(state, start);
        }
        assertEquals(20, state.getAutonomyStallTicks());

        double radius = AetherhavenConstants.AUTONOMY_STALL_ANCHOR_RADIUS + 0.5;
        AutonomyStuckTeleportRecovery.updateStall(state, new Vector3d(radius, 64.0, 0.0));
        assertEquals(0, state.getAutonomyStallTicks());
    }

    @Test
    void stallTicksResetWhenMovingTowardGoal() {
        TouristAutonomyState state = TouristAutonomyState.fresh(0L);
        AutonomyStuckTeleportRecovery.updateStall(state, new Vector3d(0.0, 64.0, 0.0), 10.0, 0.0);
        for (int i = 0; i < 15; i++) {
            AutonomyStuckTeleportRecovery.updateStall(state, new Vector3d(0.0, 64.0, 0.0), 10.0, 0.0);
        }
        assertEquals(15, state.getAutonomyStallTicks());

        AutonomyStuckTeleportRecovery.updateStall(state, new Vector3d(0.5, 64.0, 0.0), 10.0, 0.0);
        assertEquals(0, state.getAutonomyStallTicks());
    }

    @Test
    void stallTeleportDueAtConfiguredThreshold() {
        TouristAutonomyState state = TouristAutonomyState.fresh(0L);
        state.setAutonomyStallTicks(AetherhavenConstants.AUTONOMY_STALL_TELEPORT_TICKS);
        assertTrue(AutonomyStuckTeleportRecovery.isStallTeleportDue(state));
    }

    @Test
    void resetAfterRecoveryClearsStallTracking() {
        TouristAutonomyState state = TouristAutonomyState.fresh(0L);
        state.setAutonomySamplePosition(1.0, 2.0);
        state.setAutonomyAnchorPosition(1.0, 2.0);
        state.setAutonomyStallTicks(42);
        AutonomyStuckTeleportRecovery.resetAfterRecovery(state);
        assertEquals(0, state.getAutonomyStallTicks());
        assertFalse(Double.isFinite(state.getAutonomySampleX()));
        assertFalse(Double.isFinite(state.getAutonomyAnchorX()));
    }

    @Test
    void shouldTrackTouristStallSkipsPoiUnlessLeaveDue() {
        assertFalse(
            AutonomyStuckTeleportRecovery.shouldTrackTouristStall(TouristAutonomyState.PHASE_POI, false)
        );
        assertTrue(
            AutonomyStuckTeleportRecovery.shouldTrackTouristStall(TouristAutonomyState.PHASE_POI, true)
        );
        assertTrue(
            AutonomyStuckTeleportRecovery.shouldTrackTouristStall(TouristAutonomyState.PHASE_TRAVEL, false)
        );
    }

    @Test
    void shouldTrackVillagerStallOnlyDuringTravel() {
        assertTrue(AutonomyStuckTeleportRecovery.shouldTrackVillagerStall(VillagerAutonomyState.PHASE_TRAVEL));
        assertFalse(AutonomyStuckTeleportRecovery.shouldTrackVillagerStall(VillagerAutonomyState.PHASE_USE));
        assertFalse(AutonomyStuckTeleportRecovery.shouldTrackVillagerStall(VillagerAutonomyState.PHASE_IDLE));
    }

    @Test
    void resolveVillagerRecoveryTargetUsesTravelCoordinates() {
        VillagerAutonomyState autonomy = VillagerAutonomyState.fresh(0L);
        autonomy.setPhase(VillagerAutonomyState.PHASE_TRAVEL);
        autonomy.setTargetPoiUuid(java.util.UUID.randomUUID());
        autonomy.setTravelTarget(12.5, 70.0, -4.25, autonomy.getTargetPoiUuid());

        Vector3d target = AutonomyStuckTeleportRecovery.resolveVillagerRecoveryTarget(autonomy);
        assertNotNull(target);
        assertEquals(12.5, target.x, 0.001);
        assertEquals(70.0, target.y, 0.001);
        assertEquals(-4.25, target.z, 0.001);
    }

    @Test
    void resolveVillagerRecoveryTargetNullOutsideTravel() {
        VillagerAutonomyState autonomy = VillagerAutonomyState.fresh(0L);
        autonomy.setPhase(VillagerAutonomyState.PHASE_IDLE);
        assertNull(AutonomyStuckTeleportRecovery.resolveVillagerRecoveryTarget(autonomy));
    }
}
