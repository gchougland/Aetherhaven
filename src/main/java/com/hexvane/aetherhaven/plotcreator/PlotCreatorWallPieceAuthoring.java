package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.wall.WallCardinal;
import com.hexvane.aetherhaven.wall.WallPieceRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/**
 * The wall style step: walk the five pieces of a style, and inside each one mark the build box, a connection point on
 * every face the piece opens onto, and what it costs to build. Connection points are what make pieces line up when the
 * wand chains them, so they must sit on the edge of the piece's own build box.
 */
public final class PlotCreatorWallPieceAuthoring {
    private PlotCreatorWallPieceAuthoring() {}

    /** Lang key suffix for a piece role, used by the progress bar and the hints. */
    @Nonnull
    public static String roleLangSuffix(@Nonnull WallPieceRole role) {
        return role.name().toLowerCase(Locale.ROOT);
    }

    /** Total substeps for a piece: the build box, one per connection point, then the build cost. */
    public static int substepCount(@Nonnull WallPieceRole role) {
        return 2 + role.connectionCount();
    }

    public static boolean isBoundsSubstep(@Nonnull PlotCreatorDraft draft) {
        return draft.getStep() == PlotCreatorStep.WALL_PIECES && draft.getWallPieceSubstepIndex() == 0;
    }

    /** True while the last substep of a piece is up, where the player sets what that piece costs to build. */
    public static boolean isMaterialsSubstep(@Nonnull PlotCreatorDraft draft) {
        if (draft.getStep() != PlotCreatorStep.WALL_PIECES) {
            return false;
        }
        PlotCreatorWallPieceDraft piece = draft.currentWallPiece();
        return piece != null && draft.getWallPieceSubstepIndex() >= substepCount(piece.getRole()) - 1;
    }

    /** True while one of the connection point substeps is up. */
    public static boolean isConnectionSubstep(@Nonnull PlotCreatorDraft draft) {
        return draft.getStep() == PlotCreatorStep.WALL_PIECES
            && !isBoundsSubstep(draft)
            && !isMaterialsSubstep(draft);
    }

    /** Index into the current piece's connection list, or -1 while the build box substep is up. */
    public static int currentConnectionIndex(@Nonnull PlotCreatorDraft draft) {
        return draft.getWallPieceSubstepIndex() - 1;
    }

    /**
     * Loads the current piece's build box and build cost into the shared draft fields, so the bounds tools and the
     * build materials menu work on the piece being authored without either of them knowing about wall styles.
     */
    public static void enterCurrentPiece(@Nonnull PlotCreatorDraft draft) {
        draft.ensureWallPieces();
        PlotCreatorWallPieceDraft piece = draft.currentWallPiece();
        if (piece == null) {
            return;
        }
        draft.setBoundsDragStart(null);
        draft.setBoundsDragEnd(null);
        draft.setActiveBoundsFaceDrag(null);
        draft.setBoundsPrimaryHeld(false);
        draft.setHoveredBoundsFace(null);
        draft.setCornerFirst(piece.getCornerFirst());
        draft.setCornerSecond(piece.getCornerSecond());
        draft.setPlotAnchor(piece.getAnchor());
        draft.getMaterials().clear();
        draft.getMaterials().addAll(piece.getMaterials());
        draft.setBoundsPhase(
            piece.hasBounds() ? PlotCreatorBoundsPhase.FACE_ADJUST : PlotCreatorBoundsPhase.INITIAL_DRAG
        );
    }

    /** Copies the shared build cost list back onto the current piece. */
    public static void commitMaterialsToCurrentPiece(@Nonnull PlotCreatorDraft draft) {
        PlotCreatorWallPieceDraft piece = draft.currentWallPiece();
        if (piece == null) {
            return;
        }
        piece.getMaterials().clear();
        piece.getMaterials().addAll(draft.getMaterials());
    }

    /**
     * Copies the shared bounds fields back onto the current piece. Moving or resizing the box invalidates connection
     * points that no longer sit on its edge, so those are dropped rather than silently left wrong.
     */
    public static void commitBoundsToCurrentPiece(@Nonnull PlotCreatorDraft draft) {
        PlotCreatorWallPieceDraft piece = draft.currentWallPiece();
        if (piece == null || draft.getCornerFirst() == null || draft.getCornerSecond() == null) {
            return;
        }
        piece.setCornerFirst(draft.getCornerFirst());
        piece.setCornerSecond(draft.getCornerSecond());
        if (draft.getPlotAnchor() == null) {
            PlotCreatorAutoAnchor.applyCenter(draft);
        }
        piece.setAnchor(draft.getPlotAnchor());
        dropConnectionsOffBoundary(piece);
    }

    private static void dropConnectionsOffBoundary(@Nonnull PlotCreatorWallPieceDraft piece) {
        piece.getConnections().removeIf(c -> !isOnFaceEdge(piece, c.face(), c.worldCell()));
    }

    /**
     * The side the current substep is asking the player to mark, or null while the build box or build cost substep is
     * up.
     */
    @Nullable
    public static WallCardinal expectedFace(@Nonnull PlotCreatorDraft draft) {
        PlotCreatorWallPieceDraft piece = draft.currentWallPiece();
        if (piece == null || !isConnectionSubstep(draft)) {
            return null;
        }
        return piece.getRole().connectionFace(currentConnectionIndex(draft));
    }

    /**
     * True when {@code cell} sits on the {@code face} edge of the piece's build box. A cell on a corner counts for both
     * of its sides, which is what makes join spots work on walls that are only one or two blocks thick.
     */
    public static boolean isOnFaceEdge(
        @Nonnull PlotCreatorWallPieceDraft piece,
        @Nonnull WallCardinal face,
        @Nonnull Vector3i cell
    ) {
        if (!piece.hasBounds()) {
            return false;
        }
        Vector3i min = piece.boundsMin();
        Vector3i max = piece.boundsMax();
        if (cell.x < min.x || cell.x > max.x || cell.z < min.z || cell.z > max.z) {
            return false;
        }
        return switch (face) {
            case NORTH -> cell.z == min.z;
            case SOUTH -> cell.z == max.z;
            case WEST -> cell.x == min.x;
            case EAST -> cell.x == max.x;
        };
    }

    /**
     * Records the clicked cell as the current connection point.
     *
     * @return an error lang suffix under {@code aetherhaven.plotcreator.error.}, or null when it was recorded
     */
    @Nullable
    public static String recordConnectionClick(@Nonnull PlotCreatorDraft draft, @Nonnull Vector3i cell) {
        PlotCreatorWallPieceDraft piece = draft.currentWallPiece();
        int index = currentConnectionIndex(draft);
        if (piece == null || index < 0) {
            return "wallConnectionNoPiece";
        }
        if (!piece.hasBounds()) {
            return "wallConnectionNeedsBounds";
        }
        WallCardinal face = piece.getRole().connectionFace(index);
        if (face == null) {
            return "wallConnectionNoPiece";
        }
        if (!isOnFaceEdge(piece, face, cell)) {
            return "wallConnectionOffEdge";
        }
        piece.setConnection(index, new PlotCreatorWallPieceDraft.Connection(face, new Vector3i(cell)));
        return null;
    }

    /** Right click on the current connection point clears it so it can be marked again. */
    public static boolean removeCurrentConnectionNear(@Nonnull PlotCreatorDraft draft, @Nonnull Vector3i cell) {
        PlotCreatorWallPieceDraft piece = draft.currentWallPiece();
        int index = currentConnectionIndex(draft);
        if (piece == null || index < 0 || index >= piece.getConnections().size()) {
            return false;
        }
        Vector3i placed = piece.getConnections().get(index).worldCell();
        if (Math.abs(placed.x - cell.x) > 1 || Math.abs(placed.y - cell.y) > 1 || Math.abs(placed.z - cell.z) > 1) {
            return false;
        }
        piece.removeConnection(index);
        return true;
    }

    /** True when the current substep already has what it needs and the player may move on. */
    public static boolean currentSubstepSatisfied(@Nonnull PlotCreatorDraft draft) {
        PlotCreatorWallPieceDraft piece = draft.currentWallPiece();
        if (piece == null) {
            return false;
        }
        if (isBoundsSubstep(draft)) {
            return draft.getCornerFirst() != null && draft.getCornerSecond() != null;
        }
        // A piece is allowed to cost nothing, so the build cost substep never blocks the player.
        if (isMaterialsSubstep(draft)) {
            return true;
        }
        return currentConnectionIndex(draft) < piece.getConnections().size();
    }

    /**
     * Moves to the next connection point, the build cost, or the next piece.
     *
     * @return false when the last piece is finished and the wizard should leave the wall step
     */
    public static boolean advanceWithinStep(@Nonnull PlotCreatorDraft draft) {
        PlotCreatorWallPieceDraft piece = draft.currentWallPiece();
        if (piece == null) {
            return false;
        }
        if (isBoundsSubstep(draft)) {
            commitBoundsToCurrentPiece(draft);
        }
        if (isMaterialsSubstep(draft)) {
            commitMaterialsToCurrentPiece(draft);
        }
        int next = draft.getWallPieceSubstepIndex() + 1;
        if (next < substepCount(piece.getRole())) {
            draft.setWallPieceSubstepIndex(next);
            return true;
        }
        if (draft.getWallPieceIndex() + 1 >= draft.getWallPieces().size()) {
            return false;
        }
        draft.setWallPieceIndex(draft.getWallPieceIndex() + 1);
        draft.setWallPieceSubstepIndex(0);
        enterCurrentPiece(draft);
        return true;
    }

    /**
     * Moves back one substep, or back to the previous piece's last substep.
     *
     * @return false when already at the first substep of the first piece
     */
    public static boolean backWithinStep(@Nonnull PlotCreatorDraft draft) {
        if (draft.getWallPieceSubstepIndex() > 0) {
            if (draft.getWallPieceSubstepIndex() == 1) {
                commitBoundsToCurrentPiece(draft);
            }
            if (isMaterialsSubstep(draft)) {
                commitMaterialsToCurrentPiece(draft);
            }
            draft.setWallPieceSubstepIndex(draft.getWallPieceSubstepIndex() - 1);
            enterCurrentPiece(draft);
            return true;
        }
        if (draft.getWallPieceIndex() <= 0) {
            return false;
        }
        draft.setWallPieceIndex(draft.getWallPieceIndex() - 1);
        PlotCreatorWallPieceDraft previous = draft.currentWallPiece();
        draft.setWallPieceSubstepIndex(previous != null ? substepCount(previous.getRole()) - 1 : 0);
        enterCurrentPiece(draft);
        return true;
    }

    /** Error lang suffix for the first piece that is not finished, or null when the style is ready to save. */
    @Nullable
    public static String validateStyle(@Nonnull PlotCreatorDraft draft) {
        if (draft.getWallPieces().isEmpty()) {
            return "wallPiecesMissing";
        }
        for (PlotCreatorWallPieceDraft piece : draft.getWallPieces()) {
            if (!piece.hasBounds() || piece.getAnchor() == null) {
                return "wallPieceBoundsMissing";
            }
            if (piece.getConnections().size() < piece.getRole().connectionCount()) {
                return "wallPieceConnectionsMissing";
            }
        }
        return null;
    }
}
