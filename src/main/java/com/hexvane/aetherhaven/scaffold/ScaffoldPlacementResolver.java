package com.hexvane.aetherhaven.scaffold;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.util.TrigMathUtil;
import org.joml.Vector3i;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.protocol.InteractionSyncData;
import com.hypixel.hytale.protocol.Position;
import org.joml.Vector3f;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.world.World;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Server-side scaffold placement: only adjusts {@link InteractionSyncData#blockPosition} when attach resolves to a wood
 * scaffold column and intent matches; otherwise mirrors vanilla (client cell unchanged).
 *
 * <p>Jump guard: upward velocity suppresses tower/horizontal snap so placement matches vanilla while airborne.
 */
public final class ScaffoldPlacementResolver {

    /** Above this server Y velocity, skip scaffold snapping (blocks jump-placing far above the column). */
    private static final double JUMP_VY_THRESHOLD = 0.08;

    private static final double TOP_FACE_CENTER_MAX = 0.16;

    /** Use (F): larger “stack up” zone on the top face; outside it, branch toward nearest edge (cardinal). */
    private static final double USE_TOP_FACE_CENTER_MAX = 0.52;

    /** Local Y on apex block (or upward hit normal) counts as aiming at the top for stack / top-face branch. */
    private static final double USE_TOP_FACE_MIN_LOCAL_Y = 0.50;

    private static final int MAX_HORIZONTAL_BRANCH_STEPS = 8;

    private ScaffoldPlacementResolver() {}

    /**
     * Fallback when standing on the column cap (feet in {@code topY+1}) after {@link #tryUseExtendRaySameColumnAboveOrApexTop}
     * returns empty: branch one block horizontally at {@code topY} using entity body yaw (same horizontal basis as
     * {@link com.hypixel.hytale.math.vector.Transform#getDirection} with pitch ignored). Ray-based top-face rules run first
     * so looking straight down / at the apex still prefers stack or ray edge placement instead of yaw.
     */
    @Nonnull
    private static java.util.Optional<Vector3i> tryDeckStandHorizontalByBodyYaw(
        @Nonnull World world,
        @Nonnull Vector3i clientPlacement,
        int cx,
        int topY,
        int cz,
        @Nonnull BlockFace resolvedFace,
        @Nullable Float bodyYawRadians
    ) {
        if (bodyYawRadians == null || Float.isNaN(bodyYawRadians)) {
            return java.util.Optional.empty();
        }
        if (resolvedFace != BlockFace.Up) {
            return java.util.Optional.empty();
        }
        if (clientPlacement.x() != cx
            || clientPlacement.z() != cz
            || clientPlacement.y() != topY + 1) {
            return java.util.Optional.empty();
        }
        float yaw = bodyYawRadians;
        double fx = -TrigMathUtil.sin(yaw);
        double fz = -TrigMathUtil.cos(yaw);
        int dx = Math.abs(fx) >= Math.abs(fz) ? (fx > 0.0 ? 1 : -1) : 0;
        int dz = Math.abs(fx) >= Math.abs(fz) ? 0 : (fz > 0.0 ? 1 : -1);
        Vector3i side = cellClearUseExtend(world, cx + dx, topY, cz + dz);
        if (side == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(side);
    }

    public static int highestScaffoldY(@Nonnull World world, int x, int z) {
        int top = -1;
        for (int y = 319; y >= 0; y--) {
            BlockType t = ChunkSectionBlockUtil.blockType(world, x, y, z);
            if (t != null && AetherhavenConstants.WOOD_SCAFFOLD_ITEM_ID.equals(t.getId())) {
                top = y;
                break;
            }
        }
        return top;
    }

    /** Lowest wood scaffold in column {@code (x,z)}, or {@code -1} if none. */
    public static int lowestScaffoldY(@Nonnull World world, int x, int z) {
        for (int y = 0; y <= 319; y++) {
            BlockType t = ChunkSectionBlockUtil.blockType(world, x, y, z);
            if (t != null && AetherhavenConstants.WOOD_SCAFFOLD_ITEM_ID.equals(t.getId())) {
                return y;
            }
        }
        return -1;
    }

    /**
     * Highest Y of the <em>contiguous</em> vertical run of wood scaffold in column {@code (x,z)} that contains
     * {@code ySeed}. Air gaps split one world column into separate stacks; {@link #highestScaffoldY} would wrongly return
     * only the topmost stack in the column.
     */
    public static int scaffoldSegmentTopY(@Nonnull World world, int x, int ySeed, int z) {
        if (ySeed < ChunkUtil.MIN_Y || ySeed > ChunkUtil.HEIGHT_MINUS_1) {
            return -1;
        }
        BlockType at = ChunkSectionBlockUtil.blockType(world, x, ySeed, z);
        if (at == null || !AetherhavenConstants.WOOD_SCAFFOLD_ITEM_ID.equals(at.getId())) {
            return -1;
        }
        int top = ySeed;
        for (int y = ySeed + 1; y <= ChunkUtil.HEIGHT_MINUS_1; y++) {
            BlockType t = ChunkSectionBlockUtil.blockType(world, x, y, z);
            if (t != null && AetherhavenConstants.WOOD_SCAFFOLD_ITEM_ID.equals(t.getId())) {
                top = y;
            } else {
                break;
            }
        }
        return top;
    }

    @Nonnull
    public static Vector3i resolve(
        @Nonnull World world,
        @Nonnull InteractionSyncData clientState,
        @Nonnull Vector3i clientPlacement,
        @Nonnull String placingBlockTypeKey,
        @Nullable Box playerWorldBounds,
        @Nullable Velocity velocity
    ) {
        return tryResolve(world, clientState, clientPlacement, placingBlockTypeKey, playerWorldBounds, velocity)
            .orElse(clientPlacement);
    }

    /**
     * Placement rules for {@link com.hexvane.aetherhaven.scaffold.ScaffoldUseExtendInteraction} (Use / F): tower-snap from
     * scaffold sides without requiring ray hits, and top-face branch using a wide center zone plus nearest-edge direction.
     * No jump-velocity guard so extend works mid-air.
     *
     * @param bodyYawRadians entity horizontal yaw (radians), used as a cap-stand fallback when ray-based rules do not
     *                       pick stack or ray-edge horizontal placement
     */
    @Nonnull
    public static Vector3i resolveUseExtend(
        @Nonnull World world,
        @Nonnull InteractionSyncData clientState,
        @Nonnull Vector3i clientPlacement,
        @Nonnull String placingBlockTypeKey,
        @Nullable Float bodyYawRadians
    ) {
        return tryResolveUseExtend(world, clientState, clientPlacement, placingBlockTypeKey, bodyYawRadians)
            .orElse(clientPlacement);
    }

    /**
     * Ray in the scaffold column: air above the apex always stacks up; hits on the apex block in the upper band use
     * top-face rules (center vs nearest cardinal). Runs before horizontal-face placement so Use (F) does not place
     * beside the cap when the player is clearly aiming at the top.
     */
    @Nonnull
    private static java.util.Optional<Vector3i> tryUseExtendRaySameColumnAboveOrApexTop(
        @Nonnull World world,
        @Nonnull Vector3i clientPlacement,
        int cx,
        int topY,
        int cz,
        @Nullable Position ray,
        @Nullable org.joml.Vector3fc rayN,
        boolean rayColumn,
        int rayHitY,
        double relYOnApex,
        @Nonnull Vector3i apexPlusOne
    ) {
        if (!rayColumn || ray == null) {
            return java.util.Optional.empty();
        }
        if (rayHitY > topY) {
            Vector3i up = cellClearForTower(world, apexPlusOne.x(), apexPlusOne.y(), apexPlusOne.z());
            return up != null ? java.util.Optional.of(up) : java.util.Optional.empty();
        }
        boolean hitNormalFavorsUp = rayN != null && rayN.y() > 0.35f;
        if (rayHitY == topY && (relYOnApex >= USE_TOP_FACE_MIN_LOCAL_Y || hitNormalFavorsUp)) {
            double lx = ray.x - (cx + 0.5);
            double lz = ray.z - (cz + 0.5);
            double ax = Math.abs(lx);
            double az = Math.abs(lz);
            if (Math.max(ax, az) < USE_TOP_FACE_CENTER_MAX) {
                Vector3i stacked = cellClearForTower(world, cx, topY + 1, cz);
                if (stacked != null) {
                    return java.util.Optional.of(stacked);
                }
                return java.util.Optional.empty();
            }
            int dx = ax >= az ? (lx >= 0.0 ? 1 : -1) : 0;
            int dz = ax >= az ? 0 : (lz >= 0.0 ? 1 : -1);
            for (int step = 1; step <= MAX_HORIZONTAL_BRANCH_STEPS; step++) {
                Vector3i candidate = cellClearUseExtend(world, cx + dx * step, topY, cz + dz * step);
                if (candidate != null) {
                    return java.util.Optional.of(candidate);
                }
            }
            return java.util.Optional.empty();
        }
        return java.util.Optional.empty();
    }

    @Nonnull
    private static java.util.Optional<Vector3i> tryResolveUseExtend(
        @Nonnull World world,
        @Nonnull InteractionSyncData clientState,
        @Nonnull Vector3i clientPlacement,
        @Nonnull String placingBlockTypeKey,
        @Nullable Float bodyYawRadians
    ) {
        if (!AetherhavenConstants.WOOD_SCAFFOLD_ITEM_ID.equals(placingBlockTypeKey)) {
            return java.util.Optional.empty();
        }

        Position ray = clientState.raycastHit;
        org.joml.Vector3fc rayN = clientState.raycastNormal;
        Vector3i columnApex = resolveUseExtendColumn(world, ray, clientPlacement, clientState.blockFace);
        if (columnApex == null) {
            return java.util.Optional.empty();
        }

        int cx = columnApex.x();
        int topY = columnApex.y();
        int cz = columnApex.z();

        Vector3i attachRef = resolveAttachColumn(world, clientPlacement, clientState.blockFace);
        BlockType belowPlacement =
            ChunkSectionBlockUtil.blockType(world, clientPlacement.x(), clientPlacement.y() - 1, clientPlacement.z());
        boolean belowPlacementIsScaffold =
            belowPlacement != null && AetherhavenConstants.WOOD_SCAFFOLD_ITEM_ID.equals(belowPlacement.getId());
        BlockFace pFace = clientState.blockFace;
        BlockFace face =
            pFace == BlockFace.Down && belowPlacementIsScaffold
                ? BlockFace.Up
                : (pFace == BlockFace.Down && clientPlacement.y() > attachRef.y() ? BlockFace.Up : pFace);

        Vector3i apexPlusOne = new Vector3i(cx, topY + 1, cz);

        boolean rayColumn = ray != null && floorBlock(ray.x) == cx && floorBlock(ray.z) == cz;
        int rayHitY = ray != null ? floorBlock(ray.y) : Integer.MIN_VALUE;
        double relYOnApex = ray != null ? (ray.y - (double) topY) : -99.0;

        if (rayColumn && rayHitY < topY) {
            BlockType hitSeg = ChunkSectionBlockUtil.blockType(world, cx, rayHitY, cz);
            if (hitSeg != null && AetherhavenConstants.WOOD_SCAFFOLD_ITEM_ID.equals(hitSeg.getId())) {
                Vector3i up = cellClearForTower(world, apexPlusOne.x(), apexPlusOne.y(), apexPlusOne.z());
                return java.util.Optional.of(up != null ? up : clientPlacement);
            }
        }

        java.util.Optional<Vector3i> sameColumnTop =
            tryUseExtendRaySameColumnAboveOrApexTop(
                world,
                clientPlacement,
                cx,
                topY,
                cz,
                ray,
                rayN,
                rayColumn,
                rayHitY,
                relYOnApex,
                apexPlusOne
            );
        if (sameColumnTop.isPresent()) {
            return sameColumnTop;
        }

        java.util.Optional<Vector3i> deckStand =
            tryDeckStandHorizontalByBodyYaw(world, clientPlacement, cx, topY, cz, face, bodyYawRadians);
        if (deckStand.isPresent()) {
            return deckStand;
        }

        if (isHorizontalSideFace(face)) {
            // Ray on column at cap: tower first, else branch beside (tower blocked).
            if (rayColumn && rayHitY == topY) {
                Vector3i upCap = cellClearForTower(world, apexPlusOne.x(), apexPlusOne.y(), apexPlusOne.z());
                if (upCap != null) {
                    return java.util.Optional.of(upCap);
                }
                Vector3i beside = resolveHorizontalBesideColumn(world, cx, topY, cz, ray, rayN, face);
                if (beside != null) {
                    return java.util.Optional.of(beside);
                }
                return java.util.Optional.of(clientPlacement);
            }
            // Ray in column below apex: stack on apex+1 (often already handled above; keep for non-scaffold ray hits).
            if (rayColumn && rayHitY < topY) {
                Vector3i up = cellClearForTower(world, apexPlusOne.x(), apexPlusOne.y(), apexPlusOne.z());
                return java.util.Optional.of(up != null ? up : clientPlacement);
            }
            // Side USE with no ray / ray not in column: client still sends a horizontal face on scaffold — stack
            // vertically on this column instead of placing in the adjacent cell beside the face you clicked.
            Vector3i sideUseTower = cellClearForTower(world, apexPlusOne.x(), apexPlusOne.y(), apexPlusOne.z());
            if (sideUseTower != null) {
                return java.util.Optional.of(sideUseTower);
            }
            Vector3i beside = resolveHorizontalBesideColumn(world, cx, topY, cz, ray, rayN, face);
            if (beside != null) {
                return java.util.Optional.of(beside);
            }
            return java.util.Optional.of(clientPlacement);
        }

        if (pFace == BlockFace.None) {
            if (rayColumn && rayHitY > topY) {
                Vector3i up = cellClearForTower(world, apexPlusOne.x(), apexPlusOne.y(), apexPlusOne.z());
                return java.util.Optional.of(up != null ? up : clientPlacement);
            }
            if (clientPlacement.x() == cx
                && clientPlacement.z() == cz
                && clientPlacement.y() >= topY + 1) {
                Vector3i up = cellClearForTower(world, apexPlusOne.x(), apexPlusOne.y(), apexPlusOne.z());
                return java.util.Optional.of(up != null ? up : clientPlacement);
            }
            return java.util.Optional.empty();
        }

        if (face == BlockFace.Up) {
            if (rayColumn && rayHitY < topY) {
                BlockType lowerSeg = ChunkSectionBlockUtil.blockType(world, cx, rayHitY, cz);
                if (lowerSeg != null && AetherhavenConstants.WOOD_SCAFFOLD_ITEM_ID.equals(lowerSeg.getId())) {
                    Vector3i up = cellClearForTower(world, apexPlusOne.x(), apexPlusOne.y(), apexPlusOne.z());
                    return java.util.Optional.of(up != null ? up : clientPlacement);
                }
            }
            if (rayColumn && rayHitY > topY) {
                Vector3i up = cellClearForTower(world, apexPlusOne.x(), apexPlusOne.y(), apexPlusOne.z());
                return java.util.Optional.of(up != null ? up : clientPlacement);
            }

            if (ray == null) {
                int hdx = clientPlacement.x() - cx;
                int hdz = clientPlacement.z() - cz;
                int py = clientPlacement.y();
                boolean cardinalFromColumn =
                    Math.abs(hdx) + Math.abs(hdz) == 1 && (py == topY || py == topY + 1);
                if (cardinalFromColumn) {
                    Vector3i inferred = cellClearUseExtend(world, clientPlacement.x(), topY, clientPlacement.z());
                    if (inferred != null) {
                        return java.util.Optional.of(inferred);
                    }
                }
            }

            if (ray != null && floorBlock(ray.x) == cx && floorBlock(ray.z) == cz) {
                double lx = ray.x - (cx + 0.5);
                double lz = ray.z - (cz + 0.5);
                double ax = Math.abs(lx);
                double az = Math.abs(lz);

                if (Math.max(ax, az) < TOP_FACE_CENTER_MAX) {
                    Vector3i stacked = cellClearForTower(world, cx, topY + 1, cz);
                    return java.util.Optional.of(stacked != null ? stacked : clientPlacement);
                }
                int ox = ax >= az ? (lx > 0.0 ? 1 : -1) : 0;
                int oz = ax >= az ? 0 : (lz > 0.0 ? 1 : -1);
                for (int step = 1; step <= MAX_HORIZONTAL_BRANCH_STEPS; step++) {
                    Vector3i candidate = cellClearUseExtend(world, cx + ox * step, topY, cz + oz * step);
                    if (candidate != null) {
                        return java.util.Optional.of(candidate);
                    }
                }
                return java.util.Optional.of(clientPlacement);
            }

            Vector3i stackUp = cellClearForTower(world, apexPlusOne.x(), apexPlusOne.y(), apexPlusOne.z());
            return java.util.Optional.of(stackUp != null ? stackUp : clientPlacement);
        }

        return java.util.Optional.empty();
    }

    public static boolean shouldUseUpPlacementNormalUseExtend(
        @Nonnull World world,
        @Nonnull InteractionSyncData clientState,
        @Nonnull Vector3i clientPlacement,
        @Nonnull Vector3i resolvedTarget,
        @Nonnull String placingBlockTypeKey
    ) {
        if (!AetherhavenConstants.WOOD_SCAFFOLD_ITEM_ID.equals(placingBlockTypeKey)) {
            return false;
        }
        Vector3i columnApex = resolveUseExtendColumn(world, clientState.raycastHit, clientPlacement, clientState.blockFace);
        if (columnApex == null) {
            return false;
        }
        Vector3i apexPlusOne = new Vector3i(columnApex.x(), columnApex.y() + 1, columnApex.z());
        return resolvedTarget.equals(apexPlusOne);
    }

    @Nonnull
    public static java.util.Optional<Vector3i> tryResolve(
        @Nonnull World world,
        @Nonnull InteractionSyncData clientState,
        @Nonnull Vector3i clientPlacement,
        @Nonnull String placingBlockTypeKey,
        @Nullable Box playerWorldBounds,
        @Nullable Velocity velocity
    ) {
        if (!AetherhavenConstants.WOOD_SCAFFOLD_ITEM_ID.equals(placingBlockTypeKey)) {
            return java.util.Optional.empty();
        }
        if (velocity != null && velocity.getY() > JUMP_VY_THRESHOLD) {
            return java.util.Optional.empty();
        }

        Vector3i attach = resolveAttachColumn(world, clientPlacement, clientState.blockFace);
        Position ray = clientState.raycastHit;

        BlockType attachType = ChunkSectionBlockUtil.blockType(world, attach.x(), attach.y(), attach.z());
        if (attachType == null || !AetherhavenConstants.WOOD_SCAFFOLD_ITEM_ID.equals(attachType.getId())) {
            return java.util.Optional.empty();
        }

        int topY = highestScaffoldY(world, attach.x(), attach.z());
        if (topY < 0) {
            return java.util.Optional.empty();
        }

        Vector3i apexPlusOne = new Vector3i(attach.x(), topY + 1, attach.z());
        BlockFace pFace = clientState.blockFace;
        BlockFace face =
            pFace == BlockFace.Down && clientPlacement.y() > attach.y() ? BlockFace.Up : pFace;

        if (isHorizontalSideFace(face)) {
            if (attach.y() < topY) {
                if (rayHitsScaffoldColumnBelowApex(world, ray, attach.x(), attach.z(), topY)) {
                    Vector3i up = cellClearForTower(world, apexPlusOne.x(), apexPlusOne.y(), apexPlusOne.z());
                    return java.util.Optional.of(up != null ? up : clientPlacement);
                }
                return java.util.Optional.of(clientPlacement);
            }
            return java.util.Optional.of(clientPlacement);
        }

        if (pFace == BlockFace.None) {
            return java.util.Optional.empty();
        }

        if (face == BlockFace.Up) {
            if (attach.y() < topY) {
                Vector3i up = cellClearForTower(world, apexPlusOne.x(), apexPlusOne.y(), apexPlusOne.z());
                return java.util.Optional.of(up != null ? up : clientPlacement);
            }

            if (attach.y() == topY && ray == null) {
                int hdx = clientPlacement.x() - attach.x();
                int hdz = clientPlacement.z() - attach.z();
                int py = clientPlacement.y();
                boolean cardinalFromColumn =
                    Math.abs(hdx) + Math.abs(hdz) == 1 && (py == topY || py == topY + 1);
                if (cardinalFromColumn) {
                    Vector3i inferred =
                        cellIfClear(world, clientPlacement.x(), topY, clientPlacement.z(), playerWorldBounds);
                    if (inferred != null) {
                        return java.util.Optional.of(inferred);
                    }
                }
            }

            if (ray != null && floorBlock(ray.x) == attach.x() && floorBlock(ray.z) == attach.z()) {
                double lx = ray.x - (attach.x() + 0.5);
                double lz = ray.z - (attach.z() + 0.5);
                double ax = Math.abs(lx);
                double az = Math.abs(lz);
                if (Math.max(ax, az) < TOP_FACE_CENTER_MAX) {
                    Vector3i stacked = cellClearForTower(world, attach.x(), attach.y() + 1, attach.z());
                    return java.util.Optional.of(stacked != null ? stacked : clientPlacement);
                }
                int ox = ax >= az ? (lx > 0.0 ? 1 : -1) : 0;
                int oz = ax >= az ? 0 : (lz > 0.0 ? 1 : -1);
                for (int step = 1; step <= MAX_HORIZONTAL_BRANCH_STEPS; step++) {
                    int tx = attach.x() + ox * step;
                    int ty = attach.y();
                    int tz = attach.z() + oz * step;
                    Vector3i candidate = cellIfClear(world, tx, ty, tz, playerWorldBounds);
                    if (candidate != null) {
                        return java.util.Optional.of(candidate);
                    }
                }
                return java.util.Optional.of(clientPlacement);
            }

            Vector3i stackUp = cellClearForTower(world, apexPlusOne.x(), apexPlusOne.y(), apexPlusOne.z());
            return java.util.Optional.of(stackUp != null ? stackUp : clientPlacement);
        }

        return java.util.Optional.empty();
    }

    public static boolean shouldUseUpPlacementNormal(
        @Nonnull World world,
        @Nonnull InteractionSyncData clientState,
        @Nonnull Vector3i clientPlacement,
        @Nonnull Vector3i resolvedTarget,
        @Nonnull String placingBlockTypeKey,
        @Nullable Velocity velocity
    ) {
        if (velocity != null && velocity.getY() > JUMP_VY_THRESHOLD) {
            return false;
        }
        if (!AetherhavenConstants.WOOD_SCAFFOLD_ITEM_ID.equals(placingBlockTypeKey)) {
            return false;
        }
        Vector3i attach = resolveAttachColumn(world, clientPlacement, clientState.blockFace);
        BlockType hit = ChunkSectionBlockUtil.blockType(world, attach.x(), attach.y(), attach.z());
        if (hit == null || !AetherhavenConstants.WOOD_SCAFFOLD_ITEM_ID.equals(hit.getId())) {
            return false;
        }
        int topY = highestScaffoldY(world, attach.x(), attach.z());
        if (topY < 0) {
            return false;
        }
        Vector3i apexPlusOne = new Vector3i(attach.x(), topY + 1, attach.z());
        if (!resolvedTarget.equals(apexPlusOne)) {
            return false;
        }
        BlockFace pFace = clientState.blockFace;
        BlockFace face =
            pFace == BlockFace.Down && clientPlacement.y() > attach.y() ? BlockFace.Up : pFace;
        if (isHorizontalSideFace(face) && attach.y() < topY) {
            return true;
        }
        return face == BlockFace.Up;
    }

    /**
     * Column apex (x, topY, z) for Use (F): prefer the scaffold cell hit by the ray, using the top of that voxel’s
     * <em>contiguous</em> vertical segment (air gaps split stacks in the same XZ); fall back to {@link #resolveAttachColumn}
     * and the segment containing the attach cell.
     */
    @Nullable
    private static Vector3i resolveUseExtendColumn(
        @Nonnull World world,
        @Nullable Position ray,
        @Nonnull Vector3i clientPlacement,
        @Nonnull BlockFace protocolFace
    ) {
        if (ray != null) {
            int rx = floorBlock(ray.x);
            int ry = floorBlock(ray.y);
            int rz = floorBlock(ray.z);
            BlockType hit = ChunkSectionBlockUtil.blockType(world, rx, ry, rz);
            if (hit != null && AetherhavenConstants.WOOD_SCAFFOLD_ITEM_ID.equals(hit.getId())) {
                int apexY = scaffoldSegmentTopY(world, rx, ry, rz);
                if (apexY >= 0) {
                    return new Vector3i(rx, apexY, rz);
                }
            }
        }
        Vector3i attach = resolveAttachColumn(world, clientPlacement, protocolFace);
        BlockType at = ChunkSectionBlockUtil.blockType(world, attach.x(), attach.y(), attach.z());
        if (at == null || !AetherhavenConstants.WOOD_SCAFFOLD_ITEM_ID.equals(at.getId())) {
            return null;
        }
        int apexY = scaffoldSegmentTopY(world, attach.x(), attach.y(), attach.z());
        if (apexY < 0) {
            return null;
        }
        return new Vector3i(attach.x(), apexY, attach.z());
    }

    /** Use (F): allow adjacent cells that overlap the player (standing on / inside scaffold). */
    @Nullable
    private static Vector3i cellClearUseExtend(@Nonnull World world, int x, int y, int z) {
        return cellIfClear(world, x, y, z, null);
    }

    /**
     * Horizontal neighbor beside the column at apex height: ray normal (outward from hit face), then block face, then ray
     * offset from column center on XZ.
     */
    @Nullable
    private static Vector3i resolveHorizontalBesideColumn(
        @Nonnull World world,
        int cx,
        int topY,
        int cz,
        @Nullable Position ray,
        @Nullable org.joml.Vector3fc rayNormal,
        @Nonnull BlockFace protocolHorizFace
    ) {
        if (rayNormal != null) {
            float nx = rayNormal.x();
            float nz = rayNormal.z();
            float ax = Math.abs(nx);
            float az = Math.abs(nz);
            if (ax >= az && ax >= 0.12f) {
                int dx = nx > 0.0f ? 1 : -1;
                Vector3i t = cellClearUseExtend(world, cx + dx, topY, cz);
                if (t != null) {
                    return t;
                }
            }
            if (az >= 0.12f) {
                int dz = nz > 0.0f ? 1 : -1;
                Vector3i t = cellClearUseExtend(world, cx, topY, cz + dz);
                if (t != null) {
                    return t;
                }
            }
        }
        if (isHorizontalSideFace(protocolHorizFace)) {
            Vector3i dir = new Vector3i(
                com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockFace.fromProtocolFace(protocolHorizFace)
                    .getDirection());
            if (dir.y() == 0 && (dir.x() != 0 || dir.z() != 0)) {
                Vector3i t = cellClearUseExtend(world, cx + dir.x(), topY, cz + dir.z());
                if (t != null) {
                    return t;
                }
            }
        }
        if (ray != null) {
            double lx = ray.x - (cx + 0.5);
            double lz = ray.z - (cz + 0.5);
            double ax = Math.abs(lx);
            double az = Math.abs(lz);
            int dx = ax >= az ? (lx >= 0.0 ? 1 : -1) : 0;
            int dz = ax >= az ? 0 : (lz >= 0.0 ? 1 : -1);
            return cellClearUseExtend(world, cx + dx, topY, cz + dz);
        }
        return null;
    }

    /**
     * Attach cell for column math: vanilla-style neighbor, scaffold at {@code blockPosition}, or cell below placement when
     * it is exactly {@code topY+1} above the column apex (stack on top).
     */
    @Nonnull
    private static Vector3i resolveAttachColumn(
        @Nonnull World world,
        @Nonnull Vector3i clientPlacement,
        @Nonnull BlockFace protocolFace
    ) {
        Vector3i primary = attachmentCell(clientPlacement, protocolFace);
        BlockType atPlacement = ChunkSectionBlockUtil.blockType(world, clientPlacement.x(), clientPlacement.y(), clientPlacement.z());
        if (atPlacement != null && AetherhavenConstants.WOOD_SCAFFOLD_ITEM_ID.equals(atPlacement.getId())) {
            return new Vector3i(clientPlacement);
        }
        BlockType atPrimary = ChunkSectionBlockUtil.blockType(world, primary.x(), primary.y(), primary.z());
        if (atPrimary != null && AetherhavenConstants.WOOD_SCAFFOLD_ITEM_ID.equals(atPrimary.getId())) {
            return primary;
        }
        BlockType below =
            ChunkSectionBlockUtil.blockType(world, clientPlacement.x(), clientPlacement.y() - 1, clientPlacement.z());
        if (below != null && AetherhavenConstants.WOOD_SCAFFOLD_ITEM_ID.equals(below.getId())) {
            int x = clientPlacement.x();
            int z = clientPlacement.z();
            int topY = highestScaffoldY(world, x, z);
            if (topY >= 0 && clientPlacement.y() == topY + 1) {
                return new Vector3i(x, topY, z);
            }
        }
        return primary;
    }

    private static boolean rayHitsScaffoldColumnBelowApex(
        @Nonnull World world,
        @Nullable Position ray,
        int colX,
        int colZ,
        int topY
    ) {
        if (ray == null) {
            return false;
        }
        int rx = floorBlock(ray.x);
        int rz = floorBlock(ray.z);
        if (rx != colX || rz != colZ) {
            return false;
        }
        int ry = floorBlock(ray.y);
        BlockType t = ChunkSectionBlockUtil.blockType(world, rx, ry, rz);
        return t != null
            && AetherhavenConstants.WOOD_SCAFFOLD_ITEM_ID.equals(t.getId())
            && ry < topY;
    }

    @Nonnull
    private static Vector3i attachmentCell(@Nonnull Vector3i placement, @Nonnull BlockFace protocolFace) {
        Vector3i dir = new Vector3i(
            com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockFace.fromProtocolFace(protocolFace)
                .getDirection());
        return new Vector3i(placement).sub(dir);
    }

    @Nullable
    private static Vector3i cellClearForTower(@Nonnull World world, int x, int y, int z) {
        if (y < 0 || y >= 320) {
            return null;
        }
        if (!isReplaceable(world, x, y, z)) {
            return null;
        }
        return new Vector3i(x, y, z);
    }

    @Nullable
    private static Vector3i cellIfClear(
        @Nonnull World world,
        int x,
        int y,
        int z,
        @Nullable Box playerWorldBounds
    ) {
        if (y < 0 || y >= 320) {
            return null;
        }
        if (!isReplaceable(world, x, y, z)) {
            return null;
        }
        if (playerWorldBounds != null && cellIntersectsBox(x, y, z, playerWorldBounds)) {
            return null;
        }
        return new Vector3i(x, y, z);
    }

    private static boolean isReplaceable(@Nonnull World world, int x, int y, int z) {
        BlockType t = ChunkSectionBlockUtil.blockType(world, x, y, z);
        if (t == null) {
            return false;
        }
        if ("Empty".equals(t.getId())) {
            return true;
        }
        return t.getMaterial() == BlockMaterial.Empty;
    }

    private static boolean cellIntersectsBox(int x, int y, int z, @Nonnull Box playerWorld) {
        Box cell = new Box(x, y, z, x + 1, y + 1, z + 1);
        return playerWorld.isIntersecting(cell);
    }

    private static int floorBlock(double v) {
        int i = (int) v;
        if (v < 0.0 && v != i) {
            return i - 1;
        }
        return i;
    }

    private static boolean isHorizontalSideFace(BlockFace pFace) {
        return pFace == BlockFace.North
            || pFace == BlockFace.South
            || pFace == BlockFace.East
            || pFace == BlockFace.West;
    }
}
