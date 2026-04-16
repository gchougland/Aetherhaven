package com.hexvane.aetherhaven.autonomy;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.DoorInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Ground sampling and block queries for POI visuals. Travel uses vanilla NPC Seek + leash.
 */
public final class VillagerBlockUtil {
    /** Max horizontal distance from POI block center (XZ) for bed/chair mount attempts. */
    public static final double MOUNT_POI_MAX_HORIZONTAL = 0.92;

    private VillagerBlockUtil() {}

    /**
     * Block rotation index; delegates to {@link WorldChunk#getRotationIndex(int, int, int)} (same path as
     * {@link com.hypixel.hytale.server.core.universe.world.accessor.IChunkAccessorSync}) until section access is
     * non-deprecated.
     */
    @SuppressWarnings({ "deprecation", "removal" })
    static int rotationIndexForLoadedChunk(@Nonnull WorldChunk chunk, int x, int y, int z) {
        if (y < 0 || y >= 320) {
            return 0;
        }
        return chunk.getRotationIndex(x, y, z);
    }

    public static int blockRotationIndexNoLoad(@Nonnull World world, int x, int y, int z) {
        if (y < 0 || y >= 320) {
            return 0;
        }
        WorldChunk wc = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        return wc != null ? rotationIndexForLoadedChunk(wc, x, y, z) : 0;
    }

    /** Feet Y at column or {@link Integer#MIN_VALUE} if unknown (chunk not loaded). */
    public static int findStandY(@Nonnull World world, int bx, int bz, int searchTopY) {
        int top = Math.min(319, searchTopY);
        for (int y = top; y >= Math.max(1, top - 10); y--) {
            if (walkableColumn(world, bx, y, bz)) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    @Nullable
    private static BlockType blockTypeNoLoad(@Nonnull World world, int x, int y, int z) {
        if (y < 0 || y >= 320) {
            return null;
        }
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            return null;
        }
        return BlockType.getAssetMap().getAsset(chunk.getBlock(x, y, z));
    }

    private static boolean walkableColumn(@Nonnull World world, int bx, int by, int bz) {
        BlockType feet = blockTypeNoLoad(world, bx, by, bz);
        BlockType head = blockTypeNoLoad(world, bx, by + 1, bz);
        BlockType below = blockTypeNoLoad(world, bx, by - 1, bz);
        if (feet == null || head == null || below == null) {
            return false;
        }
        return isPassable(world, bx, by, bz, feet) && isPassable(world, bx, by + 1, bz, head) && isGround(below);
    }

    private static boolean isPassable(@Nonnull World world, int x, int y, int z, @Nullable BlockType t) {
        if (t == null || t == BlockType.EMPTY) {
            return true;
        }
        if (t.getMaterial() == BlockMaterial.Empty) {
            return true;
        }
        WorldChunk wc = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (wc != null) {
            RotationTuple rt = RotationTuple.get(rotationIndexForLoadedChunk(wc, x, y, z));
            return DoorInteraction.getDoorAtPosition(world, x, y, z, rt.yaw()) != null;
        }
        return isDoorBlockTypeWhenChunkUnknown(t);
    }

    /** When the column is not resident, fall back to asset metadata (same as vanilla door checks before chunk load). */
    @SuppressWarnings("deprecation")
    private static boolean isDoorBlockTypeWhenChunkUnknown(@Nonnull BlockType t) {
        return t.isDoor();
    }

    private static boolean isGround(@Nullable BlockType t) {
        if (t == null || t == BlockType.EMPTY) {
            return false;
        }
        return t.getMaterial() == BlockMaterial.Solid;
    }

    /**
     * True if the NPC is close enough on XZ and the column samples along the segment from the NPC to the POI block
     * center are walk-passable (feet + head), excluding the POI column. Blocks mounts that snap through walls/windows
     * when {@link com.hypixel.hytale.builtin.mounts.BlockMountComponent#findAvailableSeat} picks a seat on the far
     * side of the block.
     */
    public static boolean canNpcMountBlockPoi(
        @Nonnull World world,
        double npcX,
        double npcYFeet,
        double npcZ,
        int bx,
        int by,
        int bz
    ) {
        double hdx = npcX - (bx + 0.5);
        double hdz = npcZ - (bz + 0.5);
        if (hdx * hdx + hdz * hdz > MOUNT_POI_MAX_HORIZONTAL * MOUNT_POI_MAX_HORIZONTAL) {
            return false;
        }
        if (npcYFeet < by - 1.25 || npcYFeet > by + 2.75) {
            return false;
        }
        return hasClearHorizontalApproachToBlockColumn(world, npcX, npcYFeet, npcZ, bx, bz);
    }

    /**
     * Ray through XZ from NPC feet toward block center; each stepped cell must have passable feet+head space, except
     * the POI column (bed/chair occupies that cell).
     */
    private static boolean hasClearHorizontalApproachToBlockColumn(
        @Nonnull World world,
        double npcX,
        double npcYFeet,
        double npcZ,
        int bx,
        int bz
    ) {
        double tx = bx + 0.5;
        double tz = bz + 0.5;
        int footY = (int) Math.floor(npcYFeet);
        for (int i = 1; i <= 20; i++) {
            double t = i / 21.0;
            double x = npcX + (tx - npcX) * t;
            double z = npcZ + (tz - npcZ) * t;
            int cx = (int) Math.floor(x);
            int cz = (int) Math.floor(z);
            if (cx == bx && cz == bz) {
                continue;
            }
            if (!columnPassableForNpcBody(world, cx, footY, cz)) {
                return false;
            }
        }
        return true;
    }

    private static boolean columnPassableForNpcBody(@Nonnull World world, int x, int y, int z) {
        return isPassable(world, x, y, z, blockTypeNoLoad(world, x, y, z))
            && isPassable(world, x, y + 1, z, blockTypeNoLoad(world, x, y + 1, z));
    }
}
