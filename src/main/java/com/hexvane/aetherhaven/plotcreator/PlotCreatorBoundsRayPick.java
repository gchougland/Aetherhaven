package com.hexvane.aetherhaven.plotcreator;

import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Place-at-range aim point for plot bounds (no world block collision). */
final class PlotCreatorBoundsRayPick {
    private PlotCreatorBoundsRayPick() {}

    @Nonnull
    static Vector3d aimPoint(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        Transform look = TargetUtil.getLook(ref, store);
        Vector3d eye = look.getPosition();
        Vector3d dir = look.getDirection();
        double len = dir.length();
        if (len < 1.0e-6) {
            return new Vector3d(eye);
        }
        dir.mul(1.0 / len);
        float distance = PlotCreatorBoundsConstants.DEFAULT_REACH;
        return new Vector3d(eye).fma(distance, dir);
    }

    @Nonnull
    static Vector3i aimCell(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        Vector3d aim = aimPoint(ref, store);
        return new Vector3i(floor(aim.x), floor(aim.y), floor(aim.z));
    }

    static int floor(double v) {
        int i = (int) Math.floor(v);
        return v < i ? i - 1 : i;
    }
}
