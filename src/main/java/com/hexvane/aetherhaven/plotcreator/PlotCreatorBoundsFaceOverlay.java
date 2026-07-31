package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.pathtool.PathDebugPreviewUtil;
import com.hypixel.hytale.math.matrix.Matrix4dUtil;
import com.hypixel.hytale.protocol.DebugShape;
import com.hypixel.hytale.protocol.packets.player.DisplayDebug;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Matrix4d;
import org.joml.Vector3f;
import org.joml.Vector3i;

/** Semi-transparent side panels on each face of the bounds box. */
final class PlotCreatorBoundsFaceOverlay {
    private static final Vector3f COLOR_DEFAULT = new Vector3f(1.0f, 1.0f, 1.0f);
    private static final Vector3f COLOR_HOVER = PathDebugPreviewUtil.COLOR_KEYFRAME_LOOK;
    private static final Vector3f COLOR_ACTIVE = PathDebugPreviewUtil.COLOR_KEYFRAME_SEL;
    private static final double THICK = PlotCreatorBoundsConstants.FACE_PANEL_THICKNESS;
    private static final double OUTSET = PlotCreatorBoundsConstants.FACE_PANEL_OUTSET;

    private PlotCreatorBoundsFaceOverlay() {}

    static void draw(
        @Nonnull PlayerRef playerRef,
        @Nonnull Vector3i min,
        @Nonnull Vector3i max,
        @Nullable PlotCreatorBoundsFace hovered,
        @Nullable PlotCreatorBoundsFace active
    ) {
        for (PlotCreatorBoundsFace face : PlotCreatorBoundsFace.values()) {
            Vector3f color = COLOR_DEFAULT;
            float opacity = 0.25f;
            if (face == active) {
                color = COLOR_ACTIVE;
                opacity = 0.45f;
            } else if (face == hovered) {
                color = COLOR_HOVER;
                opacity = 0.38f;
            }
            drawFace(playerRef, face, min, max, color, opacity);
        }
    }

    private static void drawFace(
        @Nonnull PlayerRef playerRef,
        @Nonnull PlotCreatorBoundsFace face,
        @Nonnull Vector3i min,
        @Nonnull Vector3i max,
        @Nonnull Vector3f color,
        float opacity
    ) {
        double cx;
        double cy;
        double cz;
        double sx;
        double sy;
        double sz;
        double x0 = min.x;
        double x1 = max.x + 1.0;
        double y0 = min.y;
        double y1 = max.y + 1.0;
        double z0 = min.z;
        double z1 = max.z + 1.0;
        switch (face) {
            case MIN_X -> {
                cx = x0 - OUTSET - THICK * 0.5;
                cy = (y0 + y1) * 0.5;
                cz = (z0 + z1) * 0.5;
                sx = THICK;
                sy = y1 - y0;
                sz = z1 - z0;
            }
            case MAX_X -> {
                cx = x1 + OUTSET + THICK * 0.5;
                cy = (y0 + y1) * 0.5;
                cz = (z0 + z1) * 0.5;
                sx = THICK;
                sy = y1 - y0;
                sz = z1 - z0;
            }
            case MIN_Y -> {
                cx = (x0 + x1) * 0.5;
                cy = y0 - OUTSET - THICK * 0.5;
                cz = (z0 + z1) * 0.5;
                sx = x1 - x0;
                sy = THICK;
                sz = z1 - z0;
            }
            case MAX_Y -> {
                cx = (x0 + x1) * 0.5;
                cy = y1 + OUTSET + THICK * 0.5;
                cz = (z0 + z1) * 0.5;
                sx = x1 - x0;
                sy = THICK;
                sz = z1 - z0;
            }
            case MIN_Z -> {
                cx = (x0 + x1) * 0.5;
                cy = (y0 + y1) * 0.5;
                cz = z0 - OUTSET - THICK * 0.5;
                sx = x1 - x0;
                sy = y1 - y0;
                sz = THICK;
            }
            case MAX_Z -> {
                cx = (x0 + x1) * 0.5;
                cy = (y0 + y1) * 0.5;
                cz = z1 + OUTSET + THICK * 0.5;
                sx = x1 - x0;
                sy = y1 - y0;
                sz = THICK;
            }
            default -> throw new IllegalStateException("Unexpected face: " + face);
        }
        Matrix4d m = new Matrix4d();
        m.identity();
        m.translate(cx, cy, cz);
        m.scale(sx * 0.5, sy * 0.5, sz * 0.5);
        DisplayDebug packet =
            new DisplayDebug(
                DebugShape.Cube,
                Matrix4dUtil.asFloatData(m),
                color,
                PlotCreatorBoundsConstants.FACE_OVERLAY_SECONDS,
                (byte) PathDebugPreviewUtil.FLAG_SOLID_OVERLAY,
                null,
                opacity
            );
        playerRef.getPacketHandler().write(packet);
    }
}
