package com.hexvane.aetherhaven.festival;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/**
 * Every festival prefab shares one footprint so the festival square plot can swap between them without moving the plot
 * sign or re-validating overlap. The shipped {@code Server/Prefabs/Festivals/} prefabs all measure 30 x 55 x 30.
 */
public final class FestivalPrefabSize {
    public static final int WIDTH_X = 30;
    public static final int HEIGHT_Y = 55;
    public static final int DEPTH_Z = 30;

    /** Inclusive span between min and max corners (one less than the block count on each axis). */
    public static final int SPAN_X = WIDTH_X - 1;
    public static final int SPAN_Y = HEIGHT_Y - 1;
    public static final int SPAN_Z = DEPTH_Z - 1;

    private FestivalPrefabSize() {}

    public static boolean matches(int sizeX, int sizeY, int sizeZ) {
        return sizeX == WIDTH_X && sizeY == HEIGHT_Y && sizeZ == DEPTH_Z;
    }

    /** Expands {@code min} into the fixed festival volume so bounds cannot be resized by the player. */
    @Nonnull
    public static Vector3i maxFromMin(@Nonnull Vector3i min) {
        return new Vector3i(min.x + SPAN_X, min.y + SPAN_Y, min.z + SPAN_Z);
    }

    @Nonnull
    public static String describe() {
        return WIDTH_X + " x " + HEIGHT_Y + " x " + DEPTH_Z;
    }

    /** Human readable mismatch reason for load-time warnings, or null when the size is correct. */
    @Nullable
    public static String mismatchReason(int sizeX, int sizeY, int sizeZ) {
        if (matches(sizeX, sizeY, sizeZ)) {
            return null;
        }
        return "expected " + describe() + " but prefab is " + sizeX + " x " + sizeY + " x " + sizeZ;
    }
}
