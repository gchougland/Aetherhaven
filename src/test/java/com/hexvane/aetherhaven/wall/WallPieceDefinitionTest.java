package com.hexvane.aetherhaven.wall;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import org.joml.Vector3i;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("wall-placement")
class WallPieceDefinitionTest {
    @Test
    void localDirectionsRotateTheSameWayHytaleRotatesPrefabs() {
        assertEquals(WallCardinal.NORTH, WallCardinal.rotated(WallCardinal.NORTH, 0));
        assertEquals(WallCardinal.WEST, WallCardinal.rotated(WallCardinal.NORTH, 1));
        assertEquals(WallCardinal.SOUTH, WallCardinal.rotated(WallCardinal.NORTH, 2));
        assertEquals(WallCardinal.EAST, WallCardinal.rotated(WallCardinal.NORTH, 3));
        assertEquals(WallCardinal.EAST, WallCardinal.rotated(WallCardinal.SOUTH, 1));
    }

    @Test
    void connectionOffsetsRotateWithTheirFace() {
        WallPieceDefinition segment = WallStyleFixtures.coreStyle().piece(WallPieceRole.SEGMENT).definition();
        assertEquals(new Vector3i(0, 0, -7), segment.worldConnectionOffset(WallCardinal.NORTH, 0));
        assertEquals(new Vector3i(0, 0, 8), segment.worldConnectionOffset(WallCardinal.SOUTH, 0));
        assertEquals(new Vector3i(-7, 0, 0), segment.worldConnectionOffset(WallCardinal.WEST, 1));
        assertEquals(new Vector3i(8, 0, 0), segment.worldConnectionOffset(WallCardinal.EAST, 1));
        assertNull(segment.worldConnectionOffset(WallCardinal.NORTH, 1));
    }

    @Test
    void footprintMatchesTheAuthoredBounds() {
        WallPieceDefinition segment = WallStyleFixtures.coreStyle().piece(WallPieceRole.SEGMENT).definition();
        assertArrayEqualsXZ(new int[] {-2, -7, 2, 8}, segment.worldBoundsXZ(new Vector3i(0, 0, 0), 0));
        assertArrayEqualsXZ(new int[] {-7, -2, 8, 2}, segment.worldBoundsXZ(new Vector3i(0, 0, 0), 1));
    }

    @Test
    void coreStyleProvidesEveryRole() {
        WallStyle style = WallStyleFixtures.coreStyle();
        assertTrue(style.isComplete());
        assertEquals("plot_wall_segment", style.piece(WallPieceRole.SEGMENT).constructionId());
        assertEquals("plot_wall_gate", style.piece(WallPieceRole.GATE).constructionId());
        assertEquals("plot_wall_tower_endcap_s", style.piece(WallPieceRole.TOWER_END).constructionId());
        assertEquals("plot_wall_tower_eastdoor_ns", style.piece(WallPieceRole.TOWER_STRAIGHT).constructionId());
        assertEquals("plot_wall_tower_outercorner_se", style.piece(WallPieceRole.TOWER_CORNER).constructionId());
    }

    @Test
    void towersResolveFromTheFacesTheyNeedToOpen() {
        WallStyle style = WallStyleFixtures.coreStyle();
        for (WallCardinal face : WallCardinal.values()) {
            WallStyle.ResolvedPiece end = style.resolveTower(EnumSet.of(face));
            assertNotNull(end);
            assertEquals("plot_wall_tower_endcap_s", end.constructionId());
            assertEquals(EnumSet.of(face), end.definition().worldFaces(end.rotationSteps()));
        }
        for (WallCardinal face : WallCardinal.values()) {
            EnumSet<WallCardinal> straight = EnumSet.of(face, face.opposite());
            WallStyle.ResolvedPiece through = style.resolveTower(straight);
            assertNotNull(through);
            assertEquals("plot_wall_tower_eastdoor_ns", through.constructionId());
            assertEquals(straight, through.definition().worldFaces(through.rotationSteps()));

            EnumSet<WallCardinal> corner = EnumSet.of(face, face.rotateCw90());
            WallStyle.ResolvedPiece turn = style.resolveTower(corner);
            assertNotNull(turn);
            assertEquals("plot_wall_tower_outercorner_se", turn.constructionId());
            assertEquals(corner, turn.definition().worldFaces(turn.rotationSteps()));
        }
    }

    @Test
    void piecesMarkedUnselectableAreNeverPicked() {
        WallStyle style = WallStyleFixtures.coreStyle();
        assertNotNull(style.pieceByConstructionId("plot_wall_tower_eastdoor_sw"));
        for (WallPieceRole role : WallPieceRole.AUTHORING_ORDER) {
            WallStyle.Piece piece = style.piece(role);
            assertNotNull(piece);
            assertFalse("plot_wall_tower_eastdoor_sw".equals(piece.constructionId()));
        }
    }

    private static void assertArrayEqualsXZ(int[] expected, int[] actual) {
        assertEquals(expected[0], actual[0], "minX");
        assertEquals(expected[1], actual[1], "minZ");
        assertEquals(expected[2], actual[2], "maxX");
        assertEquals(expected[3], actual[3], "maxZ");
    }
}
