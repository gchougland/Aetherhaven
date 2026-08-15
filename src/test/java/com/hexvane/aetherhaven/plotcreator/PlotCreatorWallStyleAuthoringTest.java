package com.hexvane.aetherhaven.plotcreator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.construction.MaterialRequirement;
import com.hexvane.aetherhaven.wall.WallCardinal;
import com.hexvane.aetherhaven.wall.WallPieceRole;
import java.util.List;
import javax.annotation.Nonnull;
import org.joml.Vector3i;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The wall style step in the plot creator: marking boxes, marking join spots on the edge of those boxes, and walking
 * the pieces forward and back.
 */
@Tag("wall-placement")
class PlotCreatorWallStyleAuthoringTest {
    @Test
    void wallModeCreatesOnePiecePerRole() {
        PlotCreatorDraft draft = wallDraft();
        assertTrue(draft.isWallMode());
        draft.ensureWallPieces();
        assertEquals(WallPieceRole.AUTHORING_ORDER.length, draft.getWallPieces().size());
        for (int i = 0; i < WallPieceRole.AUTHORING_ORDER.length; i++) {
            assertEquals(WallPieceRole.AUTHORING_ORDER[i], draft.getWallPieces().get(i).getRole());
        }
    }

    @Test
    void connectionClickOffTheBoundaryIsRejected() {
        PlotCreatorDraft draft = draftOnConnectionSubstep();
        assertEquals(
            "wallConnectionOffEdge",
            PlotCreatorWallPieceAuthoring.recordConnectionClick(draft, new Vector3i(0, 64, 0))
        );
        assertTrue(currentPiece(draft).getConnections().isEmpty());
    }

    @Test
    void aCornerCountsForBothOfItsSidesSoThinWallsStillWork() {
        PlotCreatorDraft draft = draftOnConnectionSubstep();
        assertNull(PlotCreatorWallPieceAuthoring.recordConnectionClick(draft, new Vector3i(-2, 64, -7)));
        assertEquals(WallCardinal.NORTH, currentPiece(draft).getConnections().get(0).face());
    }

    @Test
    void aTwoBlockThickWallCanTakeJoinSpotsOnBothEnds() {
        PlotCreatorDraft draft = wallDraft();
        draft.ensureWallPieces();
        markBounds(draft, new Vector3i(0, 64, -7), new Vector3i(1, 74, 8));
        draft.setWallPieceSubstepIndex(1);
        assertNull(PlotCreatorWallPieceAuthoring.recordConnectionClick(draft, new Vector3i(0, 64, -7)));
        draft.setWallPieceSubstepIndex(2);
        assertNull(PlotCreatorWallPieceAuthoring.recordConnectionClick(draft, new Vector3i(0, 64, 8)));
        assertEquals(2, currentPiece(draft).getConnections().size());
    }

    @Test
    void eachSubstepAsksForOneFixedSide() {
        PlotCreatorDraft draft = draftOnConnectionSubstep();
        assertEquals(WallCardinal.NORTH, PlotCreatorWallPieceAuthoring.expectedFace(draft));
        assertNull(PlotCreatorWallPieceAuthoring.recordConnectionClick(draft, new Vector3i(0, 64, -7)));
        List<PlotCreatorWallPieceDraft.Connection> connections = currentPiece(draft).getConnections();
        assertEquals(1, connections.size());
        assertEquals(WallCardinal.NORTH, connections.get(0).face());
        assertEquals(new Vector3i(0, 64, -7), connections.get(0).worldCell());

        draft.setWallPieceSubstepIndex(2);
        assertEquals(WallCardinal.SOUTH, PlotCreatorWallPieceAuthoring.expectedFace(draft));
        assertEquals(
            "wallConnectionOffEdge",
            PlotCreatorWallPieceAuthoring.recordConnectionClick(draft, new Vector3i(0, 64, -7))
        );
        assertNull(PlotCreatorWallPieceAuthoring.recordConnectionClick(draft, new Vector3i(0, 64, 8)));
        assertEquals(2, currentPiece(draft).getConnections().size());
    }

    @Test
    void aCornerTowerAsksForTwoSidesAtRightAngles() {
        PlotCreatorDraft draft = wallDraft();
        draft.ensureWallPieces();
        draft.setWallPieceIndex(indexOf(draft, WallPieceRole.TOWER_CORNER));
        markBounds(draft, new Vector3i(-3, 64, -3), new Vector3i(3, 74, 3));
        draft.setWallPieceSubstepIndex(1);
        assertEquals(WallCardinal.SOUTH, PlotCreatorWallPieceAuthoring.expectedFace(draft));
        assertNull(PlotCreatorWallPieceAuthoring.recordConnectionClick(draft, new Vector3i(0, 64, 3)));
        draft.setWallPieceSubstepIndex(2);
        assertEquals(WallCardinal.EAST, PlotCreatorWallPieceAuthoring.expectedFace(draft));
        assertEquals(
            "wallConnectionOffEdge",
            PlotCreatorWallPieceAuthoring.recordConnectionClick(draft, new Vector3i(0, 64, -3))
        );
        assertNull(PlotCreatorWallPieceAuthoring.recordConnectionClick(draft, new Vector3i(3, 64, 0)));
    }

    @Test
    void resizingTheBoxDropsJoinSpotsThatNoLongerSitOnIt() {
        PlotCreatorDraft draft = draftOnConnectionSubstep();
        assertNull(PlotCreatorWallPieceAuthoring.recordConnectionClick(draft, new Vector3i(0, 64, -7)));
        assertEquals(1, currentPiece(draft).getConnections().size());
        draft.setWallPieceSubstepIndex(0);
        markBounds(draft, new Vector3i(-2, 64, -4), new Vector3i(2, 74, 8));
        assertTrue(currentPiece(draft).getConnections().isEmpty());
    }

    @Test
    void advancingWalksTheJoinSpotsAndTheCostThenMovesToTheNextPiece() {
        PlotCreatorDraft draft = wallDraft();
        draft.ensureWallPieces();
        PlotCreatorWallPieceAuthoring.enterCurrentPiece(draft);
        markBounds(draft, new Vector3i(-2, 64, -7), new Vector3i(2, 74, 8));
        assertTrue(PlotCreatorWallPieceAuthoring.advanceWithinStep(draft));
        assertEquals(1, draft.getWallPieceSubstepIndex());
        assertNull(PlotCreatorWallPieceAuthoring.recordConnectionClick(draft, new Vector3i(0, 64, -7)));
        assertTrue(PlotCreatorWallPieceAuthoring.advanceWithinStep(draft));
        assertEquals(2, draft.getWallPieceSubstepIndex());
        assertNull(PlotCreatorWallPieceAuthoring.recordConnectionClick(draft, new Vector3i(0, 64, 8)));
        assertTrue(PlotCreatorWallPieceAuthoring.advanceWithinStep(draft));
        assertEquals(3, draft.getWallPieceSubstepIndex());
        assertTrue(PlotCreatorWallPieceAuthoring.isMaterialsSubstep(draft));
        assertTrue(PlotCreatorWallPieceAuthoring.advanceWithinStep(draft));
        assertEquals(1, draft.getWallPieceIndex());
        assertEquals(0, draft.getWallPieceSubstepIndex());
        assertTrue(PlotCreatorWallPieceAuthoring.backWithinStep(draft));
        assertEquals(0, draft.getWallPieceIndex());
        assertEquals(3, draft.getWallPieceSubstepIndex());
    }

    @Test
    void everyPieceEndsOnItsBuildCost() {
        assertEquals(3, PlotCreatorWallPieceAuthoring.substepCount(WallPieceRole.TOWER_END));
        assertEquals(4, PlotCreatorWallPieceAuthoring.substepCount(WallPieceRole.SEGMENT));
        PlotCreatorDraft draft = wallDraft();
        draft.ensureWallPieces();
        draft.setWallPieceIndex(indexOf(draft, WallPieceRole.TOWER_END));
        draft.setWallPieceSubstepIndex(2);
        assertTrue(PlotCreatorWallPieceAuthoring.isMaterialsSubstep(draft));
        assertFalse(PlotCreatorWallPieceAuthoring.isConnectionSubstep(draft));
        assertNull(PlotCreatorWallPieceAuthoring.expectedFace(draft));
        // A piece may cost nothing, so the cost substep never blocks the player.
        assertTrue(PlotCreatorWallPieceAuthoring.currentSubstepSatisfied(draft));
    }

    /** Each piece keeps its own cost list, swapped in and out of the shared one the build materials menu edits. */
    @Test
    void eachPieceKeepsItsOwnBuildCost() {
        PlotCreatorDraft draft = wallDraft();
        draft.ensureWallPieces();
        PlotCreatorWallPieceAuthoring.enterCurrentPiece(draft);
        draft.getMaterials().add(MaterialRequirement.ofItem("Rock_Stone_Brick", 40));
        PlotCreatorWallPieceAuthoring.commitMaterialsToCurrentPiece(draft);

        draft.setWallPieceIndex(1);
        PlotCreatorWallPieceAuthoring.enterCurrentPiece(draft);
        assertTrue(draft.getMaterials().isEmpty());
        draft.getMaterials().add(MaterialRequirement.ofResourceType("Wood_All", 12));
        PlotCreatorWallPieceAuthoring.commitMaterialsToCurrentPiece(draft);

        draft.setWallPieceIndex(0);
        PlotCreatorWallPieceAuthoring.enterCurrentPiece(draft);
        assertEquals(1, draft.getMaterials().size());
        assertEquals("Rock_Stone_Brick", draft.getMaterials().get(0).getItemId());
        assertEquals(40, draft.getMaterials().get(0).getCount());
        assertEquals("Wood_All", draft.getWallPieces().get(1).getMaterials().get(0).getResourceTypeId());
    }

    @Test
    void anUnfinishedStyleReportsWhichPartIsMissing() {
        PlotCreatorDraft draft = wallDraft();
        draft.ensureWallPieces();
        assertEquals("wallPieceBoundsMissing", PlotCreatorWallPieceAuthoring.validateStyle(draft));
        for (PlotCreatorWallPieceDraft piece : draft.getWallPieces()) {
            piece.setCornerFirst(new Vector3i(-2, 64, -7));
            piece.setCornerSecond(new Vector3i(2, 74, 8));
            piece.setAnchor(new Vector3i(0, 64, 0));
        }
        assertEquals("wallPieceConnectionsMissing", PlotCreatorWallPieceAuthoring.validateStyle(draft));
    }

    @Test
    void pieceIdsHangOffTheStyleBaseId() {
        PlotCreatorDraft draft = wallDraft();
        draft.ensureWallPieces();
        draft.setConstructionId("plot_wall_mossy");
        assertEquals("mossy", PlotCreatorWallStyleIds.styleId(draft));
        assertEquals(
            List.of(
                "plot_wall_mossy_segment",
                "plot_wall_mossy_gate",
                "plot_wall_mossy_tower_end",
                "plot_wall_mossy_tower_straight",
                "plot_wall_mossy_tower_corner"
            ),
            PlotCreatorWallStyleIds.pieceConstructionIds(draft)
        );
    }

    @Test
    void anIdWithoutTheWallPrefixHasNoStyleId() {
        PlotCreatorDraft draft = wallDraft();
        draft.setConstructionId("plot_mossy");
        assertNull(PlotCreatorWallStyleIds.styleId(draft));
        assertNull(PlotCreatorWallStyleIds.baseId(draft));
    }

    @Test
    void theWallStepReplacesTheSharedBoundsStep() {
        PlotCreatorDraft draft = wallDraft();
        draft.setStep(PlotCreatorStep.WALL_PIECES);
        draft.setWallPieceSubstepIndex(0);
        assertTrue(draft.isEditingBounds());
        draft.setWallPieceSubstepIndex(1);
        assertFalse(draft.isEditingBounds());
    }

    @Test
    void pieceNamesReadFromTheStyleName() {
        PlotCreatorDraft draft = wallDraft();
        draft.setDisplayName("Mossy wall");
        assertEquals("Mossy wall", PlotCreatorWallStyleWriter.pieceDisplayName(draft, WallPieceRole.SEGMENT));
        assertEquals("Mossy wall gate", PlotCreatorWallStyleWriter.pieceDisplayName(draft, WallPieceRole.GATE));
        assertEquals(
            "Mossy wall corner tower",
            PlotCreatorWallStyleWriter.pieceDisplayName(draft, WallPieceRole.TOWER_CORNER)
        );
    }

    @Nonnull
    private static PlotCreatorDraft wallDraft() {
        PlotCreatorDraft draft = new PlotCreatorDraft();
        draft.setKinds(List.of(PlotBuildingKind.WALL));
        draft.setStep(PlotCreatorStep.WALL_PIECES);
        return draft;
    }

    /** A straight wall piece with its box marked, sitting on the first join spot substep. */
    @Nonnull
    private static PlotCreatorDraft draftOnConnectionSubstep() {
        PlotCreatorDraft draft = wallDraft();
        draft.ensureWallPieces();
        markBounds(draft, new Vector3i(-2, 64, -7), new Vector3i(2, 74, 8));
        draft.setWallPieceSubstepIndex(1);
        return draft;
    }

    private static void markBounds(@Nonnull PlotCreatorDraft draft, @Nonnull Vector3i min, @Nonnull Vector3i max) {
        draft.setCornerFirst(min);
        draft.setCornerSecond(max);
        draft.setPlotAnchor(new Vector3i(0, min.y, 0));
        PlotCreatorWallPieceAuthoring.commitBoundsToCurrentPiece(draft);
    }

    private static int indexOf(@Nonnull PlotCreatorDraft draft, @Nonnull WallPieceRole role) {
        for (int i = 0; i < draft.getWallPieces().size(); i++) {
            if (draft.getWallPieces().get(i).getRole() == role) {
                return i;
            }
        }
        throw new IllegalStateException("no piece for role " + role);
    }

    @Nonnull
    private static PlotCreatorWallPieceDraft currentPiece(@Nonnull PlotCreatorDraft draft) {
        PlotCreatorWallPieceDraft piece = draft.currentWallPiece();
        assertNotNull(piece);
        return piece;
    }
}
