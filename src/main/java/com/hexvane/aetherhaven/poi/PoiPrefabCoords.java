package com.hexvane.aetherhaven.poi;

import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.PrefabLocalOffset;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Prefab-local POI coordinates from plot anchor + yaw, for debug labels. */
public final class PoiPrefabCoords {
    private PoiPrefabCoords() {}

    /**
     * @return local (lx, ly, lz) if plot and construction resolve; otherwise null
     */
    @Nullable
    public static Vector3i tryLocalFromWorld(
        @Nonnull PoiEntry poi,
        @Nonnull TownRecord town,
        @Nonnull ConstructionDefinition construction
    ) {
        if (poi.getPlotId() == null) {
            return null;
        }
        PlotInstance plot = town.findPlotById(poi.getPlotId());
        if (plot == null) {
            return null;
        }
        Vector3i anchor = plot.resolvePrefabAnchorWorld(construction);
        Rotation yaw = plot.resolvePrefabYaw();
        int dx = poi.getX() - anchor.x;
        int dy = poi.getY() - anchor.y;
        int dz = poi.getZ() - anchor.z;
        return PrefabLocalOffset.inverseRotateWorldDelta(yaw, dx, dy, dz);
    }

    /**
     * Prefab-local cell for an arbitrary world point (e.g. interaction target), using floored block coordinates.
     */
    @Nullable
    public static Vector3i tryLocalFromWorldPoint(
        double wx,
        double wy,
        double wz,
        @Nonnull PoiEntry poi,
        @Nonnull TownRecord town,
        @Nonnull ConstructionDefinition construction
    ) {
        if (poi.getPlotId() == null) {
            return null;
        }
        PlotInstance plot = town.findPlotById(poi.getPlotId());
        if (plot == null) {
            return null;
        }
        Vector3i anchor = plot.resolvePrefabAnchorWorld(construction);
        Rotation yaw = plot.resolvePrefabYaw();
        int dx = (int) Math.floor(wx) - anchor.x;
        int dy = (int) Math.floor(wy) - anchor.y;
        int dz = (int) Math.floor(wz) - anchor.z;
        return PrefabLocalOffset.inverseRotateWorldDelta(yaw, dx, dy, dz);
    }
}
