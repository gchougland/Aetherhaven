package com.hexvane.aetherhaven.placement;

import com.hexvane.aetherhaven.plot.ManagementBlock;
import com.hexvane.aetherhaven.plot.PlotSignBlock;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Resolves block entities for plot sign / management UI. Uses the ray-hit cell and {@link World#getBaseBlock}
 * with a tall Y sweep so {@code Block_Vertical_Half} and multi-voxel props still find the chunk component holder.
 */
public final class PlotConstructionBlockResolver {
    private static final int Y_RADIUS = 10;

    public record PlotConstructionTarget(@Nonnull Ref<ChunkStore> blockRef, @Nonnull Vector3i blockWorldPos) {}

    private PlotConstructionBlockResolver() {}

    @Nullable
    public static PlotConstructionTarget resolveForPlotUi(@Nonnull World world, @Nonnull BlockPosition targetBlock) {
        BlockPosition base = world.getBaseBlock(targetBlock);
        int hitX = targetBlock.x;
        int hitY = targetBlock.y;
        int hitZ = targetBlock.z;
        int baseX = base.x;
        int baseY = base.y;
        int baseZ = base.z;

        PlotConstructionTarget hitCol = probeColumns(world, hitX, hitZ, hitY, baseY);
        if (hitCol != null) {
            return hitCol;
        }
        if (baseX != hitX || baseZ != hitZ) {
            return probeColumns(world, baseX, baseZ, hitY, baseY);
        }
        return null;
    }

    @Nullable
    private static PlotConstructionTarget probeColumns(
        @Nonnull World world,
        int blockX,
        int blockZ,
        int hitY,
        int baseY
    ) {
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(blockX, blockZ));
        if (chunk == null) {
            return null;
        }
        int yMin = Math.min(hitY, baseY) - Y_RADIUS;
        int yMax = Math.max(hitY, baseY) + Y_RADIUS;
        yMin = Math.max(yMin, 0);
        yMax = Math.min(yMax, 319);
        for (int y = yMin; y <= yMax; y++) {
            Ref<ChunkStore> blockRef = chunk.getBlockComponentEntity(blockX, y, blockZ);
            if (blockRef == null) {
                continue;
            }
            Store<ChunkStore> cs = blockRef.getStore();
            if (cs.getComponent(blockRef, ManagementBlock.getComponentType()) != null
                || cs.getComponent(blockRef, PlotSignBlock.getComponentType()) != null) {
                return new PlotConstructionTarget(blockRef, new Vector3i(blockX, y, blockZ));
            }
        }
        return null;
    }
}
