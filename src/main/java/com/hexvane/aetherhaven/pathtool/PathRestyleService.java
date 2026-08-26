package com.hexvane.aetherhaven.pathtool;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;

import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.config.PathToolStyleDefinition;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Restyles a committed path by restoring undo terrain on path surfaces, then placing a new style.
 * Undo snapshots stay as the original pre-path blocks so remove still works correctly.
 */
public final class PathRestyleService {
    private static final int PLACE = 2;
    private static final int SET = 2;
    @Nonnull
    private static final RotationTuple FLAT = RotationTuple.NONE;

    private PathRestyleService() {}

    /**
     * Restyles a committed path by restoring original terrain from undo, then placing the new style.
     * Undo snapshots are left unchanged so a later remove still restores pre-path blocks.
     *
     * @param fallbackWidthBlocks used when the commit has no stored width and width cannot be inferred
     */
    public static int restyle(
        @Nonnull World world,
        @Nonnull AetherhavenPluginConfig cfg,
        @Nonnull PathCommitRecord rec,
        int pathStyleIndex,
        int fallbackWidthBlocks
    ) {
        return restyle(world, cfg, rec, pathStyleIndex, fallbackWidthBlocks, ThreadLocalRandom.current());
    }

    public static int restyle(
        @Nonnull World world,
        @Nonnull AetherhavenPluginConfig cfg,
        @Nonnull PathCommitRecord rec,
        int pathStyleIndex,
        int fallbackWidthBlocks,
        @Nonnull Random random
    ) {
        List<SurfaceCell> surfaces = resolveSurfaceCells(rec);
        if (surfaces.isEmpty()) {
            return 0;
        }
        int width = resolveWidth(rec, surfaces, fallbackWidthBlocks);
        // Put original ground back on path surfaces first so remove after restyle never
        // restores a previous style. Leave foliage-clear undo cells alone (still air until remove).
        restoreSurfaceCells(world, surfaces, rec);
        int ok = 0;
        for (SurfaceCell cell : surfaces) {
            int lateral = cell.lateralIndex != null
                ? cell.lateralIndex
                : inferLateral(cell.x, cell.z, rec.navNodes, width);
            String placeId = PathCementService.pickPlaceId(lateral, random, pathStyleIndex, width, cfg);
            WorldChunk ch = ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(cell.x, cell.z));
            if (ch == null) {
                continue;
            }
            if (!PathCementService.placePathBlock(world, cell.x, cell.y, cell.z, placeId, RotationTuple.NONE_INDEX, PLACE)) {
                continue;
            }
            ok++;
        }
        return ok;
    }

    /** Restores only surface undo cells (matched by x/y/z) before placing the new style. */
    private static void restoreSurfaceCells(
        @Nonnull World world,
        @Nonnull List<SurfaceCell> surfaces,
        @Nonnull PathCommitRecord rec
    ) {
        if (rec.undo == null || rec.undo.isEmpty() || surfaces.isEmpty()) {
            return;
        }
        Map<String, PathToolUndoCell> byPos = new HashMap<>();
        for (PathToolUndoCell u : rec.undo) {
            if (u == null) {
                continue;
            }
            byPos.put(u.x + ":" + u.y + ":" + u.z, u);
        }
        for (SurfaceCell cell : surfaces) {
            PathToolUndoCell u = byPos.get(cell.x + ":" + cell.y + ":" + cell.z);
            if (u == null) {
                continue;
            }
            WorldChunk ch = ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(u.x, u.z));
            if (ch == null) {
                continue;
            }
            @Nullable
            BlockType t = BlockType.getAssetMap().getAsset(u.blockId);
            if (t == null) {
                t = BlockType.EMPTY;
            }
            int index = t == BlockType.EMPTY ? BlockType.EMPTY_ID : BlockType.getAssetMap().getIndex(u.blockId);
            if (t != BlockType.EMPTY) {
                ch.setBlock(u.x, u.y, u.z, index, t, u.rotationIndex, 0, SET);
            } else {
                ch.setBlock(u.x, u.y, u.z, BlockType.EMPTY_ID, BlockType.EMPTY, 0, 0, 10);
            }
        }
    }

    @Nonnull
    private static List<SurfaceCell> resolveSurfaceCells(@Nonnull PathCommitRecord rec) {
        if (rec.undo == null || rec.undo.isEmpty()) {
            return List.of();
        }
        boolean anyLat = false;
        for (PathToolUndoCell u : rec.undo) {
            if (u != null && u.lateralIndex != null) {
                anyLat = true;
                break;
            }
        }
        if (anyLat) {
            List<SurfaceCell> out = new ArrayList<>();
            for (PathToolUndoCell u : rec.undo) {
                if (u == null || u.lateralIndex == null) {
                    continue;
                }
                out.add(new SurfaceCell(u.x, u.y, u.z, u.lateralIndex));
            }
            return out;
        }
        // Legacy: foliage clears are above the surface, so take the lowest y per (x,z).
        Map<String, PathToolUndoCell> lowest = new HashMap<>();
        for (PathToolUndoCell u : rec.undo) {
            if (u == null) {
                continue;
            }
            String key = u.x + ":" + u.z;
            PathToolUndoCell prev = lowest.get(key);
            if (prev == null || u.y < prev.y) {
                lowest.put(key, u);
            }
        }
        List<SurfaceCell> out = new ArrayList<>(lowest.size());
        for (PathToolUndoCell u : lowest.values()) {
            out.add(new SurfaceCell(u.x, u.y, u.z, null));
        }
        return out;
    }

    private static int resolveWidth(
        @Nonnull PathCommitRecord rec,
        @Nonnull List<SurfaceCell> surfaces,
        int fallbackWidthBlocks
    ) {
        if (rec.pathWidthBlocks >= 1 && rec.pathWidthBlocks <= PathToolStyleDefinition.MAX_PATH_WIDTH_BLOCKS) {
            return rec.pathWidthBlocks;
        }
        int inferred = inferWidthFromNav(surfaces, rec.navNodes);
        if (inferred >= 1) {
            return inferred;
        }
        return Math.max(1, Math.min(PathToolStyleDefinition.MAX_PATH_WIDTH_BLOCKS, fallbackWidthBlocks));
    }

    private static int inferWidthFromNav(
        @Nonnull List<SurfaceCell> surfaces,
        @Nullable List<PathNavPoint> navNodes
    ) {
        if (navNodes == null || navNodes.size() < 2 || surfaces.isEmpty()) {
            return 0;
        }
        double maxAbs = 0.0;
        for (SurfaceCell cell : surfaces) {
            @Nullable
            Double signed = signedLateralOffset(cell.x, cell.z, navNodes);
            if (signed == null) {
                continue;
            }
            maxAbs = Math.max(maxAbs, Math.abs(signed));
        }
        if (maxAbs <= 0.01) {
            return 1;
        }
        int w = (int) Math.round(maxAbs * 2.0) + 1;
        return Math.max(1, Math.min(PathToolStyleDefinition.MAX_PATH_WIDTH_BLOCKS, w));
    }

    private static int inferLateral(int x, int z, @Nullable List<PathNavPoint> navNodes, int pathWidthBlocks) {
        int w = Math.max(1, Math.min(PathToolStyleDefinition.MAX_PATH_WIDTH_BLOCKS, pathWidthBlocks));
        @Nullable
        Double signed = signedLateralOffset(x, z, navNodes);
        if (signed == null) {
            return w / 2;
        }
        int lat = (int) Math.round(signed + 0.5 * (w - 1));
        return Math.max(0, Math.min(w - 1, lat));
    }

    @Nullable
    private static Double signedLateralOffset(int x, int z, @Nullable List<PathNavPoint> navNodes) {
        if (navNodes == null || navNodes.size() < 2) {
            return null;
        }
        double cx = x + 0.5;
        double cz = z + 0.5;
        double bestDistSq = Double.POSITIVE_INFINITY;
        double bestSigned = 0.0;
        boolean hit = false;
        for (int i = 0; i + 1 < navNodes.size(); i++) {
            PathNavPoint a = navNodes.get(i);
            PathNavPoint b = navNodes.get(i + 1);
            if (a == null || b == null) {
                continue;
            }
            double ax = a.x;
            double az = a.z;
            double bx = b.x;
            double bz = b.z;
            double abx = bx - ax;
            double abz = bz - az;
            double abLenSq = abx * abx + abz * abz;
            if (abLenSq < 1.0e-8) {
                continue;
            }
            double t = ((cx - ax) * abx + (cz - az) * abz) / abLenSq;
            t = Math.max(0.0, Math.min(1.0, t));
            double px = ax + abx * t;
            double pz = az + abz * t;
            double dx = cx - px;
            double dz = cz - pz;
            double distSq = dx * dx + dz * dz;
            if (distSq >= bestDistSq) {
                continue;
            }
            double len = Math.sqrt(abLenSq);
            double tx = abx / len;
            double tz = abz / len;
            // Right vector in XZ (up cross tangent): (tz, -tx) for a right-handed horizontal frame.
            double rx = tz;
            double rz = -tx;
            bestDistSq = distSq;
            bestSigned = dx * rx + dz * rz;
            hit = true;
        }
        return hit ? bestSigned : null;
    }

    private record SurfaceCell(int x, int y, int z, @Nullable Integer lateralIndex) {}
}