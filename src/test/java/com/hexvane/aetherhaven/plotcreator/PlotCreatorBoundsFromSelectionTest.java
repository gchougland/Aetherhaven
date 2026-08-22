package com.hexvane.aetherhaven.plotcreator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.hexvane.aetherhaven.wall.WallPieceRole;
import java.util.List;
import java.util.UUID;
import org.joml.Vector3i;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("construction")
class PlotCreatorBoundsFromSelectionTest {
    @Test
    void festivalLockedReturnsError() {
        PlotCreatorSession session = new PlotCreatorSession(UUID.randomUUID(), null);
        session.getDraft().setFestivalSizeLocked(true);

        assertEquals(
            "boundsLockedFestival",
            PlotCreatorService.applyBoundsFromBuilderSelection(session, new Vector3i(0, 0, 0), new Vector3i(4, 4, 4))
        );
    }

    @Test
    void tooFlatReturnsError() {
        PlotCreatorSession session = new PlotCreatorSession(UUID.randomUUID(), null);

        assertEquals(
            "boundsTooFlat",
            PlotCreatorService.applyBoundsFromBuilderSelection(session, new Vector3i(0, 0, 0), new Vector3i(4, 0, 4))
        );
    }

    @Test
    void validBoundsSetsCornersAndFaceAdjustPhase() {
        PlotCreatorSession session = new PlotCreatorSession(UUID.randomUUID(), null);
        PlotCreatorDraft draft = session.getDraft();
        draft.setStep(PlotCreatorStep.BOUNDS);
        Vector3i min = new Vector3i(10, 64, 20);
        Vector3i max = new Vector3i(14, 68, 24);

        assertNull(PlotCreatorService.applyBoundsFromBuilderSelection(session, min, max));

        assertEquals(min, draft.getCornerFirst());
        assertEquals(max, draft.getCornerSecond());
        assertEquals(PlotCreatorBoundsPhase.FACE_ADJUST, draft.getBoundsPhase());
    }

    @Test
    void wallPieceBoundsSubstepUpdatesCurrentPiece() {
        PlotCreatorSession session = new PlotCreatorSession(UUID.randomUUID(), null);
        PlotCreatorDraft draft = session.getDraft();
        draft.setKinds(List.of(PlotBuildingKind.WALL));
        draft.setStep(PlotCreatorStep.WALL_PIECES);
        draft.ensureWallPieces();
        Vector3i min = new Vector3i(-2, 64, -7);
        Vector3i max = new Vector3i(2, 74, 8);

        assertNull(PlotCreatorService.applyBoundsFromBuilderSelection(session, min, max));

        PlotCreatorWallPieceDraft piece = draft.currentWallPiece();
        assertNotNull(piece);
        assertEquals(WallPieceRole.SEGMENT, piece.getRole());
        assertEquals(min, piece.getCornerFirst());
        assertEquals(max, piece.getCornerSecond());
    }
}
