package com.hexvane.aetherhaven.autonomy;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.mountpoints.BlockMountPoint;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.DoorInteraction;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

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

    /** After block-mount release, snap feet to standable ground at the current X/Z (e.g. off a bed or chair). */
    public static void snapNpcToStandY(@Nonnull Ref<EntityStore> npcRef, @Nonnull Store<EntityStore> store) {
        snapNpcToStandY(npcRef, store, null);
    }

    public static void snapNpcToStandY(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nullable CommandBuffer<EntityStore> commandBuffer
    ) {
        TransformComponent tc =
            commandBuffer != null
                ? commandBuffer.getComponent(npcRef, TransformComponent.getComponentType())
                : store.getComponent(npcRef, TransformComponent.getComponentType());
        if (tc == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        Vector3d pos = tc.getPosition();
        int bx = (int) Math.floor(pos.x);
        int bz = (int) Math.floor(pos.z);
        int standY = findStandY(world, bx, bz, (int) Math.floor(pos.y) + 2);
        if (standY == Integer.MIN_VALUE) {
            return;
        }
        pos.y = standY + 0.02;
        if (commandBuffer != null) {
            commandBuffer.putComponent(npcRef, TransformComponent.getComponentType(), tc);
        } else {
            store.putComponent(npcRef, TransformComponent.getComponentType(), tc);
        }
    }

    /**
     * Walkable feet Y in {@code (bx, bz)} for POI travel: searches only near {@code poiBlockY} so a multi-height column
     * (e.g. house with a walkable roof) does not resolve to a roof/attic above the actual POI. Falls back to
     * {@link #findStandY} when the band has no stand (e.g. column unloaded).
     */
    public static int findStandYNearPoiBlockY(@Nonnull World world, int bx, int bz, int poiBlockY, int npcFeetY) {
        int start = Math.min(319, Math.min(poiBlockY + 3, Math.max(poiBlockY, npcFeetY) + 2));
        for (int y = start; y >= Math.max(1, poiBlockY - 14); y--) {
            if (walkableColumn(world, bx, y, bz)) {
                return y;
            }
        }
        return findStandY(world, bx, bz, (int) Math.min(319, Math.max(1, npcFeetY) + 3));
    }

    /**
     * Resolves stand Y for POI / commute: optional plot AABB from {@link AutonomyNavBounds} caps the column so roof
     * walkables are not chosen; otherwise same band as {@link #findStandYNearPoiBlockY}.
     */
    public static int findStandYForNav(
        @Nonnull World world,
        int bx,
        int bz,
        int poiBlockY,
        int npcFeetY,
        @Nullable AutonomyNavBounds.NavVerticalRange range
    ) {
        if (range != null && range.isUsable()) {
            int start =
                Math.min(
                    319,
                    Math.min(
                        range.maxFeetY(),
                        Math.min(poiBlockY + 3, Math.max(poiBlockY, npcFeetY) + 2)
                    )
                );
            for (int y = start; y >= Math.max(1, Math.max(poiBlockY - 14, range.minFeetY())); y--) {
                if (walkableColumn(world, bx, y, bz)) {
                    return y;
                }
            }
        }
        return findStandYNearPoiBlockY(world, bx, bz, poiBlockY, npcFeetY);
    }

    /**
     * For wander step scoring: the probed XZ may be far from the NPC; do not use the first walkable up a tall air column
     * (roof). Only search a band around the NPC’s current feet.
     */
    public static int findStandYNearNpcFeetInColumn(
        @Nonnull World world, int bx, int bz, int npcFeetY
    ) {
        int start = Math.min(319, Math.max(1, npcFeetY) + 2);
        for (int y = start; y >= Math.max(1, npcFeetY - 10); y--) {
            if (walkableColumn(world, bx, y, bz)) {
                return y;
            }
        }
        return findStandY(world, bx, bz, start);
    }

    /**
     * Reads an already-resident chunk without changing its ticking state. Unlike {@link World#getBlockType(int, int,
     * int)}, this is safe to call from an entity tick system because it never routes through {@code getChunk()}.
     */
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

    /** True when an NPC can stand at feet Y in this column (passable feet/head, solid ground below). */
    public static boolean isNpcStandColumn(@Nonnull World world, int bx, int feetY, int bz) {
        return walkableColumn(world, bx, feetY, bz);
    }

    /**
     * Snaps a spawn/travel target to a validated feet column when the requested position is floating or inside blocks.
     */
    @Nonnull
    public static Vector3d snapNpcFeetToStand(@Nonnull World world, @Nonnull Vector3d feet) {
        int bx = (int) Math.floor(feet.x);
        int bz = (int) Math.floor(feet.z);
        int probeFeetY = (int) Math.floor(feet.y);
        if (isNpcStandColumn(world, bx, probeFeetY, bz)) {
            return new Vector3d(bx + 0.5, probeFeetY, bz + 0.5);
        }
        int standY = findStandY(world, bx, bz, Math.min(319, probeFeetY + 4));
        if (standY != Integer.MIN_VALUE) {
            return new Vector3d(bx + 0.5, standY, bz + 0.5);
        }
        return feet;
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
            return DoorInteraction.getDoorAtPosition(world.getChunkStore(), x, y, z, rt.yaw()) != null;
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
    /**
     * Finds a chair/bed-style mount block near {@code npcFeet}, preferring the block under the spawn marker when the
     * marker was placed on a seat. Only blocks with seat/bed mount points are considered.
     */
    @Nullable
    public static Vector3i findMountBlockNear(
        @Nonnull World world,
        double npcX,
        double npcYFeet,
        double npcZ,
        @Nonnull Vector3d spawnMarkerPosition
    ) {
        int markerBx = (int) Math.floor(spawnMarkerPosition.x);
        int markerBy = (int) Math.floor(spawnMarkerPosition.y);
        int markerBz = (int) Math.floor(spawnMarkerPosition.z);
        Vector3i underMarker = tryMountBlockAt(world, npcX, npcYFeet, npcZ, markerBx, markerBy - 1, markerBz);
        if (underMarker != null) {
            return underMarker;
        }
        return tryMountBlockAt(world, npcX, npcYFeet, npcZ, markerBx, markerBy, markerBz);
    }

    /** Furniture mount shape for POI visuals: seats vs beds vs neither. */
    public enum FurnitureMountKind {
        NONE,
        SEAT,
        BED
    }

    /**
     * Prefers beds over seats if a block somehow defines both; otherwise seats, then none.
     */
    @Nonnull
    public static FurnitureMountKind furnitureMountKind(@Nonnull World world, int bx, int by, int bz) {
        if (by < 0 || by >= 320) {
            return FurnitureMountKind.NONE;
        }
        BlockType blockType = blockTypeNoLoad(world, bx, by, bz);
        if (blockType == null || blockType == BlockType.EMPTY) {
            return FurnitureMountKind.NONE;
        }
        if (blockType.getBeds() != null) {
            return FurnitureMountKind.BED;
        }
        if (blockType.getSeats() != null) {
            return FurnitureMountKind.SEAT;
        }
        return FurnitureMountKind.NONE;
    }

    public static boolean hasSeats(@Nonnull World world, int bx, int by, int bz) {
        return furnitureMountKind(world, bx, by, bz) == FurnitureMountKind.SEAT;
    }

    public static boolean hasBeds(@Nonnull World world, int bx, int by, int bz) {
        return furnitureMountKind(world, bx, by, bz) == FurnitureMountKind.BED;
    }

    /** True when the block at {@code (bx, by, bz)} has seat or bed mount points. */
    public static boolean isBlockMountSeat(@Nonnull World world, int bx, int by, int bz) {
        return furnitureMountKind(world, bx, by, bz) != FurnitureMountKind.NONE;
    }

    /** True when the chunk for an adventurer spawn marker column is loaded (seat lookup and mount need this). */
    public static boolean isGuildHallSpawnColumnLoaded(@Nonnull World world, @Nonnull Vector3d spawnAnchor) {
        int bx = (int) Math.floor(spawnAnchor.x);
        int bz = (int) Math.floor(spawnAnchor.z);
        return world.getChunkIfInMemory(com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(bx, bz)) != null;
    }

    /**
     * Lowest seat block with a resolvable mount point under a guild hall adventurer spawn anchor (marker is above the
     * chair). Scans down so stacked chair voxels pick the base block BlockMountAPI can use.
     */
    @Nullable
    public static Vector3i findGuildHallSeatBelowSpawn(@Nonnull World world, @Nonnull Vector3d spawnAnchor) {
        int bx = (int) Math.floor(spawnAnchor.x);
        int bz = (int) Math.floor(spawnAnchor.z);
        int anchorBlockY = (int) Math.floor(spawnAnchor.y);
        Vector3i lowestWithSeat = null;
        for (int dy = 1; dy <= 6; dy++) {
            int by = anchorBlockY - dy;
            if (by < 0) {
                break;
            }
            if (!isBlockMountSeat(world, bx, by, bz)) {
                continue;
            }
            Vector3i candidate = new Vector3i(bx, by, bz);
            if (seatWorldPosition(world, candidate) != null) {
                lowestWithSeat = candidate;
            }
        }
        if (lowestWithSeat != null) {
            return lowestWithSeat;
        }
        if (isBlockMountSeat(world, bx, anchorBlockY, bz)) {
            Vector3i sameCell = new Vector3i(bx, anchorBlockY, bz);
            if (seatWorldPosition(world, sameCell) != null) {
                return sameCell;
            }
        }
        return null;
    }

    /** World-space seat point for a chair/stool block, or null when the block has no seat mount points. */
    @Nullable
    public static Vector3d seatWorldPosition(@Nonnull World world, @Nonnull Vector3i mountBlock) {
        BlockType blockType = blockTypeNoLoad(world, mountBlock.x, mountBlock.y, mountBlock.z);
        if (blockType == null || blockType.getSeats() == null) {
            return null;
        }
        int rotationIndex = blockRotationIndexNoLoad(world, mountBlock.x, mountBlock.y, mountBlock.z);
        BlockMountPoint[] points = blockType.getSeats().getRotated(rotationIndex);
        if (points == null || points.length == 0) {
            return null;
        }
        return points[0].computeWorldSpacePosition(mountBlock);
    }

    /**
     * Feet position for a guild hall display adventurer: chair seat under the spawn marker when present, otherwise the
     * marker anchor (standing cell center).
     */
    @Nonnull
    public static Vector3d resolveGuildHallAdventurerFeetPosition(@Nonnull World world, @Nonnull Vector3d spawnMarker) {
        Vector3i seatBlock = findGuildHallSeatBelowSpawn(world, spawnMarker);
        if (seatBlock == null) {
            return new Vector3d(spawnMarker);
        }
        Vector3d seatPos = seatWorldPosition(world, seatBlock);
        return seatPos != null ? new Vector3d(seatPos) : new Vector3d(spawnMarker);
    }

    @Nullable
    private static Vector3i tryMountBlockAt(
        @Nonnull World world,
        double npcX,
        double npcYFeet,
        double npcZ,
        int bx,
        int by,
        int bz
    ) {
        if (by < 0 || by >= 320) {
            return null;
        }
        if (!isBlockMountSeat(world, bx, by, bz)) {
            return null;
        }
        if (!canNpcMountBlockPoi(world, npcX, npcYFeet, npcZ, bx, by, bz)) {
            return null;
        }
        return new Vector3i(bx, by, bz);
    }

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
