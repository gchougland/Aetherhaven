package com.hexvane.aetherhaven.plotcreator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.hexvane.aetherhaven.wall.WallPieceRole;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolSelectionTransform;
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
    void validBoundsSetsCornersAndSelectionPhase() {
        PlotCreatorSession session = new PlotCreatorSession(UUID.randomUUID(), null);
        PlotCreatorDraft draft = session.getDraft();
        draft.setStep(PlotCreatorStep.BOUNDS);
        Vector3i min = new Vector3i(10, 64, 20);
        Vector3i max = new Vector3i(14, 68, 24);

        assertNull(PlotCreatorService.applyBoundsFromBuilderSelection(session, min, max));

        assertEquals(min, draft.getCornerFirst());
        assertEquals(max, draft.getCornerSecond());
        assertEquals(PlotCreatorBoundsPhase.SELECTION, draft.getBoundsPhase());
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

    @Test
    void clearedSelectionSentinelIsDetected() {
        assertEquals(
            true,
            PlotCreatorSelectionBoundsService.isSelectionClearPacket(
                Integer.MIN_VALUE,
                Integer.MIN_VALUE,
                Integer.MIN_VALUE,
                Integer.MIN_VALUE,
                Integer.MIN_VALUE,
                Integer.MIN_VALUE
            )
        );
        assertEquals(
            false,
            PlotCreatorSelectionBoundsService.isSelectionClearPacket(0, 0, 0, 4, 4, 4)
        );
    }

    @Test
    void selectionTransformAppliesTranslationToBounds() {
        BuilderToolSelectionTransform packet = new BuilderToolSelectionTransform();
        packet.initialSelectionMin = blockPos(10, 64, 20);
        packet.initialSelectionMax = blockPos(14, 68, 24);
        packet.translationOffset = blockPos(2, 0, -1);
        packet.applyTransformationToSelectionMinMax = true;

        Vector3i[] bounds = PlotCreatorSelectionBoundsService.boundsFromSelectionTransform(packet);

        assertNotNull(bounds);
        assertEquals(new Vector3i(12, 64, 19), bounds[0]);
        assertEquals(new Vector3i(16, 68, 23), bounds[1]);
    }

    private static com.hypixel.hytale.protocol.BlockPosition blockPos(int x, int y, int z) {
        var pos = new com.hypixel.hytale.protocol.BlockPosition();
        pos.x = x;
        pos.y = y;
        pos.z = z;
        return pos;
    }
}
