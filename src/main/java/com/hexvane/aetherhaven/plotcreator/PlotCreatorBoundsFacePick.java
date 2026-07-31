package com.hexvane.aetherhaven.plotcreator;

import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Ray vs axis-aligned face slabs for look-to-highlight during bounds face adjust. */
final class PlotCreatorBoundsFacePick {
    private static final double OUTSET = PlotCreatorBoundsConstants.FACE_PANEL_OUTSET;

    private PlotCreatorBoundsFacePick() {}

    @Nullable
    static PlotCreatorBoundsFace pick(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull Vector3i min,
        @Nonnull Vector3i max,
        double maxReach
    ) {
        Transform look = TargetUtil.getLook(ref, store);
        Vector3d origin = look.getPosition();
        Vector3d dir = look.getDirection();
        double len = dir.length();
        if (len < 1.0e-6) {
            return null;
        }
        dir.mul(1.0 / len);

        double bestT = Double.POSITIVE_INFINITY;
        PlotCreatorBoundsFace best = null;
        for (PlotCreatorBoundsFace face : PlotCreatorBoundsFace.values()) {
            Double t = rayHitFace(origin, dir, face, min, max, maxReach);
            if (t != null && t < bestT) {
                bestT = t;
                best = face;
            }
        }
        return best;
    }

    @Nullable
    private static Double rayHitFace(
        @Nonnull Vector3d origin,
        @Nonnull Vector3d dir,
        @Nonnull PlotCreatorBoundsFace face,
        @Nonnull Vector3i min,
        @Nonnull Vector3i max,
        double maxReach
    ) {
        double plane;
        int axis;
        double spanA0;
        double spanA1;
        double spanB0;
        double spanB1;
        switch (face) {
            case MIN_X -> {
                plane = min.x - OUTSET;
                axis = 0;
                spanA0 = min.y;
                spanA1 = max.y + 1.0;
                spanB0 = min.z;
                spanB1 = max.z + 1.0;
            }
            case MAX_X -> {
                plane = max.x + 1.0 + OUTSET;
                axis = 0;
                spanA0 = min.y;
                spanA1 = max.y + 1.0;
                spanB0 = min.z;
                spanB1 = max.z + 1.0;
            }
            case MIN_Y -> {
                plane = min.y - OUTSET;
                axis = 1;
                spanA0 = min.x;
                spanA1 = max.x + 1.0;
                spanB0 = min.z;
                spanB1 = max.z + 1.0;
            }
            case MAX_Y -> {
                plane = max.y + 1.0 + OUTSET;
                axis = 1;
                spanA0 = min.x;
                spanA1 = max.x + 1.0;
                spanB0 = min.z;
                spanB1 = max.z + 1.0;
            }
            case MIN_Z -> {
                plane = min.z - OUTSET;
                axis = 2;
                spanA0 = min.x;
                spanA1 = max.x + 1.0;
                spanB0 = min.y;
                spanB1 = max.y + 1.0;
            }
            case MAX_Z -> {
                plane = max.z + 1.0 + OUTSET;
                axis = 2;
                spanA0 = min.x;
                spanA1 = max.x + 1.0;
                spanB0 = min.y;
                spanB1 = max.y + 1.0;
            }
            default -> throw new IllegalStateException("Unexpected face: " + face);
        }
        double d = axisComponent(dir, axis);
        if (Math.abs(d) < 1.0e-9) {
            return null;
        }
        double t = (plane - axisComponent(origin, axis)) / d;
        if (t < 0.0 || t > maxReach) {
            return null;
        }
        double a;
        double b;
        if (axis == 0) {
            a = origin.y + dir.y * t;
            b = origin.z + dir.z * t;
        } else if (axis == 1) {
            a = origin.x + dir.x * t;
            b = origin.z + dir.z * t;
        } else {
            a = origin.x + dir.x * t;
            b = origin.y + dir.y * t;
        }
        if (a < spanA0 || a > spanA1 || b < spanB0 || b > spanB1) {
            return null;
        }
        return t;
    }

    private static double axisComponent(@Nonnull Vector3d v, int axis) {
        return switch (axis) {
            case 0 -> v.x;
            case 1 -> v.y;
            default -> v.z;
        };
    }
}
