package com.hexvane.aetherhaven.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("map-marker")
class MapMarkerTransformsTest {

    @Test
    void at_usesFiniteZeroRotation() {
        var transform = MapMarkerTransforms.at(10.5, 64.0, -3.5);
        assertTrue(transform.getRotation().isFinite());
        assertEquals(0f, transform.getRotation().pitch());
        assertEquals(0f, transform.getRotation().yaw());
        assertEquals(0f, transform.getRotation().roll());
    }
}
