package com.hexvane.aetherhaven.placement;

import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/** Resolves world prefab paste origin for in-place reconstruct without catalog sign-offset drift. */
public final class PlotInPlacePrefabAnchor {
    private PlotInPlacePrefabAnchor() {}

    /**
     * @return paste origin for {@link PlotReconstructService}, or null when it cannot be determined safely
     */
    @Nullable
    public static Vector3i resolveInPlaceAnchor(
        @Nonnull PlotInstance plot,
        @Nonnull Rotation yaw,
        @Nonnull IPrefabBuffer buffer,
        @Nonnull PlotFootprintRecord footprintBeforeClear
    ) {
        if (plot.hasStoredPrefabWorldAnchor()) {
            return plot.getStoredPrefabWorldAnchor();
        }
        return inferFromFootprint(footprintBeforeClear, yaw, buffer);
    }

    @Nullable
    private static Vector3i inferFromFootprint(
        @Nonnull PlotFootprintRecord footprint,
        @Nonnull Rotation yaw,
        @Nonnull IPrefabBuffer buffer
    ) {
        if (!PlotFootprintUtil.hasSolidVoxels(yaw, buffer)) {
            return null;
        }
        Vector3i zero = new Vector3i(0, 0, 0);
        PlotFootprintRecord atOrigin = PlotFootprintUtil.computeFootprint(zero, yaw, buffer);
        return new Vector3i(
            footprint.getMinX() - atOrigin.getMinX(),
            footprint.getMinY() - atOrigin.getMinY(),
            footprint.getMinZ() - atOrigin.getMinZ()
        );
    }
}
