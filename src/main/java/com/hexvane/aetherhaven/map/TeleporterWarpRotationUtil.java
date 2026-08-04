package com.hexvane.aetherhaven.map;

import com.hexvane.aetherhaven.entity.EntityRotationUtil;
import com.hypixel.hytale.builtin.adventure.teleporter.component.Teleporter;
import com.hypixel.hytale.builtin.teleport.TeleportPlugin;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Transform;
import javax.annotation.Nonnull;

/** Repairs teleporter warp rotations that use partial NaN axes or runaway yaw. */
public final class TeleporterWarpRotationUtil {
    private TeleporterWarpRotationUtil() {}

    /**
     * @return finite rotation with pitch/roll zero and yaw wrapped to {@code [-π, π]}
     */
    @Nonnull
    public static Rotation3f repairRotation(@Nonnull Rotation3f rotation) {
        float yaw = rotation.yaw();
        if (!Float.isFinite(yaw)) {
            yaw = 0f;
        } else {
            yaw = MathUtil.wrapAngle(yaw);
        }
        return new Rotation3f(0f, yaw, 0f);
    }

    public static boolean rotationNeedsRepair(@Nonnull Rotation3f rotation) {
        if (EntityRotationUtil.needsRepair(rotation)) {
            return true;
        }
        Rotation3f repaired = repairRotation(rotation);
        return !approximatelyEqual(rotation.pitch(), repaired.pitch())
            || !approximatelyEqual(rotation.yaw(), repaired.yaw())
            || !approximatelyEqual(rotation.roll(), repaired.roll());
    }

    /**
     * @return {@code true} if the warp was updated in {@link TeleportPlugin}
     */
    public static boolean repairWarpIfNeeded(@Nonnull String warpId) {
        TeleportPlugin teleport = TeleportPlugin.get();
        if (teleport == null) {
            return false;
        }
        var warp = teleport.getWarps().get(warpId.toLowerCase());
        if (warp == null || !Teleporter.CREATOR_IDENTIFIER.equals(warp.getCreator())) {
            return false;
        }
        Transform transform = warp.getTransform();
        if (transform == null || !rotationNeedsRepair(transform.getRotation())) {
            return false;
        }
        applyRepairedRotation(transform.getRotation());
        return teleport.addWarp(warp, true);
    }

    private static void applyRepairedRotation(@Nonnull Rotation3f rotation) {
        Rotation3f repaired = repairRotation(rotation);
        rotation.x = repaired.pitch();
        rotation.y = repaired.yaw();
        rotation.z = repaired.roll();
    }

    private static boolean approximatelyEqual(float a, float b) {
        if (Float.isNaN(a) && Float.isNaN(b)) {
            return true;
        }
        return a == b;
    }
}
