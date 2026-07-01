package com.hexvane.aetherhaven.map;

import com.hypixel.hytale.math.vector.Transform;
import javax.annotation.Nonnull;

/** Map-marker positions with finite rotation so creative map teleport never applies NaN body yaw. */
public final class MapMarkerTransforms {
    private MapMarkerTransforms() {}

    @Nonnull
    public static Transform at(double x, double y, double z) {
        return new Transform(x, y, z, 0f, 0f, 0f);
    }
}
