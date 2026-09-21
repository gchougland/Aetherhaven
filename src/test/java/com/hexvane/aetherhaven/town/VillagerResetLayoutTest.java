package com.hexvane.aetherhaven.town;

import static org.junit.jupiter.api.Assertions.*;
import org.joml.Vector3d;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
class VillagerResetLayoutTest {
    @Test void villagersSurroundThePlayerWithRoomBetweenNeighborsForSmallAndLargeTowns() {
        var center = new Vector3d(-12.3, 64.5, 20.7);
        for (int count : new int[]{1, 2, 3, 10, 30, 100}) {
            var total = new Vector3d();
            double radius = VillagerResetLayout.position(center, 0, count).distance(center);
            assertTrue(radius >= 3 - 1e-9);
            for (int slot = 0; slot < count; slot++) {
                var position = VillagerResetLayout.position(center, slot, count);
                assertEquals(center.y, position.y);
                assertEquals(radius, position.distance(center), 1e-9);
                if (count > 1) assertTrue(position.distance(VillagerResetLayout.position(center, slot + 1, count)) >= 1.75 - 1e-9);
                total.add(position);
            }
            if (count > 1) assertEquals(0, total.div(count).distance(center), 1e-9);
        }
        assertEquals(new Vector3d(-12.3, 64.5, 20.7), center, "Do not move the player's base position");
    }
}
