package com.hexvane.aetherhaven.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hypixel.hytale.math.vector.Rotation3f;
import org.joml.Vector3d;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("entity")
class BlockEntityScaleMigrationTest {

    @Test
    void naturalOldScaleMovesUpHalfBlock() {
        Vector3d position = new Vector3d(10.0, 5.0, 20.0);
        BlockEntityScaleMigration.applyAnchorShift(position, new Rotation3f(), 2.0f);
        assertEquals(10.0, position.x, 1e-6);
        assertEquals(5.5, position.y, 1e-6);
        assertEquals(20.0, position.z, 1e-6);
    }

    @Test
    void mapOldScaleMovesUpThreeTenths() {
        Vector3d position = new Vector3d(0.0, 2.0, 0.0);
        BlockEntityScaleMigration.applyAnchorShift(position, new Rotation3f(), 1.2f);
        assertEquals(0.0, position.x, 1e-6);
        assertEquals(2.3, position.y, 1e-6);
        assertEquals(0.0, position.z, 1e-6);
    }
}
