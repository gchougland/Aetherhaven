package com.hexvane.aetherhaven.tourist;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;

import com.hexvane.aetherhaven.autonomy.VillagerBlockUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

public final class TouristPortalBlockUtil {
    private static final int[][] PLATFORM_OFFSETS_X_WIDE = {{0, 0}, {1, 0}};
    private static final int[][] PLATFORM_OFFSETS_Z_WIDE = {{0, 0}, {0, 1}};

    private enum PortalAxis {
        X,
        Z
    }

    private TouristPortalBlockUtil() {}

    public static boolean isTouristPortalBlock(@Nullable BlockType type) {
        return type != null && TownPortalTravelColor.isTouristPortalBlockTypeId(type.getId());
    }

    /** Stand radius for player portal UI detection (horizontal, from base block center). */
    private static final double PLAYER_STAND_RADIUS_SQ = 4.0;

    /**
     * True if any in-memory block at {@code (fx, fy + dy, fz)} for {@code dy} in {@code [-2, 1]} is a tourist portal
     * type. O(4) chunk reads — no registry or plot scan.
     */
    public static boolean hasPortalBlockNear(@Nonnull World world, int fx, int fy, int fz) {
        for (int dy = -2; dy <= 1; dy++) {
            if (isTouristPortalBlock(blockTypeIfLoaded(world, fx, fy + dy, fz))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Lightweight stand check for player portal detection. Uses horizontal distance to the base block center only
     * (no layout / stand-column search). NPC despawn continues to use {@link #isNearPortalDespawn}.
     */
    public static boolean isNearPortalStand(@Nonnull Vector3i blockPos, @Nonnull Vector3d feet) {
        double cx = blockPos.x + 0.5;
        double cz = blockPos.z + 0.5;
        double dx = feet.x - cx;
        double dz = feet.z - cz;
        return dx * dx + dz * dz <= PLAYER_STAND_RADIUS_SQ;
    }

    /** @visibleForTesting */
    static double playerStandRadiusSqForTesting() {
        return PLAYER_STAND_RADIUS_SQ;
    }

    /**
     * True for the non-filler base cell of a multi-block portal. Filler voxels share the block type id but must not
     * create their own {@link TouristPortalRecord}.
     */
    public static boolean isPortalBaseBlock(@Nonnull World world, int x, int y, int z) {
        if (ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(x, z)) == null) {
            return false;
        }
        if (!isTouristPortalBlock(ChunkSectionBlockUtil.blockType(world, x, y, z))) {
            return false;
        }
        return ChunkSectionBlockUtil.filler(world, x, y, z) == 0;
    }

    /**
     * Resolves any clicked voxel of the multi-block portal to the base voxel that owns its block component.
     */
    @Nonnull
    public static Vector3i resolvePortalBaseBlock(@Nonnull World world, @Nonnull Vector3i pos) {
        if (ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(pos.x, pos.z)) == null) {
            return new Vector3i(pos);
        }
        int filler = ChunkSectionBlockUtil.filler(world, pos.x, pos.y, pos.z);
        if (filler == FillerBlockUtil.NO_FILLER) {
            return new Vector3i(pos);
        }
        return new Vector3i(
            pos.x - FillerBlockUtil.unpackX(filler),
            pos.y - FillerBlockUtil.unpackY(filler),
            pos.z - FillerBlockUtil.unpackZ(filler)
        );
    }

    @Nullable
    public static TouristPortalBlock getBlockComponent(@Nonnull World world, @Nonnull Vector3i pos) {
        if (ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(pos.x, pos.z)) == null) {
            return null;
        }
        Ref<ChunkStore> blockRef = ChunkSectionBlockUtil.blockEntityRefAt(world, pos.x, pos.y, pos.z);
        if (blockRef == null) {
            return null;
        }
        return blockRef.getStore().getComponent(blockRef, TouristPortalBlock.getComponentType());
    }

    public static boolean writeBlockComponent(@Nonnull World world, @Nonnull Vector3i pos, @Nonnull TouristPortalBlock block) {
        if (ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(pos.x, pos.z)) == null) {
            return false;
        }
        Ref<ChunkStore> blockRef = ChunkSectionBlockUtil.blockEntityRefAt(world, pos.x, pos.y, pos.z);
        if (blockRef == null) {
            return false;
        }
        Store<ChunkStore> cs = blockRef.getStore();
        cs.putComponent(blockRef, TouristPortalBlock.getComponentType(), copyBlock(block));
        return true;
    }

    @Nonnull
    private static TouristPortalBlock copyBlock(@Nonnull TouristPortalBlock block) {
        return new TouristPortalBlock(block.getPortalId(), block.getTownId(), block.getPlotId(), block.isConfigured());
    }

    public static void syncConfigToBlock(@Nonnull World world, @Nonnull Vector3i pos, @Nonnull TouristPortalRecord record) {
        TouristPortalBlock existing = getBlockComponent(world, pos);
        TouristPortalBlock block =
            existing != null
                ? copyBlock(existing)
                : new TouristPortalBlock(
                    record.getPortalId().toString(),
                    record.getTownId().toString(),
                    record.getPlotId().toString(),
                    false
                );
        block.applyRecord(record);
        if (!writeBlockComponent(world, pos, block)) {
            world.execute(() -> writeBlockComponent(world, pos, block));
        }
    }

    /** Center of the 2×1 portal platform for particles and burst VFX. */
    @Nonnull
    public static Vector3d portalEffectCenter(@Nonnull World world, @Nonnull Vector3i blockPos) {
        PortalLayout layout = resolvePortalLayout(world, blockPos);
        return new Vector3d(layout.centerX(), blockPos.y + 2.5, layout.centerZ());
    }

    /** Invokes {@code consumer} for each voxel of the 2×1 portal platform (base + filler cells). */
    public static void forEachPlatformCell(
        @Nonnull World world,
        @Nonnull Vector3i basePos,
        @Nonnull Consumer<Vector3i> consumer
    ) {
        Vector3i base = resolvePortalBaseBlock(world, basePos);
        PortalLayout layout = resolvePortalLayout(world, base);
        int y = base.y;
        for (int[] off : layout.platformOffsets()) {
            consumer.accept(new Vector3i(layout.minX() + off[0], y, layout.minZ() + off[1]));
        }
    }

    /** Feet position at the center of the portal platform (spawn). */
    @Nonnull
    public static Vector3d spawnFeetPosition(@Nonnull World world, @Nonnull Vector3i blockPos) {
        return spawnFeetPosition(world, blockPos, 0L);
    }

    /** Feet position at the center of the portal platform; {@code salt} is ignored (kept for call-site compatibility). */
    @Nonnull
    public static Vector3d spawnFeetPosition(@Nonnull World world, @Nonnull Vector3i blockPos, long salt) {
        return portalPlatformCenterFeet(world, blockPos);
    }

    /** Stand position at the center of the portal platform (return / despawn approach). */
    @Nonnull
    public static Vector3d returnStandPosition(@Nonnull World world, @Nonnull Vector3i blockPos) {
        return portalPlatformCenterFeet(world, blockPos);
    }

    /** True when the NPC is close enough to the portal platform to despawn. */
    public static boolean isNearPortalDespawn(@Nonnull World world, @Nonnull Vector3i blockPos, @Nonnull Vector3d feet) {
        Vector3d stand = returnStandPosition(world, blockPos);
        double dx = feet.x - stand.x;
        double dz = feet.z - stand.z;
        if (dx * dx + dz * dz <= 2.25) {
            return true;
        }
        Vector3d center = portalEffectCenter(world, blockPos);
        dx = feet.x - center.x;
        dz = feet.z - center.z;
        return dx * dx + dz * dz <= 9.0;
    }

    @Nonnull
    private static Vector3d portalPlatformCenterFeet(@Nonnull World world, @Nonnull Vector3i blockPos) {
        PortalLayout layout = resolvePortalLayout(world, blockPos);
        for (int feetY : standSearchHeights(blockPos.y)) {
            if (tryStandAtCenter(world, layout, feetY) != null) {
                return new Vector3d(layout.centerX(), feetY, layout.centerZ());
            }
        }
        Vector3d fallback = new Vector3d(layout.centerX(), blockPos.y + 1.0, layout.centerZ());
        return VillagerBlockUtil.snapNpcFeetToStand(world, fallback);
    }

    @Nullable
    private static Vector3d tryStandAtCenter(@Nonnull World world, @Nonnull PortalLayout layout, int feetY) {
        if (feetY < 1 || feetY >= 320) {
            return null;
        }
        for (int[] off : layout.platformOffsets()) {
            int bx = layout.minX() + off[0];
            int bz = layout.minZ() + off[1];
            if (VillagerBlockUtil.isNpcStandColumn(world, bx, feetY, bz)) {
                return new Vector3d(layout.centerX(), feetY, layout.centerZ());
            }
        }
        return null;
    }

    @Nonnull
    private static PortalLayout resolvePortalLayout(@Nonnull World world, @Nonnull Vector3i blockPos) {
        Vector3i min = portalMinCorner(world, blockPos);
        PortalAxis axis = resolvePortalAxis(world, blockPos, min);
        double centerX = axis == PortalAxis.X ? min.x + 1.0 : min.x + 0.5;
        double centerZ = axis == PortalAxis.Z ? min.z + 1.0 : min.z + 0.5;
        int[][] offsets = axis == PortalAxis.X ? PLATFORM_OFFSETS_X_WIDE : PLATFORM_OFFSETS_Z_WIDE;
        return new PortalLayout(min.x, min.z, centerX, centerZ, offsets);
    }

    @Nonnull
    private static Vector3i portalMinCorner(@Nonnull World world, @Nonnull Vector3i blockPos) {
        int x = blockPos.x;
        int y = blockPos.y;
        int z = blockPos.z;
        if (isPortalBlockAt(world, x - 1, y, z)) {
            x--;
        }
        if (isPortalBlockAt(world, x, y, z - 1)) {
            z--;
        }
        return new Vector3i(x, y, z);
    }

    @Nonnull
    private static PortalAxis resolvePortalAxis(@Nonnull World world, @Nonnull Vector3i blockPos, @Nonnull Vector3i min) {
        if (isPortalBlockAt(world, min.x + 1, blockPos.y, min.z)
            || isPortalBlockAt(world, blockPos.x + 1, blockPos.y, blockPos.z)
            || isPortalBlockAt(world, blockPos.x - 1, blockPos.y, blockPos.z)) {
            return PortalAxis.X;
        }
        if (isPortalBlockAt(world, min.x, blockPos.y, min.z + 1)
            || isPortalBlockAt(world, blockPos.x, blockPos.y, blockPos.z + 1)
            || isPortalBlockAt(world, blockPos.x, blockPos.y, blockPos.z - 1)) {
            return PortalAxis.Z;
        }
        return PortalAxis.X;
    }

    private static boolean isPortalBlockAt(@Nonnull World world, int x, int y, int z) {
        BlockType type = blockTypeIfLoaded(world, x, y, z);
        return isTouristPortalBlock(type);
    }

    /** In-memory block read — safe from entity tick systems (no synchronous chunk load). */
    @Nullable
    private static BlockType blockTypeIfLoaded(@Nonnull World world, int x, int y, int z) {
        if (y < 0 || y >= 320) {
            return null;
        }
        if (ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(x, z)) == null) {
            return null;
        }
        return ChunkSectionBlockUtil.blockType(world, x, y, z);
    }

    @Nonnull
    private static int[] standSearchHeights(int portalY) {
        return new int[] { portalY + 1, portalY, portalY + 2, portalY - 1 };
    }

    @Nullable
    public static UUID portalIdAt(@Nonnull World world, @Nonnull Vector3i pos) {
        TouristPortalBlock block = getBlockComponent(world, pos);
        if (block == null || block.getPortalId().isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(block.getPortalId().trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private record PortalLayout(int minX, int minZ, double centerX, double centerZ, int[][] platformOffsets) {}
}
