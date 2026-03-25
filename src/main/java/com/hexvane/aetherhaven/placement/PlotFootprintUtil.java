package com.hexvane.aetherhaven.placement;

import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hypixel.hytale.math.util.FastRandom;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.PrefabRotation;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferCall;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import java.util.Random;
import javax.annotation.Nonnull;

public final class PlotFootprintUtil {
    private PlotFootprintUtil() {}

    /**
     * Axis-aligned bounds in world space for all non-air prefab voxels. {@code origin} is the prefab (0,0,0) corner in
     * world space, same as {@link com.hexvane.aetherhaven.prefab.ConstructionAnimator} (sign position + plot anchor offset).
     * {@link IPrefabBuffer#forEach} with {@link PrefabBufferCall} already applies placement yaw to x/z and per-block rotation.
     */
    @Nonnull
    public static PlotFootprintRecord computeFootprint(@Nonnull Vector3i origin, @Nonnull Rotation yaw, @Nonnull IPrefabBuffer buffer) {
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
}
