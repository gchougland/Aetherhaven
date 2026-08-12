package com.hexvane.aetherhaven.festival;

import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.PrefabRotation;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/**
 * Every festival prefab shares one footprint so the festival square plot can swap between them without moving the plot
 * sign or re-validating overlap. The reserved volume is always 30 x 55 x 30 in prefab-local cells
 * ({@link #LOCAL_MIN_X}..{@link #LOCAL_MAX_X}, etc.). Solid content may omit empty air; swaps clear the full box.
 */
public final class FestivalPrefabSize {
    public static final int WIDTH_X = 30;
    public static final int HEIGHT_Y = 55;
    public static final int DEPTH_Z = 30;

    /** Inclusive span between min and max corners (one less than the block count on each axis). */
    public static final int SPAN_X = WIDTH_X - 1;
    public static final int SPAN_Y = HEIGHT_Y - 1;
    public static final int SPAN_Z = DEPTH_Z - 1;

    /** Prefab-local reserved box (matches shipped festival prefabs). */
    public static final int LOCAL_MIN_X = -14;
    public static final int LOCAL_MIN_Y = 0;
    public static final int LOCAL_MIN_Z = -14;
    public static final int LOCAL_MAX_X = LOCAL_MIN_X + SPAN_X;
    public static final int LOCAL_MAX_Y = LOCAL_MIN_Y + SPAN_Y;
    public static final int LOCAL_MAX_Z = LOCAL_MIN_Z + SPAN_Z;

    private FestivalPrefabSize() {}

    public static boolean matches(int sizeX, int sizeY, int sizeZ) {
        return sizeX == WIDTH_X && sizeY == HEIGHT_Y && sizeZ == DEPTH_Z;
    }

    /** Expands {@code min} into the fixed festival volume so bounds cannot be resized by the player. */
    @Nonnull
    public static Vector3i maxFromMin(@Nonnull Vector3i min) {
        return new Vector3i(min.x + SPAN_X, min.y + SPAN_Y, min.z + SPAN_Z);
    }

    /**
     * World AABB of the reserved festival volume at {@code anchor} with {@code yaw}, using the fixed local box (not
     * the solid content AABB of an airless prefab).
     */
    @Nonnull
    public static PlotFootprintRecord footprintAt(@Nonnull Vector3i anchor, @Nonnull Rotation yaw) {
        PrefabRotation rotation = PrefabRotation.fromRotation(yaw);
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int x : new int[] { LOCAL_MIN_X, LOCAL_MAX_X }) {
            for (int y : new int[] { LOCAL_MIN_Y, LOCAL_MAX_Y }) {
                for (int z : new int[] { LOCAL_MIN_Z, LOCAL_MAX_Z }) {
                    Vector3i corner = new Vector3i(x, y, z);
                    rotation.rotate(corner);
                    minX = Math.min(minX, anchor.x + corner.x);
                    minY = Math.min(minY, anchor.y + corner.y);
                    minZ = Math.min(minZ, anchor.z + corner.z);
                    maxX = Math.max(maxX, anchor.x + corner.x);
                    maxY = Math.max(maxY, anchor.y + corner.y);
                    maxZ = Math.max(maxZ, anchor.z + corner.z);
                }
            }
        }
        return new PlotFootprintRecord(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Nonnull
    public static String describe() {
        return WIDTH_X + " x " + HEIGHT_Y + " x " + DEPTH_Z;
    }

    /** True for festival activity prefabs and the everyday festival square prefab. */
    public static boolean usesReservedFootprint(@Nullable String prefabPathKey) {
        if (prefabPathKey == null || prefabPathKey.isBlank()) {
            return false;
        }
        String key = prefabPathKey.trim().replace('\\', '/');
        return key.contains("/Festivals/")
            || key.startsWith("Festivals/")
            || key.endsWith("Festival_Square.prefab.json");
    }

    /**
     * True when buffer content stays inside the reserved local box. Airless prefabs are smaller than the full
     * reserved size; that is expected.
     */
    @Nullable
    public static String contentOutsideReservedReason(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (minX < LOCAL_MIN_X
            || maxX > LOCAL_MAX_X
            || minY < LOCAL_MIN_Y
            || maxY > LOCAL_MAX_Y
            || minZ < LOCAL_MIN_Z
            || maxZ > LOCAL_MAX_Z) {
            return "content outside reserved " + describe() + " local box";
        }
        return null;
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
