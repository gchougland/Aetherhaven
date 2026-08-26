package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.builtin.mounts.BlockMountComponent;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.mountpoints.BlockMountPoint;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.mountpoints.RotatedMountPointsArray;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.DoorInteraction;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
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

    /** Max blocks to raycast downward when resolving stand placement from air clicks. */
    public static final double DOWNCAST_MAX_DISTANCE = 32.0;

    private static final double THIN_SURFACE_TOP_Y = 0.999;

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
        WorldChunk wc = ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(x, z));
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

    /**
     * Walkable feet Y for a POI stand/leash hint. Never returns a Y where the villager would occupy a solid block:
     * if {@code hintY} is inside furniture/ground, climbs until feet+head are clear with ground underfoot.
     */
    public static int resolveClearStandFeetY(@Nonnull World world, int bx, int hintY, int bz) {
        int hint = Math.max(0, Math.min(319, hintY));
        BlockType atHint = blockTypeNoLoad(world, bx, hint, bz);
        int start = hint;
        if (atHint != null && !isPassable(world, bx, hint, bz, atHint)) {
            // Hint cell is solid (e.g. floor or bookcase) — stand on top of it, then climb further if needed.
            start = Math.min(319, hint + 1);
        }
        for (int y = start; y <= Math.min(319, start + 10); y++) {
            if (walkableColumn(world, bx, y, bz)) {
                return y;
            }
        }
        int near = findStandYNearPoiBlockY(world, bx, bz, hint, hint);
        if (near != Integer.MIN_VALUE && walkableColumn(world, bx, near, bz)) {
            return near;
        }
        return findStandY(world, bx, bz, Math.min(319, Math.max(start, hint) + 4));
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
        pos.y = resolveFeetYForStandCell(world, bx, standY, bz);
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
        WorldChunk chunk = ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(x, z));
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
     * Feet Y uses the ground block hitbox top so thin flats (carpet, cloth roof) are not floating a block above.
     */
    @Nonnull
    public static Vector3d snapNpcFeetToStand(@Nonnull World world, @Nonnull Vector3d feet) {
        int bx = (int) Math.floor(feet.x);
        int bz = (int) Math.floor(feet.z);
        int probeFeetY = (int) Math.floor(feet.y);
        int standCellY = probeFeetY;
        if (!isNpcStandColumn(world, bx, probeFeetY, bz)) {
            int standY = findStandY(world, bx, bz, Math.min(319, probeFeetY + 4));
            if (standY != Integer.MIN_VALUE) {
                standCellY = standY;
            }
        }
        double feetY = resolveFeetYForStandCell(world, bx, standCellY, bz);
        return new Vector3d(bx + 0.5, feetY, bz + 0.5);
    }

    /** World Y for NPC feet when standing in {@code standCellY} on the ground block below. */
    public static double resolveFeetYForStandCell(@Nonnull World world, int bx, int standCellY, int bz) {
        int groundY = findGroundBlockYBelowStandCell(world, bx, standCellY, bz);
        if (groundY == Integer.MIN_VALUE) {
            return standCellY + 0.02;
        }
        return blockSurfaceTopY(world, bx, groundY, bz) + 0.02;
    }

    /** Top Y of a block's collision hitbox in world space. */
    public static double blockSurfaceTopY(@Nonnull World world, int bx, int by, int bz) {
        BlockType blockType = blockTypeNoLoad(world, bx, by, bz);
        if (blockType == null || blockType == BlockType.EMPTY) {
            return by;
        }
        BlockBoundingBoxes hitboxAsset = BlockBoundingBoxes.getAssetMap().getAsset(blockType.getHitboxTypeIndex());
        if (hitboxAsset == null) {
            return by + 1.0;
        }
        int rotationIndex = blockRotationIndexNoLoad(world, bx, by, bz);
        return by + hitboxAsset.get(rotationIndex).getBoundingBox().max.y;
    }

    private static int findGroundBlockYBelowStandCell(@Nonnull World world, int bx, int standCellY, int bz) {
        for (int y = standCellY - 1; y >= Math.max(0, standCellY - 8); y--) {
            if (isGroundBlock(world, bx, y, bz)) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    private static boolean isGroundBlock(@Nonnull World world, int x, int y, int z) {
        BlockType blockType = blockTypeNoLoad(world, x, y, z);
        if (blockType == null || blockType == BlockType.EMPTY) {
            return false;
        }
        if (blockType.getMaterial() == BlockMaterial.Solid) {
            return true;
        }
        return isThinSurfaceBlock(world, x, y, z);
    }

    /** True when the block hitbox is a full 1×1×1 unit cube. */
    public static boolean isFullUnitBlock(@Nonnull World world, int x, int y, int z) {
        BlockType blockType = blockTypeNoLoad(world, x, y, z);
        if (blockType == null || blockType == BlockType.EMPTY) {
            return false;
        }
        if (BlockBoundingBoxes.DEFAULT.equals(blockType.getHitboxType())) {
            return true;
        }
        BlockBoundingBoxes hitboxAsset = BlockBoundingBoxes.getAssetMap().getAsset(blockType.getHitboxTypeIndex());
        if (hitboxAsset == null) {
            return false;
        }
        int rotationIndex = blockRotationIndexNoLoad(world, x, y, z);
        return hitboxAsset.get(rotationIndex).getBoundingBox().isUnitBox();
    }

    /**
     * Half slabs, carpets, and other non-unit surfaces whose top is below a full block height.
     */
    public static boolean isThinSurfaceBlock(@Nonnull World world, int x, int y, int z) {
        if (isFullUnitBlock(world, x, y, z)) {
            return false;
        }
        if (isBlockMountSeat(world, x, y, z)) {
            return true;
        }
        BlockType blockType = blockTypeNoLoad(world, x, y, z);
        if (blockType == null || blockType == BlockType.EMPTY) {
            return false;
        }
        BlockBoundingBoxes hitboxAsset = BlockBoundingBoxes.getAssetMap().getAsset(blockType.getHitboxTypeIndex());
        if (hitboxAsset == null) {
            return false;
        }
        int rotationIndex = blockRotationIndexNoLoad(world, x, y, z);
        return hitboxAsset.get(rotationIndex).getBoundingBox().max.y < THIN_SURFACE_TOP_Y;
    }

    /**
     * Resolves the supporting block for a plot-creator click. Air clicks raycast down; solid, thin, and mount blocks
     * use the clicked cell (filler voxels resolve to the furniture origin).
     */
    @Nonnull
    public static Vector3i resolveSupportBlockFromClick(@Nonnull World world, @Nonnull Vector3i clicked) {
        if (isSupportBlockAtClick(world, clicked)) {
            return resolveMountBaseBlock(world, clicked.x, clicked.y, clicked.z);
        }
        Vector3i hit =
            TargetUtil.getTargetBlock(
                world,
                (blockId, fluidId) -> blockId == 0,
                clicked.x + 0.5,
                clicked.y + 0.5,
                clicked.z + 0.5,
                0.0,
                -1.0,
                0.0,
                DOWNCAST_MAX_DISTANCE
            );
        if (hit != null) {
            return resolveMountBaseBlock(world, hit.x, hit.y, hit.z);
        }
        return new Vector3i(clicked);
    }

    /**
     * Block cell where NPC feet stand, given a supporting surface block (full cube, slab, carpet, chair, etc.).
     */
    @Nonnull
    public static Vector3i resolveStandBlockFromSupport(@Nonnull World world, @Nonnull Vector3i support) {
        int sx = support.x;
        int sy = support.y;
        int sz = support.z;
        if (isFullUnitBlock(world, sx, sy, sz)) {
            return new Vector3i(sx, sy + 1, sz);
        }
        for (int feetY = sy + 1; feetY >= sy; feetY--) {
            if (walkableColumn(world, sx, feetY, sz)) {
                return new Vector3i(sx, feetY, sz);
            }
        }
        int standY = findStandY(world, sx, sz, Math.min(319, sy + 4));
        if (standY != Integer.MIN_VALUE) {
            return new Vector3i(sx, standY, sz);
        }
        return new Vector3i(sx, sy + 1, sz);
    }

    /** Resolves support from a click then the stand cell above/on that surface. */
    @Nonnull
    public static Vector3i resolveStandBlockFromClick(@Nonnull World world, @Nonnull Vector3i clicked) {
        Vector3i support = resolveSupportBlockFromClick(world, clicked);
        return resolveStandBlockFromSupport(world, support);
    }

    /**
     * Body yaw (radians) for sitting forward on a chair, bench, or bed mount block.
     * Matches {@link BlockMountPoint#computeRotationEuler} / {@link com.hypixel.hytale.builtin.mounts.BlockMountAPI}.
     */
    @Nullable
    public static Float seatForwardYawRadians(@Nonnull World world, @Nonnull Vector3i mountBlock) {
        Vector3i base = resolveMountBaseBlock(world, mountBlock.x, mountBlock.y, mountBlock.z);
        BlockMountPoint point = primaryMountPoint(world, base);
        if (point == null) {
            return null;
        }
        int rotationIndex = blockRotationIndexNoLoad(world, base.x, base.y, base.z);
        return normalizeYawRadians(point.computeRotationEuler(rotationIndex).yaw());
    }

    private static float normalizeYawRadians(float yaw) {
        float y = yaw;
        while (y > Math.PI) {
            y -= (float) (2.0 * Math.PI);
        }
        while (y <= -Math.PI) {
            y += (float) (2.0 * Math.PI);
        }
        return y;
    }

    /** Prefers seat forward yaw for a mount block; otherwise returns {@code fallbackYawRadians}. */
    public static float resolveMountBodyYawRadians(
        @Nonnull World world,
        @Nonnull Vector3i mountBlock,
        float fallbackYawRadians
    ) {
        Float seatYaw = seatForwardYawRadians(world, mountBlock);
        return seatYaw != null ? seatYaw : fallbackYawRadians;
    }

    /** Prefers seat forward yaw under a guild hall spawn marker; otherwise returns {@code fallbackYawRadians}. */
    public static float resolveSeatedDisplayYawRadians(
        @Nonnull World world,
        @Nonnull Vector3d spawnMarker,
        float fallbackYawRadians
    ) {
        Vector3i seatBlock = findGuildHallSeatBelowSpawn(world, spawnMarker);
        if (seatBlock == null) {
            return fallbackYawRadians;
        }
        return resolveMountBodyYawRadians(world, seatBlock, fallbackYawRadians);
    }

    @Nullable
    private static BlockMountPoint primaryMountPoint(@Nonnull World world, @Nonnull Vector3i base) {
        BlockType blockType = blockTypeNoLoad(world, base.x, base.y, base.z);
        if (blockType == null) {
            return null;
        }
        int rotationIndex = blockRotationIndexNoLoad(world, base.x, base.y, base.z);
        RotatedMountPointsArray seats = blockType.getSeats();
        if (seats != null) {
            BlockMountPoint[] points = seats.getRotated(rotationIndex);
            if (points != null && points.length > 0) {
                return points[0];
            }
        }
        RotatedMountPointsArray beds = blockType.getBeds();
        if (beds != null) {
            BlockMountPoint[] points = beds.getRotated(rotationIndex);
            if (points != null && points.length > 0) {
                return points[0];
            }
        }
        return null;
    }

    private static boolean isSupportBlockAtClick(@Nonnull World world, @Nonnull Vector3i clicked) {
        BlockType blockType = blockTypeNoLoad(world, clicked.x, clicked.y, clicked.z);
        if (blockType == null || blockType == BlockType.EMPTY) {
            return false;
        }
        if (isBlockMountSeat(world, clicked.x, clicked.y, clicked.z)) {
            return true;
        }
        if (isThinSurfaceBlock(world, clicked.x, clicked.y, clicked.z)) {
            return true;
        }
        return blockType.getMaterial() == BlockMaterial.Solid;
    }

    private static boolean walkableColumn(@Nonnull World world, int bx, int by, int bz) {
        BlockType feet = blockTypeNoLoad(world, bx, by, bz);
        BlockType head = blockTypeNoLoad(world, bx, by + 1, bz);
        if (feet == null || head == null) {
            return false;
        }
        return isPassable(world, bx, by, bz, feet) && isPassable(world, bx, by + 1, bz, head) && isGroundBlock(world, bx, by - 1, bz);
    }

    private static boolean isPassable(@Nonnull World world, int x, int y, int z, @Nullable BlockType t) {
        if (t == null || t == BlockType.EMPTY) {
            return true;
        }
        if (t.getMaterial() == BlockMaterial.Empty) {
            return true;
        }
        WorldChunk wc = ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(x, z));
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
        return ChunkSectionBlockUtil.worldChunkIfInMemory(world, com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(bx, bz)) != null;
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

    /**
     * Multi-block furniture (village benches, etc.) stores secondary voxels with non-zero filler pointing at the
     * origin cell. Seats/beds are defined relative to that origin — mounting a filler cell shifts the side seat into
     * empty air for some rotations.
     */
    @Nonnull
    @SuppressWarnings({ "deprecation", "removal" })
    public static Vector3i resolveMountBaseBlock(@Nonnull World world, int x, int y, int z) {
        WorldChunk chunk = ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            return new Vector3i(x, y, z);
        }
        int filler = chunk.getFiller(x, y, z);
        if (filler == FillerBlockUtil.NO_FILLER) {
            return new Vector3i(x, y, z);
        }
        return new Vector3i(
            x - FillerBlockUtil.unpackX(filler),
            y - FillerBlockUtil.unpackY(filler),
            z - FillerBlockUtil.unpackZ(filler)
        );
    }

    /** World-space seat point for a chair/stool block, or null when the block has no free seat mount points. */
    @Nullable
    public static Vector3d seatWorldPosition(@Nonnull World world, @Nonnull Vector3i mountBlock) {
        return preferredAvailableSeatWorldPosition(world, mountBlock);
    }

    /** True when the furniture block still has an open seat mount point (players/tourists/villagers count). */
    public static boolean hasAvailableSeat(@Nonnull World world, int bx, int by, int bz) {
        Vector3i base = resolveMountBaseBlock(world, bx, by, bz);
        return preferredAvailableSeatWorldPosition(world, base) != null;
    }

    /**
     * Picks an empty seat for multi-seat furniture (e.g. village benches). Uses vanilla
     * {@link BlockMountComponent#findAvailableSeat} so occupancy matches what {@code mountOnBlock} checks (identity of
     * rotated mount points), not entity feet — feet positions lag command-buffer mounts and blocked the second seat.
     * Always resolves filler voxels to the furniture origin first.
     */
    @Nullable
    public static Vector3d preferredAvailableSeatWorldPosition(@Nonnull World world, @Nonnull Vector3i mountBlock) {
        Vector3i base = resolveMountBaseBlock(world, mountBlock.x, mountBlock.y, mountBlock.z);
        BlockType blockType = blockTypeNoLoad(world, base.x, base.y, base.z);
        if (blockType == null || blockType.getSeats() == null) {
            return null;
        }
        int rotationIndex = blockRotationIndexNoLoad(world, base.x, base.y, base.z);
        BlockMountPoint[] points = blockType.getSeats().getRotated(rotationIndex);
        if (points == null || points.length == 0) {
            return null;
        }
        Vector3d blockCenter = new Vector3d(base.x + 0.5, base.y + 0.5, base.z + 0.5);
        BlockMountComponent seatComp = blockMountComponentNoLoad(world, base);
        if (seatComp != null) {
            BlockMountPoint picked = seatComp.findAvailableSeat(base, points, blockCenter);
            return picked != null ? picked.computeWorldSpacePosition(base) : null;
        }
        BlockMountPoint best = null;
        double bestDistSq = Double.POSITIVE_INFINITY;
        for (BlockMountPoint point : points) {
            Vector3d seatPos = point.computeWorldSpacePosition(base);
            double distSq = seatPos.distanceSquared(blockCenter);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = point;
            }
        }
        return best != null ? best.computeWorldSpacePosition(base) : null;
    }

    @Nullable
    private static BlockMountComponent blockMountComponentNoLoad(@Nonnull World world, @Nonnull Vector3i mountBlock) {
        try {
            Ref<ChunkStore> blockRef = ChunkSectionBlockUtil.blockEntityRefAt(
                world,
                mountBlock.x,
                mountBlock.y,
                mountBlock.z
            );
            if (blockRef == null || !blockRef.isValid()) {
                return null;
            }
            return world.getChunkStore().getStore().getComponent(blockRef, BlockMountComponent.getComponentType());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * Feet position for a guild hall display adventurer: chair seat under the spawn marker when present, otherwise the
     * marker anchor (standing cell center).
     */
    @Nonnull
    public static Vector3d resolveGuildHallAdventurerFeetPosition(@Nonnull World world, @Nonnull Vector3d spawnMarker) {
        Vector3i seatBlock = findGuildHallSeatBelowSpawn(world, spawnMarker);
        if (seatBlock != null) {
            Vector3d seatPos = seatWorldPosition(world, seatBlock);
            if (seatPos != null) {
                return new Vector3d(seatPos);
            }
        }
        return snapNpcFeetToStand(world, spawnMarker);
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
