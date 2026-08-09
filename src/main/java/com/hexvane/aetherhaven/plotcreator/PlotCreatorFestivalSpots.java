package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.festival.FestivalDefinition;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * Converts between festival stand spots and the plot creator's work POI drafts, so festival spots can be marked with
 * the same important spots flow every other building uses.
 */
public final class PlotCreatorFestivalSpots {
    private PlotCreatorFestivalSpots() {}

    @Nonnull
    public static PlotCreatorPoiDraft toPoiDraft(@Nonnull FestivalDefinition.SpotRow spot) {
        PlotCreatorPoiDraft poi = new PlotCreatorPoiDraft();
        poi.setLocal(spot.getLocalX(), spot.getLocalY(), spot.getLocalZ());
        poi.setTags(List.of("WORK"));
        poi.setCapacity(1);
        poi.setInteractionKind("WORK_SURFACE");
        poi.setWorkResidentKind(spot.getResidentKind());
        poi.setInteractionTargetYawDegrees(spot.getYawDegrees());
        return poi;
    }

    /** Every marked work POI in the draft, as festival spot rows in prefab-local space. */
    @Nonnull
    public static List<FestivalDefinition.SpotRow> fromDraft(@Nonnull PlotCreatorDraft draft) {
        List<FestivalDefinition.SpotRow> out = new ArrayList<>();
        for (PlotCreatorPoiDraft poi : draft.getPois()) {
            String kind = poi.getWorkResidentKind();
            if (kind == null) {
                continue;
            }
            float yaw = poi.getInteractionTargetYawDegrees() != null ? poi.getInteractionTargetYawDegrees() : 0.0f;
            out.add(FestivalDefinition.SpotRow.of(kind, poi.getLocalX(), poi.getLocalY(), poi.getLocalZ(), yaw));
        }
        return out;
    }
}
