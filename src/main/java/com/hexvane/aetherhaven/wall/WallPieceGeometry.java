package com.hexvane.aetherhaven.wall;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/**
 * Wall placement math built on authored connection points. A connection point is the last cell of a piece on that
 * face, so two pieces join when their connection cells sit exactly one block apart along the join direction:
 *
 * <pre>anchorB = connectionCellA + joinDir - rotate(localConnectionB, yawB)</pre>
 *
 * Only X and Z take part, which is why players can still raise and lower a piece freely.
 */
public final class WallPieceGeometry {
    /** Sign / logical anchor offset for wall pieces (centre of the footprint on the ground). */
    public static final int[] PLOT_ANCHOR_OFFSET = new int[] {0, 0, 0};

    private WallPieceGeometry() {}

    /** World cell of the piece's connection on {@code worldFace}, or null when it has none there. */
    @Nullable
    public static Vector3i connectionCellWorld(
        @Nonnull WallPieceDefinition def,
        @Nonnull Vector3i signAnchor,
        int rotationSteps,
        @Nonnull WallCardinal worldFace
    ) {
        Vector3i offset = def.worldConnectionOffset(worldFace, rotationSteps);
        if (offset == null) {
            return null;
        }
        return new Vector3i(signAnchor.x + offset.x, signAnchor.y, signAnchor.z + offset.z);
    }

    /**
     * Sign anchor for a piece that joins the {@code joinDir} face of an already placed piece. Null when either piece
     * has no connection on the face it would need.
     */
    @Nullable
    public static Vector3i joinedSignAnchor(
        @Nonnull WallPieceDefinition fromDef,
        @Nonnull Vector3i fromSign,
        int fromRotationSteps,
        @Nonnull WallCardinal joinDir,
        @Nonnull WallPieceDefinition toDef,
        int toRotationSteps
    ) {
        Vector3i exitCell = connectionCellWorld(fromDef, fromSign, fromRotationSteps, joinDir);
        if (exitCell == null) {
            return null;
        }
        Vector3i entryOffset = toDef.worldConnectionOffset(joinDir.opposite(), toRotationSteps);
        if (entryOffset == null) {
            return null;
        }
        return new Vector3i(
            exitCell.x + joinDir.dx - entryOffset.x,
            fromSign.y,
            exitCell.z + joinDir.dz - entryOffset.z
        );
    }

    /** World footprint of a placed piece as {@code [minX, minZ, maxX, maxZ]}. */
    @Nonnull
    public static int[] worldBoundsXZ(
        @Nonnull WallPieceDefinition def, @Nonnull Vector3i signAnchor, int rotationSteps
    ) {
        return def.worldBoundsXZ(signAnchor, rotationSteps);
    }

    /**
     * Distance between the two connection cells of a straight piece, used when probing town data along a wall run.
     * Falls back to the built in segment length when the style has no straight piece.
     */
    public static int segmentChainSpan(@Nullable WallStyle style) {
        WallStyle.Piece segment = style == null ? null : style.piece(WallPieceRole.SEGMENT);
        if (segment != null) {
            WallPieceDefinition def = segment.definition();
            Vector3i north = def.localConnection(WallCardinal.NORTH);
            Vector3i south = def.localConnection(WallCardinal.SOUTH);
            if (north != null && south != null) {
                return Math.abs(south.z - north.z) + 1;
            }
            Vector3i east = def.localConnection(WallCardinal.EAST);
            Vector3i west = def.localConnection(WallCardinal.WEST);
            if (east != null && west != null) {
                return Math.abs(east.x - west.x) + 1;
            }
        }
        return 16;
    }

    public static int segmentChainSpan() {
        return segmentChainSpan(WallStyleCatalog.get().defaultStyle());
    }

    /**
     * True when the piece is a tower. Uses the installed styles, with the built in id prefix as a fallback so this
     * still answers correctly before the catalog is loaded.
     */
    public static boolean isTowerConstructionId(@Nonnull String constructionId) {
        WallPieceRole role = WallStyleCatalog.get().roleFor(constructionId);
        if (role != null) {
            return role.isTower();
        }
        return constructionId.contains("_tower");
    }
}
