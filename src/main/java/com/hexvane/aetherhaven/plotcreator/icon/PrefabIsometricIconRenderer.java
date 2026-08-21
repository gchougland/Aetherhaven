package com.hexvane.aetherhaven.plotcreator.icon;

import com.hexvane.aetherhaven.placement.FrontFacing;
import com.hexvane.aetherhaven.wall.WallCardinal;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/** Renders a low-fidelity isometric thumbnail from a relativized {@link BlockSelection}. */
public final class PrefabIsometricIconRenderer {
    public static final int ICON_SIZE = 64;
    private static final float CANVAS_FILL = 0.92f;
    private PrefabIsometricIconRenderer() {}

    @Nullable
    public static BufferedImage render(@Nonnull BlockSelection prefab) {
        return render(prefab, FrontFacing.NORTH);
    }

    @Nullable
    public static BufferedImage render(@Nonnull BlockSelection prefab, @Nullable String frontFacing) {
        List<BlockCell> collected = new ArrayList<>();
        prefab.forEachBlock((x, y, z, holder) -> {
            int blockId = holder.blockId();
            if (!BlockColorResolver.isRenderable(blockId)) {
                return;
            }
            BlockColorResolver.FaceColors faces = BlockColorResolver.resolveFaceColors(blockId);
            collected.add(new BlockCell(x, y, z, faces));
        });
        prefab.forEachFluid((x, y, z, fluidId, level) -> {
            if (!BlockColorResolver.isRenderableFluid(fluidId)) {
                return;
            }
            if (prefab.hasBlockAtWorldPos(x, y, z)) {
                int blockId = prefab.getBlockAtWorldPos(x, y, z);
                if (BlockColorResolver.isRenderable(blockId)) {
                    return;
                }
            }
            collected.add(new BlockCell(x, y, z, BlockColorResolver.resolveFluidFaceColors(fluidId)));
        });
        if (collected.isEmpty()) {
            return null;
        }

        List<BlockCell> cells = collected;
        int alignSteps = FrontFacing.iconAlignStepsToNorth(frontFacing);
        if (alignSteps != 0) {
            List<BlockCell> rotated = new ArrayList<>(collected.size());
            for (BlockCell cell : collected) {
                Vector3i v = WallCardinal.rotateOffset(new Vector3i(cell.x, cell.y, cell.z), alignSteps);
                rotated.add(new BlockCell(v.x, v.y, v.z, cell.faces()));
            }
            cells = rotated;
        }

        int maxCanvas = Math.round(ICON_SIZE * CANVAS_FILL);
        // Always project with uniform world-unit spacing, then downscale. The old direct-render path
        // compressed vertical spacing (groundSlope=1 vs tileHalfW horizontal) and squished some prefabs.
        int tileHalfW = 5;
        TileGrid uniformGrid = buildUniformProjectionGrid(tileHalfW, 0, 0);
        ScreenBounds uniformBounds = measureBounds(cells, uniformGrid);
        int scratchPad = 1;
        int scratchWidth = inclusiveSpan(uniformBounds.minX(), uniformBounds.maxX()) + scratchPad * 2;
        int scratchHeight = inclusiveSpan(uniformBounds.minY(), uniformBounds.maxY()) + scratchPad * 2;
        TileGrid scratchGrid = buildUniformProjectionGrid(
            tileHalfW,
            scratchPad - uniformBounds.minX(),
            scratchPad - uniformBounds.minY()
        );
        List<Voxel> scratchVoxels = buildSortedVoxels(cells, scratchGrid);

        BufferedImage scratch = new BufferedImage(scratchWidth, scratchHeight, BufferedImage.TYPE_INT_ARGB);
        int[] scratchPixels = new int[scratchWidth * scratchHeight];
        paintVoxels(scratchPixels, scratchWidth, scratchVoxels, scratchGrid);
        scratch.setRGB(0, 0, scratchWidth, scratchHeight, scratchPixels, 0, scratchWidth);

        return downscaleToIcon(scratch, scratchWidth, scratchHeight, maxCanvas);
    }

    private static int inclusiveSpan(int min, int max) {
        return max - min + 1;
    }

    @Nonnull
    private static List<Voxel> buildSortedVoxels(@Nonnull List<BlockCell> cells, @Nonnull TileGrid grid) {
        List<Voxel> voxels = new ArrayList<>(cells.size());
        for (BlockCell cell : cells) {
            int cx = projectScreenX(cell.x, cell.z, grid);
            int cy = projectScreenY(cell.x, cell.y, cell.z, grid);
            voxels.add(new Voxel(cell.x, cell.y, cell.z, cx, cy, viewFaces(cell.faces())));
        }

        // One Y level at a time (bottom to top); within each level, back-to-front by x+z diagonal.
        voxels.sort(
            Comparator.<Voxel>comparingInt(Voxel::y)
                .thenComparingInt(v -> v.x + v.z)
                .thenComparingInt(Voxel::x)
                .thenComparingInt(Voxel::z)
        );
        return voxels;
    }

    private static int projectScreenX(int x, int z, @Nonnull TileGrid grid) {
        return projectScreenX(x, z, grid.tileHalfW(), grid.originX());
    }

    private static int projectScreenY(int x, int y, int z, @Nonnull TileGrid grid) {
        return projectScreenY(x, y, z, grid.groundSlope(), grid.vertStep(), grid.originY());
    }

    /**
     * Prefab-local axes are read as-is from the relativized buffer. {@code (z - x, x + z)} places the building front
     * toward the bottom-right of the icon (door down-right), matching in-game plot facing.
     */
    private static int projectScreenX(int x, int z, int tileHalfW, int originX) {
        return originX + (z - x) * tileHalfW;
    }

    private static int projectScreenY(int x, int y, int z, int groundSlope, int vertStep, int originY) {
        return originY + (x + z) * groundSlope - y * vertStep;
    }

    @Nonnull
    private static BlockColorResolver.FaceColors viewFaces(@Nonnull BlockColorResolver.FaceColors faces) {
        return new BlockColorResolver.FaceColors(faces.top(), faces.right(), faces.left());
    }

    private static void paintVoxels(
        @Nonnull int[] pixels,
        int bufferWidth,
        @Nonnull List<Voxel> voxels,
        @Nonnull TileGrid grid
    ) {
        // Per Y level: all sides then all tops. A global sides-then-tops pass lets lower-layer
        // tops paint over upper-layer sides at the same (x, z), which looks like unwrapped crosses.
        int levelY = Integer.MIN_VALUE;
        List<Voxel> level = new ArrayList<>();
        for (Voxel voxel : voxels) {
            if (voxel.y() != levelY && !level.isEmpty()) {
                paintLevel(pixels, bufferWidth, level, grid);
                level.clear();
            }
            levelY = voxel.y();
            level.add(voxel);
        }
        if (!level.isEmpty()) {
            paintLevel(pixels, bufferWidth, level, grid);
        }
    }

    @Nonnull
    private static BufferedImage downscaleToIcon(
        @Nonnull BufferedImage scratch,
        int scratchWidth,
        int scratchHeight,
        int maxCanvas
    ) {
        // One scale factor for both axes so aspect ratio is preserved (no horizontal/vertical stretch).
        double scale = Math.min((double) maxCanvas / scratchWidth, (double) maxCanvas / scratchHeight);
        int drawWidth = Math.max(1, (int) Math.round(scratchWidth * scale));
        int drawHeight = Math.max(1, (int) Math.round(scratchHeight * scale));
        int drawX = (ICON_SIZE - drawWidth) / 2;
        int drawY = (ICON_SIZE - drawHeight) / 2;

        BufferedImage image = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.drawImage(
                scratch,
                drawX,
                drawY,
                drawX + drawWidth,
                drawY + drawHeight,
                0,
                0,
                scratchWidth,
                scratchHeight,
                null
            );
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static void paintLevel(
        @Nonnull int[] pixels,
        int bufferWidth,
        @Nonnull List<Voxel> level,
        @Nonnull TileGrid grid
    ) {
        for (Voxel voxel : level) {
            paintIsometricSides(
                pixels,
                bufferWidth,
                voxel.cx(),
                voxel.cy(),
                grid.renderHalfW(),
                grid.renderHalfH(),
                grid.renderSideDepth(),
                voxel.faces()
            );
        }
        for (Voxel voxel : level) {
            paintIsometricTop(
                pixels,
                bufferWidth,
                voxel.cx(),
                voxel.cy(),
                grid.renderHalfW(),
                grid.renderHalfH(),
                voxel.faces().top()
            );
        }
    }

    /** Maps x, y, and z with the same world-unit step so height is not compressed before scaling. */
    @Nonnull
    private static TileGrid buildUniformProjectionGrid(int tileHalfW, int originX, int originY) {
        int tileHalfH = Math.max(1, tileHalfW / 2);
        int unit = tileHalfW;
        int groundSlope = Math.max(1, unit / 2);
        int vertStep = unit;
        int sideDepth = unit;
        int renderHalfW = tileHalfW + (tileHalfW >= 4 ? 1 : 0);
        int renderHalfH = tileHalfH + (tileHalfH >= 2 ? 1 : 0);
        int renderSideDepth = sideDepth + 1;
        return new TileGrid(
            tileHalfW,
            tileHalfH,
            groundSlope,
            vertStep,
            sideDepth,
            renderHalfW,
            renderHalfH,
            renderSideDepth,
            originX,
            originY
        );
    }

    @Nonnull
    private static ScreenBounds measureBounds(@Nonnull List<BlockCell> cells, @Nonnull TileGrid grid) {
        return measureBounds(
            cells,
            grid.renderHalfW(),
            grid.renderHalfH(),
            grid.tileHalfW(),
            grid.groundSlope(),
            grid.vertStep(),
            grid.renderSideDepth(),
            grid.originX(),
            grid.originY()
        );
    }

    @Nonnull
    private static ScreenBounds measureBounds(
        @Nonnull List<BlockCell> cells,
        int renderHalfW,
        int renderHalfH,
        int tileHalfW,
        int groundSlope,
        int vertStep,
        int renderSideDepth,
        int originX,
        int originY
    ) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (BlockCell cell : cells) {
            int cx = projectScreenX(cell.x, cell.z, tileHalfW, originX);
            int cy = projectScreenY(cell.x, cell.y, cell.z, groundSlope, vertStep, originY);
            minX = Math.min(minX, cx - renderHalfW);
            maxX = Math.max(maxX, cx + renderHalfW);
            minY = Math.min(minY, cy - renderHalfH);
            maxY = Math.max(maxY, cy + renderHalfH + renderSideDepth);
        }
        return new ScreenBounds(minX, minY, maxX, maxY);
    }

    /**
     * Left and right faces are parallelograms dropped from the west/east corners of the top
     * diamond down to the south edge — not an unwrapped cross meeting under the center.
     */
    private static void paintIsometricSides(
        @Nonnull int[] pixels,
        int bufferWidth,
        int cx,
        int cy,
        int halfW,
        int halfH,
        int sideDepth,
        @Nonnull BlockColorResolver.FaceColors faces
    ) {
        int westX = cx - halfW;
        int eastX = cx + halfW;
        int southY = cy + halfH;
        int southBaseY = southY + sideDepth;

        fillQuad(pixels, bufferWidth, westX, cy, cx, southY, cx, southBaseY, westX, cy + sideDepth, faces.left());
        fillQuad(pixels, bufferWidth, eastX, cy, cx, southY, cx, southBaseY, eastX, cy + sideDepth, faces.right());
    }

    private static void paintIsometricTop(
        @Nonnull int[] pixels,
        int bufferWidth,
        int cx,
        int cy,
        int halfW,
        int halfH,
        int argb
    ) {
        fillDiamond(pixels, bufferWidth, cx, cy, halfW, halfH, argb);
    }

    private static void fillDiamond(@Nonnull int[] pixels, int bufferWidth, int cx, int cy, int halfW, int halfH, int argb) {
        for (int row = -halfH; row <= halfH; row++) {
            int width = halfW * (halfH - Math.abs(row)) / Math.max(halfH, 1);
            for (int col = -width; col <= width; col++) {
                setPixel(pixels, bufferWidth, cx + col, cy + row, argb);
            }
        }
    }

    private static void fillQuad(
        @Nonnull int[] pixels,
        int bufferWidth,
        int x0,
        int y0,
        int x1,
        int y1,
        int x2,
        int y2,
        int x3,
        int y3,
        int argb
    ) {
        int minY = min4(y0, y1, y2, y3);
        int maxY = max4(y0, y1, y2, y3);
        int minX = min4(x0, x1, x2, x3);
        int maxX = max4(x0, x1, x2, x3);
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if (pointInQuad(x, y, x0, y0, x1, y1, x2, y2, x3, y3)) {
                    setPixel(pixels, bufferWidth, x, y, argb);
                }
            }
        }
    }

    private static boolean pointInQuad(
        int px,
        int py,
        int x0,
        int y0,
        int x1,
        int y1,
        int x2,
        int y2,
        int x3,
        int y3
    ) {
        return sameSide(px, py, x0, y0, x1, y1, x2, y2)
            && sameSide(px, py, x1, y1, x2, y2, x3, y3)
            && sameSide(px, py, x2, y2, x3, y3, x0, y0)
            && sameSide(px, py, x3, y3, x0, y0, x1, y1);
    }

    private static boolean sameSide(int px, int py, int ax, int ay, int bx, int by, int cx, int cy) {
        long cross1 = (long) (bx - ax) * (py - ay) - (long) (by - ay) * (px - ax);
        long cross2 = (long) (bx - ax) * (cy - ay) - (long) (by - ay) * (cx - ax);
        return cross1 >= 0 == cross2 >= 0;
    }

    private static void setPixel(@Nonnull int[] pixels, int bufferWidth, int x, int y, int argb) {
        if (x < 0 || y < 0 || x >= bufferWidth) {
            return;
        }
        int index = y * bufferWidth + x;
        if (index < 0 || index >= pixels.length) {
            return;
        }
        pixels[index] = argb;
    }

    private static int min4(int a, int b, int c, int d) {
        return Math.min(Math.min(a, b), Math.min(c, d));
    }

    private static int max4(int a, int b, int c, int d) {
        return Math.max(Math.max(a, b), Math.max(c, d));
    }

    private record BlockCell(int x, int y, int z, BlockColorResolver.FaceColors faces) {}

    private record ScreenBounds(int minX, int minY, int maxX, int maxY) {}

    private record TileGrid(
        int tileHalfW,
        int tileHalfH,
        int groundSlope,
        int vertStep,
        int sideDepth,
        int renderHalfW,
        int renderHalfH,
        int renderSideDepth,
        int originX,
        int originY
    ) {}

    private record Voxel(int x, int y, int z, int cx, int cy, BlockColorResolver.FaceColors faces) {}
}
