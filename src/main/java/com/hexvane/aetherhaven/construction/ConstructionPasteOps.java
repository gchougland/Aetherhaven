package com.hexvane.aetherhaven.construction;

import com.hypixel.hytale.assetstore.map.BlockTypeAssetMap;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.blocktype.component.BlockPhysics;
import com.hypixel.hytale.server.core.entity.entities.BlockEntity;
import com.hypixel.hytale.server.core.modules.entity.component.FromPrefab;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.prefab.PrefabRotation;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Shared prefab paste steps used by {@link com.hexvane.aetherhaven.prefab.ConstructionAnimator} and passive assembly.
 * Keeps ordering, RNG seed, and block settings aligned with the original animator.
 */
public final class ConstructionPasteOps {
    /**
     * Forced block placement: bit 2 only. {@link com.hypixel.hytale.server.core.util.FillerBlockUtil#setFillerBlocksAt}
     * runs when {@code (settings & 8) == 0} (see {@link WorldChunk#setBlock}); bit 8 suppresses that and breaks
     * multi-block furniture (beds): sibling cells keep old terrain and overlap the model.
     */
    public static final int SET_BLOCK_SETTINGS_PLACE = 2;
    /** Air clears / {@link WorldChunk#breakBlock}: keep {@code 8|2} tuning from earlier construction fixes. */
    public static final int SET_BLOCK_SETTINGS_CLEAR = 10;

    /**
     * Each {@link IPrefabBuffer#forEach} pass that reads chance blocks must use the same RNG sequence from the
     * start of the iteration (see {@link com.hypixel.hytale.server.core.prefab.selection.buffer.impl.PrefabBuffer}).
     */
    public static final long PREFAB_BUFFER_ITERATION_SEED = 0L;

    private ConstructionPasteOps() {}

    public record PendingBlock(
        int x,
        int y,
        int z,
        int blockId,
        @Nullable Holder<ChunkStore> holder,
        int supportValue,
        int blockRotation,
        int filler,
        int fluidId,
        int fluidLevel
    ) {}

    public record PrefabSequence(
        @Nonnull List<PendingBlock> pendingBlocks,
        @Nonnull List<Holder<EntityStore>> prefabEntitiesInOrder,
        @Nonnull PrefabRotation prefabRotation
    ) {}

    @Nonnull
    public static PrefabSequence buildSequence(@Nonnull IPrefabBuffer bufferAccess, @Nonnull Rotation yaw) {
        Random bufferIterationRandom = new Random(PREFAB_BUFFER_ITERATION_SEED);
        PrefabRotation prefabRotation = PrefabRotation.fromRotation(yaw);
        PrefabBufferCall call = new PrefabBufferCall(bufferIterationRandom, prefabRotation);
        List<PendingBlock> pending = new ArrayList<>();
        List<Holder<EntityStore>> prefabEntitiesInOrder = new ArrayList<>();
        bufferAccess.forEach(
            IPrefabBuffer.iterateAllColumns(),
            (x, y, z, blockId, holder, supportValue, blockRotation, filler, t, fluidId, fluidLevel) -> {
                if (blockId == 0 && filler == 0) {
                    pending.add(new PendingBlock(x, y, z, 0, null, 0, 0, 0, fluidId, fluidLevel));
                    return;
                }
                pending.add(new PendingBlock(x, y, z, blockId, holder, supportValue, blockRotation, filler, fluidId, fluidLevel));
            },
            (x, z, entityWrappers, tt) -> {
                if (entityWrappers == null || entityWrappers.length == 0) {
                    return;
                }
                for (Holder<EntityStore> h : entityWrappers) {
                    if (h != null) {
                        prefabEntitiesInOrder.add(h.clone());
                    }
                }
            },
            null,
            call
        );
        Comparator<PendingBlock> byColumn =
            Comparator.comparingInt(PendingBlock::y).thenComparingInt(PendingBlock::x).thenComparingInt(PendingBlock::z);
        pending.sort(byColumn);
        return new PrefabSequence(pending, prefabEntitiesInOrder, prefabRotation);
    }

    /**
     * Prefab air with no fluid ({@code blockId == 0}, {@code filler == 0}, {@code fluidId == 0}). Assembly and batched
     * placement skip these so players do not spend ticks “building” empty cells; {@link #prepAssemblySite} still uses
     * the full sequence so interiors are carved.
     */
    public static boolean isPureAirPrefabCell(@Nonnull PendingBlock pb) {
        return pb.blockId == 0 && pb.filler == 0 && pb.fluidId == 0;
    }

    @Nonnull
    public static List<PendingBlock> withoutPureAirCells(@Nonnull List<PendingBlock> full) {
        return full.stream().filter(pb -> !isPureAirPrefabCell(pb)).collect(Collectors.toUnmodifiableList());
    }

    @Nonnull
    public static LocalCachedChunkAccessor createAccessor(
        @Nonnull World world,
        @Nonnull Vector3i origin,
        @Nonnull IPrefabBuffer bufferAccess
    ) {
        double xLength = bufferAccess.getMaxX() - bufferAccess.getMinX();
        double zLength = bufferAccess.getMaxZ() - bufferAccess.getMinZ();
        int prefabRadius = (int) Math.floor(0.5 * Math.sqrt(xLength * xLength + zLength * zLength));
        return LocalCachedChunkAccessor.atWorldCoords(world, origin.getX(), origin.getZ(), prefabRadius);
    }

    /**
     * Before assembly: clear terrain like the animator would (air cells + strip solid cells to air) so previews are visible.
     * Does not place final prefab solids; does not spawn entities. Filler-only cells get fluid only when applicable.
     */
    public static void prepAssemblySite(
        @Nonnull World world,
        @Nonnull Vector3i origin,
        @Nonnull List<PendingBlock> pending,
        boolean force,
        @Nonnull PrefabRotation prefabRotation,
        @Nonnull IPrefabBuffer bufferAccess
    ) {
        LocalCachedChunkAccessor chunkAccessor = createAccessor(world, origin, bufferAccess);
        BlockTypeAssetMap<String, BlockType> blockTypeMap = BlockType.getAssetMap();
        for (PendingBlock pb : pending) {
            int bx = origin.x + pb.x;
            int by = origin.y + pb.y;
            int bz = origin.z + pb.z;
            WorldChunk chunk = chunkAccessor.getNonTickingChunk(ChunkUtil.indexChunkFromBlock(bx, bz));
            applyPrefabFluidForCell(world, bx, by, bz, pb.fluidId, pb.fluidLevel, chunkAccessor);
            if (pb.filler != 0) {
                continue;
            }
            if (pb.blockId == 0) {
                if (force) {
                    chunk.setBlock(bx, by, bz, BlockType.EMPTY_ID, BlockType.EMPTY, 0, 0, SET_BLOCK_SETTINGS_CLEAR);
                } else {
                    chunk.breakBlock(bx, by, bz, SET_BLOCK_SETTINGS_CLEAR);
                }
            } else {
                chunk.setBlock(bx, by, bz, BlockType.EMPTY_ID, BlockType.EMPTY, 0, 0, SET_BLOCK_SETTINGS_CLEAR);
            }
        }
    }

    public static void placeOne(
        @Nonnull World world,
        @Nonnull Vector3i origin,
        @Nonnull PendingBlock pb,
        boolean force,
        @Nonnull LocalCachedChunkAccessor chunkAccessor,
        @Nonnull BlockTypeAssetMap<String, BlockType> blockTypeMap
    ) {
        int bx = origin.x + pb.x;
        int by = origin.y + pb.y;
        int bz = origin.z + pb.z;
        WorldChunk chunk = chunkAccessor.getNonTickingChunk(ChunkUtil.indexChunkFromBlock(bx, bz));
        applyPrefabFluidForCell(world, bx, by, bz, pb.fluidId, pb.fluidLevel, chunkAccessor);
        BlockType block = blockTypeMap.getAsset(pb.blockId);
        String blockKey = block.getId();
        if (pb.filler != 0) {
            if (pb.holder != null) {
                setBlockEntityHolder(world, chunk, bx, by, bz, block, pb.blockRotation, pb.holder.clone());
            }
            return;
        }
        if (pb.blockId == 0) {
            if (force) {
                chunk.setBlock(bx, by, bz, BlockType.EMPTY_ID, BlockType.EMPTY, 0, 0, SET_BLOCK_SETTINGS_CLEAR);
            } else {
                chunk.breakBlock(bx, by, bz, SET_BLOCK_SETTINGS_CLEAR);
            }
            return;
        }
        if (!force) {
            RotationTuple rot = RotationTuple.get(pb.blockRotation);
            chunk.placeBlock(bx, by, bz, blockKey, rot.yaw(), rot.pitch(), rot.roll(), SET_BLOCK_SETTINGS_PLACE);
        } else {
            int indexKey = blockTypeMap.getIndex(blockKey);
            BlockType type = blockTypeMap.getAsset(indexKey);
            chunk.setBlock(bx, by, bz, indexKey, type, pb.blockRotation, pb.filler, SET_BLOCK_SETTINGS_PLACE);
        }
        if (pb.supportValue != 0) {
            Ref<ChunkStore> ref = chunk.getReference();
            Store<ChunkStore> store = ref.getStore();
            Ref<ChunkStore> section = sectionRefForBlockY(chunk, by);
            if (section != null) {
                BlockPhysics.setSupportValue(store, section, bx, by, bz, pb.supportValue);
            }
        }
        if (pb.holder != null) {
            setBlockEntityHolder(world, chunk, bx, by, bz, block, pb.blockRotation, pb.holder.clone());
        }
    }

    public static void finishFluidsAndEntities(
        @Nonnull World world,
        @Nonnull Vector3i origin,
        @Nonnull PrefabRotation prefabRotation,
        int prefabId,
        @Nonnull IPrefabBuffer bufferAccess,
        @Nonnull List<Holder<EntityStore>> prefabEntitiesInOrder,
        @Nonnull ComponentAccessor<EntityStore> entityAccessor
    ) {
        LocalCachedChunkAccessor chunkAccessor = createAccessor(world, origin, bufferAccess);
        PrefabBufferCall secondPassCall = new PrefabBufferCall(new Random(PREFAB_BUFFER_ITERATION_SEED), prefabRotation);
        bufferAccess.forEach(
            IPrefabBuffer.iterateAllColumns(),
            (x, y, z, blockId, holder, supportValue, blockRotation, filler, t, fluidId, fluidLevel) -> {
                if (filler == 0) {
                    return;
                }
                int bx = origin.x + x;
                int by = origin.y + y;
                int bz = origin.z + z;
                applyPrefabFluidForCell(world, bx, by, bz, fluidId, fluidLevel, chunkAccessor);
            },
            null,
            null,
            secondPassCall
        );
        for (int i = 0; i < prefabEntitiesInOrder.size(); i++) {
            Holder<EntityStore> source = prefabEntitiesInOrder.get(i);
            spawnPrefabEntityLikePaste(world, origin, prefabRotation, prefabId, entityAccessor, source);
        }
    }

    public static void setBlockEntityHolder(
        @Nonnull World world,
        @Nonnull WorldChunk chunk,
        int bx,
        int by,
        int bz,
        @Nonnull BlockType blockType,
        int rotation,
        @Nonnull Holder<ChunkStore> holder
    ) {
        com.hypixel.hytale.server.core.modules.block.BlockEntity.setBlockEntity(
            world.getChunkStore().getStore(),
            chunk.getReference(),
            chunk.getBlockComponentChunk(),
            bx,
            by,
            bz,
            blockType,
            rotation,
            holder
        );
    }

    @SuppressWarnings("deprecation")
    private static Ref<ChunkStore> sectionRefForBlockY(@Nonnull WorldChunk chunk, int blockY) {
        Ref<ChunkStore> columnRef = chunk.getReference();
        Store<ChunkStore> store = columnRef.getStore();
        ChunkColumn column = store.getComponent(columnRef, ChunkColumn.getComponentType());
        return column == null ? null : column.getSection(ChunkUtil.chunkCoordinate(blockY));
    }

    public static void applyPrefabFluidForCell(
        @Nonnull World world,
        int bx,
        int by,
        int bz,
        int fluidId,
        int fluidLevel,
        @Nonnull LocalCachedChunkAccessor chunkAccessor
    ) {
        WorldChunk chunk = chunkAccessor.getNonTickingChunk(ChunkUtil.indexChunkFromBlock(bx, bz));
        Store<ChunkStore> fluidStore = world.getChunkStore().getStore();
        Ref<ChunkStore> section = sectionRefForBlockY(chunk, by);
        if (section == null) {
            return;
        }
        FluidSection fluidSection = fluidStore.ensureAndGetComponent(section, FluidSection.getComponentType());
        fluidSection.setFluid(bx, by, bz, fluidId, (byte) fluidLevel);
    }

    public static void spawnPrefabEntityLikePaste(
        @Nonnull World world,
        @Nonnull Vector3i origin,
        @Nonnull PrefabRotation prefabRotation,
        int prefabId,
        @Nonnull ComponentAccessor<EntityStore> entityAccessor,
        @Nonnull Holder<EntityStore> entityToAdd
    ) {
        Holder<EntityStore> clone = entityToAdd.clone();
        TransformComponent transformComp = clone.getComponent(TransformComponent.getComponentType());
        if (transformComp == null) {
            return;
        }
        Vector3d w = transformComp.getPosition().clone();
        boolean blockEntity = clone.getComponent(BlockEntity.getComponentType()) != null;
        Vector3d centerOffset = blockEntity ? new Vector3d(0.5, 0.0, 0.5) : new Vector3d(0.5, 0.5, 0.5);
        w.subtract(centerOffset);
        prefabRotation.rotate(w);
        w.add(centerOffset);
        w.add(origin);
        Vector3d pos = transformComp.getPosition();
        pos.x = w.x;
        pos.y = w.y;
        pos.z = w.z;
        float dyaw = prefabRotation.getYaw();
        if (prefabRotation == PrefabRotation.ROTATION_90 || prefabRotation == PrefabRotation.ROTATION_270) {
            dyaw += (float) Math.PI;
        }
        transformComp.getRotation().setYaw(transformComp.getRotation().getYaw() + dyaw);
        HeadRotation headRotation = clone.getComponent(HeadRotation.getComponentType());
        if (headRotation != null) {
            headRotation.getRotation().setYaw(headRotation.getRotation().getYaw() + dyaw);
        }
        PrefabPlaceEntityEvent prefabPlaceEntityEvent = new PrefabPlaceEntityEvent(prefabId, clone);
        entityAccessor.invoke(prefabPlaceEntityEvent);
        clone.ensureComponent(FromPrefab.getComponentType());
        entityAccessor.addEntity(clone, AddReason.LOAD);
    }
}
