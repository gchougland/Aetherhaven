package com.hexvane.aetherhaven.entity;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import javax.annotation.Nonnull;
import org.joml.Vector3dc;

/** Chunk load checks for entity spawn and motion (see vanilla {@code UpdateLocationSystems}). */
public final class EntityChunkUtil {
    private EntityChunkUtil() {}

    public static boolean isBlockChunkInMemory(@Nonnull World world, int blockX, int blockZ) {
        return ChunkSectionBlockUtil.isChunkInMemory(world, blockX, blockZ);
    }

    public static boolean isPositionChunkInMemory(@Nonnull World world, @Nonnull Vector3dc position) {
        return isBlockChunkInMemory(world, (int) Math.floor(position.x()), (int) Math.floor(position.z()));
    }
}
