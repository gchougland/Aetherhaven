package com.hexvane.aetherhaven.plotcreator;

import javax.annotation.Nonnull;
import org.joml.Vector3i;

/** Places the plot sign anchor at the horizontal center of the marked build bounds. */
final class PlotCreatorAutoAnchor {
    private PlotCreatorAutoAnchor() {}

    static void applyCenter(@Nonnull PlotCreatorDraft draft) {
        if (!PlotCreatorAnchorRules.hasBounds(draft)) {
            return;
        }
        Vector3i min = draft.boundsMin();
        Vector3i max = draft.boundsMax();
        Vector3i center =
            new Vector3i((min.x + max.x) / 2, min.y, (min.z + max.z) / 2);
        Vector3i previous = draft.getPlotAnchor();
        if (draft.isBuildingEditorMode() && previous != null && !previous.equals(center)) {
            BuildingEditorSessionStarter.rebaseLocalsForNewPlotSign(draft, previous, center);
        }
        draft.setPlotAnchor(center);
        draft.setPrefabOriginMin(new Vector3i(min));
        PlotCreatorLocalCoords.recomputeAnchorOffset(draft);
    }
}
