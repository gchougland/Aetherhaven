package com.hexvane.aetherhaven.map;

import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.math.util.ChunkUtil;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Paints axis-aligned town territory edges onto map tile pixels. */
public final class TownBorderMapRenderer {
    /** Hytale map pixels use ARGB: R in bits 24-31, G 16-23, B 8-15, A 0-7. */
    private static final int OWN_TOWN_COLOR = (0xD4 << 24) | (0xAF << 16) | (0x37 << 8) | 0xFF;

    private static final float MIN_TOLERANCE = 1.5f;

    private TownBorderMapRenderer() {}

    /** All map chunk indices (long) that lie on any town's territory perimeter. */
    public static void collectPerimeterChunkIndices(@Nonnull List<TownRecord> towns, @Nonnull LongSet out) {
        for (TownRecord town : towns) {
            addPerimeterChunks(town, out);
        }
    }

    private static void addPerimeterChunks(@Nonnull TownRecord town, @Nonnull LongSet out) {
        int cx = ChunkUtil.chunkCoordinate(town.getCharterX());
        int cz = ChunkUtil.chunkCoordinate(town.getCharterZ());
        int r = town.getTerritoryChunkRadius();
        int minX = cx - r;
        int maxX = cx + r;
        int minZ = cz - r;
        int maxZ = cz + r;
        for (int x = minX; x <= maxX; x++) {
            out.add(ChunkUtil.indexChunk(x, minZ));
            out.add(ChunkUtil.indexChunk(x, maxZ));
        }
        for (int z = minZ + 1; z < maxZ; z++) {
            out.add(ChunkUtil.indexChunk(minX, z));
            out.add(ChunkUtil.indexChunk(maxX, z));
        }
    }

    public static boolean mapChunkIntersectsAnyTownBorder(int mapChunkX, int mapChunkZ, @Nonnull List<TownRecord> towns) {
        for (TownRecord town : towns) {
            if (mapChunkOnTownPerimeter(mapChunkX, mapChunkZ, town)) {
                return true;
            }
        }
        return false;
    }

    private static boolean mapChunkOnTownPerimeter(int mapChunkX, int mapChunkZ, @Nonnull TownRecord town) {
        int cx = ChunkUtil.chunkCoordinate(town.getCharterX());
        int cz = ChunkUtil.chunkCoordinate(town.getCharterZ());
        int r = town.getTerritoryChunkRadius();
        if (mapChunkX < cx - r || mapChunkX > cx + r || mapChunkZ < cz - r || mapChunkZ > cz + r) {
            return false;
        }
        return mapChunkX == cx - r || mapChunkX == cx + r || mapChunkZ == cz - r || mapChunkZ == cz + r;
    }

    public static void paintTownBorders(
        @Nonnull int[] pixels,
        int width,
        int height,
        int mapChunkX,
        int mapChunkZ,
        @Nonnull List<TownRecord> towns,
        @Nullable UUID viewerTownId
    ) {
        int chunkMinX = ChunkUtil.minBlock(mapChunkX);
        int chunkMinZ = ChunkUtil.minBlock(mapChunkZ);
        float scaleX = (float) width / TownMapImagePixels.MAP_CHUNK_BLOCK_SIZE;
        float scaleZ = (float) height / TownMapImagePixels.MAP_CHUNK_BLOCK_SIZE;
        float tolerance = Math.max(MIN_TOLERANCE, 1.0f / Math.min(scaleX, scaleZ));

        for (int py = 0; py < height; py++) {
            for (int px = 0; px < width; px++) {
                float worldX = chunkMinX + (px / scaleX);
                float worldZ = chunkMinZ + (py / scaleZ);
                int color = borderColorAt(worldX, worldZ, towns, viewerTownId, tolerance);
                if (color != 0) {
                    pixels[py * width + px] = color;
                }
            }
        }
    }

    private static int borderColorAt(
        float worldX,
        float worldZ,
        @Nonnull List<TownRecord> towns,
        @Nullable UUID viewerTownId,
        float tolerance
    ) {
        int best = 0;
        for (TownRecord town : towns) {
            if (!isOnTownBorder(worldX, worldZ, town, tolerance)) {
                continue;
            }
            int color = townColor(town, viewerTownId);
            if (best == 0) {
                best = color;
            }
        }
        return best;
    }

    private static boolean isOnTownBorder(float worldX, float worldZ, @Nonnull TownRecord town, float tolerance) {
        int cx = ChunkUtil.chunkCoordinate(town.getCharterX());
        int cz = ChunkUtil.chunkCoordinate(town.getCharterZ());
        int r = town.getTerritoryChunkRadius();
        int west = ChunkUtil.minBlock(cx - r);
        int east = ChunkUtil.maxBlock(cx + r);
        int north = ChunkUtil.minBlock(cz - r);
        int south = ChunkUtil.maxBlock(cz + r);

        if (worldX < west - tolerance || worldX > east + tolerance || worldZ < north - tolerance || worldZ > south + tolerance) {
            return false;
        }

        boolean onWest = Math.abs(worldX - west) <= tolerance;
        boolean onEast = Math.abs(worldX - east) <= tolerance;
        boolean onNorth = Math.abs(worldZ - north) <= tolerance;
        boolean onSouth = Math.abs(worldZ - south) <= tolerance;

        if (onWest && worldZ >= north - tolerance && worldZ <= south + tolerance) {
            return true;
        }
        if (onEast && worldZ >= north - tolerance && worldZ <= south + tolerance) {
            return true;
        }
        if (onNorth && worldX >= west - tolerance && worldX <= east + tolerance) {
            return true;
        }
        return onSouth && worldX >= west - tolerance && worldX <= east + tolerance;
    }

    private static int townColor(@Nonnull TownRecord town, @Nullable UUID viewerTownId) {
        if (viewerTownId != null && viewerTownId.equals(town.getTownId())) {
            return OWN_TOWN_COLOR;
        }
        int hash = town.getTownId().hashCode();
        int r = 0x60 + (hash & 0x7F);
        int g = 0x90 + ((hash >> 8) & 0x5F);
        int b = 0xC0 + ((hash >> 16) & 0x3F);
        return (r << 24) | (g << 16) | (b << 8) | 0xFF;
    }
}
