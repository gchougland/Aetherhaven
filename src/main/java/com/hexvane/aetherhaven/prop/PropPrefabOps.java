package com.hexvane.aetherhaven.prop;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;

import com.hexvane.aetherhaven.construction.ConstructionPasteOps;
import com.hexvane.aetherhaven.construction.ConstructionPasteOps.PendingBlock;
import com.hexvane.aetherhaven.construction.ConstructionPrefabSequence;
import com.hexvane.aetherhaven.placement.PlotFootprintUtil;
import com.hexvane.aetherhaven.placement.PrefabTriggerVolumeCleanup;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hypixel.hytale.assetstore.map.BlockTypeAssetMap;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.PrefabRotation;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import java.nio.file.Path;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/**
 * Prop prefab paste / remove / validity checks, built on {@link ConstructionPasteOps}. Solids, interactive block
 * entities, and fluids are handled here; entity pasting/removal lives in {@link PropEntityOps}.
 */
public final class PropPrefabOps {
    /**
     * Teardown clear must update block-entity state; matches {@link com.hexvane.aetherhaven.placement.PrefabFootprintClearUtil}'s
     * {@code FORCE_CLEAR_SETTINGS} (that method is private there, so it is replicated here).
     */
    private static final int FORCE_CLEAR_SETTINGS =
        SetBlockSettings.NO_SEND_PARTICLES | SetBlockSettings.NO_DROP_ITEMS;

    private PropPrefabOps() {}

    /**
     * Pastes every solid cell of the prop prefab at {@code origin} (world-space prefab corner), plus interactive block
     * entities and prefab fluid cells (liquids are not written by {@link ConstructionPasteOps#forcePasteAllSolids}).
     */
    public static void pasteSolidsOnly(
        @Nonnull World world,
        @Nonnull Vector3i origin,
        @Nonnull Rotation yaw,
        @Nonnull IPrefabBuffer buffer
    ) {
        ConstructionPasteOps.forcePasteAllSolids(world, origin, yaw, false, buffer);
        ConstructionPasteOps.placeInteractiveBlockEntitiesFromPrefab(world, origin, yaw, buffer);
        ConstructionPasteOps.applyCompletionFluids(
            world, origin, PrefabRotation.fromRotation(yaw), false, buffer
        );
    }

    /**
     * Clears every origin solid cell that still matches the prefab's expected block (skips cells a player has since
     * edited), then clears fluids listed in the prefab footprint.
     */
    public static void removeSolidsOnly(
        @Nonnull World world,
        @Nonnull Vector3i origin,
        @Nonnull Rotation yaw,
        @Nonnull IPrefabBuffer buffer
    ) {
        ConstructionPrefabSequence seq = ConstructionPasteOps.buildSequence(buffer, yaw);
        BlockTypeAssetMap<String, BlockType> blockTypeMap = BlockType.getAssetMap();
        for (PendingBlock pb : ConstructionPasteOps.withoutPureAirCells(seq.pendingBlocks())) {
            if (!isOriginSolidCell(pb)) {
                continue;
            }
            BlockType expected = blockTypeMap.getAsset(pb.blockId());
            if (expected == null || expected.getId() == null) {
                continue;
            }
            int bx = origin.x + pb.x();
            int by = origin.y + pb.y();
            int bz = origin.z + pb.z();
            BlockType worldBlock = blockTypeAt(world, bx, by, bz);
            if (worldBlock == null || worldBlock.getId() == null || !worldBlock.getId().equals(expected.getId())) {
                continue;
            }
            clearBlockCell(world, bx, by, bz);
        }
        ConstructionPasteOps.clearAllFluidsInPrefabFootprint(
            world, origin, seq.pendingBlocks(), false
        );
    }

    /**
     * Strict placement check: every origin solid destination must be empty ({@link BlockType#EMPTY} or
     * {@link BlockMaterial#Empty}). Air cells in the prefab are ignored. Unloaded chunks fail the check (safer than
     * assuming empty).
     */
    public static boolean canPlaceSolids(
        @Nonnull World world,
        @Nonnull Vector3i origin,
        @Nonnull Rotation yaw,
        @Nonnull IPrefabBuffer buffer
    ) {
        ConstructionPrefabSequence seq = ConstructionPasteOps.buildSequence(buffer, yaw);
        for (PendingBlock pb : ConstructionPasteOps.withoutPureAirCells(seq.pendingBlocks())) {
            if (!isOriginSolidCell(pb)) {
                continue;
            }
            int bx = origin.x + pb.x();
            int by = origin.y + pb.y();
            int bz = origin.z + pb.z();
            BlockType worldBlock = blockTypeAt(world, bx, by, bz);
            if (worldBlock == null) {
                return false;
            }
            if (worldBlock != BlockType.EMPTY && worldBlock.getMaterial() != BlockMaterial.Empty) {
                return false;
            }
        }
        return true;
    }

    /** True when every origin solid cell still matches the prefab's expected block (nothing has been dug out/replaced). */
    public static boolean isIntact(
        @Nonnull World world,
        @Nonnull Vector3i origin,
        @Nonnull Rotation yaw,
        @Nonnull IPrefabBuffer buffer
    ) {
        ConstructionPrefabSequence seq = ConstructionPasteOps.buildSequence(buffer, yaw);
        BlockTypeAssetMap<String, BlockType> blockTypeMap = BlockType.getAssetMap();
        for (PendingBlock pb : ConstructionPasteOps.withoutPureAirCells(seq.pendingBlocks())) {
            if (!isOriginSolidCell(pb)) {
                continue;
            }
            BlockType expected = blockTypeMap.getAsset(pb.blockId());
            if (expected == null || expected.getId() == null) {
                continue;
            }
            int bx = origin.x + pb.x();
            int by = origin.y + pb.y();
            int bz = origin.z + pb.z();
            BlockType worldBlock = blockTypeAt(world, bx, by, bz);
            if (worldBlock == null || worldBlock.getId() == null || !worldBlock.getId().equals(expected.getId())) {
                return false;
            }
        }
        return true;
    }

    /** True when this world block cell is one of the prop's origin solid voxels at its current placement. */
    public static boolean blockBelongsToProp(
        @Nonnull Vector3i origin,
        @Nonnull Rotation yaw,
        @Nonnull IPrefabBuffer buffer,
        int x,
        int y,
        int z
    ) {
        PlotFootprintRecord fp = footprint(origin, yaw, buffer);
        if (!fp.containsBlock(x, y, z)) {
            return false;
        }
        ConstructionPrefabSequence seq = ConstructionPasteOps.buildSequence(buffer, yaw);
        for (PendingBlock pb : ConstructionPasteOps.withoutPureAirCells(seq.pendingBlocks())) {
            if (!isOriginSolidCell(pb)) {
                continue;
            }
            if (origin.x + pb.x() == x && origin.y + pb.y() == y && origin.z + pb.z() == z) {
                return true;
            }
        }
        return false;
    }

    /** True when the prefab has at least one origin solid voxel (i.e. is not empty/decorative-only air). */
    public static boolean hasOriginSolids(@Nonnull Rotation yaw, @Nonnull IPrefabBuffer buffer) {
        return PlotFootprintUtil.hasSolidVoxels(yaw, buffer);
    }

    /** True when the prefab lists at least one entity in the buffer columns or the prefab file's entity list. */
    public static boolean hasPrefabEntities(@Nonnull IPrefabBuffer buffer) {
        return hasPrefabEntities(buffer, null);
    }

    /**
     * True when the prefab has entities. NPCs and other holders often live only on {@link
     * com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection}, not in the cached buffer columns used by
     * {@link ConstructionPasteOps#buildSequence}.
     */
    public static boolean hasPrefabEntities(@Nonnull IPrefabBuffer buffer, @Nullable String prefabPathKey) {
        if (!ConstructionPasteOps.buildSequence(buffer, Rotation.None).prefabEntitiesInOrder().isEmpty()) {
            return true;
        }
        return blockSelectionEntityCount(prefabPathKey) > 0;
    }

    private static int blockSelectionEntityCount(@Nullable String prefabPathKey) {
        if (prefabPathKey == null || prefabPathKey.isBlank()) {
            return 0;
        }
        Path path = PrefabResolveUtil.resolvePrefabPath(prefabPathKey);
        if (path == null) {
            return 0;
        }
        try {
            var selection = PrefabStore.get().getPrefab(path);
            return selection != null ? selection.getEntityCount() : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /**
     * Placement wireframe / validity outline: solid origin voxels when present. Entity-only props use a single block at
     * the placement anchor. Packaging overlays still use {@link #footprint}.
     */
    @Nonnull
    public static PlotFootprintRecord placementOutlineFootprint(
        @Nonnull Vector3i origin,
        @Nonnull Rotation yaw,
        @Nonnull IPrefabBuffer buffer
    ) {
        if (!hasOriginSolids(yaw, buffer)) {
            return unitFootprint(origin);
        }
        return PlotFootprintUtil.computeFootprint(origin, yaw, buffer);
    }

    @Nonnull
    public static PlotFootprintRecord footprint(@Nonnull Vector3i origin, @Nonnull Rotation yaw, @Nonnull IPrefabBuffer buffer) {
        // Entity-only props have no reserved solid volume; use a 1x1x1 pick/protect box at the anchor.
        if (!hasOriginSolids(yaw, buffer)) {
            return unitFootprint(origin);
        }
        // Reserved prefab volume (empty cells included). Used for packaging pick / padded visual cubes and exact
        // entity leftover sweeps. Solid-only footprints can miss furniture companions that still sit in the volume.
        return PrefabTriggerVolumeCleanup.prefabBox(origin, yaw, buffer);
    }

    @Nonnull
    private static PlotFootprintRecord unitFootprint(@Nonnull Vector3i origin) {
        return new PlotFootprintRecord(origin.x, origin.y, origin.z, origin.x, origin.y, origin.z);
    }

    private static boolean isOriginSolidCell(@Nonnull PendingBlock pb) {
        return pb.filler() == FillerBlockUtil.NO_FILLER && pb.blockId() != 0;
    }

    /**
     * Reads block type without promoting chunks to ticking (safe off the world tick); returns {@code null} when the
     * chunk is not currently in memory.
     */
    @Nullable
    private static BlockType blockTypeAt(@Nonnull World world, int x, int y, int z) {
        if (y < 0 || y >= 320) {
            return null;
        }
        if (ChunkSectionBlockUtil.sectionRefAt(world, x, y, z) == null) {
            return null;
        }
        return ChunkSectionBlockUtil.blockType(world, x, y, z);
    }

    /** Mirrors {@code PrefabFootprintClearUtil#forceClearBlockCell} (private there). */
    private static void clearBlockCell(@Nonnull World world, int x, int y, int z) {
        if (ChunkSectionBlockUtil.sectionRefAt(world, x, y, z) == null) {
            return;
        }
        Ref<ChunkStore> blockEntityRef = ChunkSectionBlockUtil.blockEntityRefAt(world, x, y, z);
        ChunkSectionBlockUtil.setBlockEmpty(world, x, y, z, FORCE_CLEAR_SETTINGS);
        if (blockEntityRef != null && blockEntityRef.isValid()) {
            world.getChunkStore().getStore().removeEntity(blockEntityRef, RemoveReason.REMOVE);
        }
    }
}
