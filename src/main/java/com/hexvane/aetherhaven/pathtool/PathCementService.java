package com.hexvane.aetherhaven.pathtool;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.config.PathToolStyleDefinition;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Places path blocks and captures a sparse undo snapshot of replaced cells.
 */
public final class PathCementService {
    private static final int PLACE = 2;
    private static final int SET_BLOCK = 10;
    /** Same tuning as plot prefab clears: breaks spawn normal drops/particles where applicable. */
    private static final int BREAK_SETTINGS = 10;
    private static final int MAX_RUBBLE_CLEAR_ABOVE = 32;
    @Nonnull
    private static final RotationTuple FLAT = RotationTuple.NONE;

    private PathCementService() {}

    /** Places a block only when the cell is empty (strict {@link BlockOperations#testPlaceBlock}). */
    public static boolean placePathBlock(
        @Nonnull World world,
        int x,
        int y,
        int z,
        @Nonnull String blockTypeKey,
        int rotationIndex,
        int settings
    ) {
        return placePathBlock(world, x, y, z, blockTypeKey, rotationIndex, settings, false);
    }

    /**
     * @param allowOccupied when {@code true}, skips the empty-cell test (for path replace over soil). Callers
     *     must already have validated the surface is replaceable.
     */
    public static boolean placePathBlock(
        @Nonnull World world,
        int x,
        int y,
        int z,
        @Nonnull String blockTypeKey,
        int rotationIndex,
        int settings,
        boolean allowOccupied
    ) {
        BlockType blockType = BlockType.getAssetMap().getAsset(blockTypeKey);
        if (blockType == null) {
            return false;
        }
        BlockSection section = ChunkSectionBlockUtil.blockSectionAt(world, x, y, z);
        if (section == null) {
            return false;
        }
        if (!allowOccupied) {
            var chunkStore = world.getChunkStore().getStore();
            if (!BlockOperations.testPlaceBlock(chunkStore, section, x, y, z, blockType, rotationIndex)) {
                return false;
            }
        }
        return ChunkSectionBlockUtil.setBlockByKey(world, x, y, z, blockTypeKey, settings);
    }


    @Nullable
    public static PathCommitRecord tryCement(
        @Nonnull World world,
        @Nonnull AetherhavenPluginConfig cfg,
        @Nonnull List<PathPlannedCell.Planned> plan,
        int pathStyleIndex,
        int pathWidthBlocks
    ) {
        return tryCement(world, cfg, plan, pathStyleIndex, pathWidthBlocks, null, ThreadLocalRandom.current());
    }

    /**
     * Seedable variant used by generated towns and other reproducible world-building tools.
     */
    @Nullable
    public static PathCommitRecord tryCement(
        @Nonnull World world,
        @Nonnull AetherhavenPluginConfig cfg,
        @Nonnull List<PathPlannedCell.Planned> plan,
        int pathStyleIndex,
        int pathWidthBlocks,
        @Nonnull Random random
    ) {
        return tryCement(world, cfg, plan, pathStyleIndex, pathWidthBlocks, null, random);
    }

    @Nullable
    public static PathCommitRecord tryCement(
        @Nonnull World world,
        @Nonnull AetherhavenPluginConfig cfg,
        @Nonnull List<PathPlannedCell.Planned> plan,
        int pathStyleIndex,
        int pathWidthBlocks,
        @Nullable Set<String> playerReplaceBlockIds,
        @Nonnull Random random
    ) {
        if (plan.isEmpty()) {
            return null;
        }
        List<PathToolUndoCell> undos = new ArrayList<>();
        @Nonnull
        Set<String> grassCleared = new HashSet<>();
        for (PathPlannedCell.Planned p : plan) {
            int x = p.pos.x();
            int y = p.pos.y();
            int z = p.pos.z();
            WorldChunk ch = ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(x, z));
            if (ch == null) {
                continue;
            }
            prepareColumnForPathSurface(world, x, y, z, undos, grassCleared);
            if (!PathToolReplacePredicate.isReplaceable(cfg, world, x, y, z, playerReplaceBlockIds)) {
                continue;
            }
            int oldIdx = ChunkSectionBlockUtil.blockId(world, x, y, z);
            BlockType oldT = BlockType.getAssetMap().getAsset(oldIdx);
            if (oldT == null) {
                continue;
            }
            int oldRot = ChunkSectionBlockUtil.rotationIndex(world, x, y, z);
            String placeId = pickPlaceId(p.lateralIndex, random, pathStyleIndex, pathWidthBlocks, cfg);
            if (!placePathBlock(world, x, y, z, placeId, RotationTuple.NONE_INDEX, PLACE, true)) {
                continue;
            }
            PathToolUndoCell u = new PathToolUndoCell();
            u.x = x;
            u.y = y;
            u.z = z;
            u.blockId = oldT.getId();
            u.rotationIndex = oldRot;
            u.lateralIndex = p.lateralIndex;
            undos.add(u);
        }
        return newShellRecord(undos, pathWidthBlocks);
    }

    @Nonnull
    public static PathCommitRecord newShellRecord() {
        return newShellRecord(new ArrayList<>(), 0);
    }

    @Nonnull
    private static PathCommitRecord newShellRecord(@Nonnull List<PathToolUndoCell> undos, int pathWidthBlocks) {
        PathCommitRecord rec = new PathCommitRecord();
        rec.id = UUID.randomUUID().toString();
        rec.createdMs = System.currentTimeMillis();
        rec.undo = undos;
        rec.pathWidthBlocks =
            Math.max(0, Math.min(PathToolStyleDefinition.MAX_PATH_WIDTH_BLOCKS, pathWidthBlocks));
        return rec;
    }

    /** Public style block picker shared with restyle. */
    @Nonnull
    public static String pickPlaceId(
        int lateralIndex,
        @Nonnull Random r,
        int pathStyleIndex,
        int pathWidthBlocks,
        @Nonnull AetherhavenPluginConfig cfg
    ) {
        return pickPlaceIdInternal(lateralIndex, r, pathStyleIndex, pathWidthBlocks, cfg);
    }

    @Nonnull
    private static String pickPlaceIdInternal(
        int lateralIndex,
        @Nonnull Random r,
        int pathStyleIndex,
        int pathWidthBlocks,
        @Nonnull AetherhavenPluginConfig cfg
    ) {
        List<PathToolStyleDefinition> styles = cfg.getPathToolStyleDefinitions();
        PathToolStyleDefinition style = null;
        if (!styles.isEmpty()) {
            style = styles.get(Math.floorMod(pathStyleIndex, styles.size()));
        }
        if (style != null && style.hasColumnLayout()) {
            return style.pickBlockForPathCell(lateralIndex, pathWidthBlocks, r);
        }
        PathPlannedCell.CellRole role = lateralRole(lateralIndex, pathWidthBlocks);
        if (role == PathPlannedCell.CellRole.Center) {
            return pickCenterBlockId(r, pathStyleIndex, cfg);
        }
        return r.nextBoolean() ? AetherhavenConstants.PATH_BLOCK_GRASS : AetherhavenConstants.PATH_BLOCK_GRASS_DEEP;
    }

    @Nonnull
    private static PathPlannedCell.CellRole lateralRole(int lateralIndex, int pathWidthBlocks) {
        int w = Math.max(1, Math.min(PathToolStyleDefinition.MAX_PATH_WIDTH_BLOCKS, pathWidthBlocks));
        if (w < 3) {
            return PathPlannedCell.CellRole.Center;
        }
        return lateralIndex == 0 || lateralIndex == w - 1
            ? PathPlannedCell.CellRole.Outline
            : PathPlannedCell.CellRole.Center;
    }

    @Nonnull
    private static String pickCenterBlockId(
        @Nonnull Random r,
        int pathStyleIndex,
        @Nonnull AetherhavenPluginConfig cfg
    ) {
        List<PathToolStyleDefinition> styles = cfg.getPathToolStyleDefinitions();
        if (styles.isEmpty()) {
            return defaultSoilCenterMix(r);
        }
        int idx = Math.floorMod(pathStyleIndex, styles.size());
        List<String> ids = styles.get(idx).getCenterBlockIds();
        if (ids.isEmpty()) {
            return defaultSoilCenterMix(r);
        }
        return ids.get(r.nextInt(ids.size()));
    }

    @Nonnull
    private static String defaultSoilCenterMix(@Nonnull Random r) {
        return r.nextBoolean() ? AetherhavenConstants.PATH_BLOCK_PATHWAY : AetherhavenConstants.PATH_BLOCK_MUD_DRY;
    }

    /**
     * Clears rubble, {@code Plant_Grass*}, and {@code Plant_Bush*} above the path surface before replace checks and
     * placement so foliage never blocks {@link PathGrounding} or {@link WorldChunk#placeBlock}. Removals are undoable.
     */
    private static void prepareColumnForPathSurface(
        @Nonnull World world,
        int x,
        int surfaceY,
        int z,
        @Nonnull List<PathToolUndoCell> undos,
        @Nonnull Set<String> alreadyCleared
    ) {
        int top = Math.min(319, surfaceY + MAX_RUBBLE_CLEAR_ABOVE);
        for (int cy = top; cy > surfaceY; cy--) {
            String k = x + ":" + cy + ":" + z;
            if (alreadyCleared.contains(k)) {
                continue;
            }
            WorldChunk ch = ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(x, z));
            if (ch == null) {
                break;
            }
            int idx = ChunkSectionBlockUtil.blockId(world, x, cy, z);
            BlockType bt = BlockType.getAssetMap().getAsset(idx);
            if (bt == null || bt == BlockType.EMPTY) {
                continue;
            }
            if (!PathFoliageUtil.isClearableAbovePath(bt)) {
                break;
            }
            String id = bt.getId();
            if (PathRubbleUtil.isRubble(bt)) {
                ChunkSectionBlockUtil.breakBlock(world, x, cy, z, BREAK_SETTINGS);
                alreadyCleared.add(k);
                continue;
            }
            if (PathFoliageUtil.isPlantBushId(id) || PathFoliageUtil.isPlantGrassId(id)) {
                appendUndoCell(world, x, cy, z, id, undos);
                if (PathFoliageUtil.isPlantBushId(id)) {
                    ChunkSectionBlockUtil.breakBlock(world, x, cy, z, BREAK_SETTINGS);
                } else {
                    ChunkSectionBlockUtil.setBlockEmpty(world, x, cy, z, SET_BLOCK);
                }
                alreadyCleared.add(k);
                continue;
            }
            break;
        }
    }

    private static void appendUndoCell(
        @Nonnull World world,
        int x,
        int y,
        int z,
        @Nonnull String blockId,
        @Nonnull List<PathToolUndoCell> undos
    ) {
        PathToolUndoCell u = new PathToolUndoCell();
        u.x = x;
        u.y = y;
        u.z = z;
        u.blockId = blockId;
        u.rotationIndex = ChunkSectionBlockUtil.rotationIndex(world, x, y, z);
        undos.add(u);
    }
}
