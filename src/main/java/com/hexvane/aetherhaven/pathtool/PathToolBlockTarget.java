package com.hexvane.aetherhaven.pathtool;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionSyncData;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

final class PathToolBlockTarget {
    /** Matches path tool item UseDistance (creative). */
    private static final double REACH = 6.0;

    private PathToolBlockTarget() {}

    @Nullable
    static Vector3i resolve(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull InteractionContext context,
        @Nullable Vector3i interactionTargetBlock
    ) {
        @Nullable
        InteractionSyncData sync = context.getClientState();
        @Nullable
        BlockPosition blockPosition = sync != null ? sync.blockPosition : context.getTargetBlock();
        if (blockPosition != null) {
            return new Vector3i(blockPosition.x, blockPosition.y, blockPosition.z);
        }
        if (interactionTargetBlock != null) {
            return interactionTargetBlock;
        }
        return TargetUtil.getTargetBlock(playerRef, REACH, store);
    }
}
