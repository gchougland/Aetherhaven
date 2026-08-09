package com.hexvane.aetherhaven.festival;

import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Reads the volume a prefab reserves so festival prefabs can be checked against {@link FestivalPrefabSize}. Uses the
 * buffer extents rather than the solid footprint: festival prefabs are saved with their empty cells so the swap clears
 * whatever the previous festival left behind, and every festival must reserve the same box even though the built
 * content is different heights.
 */
public final class FestivalPrefabMetrics {
    private FestivalPrefabMetrics() {}

    public record Size(int x, int y, int z) {}

    /** @return reserved prefab volume, or null when the prefab is not loaded yet */
    @Nullable
    public static Size sizeOf(@Nullable String prefabPathKey) {
        if (prefabPathKey == null || prefabPathKey.isBlank()) {
            return null;
        }
        IPrefabBuffer buffer = PrefabResolveUtil.resolvePrefabBuffer(prefabPathKey);
        return buffer != null ? sizeOf(buffer) : null;
    }

    @Nonnull
    public static Size sizeOf(@Nonnull IPrefabBuffer buffer) {
        return new Size(
            buffer.getMaxX() - buffer.getMinX() + 1,
            buffer.getMaxY() - buffer.getMinY() + 1,
            buffer.getMaxZ() - buffer.getMinZ() + 1
        );
    }

    /** @return a warning message when the prefab is loaded and the wrong size, otherwise null */
    @Nullable
    public static String validateFestivalSize(@Nullable String prefabPathKey) {
        Size size = sizeOf(prefabPathKey);
        if (size == null) {
            return null;
        }
        return FestivalPrefabSize.mismatchReason(size.x(), size.y(), size.z());
    }
}
