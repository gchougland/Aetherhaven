package com.hexvane.aetherhaven.scaffold;



import com.hypixel.hytale.math.shape.Box;

import com.hypixel.hytale.math.vector.Vector3i;

import com.hypixel.hytale.protocol.InteractionSyncData;

import com.hypixel.hytale.server.core.modules.physics.component.Velocity;

import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;

import javax.annotation.Nullable;



/**

 * @deprecated Prefer {@link ScaffoldPlacementResolver}; retained as a stable entry for callers.

 */

@Deprecated

public final class ScaffoldColumn {



    private ScaffoldColumn() {}



    public static boolean shouldUseUpPlacementNormal(

        @Nonnull World world,

        @Nonnull InteractionSyncData clientState,

        @Nonnull Vector3i clientPlacement,

        @Nonnull Vector3i resolvedTarget,

        @Nonnull String placingBlockTypeKey,

        @Nullable Velocity velocity

    ) {

        return ScaffoldPlacementResolver.shouldUseUpPlacementNormal(

            world, clientState, clientPlacement, resolvedTarget, placingBlockTypeKey, velocity);

    }



    @Nonnull

    public static Vector3i resolveStackPlacement(

        @Nonnull World world,

        @Nonnull InteractionSyncData clientState,

        @Nonnull Vector3i clientPlacement,

        @Nonnull String placingBlockTypeKey,

        @Nullable Box playerWorldBounds,

        @Nullable Velocity velocity

    ) {

        return ScaffoldPlacementResolver.resolve(

            world, clientState, clientPlacement, placingBlockTypeKey, playerWorldBounds, velocity);

    }



    public static int highestScaffoldY(@Nonnull World world, int x, int z) {

        return ScaffoldPlacementResolver.highestScaffoldY(world, x, z);

    }

}

