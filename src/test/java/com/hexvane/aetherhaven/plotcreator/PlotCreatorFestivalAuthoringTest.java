package com.hexvane.aetherhaven.plotcreator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hexvane.aetherhaven.festival.FestivalDefinition;
import com.hexvane.aetherhaven.festival.FestivalRaceLanes;
import com.hexvane.aetherhaven.festival.NewLifeFestivalMechanic;
import com.hexvane.aetherhaven.festival.pigrace.PigRaceLanes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("crossmod")
final class PlotCreatorFestivalAuthoringTest {
    @TempDir
    Path tempDir;

    @Test
    void mechanicDefaultsAddMerchantCenterpieceAndRaceSpots() {
        PlotCreatorDraft draft = new PlotCreatorDraft();
        draft.setKinds(List.of(PlotBuildingKind.FESTIVAL));
        draft.setFestivalMechanicId(PigRaceLanes.MECHANIC_ID);

        PlotCreatorFestivalMechanicDefaults.ensureRequiredSelectedSpots(draft);

        assertTrue(
            draft.getSelectedSpots().stream()
                .anyMatch(
                    s ->
                        s.type() == PlotCreatorSubstepType.FESTIVAL_NPC
                            && PlotCreatorFestivalNpcRoles.PIG_RACE_MERCHANT.equals(s.workResidentKind())
                )
        );
        assertTrue(
            draft.getSelectedSpots().stream()
                .anyMatch(s -> s.type() == PlotCreatorSubstepType.FESTIVAL_TOURIST_SPOT)
        );
        assertTrue(
            draft.getSelectedSpots().stream().anyMatch(s -> s.type() == PlotCreatorSubstepType.FESTIVAL_RACE_LANE)
        );
    }

    @Test
    void newLifeDefaultsAddSeedSellerAndCenterpiece() {
        PlotCreatorDraft draft = new PlotCreatorDraft();
        draft.setKinds(List.of(PlotBuildingKind.FESTIVAL));
        draft.setFestivalMechanicId(NewLifeFestivalMechanic.MECHANIC_ID);

        PlotCreatorFestivalMechanicDefaults.ensureRequiredSelectedSpots(draft);

        assertTrue(
            draft.getSelectedSpots().stream()
                .anyMatch(
                    s ->
                        s.type() == PlotCreatorSubstepType.FESTIVAL_NPC
                            && PlotCreatorFestivalNpcRoles.SEED_SELLER.equals(s.workResidentKind())
                )
        );
        assertTrue(
            draft.getSelectedSpots().stream()
                .anyMatch(s -> s.type() == PlotCreatorSubstepType.FESTIVAL_CENTERPIECE)
        );
    }

    @Test
    void festivalJsonWriterWritesNpcTouristCenterpieceAndRaceLanes() throws Exception {
        PlotCreatorDraft draft = new PlotCreatorDraft();
        draft.setKinds(List.of(PlotBuildingKind.FESTIVAL));
        draft.setFestivalId("lantern_night");
        draft.setDisplayName("Lantern Night");
        draft.setPrefabPath("Festivals/Festival_Lantern_Night.prefab.json");
        draft.setFestivalSeason("SPRING");
        draft.setFestivalDayOfSeason(10);
        draft.setFestivalStartHour(8);
        draft.setFestivalEndHour(20);
        draft.setFestivalMechanicId(PigRaceLanes.MECHANIC_ID);
        draft.getFestivalNpcs().add(
            FestivalDefinition.NpcRow.of(PlotCreatorFestivalNpcRoles.PIG_RACE_MERCHANT, 1, 6, 2, 90f)
        );
        draft.getFestivalTouristSpots().add(FestivalDefinition.TouristSpotRow.of(-3, 6, 4, 0f));
        draft.setFestivalCenterpieceLocal(new int[] {0, 6, 0});
        draft.getFestivalRaceLanes().add(
            FestivalDefinition.RaceLaneRow.of("Aetherhaven_Festival_Pig_Race_Pink", -7, 6, -8, -7, 6, 8)
        );
        draft.getPois().add(PlotCreatorFestivalSpots.toPoiDraft(FestivalDefinition.SpotRow.of("elder", 2, 6, 2, 180f)));

        Path out = tempDir.resolve("lantern_night.json");
        PlotCreatorFestivalJsonWriter.writeFestival(out, draft, null);

        JsonObject root = new Gson().fromJson(Files.readString(out), JsonObject.class);
        assertEquals("pig_race", root.get("mechanicId").getAsString());
        assertTrue(root.has("npcs"));
        assertTrue(root.has("touristSpots"));
        assertTrue(root.has("centerpieceLocal"));
        assertTrue(root.has("raceLanes"));
        assertTrue(root.has("spots"));
        assertEquals(1, root.getAsJsonArray("raceLanes").size());
    }

    @Test
    void raceLaneResolverUsesFestivalJsonThenFallsBack() {
        assertEquals(4, FestivalRaceLanes.resolve(null).size());

        FestivalDefinition def = new Gson().fromJson(
            """
            {
              "id": "custom_race",
              "raceLanes": [
                {
                  "npcRoleId": "Aetherhaven_Festival_Pig_Race_Pink",
                  "startLocalX": 1, "startLocalY": 6, "startLocalZ": 2,
                  "finishLocalX": 1, "finishLocalY": 6, "finishLocalZ": 10
                }
              ]
            }
            """,
            FestivalDefinition.class
        );
        List<PigRaceLanes.Lane> lanes = FestivalRaceLanes.resolve(def);
        assertEquals(1, lanes.size());
        assertEquals(1, lanes.get(0).startLocalX());
        assertEquals(10, lanes.get(0).finishLocalZ());
    }

    @Test
    void applyFestivalFieldsSeedsFullSetup() {
        FestivalDefinition existing = new Gson().fromJson(
            """
            {
              "id": "pig_race",
              "displayName": "Pig Racing Festival",
              "prefabPath": "Festivals/Festival_Pig_Race.prefab.json",
              "season": "Spring",
              "dayOfSeason": 20,
              "mechanicId": "pig_race",
              "spots": [
                { "residentKind": "elder", "localX": 0, "localY": 6, "localZ": 1, "yawDegrees": 0 }
              ],
              "npcs": [
                {
                  "npcRoleId": "Aetherhaven_Festival_Pig_Race_Merchant",
                  "localX": -4, "localY": 6, "localZ": -12, "yawDegrees": 180
                }
              ],
              "touristSpots": [
                { "localX": -13, "localY": 6, "localZ": -5, "yawDegrees": 90 }
              ],
              "raceLanes": [
                {
                  "npcRoleId": "Aetherhaven_Festival_Pig_Race_Pink",
                  "startLocalX": -7, "startLocalY": 6, "startLocalZ": -8,
                  "finishLocalX": -7, "finishLocalY": 6, "finishLocalZ": 8
                }
              ]
            }
            """,
            FestivalDefinition.class
        );
        PlotCreatorDraft draft = new PlotCreatorDraft();
        draft.setKinds(List.of(PlotBuildingKind.FESTIVAL));
        PlotCreatorFestivalDraftSetup.applyFestivalFields(draft, existing);

        assertEquals("pig_race", draft.getFestivalMechanicId());
        assertEquals(1, draft.getFestivalNpcs().size());
        assertEquals(1, draft.getFestivalTouristSpots().size());
        assertEquals(1, draft.getFestivalRaceLanes().size());
        assertFalse(draft.getSelectedSpots().isEmpty());
        assertNull(draft.getFestivalCenterpieceLocal());
    }

    @Test
    void settingsAcceptFestivalActivityLabels() {
        PlotCreatorDraft draft = new PlotCreatorDraft();
        draft.setKinds(List.of(PlotBuildingKind.FESTIVAL));
        draft.setDisplayName("Lantern Night");
        draft.setFestivalSeasonInput("Spring");
        draft.setFestivalDayInput("5");
        draft.setFestivalStartHourInput("8");
        draft.setFestivalEndHourInput("20");
        draft.setFestivalMechanicInput("Pig Racing");

        assertNull(PlotCreatorFestivalSettings.applyInput(draft));
        assertEquals(PigRaceLanes.MECHANIC_ID, draft.getFestivalMechanicId());
        assertEquals("Pig Racing", draft.getFestivalMechanicInput());
    }

    @Test
    void hallowsEveDefaultsAddMerchantMazeStartCenterpieceAndOrbs() {
        PlotCreatorDraft draft = new PlotCreatorDraft();
        draft.setKinds(List.of(PlotBuildingKind.FESTIVAL));
        draft.setFestivalMechanicId(com.hexvane.aetherhaven.festival.hallowseve.HallowsEveIds.MECHANIC_ID);

        PlotCreatorFestivalMechanicDefaults.ensureRequiredSelectedSpots(draft);

        assertTrue(
            draft.getSelectedSpots().stream()
                .anyMatch(
                    s ->
                        s.type() == PlotCreatorSubstepType.FESTIVAL_NPC
                            && PlotCreatorFestivalNpcRoles.HALLOWS_EVE_MERCHANT.equals(s.workResidentKind())
                )
        );
        assertTrue(
            draft.getSelectedSpots().stream().anyMatch(s -> s.type() == PlotCreatorSubstepType.FESTIVAL_CENTERPIECE)
        );
        assertTrue(
            draft.getSelectedSpots().stream().anyMatch(s -> s.type() == PlotCreatorSubstepType.FESTIVAL_MAZE_START)
        );
        assertTrue(
            draft.getSelectedSpots().stream()
                .anyMatch(
                    s ->
                        s.type() == PlotCreatorSubstepType.FESTIVAL_MAZE_ORB_SPAWN
                            && s.minCount() == PlotCreatorFestivalMechanicDefaults.DEFAULT_HALLOWS_EVE_ORB_SPAWNS
                )
        );
        assertTrue(
            draft.getSelectedSpots().stream().anyMatch(s -> s.type() == PlotCreatorSubstepType.FESTIVAL_TOURIST_SPOT)
        );
    }

    @Test
    void settingsAcceptHallowsEveActivityLabel() {
        PlotCreatorDraft draft = new PlotCreatorDraft();
        draft.setKinds(List.of(PlotBuildingKind.FESTIVAL));
        draft.setDisplayName("Hallow's Eve");
        draft.setFestivalSeasonInput("Autumn");
        draft.setFestivalDayInput("25");
        draft.setFestivalStartHourInput("15");
        draft.setFestivalEndHourInput("0");
        draft.setFestivalMechanicInput("Hallow's Eve");

        assertNull(PlotCreatorFestivalSettings.applyInput(draft));
        assertEquals(
            com.hexvane.aetherhaven.festival.hallowseve.HallowsEveIds.MECHANIC_ID,
            draft.getFestivalMechanicId()
        );
    }

    @Test
    void marketDefaultsAddElderShopStandsDisplaysAndTourists() {
        PlotCreatorDraft draft = new PlotCreatorDraft();
        draft.setKinds(List.of(PlotBuildingKind.FESTIVAL));
        draft.setFestivalMechanicId(com.hexvane.aetherhaven.festival.market.MarketIds.MECHANIC_ID);

        PlotCreatorFestivalMechanicDefaults.ensureRequiredSelectedSpots(draft);

        assertTrue(
            draft.getSelectedSpots().stream()
                .anyMatch(
                    s ->
                        s.type() == PlotCreatorSubstepType.WORK_POI
                            && "elder".equals(s.workResidentKind())
                )
        );
        assertTrue(
            draft.getSelectedSpots().stream()
                .anyMatch(
                    s ->
                        s.type() == PlotCreatorSubstepType.WORK_POI
                            && "market_shop".equals(s.workResidentKind())
                            && s.minCount() == com.hexvane.aetherhaven.festival.market.MarketIds.SHOP_SPOT_COUNT
                )
        );
        assertTrue(
            draft.getSelectedSpots().stream()
                .anyMatch(
                    s ->
                        s.type() == PlotCreatorSubstepType.FESTIVAL_MARKET_STAND
                            && s.minCount() == PlotCreatorFestivalMechanicDefaults.DEFAULT_MARKET_STANDS
                )
        );
        assertTrue(
            draft.getSelectedSpots().stream()
                .anyMatch(
                    s ->
                        s.type() == PlotCreatorSubstepType.FESTIVAL_MARKET_DISPLAY
                            && s.minCount() == PlotCreatorFestivalMechanicDefaults.DEFAULT_MARKET_DISPLAYS
                )
        );
        assertTrue(
            draft.getSelectedSpots().stream().anyMatch(s -> s.type() == PlotCreatorSubstepType.FESTIVAL_TOURIST_SPOT)
        );
        assertFalse(
            draft.getSelectedSpots().stream().anyMatch(s -> s.type() == PlotCreatorSubstepType.FESTIVAL_NPC)
        );
        assertNull(PlotCreatorFestivalNpcRoles.defaultMerchantForMechanic(
            com.hexvane.aetherhaven.festival.market.MarketIds.MECHANIC_ID
        ));
    }

    @Test
    void marketStandRequirementStaysAtFourWhenExtraStandsArePlaced() {
        PlotCreatorDraft draft = new PlotCreatorDraft();
        draft.setKinds(List.of(PlotBuildingKind.FESTIVAL));
        draft.setFestivalMechanicId(com.hexvane.aetherhaven.festival.market.MarketIds.MECHANIC_ID);
        for (int i = 0; i < 6; i++) {
            draft.getFestivalMarketStands().add(FestivalDefinition.RaceStartSpotRow.of(i, 6, 0, 0f));
        }
        PlotCreatorFestivalMechanicDefaults.ensureRequiredSelectedSpots(draft);
        assertEquals(
            PlotCreatorFestivalMechanicDefaults.DEFAULT_MARKET_STANDS,
            draft.getSelectedSpots().stream()
                .filter(s -> s.type() == PlotCreatorSubstepType.FESTIVAL_MARKET_STAND)
                .findFirst()
                .orElseThrow()
                .minCount()
        );
    }

    @Test
    void settingsAcceptMarketFestivalActivityLabel() {
        PlotCreatorDraft draft = new PlotCreatorDraft();
        draft.setKinds(List.of(PlotBuildingKind.FESTIVAL));
        draft.setDisplayName("Market Festival");
        draft.setFestivalSeasonInput("Autumn");
        draft.setFestivalDayInput("12");
        draft.setFestivalStartHourInput("8");
        draft.setFestivalEndHourInput("20");
        draft.setFestivalMechanicInput("Market Festival");

        assertNull(PlotCreatorFestivalSettings.applyInput(draft));
        assertEquals(
            com.hexvane.aetherhaven.festival.market.MarketIds.MECHANIC_ID,
            draft.getFestivalMechanicId()
        );
    }
}
