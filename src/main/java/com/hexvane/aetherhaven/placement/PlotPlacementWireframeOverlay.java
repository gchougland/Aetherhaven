package com.hexvane.aetherhaven.placement;

import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.math.matrix.Matrix4d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.DebugShape;
import com.hypixel.hytale.protocol.packets.player.ClearDebugShapes;
import com.hypixel.hytale.protocol.packets.player.DisplayDebug;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Axis-aligned box edges as thin debug cylinders (same math as {@link DebugUtils#addLine}). Uses
 * {@link DebugUtils#FLAG_NO_WIREFRAME} so only the tinted solid draws — default debug also draws black wireframe edges
 * that dominate the fill at a distance.
 */
public final class PlotPlacementWireframeOverlay {
    /**
     * {@link DisplayDebug#time} lifetime (seconds in stock tooling). Keep high: the UI calls {@link #clearFor} on
     * close, and the path tool uses a long hold to avoid visible expiry between refreshes.
     */
    private static final float OUTLINE_DISPLAY_SECONDS = 6f * 60f * 60f;

    /**
     * Each footprint uses 12 line cylinders. The client appears to cap how many debug shapes apply per burst; we draw
     * the active placement outline first, then at most this many existing plots (nearest AABB centers first).
     */
    private static final int MAX_SIBLING_PLOT_OUTLINES = 24;

    /** Match vanilla debug lines (thin cylinders). */
    private static final double LINE_THICKNESS = 0.04;

    /** Solid fill only; hides the black wireframe hull on {@link DebugShape#Cylinder}. */
    private static final int LINE_FLAGS = DebugUtils.FLAG_NO_WIREFRAME;

    private PlotPlacementWireframeOverlay() {}

    public static void clearFor(@Nullable PlayerRef player) {
        if (player == null) {
            return;
        }
        player.getPacketHandler().write(new ClearDebugShapes());
    }

    public static void send(
        @Nonnull PlayerRef player,
        @Nonnull PlotFootprintRecord placementFootprint,
        boolean placementValid,
        @Nullable TownRecord town
    ) {
        clearFor(player);
        Vector3f placeColor = placementValid ? DebugUtils.COLOR_WHITE : DebugUtils.COLOR_RED;
        addBoxEdges(player, placementFootprint, placeColor);
        if (town == null) {
            return;
        }
        List<PlotInstance> plots = new ArrayList<>(town.getPlotInstances());
        if (plots.size() > MAX_SIBLING_PLOT_OUTLINES) {
            plots.sort(Comparator.comparingDouble(p -> aabbCenterDistSq(p.toFootprint(), placementFootprint)));
            plots = plots.subList(0, MAX_SIBLING_PLOT_OUTLINES);
        }
        for (PlotInstance p : plots) {
            addBoxEdges(player, p.toFootprint(), DebugUtils.COLOR_SILVER);
        }
    }

    private static double aabbCenterDistSq(@Nonnull PlotFootprintRecord a, @Nonnull PlotFootprintRecord b) {
        double ax = (a.getMinX() + a.getMaxX() + 1) * 0.5;
        double ay = (a.getMinY() + a.getMaxY() + 1) * 0.5;
        double az = (a.getMinZ() + a.getMaxZ() + 1) * 0.5;
        double bx = (b.getMinX() + b.getMaxX() + 1) * 0.5;
        double by = (b.getMinY() + b.getMaxY() + 1) * 0.5;
        double bz = (b.getMinZ() + b.getMaxZ() + 1) * 0.5;
        double dx = ax - bx;
        double dy = ay - by;
        double dz = az - bz;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Twelve edges of the integer footprint AABB: blocks {@code [min, max]} → world extents
     * {@code [min, max+1)}.
     */
    private static void addBoxEdges(@Nonnull PlayerRef player, @Nonnull PlotFootprintRecord fp, @Nonnull Vector3f color) {
        double minX = fp.getMinX();
        double minY = fp.getMinY();
        double minZ = fp.getMinZ();
        double maxX = fp.getMaxX() + 1.0;
        double maxY = fp.getMaxY() + 1.0;
        double maxZ = fp.getMaxZ() + 1.0;

        // Bottom (y = minY)
        sendLineCylinder(player, minX, minY, minZ, maxX, minY, minZ, color);
        sendLineCylinder(player, maxX, minY, minZ, maxX, minY, maxZ, color);
        sendLineCylinder(player, maxX, minY, maxZ, minX, minY, maxZ, color);
        sendLineCylinder(player, minX, minY, maxZ, minX, minY, minZ, color);
        // Top (y = maxY)
        sendLineCylinder(player, minX, maxY, minZ, maxX, maxY, minZ, color);
        sendLineCylinder(player, maxX, maxY, minZ, maxX, maxY, maxZ, color);
        sendLineCylinder(player, maxX, maxY, maxZ, minX, maxY, maxZ, color);
        sendLineCylinder(player, minX, maxY, maxZ, minX, maxY, minZ, color);
        // Verticals
        sendLineCylinder(player, minX, minY, minZ, minX, maxY, minZ, color);
        sendLineCylinder(player, maxX, minY, minZ, maxX, maxY, minZ, color);
        sendLineCylinder(player, maxX, minY, maxZ, maxX, maxY, maxZ, color);
        sendLineCylinder(player, minX, minY, maxZ, minX, maxY, maxZ, color);
    }

    /** Same transform as {@link DebugUtils#addLine} (cylinder along segment). */
    private static void sendLineCylinder(
        @Nonnull PlayerRef player,
        double startX,
        double startY,
        double startZ,
        double endX,
        double endY,
        double endZ,
        @Nonnull Vector3f color
    ) {
        double dirX = endX - startX;
        double dirY = endY - startY;
        double dirZ = endZ - startZ;
        double length = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (length < 0.001) {
            return;
        }
        Matrix4d tmp = new Matrix4d();
        Matrix4d matrix = new Matrix4d();
        matrix.identity();
        matrix.translate(startX, startY, startZ);
        double angleY = Math.atan2(dirZ, dirX);
        matrix.rotateAxis(angleY + (Math.PI / 2), 0.0, 1.0, 0.0, tmp);
        double angleX = Math.atan2(Math.sqrt(dirX * dirX + dirZ * dirZ), dirY);
        matrix.rotateAxis(angleX, 1.0, 0.0, 0.0, tmp);
        matrix.translate(0.0, length / 2.0, 0.0);
        matrix.scale(LINE_THICKNESS, length, LINE_THICKNESS);
        DisplayDebug packet =
            new DisplayDebug(
                DebugShape.Cylinder,
                matrix.asFloatData(),
                new com.hypixel.hytale.protocol.Vector3f(color.x, color.y, color.z),
                OUTLINE_DISPLAY_SECONDS,
                (byte) LINE_FLAGS,
                null,
                DebugUtils.DEFAULT_OPACITY
            );
        player.getPacketHandler().write(packet);
    }
}
