package com.hexvane.aetherhaven.placement;

import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.pathtool.PathGrounding;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.World;
import javax.annotation.Nonnull;

/**
 * Keeps plot sign XZ from the placement preview, snaps sign Y to terrain under the prefab footprint, and falls back to
 * the footprint floor when no surface is found (e.g. fully underground).
 */
public final class PlotSignGrounding {
    private static final int MAX_RAY_DOWN = 512;

    private PlotSignGrounding() {}

    /**
     * @param anchorPreview session anchor (XZ and yaw from preview; preview Y is only used when raycast fails)
     * @return world cell for the plot sign block (one block above solid surface when found)
     */
    @Nonnull
    public static Vector3i resolveSignCell(
        @Nonnull World world,
        @Nonnull Vector3i anchorPreview,
        @Nonnull ConstructionDefinition def,
        @Nonnull Rotation prefabYaw,
        @Nonnull IPrefabBuffer buf
    ) {
        int sx = anchorPreview.x;
        int sz = anchorPreview.z;
        Vector3i prefabOrigin = def.resolvePrefabAnchorWorld(anchorPreview, prefabYaw);
        PlotFootprintRecord fp = PlotFootprintUtil.computeFootprint(prefabOrigin, prefabYaw, buf);
        int startY = fp.getMaxY();
        Integer support = PathGrounding.findSupportY(world, sx, sz, startY, MAX_RAY_DOWN, 1);
        int signY;
        if (support != null) {
            signY = Math.min(318, support + 1);
        } else {
            signY = fp.getMinY();
        }
        signY = Math.max(1, Math.min(318, signY));
        return new Vector3i(sx, signY, sz);
    }
}
