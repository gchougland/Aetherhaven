package com.hexvane.aetherhaven.territory;

import com.hexvane.aetherhaven.map.TownMapImagePixels;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.worldmap.MapImage;
import com.hypixel.hytale.server.core.asset.common.CommonAsset;
import com.hypixel.hytale.server.core.asset.common.CommonAssetRegistry;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;

/** Registers per-player PNG thumbnails from world map tiles for the town expansion UI. */
public final class TownExpansionMapThumbnails {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int THUMB_PX = 40;
    private static final ConcurrentHashMap<String, Integer> REGISTERED_CONTENT_HASH = new ConcurrentHashMap<>();

    public enum CellOverlay {
        NONE,
        OWNED,
        OTHER_TOWN,
        CAN_CLAIM,
        SELECTED
    }

    private TownExpansionMapThumbnails() {}

    @Nonnull
    public static String assetPath(@Nonnull UUID viewerUuid, int anchorChunkX, int anchorChunkZ) {
        String viewerKey = viewerUuid.toString().replace("-", "");
        return "Icons/ItemsGenerated/Aetherhaven_ExpMap_"
            + viewerKey
            + "_"
            + anchorChunkX
            + "_"
            + anchorChunkZ
            + ".png";
    }

    /**
     * Registers a PNG for one 2×2 chunk block (four map tiles stitched into {@link #THUMB_PX}×{@link #THUMB_PX}).
     */
    @Nonnull
    public static ThumbnailPrep prepareBlockThumbnail(
        @Nonnull String packId,
        @Nonnull UUID viewerUuid,
        int anchorChunkX,
        int anchorChunkZ,
        @Nullable MapImage northWest,
        @Nullable MapImage northEast,
        @Nullable MapImage southWest,
        @Nullable MapImage southEast,
        @Nonnull CellOverlay overlay,
        @Nonnull String overlayHex
    ) {
        byte[] png = renderBlockPng(northWest, northEast, southWest, southEast, overlay, overlayHex);
        String assetName = assetPath(viewerUuid, anchorChunkX, anchorChunkZ);
        if (png == null) {
            return new ThumbnailPrep(assetName, null);
        }
        int contentHash = java.util.Arrays.hashCode(png);
        String cacheKey = packId + "|" + assetName;
        Integer prev = REGISTERED_CONTENT_HASH.get(cacheKey);
        CommonAsset asset = new BytesCommonAsset(assetName, png);
        CommonAsset push = null;
        if (prev == null || prev != contentHash) {
            CommonAssetRegistry.addCommonAsset(packId, asset);
            REGISTERED_CONTENT_HASH.put(cacheKey, contentHash);
            push = asset;
        }
        return new ThumbnailPrep(assetName, push);
    }

    @Nonnull
    public static ThumbnailPrep prepareThumbnail(
        @Nonnull String packId,
        @Nonnull UUID viewerUuid,
        int chunkX,
        int chunkZ,
        @Nullable MapImage mapImage,
        @Nonnull CellOverlay overlay,
        @Nonnull String overlayHex
    ) {
        return prepareBlockThumbnail(
            packId, viewerUuid, chunkX, chunkZ, mapImage, mapImage, mapImage, mapImage, overlay, overlayHex
        );
    }

    public record ThumbnailPrep(@Nonnull String assetPath, @Nullable CommonAsset pushToClient) {}

    @Nullable
    private static byte[] renderBlockPng(
        @Nullable MapImage northWest,
        @Nullable MapImage northEast,
        @Nullable MapImage southWest,
        @Nullable MapImage southEast,
        @Nonnull CellOverlay overlay,
        @Nonnull String overlayHex
    ) {
        BufferedImage img = renderBlockImage(northWest, northEast, southWest, southEast, overlay, overlayHex);
        if (img == null) {
            return null;
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (!ImageIO.write(img, "png", out)) {
                return null;
            }
            return out.toByteArray();
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to encode expansion map thumbnail");
            return null;
        }
    }

    @Nullable
    private static byte[] renderPng(@Nullable MapImage mapImage, @Nonnull CellOverlay overlay, @Nonnull String overlayHex) {
        return renderBlockPng(mapImage, mapImage, mapImage, mapImage, overlay, overlayHex);
    }

    @Nullable
    private static BufferedImage renderBlockImage(
        @Nullable MapImage northWest,
        @Nullable MapImage northEast,
        @Nullable MapImage southWest,
        @Nullable MapImage southEast,
        @Nonnull CellOverlay overlay,
        @Nonnull String overlayHex
    ) {
        int half = THUMB_PX / 2;
        BufferedImage base = new BufferedImage(THUMB_PX, THUMB_PX, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gBase = base.createGraphics();
        gBase.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        stampQuadrant(gBase, northWest, 0, 0, half);
        stampQuadrant(gBase, northEast, half, 0, half);
        stampQuadrant(gBase, southWest, 0, half, half);
        stampQuadrant(gBase, southEast, half, half, half);
        gBase.dispose();
        fillMissingQuadrants(base, half);
        return applyOverlay(base, overlay, overlayHex);
    }

    private static void fillMissingQuadrants(@Nonnull BufferedImage base, int half) {
        java.awt.Color fill = new java.awt.Color(0x3a, 0x45, 0x55);
        Graphics2D g = base.createGraphics();
        for (int q = 0; q < 4; q++) {
            int ox = (q % 2) * half;
            int oy = (q / 2) * half;
            boolean empty = true;
            for (int y = 0; y < half && empty; y++) {
                for (int x = 0; x < half; x++) {
                    if ((base.getRGB(ox + x, oy + y) >>> 24) != 0) {
                        empty = false;
                        break;
                    }
                }
            }
            if (empty) {
                g.setColor(fill);
                g.fillRect(ox, oy, half, half);
            }
        }
        g.dispose();
    }

    private static void stampQuadrant(
        @Nonnull Graphics2D g,
        @Nullable MapImage mapImage,
        int destX,
        int destY,
        int destSize
    ) {
        BufferedImage part = mapImageToBuffered(mapImage);
        if (part == null) {
            return;
        }
        g.drawImage(part, destX, destY, destSize, destSize, null);
    }

    @Nullable
    private static BufferedImage renderImage(@Nullable MapImage mapImage, @Nonnull CellOverlay overlay, @Nonnull String overlayHex) {
        BufferedImage base = mapImageToBuffered(mapImage);
        if (base == null) {
            base = new BufferedImage(THUMB_PX, THUMB_PX, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = base.createGraphics();
            g.setColor(new java.awt.Color(0x3a, 0x45, 0x55));
            g.fillRect(0, 0, THUMB_PX, THUMB_PX);
            g.dispose();
        } else if (base.getWidth() != THUMB_PX || base.getHeight() != THUMB_PX) {
            BufferedImage scaled = new BufferedImage(THUMB_PX, THUMB_PX, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(base, 0, 0, THUMB_PX, THUMB_PX, null);
            g.dispose();
            base = scaled;
        }
        return applyOverlay(base, overlay, overlayHex);
    }

    @Nullable
    private static BufferedImage applyOverlay(
        @Nonnull BufferedImage base,
        @Nonnull CellOverlay overlay,
        @Nonnull String overlayHex
    ) {
        if (overlay == CellOverlay.NONE) {
            return base;
        }
        float alpha =
            switch (overlay) {
                case OWNED -> 0.42f;
                case OTHER_TOWN -> 0.55f;
                case CAN_CLAIM -> 0.22f;
                case SELECTED -> 0.18f;
                case NONE -> 0f;
            };
        int rgb = parseHexRgb(overlayHex);
        int r = (rgb >> 16) & 0xFF;
        int gr = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        if (overlay == CellOverlay.SELECTED) {
            r = 255;
            gr = 255;
            b = 255;
            alpha = 0.35f;
        }
        BufferedImage out = new BufferedImage(THUMB_PX, THUMB_PX, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(base, 0, 0, null);
        g.setColor(new java.awt.Color(r, gr, b, (int) (255 * alpha)));
        g.fillRect(0, 0, THUMB_PX, THUMB_PX);
        if (overlay == CellOverlay.CAN_CLAIM || overlay == CellOverlay.SELECTED) {
            drawInsetBorder(g, THUMB_PX, overlay == CellOverlay.SELECTED ? new java.awt.Color(255, 255, 255, 240) : new java.awt.Color(0x9a, 0xd4, 0x5d, 220));
        }
        g.dispose();
        return out;
    }

    private static void drawInsetBorder(@Nonnull Graphics2D g, int size, @Nonnull java.awt.Color color) {
        g.setStroke(new java.awt.BasicStroke(2f));
        g.setColor(color);
        g.drawRect(1, 1, size - 3, size - 3);
        g.drawRect(2, 2, size - 5, size - 5);
    }

    @Nullable
    private static BufferedImage mapImageToBuffered(@Nullable MapImage mapImage) {
        if (!TownMapImagePixels.hasPixelData(mapImage)) {
            return null;
        }
        int[] pixels = TownMapImagePixels.unpackToArgb(mapImage);
        if (pixels == null || mapImage.width <= 0 || mapImage.height <= 0) {
            return null;
        }
        BufferedImage raw = new BufferedImage(mapImage.width, mapImage.height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < mapImage.height; y++) {
            for (int x = 0; x < mapImage.width; x++) {
                int i = y * mapImage.width + x;
                raw.setRGB(x, y, mapPixelToJavaArgb(pixels[i]));
            }
        }
        BufferedImage scaled = new BufferedImage(THUMB_PX, THUMB_PX, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(raw, 0, 0, THUMB_PX, THUMB_PX, null);
        g.dispose();
        return scaled;
    }

    private static int mapPixelToJavaArgb(int mapPx) {
        int a = mapPx & 0xFF;
        int b = (mapPx >> 8) & 0xFF;
        int gr = (mapPx >> 16) & 0xFF;
        int r = (mapPx >> 24) & 0xFF;
        if (a == 0) {
            a = 255;
        }
        return (a << 24) | (r << 16) | (gr << 8) | b;
    }

    private static int parseHexRgb(@Nonnull String hex) {
        String s = hex.startsWith("#") ? hex.substring(1) : hex;
        if (s.length() < 6) {
            return 0x888888;
        }
        return Integer.parseInt(s.substring(0, 6), 16);
    }

    private static final class BytesCommonAsset extends CommonAsset {
        private final byte[] bytes;

        private BytesCommonAsset(@Nonnull String name, @Nonnull byte[] bytes) {
            super(name, bytes);
            this.bytes = bytes;
        }

        @Nonnull
        @Override
        protected CompletableFuture<byte[]> getBlob0() {
            return CompletableFuture.completedFuture(bytes);
        }
    }
}
