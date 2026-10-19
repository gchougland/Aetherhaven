package com.hexvane.aetherhaven.plotcreator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.festival.CustomFestivalPaths;
import com.hexvane.aetherhaven.festival.FestivalDefinition;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("crossmod")
final class PlotCreatorFestivalFlowTest {
    @Test
    void freshFestivalPickCreatesTheFullFootprintWithoutAManualBoundsStep() {
        PlotCreatorDraft draft = festivalDraft();
        assertFalse(PlotCreatorService.stepOrder(draft).contains(PlotCreatorStep.BOUNDS));
        var center = BuildingEditorFestivalSessionStarter.pasteCenterNearPlayer(new org.joml.Vector3d(10.5, 80, 20.5), 0);
        PlotCreatorFestivalDraftSetup.initializeFootprint(draft, center);
        assertEquals(new org.joml.Vector3i(10, 80, 14), draft.getPlotAnchor());
        var expected = com.hexvane.aetherhaven.festival.FestivalPrefabSize.footprintAt(center,
            com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation.None);
        assertEquals(new org.joml.Vector3i(expected.getMinX(), expected.getMinY(), expected.getMinZ()), draft.boundsMin());
        assertEquals(new org.joml.Vector3i(expected.getMaxX(), expected.getMaxY(), expected.getMaxZ()), draft.boundsMax());
        assertTrue(draft.isFestivalSizeLocked());
    }

    @Test
    void changingFestivalKeepsItsAuthoringLocationEvenWhenThePlayerMoves() {
        PlotCreatorDraft draft = festivalDraft();
        PlotCreatorFestivalDraftSetup.initializeFootprint(draft, new org.joml.Vector3i(-40, 80, -30));
        var before = new org.joml.Vector3i(draft.boundsMin());
        PlotCreatorFestivalDraftSetup.initializeFootprint(draft, new org.joml.Vector3i(200, 100, 300));
        assertEquals(before, draft.boundsMin());
        assertEquals(new org.joml.Vector3i(-40, 80, -30), draft.getPlotAnchor());
    }

    @Test
    void existingVariantBoundsKeepTheirOriginAndSnapToTheSharedSize() {
        PlotCreatorDraft draft = new PlotCreatorDraft();
        PlotCreatorBoundsValidation.commitCorners(draft, new org.joml.Vector3i(-100, 30, 50), new org.joml.Vector3i(-90, 40, 60));
        PlotCreatorFestivalDraftSetup.initializeFootprint(draft, null);
        assertEquals(new org.joml.Vector3i(-100, 30, 50), draft.boundsMin());
        assertEquals(new org.joml.Vector3i(-71, 84, 79), draft.boundsMax());
        assertEquals(draft.boundsMin(), draft.getPrefabOriginMin());
    }

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

    @Test
    void festivalExportOmitsTownRecordsShelfEvenWhenStartingFromTheBasePrefab() {
        PlotCreatorDraft draft = festivalDraft();
        draft.setPrefabPath(CustomFestivalPaths.BASE_PREFAB_PATH);

        assertTrue(PlotCreatorPrefabExporter.shouldOmitManagementBlockFromFestivalExport(draft));
    }

    @Test
    void carnivalPrefabExportOmitsTownRecordsShelf() {
        PlotCreatorDraft draft = festivalDraft();
        draft.setPrefabPath("Festivals/Festival_Carnival.prefab.json");
        draft.setLockedPrefabPathKey("Festivals/Festival_Carnival.prefab.json");

        assertTrue(PlotCreatorPrefabExporter.shouldOmitManagementBlockFromFestivalExport(draft));
    }

    @Test
    void festivalFolderPrefabWithoutFestivalModeOmitsTownRecordsShelf() {
        PlotCreatorDraft draft = new PlotCreatorDraft();
        draft.setPrefabPath("Festivals/Festival_Carnival.prefab.json");

        assertTrue(PlotCreatorPrefabExporter.shouldOmitManagementBlockFromFestivalExport(draft));
    }

    @Test
    void pickingAListedFestivalMakesALookInsteadOfEditing() {
        FestivalDefinition existing = new Gson().fromJson(
            """
            {
              "id": "carnival",
              "displayName": "Carnival Festival",
              "prefabPath": "Festivals/Festival_Carnival.prefab.json",
              "season": "Summer",
              "dayOfSeason": 21,
              "mechanicId": "carnival"
            }
            """,
            FestivalDefinition.class
        );
        PlotCreatorDraft draft = festivalDraft();
        PlotCreatorFestivalDraftSetup.applyFestivalFields(draft, existing, true);

        assertNull(draft.getEditingFestivalId());
        assertEquals("carnival", draft.getCountsAsFestivalId());
        assertTrue(draft.isFestivalLookMode());
        assertNull(draft.getLockedPrefabPathKey());
        assertNull(draft.getFestivalId());
        assertNull(draft.getStyleId());
    }

    @Test
    void editingALookKeepsItsStyle() {
        FestivalDefinition existing = new Gson().fromJson(
            """
            {
              "id": "carnival_neon",
              "displayName": "Neon Carnival",
              "prefabPath": "Festivals/Festival_carnival_neon.prefab.json",
              "season": "Summer",
              "dayOfSeason": 21,
              "festivalVariant": true,
              "countsAsFestivalId": "carnival",
              "styleId": "Neon"
            }
            """,
            FestivalDefinition.class
        );
        PlotCreatorDraft draft = festivalDraft();
        PlotCreatorFestivalDraftSetup.applyFestivalFields(draft, existing);

        assertEquals("carnival", draft.getCountsAsFestivalId());
        assertEquals("neon", draft.getStyleId());
    }

    @Test
    void festivalSquareVariantExportKeepsTownRecordsShelf() {
        PlotCreatorDraft draft = new PlotCreatorDraft();
        draft.setConstructionId("plot_my_square");
        draft.setCountsAsConstructionIds(List.of("plot_festival_square"));
        draft.setPrefabPath("Festivals/Festival_plot_my_square.prefab.json");

        assertTrue(draft.countsAsFestivalSquare());
        assertFalse(PlotCreatorPrefabExporter.shouldOmitManagementBlockFromFestivalExport(draft));
    }

    @Test
    void everydayFestivalSquareExportKeepsTownRecordsShelf() {
        PlotCreatorDraft draft = new PlotCreatorDraft();
        draft.setConstructionId("plot_festival_square");
        draft.setPrefabPath(CustomFestivalPaths.BASE_PREFAB_PATH);

        assertFalse(PlotCreatorPrefabExporter.shouldOmitManagementBlockFromFestivalExport(draft));
    }

    @Test
    void ordinaryBuildingExportKeepsTownRecordsShelf() {
        PlotCreatorDraft draft = new PlotCreatorDraft();
        draft.setConstructionId("plot_inn");
        draft.setPrefabPath("plot_inn.prefab.json");

        assertFalse(PlotCreatorPrefabExporter.shouldOmitManagementBlockFromFestivalExport(draft));
    }

    private static PlotCreatorDraft festivalDraft() {
        PlotCreatorDraft draft = new PlotCreatorDraft();
        draft.setKinds(List.of(PlotBuildingKind.FESTIVAL));
        return draft;
    }
}
