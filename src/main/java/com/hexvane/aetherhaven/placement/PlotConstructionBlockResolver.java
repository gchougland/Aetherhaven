package com.hexvane.aetherhaven.placement;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Resolves block entities for plot sign / management UI. Uses the ray-hit cell and filler-unpacked base position
 * (same logic as former {@code IChunkAccessorSync#getBaseBlock}) with a tall Y sweep so {@code Block_Vertical_Half}
 * and multi-voxel props still find the chunk component holder.
 *
 * <p>Resolves by <em>required</em> component so a plot sign and management block in the same column (e.g. origin
 * stack) do not steal each other's interactions. The match closest to the hit Y wins.
 */
public final class PlotConstructionBlockResolver {
    private static final int Y_RADIUS = 10;

    public record PlotConstructionTarget(@Nonnull Ref<ChunkStore> blockRef, @Nonnull Vector3i blockWorldPos) {}

    private PlotConstructionBlockResolver() {}

    @Nonnull
    private static BlockPosition baseBlockPosition(@Nonnull World world, @Nonnull BlockPosition position) {
        if (position.y < 0 || position.y >= 320) {
            return position;
        }
        WorldChunk chunk = world.getNonTickingChunk(ChunkUtil.indexChunkFromBlock(position.x, position.z));
        if (chunk == null) {
            return position;
        }
        BlockChunk bc = chunk.getBlockChunk();
        if (bc == null) {
            return position;
        }
        int filler = bc.getSectionAtBlockY(position.y).getFiller(position.x, position.y, position.z);
        if (filler == 0) {
            return position;
        }
        return new BlockPosition(
            position.x - FillerBlockUtil.unpackX(filler),
            position.y - FillerBlockUtil.unpackY(filler),
            position.z - FillerBlockUtil.unpackZ(filler)
        );
    }

    @Nullable
    public static <C extends Component<ChunkStore>> PlotConstructionTarget resolveForPlotUi(
        @Nonnull World world,
        @Nonnull BlockPosition targetBlock,
        @Nonnull ComponentType<ChunkStore, C> requiredComponent
    ) {
        BlockPosition base = baseBlockPosition(world, targetBlock);
        int hitX = targetBlock.x;
        int hitY = targetBlock.y;
        int hitZ = targetBlock.z;
        int baseX = base.x;
        int baseY = base.y;
        int baseZ = base.z;

        PlotConstructionTarget hitCol = probeColumns(world, hitX, hitZ, hitY, baseY, requiredComponent);
        if (hitCol != null) {
            return hitCol;
        }
        if (baseX != hitX || baseZ != hitZ) {
            return probeColumns(world, baseX, baseZ, hitY, baseY, requiredComponent);
        }
        return null;
    }

    @Nullable
    private static <C extends Component<ChunkStore>> PlotConstructionTarget probeColumns(
        @Nonnull World world,
        int blockX,
        int blockZ,
        int hitY,
        int baseY,
        @Nonnull ComponentType<ChunkStore, C> requiredComponent
    ) {
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(blockX, blockZ));
        if (chunk == null) {
            return null;
        }
        int yMin = Math.min(hitY, baseY) - Y_RADIUS;
        int yMax = Math.max(hitY, baseY) + Y_RADIUS;
        yMin = Math.max(yMin, 0);
        yMax = Math.min(yMax, 319);
        Ref<ChunkStore> bestRef = null;
        int bestY = 0;
        int bestDist = Integer.MAX_VALUE;
        for (int y = yMin; y <= yMax; y++) {
            Ref<ChunkStore> blockRef = chunk.getBlockComponentEntity(blockX, y, blockZ);
            if (blockRef == null) {
                continue;
            }
            Store<ChunkStore> cs = blockRef.getStore();
            if (cs.getComponent(blockRef, requiredComponent) == null) {
                continue;
            }
            int dist = Math.abs(y - hitY);
            if (dist < bestDist) {
                bestDist = dist;
                bestRef = blockRef;
                bestY = y;
            }
        }
        return bestRef == null ? null : new PlotConstructionTarget(bestRef, new Vector3i(blockX, bestY, blockZ));
    }
}
