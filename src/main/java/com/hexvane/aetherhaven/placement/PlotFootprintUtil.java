package com.hexvane.aetherhaven.placement;

import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.festival.FestivalPrefabSize;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hypixel.hytale.math.util.FastRandom;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.PrefabRotation;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferCall;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import java.util.Random;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PlotFootprintUtil {
    private PlotFootprintUtil() {}

    /**
     * Axis-aligned bounds in world space for all non-air prefab voxels. {@code origin} is the prefab (0,0,0) corner in
     * world space, same as {@link com.hexvane.aetherhaven.prefab.ConstructionAnimator} (sign position + world-axis
     * {@code plotAnchorOffset} from the construction definition in {@code Server/Aetherhaven/Buildings/}).
     * {@link IPrefabBuffer#forEach} with {@link PrefabBufferCall} already applies placement yaw to x/z and per-block rotation.
     */
    @Nonnull
    public static PlotFootprintRecord computeFootprint(@Nonnull Vector3i origin, @Nonnull Rotation yaw, @Nonnull IPrefabBuffer buffer) {
        return computeFootprint(origin, yaw, buffer, (String) null);
    }

    @Nonnull
    public static PlotFootprintRecord computeFootprint(
        @Nonnull Vector3i origin,
        @Nonnull Rotation yaw,
        @Nonnull IPrefabBuffer buffer,
        @Nullable ConstructionDefinition def
    ) {
        if (def != null) {
            if (FestivalPrefabSize.usesReservedFootprint(def)) {
                return FestivalPrefabSize.footprintAt(origin, yaw);
            }
            PlotFootprintRecord authored = footprintFromBoundsLocal(origin, yaw, def);
            if (authored != null) {
                return authored;
            }
            return computeFootprint(origin, yaw, buffer, (String) null);
        }
        return computeFootprint(origin, yaw, buffer, (String) null);
    }

    /**
     * Like {@link #computeFootprint(Vector3i, Rotation, IPrefabBuffer)} but festival square / festival activity prefabs
     * always use the fixed {@link FestivalPrefabSize} reserved box (they omit empty air in the file).
     */
    @Nonnull
    public static PlotFootprintRecord computeFootprint(
        @Nonnull Vector3i origin,
        @Nonnull Rotation yaw,
        @Nonnull IPrefabBuffer buffer,
        @Nullable String prefabPathKey
    ) {
        if (FestivalPrefabSize.usesReservedFootprint(prefabPathKey)) {
            return FestivalPrefabSize.footprintAt(origin, yaw);
        }
        PrefabRotation pr = PrefabRotation.fromRotation(yaw);
        Random random = new FastRandom();
        PrefabBufferCall call = new PrefabBufferCall(random, pr);
        final int[] b = {
            Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE
        };
        buffer.forEach(
            IPrefabBuffer.iterateAllColumns(),
            (x, y, z, blockId, holder, supportValue, blockRotation, filler, t, fluidId, fluidLevel) -> {
                if (filler != 0 || blockId == 0) {
                    return;
                }
                int wx = origin.x + x;
                int wy = origin.y + y;
                int wz = origin.z + z;
                b[0] = Math.min(b[0], wx);
                b[1] = Math.min(b[1], wy);
                b[2] = Math.min(b[2], wz);
                b[3] = Math.max(b[3], wx);
                b[4] = Math.max(b[4], wy);
                b[5] = Math.max(b[5], wz);
            },
            null,
            null,
            call
        );
        if (b[0] == Integer.MAX_VALUE) {
            return new PlotFootprintRecord(origin.x, origin.y, origin.z, origin.x, origin.y, origin.z);
        }
        return new PlotFootprintRecord(b[0], b[1], b[2], b[3], b[4], b[5]);
    }

    /**
     * World AABB of an authored {@code boundsLocal} box at {@code origin} with {@code yaw}. Null when the definition
     * has no stored bounds.
     */
    @Nullable
    public static PlotFootprintRecord footprintFromBoundsLocal(
        @Nonnull Vector3i origin,
        @Nonnull Rotation yaw,
        @Nonnull ConstructionDefinition def
    ) {
        Vector3i localMin = def.getBoundsLocalMin();
        Vector3i localMax = def.getBoundsLocalMax();
        if (localMin == null || localMax == null) {
            return null;
        }
        return footprintFromLocalBox(origin, yaw, localMin, localMax);
    }

    /** World AABB of an inclusive prefab-local box at {@code origin} with placement {@code yaw}. */
    @Nonnull
    public static PlotFootprintRecord footprintFromLocalBox(
        @Nonnull Vector3i origin,
        @Nonnull Rotation yaw,
        @Nonnull Vector3i localMin,
        @Nonnull Vector3i localMax
    ) {
        PrefabRotation rotation = PrefabRotation.fromRotation(yaw);
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int x : new int[] {localMin.x, localMax.x}) {
            for (int y : new int[] {localMin.y, localMax.y}) {
                for (int z : new int[] {localMin.z, localMax.z}) {
                    Vector3i corner = new Vector3i(x, y, z);
                    rotation.rotate(corner);
                    minX = Math.min(minX, origin.x + corner.x);
                    minY = Math.min(minY, origin.y + corner.y);
                    minZ = Math.min(minZ, origin.z + corner.z);
                    maxX = Math.max(maxX, origin.x + corner.x);
                    maxY = Math.max(maxY, origin.y + corner.y);
                    maxZ = Math.max(maxZ, origin.z + corner.z);
                }
            }
        }
        return new PlotFootprintRecord(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public static boolean hasSolidVoxels(@Nonnull Rotation yaw, @Nonnull IPrefabBuffer buffer) {
        PrefabRotation pr = PrefabRotation.fromRotation(yaw);
        Random random = new FastRandom();
        PrefabBufferCall call = new PrefabBufferCall(random, pr);
        final boolean[] any = { false };
        buffer.forEach(
            IPrefabBuffer.iterateAllColumns(),
            (x, y, z, blockId, holder, supportValue, blockRotation, filler, t, fluidId, fluidLevel) -> {
                if (filler == 0 && blockId != 0) {
                    any[0] = true;
                }
            },
            null,
            null,
            call
        );
        return any[0];
    }
}
