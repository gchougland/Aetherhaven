package com.hexvane.aetherhaven.plotcreator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.joml.Vector3i;

@Tag("crossmod")
class PlotCreatorSpotPlacementTest {

    @Test
    void prefabYawFromWorld_subtractsPlotRotation() {
        PlotCreatorDraft draft = new PlotCreatorDraft();
        draft.setRotationYaw("Ninety");
        float worldYaw = (float) (Math.PI / 2.0);
        assertEquals(0f, PlotCreatorSpotPlacement.prefabYawFromWorld(draft, worldYaw), 0.001f);
    }

    @Test
    void prefabYawFromWorld_nonePlacementPreservesYaw() {
        PlotCreatorDraft draft = new PlotCreatorDraft();
        draft.setRotationYaw("None");
        float worldYaw = 1.25f;
        assertEquals(worldYaw, PlotCreatorSpotPlacement.prefabYawFromWorld(draft, worldYaw), 0.001f);
    }

    @Test
    void matchesStandSpawnClick_sameStandCell() {
        Vector3i stand = new Vector3i(10, 65, 20);
        assertTrue(PlotCreatorSpotPlacement.matchesStandSpawnClick(null, stand, stand));
    }

    @Test
    void horizontalForwardLocal_ninetyFacesNegativeX() {
        int[] forward = PlotCreatorPoiInteractionTarget.horizontalForwardLocal((float) (Math.PI / 2.0));
        assertEquals(-1, forward[0]);
        assertEquals(0, forward[1]);
        assertEquals(0, forward[2]);
    }

    @Test
    void applyFacingFromSeatFacing_setsOppositeStandOffset() {
        PlotCreatorDraft draft = new PlotCreatorDraft();
        draft.setRotationYaw("None");
        PlotCreatorPoiDraft poi = new PlotCreatorPoiDraft();
        int[] poiLocal = new int[] {5, 64, 8};
        PlotCreatorPoiInteractionTarget.applyFromSeatFacing(draft, 0f, poiLocal, poi);
        assertEquals(5, poi.getInteractionTargetLocalX());
        assertEquals(64, poi.getInteractionTargetLocalY());
        assertEquals(9, poi.getInteractionTargetLocalZ());
        assertEquals(0f, poi.getInteractionTargetYawDegrees(), 0.001f);
    }
}
