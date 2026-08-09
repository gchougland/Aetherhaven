package com.hexvane.aetherhaven.plotcreator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.festival.CustomFestivalPaths;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("crossmod")
final class PlotCreatorFestivalFlowTest {
    @Test
    void festivalStepFollowsTheKindStepAndSkipsVariantAndMaterials() {
        PlotCreatorDraft draft = festivalDraft();

        List<PlotCreatorStep> order = PlotCreatorService.stepOrder(draft);

        assertEquals(PlotCreatorStep.FESTIVAL, order.get(order.indexOf(PlotCreatorStep.KIND) + 1));
        assertTrue(order.contains(PlotCreatorStep.IMPORTANT_SPOTS));
        assertTrue(order.contains(PlotCreatorStep.CONFIGURE));
        assertTrue(order.contains(PlotCreatorStep.PREFAB_SAVE));
        assertFalse(order.contains(PlotCreatorStep.VARIANT));
        assertFalse(order.contains(PlotCreatorStep.MATERIALS));
    }

    @Test
    void festivalIsExclusiveWithOtherBuildingTypes() {
        assertTrue(PlotBuildingKind.FESTIVAL.isExclusiveKind());
        assertTrue(PlotBuildingKind.selectableKinds(true, List.of()).contains(PlotBuildingKind.FESTIVAL));
    }

    @Test
    void pickingAnotherBuildingTypeDropsTheFestivalSelection() {
        PlotCreatorDraft draft = festivalDraft();
        draft.setFestivalPicked(true);
        draft.setFestivalSizeLocked(true);

        draft.setKinds(List.of(PlotBuildingKind.DECORATION));
        draft.clearFestivalSelection();

        assertFalse(draft.isFestivalMode());
        assertFalse(draft.isFestivalSizeLocked());
        assertNull(draft.getFestivalId());
    }

    @Test
    void settingsRejectTheBaseFestivalSquareName() {
        PlotCreatorDraft draft = festivalDraft();
        draft.setDisplayName("Festival Square");

        assertEquals("festivalIdReserved", PlotCreatorFestivalSettings.applyInput(draft));
    }

    @Test
    void settingsNameTheFestivalFromItsDisplayNameAndPointAtTheFestivalPrefabFolder() {
        PlotCreatorDraft draft = festivalDraft();
        draft.setDisplayName("Lantern Night");
        draft.setFestivalSeasonInput("Autumn");
        draft.setFestivalDayInput("21");
        draft.setFestivalStartHourInput("18");
        draft.setFestivalEndHourInput("4");

        assertNull(PlotCreatorFestivalSettings.applyInput(draft));
        assertEquals("lantern_night", draft.getFestivalId());
        assertEquals(CustomFestivalPaths.prefabFileName("lantern_night"), draft.getPrefabFileName());
        assertEquals("AUTUMN", draft.getFestivalSeason());
        assertEquals(21, draft.getFestivalDayOfSeason());
        assertEquals(18, draft.getFestivalStartHour());
        assertEquals(4, draft.getFestivalEndHour());
    }

    @Test
    void settingsRejectBadDaysSeasonsAndHours() {
        PlotCreatorDraft draft = festivalDraft();
        draft.setDisplayName("Lantern Night");

        draft.setFestivalSeasonInput("Harvest");
        assertEquals("festivalSeason", PlotCreatorFestivalSettings.applyInput(draft));

        draft.setFestivalSeasonInput("Autumn");
        draft.setFestivalDayInput("29");
        assertEquals("festivalDay", PlotCreatorFestivalSettings.applyInput(draft));

        draft.setFestivalDayInput("21");
        draft.setFestivalStartHourInput("25");
        assertEquals("festivalHours", PlotCreatorFestivalSettings.applyInput(draft));

        draft.setFestivalStartHourInput("18");
        draft.setFestivalEndHourInput("18");
        assertEquals("festivalSameHours", PlotCreatorFestivalSettings.applyInput(draft));
    }

    @Test
    void allDayFestivalsIgnoreTheHourFields() {
        PlotCreatorDraft draft = festivalDraft();
        draft.setDisplayName("Quiet Day");
        draft.setFestivalAllDay(true);
        draft.setFestivalStartHourInput("nonsense");
        draft.setFestivalEndHourInput("nonsense");

        assertNull(PlotCreatorFestivalSettings.applyInput(draft));
        assertTrue(draft.isFestivalAllDay());
    }

    private static PlotCreatorDraft festivalDraft() {
        PlotCreatorDraft draft = new PlotCreatorDraft();
        draft.setKinds(List.of(PlotBuildingKind.FESTIVAL));
        return draft;
    }
}
