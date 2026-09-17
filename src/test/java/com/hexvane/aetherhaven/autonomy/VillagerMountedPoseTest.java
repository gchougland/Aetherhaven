package com.hexvane.aetherhaven.autonomy;

import com.hypixel.hytale.builtin.mounts.BlockMountComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.BlockMountType;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.mountpoints.RotatedMountPointsArray;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.mountpoints.BlockMountPoint;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesSystems;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.system.TransformSystems;
import com.hypixel.hytale.server.core.modules.entity.system.ModelSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.systems.MovementStatesSystem;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("autonomy")
class VillagerMountedPoseTest {
    // Identity-only references for the native seat map; these tests never access a store.
    private final Ref<EntityStore> first = new Ref<>(null, 1);
    private final Ref<EntityStore> second = new Ref<>(null, 2);

    @Test void occupiedOriginSeatRedirectsToFloorBesideSecondSeatForEveryRotation() {
        // Exact native Furniture_Village_Bench offsets: origin seat and filler seat one block left.
        var points = new RotatedMountPointsArray(new BlockMountPoint[]{
            new BlockMountPoint(new Vector3d(0, 0, .2), 0),
            new BlockMountPoint(new Vector3d(-1, 0, .2), 0)});
        for (Rotation yaw : Rotation.values()) {
            var block = new Vector3i(12, 70, -30);
            int rotation = RotationTuple.of(yaw, Rotation.None, Rotation.None).index();
            var rotated = points.getRotated(rotation);
            var seats = new BlockMountComponent(BlockMountType.Seat, block, null, rotation);
            var click = rotated[0].computeWorldSpacePosition(block);
            seats.putSeatedEntity(rotated[0], first);
            var free = seats.findAvailableSeat(block, rotated, click);
            assertSame(rotated[1], free, "Clicking the occupied seat must select the other seat");
            var occupiedColumns = java.util.Arrays.stream(rotated)
                .map(p -> p.computeWorldSpacePosition(block))
                .map(p -> new Vector3i((int) Math.floor(p.x), 70, (int) Math.floor(p.z))).toList();
            var approach = VillagerBlockUtil.findSeatApproach(free.computeWorldSpacePosition(block), 70,
                cell -> cell.y == 70 && !occupiedColumns.contains(cell));
            assertNotNull(approach);
            assertEquals(70.02, approach.y, 1e-8, "Walk on the park floor, not on top of the bench");
            var seatPosition = free.computeWorldSpacePosition(block);
            double dx = approach.x - seatPosition.x, dz = approach.z - seatPosition.z;
            assertTrue(dx * dx + dz * dz <= VillagerBlockUtil.MOUNT_POI_MAX_HORIZONTAL * VillagerBlockUtil.MOUNT_POI_MAX_HORIZONTAL);
            seats.putSeatedEntity(free, second);
            assertNull(seats.findAvailableSeat(block, rotated, click), "A third arrival cannot claim either occupied seat");
            seats.removeSeatedEntity(first);
            assertSame(rotated[0], seats.findAvailableSeat(block, rotated, click));
        }
    }

    @Test void noFloorApproachDoesNotFallBackToStandingAboveTheBench() {
        assertNull(VillagerBlockUtil.findSeatApproach(new Vector3d(.5, 70.5, .7), 70, cell -> false));
    }

    @Test void fallingAndStaleMovementFlagsBecomeSeatedAndAreClearedOnDismount() {
        var states = new MovementStates();
        states.falling = states.fallingFar = states.jumping = states.running = true;
        states.walking = states.sprinting = states.flying = states.swimming = states.sliding = true;
        assertTrue(VillagerMountedPoseSystem.applyMountedMovement(states, BlockMountType.Seat));
        assertTrue(states.sitting);
        assertTrue(states.onGround);
        assertFalse(states.sleeping || states.falling || states.fallingFar || states.jumping || states.running
            || states.walking || states.sprinting || states.flying || states.swimming || states.sliding);
        for (int tick = 0; tick < 100; tick++) {
            // Vanilla movement can consider a suspended mount point to be dropping.
            states.falling = states.fallingFar = true;
            assertFalse(VillagerMountedPoseSystem.applyMountedMovement(states, BlockMountType.Seat),
                "Restore movement flags without restarting the seated animation");
            assertFalse(states.falling || states.fallingFar);
        }
        VillagerMountedPoseSystem.clearMountedMovement(states);
        assertFalse(states.sitting || states.sleeping);
        assertTrue(VillagerMountedPoseSystem.applyMountedMovement(states, BlockMountType.Bed));
        assertTrue(states.sleeping);
        assertFalse(states.sitting);
        VillagerMountedPoseSystem.clearMountedMovement(states);
        assertFalse(states.sitting || states.sleeping);
    }

    @Test void restoresBothOccupiedSeatsAfterCollisionDisplacementAtEveryBenchRotation() {
        for (Rotation yaw : Rotation.values()) {
            var block = new Vector3i(12, 70, -30);
            int rotation = RotationTuple.of(yaw, Rotation.None, Rotation.None).index();
            var seats = new BlockMountComponent(BlockMountType.Seat, block, null, rotation);
            var left = new BlockMountPoint(new Vector3d(-.5, .4, .1), 0)
                .rotate(yaw, Rotation.None, Rotation.None);
            var right = new BlockMountPoint(new Vector3d(.5, .4, .1), 0)
                .rotate(yaw, Rotation.None, Rotation.None);
            seats.putSeatedEntity(left, first);
            seats.putSeatedEntity(right, second);
            var firstPose = new TransformComponent(left.computeWorldSpacePosition(block), new Rotation3f());
            var secondPose = new TransformComponent(right.computeWorldSpacePosition(block).add(0, 1, 0), new Rotation3f());

            for (int tick = 0; tick < 3; tick++) {
                secondPose.setPosition(new Vector3d(secondPose.getPosition()).add(0, .25, 0));
                assertTrue(VillagerMountedPoseSystem.alignToOccupiedSeat(second, seats, secondPose));
                assertEquals(0, secondPose.getPosition().distanceSquared(right.computeWorldSpacePosition(block)), 1e-12);
                assertEquals(right.computeRotationEuler(rotation), secondPose.getRotation());
                assertEquals(0, firstPose.getPosition().distanceSquared(left.computeWorldSpacePosition(block)), 1e-12);
            }
            assertSame(left, seats.getSeatBlockBySeatedEntity(first));
            assertSame(right, seats.getSeatBlockBySeatedEntity(second));
        }
    }

    @Test void releasedVillagerCannotSnapToASeatReusedBySomeoneElse() {
        var seats = new BlockMountComponent(BlockMountType.Seat, new Vector3i(2, 60, 4), null, 0);
        var point = new BlockMountPoint(new Vector3d(.5, .4, .1), 0);
        seats.putSeatedEntity(point, first);
        seats.removeSeatedEntity(first);
        seats.putSeatedEntity(point, second);
        var pose = new TransformComponent(new Vector3d(3, 61, 4), new Rotation3f());
        var before = new Vector3d(pose.getPosition());
        assertFalse(VillagerMountedPoseSystem.alignToOccupiedSeat(first, seats, pose));
        assertEquals(before, pose.getPosition());
        assertTrue(VillagerMountedPoseSystem.alignToOccupiedSeat(second, seats, pose));
        assertEquals(point.computeWorldSpacePosition(seats.getBlockPos()), pose.getPosition());
    }

    @Test void finalPoseRunsAfterNpcMovementAndBeforeNetworkUpdates() {
        var dependencies = new VillagerMountedPoseSystem().getDependencies();
        assertTrue(dependencies.stream().anyMatch(d -> d instanceof SystemDependency<?, ?> s
            && s.getSystemClass() == MovementStatesSystem.class && s.getOrder() == Order.AFTER));
        for (Class<?> tracker : new Class<?>[]{ModelSystems.UpdateMovementStateBoundingBox.class,
            TransformSystems.EntityTrackerUpdate.class, MovementStatesSystems.TickingSystem.class}) {
            assertTrue(dependencies.stream().anyMatch(d -> d instanceof SystemDependency<?, ?> s
                && s.getSystemClass() == tracker && s.getOrder() == Order.BEFORE));
        }
        assertEquals("Sit", VillagerMountedPoseSystem.poseFor(BlockMountType.Seat));
        assertEquals("Sleep", VillagerMountedPoseSystem.poseFor(BlockMountType.Bed));
    }
}
