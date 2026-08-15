package com.hexvane.aetherhaven.wall;

import org.joml.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.PrefabRotation;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** World XZ facing for wall chaining (Hytale: north = negative Z). */
public enum WallCardinal {
    NORTH(0, -1),
    EAST(1, 0),
    SOUTH(0, 1),
    WEST(-1, 0);

    public final int dx;
    public final int dz;

    WallCardinal(int dx, int dz) {
        this.dx = dx;
        this.dz = dz;
    }

    @Nonnull
    public WallCardinal opposite() {
        return switch (this) {
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case EAST -> WEST;
            case WEST -> EAST;
        };
    }

    /** 90° counter-clockwise on XZ (north → west). */
    @Nonnull
    public WallCardinal rotateCcw90() {
        return switch (this) {
            case NORTH -> WEST;
            case WEST -> SOUTH;
            case SOUTH -> EAST;
            case EAST -> NORTH;
        };
    }

    /** 90° clockwise on XZ (north → east). */
    @Nonnull
    public WallCardinal rotateCw90() {
        return switch (this) {
            case NORTH -> EAST;
            case EAST -> SOUTH;
            case SOUTH -> WEST;
            case WEST -> NORTH;
        };
    }

    /** World direction toward the top of the screen when the camera sits on {@code viewFromSide}. */
    @Nonnull
    public static WallCardinal screenUp(@Nonnull WallCardinal viewFromSide) {
        return viewFromSide.opposite();
    }

    @Nonnull
    public static WallCardinal screenDown(@Nonnull WallCardinal viewFromSide) {
        return viewFromSide;
    }

    @Nonnull
    public static WallCardinal screenLeft(@Nonnull WallCardinal viewFromSide) {
        return screenUp(viewFromSide).rotateCcw90();
    }

    @Nonnull
    public static WallCardinal screenRight(@Nonnull WallCardinal viewFromSide) {
        return screenUp(viewFromSide).rotateCw90();
    }

    @Nonnull
    public static WallCardinal fromExpandPad(@Nonnull String eventId, @Nonnull WallCardinal viewFromSide) {
        return switch (eventId) {
            case "ExpandZm" -> screenUp(viewFromSide);
            case "ExpandZp" -> screenDown(viewFromSide);
            case "ExpandXm" -> screenLeft(viewFromSide);
            case "ExpandXp" -> screenRight(viewFromSide);
            default -> NORTH;
        };
    }

    @Nullable
    public static WallCardinal fromTogglePad(@Nonnull String eventId, @Nonnull WallCardinal viewFromSide) {
        return switch (eventId) {
            case "ToggleConnN" -> screenUp(viewFromSide);
            case "ToggleConnS" -> screenDown(viewFromSide);
            case "ToggleConnE" -> screenRight(viewFromSide);
            case "ToggleConnW" -> screenLeft(viewFromSide);
            default -> null;
        };
    }

    /** UI selector for highlighting a world-direction toggle on the tower pad. */
    @Nonnull
    public static String togglePadSelector(@Nonnull WallCardinal worldDir, @Nonnull WallCardinal viewFromSide) {
        if (worldDir == screenUp(viewFromSide)) {
            return "#ToggleConnN";
        }
        if (worldDir == screenDown(viewFromSide)) {
            return "#ToggleConnS";
        }
        if (worldDir == screenRight(viewFromSide)) {
            return "#ToggleConnE";
        }
        return "#ToggleConnW";
    }

    @Nonnull
    public static String expandPadSelector(@Nonnull WallCardinal worldDir, @Nonnull WallCardinal viewFromSide) {
        if (worldDir == screenUp(viewFromSide)) {
            return "#BtnExpandZm";
        }
        if (worldDir == screenDown(viewFromSide)) {
            return "#BtnExpandZp";
        }
        if (worldDir == screenRight(viewFromSide)) {
            return "#BtnExpandXp";
        }
        return "#BtnExpandXm";
    }

    /** Unit vector for this direction on the XZ plane. */
    @Nonnull
    public Vector3i unitVector() {
        return new Vector3i(dx, 0, dz);
    }

    /** World direction a prefab local direction faces after {@code steps} 90 degree prefab rotations. */
    @Nonnull
    public static WallCardinal rotated(@Nonnull WallCardinal localDir, int steps) {
        Vector3i v = localDir.unitVector();
        PrefabRotation.fromRotation(prefabYawFromSteps(steps)).rotate(v);
        WallCardinal out = fromVector(new Vector3i(0, 0, 0), v);
        return out != null ? out : localDir;
    }

    /** Rotation steps that make {@code localDir} face {@code worldDir}. */
    public static int stepsAligning(@Nonnull WallCardinal localDir, @Nonnull WallCardinal worldDir) {
        for (int steps = 0; steps < 4; steps++) {
            if (rotated(localDir, steps) == worldDir) {
                return steps;
            }
        }
        return 0;
    }

    @Nonnull
    public static Rotation prefabYawFromSteps(int steps) {
        return switch ((steps % 4 + 4) % 4) {
            case 1 -> Rotation.Ninety;
            case 2 -> Rotation.OneEighty;
            case 3 -> Rotation.TwoSeventy;
            default -> Rotation.None;
        };
    }

    /** Rotates a prefab local offset into world space for {@code steps} 90 degree prefab rotations. */
    @Nonnull
    public static Vector3i rotateOffset(@Nonnull Vector3i local, int steps) {
        Vector3i copy = new Vector3i(local);
        PrefabRotation.fromRotation(prefabYawFromSteps(steps)).rotate(copy);
        return copy;
    }

    @Nonnull
    public static WallCardinal fromRotationSteps(int steps) {
        return switch ((steps % 4 + 4) % 4) {
            case 1 -> EAST;
            case 2 -> SOUTH;
            case 3 -> WEST;
            default -> NORTH;
        };
    }

    /** Dominant horizontal direction from {@code from} to {@code to} (zero vector → null). */
    @Nullable
    public static WallCardinal fromVector(@Nonnull Vector3i from, @Nonnull Vector3i to) {
        int dx = to.x - from.x;
        int dz = to.z - from.z;
        if (dx == 0 && dz == 0) {
            return null;
        }
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx > 0 ? EAST : WEST;
        }
        return dz > 0 ? SOUTH : NORTH;
    }
}
