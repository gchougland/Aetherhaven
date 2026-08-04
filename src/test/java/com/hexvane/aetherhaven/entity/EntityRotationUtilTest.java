package com.hexvane.aetherhaven.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("entity")
class EntityRotationUtilTest {

    @Test
    void repair_nanRollFinitePitchYaw() {
        Rotation3f rotation = new Rotation3f(0.2088248f, 4.770728f, Float.NaN);
        assertTrue(EntityRotationUtil.needsRepair(rotation));
        Rotation3f repaired = EntityRotationUtil.repair(rotation);
        assertTrue(repaired.isFinite());
        assertEquals(0.2088248f, repaired.pitch(), 1e-5f);
        assertEquals(0f, repaired.roll());
    }

    @Test
    void repair_allNan() {
        Rotation3f rotation = new Rotation3f(Float.NaN, Float.NaN, Float.NaN);
        Rotation3f repaired = EntityRotationUtil.repair(rotation);
        assertTrue(repaired.isFinite());
        assertEquals(0f, repaired.pitch());
        assertEquals(0f, repaired.yaw());
        assertEquals(0f, repaired.roll());
    }

    @Test
    void repairInPlace_falseWhenAlreadyValid() {
        Rotation3f rotation = new Rotation3f(0.1f, 1.5707963f, 0f);
        assertFalse(EntityRotationUtil.repairInPlace(rotation));
        assertEquals(0.1f, rotation.pitch(), 1e-5f);
        assertEquals(1.5707963f, rotation.yaw(), 1e-5f);
        assertEquals(0f, rotation.roll());
    }

    @Test
    void repair_wrapsRunawayYaw() {
        float runaway = 97.03263f;
        Rotation3f repaired = EntityRotationUtil.repair(new Rotation3f(0f, runaway, Float.NaN));
        assertEquals(MathUtil.wrapAngle(runaway), repaired.yaw(), 1e-5f);
    }

    @Test
    void setBodyYaw_clearsNonFiniteRoll() {
        Rotation3f rotation = new Rotation3f(0.2f, 1f, Float.NaN);
        EntityRotationUtil.setBodyYaw(rotation, 2.5f);
        assertTrue(rotation.isFinite());
        assertEquals(0.2f, rotation.pitch(), 1e-5f);
        assertEquals(2.5f, rotation.yaw(), 1e-5f);
        assertEquals(0f, rotation.roll());
    }
}
