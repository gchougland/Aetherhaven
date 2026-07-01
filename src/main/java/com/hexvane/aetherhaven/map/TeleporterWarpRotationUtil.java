package com.hexvane.aetherhaven.map;

import com.hypixel.hytale.builtin.teleport.TeleportPlugin;
import com.hypixel.hytale.builtin.teleport.Warp;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

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
        Rotation3f repaired = repairRotation(rotation);
        return !approximatelyEqual(rotation.pitch(), repaired.pitch())
            || !approximatelyEqual(rotation.yaw(), repaired.yaw())
            || !approximatelyEqual(rotation.roll(), repaired.roll());
    }

    /**
     * @return {@code true} if the warp was updated in {@link TeleportPlugin}
     */
    public static boolean repairWarpIfNeeded(@Nonnull Warp warp) {
        Transform transform = warp.getTransform();
        if (transform == null) {
            return false;
        }
        Rotation3f rotation = transform.getRotation();
        if (!rotationNeedsRepair(rotation)) {
            return false;
        }
        World world = Universe.get().getWorld(warp.getWorld());
        if (world == null) {
            return false;
        }
        Rotation3f repaired = repairRotation(rotation);
        Transform fixedTransform = new Transform(new Vector3d(transform.getPosition()), repaired);
        Warp fixed = new Warp(fixedTransform, warp.getId(), world, warp.getCreator(), warp.getCreationDate());
        TeleportPlugin teleport = TeleportPlugin.get();
        if (teleport == null) {
            return false;
        }
        return teleport.addWarp(fixed, true);
    }

    @Nullable
    public static Warp repairedWarp(@Nonnull Warp warp) {
        Transform transform = warp.getTransform();
        if (transform == null || !rotationNeedsRepair(transform.getRotation())) {
            return null;
        }
        World world = Universe.get().getWorld(warp.getWorld());
        if (world == null) {
            return null;
        }
        Transform fixedTransform =
            new Transform(new Vector3d(transform.getPosition()), repairRotation(transform.getRotation()));
        return new Warp(fixedTransform, warp.getId(), world, warp.getCreator(), warp.getCreationDate());
    }

    private static boolean approximatelyEqual(float a, float b) {
        if (Float.isNaN(a) && Float.isNaN(b)) {
            return true;
        }
        return a == b;
    }
}
