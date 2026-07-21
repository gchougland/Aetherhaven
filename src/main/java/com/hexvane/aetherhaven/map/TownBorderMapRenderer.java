package com.hexvane.aetherhaven.map;

import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.tourist.TownPortalTravelColor;
import com.hypixel.hytale.math.util.ChunkUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Paints axis-aligned town territory edges onto map tile pixels. */
public final class TownBorderMapRenderer {
    private static final float MIN_TOLERANCE = 1.5f;

    private TownBorderMapRenderer() {}

    /** Sparse border geometry for one map chunk: parallel pixel and town-index arrays. */
    public static final class BorderGeometry {
        public final int[] pixelIndices;
        public final int[] townIndices;

        BorderGeometry(int[] pixelIndices, int[] townIndices) {
            this.pixelIndices = pixelIndices;
            this.townIndices = townIndices;
        }

        public boolean isEmpty() {
            return pixelIndices.length == 0;
        }
    }

    /** All map chunk indices (long) that lie on any town's territory perimeter. */
    public static void collectPerimeterChunkIndices(@Nonnull List<TownRecord> towns, @Nonnull LongSet out) {
        for (TownRecord town : towns) {
            addPerimeterChunks(town, out);
        }
    }

    /**
     * Maps each perimeter chunk index to the list of town indices whose border passes through that chunk.
     */
    @Nonnull
    public static Long2ObjectMap<int[]> buildTownsByPerimeterChunk(@Nonnull List<TownRecord> towns) {
        Long2ObjectMap<IntArrayList> building = new Long2ObjectOpenHashMap<>();
        for (int townIndex = 0; townIndex < towns.size(); townIndex++) {
            LongOpenHashSet chunks = new LongOpenHashSet();
            addPerimeterChunks(towns.get(townIndex), chunks);
            for (long chunkIndex : chunks) {
                building.computeIfAbsent(chunkIndex, ignored -> new IntArrayList(2)).add(townIndex);
            }
        }
        Long2ObjectMap<int[]> result = new Long2ObjectOpenHashMap<>(building.size());
        for (Long2ObjectMap.Entry<IntArrayList> entry : building.long2ObjectEntrySet()) {
            result.put(entry.getLongKey(), entry.getValue().toIntArray());
        }
        return result;
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

    /**
     * Collects sparse border pixel positions for a map chunk using analytic edge rasterization.
     * When multiple towns overlap a pixel, the first town in {@code townIndices} wins (matches legacy behavior).
     */
    @Nonnull
    public static BorderGeometry collectBorderGeometry(
        int mapChunkX,
        int mapChunkZ,
        int width,
        int height,
        @Nonnull List<TownRecord> towns,
        @Nonnull int[] townIndices
    ) {
        if (townIndices.length == 0 || width <= 0 || height <= 0) {
            return new BorderGeometry(new int[0], new int[0]);
        }

        int chunkMinX = ChunkUtil.minBlock(mapChunkX);
        int chunkMinZ = ChunkUtil.minBlock(mapChunkZ);
        int chunkMaxX = ChunkUtil.maxBlock(mapChunkX);
        int chunkMaxZ = ChunkUtil.maxBlock(mapChunkZ);
        float scaleX = (float) width / TownMapImagePixels.MAP_CHUNK_BLOCK_SIZE;
        float scaleZ = (float) height / TownMapImagePixels.MAP_CHUNK_BLOCK_SIZE;
        float halfThickness = Math.max(MIN_TOLERANCE, 1.0f / Math.min(scaleX, scaleZ)) * 0.5f;

        boolean[] claimed = new boolean[width * height];
        int[] pixelOwner = new int[width * height];
        java.util.Arrays.fill(pixelOwner, -1);

        IntArrayList pixels = new IntArrayList();
        IntArrayList owners = new IntArrayList();

        for (int ti = 0; ti < townIndices.length; ti++) {
            int townIndex = townIndices[ti];
            if (townIndex < 0 || townIndex >= towns.size()) {
                continue;
            }
            rasterizeTownEdges(
                towns.get(townIndex),
                chunkMinX,
                chunkMinZ,
                chunkMaxX,
                chunkMaxZ,
                width,
                height,
                scaleX,
                scaleZ,
                halfThickness,
                townIndex,
                claimed,
                pixelOwner,
                pixels,
                owners);
        }

        return new BorderGeometry(pixels.toIntArray(), owners.toIntArray());
    }

    private static void rasterizeTownEdges(
        @Nonnull TownRecord town,
        int chunkMinX,
        int chunkMinZ,
        int chunkMaxX,
        int chunkMaxZ,
        int width,
        int height,
        float scaleX,
        float scaleZ,
        float halfThickness,
        int townIndex,
        @Nonnull boolean[] claimed,
        @Nonnull int[] pixelOwner,
        @Nonnull IntArrayList pixels,
        @Nonnull IntArrayList owners
    ) {
        int cx = ChunkUtil.chunkCoordinate(town.getCharterX());
        int cz = ChunkUtil.chunkCoordinate(town.getCharterZ());
        int r = town.getTerritoryChunkRadius();
        float west = ChunkUtil.minBlock(cx - r);
        float east = ChunkUtil.maxBlock(cx + r);
        float north = ChunkUtil.minBlock(cz - r);
        float south = ChunkUtil.maxBlock(cz + r);

        rasterizeHorizontalEdge(
            north, west, east, chunkMinX, chunkMinZ, chunkMaxX, chunkMaxZ,
            width, height, scaleX, scaleZ, halfThickness, townIndex, claimed, pixelOwner, pixels, owners);
        rasterizeHorizontalEdge(
            south, west, east, chunkMinX, chunkMinZ, chunkMaxX, chunkMaxZ,
            width, height, scaleX, scaleZ, halfThickness, townIndex, claimed, pixelOwner, pixels, owners);
        rasterizeVerticalEdge(
            west, north, south, chunkMinX, chunkMinZ, chunkMaxX, chunkMaxZ,
            width, height, scaleX, scaleZ, halfThickness, townIndex, claimed, pixelOwner, pixels, owners);
        rasterizeVerticalEdge(
            east, north, south, chunkMinX, chunkMinZ, chunkMaxX, chunkMaxZ,
            width, height, scaleX, scaleZ, halfThickness, townIndex, claimed, pixelOwner, pixels, owners);
    }

    private static void rasterizeHorizontalEdge(
        float worldZ,
        float edgeMinX,
        float edgeMaxX,
        int chunkMinX,
        int chunkMinZ,
        int chunkMaxX,
        int chunkMaxZ,
        int width,
        int height,
        float scaleX,
        float scaleZ,
        float halfThickness,
        int townIndex,
        @Nonnull boolean[] claimed,
        @Nonnull int[] pixelOwner,
        @Nonnull IntArrayList pixels,
        @Nonnull IntArrayList owners
    ) {
        if (worldZ < chunkMinZ - halfThickness || worldZ > chunkMaxZ + halfThickness) {
            return;
        }
        float clipMinX = Math.max(edgeMinX, chunkMinX);
        float clipMaxX = Math.min(edgeMaxX, chunkMaxX);
        if (clipMinX > clipMaxX) {
            return;
        }

        int pyMin = Math.max(0, (int) Math.floor((worldZ - halfThickness - chunkMinZ) * scaleZ));
        int pyMax = Math.min(height - 1, (int) Math.ceil((worldZ + halfThickness - chunkMinZ) * scaleZ));
        int pxMin = Math.max(0, (int) Math.floor((clipMinX - chunkMinX) * scaleX));
        int pxMax = Math.min(width - 1, (int) Math.ceil((clipMaxX - chunkMinX) * scaleX));

        for (int py = pyMin; py <= pyMax; py++) {
            for (int px = pxMin; px <= pxMax; px++) {
                claimPixel(px, py, width, townIndex, claimed, pixelOwner, pixels, owners);
            }
        }
    }

    private static void rasterizeVerticalEdge(
        float worldX,
        float edgeMinZ,
        float edgeMaxZ,
        int chunkMinX,
        int chunkMinZ,
        int chunkMaxX,
        int chunkMaxZ,
        int width,
        int height,
        float scaleX,
        float scaleZ,
        float halfThickness,
        int townIndex,
        @Nonnull boolean[] claimed,
        @Nonnull int[] pixelOwner,
        @Nonnull IntArrayList pixels,
        @Nonnull IntArrayList owners
    ) {
        if (worldX < chunkMinX - halfThickness || worldX > chunkMaxX + halfThickness) {
            return;
        }
        float clipMinZ = Math.max(edgeMinZ, chunkMinZ);
        float clipMaxZ = Math.min(edgeMaxZ, chunkMaxZ);
        if (clipMinZ > clipMaxZ) {
            return;
        }

        int pxMin = Math.max(0, (int) Math.floor((worldX - halfThickness - chunkMinX) * scaleX));
        int pxMax = Math.min(width - 1, (int) Math.ceil((worldX + halfThickness - chunkMinX) * scaleX));
        int pyMin = Math.max(0, (int) Math.floor((clipMinZ - chunkMinZ) * scaleZ));
        int pyMax = Math.min(height - 1, (int) Math.ceil((clipMaxZ - chunkMinZ) * scaleZ));

        for (int py = pyMin; py <= pyMax; py++) {
            for (int px = pxMin; px <= pxMax; px++) {
                claimPixel(px, py, width, townIndex, claimed, pixelOwner, pixels, owners);
            }
        }
    }

    private static void claimPixel(
        int px,
        int py,
        int width,
        int townIndex,
        @Nonnull boolean[] claimed,
        @Nonnull int[] pixelOwner,
        @Nonnull IntArrayList pixels,
        @Nonnull IntArrayList owners
    ) {
        int index = py * width + px;
        if (claimed[index]) {
            return;
        }
        claimed[index] = true;
        pixelOwner[index] = townIndex;
        pixels.add(index);
        owners.add(townIndex);
    }

    @Nonnull
    public static int[] colorsForGeometry(
        @Nonnull BorderGeometry geometry,
        @Nonnull List<TownRecord> towns,
        @Nullable UUID viewerTownId
    ) {
        int[] colors = new int[geometry.pixelIndices.length];
        for (int i = 0; i < geometry.pixelIndices.length; i++) {
            int townIndex = geometry.townIndices[i];
            colors[i] = townColor(towns.get(townIndex), viewerTownId);
        }
        return colors;
    }

    public static int townColor(@Nonnull TownRecord town, @Nullable UUID viewerTownId) {
        return TownPortalTravelColor.toOpaqueArgb(town);
    }
}
