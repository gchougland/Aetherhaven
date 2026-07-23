package com.hexvane.aetherhaven.questboard;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

final class RaidSpawnGroundUtil {
    private static final int MIN_SPAWN_FEET_ABOVE_SURFACE = 2;
    private static final int SURFACE_FEET_ABOVE_GROUND = 8;
    private static final int OPEN_SKY_CLEARANCE_BLOCKS = 10;
    private static final int SOLID_GROUND_DEPTH = 2;

    private RaidSpawnGroundUtil() {}

    @Nullable
    static Vector3d findSpawnPosition(@Nonnull World world, int bx, int bz, int charterY) {
        WorldChunk chunk = loadChunkForSpawn(world, bx, bz);
        if (chunk == null) {
            return null;
        }
        int surfaceGroundY = chunk.getHeight(bx, bz);
        if (surfaceGroundY < 1) {
            return null;
        }
        int maxFeetY = Math.min(319, surfaceGroundY + SURFACE_FEET_ABOVE_GROUND);
        int minFeetY = Math.max(1, surfaceGroundY + MIN_SPAWN_FEET_ABOVE_SURFACE);
        for (int feetY = maxFeetY; feetY >= minFeetY; feetY--) {
            Vector3d pos = trySurfaceStand(world, chunk, bx, feetY, bz, surfaceGroundY);
            if (pos != null) {
                return pos;
            }
        }
        for (int feetY = surfaceGroundY + 1; feetY < minFeetY; feetY++) {
            Vector3d pos = trySurfaceStand(world, chunk, bx, feetY, bz, surfaceGroundY);
            if (pos != null) {
                return pos;
            }
        }
        for (int feetY = surfaceGroundY; feetY >= Math.max(1, surfaceGroundY - 2); feetY--) {
            Vector3d pos = trySurfaceStand(world, chunk, bx, feetY, bz, surfaceGroundY);
            if (pos != null) {
                return pos;
            }
        }
        return null;
    }

    @Nullable
    private static Vector3d trySurfaceStand(
        @Nonnull World world,
        @Nonnull WorldChunk chunk,
        int bx,
        int feetY,
        int bz,
        int surfaceGroundY
    ) {
        if (feetY < surfaceGroundY || feetY > surfaceGroundY + SURFACE_FEET_ABOVE_GROUND) {
            return null;
        }
        if (!isSafeRaidStand(world, chunk, bx, feetY, bz)) {
            return null;
        }
        if (!hasSolidGroundDepth(chunk, bx, feetY, bz, SOLID_GROUND_DEPTH)) {
            return null;
        }
        if (!hasOpenSky(chunk, bx, feetY, bz)) {
            return null;
        }
        return new Vector3d(bx + 0.5, feetY, bz + 0.5);
    }

    static boolean isSafeRaidStand(@Nonnull World world, int bx, int feetY, int bz) {
        WorldChunk chunk = loadChunkForSpawn(world, bx, bz);
        if (chunk == null) {
            return false;
        }
        return isSafeRaidStand(world, chunk, bx, feetY, bz);
    }

    private static boolean isSafeRaidStand(
        @Nonnull World world,
        @Nonnull WorldChunk chunk,
        int bx,
        int feetY,
        int bz
    ) {
        if (!isWalkableStand(chunk, bx, feetY, bz)) {
            return false;
        }
        if (hasFluid(world, chunk, bx, feetY, bz) || hasFluid(world, chunk, bx, feetY + 1, bz)) {
            return false;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                int nx = bx + dx;
                int nz = bz + dz;
                WorldChunk neighbor = chunk;
                if (ChunkUtil.indexChunkFromBlock(nx, nz) != chunk.getIndex()) {
                    neighbor = loadChunkForSpawn(world, nx, nz);
                    if (neighbor == null) {
                        return false;
                    }
                }
                if (hasFluid(world, neighbor, nx, feetY, nz) || hasFluid(world, neighbor, nx, feetY + 1, nz)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean hasSolidGroundDepth(
        @Nonnull WorldChunk chunk,
        int bx,
        int feetY,
        int bz,
        int depth
    ) {
        for (int d = 1; d <= depth; d++) {
            BlockType below = readBlock(chunk, bx, feetY - d, bz);
            if (below == null || !isGround(below)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasOpenSky(@Nonnull WorldChunk chunk, int bx, int feetY, int bz) {
        int top = Math.min(319, feetY + OPEN_SKY_CLEARANCE_BLOCKS);
        for (int y = feetY + 2; y <= top; y++) {
            BlockType block = readBlock(chunk, bx, y, bz);
            if (block == null || !isPassable(block)) {
                return false;
            }
        }
        return true;
    }

    /** Loads chunks if needed. Must not run from entity tick systems. */
    @Nullable
    private static WorldChunk loadChunkForSpawn(@Nonnull World world, int x, int z) {
        return world.getChunk(ChunkUtil.indexChunkFromBlock(x, z));
    }

    private static boolean isWalkableStand(@Nonnull WorldChunk chunk, int bx, int feetY, int bz) {
        BlockType feet = readBlock(chunk, bx, feetY, bz);
        BlockType head = readBlock(chunk, bx, feetY + 1, bz);
        BlockType below = readBlock(chunk, bx, feetY - 1, bz);
        if (feet == null || head == null || below == null) {
            return false;
        }
        return isPassable(feet) && isPassable(head) && isGround(below);
    }

    @Nullable
    private static BlockType readBlock(@Nonnull WorldChunk chunk, int x, int y, int z) {
        if (y < 0 || y >= 320) {
            return null;
        }
        return BlockType.getAssetMap().getAsset(chunk.getBlock(x, y, z));
    }

    private static boolean isPassable(@Nonnull BlockType type) {
        if (type == BlockType.EMPTY) {
            return true;
        }
        return type.getMaterial() == BlockMaterial.Empty;
    }

    private static boolean isGround(@Nonnull BlockType type) {
        if (type == BlockType.EMPTY) {
            return false;
        }
        return type.getMaterial() == BlockMaterial.Solid;
    }

    private static boolean hasFluid(
        @Nonnull World world,
        @Nonnull WorldChunk chunk,
        int x,
        int y,
        int z
    ) {
        if (y < 0 || y >= 320) {
            return false;
        }
        ChunkStore chunkStore = world.getChunkStore();
        if (chunkStore == null) {
            return false;
        }
        Ref<ChunkStore> sectionRef = chunkStore.getChunkSectionReferenceAtBlock(x, y, z);
        if (sectionRef == null || !sectionRef.isValid()) {
            return false;
        }
        FluidSection fluidSection = chunkStore.getStore().getComponent(sectionRef, FluidSection.getComponentType());
        return fluidSection != null && fluidSection.getFluidId(x, y, z) != 0;
    }
}
