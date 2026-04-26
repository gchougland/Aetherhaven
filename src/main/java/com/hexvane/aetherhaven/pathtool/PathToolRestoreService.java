package com.hexvane.aetherhaven.pathtool;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Restores a {@link PathCommitRecord} by re-applying previous block types (best-effort if chunks unload).
 */
public final class PathToolRestoreService {
    private static final int SET = 2;

    private PathToolRestoreService() {}

    public static int restoreAndRemove(@Nonnull World world, @Nonnull PathCommitRecord rec) {
        int ok = 0;
        for (PathToolUndoCell c : rec.undo) {
            WorldChunk ch = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(c.x, c.z));
            if (ch == null) {
                continue;
            }
            @Nullable
            BlockType t = BlockType.getAssetMap().getAsset(c.blockId);
            if (t == null) {
                t = BlockType.EMPTY;
            }
            int index = t == BlockType.EMPTY ? BlockType.EMPTY_ID : BlockType.getAssetMap().getIndex(c.blockId);
            if (t != BlockType.EMPTY) {
                ch.setBlock(c.x, c.y, c.z, index, t, c.rotationIndex, 0, SET);
            } else {
                ch.setBlock(c.x, c.y, c.z, BlockType.EMPTY_ID, BlockType.EMPTY, 0, 0, 10);
            }
            ok++;
        }
        return ok;
    }
}
