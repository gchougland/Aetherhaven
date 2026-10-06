package com.hexvane.aetherhaven.plotcreator;

import java.util.List;
import org.joml.Vector3i;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("construction")
class HunterStorageRequirementsTest {
    private PlotCreatorDraft draft() {
        var draft=new PlotCreatorDraft();
        draft.setEditingConstructionId("plot_monster_hunter");
        draft.setBuildingEditorMode(true);
        draft.setKinds(List.of(PlotBuildingKind.WORK));
        draft.setPlotAnchor(new Vector3i(0,0,0));
        draft.setCornerFirst(new Vector3i(-5,-1,-5));
        draft.setCornerSecond(new Vector3i(5,5,5));
        return draft;
    }
    @Test void existingLodgeRequiresStorageEvenWhenItWasNotSelected() {
        var draft=draft();
        assertTrue(PlotBuildingKindRequirements.defaultRequirements(draft,null).stream()
            .anyMatch(r->r.type()==PlotCreatorSubstepType.PRODUCTION_STORAGE));
        draft.getSelectedSpots().add(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.MANAGEMENT_BLOCK,1));
        PlotCreatorProductionRules.enforceSelectedSpots(draft);
        PlotCreatorProductionRules.enforceSelectedSpots(draft);
        assertEquals(1,draft.getSelectedSpots().stream().filter(s->s.type()==PlotCreatorSubstepType.PRODUCTION_STORAGE).count());
    }
    @Test void savingNeedsAMarkedStorageInsideTheCurrentBounds() {
        var draft=draft();
        assertEquals("huntStorageRequired",PlotCreatorProductionRules.validationError(draft,null));
        draft.setProductionStorageLocalPos(new int[]{8,0,0});
        assertEquals("huntStorageRequired",PlotCreatorProductionRules.validationError(draft,null));
        draft.setProductionStorageLocalPos(new int[]{2,0,2});
        assertNull(PlotCreatorProductionRules.validationError(draft,null));
    }
}
