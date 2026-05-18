package com.hexvane.aetherhaven.map;

import com.hypixel.hytale.protocol.packets.worldmap.MapImage;
import com.hypixel.hytale.server.core.universe.world.chunk.palette.BitFieldArr;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Unpacks and repacks palette-based {@link MapImage} tiles for per-player border overlays. */
public final class TownMapImagePixels {
    public static final int MAP_CHUNK_BLOCK_SIZE = 32;

    private TownMapImagePixels() {}

    public static boolean hasPixelData(@Nullable MapImage image) {
        if (image == null || image.width <= 0 || image.height <= 0) {
            return false;
        }
        int pixelCount = image.width * image.height;
        return pixelCount > 0
            && image.palette != null
            && image.palette.length > 0
            && image.packedIndices != null
            && image.packedIndices.length > 0
            && Byte.toUnsignedInt(image.bitsPerIndex) > 0;
    }

    @Nullable
    public static MapImage cloneImage(@Nullable MapImage source) {
        if (!hasPixelData(source)) {
            return null;
        }
        MapImage copy = new MapImage(source.width, source.height, null, (byte) 0, null);
        copy.palette = source.palette.clone();
        copy.bitsPerIndex = source.bitsPerIndex;
        copy.packedIndices = source.packedIndices.clone();
        return copy;
    }

    @Nullable
    public static int[] unpackToArgb(@Nullable MapImage image) {
        if (!hasPixelData(image)) {
            return null;
        }
        int bits = Byte.toUnsignedInt(image.bitsPerIndex);
        int pixelCount = image.width * image.height;
        int[] palette = image.palette;

        BitFieldArr indices = new BitFieldArr(bits, pixelCount);
        indices.set(image.packedIndices);

        int[] out = new int[pixelCount];
        for (int i = 0; i < pixelCount; i++) {
            int paletteIndex = indices.get(i);
            out[i] = (paletteIndex >= 0 && paletteIndex < palette.length) ? palette[paletteIndex] : 0;
        }
        return out;
    }

    public static void repackFromArgb(@Nonnull MapImage target, @Nonnull int[] pixels) {
        MapImage encoded = fromRawPixels(target.width, target.height, pixels);
        target.palette = encoded.palette;
        target.bitsPerIndex = encoded.bitsPerIndex;
        target.packedIndices = encoded.packedIndices;
    }

    @Nonnull
    public static MapImage fromRawPixels(int width, int height, @Nonnull int[] pixels) {
        int pixelCount = width * height;
        Map<Integer, Integer> colorToIndex = new LinkedHashMap<>();
        for (int i = 0; i < pixelCount; i++) {
            colorToIndex.computeIfAbsent(pixels[i], ignored -> colorToIndex.size());
        }

        int[] palette = new int[colorToIndex.size()];
        for (Map.Entry<Integer, Integer> entry : colorToIndex.entrySet()) {
            palette[entry.getValue()] = entry.getKey();
        }

        int bitsPerIndex = calculateBitsRequired(Math.max(1, palette.length));
        BitFieldArr indices = new BitFieldArr(bitsPerIndex, pixelCount);
        for (int i = 0; i < pixelCount; i++) {
            indices.set(i, colorToIndex.get(pixels[i]));
        }

        return new MapImage(width, height, palette, (byte) bitsPerIndex, indices.get());
    }

    private static int calculateBitsRequired(int colorCount) {
        if (colorCount <= 16) {
            return 4;
        }
        if (colorCount <= 256) {
            return 8;
        }
        if (colorCount <= 4096) {
            return 12;
        }
        return 16;
    }
}
