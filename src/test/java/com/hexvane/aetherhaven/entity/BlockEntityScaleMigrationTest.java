package com.hexvane.aetherhaven.entity;

import static org.junit.jupiter.api.Assertions.*;

import com.hypixel.hytale.math.vector.Rotation3f;
import org.joml.Vector3d;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("entity")
class BlockEntityScaleMigrationTest {
    @Test void currentDecorationsKeepTheirAuthoredSizesAndPositions() {
        for (float size : new float[] {0.5f, 1f, 2f, 4f}) {
            var block = new com.hypixel.hytale.server.core.entity.entities.BlockEntity("Furniture_Village_Chair");
            var scale = new com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent(size);
            var transform = new com.hypixel.hytale.server.core.modules.entity.component.TransformComponent(new Vector3d(1,2,3),new Rotation3f());
            assertFalse(BlockEntityScaleMigration.migrateLegacyAnchor(block,scale,transform));
            assertEquals(size,scale.getScale());
            assertEquals(new Vector3d(1,2,3),transform.getPosition());
        }
    }
    @Test void nativeLegacyFlagMigratesExactlyOnceEvenForLargeDecorations() {
        var block = com.hypixel.hytale.server.core.entity.entities.BlockEntity.CODEC.decode(
            org.bson.BsonDocument.parse("{\"Version\":1,\"BlockTypeKey\":\"Furniture_Village_Chair\",\"HasOutdatedAnchor\":true}"),new com.hypixel.hytale.codec.ExtraInfo());
        var scale = new com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent(4f);
        var transform = new com.hypixel.hytale.server.core.modules.entity.component.TransformComponent(new Vector3d(),new Rotation3f());
        assertTrue(BlockEntityScaleMigration.migrateLegacyAnchor(block,scale,transform));
        assertEquals(2f,scale.getScale());
        assertEquals(1,transform.getPosition().y,1e-6);
        assertFalse(BlockEntityScaleMigration.migrateLegacyAnchor(block,scale,transform));
        assertEquals(2f,scale.getScale());
        assertFalse(block.consumeOutdatedAnchor());
    }

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
