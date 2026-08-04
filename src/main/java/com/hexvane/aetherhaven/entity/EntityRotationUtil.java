package com.hexvane.aetherhaven.entity;

import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Rotation3fc;
import javax.annotation.Nonnull;

/** Repairs non-finite entity rotations from partial codec loads or yaw-only updates. */
public final class EntityRotationUtil {
    private EntityRotationUtil() {}

    public static boolean needsRepair(@Nonnull Rotation3fc rotation) {
        return !Float.isFinite(rotation.pitch())
            || !Float.isFinite(rotation.yaw())
            || !Float.isFinite(rotation.roll());
    }

    /**
     * @return {@code true} if any axis was changed
     */
    public static boolean repairInPlace(@Nonnull Rotation3f rotation) {
        if (!needsRepair(rotation)) {
            return false;
        }
        rotation.setPitch(finiteOrZero(rotation.pitch()));
        rotation.setYaw(finiteOrZeroYaw(rotation.yaw()));
        rotation.setRoll(finiteOrZero(rotation.roll()));
        return true;
    }

    @Nonnull
    public static Rotation3f repair(@Nonnull Rotation3fc rotation) {
        return new Rotation3f(
            finiteOrZero(rotation.pitch()),
            finiteOrZeroYaw(rotation.yaw()),
            finiteOrZero(rotation.roll())
        );
    }

    /** Sets body yaw and clears any non-finite pitch or roll left by partial rotation updates. */
    public static void setBodyYaw(@Nonnull Rotation3f rotation, float yaw) {
        rotation.setYaw(finiteOrZeroYaw(yaw));
        if (!Float.isFinite(rotation.pitch())) {
            rotation.setPitch(0f);
        }
        if (!Float.isFinite(rotation.roll())) {
            rotation.setRoll(0f);
        }
    }

    private static float finiteOrZero(float value) {
        return Float.isFinite(value) ? value : 0f;
    }

    private static float finiteOrZeroYaw(float yaw) {
        if (!Float.isFinite(yaw)) {
            return 0f;
        }
        return MathUtil.wrapAngle(yaw);
    }
}
