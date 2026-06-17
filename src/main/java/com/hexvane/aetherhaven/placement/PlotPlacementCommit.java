package com.hexvane.aetherhaven.placement;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.plot.PlotSignBlock;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.modules.block.BlockEntity;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

public final class PlotPlacementCommit {
    /** Bit 2 skips automatic block-entity attachment in {@code placeBlock}; we attach explicitly on the world thread. */
    private static final int PLACE_SETTINGS = 10;

    private PlotPlacementCommit() {}

    /**
     * Places {@link AetherhavenConstants#PLOT_SIGN_ITEM_ID} at {@code anchor} with NESW rotation from {@code prefabYaw}
     * and {@link PlotSignBlock} construction id.
     */
    public static boolean placePlotSign(
        @Nonnull World world,
        int x,
        int y,
        int z,
        @Nonnull Rotation prefabYaw,
        @Nonnull String constructionId,
        @Nonnull UUID plotId,
        @Nonnull Store<EntityStore> entityStore
    ) {
        if (world.isInThread()) {
            return placePlotSignOnWorldThread(world, x, y, z, prefabYaw, constructionId, plotId);
        }
        return CompletableFuture.supplyAsync(
                () -> placePlotSignOnWorldThread(world, x, y, z, prefabYaw, constructionId, plotId),
                world
            )
            .join();
    }

    private static boolean placePlotSignOnWorldThread(
        @Nonnull World world,
        int x,
        int y,
        int z,
        @Nonnull Rotation prefabYaw,
        @Nonnull String constructionId,
        @Nonnull UUID plotId
    ) {
        WorldChunk chunk = world.getChunk(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            return false;
        }
        BlockType blockType = BlockType.getAssetMap().getAsset(AetherhavenConstants.PLOT_SIGN_ITEM_ID);
        if (blockType == null) {
            return false;
        }
        RotationTuple rt = RotationTuple.of(prefabYaw, Rotation.None, Rotation.None);
        int rotationIndex = rt.index();
        // validatePlacement=false: replace existing blocks/fluids at the anchor (same idea as paste replacing voxels).
        boolean ok =
            chunk.placeBlock(x, y, z, AetherhavenConstants.PLOT_SIGN_ITEM_ID, rt, PLACE_SETTINGS, false);
        if (!ok) {
            return false;
        }
        chunk.setTicking(x, y, z, true);

        BlockComponentChunk blockComponentChunk = chunk.getBlockComponentChunk();
        if (blockComponentChunk == null) {
            world.breakBlock(x, y, z, PLACE_SETTINGS);
            return false;
        }

        String plotIdStr = plotId.toString();
        if (!attachPlotSignBlockEntity(
            world, chunk, blockComponentChunk, x, y, z, blockType, rotationIndex, constructionId, plotIdStr
        )) {
            world.breakBlock(x, y, z, PLACE_SETTINGS);
            return false;
        }
        return true;
    }

    /**
     * Attaches {@link PlotSignBlock} via {@link BlockEntity#setBlockEntity} when missing (PLACE_SETTINGS skips
     * automatic attachment), then writes plot metadata on the live ref or pending holder.
     */
    private static boolean attachPlotSignBlockEntity(
        @Nonnull World world,
        @Nonnull WorldChunk chunk,
        @Nonnull BlockComponentChunk blockComponentChunk,
        int x,
        int y,
        int z,
        @Nonnull BlockType blockType,
        int rotationIndex,
        @Nonnull String constructionId,
        @Nonnull String plotIdStr
    ) {
        int index = ChunkUtil.indexBlockInColumn(x, y, z);
        if (blockComponentChunk.getComponent(index, PlotSignBlock.getComponentType()) == null) {
            Holder<ChunkStore> template = blockType.getBlockEntity();
            if (template == null) {
                return false;
            }
            Holder<ChunkStore> holder = template.clone();
            holder.putComponent(
                PlotSignBlock.getComponentType(), new PlotSignBlock(constructionId, plotIdStr)
            );
            Ref<ChunkStore> chunkRef = chunk.getReference();
            if (chunkRef == null) {
                return false;
            }
            BlockEntity.setBlockEntity(
                world.getChunkStore().getStore(),
                chunkRef,
                blockComponentChunk,
                x,
                y,
                z,
                blockType,
                rotationIndex,
                holder
            );
        }
        return writePlotSignMetadata(chunk, blockComponentChunk, x, y, z, constructionId, plotIdStr);
    }

    private static boolean writePlotSignMetadata(
        @Nonnull WorldChunk chunk,
        @Nonnull BlockComponentChunk blockComponentChunk,
        int x,
        int y,
        int z,
        @Nonnull String constructionId,
        @Nonnull String plotIdStr
    ) {
        Ref<ChunkStore> signRef = chunk.getBlockComponentEntity(x, y, z);
        if (signRef != null && signRef.isValid()) {
            signRef.getStore()
                .putComponent(
                    signRef, PlotSignBlock.getComponentType(), new PlotSignBlock(constructionId, plotIdStr)
                );
            return true;
        }
        int index = ChunkUtil.indexBlockInColumn(x, y, z);
        PlotSignBlock onHolder = blockComponentChunk.getComponent(index, PlotSignBlock.getComponentType());
        if (onHolder != null) {
            onHolder.setConstructionId(constructionId);
            onHolder.setPlotId(plotIdStr);
            return true;
        }
        return false;
    }

    /** Replaces an existing plot sign (same cell) with a new construction id and placement yaw. */
    public static boolean replacePlotSign(
        @Nonnull World world,
        int x,
        int y,
        int z,
        @Nonnull Rotation prefabYaw,
        @Nonnull String constructionId,
        @Nonnull UUID plotId,
        @Nonnull Store<EntityStore> entityStore
    ) {
        if (world.isInThread()) {
            world.breakBlock(x, y, z, PLACE_SETTINGS);
            return placePlotSignOnWorldThread(world, x, y, z, prefabYaw, constructionId, plotId);
        }
        return CompletableFuture.supplyAsync(
                () -> {
                    world.breakBlock(x, y, z, PLACE_SETTINGS);
                    return placePlotSignOnWorldThread(world, x, y, z, prefabYaw, constructionId, plotId);
                },
                world
            )
            .join();
    }
}
