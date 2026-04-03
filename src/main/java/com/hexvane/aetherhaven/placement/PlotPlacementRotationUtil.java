package com.hexvane.aetherhaven.placement;

import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import javax.annotation.Nonnull;

/**
 * Prefab preview rotates around the prefab buffer origin; shifting the plot sign after each 90° step keeps the
 * axis-aligned footprint center fixed in world space.
 */
public final class PlotPlacementRotationUtil {
    private PlotPlacementRotationUtil() {}

    /**
     * Footprint center in world space when the plot sign block is at (0,0,0) for {@code yaw}. Shifting the sign by
     * {@code S} shifts the footprint by {@code S}, so center(sign, yaw) = sign + centerAtSignOrigin(yaw).
     */
    @Nonnull
    public static Vector3d footprintCenterAtSignOrigin(
        @Nonnull ConstructionDefinition def, @Nonnull Rotation yaw, @Nonnull IPrefabBuffer buf
    ) {
        Vector3i prefabOrigin = def.resolvePrefabAnchorWorld(new Vector3i(0, 0, 0), yaw);
        PlotFootprintRecord fp = PlotFootprintUtil.computeFootprint(prefabOrigin, yaw, buf);
        return new Vector3d(
            (fp.getMinX() + fp.getMaxX() + 1) * 0.5,
            (fp.getMinY() + fp.getMaxY() + 1) * 0.5,
            (fp.getMinZ() + fp.getMaxZ() + 1) * 0.5
        );
    }

    /**
     * Advances rotation one step clockwise and adjusts the session anchor so the footprint center stays in place.
     */
    public static void rotateClockwise90PreservingFootprintCenter(
        @Nonnull PlotPlacementSession session, @Nonnull ConstructionDefinition def, @Nonnull IPrefabBuffer buf
    ) {
        Rotation oldYaw = session.getPrefabYaw();
        Vector3d k0 = footprintCenterAtSignOrigin(def, oldYaw, buf);
        Vector3i sign0 = session.getAnchor();
        session.rotateClockwise90();
        Rotation newYaw = session.getPrefabYaw();
        Vector3d k1 = footprintCenterAtSignOrigin(def, newYaw, buf);
        double x = sign0.x + k0.x - k1.x;
        double y = sign0.y + k0.y - k1.y;
        double z = sign0.z + k0.z - k1.z;
        session.setAnchor(new Vector3i((int) Math.round(x), (int) Math.round(y), (int) Math.round(z)));
    }
}
