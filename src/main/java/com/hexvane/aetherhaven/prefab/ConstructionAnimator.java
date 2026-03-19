package com.hexvane.aetherhaven.prefab;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hypixel.hytale.assetstore.map.BlockTypeAssetMap;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.util.FastRandom;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.blocktype.component.BlockPhysics;
import com.hypixel.hytale.server.core.modules.entity.component.FromPrefab;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.prefab.PrefabRotation;
import com.hypixel.hytale.server.core.prefab.event.PrefabPasteEvent;
import com.hypixel.hytale.server.core.prefab.event.PrefabPlaceEntityEvent;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferCall;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.LocalCachedChunkAccessor;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkColumn;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.PrefabUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Places prefab blocks bottom-up in batches; applies fluids and entities once at the end.
 * Batches are spaced using {@link AetherhavenPlugin#scheduleOnWorld(World, Runnable, long)} so work spreads across time.
 */
public final class ConstructionAnimator {
    private static final int SET_BLOCK_SETTINGS = 10;

    private final AetherhavenPlugin plugin;
    private final World world;
    private final Vector3i origin;
    private final Rotation yaw;
    private final boolean force;
    private final Random random;
    private final ComponentAccessor<EntityStore> entityAccessor;
    private final List<PendingBlock> blocks;
    private final IPrefabBuffer bufferAccess;
    private final PrefabRotation prefabRotation;
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
        Rotation yaw,
        boolean force,
        Random random,
        ComponentAccessor<EntityStore> entityAccessor,
        List<PendingBlock> blocks,
        IPrefabBuffer bufferAccess,
        PrefabRotation prefabRotation,
        int prefabId,
        int blocksPerBatch,
        long batchDelayMs,
        @Nullable Runnable onComplete
    ) {
        this.plugin = plugin;
        this.world = world;
        this.origin = origin;
        this.yaw = yaw;
        this.force = force;
        this.random = random;
        this.entityAccessor = entityAccessor;
        this.blocks = blocks;
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
        Random random = new FastRandom();
        PrefabRotation prefabRotation = PrefabRotation.fromRotation(yaw);
        PrefabBufferCall call = new PrefabBufferCall(random, prefabRotation);
        List<PendingBlock> list = new ArrayList<>();
        bufferAccess.forEach(
            IPrefabBuffer.iterateAllColumns(),
            (x, y, z, blockId, holder, supportValue, blockRotation, filler, t, fluidId, fluidLevel) -> {
                if (filler != 0 || blockId == 0) {
                    return;
                }
                list.add(new PendingBlock(x, y, z, blockId, holder, supportValue, blockRotation, filler));
            },
            null,
            null,
            call
        );
        list.sort(Comparator.comparingInt(PendingBlock::y).thenComparingInt(PendingBlock::x).thenComparingInt(PendingBlock::z));
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
            yaw,
            force,
            random,
            entityAccessor,
            list,
            bufferAccess,
            prefabRotation,
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
        double xLength = bufferAccess.getMaxX() - bufferAccess.getMinX();
        double zLength = bufferAccess.getMaxZ() - bufferAccess.getMinZ();
        int prefabRadius = (int) Math.floor(0.5 * Math.sqrt(xLength * xLength + zLength * zLength));
        LocalCachedChunkAccessor chunkAccessor = LocalCachedChunkAccessor.atWorldCoords(world, origin.getX(), origin.getZ(), prefabRadius);
        BlockTypeAssetMap<String, BlockType> blockTypeMap = BlockType.getAssetMap();
        int placed = 0;
        while (index < blocks.size() && placed < blocksPerBatch) {
            PendingBlock pb = blocks.get(index++);
            placeOne(pb, chunkAccessor, blockTypeMap);
            placed++;
        }
        if (index >= blocks.size()) {
            finishFluidsAndEntities(chunkAccessor);
            PrefabPasteEvent end = new PrefabPasteEvent(prefabId, false);
            entityAccessor.invoke(end);
            if (onComplete != null) {
                onComplete.run();
            }
            bufferAccess.release();
            finished.set(true);
        } else {
            plugin.scheduleOnWorld(world, this::runBatch, batchDelayMs);
        }
    }

    private void placeOne(
        PendingBlock pb,
        LocalCachedChunkAccessor chunkAccessor,
        BlockTypeAssetMap<String, BlockType> blockTypeMap
    ) {
        int bx = origin.x + pb.x;
        int by = origin.y + pb.y;
        int bz = origin.z + pb.z;
        WorldChunk chunk = chunkAccessor.getNonTickingChunk(ChunkUtil.indexChunkFromBlock(bx, bz));
        BlockType block = blockTypeMap.getAsset(pb.blockId);
        String blockKey = block.getId();
        if (!force) {
            RotationTuple rot = RotationTuple.get(pb.blockRotation);
            chunk.placeBlock(bx, by, bz, blockKey, rot.yaw(), rot.pitch(), rot.roll(), SET_BLOCK_SETTINGS);
        } else {
            int indexKey = blockTypeMap.getIndex(blockKey);
            BlockType type = blockTypeMap.getAsset(indexKey);
            chunk.setBlock(bx, by, bz, indexKey, type, pb.blockRotation, pb.filler, SET_BLOCK_SETTINGS);
        }
        if (pb.supportValue != 0) {
            Ref<ChunkStore> ref = chunk.getReference();
            Store<ChunkStore> store = ref.getStore();
            ChunkColumn column = store.getComponent(ref, ChunkColumn.getComponentType());
            BlockPhysics.setSupportValue(store, column.getSection(ChunkUtil.chunkCoordinate(by)), bx, by, bz, pb.supportValue);
        }
        if (pb.holder != null) {
            chunk.setState(bx, by, bz, block, pb.blockRotation, pb.holder.clone());
        }
    }

    private void finishFluidsAndEntities(LocalCachedChunkAccessor chunkAccessor) {
        Store<ChunkStore> fluidStore = world.getChunkStore().getStore();
        PrefabBufferCall call = new PrefabBufferCall(random, prefabRotation);
        bufferAccess.forEach(
            IPrefabBuffer.iterateAllColumns(),
            (x, y, z, blockId, holder, supportValue, blockRotation, filler, t, fluidId, fluidLevel) -> {
                int bx = origin.x + x;
                int by = origin.y + y;
                int bz = origin.z + z;
                WorldChunk chunk = chunkAccessor.getNonTickingChunk(ChunkUtil.indexChunkFromBlock(bx, bz));
                ChunkColumn fluidColumn = fluidStore.getComponent(chunk.getReference(), ChunkColumn.getComponentType());
                Ref<ChunkStore> section = fluidColumn.getSection(ChunkUtil.chunkCoordinate(by));
                FluidSection fluidSection = fluidStore.ensureAndGetComponent(section, FluidSection.getComponentType());
                fluidSection.setFluid(bx, by, bz, fluidId, (byte) fluidLevel);
            },
            (x, z, entityWrappers, tt) -> {
                if (entityWrappers != null && entityWrappers.length != 0) {
                    for (int ei = 0; ei < entityWrappers.length; ei++) {
                        Holder<EntityStore> entityToAdd = entityWrappers[ei];
                        if (entityToAdd == null) {
                            continue;
                        }
                        Holder<EntityStore> clone = entityToAdd.clone();
                        TransformComponent transformComp = clone.getComponent(TransformComponent.getComponentType());
                        if (transformComp == null) {
                            continue;
                        }
                        Vector3d entityPosition = transformComp.getPosition().clone();
                        prefabRotation.rotate(entityPosition);
                        Vector3d entityWorldPosition = entityPosition.add(origin);
                        transformComp = clone.getComponent(TransformComponent.getComponentType());
                        if (transformComp != null) {
                            Vector3d p = transformComp.getPosition();
                            p.x = entityWorldPosition.x;
                            p.y = entityWorldPosition.y;
                            p.z = entityWorldPosition.z;
                            PrefabPlaceEntityEvent prefabPlaceEntityEvent = new PrefabPlaceEntityEvent(prefabId, clone);
                            entityAccessor.invoke(prefabPlaceEntityEvent);
                            clone.ensureComponent(FromPrefab.getComponentType());
                            entityAccessor.addEntity(clone, AddReason.LOAD);
                        }
                    }
                }
            },
            null,
            call
        );
    }

    private record PendingBlock(int x, int y, int z, int blockId, @Nullable Holder<ChunkStore> holder, int supportValue, int blockRotation, int filler) {}
}
