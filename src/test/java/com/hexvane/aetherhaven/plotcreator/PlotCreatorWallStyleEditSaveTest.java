package com.hexvane.aetherhaven.plotcreator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.MaterialRequirement;
import com.hexvane.aetherhaven.wall.WallCardinal;
import com.hexvane.aetherhaven.wall.WallPieceDefinition;
import com.hexvane.aetherhaven.wall.WallStyle;
import com.hexvane.aetherhaven.wall.WallStyleFixtures;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import org.joml.Vector3i;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Opening a wall in the building editor and saving it again has to land on the same files it came from, so the shipped
 * walls can be reshaped and picked up by the asset sync, and so nothing about their shape or height drifts on the way
 * through.
 */
@Tag("wall-placement")
class PlotCreatorWallStyleEditSaveTest {
    @Test
    void theShippedWallCanBeOpenedForEditing() {
        ConstructionCatalog catalog = WallStyleFixtures.coreConstructionCatalog();
        WallStyle style = WallStyleFixtures.coreStyle();
        assertTrue(PlotCreatorWallStyleLoader.isEditable(catalog, style));
        assertEquals("plot_wall_core", PlotCreatorWallStyleLoader.baseIdForStyle(style));
    }

    @Test
    void savingAnOpenedWallWritesOverTheSameBuildingsAndPrefabs() {
        PlotCreatorDraft draft = loadedCoreDraft();
        WallStyle style = WallStyleFixtures.coreStyle();
        ConstructionCatalog catalog = WallStyleFixtures.coreConstructionCatalog();
        List<String> expectedIds = new ArrayList<>();
        List<String> expectedPrefabs = new ArrayList<>();
        for (WallStyle.Piece piece : style.piecesInOrder()) {
            ConstructionDefinition def = catalog.get(piece.constructionId());
            assertNotNull(def);
            expectedIds.add(piece.constructionId());
            expectedPrefabs.add(def.getPrefabPath());
        }
        assertEquals(expectedIds, PlotCreatorWallStyleIds.pieceConstructionIds(draft));
        List<String> prefabs = new ArrayList<>();
        for (PlotCreatorWallPieceDraft piece : draft.getWallPieces()) {
            prefabs.add(PlotCreatorWallStyleIds.piecePrefabPathKey(draft, piece, "plot_wall_core"));
        }
        assertEquals(expectedPrefabs, prefabs);
        assertEquals("core", PlotCreatorWallStyleIds.styleIdForBase("plot_wall_core"));
    }

    @Test
    void renamingAnOpenedWallSavesItAsAFreshStyle() {
        PlotCreatorDraft draft = loadedCoreDraft();
        draft.setConstructionId("plot_wall_mossy");
        assertFalse(PlotCreatorWallStyleIds.isSavingLoadedStyle(draft, "plot_wall_mossy"));
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
        assertEquals("mossy", PlotCreatorWallStyleIds.styleIdForBase("plot_wall_mossy"));
    }

    /** The box and join spots the editor shows have to measure back to the exact numbers in the building file. */
    @Test
    void openedPiecesMeasureBackToTheShapeTheyCameFrom() {
        PlotCreatorDraft draft = loadedCoreDraft();
        WallStyle style = WallStyleFixtures.coreStyle();
        for (PlotCreatorWallPieceDraft piece : draft.getWallPieces()) {
            WallStyle.Piece source = style.piece(piece.getRole());
            assertNotNull(source);
            WallPieceDefinition shape = source.definition();
            Vector3i anchor = piece.getAnchor();
            assertNotNull(anchor);
            assertEquals(shape.boundsMinLocal(), new Vector3i(piece.boundsMin()).sub(anchor), piece.getRole().name());
            assertEquals(shape.boundsMaxLocal(), new Vector3i(piece.boundsMax()).sub(anchor), piece.getRole().name());
            for (PlotCreatorWallPieceDraft.Connection c : piece.getConnections()) {
                Vector3i local = shape.localConnection(c.face());
                assertNotNull(local, piece.getRole() + " " + c.face());
                assertEquals(local.x, c.worldCell().x - anchor.x, piece.getRole() + " " + c.face() + " x");
                assertEquals(local.z, c.worldCell().z - anchor.z, piece.getRole() + " " + c.face() + " z");
            }
        }
    }

    /** Re-saving keeps whatever offset a piece already had, so an edited wall never moves up or down a block. */
    @Test
    void openedPiecesKeepTheHeightOffsetTheyWereSavedWith() {
        PlotCreatorDraft draft = loadedCoreDraft();
        ConstructionCatalog catalog = WallStyleFixtures.coreConstructionCatalog();
        for (PlotCreatorWallPieceDraft piece : draft.getWallPieces()) {
            ConstructionDefinition def = catalog.get(piece.getConstructionId());
            assertNotNull(def);
            int[] kept = piece.getPlotAnchorOffset();
            assertNotNull(kept);
            assertEquals(def.getPlotAnchorOffset()[1], kept[1], piece.getRole().name());
        }
    }

    /** Opening a wall shows the cost each piece already has, so re-saving does not make it free. */
    @Test
    void openedPiecesKeepTheBuildCostTheyWereSavedWith() {
        PlotCreatorDraft draft = loadedCoreDraft();
        ConstructionCatalog catalog = WallStyleFixtures.coreConstructionCatalog();
        for (PlotCreatorWallPieceDraft piece : draft.getWallPieces()) {
            ConstructionDefinition def = catalog.get(piece.getConstructionId());
            assertNotNull(def);
            assertEquals(def.getMaterials().size(), piece.getMaterials().size(), piece.getRole().name());
            assertFalse(piece.getMaterials().isEmpty(), piece.getRole().name());
        }
    }

    /** What the player set on a piece has to end up in that piece's own building file. */
    @Test
    void savingWritesTheCostSetOnEachPiece() {
        PlotCreatorDraft draft = loadedCoreDraft();
        PlotCreatorWallPieceDraft piece = draft.getWallPieces().get(0);
        piece.getMaterials().clear();
        piece.getMaterials().add(MaterialRequirement.ofItem("Rock_Stone_Brick", 64));
        Vector3i anchor = piece.getAnchor();
        assertNotNull(anchor);
        Map<String, Object> json =
            PlotCreatorWallStyleWriter.buildingMap(
                draft, piece, "plot_wall_core_segment", "plot_wall_core_segment.prefab.json", "core", anchor
            );
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> materials = (List<Map<String, Object>>) json.get("materials");
        assertNotNull(materials);
        assertEquals(1, materials.size());
        assertEquals("Rock_Stone_Brick", materials.get(0).get("itemId"));
        assertEquals(64, materials.get(0).get("count"));

        piece.getMaterials().clear();
        assertFalse(
            PlotCreatorWallStyleWriter.buildingMap(
                draft, piece, "plot_wall_core_segment", "plot_wall_core_segment.prefab.json", "core", anchor
            ).containsKey("materials")
        );
    }

    @Test
    void everyExpectedJoinSpotSurvivesTheRoundTrip() {
        PlotCreatorDraft draft = loadedCoreDraft();
        for (PlotCreatorWallPieceDraft piece : draft.getWallPieces()) {
            assertEquals(piece.getRole().connectionCount(), piece.getConnections().size(), piece.getRole().name());
            for (WallCardinal face : piece.getRole().connectionFaces()) {
                assertNotNull(piece.connectionOn(face), piece.getRole() + " " + face);
            }
            assertTrue(piece.isComplete(), piece.getRole().name());
        }
    }

    /** The shipped wall pasted piece by piece, the way the building editor lays it out before handing it to the wizard. */
    @Nonnull
    private static PlotCreatorDraft loadedCoreDraft() {
        ConstructionCatalog catalog = WallStyleFixtures.coreConstructionCatalog();
        WallStyle style = WallStyleFixtures.coreStyle();
        String baseId = PlotCreatorWallStyleLoader.baseIdForStyle(style);
        assertNotNull(baseId);
        List<PlotCreatorWallStyleLoader.PastedPiece> pasted = new ArrayList<>();
        int cursorX = 100;
        for (WallStyle.Piece piece : style.piecesInOrder()) {
            Vector3i min = piece.definition().boundsMinLocal();
            Vector3i max = piece.definition().boundsMaxLocal();
            int signX = cursorX - min.x;
            pasted.add(
                new PlotCreatorWallStyleLoader.PastedPiece(
                    piece.role(), piece.constructionId(), new Vector3i(signX, 64, -40)
                )
            );
            cursorX = signX + max.x + 7;
        }
        PlotCreatorDraft draft = new PlotCreatorDraft();
        PlotCreatorWallStyleLoader.loadIntoDraft(draft, catalog, style, baseId, pasted);
        assertTrue(PlotCreatorWallStyleIds.isSavingLoadedStyle(draft, baseId));
        return draft;
    }
}
