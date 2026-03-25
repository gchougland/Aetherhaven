package com.hexvane.aetherhaven.autonomy;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Horizontal steps toward a POI with optional ground height sampling from the world (avoids skating through walls
 * when combined with {@link VillagerPoiPathfinder} waypoints).
 */
public final class VillagerPoiMovement {
    private VillagerPoiMovement() {}

    /**
     * Steps horizontally toward ({@code tx}, {@code tz}), updates yaw to face the target.
     * When {@code world} is non-null, Y is derived from {@link VillagerPoiPathfinder#findStandY} under the entity;
     * otherwise Y is set to {@code ty}.
     *
     * @return true if within {@code reachSq} (caller should stop stepping)
     */
    public static boolean stepHorizontalToward(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        double tx,
        double ty,
        double tz,
        @Nullable World world,
        double blocksPerSecond,
        float dt,
        double reachSq
    ) {
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc == null) {
            return false;
        }
        Vector3d pos = tc.getPosition();
        double dx = tx - pos.x;
        double dz = tz - pos.z;
        double distSq = dx * dx + dz * dz;
        if (distSq <= reachSq) {
            applyGroundY(world, pos, ty);
            tc.getRotation().setYaw(bodyYawAlongMove(dx, dz));
            commandBuffer.putComponent(ref, TransformComponent.getComponentType(), tc);
            return true;
        }
        double len = Math.sqrt(distSq);
        double step = Math.min(len, blocksPerSecond * dt);
        pos.x += dx / len * step;
        pos.z += dz / len * step;
        applyGroundY(world, pos, ty);
        tc.getRotation().setYaw(bodyYawAlongMove(dx, dz));
        commandBuffer.putComponent(ref, TransformComponent.getComponentType(), tc);
        return false;
    }

    /** Player-derived models face -Z when yaw=0; add π so forward matches horizontal travel direction. */
    private static float bodyYawAlongMove(double dx, double dz) {
        return (float) (Math.atan2(dx, dz) + Math.PI);
    }

    private static void applyGroundY(@Nullable World world, @Nonnull Vector3d pos, double fallbackY) {
        if (world == null) {
            pos.y = fallbackY;
            return;
        }
        int bx = (int) Math.floor(pos.x);
        int bz = (int) Math.floor(pos.z);
        int sy = VillagerPoiPathfinder.findStandY(world, bx, bz, (int) Math.floor(pos.y) + 3);
        if (sy == Integer.MIN_VALUE) {
            pos.y = fallbackY;
        } else {
            pos.y = sy + 0.02;
        }
    }
}
