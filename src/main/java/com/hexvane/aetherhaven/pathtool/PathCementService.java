package com.hexvane.aetherhaven.pathtool;

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
    private static final int MAX_GRASS_CLEAR_ABOVE = 6;
    private static final int MAX_RUBBLE_CLEAR_ABOVE = 32;
    @Nonnull
    private static final RotationTuple FLAT = RotationTuple.NONE;

    private PathCementService() {}

    @Nullable
    public static PathCommitRecord tryCement(
        @Nonnull World world,
        @Nonnull AetherhavenPluginConfig cfg,
        @Nonnull List<PathPlannedCell.Planned> plan,
        int pathStyleIndex
    ) {
        if (plan.isEmpty()) {
            return null;
        }
        List<PathToolUndoCell> undos = new ArrayList<>();
        @Nonnull
        Set<String> grassCleared = new HashSet<>();
        Random r = ThreadLocalRandom.current();
        for (PathPlannedCell.Planned p : plan) {
            int x = p.pos.getX();
            int y = p.pos.getY();
            int z = p.pos.getZ();
            if (!PathToolReplacePredicate.isReplaceable(cfg, world, x, y, z)) {
                continue;
            }
            WorldChunk ch = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
            if (ch == null) {
                continue;
            }
            breakRubbleColumnAbove(world, x, y, z);
            int oldIdx = ch.getBlock(x, y, z);
            BlockType oldT = BlockType.getAssetMap().getAsset(oldIdx);
            if (oldT == null) {
                continue;
            }
            int oldRot = chunkRotationIndex(ch, x, y, z);
            String placeId = pickPlaceId(p.role, r, pathStyleIndex, cfg);
            if (!ch.placeBlock(x, y, z, placeId, FLAT, PLACE, false)) {
                continue;
            }
            PathToolUndoCell u = new PathToolUndoCell();
            u.x = x;
            u.y = y;
            u.z = z;
            u.blockId = oldT.getId();
            u.rotationIndex = oldRot;
            undos.add(u);
            clearPlantGrassColumnAbove(
                world,
                x,
                y,
                z,
                undos,
                grassCleared
            );
        }
        if (undos.isEmpty()) {
            return null;
        }
        PathCommitRecord rec = new PathCommitRecord();
        rec.id = UUID.randomUUID().toString();
        rec.createdMs = System.currentTimeMillis();
        rec.undo = undos;
        return rec;
    }

    @SuppressWarnings({ "deprecation", "removal" })
    private static int chunkRotationIndex(@Nonnull WorldChunk chunk, int x, int y, int z) {
        if (y < 0 || y >= 320) {
            return 0;
        }
        return chunk.getRotationIndex(x, y, z);
    }

    @Nonnull
    private static String pickPlaceId(
        @Nonnull PathPlannedCell.CellRole role,
        @Nonnull Random r,
        int pathStyleIndex,
        @Nonnull AetherhavenPluginConfig cfg
    ) {
        if (role == PathPlannedCell.CellRole.Center) {
            return pickCenterBlockId(r, pathStyleIndex, cfg);
        }
        return r.nextBoolean() ? AetherhavenConstants.PATH_BLOCK_GRASS : AetherhavenConstants.PATH_BLOCK_GRASS_DEEP;
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
     * After placing path surface, removes decorative/tall plant grass ({@code *Plant_Grass*}) stacked above the cell so the
     * path reads cleanly. Each removal is added to the same undo list as the path.
     */
    @SuppressWarnings({ "deprecation", "removal" })
    /**
     * Breaks rubble blocks stacked above the path surface (highest Y first) so {@link World#breakBlock} runs normal
     * break rules instead of silently clearing with {@link WorldChunk#setBlock}.
     */
    private static void breakRubbleColumnAbove(@Nonnull World world, int x, int surfaceY, int z) {
        int top = Math.min(319, surfaceY + MAX_RUBBLE_CLEAR_ABOVE);
        for (int cy = top; cy > surfaceY; cy--) {
            WorldChunk ch = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
            if (ch == null) {
                break;
            }
            int idx = ch.getBlock(x, cy, z);
            BlockType bt = BlockType.getAssetMap().getAsset(idx);
            if (bt == null || bt == BlockType.EMPTY) {
                continue;
            }
            if (PathRubbleUtil.isRubble(bt)) {
                world.breakBlock(x, cy, z, BREAK_SETTINGS);
            }
        }
    }

    private static void clearPlantGrassColumnAbove(
        @Nonnull World world,
        int x,
        int surfaceY,
        int z,
        @Nonnull List<PathToolUndoCell> undos,
        @Nonnull Set<String> alreadyCleared
    ) {
        for (int dy = 1; dy <= MAX_GRASS_CLEAR_ABOVE; dy++) {
            int py = surfaceY + dy;
            if (py < 0 || py >= 320) {
                break;
            }
            String k = x + ":" + py + ":" + z;
            if (alreadyCleared.contains(k)) {
                continue;
            }
            WorldChunk ch = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
            if (ch == null) {
                break;
            }
            int idx = ch.getBlock(x, py, z);
            BlockType bt = BlockType.getAssetMap().getAsset(idx);
            if (bt == null || bt == BlockType.EMPTY) {
                continue;
            }
            String id = bt.getId();
            if (id == null) {
                break;
            }
            if (id.contains("Plant_Grass")) {
                int rot = chunkRotationIndex(ch, x, py, z);
                PathToolUndoCell u = new PathToolUndoCell();
                u.x = x;
                u.y = py;
                u.z = z;
                u.blockId = id;
                u.rotationIndex = rot;
                undos.add(u);
                ch.setBlock(x, py, z, BlockType.EMPTY_ID, BlockType.EMPTY, 0, 0, SET_BLOCK);
                alreadyCleared.add(k);
            } else {
                break;
            }
        }
    }
}
