package com.hexvane.aetherhaven.construction;

import com.hexvane.aetherhaven.construction.assembly.AssemblyObstructionUtil;
import com.hexvane.aetherhaven.placement.PrefabFootprintClearUtil;
import com.hypixel.hytale.assetstore.map.BlockTypeAssetMap;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import org.joml.Vector3d;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.blocktype.component.BlockPhysics;
import com.hypixel.hytale.server.core.entity.entities.BlockEntity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.modules.entity.component.FromPrefab;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.core.prefab.PrefabRotation;
import com.hypixel.hytale.server.core.prefab.event.PrefabPlaceEntityEvent;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferCall;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.LocalCachedChunkAccessor;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkColumn;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Collection;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Shared prefab paste steps used by {@link com.hexvane.aetherhaven.prefab.ConstructionAnimator} and passive assembly.
 * Keeps ordering, RNG seed, and block settings aligned with the original animator.
 */
public final class ConstructionPasteOps {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * {@code placeBlock} settings for chests/benches at build completion: bit 2 skips automatic block-entity
     * attachment; we attach explicitly on a ticking chunk (same as {@link com.hexvane.aetherhaven.placement.PlotPlacementCommit}).
     */
    private static final int INTERACTIVE_BLOCK_PLACE_SETTINGS = 10;

    /**
     * {@link com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor#placeBlock} settings for prefab
     * construction ({@link com.hypixel.hytale.server.core.util.PrefabUtil} uses {@code 0} for force paste).
     * Must stay {@code 0}: {@code placeBlock} maps bit 2 to {@code setBlock} flag 256 ({@code updateBlockArea}), which
     * marks blocks ticking during incremental assembly and breaks furniture that needs support below. Use
     * {@code placeBlock} (not raw {@code setBlock} with bit 2 set) so benches, chests, and other
     * {@link BlockType#getBlockEntity()} blocks still get working components/interactions.
     */
    public static final int SET_BLOCK_SETTINGS_PLACE = 0;
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

    /** Split of non-air assembly cells: incremental frontier uses {@code main}; {@code deferred} is placed in one batch at completion. */
    public record AssemblyDeferredPartition(
        @Nonnull List<PendingBlock> main,
        @Nonnull List<PendingBlock> deferred
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
     * placement skip these so players do not spend ticks “building” empty cells; {@link #prepAssemblySite} still walks
     * the full sequence so interiors are carved. Prefab fluids are not applied during prep or incremental placement —
     * only at completion via {@link #finishFluidsAndEntities}.
     */
    public static boolean isPureAirPrefabCell(@Nonnull PendingBlock pb) {
        return pb.blockId == 0 && pb.filler == 0 && pb.fluidId == 0;
    }

    @Nonnull
    public static List<PendingBlock> withoutPureAirCells(@Nonnull List<PendingBlock> full) {
        return full.stream().filter(pb -> !isPureAirPrefabCell(pb)).collect(Collectors.toUnmodifiableList());
    }

    /**
     * {@code true} when {@link #placeOne} would not change loaded block state (air-on-air, matching block already
     * present, filler segment with no holder). Lets passive assembly and the builder skip empty frontier cells in one
     * burst instead of pacing each no-op slot.
     */
    public static boolean isAssemblyPlacementNoOp(
        @Nonnull Vector3i origin,
        @Nonnull PendingBlock pb,
        @Nonnull LocalCachedChunkAccessor chunkAccessor,
        @Nonnull BlockTypeAssetMap<String, BlockType> blockTypeMap
    ) {
        if (isPureAirPrefabCell(pb)) {
            return true;
        }
        if (isInteractiveBlockEntityOrigin(pb, blockTypeMap)) {
            return true;
        }
        if (pb.filler() != 0) {
            return pb.holder() == null;
        }
        int wx = origin.x + pb.x();
        int wy = origin.y + pb.y();
        int wz = origin.z + pb.z();
        BlockType worldBlock = blockTypeAt(chunkAccessor, wx, wy, wz);
        if (worldBlock == null) {
            return false;
        }
        if (pb.blockId() == 0) {
            return worldBlock == BlockType.EMPTY;
        }
        BlockType target = blockTypeMap.getAsset(pb.blockId());
        if (target == null) {
            return false;
        }
        if (target == BlockType.EMPTY || target.getMaterial() == BlockMaterial.Empty) {
            return worldBlock == BlockType.EMPTY || AssemblyObstructionUtil.isSoftClearingSkippedBlock(worldBlock);
        }
        String targetId = target.getId();
        String worldId = worldBlock.getId();
        if (targetId == null || !targetId.equals(worldId)) {
            return false;
        }
        if (blockTypeNeedsLiveBlockEntityRef(target)) {
            WorldChunk chunk = chunkAccessor.getNonTickingChunk(ChunkUtil.indexChunkFromBlock(wx, wz));
            if (chunk == null || !chunk.getReference().isValid()) {
                return false;
            }
            BlockComponentChunk blockComponents = chunk.getBlockComponentChunk();
            if (blockComponents == null) {
                return false;
            }
            int index = ChunkUtil.indexBlockInColumn(wx, wy, wz);
            if (target.getBench() != null) {
                return chunk.getBlockComponentEntity(wx, wy, wz) != null;
            }
            return blockComponents.getComponent(index, ItemContainerBlock.getComponentType()) != null
                || chunk.getBlockComponentEntity(wx, wy, wz) != null;
        }
        return true;
    }

    @Nullable
    private static BlockType blockTypeAt(
        @Nonnull LocalCachedChunkAccessor chunkAccessor,
        int wx,
        int wy,
        int wz
    ) {
        WorldChunk chunk = chunkAccessor.getNonTickingChunk(ChunkUtil.indexChunkFromBlock(wx, wz));
        if (chunk == null || !chunk.getReference().isValid()) {
            return null;
        }
        return BlockType.getAssetMap().getAsset(chunk.getBlock(wx, wy, wz));
    }

    /**
     * Partitions {@code nonAirPlacementOrder} (typically {@link #withoutPureAirCells}) so block types listed in the
     * construction JSON’s {@code assemblyDeferredBlockIds} are not indexed for the assembly frontier — they are written
     * in {@code deferred} order after the frontier completes.
     */
    @Nonnull
    public static AssemblyDeferredPartition partitionAssemblyDeferredBlocks(
        @Nonnull List<PendingBlock> nonAirPlacementOrder,
        @Nonnull BlockTypeAssetMap<String, BlockType> blockTypeMap,
        @Nonnull Collection<String> deferBlockTypeIds
    ) {
        if (deferBlockTypeIds.isEmpty()) {
            return new AssemblyDeferredPartition(nonAirPlacementOrder, List.of());
        }
        HashSet<String> want = new HashSet<>();
        for (String id : deferBlockTypeIds) {
            if (id != null && !id.isBlank()) {
                want.add(id.trim());
            }
        }
        if (want.isEmpty()) {
            return new AssemblyDeferredPartition(nonAirPlacementOrder, List.of());
        }
        List<PendingBlock> main = new ArrayList<>();
        List<PendingBlock> deferred = new ArrayList<>();
        for (PendingBlock pb : nonAirPlacementOrder) {
            String typeId = null;
            if (pb.blockId() != 0) {
                BlockType bt = blockTypeMap.getAsset(pb.blockId());
                if (bt != null) {
                    typeId = bt.getId();
                }
            }
            if (typeId != null && want.contains(typeId)) {
                deferred.add(pb);
            } else {
                main.add(pb);
            }
        }
        if (main.isEmpty() && !deferred.isEmpty()) {
            return new AssemblyDeferredPartition(nonAirPlacementOrder, List.of());
        }
        return new AssemblyDeferredPartition(List.copyOf(main), List.copyOf(deferred));
    }

    /**
     * Origin voxels for chests, workbenches, aquariums, and other blocks with live block-entity state. These are
     * deferred from {@link #placeOne} / {@link #forcePasteAllSolids} and written once at build completion via
     * {@link #placeInteractiveBlockEntitiesFromPrefab}.
     *
     * <p>Placing them during incremental assembly and again in {@code forcePasteAllSolids} removes the first
     * block entity ({@code RemoveReason.REMOVE}), which drops stored contents (vanilla item containers, modded
     * aquariums, etc.) even though the replacement still has the prefab state.
     */
    public static boolean isInteractiveBlockEntityOrigin(
        @Nonnull PendingBlock pb,
        @Nonnull BlockTypeAssetMap<String, BlockType> blockTypeMap
    ) {
        if (pb.filler() != FillerBlockUtil.NO_FILLER || pb.blockId() == 0) {
            return false;
        }
        BlockType block = blockTypeMap.getAsset(pb.blockId());
        if (block == null) {
            return false;
        }
        if (holderHasSerializedBlockEntityState(pb.holder())) {
            return true;
        }
        return blockTypeNeedsLiveBlockEntityRef(block);
    }

    /**
     * Prefab cell carried a block-entity holder (chest inventory, aquarium contents, placement metadata, …).
     * Always defer so force-paste does not destroy-and-recreate that entity mid-build.
     */
    private static boolean holderHasSerializedBlockEntityState(@Nullable Holder<ChunkStore> holder) {
        return holder != null;
    }

    private static boolean blockTypeNeedsLiveBlockEntityRef(@Nonnull BlockType block) {
        if (block.getBench() != null) {
            return true;
        }
        Holder<ChunkStore> template = block.getBlockEntity();
        // Any block-entity template (not only ItemContainerBlock): aquariums and similar drop on REMOVE.
        if (template != null) {
            return true;
        }
        Map<InteractionType, String> interactions = block.getInteractions();
        if (interactions == null || interactions.isEmpty()) {
            return false;
        }
        String use = interactions.get(InteractionType.Use);
        if (use == null) {
            return false;
        }
        String lower = use.toLowerCase();
        return lower.contains("open_container") || lower.contains("bench");
    }

    /**
     * Places chests, workbenches, and other interactive block entities on ticking chunks with explicit entity
     * attachment. Call once at build completion after {@link #forcePasteAllSolids}.
     */
    public static void placeInteractiveBlockEntitiesFromPrefab(
        @Nonnull World world,
        @Nonnull Vector3i origin,
        @Nonnull Rotation yaw,
        @Nonnull IPrefabBuffer bufferAccess
    ) {
        PrefabSequence seq = buildSequence(bufferAccess, yaw);
        BlockTypeAssetMap<String, BlockType> blockTypeMap = BlockType.getAssetMap();
        int failed = 0;
        int placed = 0;
        for (PendingBlock pb : withoutPureAirCells(seq.pendingBlocks())) {
            if (!isInteractiveBlockEntityOrigin(pb, blockTypeMap)) {
                continue;
            }
            if (placeInteractiveBlockEntityCell(world, origin, pb, blockTypeMap)) {
                placed++;
            } else {
                failed++;
            }
        }
        if (failed > 0) {
            LOGGER.at(Level.WARNING).log(
                "Interactive block-entity placement at %s: %d ok, %d failed",
                origin,
                placed,
                failed
            );
        }
    }

    private static boolean placeInteractiveBlockEntityCell(
        @Nonnull World world,
        @Nonnull Vector3i origin,
        @Nonnull PendingBlock pb,
        @Nonnull BlockTypeAssetMap<String, BlockType> blockTypeMap
    ) {
        int bx = origin.x + pb.x();
        int by = origin.y + pb.y();
        int bz = origin.z + pb.z();
        BlockType block = blockTypeMap.getAsset(pb.blockId());
        if (block == null) {
            return false;
        }
        WorldChunk chunk = resolveTickingChunk(world, bx, bz);
        if (chunk == null || !chunk.getReference().isValid()) {
            LOGGER.at(Level.WARNING).log(
                "Interactive block placement: ticking chunk unavailable at %d,%d,%d (%s)",
                bx,
                by,
                bz,
                block.getId()
            );
            return false;
        }
        String blockKey = block.getId();
        BlockType worldBlock = BlockType.getAssetMap().getAsset(chunk.getBlock(bx, by, bz));
        boolean needsVoxel =
            worldBlock == null
                || worldBlock == BlockType.EMPTY
                || worldBlock.getId() == null
                || !blockKey.equals(worldBlock.getId());
        if (needsVoxel) {
            RotationTuple rot = RotationTuple.get(pb.blockRotation());
            if (!chunk.placeBlock(bx, by, bz, blockKey, rot, INTERACTIVE_BLOCK_PLACE_SETTINGS, false)) {
                LOGGER.at(Level.WARNING).log(
                    "Interactive block placement: placeBlock failed at %d,%d,%d (%s)",
                    bx,
                    by,
                    bz,
                    blockKey
                );
                return false;
            }
        }
        if (pb.supportValue() != 0) {
            Ref<ChunkStore> columnRef = chunk.getReference();
            if (columnRef.isValid()) {
                Store<ChunkStore> store = columnRef.getStore();
                Ref<ChunkStore> section = sectionRefForBlockY(chunk, by);
                if (section != null) {
                    BlockPhysics.setSupportValue(store, section, bx, by, bz, pb.supportValue());
                }
            }
        }
        chunk.setTicking(bx, by, bz, true);
        BlockComponentChunk blockComponents = chunk.getBlockComponentChunk();
        if (blockComponents == null) {
            return false;
        }
        Holder<ChunkStore> entityHolder = null;
        if (pb.holder() != null) {
            entityHolder = pb.holder().clone();
        } else if (block.getBlockEntity() != null) {
            entityHolder = block.getBlockEntity().clone();
        }
        if (entityHolder == null) {
            // No prefab/template state to attach; keep whatever placeBlock already created.
            Ref<ChunkStore> existing = chunk.getBlockComponentEntity(bx, by, bz);
            return existing != null && existing.isValid();
        }
        // Always apply the prefab holder. placeBlock may have already spawned an empty template
        // entity (e.g. aquariums); returning early left stocked prefab contents unapplied.
        com.hypixel.hytale.server.core.modules.block.BlockEntity.setBlockEntity(
            world.getChunkStore().getStore(),
            chunk.getReference(),
            blockComponents,
            bx,
            by,
            bz,
            block,
            pb.blockRotation(),
            entityHolder
        );
        Ref<ChunkStore> attached = chunk.getBlockComponentEntity(bx, by, bz);
        if (attached == null || !attached.isValid()) {
            LOGGER.at(Level.WARNING).log(
                "Interactive block placement: block entity missing after attach at %d,%d,%d (%s)",
                bx,
                by,
                bz,
                blockKey
            );
            return false;
        }
        return true;
    }

    @Nullable
    private static WorldChunk resolveTickingChunk(@Nonnull World world, int bx, int bz) {
        long chunkIndex = ChunkUtil.indexChunkFromBlock(bx, bz);
        WorldChunk chunk = world.getChunk(chunkIndex);
        if (chunk != null) {
            return chunk;
        }
        if (!world.isInThread()) {
            return CompletableFuture.supplyAsync(() -> resolveTickingChunk(world, bx, bz), world).join();
        }
        try {
            return world.getChunkAsync(chunkIndex).join();
        } catch (RuntimeException e) {
            LOGGER.at(Level.WARNING).withCause(e).log("Failed to load ticking chunk %d for interactive block", chunkIndex);
            return null;
        }
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
        return LocalCachedChunkAccessor.atWorldCoords(world, origin.x(), origin.z(), prefabRadius);
    }

    /**
     * Before assembly: clear the entire prefab footprint to air (including {@code filler != 0} furniture / multi-block
     * cells) so existing terrain does not float inside the volume until those indices reach the placement frontier.
     * Does not place final prefab solids, fluids, or entities — prefab fluids are written at completion via
     * {@link #finishFluidsAndEntities}.
     */
    /**
     * Clears world fluids in footprint cells whose prefab has no fluid, so interiors are not left flooded after the
     * manual clearing phase. Prefab fluids themselves are applied later in {@link #finishFluidsAndEntities}.
     */
    public static void clearNonPrefabFluidsInFootprint(
        @Nonnull World world,
        @Nonnull Vector3i origin,
        @Nonnull List<PendingBlock> footprint,
        @Nonnull LocalCachedChunkAccessor chunkAccessor
    ) {
        for (PendingBlock pb : footprint) {
            if (pb.fluidId() != 0) {
                continue;
            }
            int bx = origin.x + pb.x();
            int by = origin.y + pb.y();
            int bz = origin.z + pb.z();
            applyPrefabFluidForCell(world, bx, by, bz, 0, 0, chunkAccessor);
        }
    }

    public static void prepAssemblySite(
        @Nonnull World world,
        @Nonnull Vector3i origin,
        @Nonnull List<PendingBlock> pending,
        boolean force,
        @Nonnull PrefabRotation prefabRotation,
        @Nonnull IPrefabBuffer bufferAccess
    ) {
        LocalCachedChunkAccessor chunkAccessor = createAccessor(world, origin, bufferAccess);
        for (PendingBlock pb : pending) {
            int bx = origin.x + pb.x;
            int by = origin.y + pb.y;
            int bz = origin.z + pb.z;
            WorldChunk chunk = chunkAccessor.getNonTickingChunk(ChunkUtil.indexChunkFromBlock(bx, bz));
            if (chunk == null || !chunk.getReference().isValid()) {
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

    /**
     * @return false if the target chunk column is not available (e.g. unloaded mid tick); caller should retry later
     *     with a fresh {@link LocalCachedChunkAccessor}.
     */
    public static boolean placeOne(
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
        if (chunk == null || !chunk.getReference().isValid()) {
            return false;
        }
        BlockType block = blockTypeMap.getAsset(pb.blockId);
        if (block == null) {
            return false;
        }
        if (pb.filler == FillerBlockUtil.NO_FILLER && pb.blockId != 0 && isInteractiveBlockEntityOrigin(pb, blockTypeMap)) {
            return true;
        }
        String blockKey = block.getId();
        // Multi-block companions: never write a second origin voxel (that floats a duplicate wardrobe/chest).
        if (pb.filler != FillerBlockUtil.NO_FILLER) {
            if (pb.holder != null) {
                setBlockEntityHolder(world, chunk, bx, by, bz, block, pb.blockRotation, pb.holder.clone());
            }
            return true;
        }
        if (pb.blockId == 0) {
            if (force) {
                chunk.setBlock(bx, by, bz, BlockType.EMPTY_ID, BlockType.EMPTY, 0, 0, SET_BLOCK_SETTINGS_CLEAR);
            } else {
                chunk.breakBlock(bx, by, bz, SET_BLOCK_SETTINGS_CLEAR);
            }
            return true;
        }
        if (PrefabFootprintClearUtil.isProductionStorageBlockTypeId(blockKey)) {
            PrefabFootprintClearUtil.forceClearProductionStorageAt(world, bx, by, bz);
        }
        // Match PrefabUtil force-paste: setBlock writes attachables (wall torches) reliably; placeBlock can no-op
        // them when support/validation disagrees even with validatePlacement=false.
        if (force) {
            chunk.setBlock(
                bx,
                by,
                bz,
                pb.blockId,
                block,
                pb.blockRotation,
                FillerBlockUtil.NO_FILLER,
                SET_BLOCK_SETTINGS_PLACE
            );
        } else {
            RotationTuple rot = RotationTuple.get(pb.blockRotation);
            if (!chunk.placeBlock(bx, by, bz, blockKey, rot, SET_BLOCK_SETTINGS_PLACE, true)) {
                return false;
            }
        }
        if (pb.supportValue != 0) {
            Ref<ChunkStore> ref = chunk.getReference();
            if (!ref.isValid()) {
                return false;
            }
            Store<ChunkStore> store = ref.getStore();
            Ref<ChunkStore> section = sectionRefForBlockY(chunk, by);
            if (section != null) {
                BlockPhysics.setSupportValue(store, section, bx, by, bz, pb.supportValue);
            }
        }
        if (pb.holder != null) {
            setBlockEntityHolder(world, chunk, bx, by, bz, block, pb.blockRotation, pb.holder.clone());
        }
        return true;
    }

    /**
     * Force-writes every non-air prefab solid using {@link WorldChunk#setBlock} (same as {@code PrefabUtil} force
     * paste). Rebuilds placement order from {@code buffer} so completion does not depend on the incremental job list.
     * Filler cells only attach holders — never a second origin voxel (avoids floating duplicate furniture).
     */
    public static void forcePasteAllSolids(
        @Nonnull World world,
        @Nonnull Vector3i origin,
        @Nonnull Rotation yaw,
        @Nonnull IPrefabBuffer bufferAccess
    ) {
        PrefabSequence seq = buildSequence(bufferAccess, yaw);
        List<PendingBlock> cells = withoutPureAirCells(seq.pendingBlocks());
        LocalCachedChunkAccessor chunkAccessor = createAccessor(world, origin, bufferAccess);
        BlockTypeAssetMap<String, BlockType> blockTypeMap = BlockType.getAssetMap();
        for (int i = 0; i < cells.size(); i++) {
            PendingBlock pb = cells.get(i);
            if (!forceSetSolid(world, origin, pb, chunkAccessor, blockTypeMap)) {
                chunkAccessor = createAccessor(world, origin, bufferAccess);
                forceSetSolid(world, origin, pb, chunkAccessor, blockTypeMap);
            }
        }
    }

    private static boolean forceSetSolid(
        @Nonnull World world,
        @Nonnull Vector3i origin,
        @Nonnull PendingBlock pb,
        @Nonnull LocalCachedChunkAccessor chunkAccessor,
        @Nonnull BlockTypeAssetMap<String, BlockType> blockTypeMap
    ) {
        int bx = origin.x + pb.x();
        int by = origin.y + pb.y();
        int bz = origin.z + pb.z();
        WorldChunk chunk = chunkAccessor.getNonTickingChunk(ChunkUtil.indexChunkFromBlock(bx, bz));
        if (chunk == null || !chunk.getReference().isValid()) {
            return false;
        }
        if (pb.blockId() == 0) {
            chunk.setBlock(bx, by, bz, BlockType.EMPTY_ID, BlockType.EMPTY, 0, 0, SET_BLOCK_SETTINGS_CLEAR);
            return true;
        }
        BlockType block = blockTypeMap.getAsset(pb.blockId());
        if (block == null) {
            return false;
        }
        if (pb.filler() == FillerBlockUtil.NO_FILLER && isInteractiveBlockEntityOrigin(pb, blockTypeMap)) {
            return true;
        }
        // PrefabUtil: filler cells never place a block voxel — only component state on the multi-block volume.
        if (pb.filler() != FillerBlockUtil.NO_FILLER) {
            if (pb.holder() != null) {
                setBlockEntityHolder(world, chunk, bx, by, bz, block, pb.blockRotation(), pb.holder().clone());
            }
            return true;
        }
        String blockKey = block.getId();
        if (PrefabFootprintClearUtil.isProductionStorageBlockTypeId(blockKey)) {
            PrefabFootprintClearUtil.forceClearProductionStorageAt(world, bx, by, bz);
        }
        chunk.setBlock(
            bx,
            by,
            bz,
            pb.blockId(),
            block,
            pb.blockRotation(),
            FillerBlockUtil.NO_FILLER,
            SET_BLOCK_SETTINGS_PLACE
        );
        if (pb.supportValue() != 0) {
            Ref<ChunkStore> ref = chunk.getReference();
            if (ref.isValid()) {
                Store<ChunkStore> store = ref.getStore();
                Ref<ChunkStore> section = sectionRefForBlockY(chunk, by);
                if (section != null) {
                    BlockPhysics.setSupportValue(store, section, bx, by, bz, pb.supportValue());
                }
            }
        }
        if (pb.holder() != null) {
            setBlockEntityHolder(world, chunk, bx, by, bz, block, pb.blockRotation(), pb.holder().clone());
        }
        return true;
    }

    /**
     * Final construction pass: write every prefab fluid cell, then spawn prefab entities. Fluids are deferred here
     * (not written during {@link #placeOne} / {@link #forcePasteAllSolids}) so they appear when the build completes.
     */
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
            try {
                spawnPrefabEntityLikePaste(world, origin, prefabRotation, prefabId, entityAccessor, source);
            } catch (RuntimeException e) {
                // One bad prefab prop must not abort the rest of the completion pass.
                HytaleLogger.forEnclosingClass()
                    .atWarning()
                    .withCause(e)
                    .log("Failed to spawn prefab entity %d for prefabId %s", i, prefabId);
            }
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
        Ref<ChunkStore> chunkRef = chunk.getReference();
        if (!chunkRef.isValid()) {
            return;
        }
        com.hypixel.hytale.server.core.modules.block.BlockEntity.setBlockEntity(
            world.getChunkStore().getStore(),
            chunkRef,
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
        if (!columnRef.isValid()) {
            return null;
        }
        Store<ChunkStore> store = columnRef.getStore();
        ChunkColumn column = store.getComponent(columnRef, ChunkColumn.getComponentType());
        if (column == null) {
            return null;
        }
        Ref<ChunkStore> section = column.getSection(ChunkUtil.chunkCoordinate(blockY));
        return section != null && section.isValid() ? section : null;
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
        if (chunk == null || !chunk.getReference().isValid()) {
            return;
        }
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
        Vector3d w = new Vector3d(transformComp.getPosition());
        boolean blockEntity = clone.getComponent(BlockEntity.getComponentType()) != null;
        Vector3d centerOffset = blockEntity ? new Vector3d(0.5, 0.0, 0.5) : new Vector3d(0.5, 0.5, 0.5);
        w.sub(centerOffset);
        prefabRotation.rotate(w);
        w.add(centerOffset);
        w.add(origin.x, origin.y, origin.z);
        Vector3d pos = transformComp.getPosition();
        pos.x = w.x;
        pos.y = w.y;
        pos.z = w.z;
        float dyaw = prefabRotation.getYaw();
        if (prefabRotation == PrefabRotation.ROTATION_90 || prefabRotation == PrefabRotation.ROTATION_270) {
            dyaw += (float) Math.PI;
        }
        transformComp.getRotation().setYaw(transformComp.getRotation().yaw() + dyaw);
        HeadRotation headRotation = clone.getComponent(HeadRotation.getComponentType());
        if (headRotation != null) {
            headRotation.getRotation().setYaw(headRotation.getRotation().yaw() + dyaw);
        }
        PrefabPlaceEntityEvent prefabPlaceEntityEvent = new PrefabPlaceEntityEvent(prefabId, clone);
        entityAccessor.invoke(prefabPlaceEntityEvent);
        if (prefabPlaceEntityEvent.isCancelled()) {
            return;
        }
        clone.ensureComponent(FromPrefab.getComponentType());
        // Decorative prefab props (sign models, bench item props, etc.) are valid melee targets unless marked
        // Invulnerable. NPCs keep role-driven invulnerability; players must never be tagged here.
        if (clone.getComponent(NPCEntity.getComponentType()) == null
            && clone.getComponent(Player.getComponentType()) == null
            && clone.getComponent(Invulnerable.getComponentType()) == null) {
            clone.ensureComponent(Invulnerable.getComponentType());
        }
        entityAccessor.addEntity(clone, AddReason.LOAD);
    }
}
