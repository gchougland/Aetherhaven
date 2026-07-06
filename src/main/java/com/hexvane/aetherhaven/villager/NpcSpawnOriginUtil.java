package com.hexvane.aetherhaven.villager;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

public final class NpcSpawnOriginUtil {
    private NpcSpawnOriginUtil() {}

    public static void attach(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull String spawnSource,
        @Nullable String spawnDetail,
        @Nonnull World world,
        @Nonnull Vector3d position
    ) {
        attach(store, ref, spawnSource, spawnDetail, world, position, 0L);
    }

    public static void attach(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull String spawnSource,
        @Nullable String spawnDetail,
        @Nonnull World world,
        @Nonnull Vector3d position,
        long spawnGameEpochDay
    ) {
        store.putComponent(
            ref,
            AetherhavenNpcSpawnOrigin.getComponentType(),
            new AetherhavenNpcSpawnOrigin(
                spawnSource,
                spawnDetail != null ? spawnDetail : "",
                world.getName(),
                position.x,
                position.y,
                position.z,
                System.currentTimeMillis(),
                spawnGameEpochDay
            )
        );
    }
}
