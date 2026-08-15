package com.hexvane.aetherhaven.wall;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.gson.GsonBuilder;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.placement.PlotPlacementHeights;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import javax.annotation.Nonnull;
import org.joml.Vector3i;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The wand measures each new piece from the stored sign of the one before it, so a placed wall piece has to keep the
 * sign exactly where the preview put it. Re-centring it on the footprint used to shift every piece with an even span,
 * which showed up in game as a block of gap and a block of sideways drift.
 */
@Tag("wall-placement")
class WallPieceAnchorRoundTripTest {
    @Test
    void placingAWallPieceKeepsTheSignOnThePreviewAnchor() {
        ConstructionDefinition def = definition(0, 0, 0);
        Vector3i preview = new Vector3i(37, 64, -102);
        for (Rotation yaw : Rotation.values()) {
            PlotPlacementHeights.ResolvedPlacement resolved =
                PlotPlacementHeights.resolveWallPiece(preview, def, yaw);
            assertEquals(preview, resolved.signCell(), "sign moved at yaw " + yaw);
            assertEquals(
                def.resolvePrefabAnchorWorld(preview, yaw), resolved.buildingPrefabAnchor(), "anchor at yaw " + yaw
            );
        }
    }

    @Test
    void wallPiecePlacementNeverMovesTheSignVertically() {
        ConstructionDefinition def = definition(0, AetherhavenConstants.PLOT_SIGN_BLOCK_Y_ABOVE_LOGICAL_ANCHOR, 0);
        for (int y = 20; y <= 200; y += 45) {
            Vector3i preview = new Vector3i(-8, y, 12);
            assertEquals(
                y, PlotPlacementHeights.resolveWallPiece(preview, def, Rotation.None).signCell().y
            );
        }
    }

    /**
     * A style authored in the plot creator writes its build box and join spots measured from the plot sign, so its
     * anchor offset has to put the prefab back on that same cell.
     */
    @Test
    void anAuthoredPieceOffsetPastesTheShapeBackOnTheSign() {
        ConstructionDefinition def = definition(0, AetherhavenConstants.PLOT_SIGN_BLOCK_Y_ABOVE_LOGICAL_ANCHOR, 0);
        Vector3i sign = new Vector3i(5, 70, -5);
        assertEquals(sign, def.resolvePrefabAnchorWorld(sign, Rotation.None));
    }

    @Test
    void theShippedWallPiecesAllKeepTheirJoinSpotsInsideTheirOwnBox() {
        WallStyle style = WallStyleFixtures.coreStyle();
        for (WallStyle.Piece piece : style.piecesInOrder()) {
            WallPieceDefinition def = piece.definition();
            Vector3i min = def.boundsMinLocal();
            Vector3i max = def.boundsMaxLocal();
            for (WallCardinal face : def.localFaces()) {
                Vector3i local = def.localConnection(face);
                assertNotNull(local, piece.constructionId() + " " + face);
                assertEquals(
                    true,
                    local.x >= min.x && local.x <= max.x && local.z >= min.z && local.z <= max.z,
                    piece.constructionId() + " join spot on " + face + " sits outside its box"
                );
            }
        }
    }

    @Nonnull
    private static ConstructionDefinition definition(int ox, int oy, int oz) {
        String json =
            "{\"id\":\"plot_wall_test_segment\",\"displayName\":\"Test wall\","
                + "\"prefabPath\":\"test.prefab.json\",\"plotAnchorOffset\":["
                + ox
                + ","
                + oy
                + ","
                + oz
                + "]}";
        ConstructionDefinition def = new GsonBuilder().create().fromJson(json, ConstructionDefinition.class);
        assertNotNull(def);
        return def;
    }
}
