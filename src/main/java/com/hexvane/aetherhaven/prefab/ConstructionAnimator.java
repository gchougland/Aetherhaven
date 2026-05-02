package com.hexvane.aetherhaven.prefab;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionPasteOps;
import com.hexvane.aetherhaven.construction.ConstructionPasteOps.PendingBlock;
import com.hypixel.hytale.assetstore.map.BlockTypeAssetMap;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.event.PrefabPasteEvent;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.LocalCachedChunkAccessor;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.PrefabUtil;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Places prefab blocks in bottom-up order (y, then x, z) like vanilla buffer column streams — not in two phases.
 * Two-phase (all primary y levels before any secondary) placed roof / air above before flowers at lower y and broke plants.
 * For each cell, applies fluid then block like vanilla prefab paste. Entities are applied once at the end.
 * Batches are spaced using {@link AetherhavenPlugin#scheduleOnWorld(World, Runnable, long)}.
 */
public final class ConstructionAnimator {
    private final AetherhavenPlugin plugin;
    private final World world;
    private final Vector3i origin;
    private final boolean force;
    private final ComponentAccessor<EntityStore> entityAccessor;
    private final List<PendingBlock> pendingBlocks;
    private final List<Holder<EntityStore>> prefabEntitiesInOrder;
    private final IPrefabBuffer bufferAccess;
    private final com.hypixel.hytale.server.core.prefab.PrefabRotation prefabRotation;
    private final int prefabId;
    private final int blocksPerBatch;
    private final long batchDelayMs;
    @Nullable
    private final Runnable onComplete;
    private int index;
    private final AtomicBoolean finished = new AtomicBoolean(false);

    private ConstructionAnimator(
        AetherhavenPlugin plugin,
        World world,
        Vector3i origin,
        boolean force,
        ComponentAccessor<EntityStore> entityAccessor,
        List<PendingBlock> pendingBlocks,
        List<Holder<EntityStore>> prefabEntitiesInOrder,
        IPrefabBuffer bufferAccess,
        com.hypixel.hytale.server.core.prefab.PrefabRotation prefabRotation,
        int prefabId,
        int blocksPerBatch,
        long batchDelayMs,
        @Nullable Runnable onComplete
    ) {
        this.plugin = plugin;
        this.world = world;
        this.origin = origin;
        this.force = force;
        this.entityAccessor = entityAccessor;
        this.pendingBlocks = pendingBlocks;
        this.prefabEntitiesInOrder = prefabEntitiesInOrder;
        this.bufferAccess = bufferAccess;
        this.prefabRotation = prefabRotation;
        this.prefabId = prefabId;
        this.blocksPerBatch = Math.max(1, blocksPerBatch);
        this.batchDelayMs = Math.max(1L, batchDelayMs);
        this.onComplete = onComplete;
    }

    public static void start(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull World world,
        @Nonnull Vector3i origin,
        @Nonnull Rotation yaw,
        boolean force,
        @Nonnull IPrefabBuffer bufferAccess,
        @Nonnull ComponentAccessor<EntityStore> entityAccessor,
        int blocksPerBatch,
        long batchDelayMs,
        @Nullable Runnable onComplete
    ) {
        world.execute(
            () -> startOnWorldThread(plugin, world, origin, yaw, force, bufferAccess, entityAccessor, blocksPerBatch, batchDelayMs, onComplete)
        );
    }

    private static void startOnWorldThread(
        AetherhavenPlugin plugin,
        World world,
        Vector3i origin,
        Rotation yaw,
        boolean force,
        IPrefabBuffer bufferAccess,
        ComponentAccessor<EntityStore> entityAccessor,
        int blocksPerBatch,
        long batchDelayMs,
        @Nullable Runnable onComplete
    ) {
        ConstructionPasteOps.PrefabSequence seq = ConstructionPasteOps.buildSequence(bufferAccess, yaw);
        int prefabId = PrefabUtil.getNextPrefabId();
        PrefabPasteEvent start = new PrefabPasteEvent(prefabId, true);
        entityAccessor.invoke(start);
        if (start.isCancelled()) {
            bufferAccess.release();
            return;
        }
        ConstructionAnimator job = new ConstructionAnimator(
            plugin,
            world,
            origin,
            force,
            entityAccessor,
            ConstructionPasteOps.withoutPureAirCells(seq.pendingBlocks()),
            seq.prefabEntitiesInOrder(),
            bufferAccess,
            seq.prefabRotation(),
            prefabId,
            blocksPerBatch,
            batchDelayMs,
            onComplete
        );
        job.runBatch();
    }

    private void runBatch() {
        if (finished.get()) {
            return;
        }
        LocalCachedChunkAccessor chunkAccessor = ConstructionPasteOps.createAccessor(world, origin, bufferAccess);
        BlockTypeAssetMap<String, BlockType> blockTypeMap = BlockType.getAssetMap();
        int placed = 0;
        while (index < pendingBlocks.size() && placed < blocksPerBatch) {
            PendingBlock pb = pendingBlocks.get(index++);
            ConstructionPasteOps.placeOne(world, origin, pb, force, chunkAccessor, blockTypeMap);
            placed++;
        }
        if (index < pendingBlocks.size()) {
            plugin.scheduleOnWorld(world, this::runBatch, batchDelayMs);
            return;
        }
        ConstructionPasteOps.finishFluidsAndEntities(
            world,
            origin,
            prefabRotation,
            prefabId,
            bufferAccess,
            prefabEntitiesInOrder,
            entityAccessor
        );
        PrefabPasteEvent end = new PrefabPasteEvent(prefabId, false);
        entityAccessor.invoke(end);
        if (onComplete != null) {
            onComplete.run();
        }
        bufferAccess.release();
        finished.set(true);
    }
}
