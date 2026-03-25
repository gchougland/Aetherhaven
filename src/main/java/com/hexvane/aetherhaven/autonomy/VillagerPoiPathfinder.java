package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Short horizontal grid paths for villager POI travel (single-floor interiors). Falls back when no path is found.
 */
public final class VillagerPoiPathfinder {
    private static final int MAX_EXPANSION = 4096;
    private static final int[][] HORIZ = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private VillagerPoiPathfinder() {}

    /**
     * Block rotation index at {@code (x,y,z)} when the chunk is already loaded — no chunk load (safe during entity tick).
     */
    public static int blockRotationIndexNoLoad(@Nonnull World world, int x, int y, int z) {
        if (y < 0 || y >= 320) {
            return 0;
        }
        WorldChunk wc = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        return wc != null ? wc.getRotationIndex(x, y, z) : 0;
    }

    /**
     * @return semicolon-separated {@code bx,bz} waypoints from start (exclusive) toward the POI, or {@code null} to use direct travel
     */
    @Nullable
    public static String findPath(@Nonnull World world, @Nonnull Vector3d startPos, @Nonnull PoiEntry poi) {
        int startBx = (int) Math.floor(startPos.x);
        int startBz = (int) Math.floor(startPos.z);
        int startFeetY = findStandY(world, startBx, startBz, (int) Math.floor(startPos.y) + 2);
        if (startFeetY == Integer.MIN_VALUE) {
            return null;
        }

        int px = poi.getX();
        int py = poi.getY();
        int pz = poi.getZ();

        Set<Long> goals = goalCells(world, px, py, pz);
        if (goals.isEmpty()) {
            return null;
        }

        long startKey = pack(startBx, startBz);
        if (goals.contains(startKey)) {
            return "";
        }

        ArrayDeque<Long> q = new ArrayDeque<>();
        Map<Long, Long> parent = new HashMap<>();
        q.add(startKey);
        parent.put(startKey, startKey);
        int expanded = 0;
        long found = Long.MIN_VALUE;

        while (!q.isEmpty() && expanded < MAX_EXPANSION) {
            long cur = q.removeFirst();
            expanded++;
            int cx = unpackX(cur);
            int cz = unpackZ(cur);
            if (goals.contains(cur)) {
                found = cur;
                break;
            }
            int standY = findStandY(world, cx, cz, Math.max(startFeetY, py) + 3);
            if (standY == Integer.MIN_VALUE) {
                continue;
            }
            for (int[] d : HORIZ) {
                int nx = cx + d[0];
                int nz = cz + d[1];
                long nk = pack(nx, nz);
                if (parent.containsKey(nk)) {
                    continue;
                }
                int nStand = findStandY(world, nx, nz, Math.max(standY, py) + 3);
                if (nStand == Integer.MIN_VALUE) {
                    continue;
                }
                if (Math.abs(nStand - standY) > 2) {
                    continue;
                }
                parent.put(nk, cur);
                q.addLast(nk);
            }
        }

        if (found == Long.MIN_VALUE) {
            return null;
        }

        List<long[]> cells = new ArrayList<>();
        long step = found;
        while (step != startKey) {
            cells.add(new long[] {unpackX(step), unpackZ(step)});
            Long p = parent.get(step);
            if (p == null) {
                return null;
            }
            step = p;
        }
        Collections.reverse(cells);
        if (cells.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(';');
            }
            sb.append(cells.get(i)[0]).append(',').append(cells.get(i)[1]);
        }
        return sb.toString();
    }

    private static Set<Long> goalCells(@Nonnull World world, int px, int py, int pz) {
        Set<Long> goals = new HashSet<>();
        for (int[] d : HORIZ) {
            int nx = px + d[0];
            int nz = pz + d[1];
            int sy = findStandY(world, nx, nz, py + 3);
            if (sy == Integer.MIN_VALUE) {
                continue;
            }
            if (Math.abs(sy - py) > 2) {
                continue;
            }
            if (walkableColumn(world, nx, sy, nz) && !occupiesSameFootprint(px, py, pz, nx, sy, nz)) {
                goals.add(pack(nx, nz));
            }
        }
        return goals;
    }

    private static boolean occupiesSameFootprint(int px, int py, int pz, int nx, int ny, int nz) {
        return nx == px && nz == pz;
    }

    static int findStandY(@Nonnull World world, int bx, int bz, int searchTopY) {
        int top = Math.min(319, searchTopY);
        for (int y = top; y >= Math.max(1, top - 10); y--) {
            if (walkableColumn(world, bx, y, bz)) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    /**
     * Block at ({@code x},{@code y},{@code z}) when its chunk is already resident — no chunk load / ticking flag changes.
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

    private static boolean walkableColumn(@Nonnull World world, int bx, int by, int bz) {
        BlockType feet = blockTypeNoLoad(world, bx, by, bz);
        BlockType head = blockTypeNoLoad(world, bx, by + 1, bz);
        BlockType below = blockTypeNoLoad(world, bx, by - 1, bz);
        if (feet == null || head == null || below == null) {
            return false;
        }
        return isPassable(feet) && isPassable(head) && isGround(below);
    }

    private static boolean isPassable(@Nullable BlockType t) {
        if (t == null || t == BlockType.EMPTY) {
            return true;
        }
        if (t.getMaterial() == BlockMaterial.Empty) {
            return true;
        }
        return t.isDoor();
    }

    private static boolean isGround(@Nullable BlockType t) {
        if (t == null || t == BlockType.EMPTY) {
            return false;
        }
        return t.getMaterial() == BlockMaterial.Solid;
    }

    private static long pack(int x, int z) {
        return ((long) x & 0xFFFFFFFFL) | (((long) z & 0xFFFFFFFFL) << 32);
    }

    private static int unpackX(long p) {
        return (int) (p & 0xFFFFFFFFL);
    }

    private static int unpackZ(long p) {
        return (int) ((p >>> 32) & 0xFFFFFFFFL);
    }
}
