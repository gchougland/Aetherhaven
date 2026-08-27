package com.hexvane.aetherhaven.placement;

import com.hexvane.aetherhaven.pathtool.PathCementService;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.plot.PlotBlockRotationUtil;
import com.hexvane.aetherhaven.plot.PlotSignBlock;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.modules.block.BlockEntity;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import org.joml.Vector3i;

public final class PlotPlacementCommit {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    /** Bit 2 skips automatic block-entity attachment in {@code placeBlock}; we attach explicitly on the world thread. */
    private static final int PLACE_SETTINGS = 10;

    public enum LinkRepairResult {
        ALREADY_OK,
        RELINKED,
        PLACED,
        SKIPPED_CHUNK_UNLOADED,
        NOT_APPLICABLE,
        FAILED
    }

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
        WorldChunk chunk = ChunkSectionBlockUtil.resolveTickingChunk(world, x, z);
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
            PathCementService.placePathBlock(world, x, y, z, AetherhavenConstants.PLOT_SIGN_ITEM_ID, rotationIndex, PLACE_SETTINGS);
        if (!ok) {
            return false;
        }
        ChunkSectionBlockUtil.setTicking(world, x, y, z, true);

        BlockComponentSection blockComponentSection = ChunkSectionBlockUtil.blockComponentSectionAt(world, x, y, z);
        if (blockComponentSection == null) {
            ChunkSectionBlockUtil.breakBlock(world, x, y, z, PLACE_SETTINGS);
            return false;
        }

        String plotIdStr = plotId.toString();
        if (!attachPlotSignBlockEntity(
            world, chunk, blockComponentSection, x, y, z, blockType, rotationIndex, constructionId, plotIdStr
        )) {
            ChunkSectionBlockUtil.breakBlock(world, x, y, z, PLACE_SETTINGS);
            return false;
        }
        return true;
    }

    /**
     * Attaches {@link PlotSignBlock} via {@link BlockEntity#setBlockEntity} when the live block-entity ref is
     * missing (PLACE_SETTINGS skips automatic attachment), then writes plot metadata on the live ref.
     */
    private static boolean attachPlotSignBlockEntity(
        @Nonnull World world,
        @Nonnull WorldChunk chunk,
        @Nonnull BlockComponentSection blockComponentSection,
        int x,
        int y,
        int z,
        @Nonnull BlockType blockType,
        int rotationIndex,
        @Nonnull String constructionId,
        @Nonnull String plotIdStr
    ) {
        ChunkSectionBlockUtil.setTicking(world, x, y, z, true);
        Ref<ChunkStore> signRef = ChunkSectionBlockUtil.blockEntityRefAt(world, x, y, z);
        if (signRef == null || !signRef.isValid()) {
            Holder<ChunkStore> template = blockType.getBlockEntity();
            if (template == null) {
                return false;
            }
            Holder<ChunkStore> holder = template.clone();
            holder.putComponent(
                PlotSignBlock.getComponentType(), new PlotSignBlock(constructionId, plotIdStr)
            );
            Ref<ChunkStore> sectionRef = ChunkSectionBlockUtil.sectionRefAt(world, x, y, z);
            if (sectionRef == null || !sectionRef.isValid() || world.getChunkStore() == null) {
                return false;
            }
            BlockEntity.setBlockEntity(
                world.getChunkStore().getStore(),
                sectionRef,
                blockComponentSection,
                x,
                y,
                z,
                blockType,
                rotationIndex,
                holder
            );
        }
        signRef = ChunkSectionBlockUtil.blockEntityRefAt(world, x, y, z);
        if (signRef == null || !signRef.isValid()) {
            return false;
        }
        signRef.getStore()
            .putComponent(signRef, PlotSignBlock.getComponentType(), new PlotSignBlock(constructionId, plotIdStr));
        return true;
    }

    /**
     * Re-links or re-places the plot sign for a {@link PlotInstanceState#BLUEPRINTING} plot. Used by
     * {@code /ah plots repair}.
     */
    @Nonnull
    public static LinkRepairResult repairPlotSignLink(@Nonnull World world, @Nonnull PlotInstance plot) {
        if (plot.getState() != PlotInstanceState.BLUEPRINTING) {
            return LinkRepairResult.NOT_APPLICABLE;
        }
        if (world.isInThread()) {
            return repairPlotSignLinkOnWorldThread(world, plot);
        }
        return CompletableFuture.supplyAsync(() -> repairPlotSignLinkOnWorldThread(world, plot), world).join();
    }

    @Nonnull
    private static LinkRepairResult repairPlotSignLinkOnWorldThread(
        @Nonnull World world, @Nonnull PlotInstance plot
    ) {
        int x = plot.getSignX();
        int y = plot.getSignY();
        int z = plot.getSignZ();
        String constructionId = plot.getConstructionId() != null ? plot.getConstructionId() : "";
        String plotIdStr = plot.getPlotId().toString();
        Rotation yaw = plot.resolvePrefabYaw();

        WorldChunk chunk = ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            return LinkRepairResult.SKIPPED_CHUNK_UNLOADED;
        }

        BlockType atType = ChunkSectionBlockUtil.blockType(world, x, y, z);
        boolean blockIsSign =
            atType != null && AetherhavenConstants.PLOT_SIGN_ITEM_ID.equals(atType.getId());

        if (blockIsSign) {
            if (isPlotSignLinked(world, x, y, z, constructionId, plotIdStr)) {
                return LinkRepairResult.ALREADY_OK;
            }
            BlockType blockType = atType;
            BlockComponentSection blockComponentSection = ChunkSectionBlockUtil.blockComponentSectionAt(world, x, y, z);
            if (blockComponentSection == null) {
                LOGGER.atWarning().log(
                    "Plot sign link repair failed at %s,%s,%s plot=%s (no block component chunk)",
                    x,
                    y,
                    z,
                    plotIdStr
                );
                return LinkRepairResult.FAILED;
            }
            if (!attachPlotSignBlockEntity(
                world,
                chunk,
                blockComponentSection,
                x,
                y,
                z,
                blockType,
                PlotBlockRotationUtil.readBlockRotationIndex(world, new Vector3i(x, y, z)),
                constructionId,
                plotIdStr
            )) {
                LOGGER.atWarning().log(
                    "Plot sign link repair failed at %s,%s,%s plot=%s (block entity attach)",
                    x,
                    y,
                    z,
                    plotIdStr
                );
                return LinkRepairResult.FAILED;
            }
            return LinkRepairResult.RELINKED;
        }

        BlockType emptyCheck = atType;
        if (emptyCheck != null && emptyCheck.getMaterial() != BlockMaterial.Empty) {
            return LinkRepairResult.FAILED;
        }
        if (!placePlotSignOnWorldThread(world, x, y, z, yaw, constructionId, plot.getPlotId())) {
            LOGGER.atWarning().log(
                "Plot sign place/link repair failed at %s,%s,%s plot=%s",
                x,
                y,
                z,
                plotIdStr
            );
            return LinkRepairResult.FAILED;
        }
        return LinkRepairResult.PLACED;
    }

    private static boolean isPlotSignLinked(
        @Nonnull World world,
        int x,
        int y,
        int z,
        @Nonnull String constructionId,
        @Nonnull String plotIdStr
    ) {
        Ref<ChunkStore> signRef = ChunkSectionBlockUtil.blockEntityRefAt(world, x, y, z);
        if (signRef == null || !signRef.isValid()) {
            return false;
        }
        PlotSignBlock existing = signRef.getStore().getComponent(signRef, PlotSignBlock.getComponentType());
        return existing != null
            && constructionId.equals(existing.getConstructionId())
            && plotIdStr.equals(existing.getPlotId());
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
            ChunkSectionBlockUtil.breakBlock(world, x, y, z, PLACE_SETTINGS);
            return placePlotSignOnWorldThread(world, x, y, z, prefabYaw, constructionId, plotId);
        }
        return CompletableFuture.supplyAsync(
                () -> {
                    ChunkSectionBlockUtil.breakBlock(world, x, y, z, PLACE_SETTINGS);
                    return placePlotSignOnWorldThread(world, x, y, z, prefabYaw, constructionId, plotId);
                },
                world
            )
            .join();
    }
}
