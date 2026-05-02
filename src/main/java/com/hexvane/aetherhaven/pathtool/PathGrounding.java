package com.hexvane.aetherhaven.pathtool;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Raycast downward in a block column to find a solid top surface to place path blocks on.
 */
public final class PathGrounding {
    private PathGrounding() {}

    /**
     * @param startY exclusive top of the ray; searches toward {@code y=1}.
     * @return the Y coordinate of a solid, replaceable “feet” block, or null if not found
     */
    @Nullable
    public static Integer findSupportY(
        @Nonnull World world,
        int blockX,
        int blockZ,
        int startY,
        int maxDown,
        int minY
    ) {
        int yTop = Math.min(319, startY);
        int yEnd = Math.max(1, minY);
        int steps = 0;
        for (int y = yTop; y >= yEnd && steps < maxDown; y--, steps++) {
            if (!isColumnLoaded(world, blockX, y, blockZ)) {
                return null;
            }
            BlockType here = getBlockType(world, blockX, y, blockZ);
            if (here == null || here == BlockType.EMPTY) {
                continue;
            }
            // Rubble sits on soil; path should target the ground beneath, not the rubble top.
            if (PathRubbleUtil.isRubble(here)) {
                continue;
            }
            if (here.getMaterial() != BlockMaterial.Solid) {
                continue;
            }
            BlockType head = getBlockType(world, blockX, y + 1, blockZ);
            if (head == null) {
                continue;
            }
            if (head == BlockType.EMPTY) {
                return y;
            }
            if (PathRubbleUtil.isRubble(head)) {
                return y;
            }
            if (head.getMaterial() != BlockMaterial.Solid) {
                return y;
            }
        }
        return null;
    }

    private static boolean isColumnLoaded(@Nonnull World world, int x, int y, int z) {
        return world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z)) != null;
    }

    @Nullable
    private static BlockType getBlockType(@Nonnull World world, int x, int y, int z) {
        if (y < 0 || y > 320) {
            return null;
        }
        WorldChunk c = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (c == null) {
            return null;
        }
        return BlockType.getAssetMap().getAsset(c.getBlock(x, y, z));
    }
}
