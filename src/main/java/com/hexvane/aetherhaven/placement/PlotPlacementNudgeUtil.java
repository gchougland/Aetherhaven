package com.hexvane.aetherhaven.placement;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/**
 * Horizontal plot nudges: in bird's-eye mode uses world -X/+X/-Z/+Z (matches fixed top-down camera).
 * In first-person, the same four buttons follow camera forward/back/left/right on the XZ plane.
 */
public final class PlotPlacementNudgeUtil {

    public enum Horizontal {
        /** Top pad: -Z world, or camera forward when not bird's-eye. */
        NEG_Z,
        /** Bottom pad: +Z world, or camera back. */
        POS_Z,
        /** Left pad: -X world, or camera strafe left. */
        NEG_X,
        /** Right pad: +X world, or camera strafe right. */
        POS_X
    }

    private PlotPlacementNudgeUtil() {}

    /** Horizontal rotation (radians) on XZ for camera-relative nudges. */
    public static float getPlayerYawRadians(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc == null) {
            return 0f;
        }
        return tc.getRotation().yaw();
    }

    public static void nudgeHorizontal(
        @Nonnull PlotPlacementSession session, boolean birdsEye, float yawRadians, @Nonnull Horizontal kind
    ) {
        int[] step = horizontalStep(birdsEye, yawRadians, kind);
        session.nudge(step[0], 0, step[1]);
    }

    /**
     * One-block XZ step for a D-pad direction. When {@code birdsEye} is false, buttons follow the player's look
     * (forward / back / left / right on the ground plane).
     *
     * @return {@code {dx, dz}}
     */
    @Nonnull
    public static int[] horizontalStep(boolean birdsEye, float yawRadians, @Nonnull Horizontal kind) {
        if (birdsEye) {
            return switch (kind) {
                case NEG_Z -> new int[] {0, -1};
                case POS_Z -> new int[] {0, 1};
                case NEG_X -> new int[] {-1, 0};
                case POS_X -> new int[] {1, 0};
            };
        }
        double sin = Math.sin(yawRadians);
        double cos = Math.cos(yawRadians);
        return switch (kind) {
            case NEG_Z -> dominantUnitXZ(-sin, -cos);
            case POS_Z -> dominantUnitXZ(sin, cos);
            case NEG_X -> dominantUnitXZ(-cos, sin);
            case POS_X -> dominantUnitXZ(cos, -sin);
        };
    }

    /** Same semantics for charter relocation (ghost block). */
    public static void nudgeCharterHorizontal(
        @Nonnull CharterRelocationSession session, boolean birdsEye, float yawRadians, @Nonnull Horizontal kind
    ) {
        int[] step = horizontalStep(birdsEye, yawRadians, kind);
        session.nudge(step[0], 0, step[1]);
    }

    /**
     * Picks one block step along +X/-X or +Z/-Z from a direction vector in the XZ plane (larger magnitude wins).
     */
    @Nonnull
    private static int[] dominantUnitXZ(double fx, double fz) {
        if (Math.abs(fx) >= Math.abs(fz)) {
            if (fx > 1e-6) {
                return new int[] {1, 0};
            }
            if (fx < -1e-6) {
                return new int[] {-1, 0};
            }
            return new int[] {0, 0};
        }
        if (fz > 1e-6) {
            return new int[] {0, 1};
        }
        if (fz < -1e-6) {
            return new int[] {0, -1};
        }
        return new int[] {0, 0};
    }
}
