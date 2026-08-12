package com.hexvane.aetherhaven.festival;

import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Validates festival prefab content against the reserved {@link FestivalPrefabSize} volume. Festival prefabs no longer
 * store empty air for the full box; swaps clear the reserved volume explicitly, so solid AABBs are usually smaller
 * than 30 x 55 x 30.
 */
public final class FestivalPrefabMetrics {
    private FestivalPrefabMetrics() {}

    public record Size(int x, int y, int z) {}

    /** @return solid content size of the prefab buffer, or null when the prefab is not loaded yet */
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

    /** @return a warning message when loaded content extends outside the reserved festival box, otherwise null */
    @Nullable
    public static String validateFestivalSize(@Nullable String prefabPathKey) {
        if (prefabPathKey == null || prefabPathKey.isBlank()) {
            return null;
        }
        IPrefabBuffer buffer = PrefabResolveUtil.resolvePrefabBuffer(prefabPathKey);
        if (buffer == null) {
            return null;
        }
        return FestivalPrefabSize.contentOutsideReservedReason(
            buffer.getMinX(),
            buffer.getMinY(),
            buffer.getMinZ(),
            buffer.getMaxX(),
            buffer.getMaxY(),
            buffer.getMaxZ()
        );
    }
}
