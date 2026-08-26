package com.hexvane.aetherhaven.construction.assembly;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;

import com.hexvane.aetherhaven.construction.ConstructionPasteOps;
import com.hexvane.aetherhaven.construction.ConstructionPasteOps.PendingBlock;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import org.joml.Vector3i;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.LocalCachedChunkAccessor;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Footprint obstruction checks for the assembly clearing phase. */
public final class AssemblyObstructionUtil {
    private AssemblyObstructionUtil() {}

    @Nullable
    private static BlockType blockTypeAt(
        @Nonnull World world,
        int wx,
        int wy,
        int wz,
        @Nonnull LocalCachedChunkAccessor chunkAccessor
    ) {
        WorldChunk chunk = ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(wx, wz));
        if (chunk == null || !chunk.getReference().isValid()) {
            return null;
        }
        int blockId = chunk.getBlock(wx, wy, wz);
        return BlockType.getAssetMap().getAsset(blockId);
    }

    public static boolean isObstructedAt(
        @Nonnull World world,
        @Nonnull Vector3i anchor,
        int wx,
        int wy,
        int wz,
        @Nonnull LocalCachedChunkAccessor chunkAccessor
    ) {
        BlockType bt = blockTypeAt(world, wx, wy, wz, chunkAccessor);
        return bt != null && bt != BlockType.EMPTY;
    }

    /**
     * Solid terrain/obstructions that seal a face for clearing-frontier purposes. {@code Plant_Grass} foliage and other
     * soft plants still count as {@link #isObstructedAt} but do not hide solid blocks below from the frontier.
     */
    public static boolean blocksClearingExposureAt(
        @Nonnull World world,
        int wx,
        int wy,
        int wz,
        @Nonnull LocalCachedChunkAccessor chunkAccessor
    ) {
        BlockType bt = blockTypeAt(world, wx, wy, wz, chunkAccessor);
        if (bt == null || bt == BlockType.EMPTY) {
            return false;
        }
        return !isSoftClearingSkippedBlock(bt);
    }

    /**
     * Foliage the clearing frontier ignores ({@link #blocksClearingExposureAt}) — removed automatically before the
     * manual clearing phase so they do not linger after solids are broken.
     */
    public static boolean isSoftClearingSkippedBlock(@Nullable BlockType bt) {
        if (bt == null || bt == BlockType.EMPTY) {
            return false;
        }
        if (isPlantGrassBlock(bt)) {
            return true;
        }
        if (bt.getMaterial() == BlockMaterial.Empty) {
            return true;
        }
        BlockGathering gathering = bt.getGathering();
        return gathering != null && gathering.getSoft() != null;
    }

    /** Silently clears {@link #isSoftClearingSkippedBlock} cells inside the loaded prefab footprint. */
    public static void clearSoftSkippedBlocksInFootprint(@Nonnull World world, @Nonnull PlotAssemblyJob job) {
        clearSoftSkippedBlocksInFootprint(world, job, null, 0);
    }

    /**
     * Like {@link #clearSoftSkippedBlocksInFootprint(World, PlotAssemblyJob)} but only for one assembly section when
     * {@code sectionMapper} is non-null.
     */
    public static void clearSoftSkippedBlocksInFootprint(
        @Nonnull World world,
        @Nonnull PlotAssemblyJob job,
        @Nullable AssemblySectionMapper sectionMapper,
        int flatSection
    ) {
        LocalCachedChunkAccessor chunkAccessor =
            ConstructionPasteOps.createAccessor(world, job.anchor(), job.buffer());
        Vector3i anchor = job.anchor();
        List<PendingBlock> footprint = job.footprintCells();
        for (int i = 0; i < footprint.size(); i++) {
            PendingBlock pb = footprint.get(i);
            if (sectionMapper != null && !sectionMapper.isCellInSection(pb, flatSection)) {
                continue;
            }
            int wx = anchor.x + pb.x();
            int wy = anchor.y + pb.y();
            int wz = anchor.z + pb.z();
            WorldChunk chunk = ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(wx, wz));
            if (chunk == null || !chunk.getReference().isValid()) {
                continue;
            }
            BlockType bt = blockTypeAt(world, wx, wy, wz, chunkAccessor);
            if (!isSoftClearingSkippedBlock(bt)) {
                continue;
            }
            chunk.setBlock(
                wx,
                wy,
                wz,
                BlockType.EMPTY_ID,
                BlockType.EMPTY,
                0,
                0,
                ConstructionPasteOps.SET_BLOCK_SETTINGS_CLEAR
            );
        }
    }

    /** Decorative/tall grass ({@code Plant_Grass_*}) — not the same as solid {@code Soil_Grass} ground blocks. */
    public static boolean isPlantGrassBlock(@Nullable BlockType bt) {
        if (bt == null || bt == BlockType.EMPTY) {
            return false;
        }
        String id = bt.getId();
        return id != null && id.contains("Plant_Grass");
    }

    public static boolean isObstructedFootprintCell(
        @Nonnull World world,
        @Nonnull PlotAssemblyJob job,
        @Nonnull Vector3i cellWorld
    ) {
        LocalCachedChunkAccessor chunkAccessor =
            ConstructionPasteOps.createAccessor(world, job.anchor(), job.buffer());
        return isObstructedFootprintCell(world, job, cellWorld, chunkAccessor);
    }

    public static boolean isObstructedFootprintCell(
        @Nonnull World world,
        @Nonnull PlotAssemblyJob job,
        @Nonnull Vector3i cellWorld,
        @Nonnull LocalCachedChunkAccessor chunkAccessor
    ) {
        if (!footprintContainsWorldCell(job, cellWorld)) {
            return false;
        }
        return isObstructedAt(world, job.anchor(), cellWorld.x, cellWorld.y, cellWorld.z, chunkAccessor);
    }

    public static boolean footprintContainsWorldCell(@Nonnull PlotAssemblyJob job, @Nonnull Vector3i cellWorld) {
        return job.footprintIndex().containsWorldCell(job.anchor(), cellWorld);
    }

    /** {@code true} when every loaded footprint cell is air (unloaded columns are ignored). */
    public static boolean isFootprintClearInLoadedChunks(@Nonnull World world, @Nonnull PlotAssemblyJob job) {
        LocalCachedChunkAccessor chunkAccessor =
            ConstructionPasteOps.createAccessor(world, job.anchor(), job.buffer());
        List<PendingBlock> footprint = job.footprintCells();
        Vector3i anchor = job.anchor();
        for (int i = 0; i < footprint.size(); i++) {
            PendingBlock pb = footprint.get(i);
            int wx = anchor.x + pb.x();
            int wy = anchor.y + pb.y();
            int wz = anchor.z + pb.z();
            WorldChunk chunk = ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(wx, wz));
            if (chunk == null || !chunk.getReference().isValid()) {
                continue;
            }
            if (isObstructedAt(world, anchor, wx, wy, wz, chunkAccessor)) {
                return false;
            }
        }
        return true;
    }

    /** {@code true} when any loaded footprint cell still holds a solid block. */
    public static boolean hasObstructionsInLoadedChunks(@Nonnull World world, @Nonnull PlotAssemblyJob job) {
        return !isFootprintClearInLoadedChunks(world, job);
    }

    /**
     * Section-aware obstruction probe: scans sections in order and returns on first obstruction (avoids full-footprint
     * reads when the first occupied section is already blocked).
     */
    public static boolean hasObstructionsInLoadedChunks(
        @Nonnull World world,
        @Nonnull PlotAssemblyJob job,
        @Nullable AssemblySectionMapper sectionMapper
    ) {
        if (sectionMapper == null) {
            return hasObstructionsInLoadedChunks(world, job);
        }
        LocalCachedChunkAccessor chunkAccessor =
            ConstructionPasteOps.createAccessor(world, job.anchor(), job.buffer());
        Vector3i anchor = job.anchor();
        List<PendingBlock> footprint = job.footprintCells();
        int vol = sectionMapper.sectionCount();
        for (int s = 0; s < vol; s++) {
            for (int i = 0; i < footprint.size(); i++) {
                PendingBlock pb = footprint.get(i);
                if (!sectionMapper.isCellInSection(pb, s)) {
                    continue;
                }
                int wx = anchor.x + pb.x();
                int wy = anchor.y + pb.y();
                int wz = anchor.z + pb.z();
                WorldChunk chunk = ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(wx, wz));
                if (chunk == null || !chunk.getReference().isValid()) {
                    continue;
                }
                if (isObstructedAt(world, anchor, wx, wy, wz, chunkAccessor)) {
                    return true;
                }
            }
        }
        return false;
    }
}
