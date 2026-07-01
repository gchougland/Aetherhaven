package com.hexvane.aetherhaven.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Test;

@Tag("map-marker")
class TeleporterWarpRotationUtilTest {

    @Test
    void rotationNeedsRepair_partialNanTeleporterSignature() {
        Rotation3f rotation = new Rotation3f(Float.NaN, (float) Math.PI, Float.NaN);
        assertTrue(TeleporterWarpRotationUtil.rotationNeedsRepair(rotation));
        Rotation3f repaired = TeleporterWarpRotationUtil.repairRotation(rotation);
        assertTrue(repaired.isFinite());
        assertEquals(0f, repaired.pitch());
        assertEquals(0f, repaired.roll());
        assertEquals((float) Math.PI, repaired.yaw(), 1e-5f);
    }

    @Test
    void rotationNeedsRepair_runawayYaw() {
        float runaway = 97.03263f;
        Rotation3f rotation = new Rotation3f(0f, runaway, 0f);
        assertTrue(TeleporterWarpRotationUtil.rotationNeedsRepair(rotation));
        Rotation3f repaired = TeleporterWarpRotationUtil.repairRotation(rotation);
        assertEquals(MathUtil.wrapAngle(runaway), repaired.yaw(), 1e-5f);
        assertTrue(repaired.isFinite());
    }

    @Test
    void rotationNeedsRepair_falseForAlreadyCleanYaw() {
        Rotation3f rotation = new Rotation3f(0f, 1.5707963f, 0f);
        assertFalse(TeleporterWarpRotationUtil.rotationNeedsRepair(rotation));
    }

    @Test
    void rotationNeedsRepair_allNanMarkerStyle() {
        Rotation3f rotation = new Rotation3f(Float.NaN, Float.NaN, Float.NaN);
        assertTrue(TeleporterWarpRotationUtil.rotationNeedsRepair(rotation));
        Rotation3f repaired = TeleporterWarpRotationUtil.repairRotation(rotation);
        assertTrue(repaired.isFinite());
        assertEquals(0f, repaired.yaw());
    }
}
