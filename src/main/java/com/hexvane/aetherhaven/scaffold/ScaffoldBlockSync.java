package com.hexvane.aetherhaven.scaffold;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.packets.world.ServerSetBlock;
import com.hypixel.hytale.server.core.modules.entity.player.ChunkTracker;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Client block sync and section lookup without deprecated {@code BlockChunk#getSectionAtBlockY} or
 * {@link BlockSection#invalidateBlock}.
 */
public final class ScaffoldBlockSync {

    private ScaffoldBlockSync() {
    }

    /**
     * Resolves the {@link BlockSection} that holds the block at world coordinates via the world's chunk store
     * (same path as {@link ChunkStore#getChunkSectionReferenceAtBlock(int, int, int)}).
     */
    @Nullable
    public static BlockSection blockSectionAtWorldBlock(
        @Nonnull World world,
        @Nonnull Store<ChunkStore> chunkStore,
        int worldX,
        int worldY,
        int worldZ
    ) {
        if (worldY < ChunkUtil.MIN_Y || worldY > ChunkUtil.HEIGHT_MINUS_1) {
            return null;
        }
        Ref<ChunkStore> sectionRef = world.getChunkStore().getChunkSectionReferenceAtBlock(worldX, worldY, worldZ);
        if (sectionRef == null || !sectionRef.isValid()) {
            return null;
        }
        return chunkStore.getComponent(sectionRef, BlockSection.getComponentType());
    }

    /**
     * Pushes the server's current block state at {@code (worldX, worldY, worldZ)} to clients that have the chunk loaded,
     * matching single-block handling in chunk replication.
     */
    public static void broadcastCurrentBlockStateToChunkWatchers(
        @Nonnull World world,
        @Nonnull Store<ChunkStore> chunkStore,
        int worldX,
        int worldY,
        int worldZ
    ) {
        BlockSection section = blockSectionAtWorldBlock(world, chunkStore, worldX, worldY, worldZ);
        if (section == null) {
            return;
        }
        int blockIdx = ChunkUtil.indexBlock(worldX, worldY, worldZ);
        int blockId = section.get(blockIdx);
        int filler = section.getFiller(blockIdx);
        int rotationIndex = section.getRotationIndex(blockIdx);
        ServerSetBlock packet = new ServerSetBlock(worldX, worldY, worldZ, blockId, (short) filler, (byte) rotationIndex);
        long chunkIndex = ChunkUtil.indexChunkFromBlock(worldX, worldZ);
        for (PlayerRef player : world.getPlayerRefs()) {
            Ref<EntityStore> playerEntityRef = player.getReference();
            if (playerEntityRef == null) {
                continue;
            }
            ChunkTracker tracker = player.getChunkTracker();
            if (tracker != null && tracker.isLoaded(chunkIndex)) {
                player.getPacketHandler().writeNoCache(packet);
            }
        }
    }
}
