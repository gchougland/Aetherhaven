package com.hexvane.aetherhaven.autonomy;

import com.hypixel.hytale.builtin.mounts.BlockMountComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.BlockMountType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.mountpoints.BlockMountPoint;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.mountpoints.RotatedMountPointsArray;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@Tag("autonomy")
class VillagerSeatExitTest {
    @Test void repeatedBenchTurnoverSeparatesDepartingAndArrivingOccupantsAtEveryRotation() {
        var nativeSeats = new RotatedMountPointsArray(new BlockMountPoint[]{
            new BlockMountPoint(new Vector3d(0, 0, .2), 0),
            new BlockMountPoint(new Vector3d(-1, 0, .2), 0)});
        for (Rotation yaw : Rotation.values()) {
            int rotation = RotationTuple.of(yaw, Rotation.None, Rotation.None).index();
            var block = new Vector3i(12, 70, -30);
            var points = nativeSeats.getRotated(rotation);
            var bench = new BlockMountComponent(BlockMountType.Seat, block, null, rotation);
            var first = new Ref<EntityStore>(null, 1);
            var second = new Ref<EntityStore>(null, 2);
            bench.putSeatedEntity(points[0], first);
            bench.putSeatedEntity(points[1], second);
            var floorOccupants = new ArrayList<Vector3d>();
            var columns = java.util.Arrays.stream(points).map(p -> p.computeWorldSpacePosition(block))
                .map(p -> new Vector3i((int) Math.floor(p.x), 70, (int) Math.floor(p.z))).toList();
            for (int visit = 0; visit < 12; visit++) {
                var occupants = new ArrayList<>(floorOccupants);
                occupants.add(points[1].computeWorldSpacePosition(block));
                var seatPosition = points[0].computeWorldSpacePosition(block);
                var exit = VillagerSeatExit.findExit(seatPosition, 70,
                    cell -> cell.y == 70 && !columns.contains(cell), occupants);
                assertNotNull(exit);
                assertEquals(70.02, exit.y, 1e-8);
                assertFalse(columns.contains(new Vector3i((int) Math.floor(exit.x), 70, (int) Math.floor(exit.z))));
                assertTrue(VillagerSeatExit.clearOfOccupants(exit, occupants));
                assertNull(bench.findAvailableSeat(block, points, seatPosition), "Departure must still own its seat");
                floorOccupants.add(exit);
                bench.removeSeatedEntity(first);
                var incoming = new Ref<EntityStore>(null, visit + 3);
                assertSame(points[0], bench.findAvailableSeat(block, points, seatPosition));
                bench.putSeatedEntity(points[0], incoming);
                assertTrue(VillagerSeatExit.clearOfOccupants(seatPosition, floorOccupants));
                first = incoming;
                // Departing villagers continue walking, leaving at most two exits occupied.
                if (floorOccupants.size() > 2) floorOccupants.removeFirst();
            }
        }
    }

    @Test void blockedExitWaitsWithoutUsingBenchTopOrOverlappingANeighbor() {
        var origin = new Vector3d(.5, 70.5, .7);
        var onlyFloor = new Vector3i(0, 70, 2);
        var neighbor = new Vector3d(.5, 70.02, 2.5);
        assertNull(VillagerSeatExit.findExit(origin, 70, cell -> cell.equals(onlyFloor), List.of(neighbor)));
        assertNotNull(VillagerSeatExit.findExit(origin, 70, cell -> cell.equals(onlyFloor), List.of()));
        assertNull(VillagerSeatExit.findExit(origin, 70, cell -> false, List.of()), "Unloaded or enclosed floor has no exit");
    }

    @Test void occupantsOnAnotherFloorDoNotBlockAValidExit() {
        assertTrue(VillagerSeatExit.clearOfOccupants(new Vector3d(.5, 70.02, 1.5),
            List.of(new Vector3d(.5, 74.02, 1.5))));
        assertFalse(VillagerSeatExit.clearOfOccupants(new Vector3d(.5, 70.02, 1.5),
            List.of(new Vector3d(.8, 70.1, 1.6))));
    }
}
