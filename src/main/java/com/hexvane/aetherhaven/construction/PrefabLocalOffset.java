package com.hexvane.aetherhaven.construction;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.PrefabRotation;
import javax.annotation.Nonnull;

/** Rotates prefab-local integer offsets the same way prefab voxels are oriented at paste time. */
public final class PrefabLocalOffset {
    private PrefabLocalOffset() {}

    @Nonnull
    public static Vector3i rotate(@Nonnull Rotation yaw, int lx, int ly, int lz) {
        PrefabRotation pr = PrefabRotation.fromRotation(yaw);
        Vector3d p = new Vector3d(lx, ly, lz);
        pr.rotate(p);
        return new Vector3i((int) Math.round(p.x), (int) Math.round(p.y), (int) Math.round(p.z));
    }

    /**
     * Inverse of {@link #rotate} for horizontal prefab yaw (Y unchanged). World delta from anchor to POI cell
     * → prefab-local cell offset.
     */
    @Nonnull
    public static Vector3i inverseRotateWorldDelta(@Nonnull Rotation yaw, int dx, int dy, int dz) {
        PrefabRotation forward = PrefabRotation.fromRotation(yaw);
        PrefabRotation inverse = inverseHorizontal(forward);
        Vector3d p = new Vector3d(dx, dy, dz);
        inverse.rotate(p);
        return new Vector3i((int) Math.round(p.x), (int) Math.round(p.y), (int) Math.round(p.z));
    }

    @Nonnull
    private static PrefabRotation inverseHorizontal(@Nonnull PrefabRotation pr) {
        return switch (pr) {
            case ROTATION_0 -> PrefabRotation.ROTATION_0;
            case ROTATION_90 -> PrefabRotation.ROTATION_270;
            case ROTATION_180 -> PrefabRotation.ROTATION_180;
            case ROTATION_270 -> PrefabRotation.ROTATION_90;
        };
    }
}
