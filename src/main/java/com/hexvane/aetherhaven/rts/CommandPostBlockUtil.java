package com.hexvane.aetherhaven.rts;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

public final class CommandPostBlockUtil {
    private CommandPostBlockUtil() {}

    public static boolean isCommandPostBlock(@Nonnull World world, @Nonnull Vector3i pos) {
        WorldChunk chunk = ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        if (chunk == null) {
            return false;
        }
        BlockType type = chunk.getBlockType(pos.x, pos.y, pos.z);
        return type != null && AetherhavenConstants.COMMAND_POST_BLOCK_TYPE_ID.equals(type.getId());
    }

    @Nullable
    public static CommandPostBlock getBlockComponent(@Nonnull World world, @Nonnull Vector3i pos) {
        WorldChunk chunk = ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        if (chunk == null) {
            return null;
        }
        Ref<ChunkStore> blockRef = chunk.getBlockComponentEntity(pos.x, pos.y, pos.z);
        if (blockRef == null) {
            return null;
        }
        return blockRef.getStore().getComponent(blockRef, CommandPostBlock.getComponentType());
    }

    public static boolean writeTownId(@Nonnull World world, @Nonnull Vector3i pos, @Nonnull String townId) {
        WorldChunk chunk = ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        if (chunk == null) {
            return false;
        }
        Ref<ChunkStore> blockRef = chunk.getBlockComponentEntity(pos.x, pos.y, pos.z);
        if (blockRef == null) {
            return false;
        }
        Store<ChunkStore> cs = blockRef.getStore();
        cs.putComponent(blockRef, CommandPostBlock.getComponentType(), new CommandPostBlock(townId));
        return true;
    }
}
