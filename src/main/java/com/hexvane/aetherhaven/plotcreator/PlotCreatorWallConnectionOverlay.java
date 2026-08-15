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

/** Highlighted cells for the connection points marked on the wall piece being authored. */
final class PlotCreatorWallConnectionOverlay {
    private static final Vector3f COLOR_PLACED = new Vector3f(0.35f, 0.85f, 1.0f);
    private static final Vector3f COLOR_CURRENT = PathDebugPreviewUtil.COLOR_KEYFRAME_SEL;
    /** Cells the player is allowed to pick for the side the wizard is asking about. */
    private static final Vector3f COLOR_TARGET_EDGE = new Vector3f(1.0f, 0.85f, 0.3f);
    private static final float SECONDS = PlotCreatorBoundsConstants.FACE_OVERLAY_SECONDS;

    private PlotCreatorWallConnectionOverlay() {}

    static void draw(@Nonnull PlayerRef playerRef, @Nonnull PlotCreatorDraft draft) {
        PlotCreatorWallPieceDraft piece = draft.currentWallPiece();
        if (piece == null) {
            return;
        }
        drawTargetEdge(playerRef, piece, PlotCreatorWallPieceAuthoring.expectedFace(draft));
        int current = PlotCreatorWallPieceAuthoring.currentConnectionIndex(draft);
        for (int i = 0; i < piece.getConnections().size(); i++) {
            drawCell(
                playerRef,
                piece.getConnections().get(i).worldCell(),
                i == current ? COLOR_CURRENT : COLOR_PLACED
            );
        }
    }

    /** Marks the row of cells along the side the current substep wants, so the player can see where to click. */
    private static void drawTargetEdge(
        @Nonnull PlayerRef playerRef,
        @Nonnull PlotCreatorWallPieceDraft piece,
        @Nullable com.hexvane.aetherhaven.wall.WallCardinal face
    ) {
        if (face == null || !piece.hasBounds()) {
            return;
        }
        Vector3i min = piece.boundsMin();
        Vector3i max = piece.boundsMax();
        int y = min.y;
        switch (face) {
            case NORTH -> markRowAlongX(playerRef, min.x, max.x, y, min.z);
            case SOUTH -> markRowAlongX(playerRef, min.x, max.x, y, max.z);
            case WEST -> markRowAlongZ(playerRef, min.x, y, min.z, max.z);
            case EAST -> markRowAlongZ(playerRef, max.x, y, min.z, max.z);
        }
    }

    private static void markRowAlongX(@Nonnull PlayerRef playerRef, int minX, int maxX, int y, int z) {
        for (int x = minX; x <= maxX; x++) {
            drawFlatCell(playerRef, new Vector3i(x, y, z));
        }
    }

    private static void markRowAlongZ(@Nonnull PlayerRef playerRef, int x, int y, int minZ, int maxZ) {
        for (int z = minZ; z <= maxZ; z++) {
            drawFlatCell(playerRef, new Vector3i(x, y, z));
        }
    }

    private static void drawFlatCell(@Nonnull PlayerRef playerRef, @Nonnull Vector3i cell) {
        Matrix4d m = new Matrix4d();
        m.identity();
        m.translate(cell.x + 0.5, cell.y + 0.05, cell.z + 0.5);
        m.scale(0.9, 0.08, 0.9);
        send(playerRef, m, COLOR_TARGET_EDGE);
    }

    private static void drawCell(@Nonnull PlayerRef playerRef, @Nonnull Vector3i cell, @Nonnull Vector3f color) {
        Matrix4d m = new Matrix4d();
        m.identity();
        m.translate(cell.x + 0.5, cell.y + 0.5, cell.z + 0.5);
        m.scale(0.55, 0.55, 0.55);
        send(playerRef, m, color);
    }

    private static void send(@Nonnull PlayerRef playerRef, @Nonnull Matrix4d m, @Nonnull Vector3f color) {
        DisplayDebug packet =
            new DisplayDebug(
                DebugShape.Cube,
                Matrix4dUtil.asFloatData(m),
                color,
                SECONDS,
                (byte) PathDebugPreviewUtil.FLAG_SOLID_OVERLAY,
                null,
                0.7f
            );
        playerRef.getPacketHandler().write(packet);
    }
}
