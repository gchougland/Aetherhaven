package com.hexvane.aetherhaven.autonomy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.poi.PoiInteractionKind;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("autonomy")
class PoiScoringInnCapTest {

    private static final Gson GSON = new Gson();

    @Test
    void applyPoiUseComplete_atInnEat_clampsHungerTo80() {
        UUID townId = UUID.randomUUID();
        UUID innPlotId = UUID.randomUUID();
        UUID villagerUuid = UUID.randomUUID();
        TownRecord town = townWithInn(townId, innPlotId);
        ConstructionCatalog catalog = houseInnCatalog();
        PoiEntry innEat = poi(innPlotId, townId, 1, 64, 1, List.of("EAT"), PoiInteractionKind.NONE);

        VillagerNeeds needs = VillagerNeeds.full();
        needs.setHunger(60f);

        PoiScoring.applyPoiUseComplete(needs, innEat, null, town, catalog, villagerUuid);

        assertEquals(PoiScoring.INN_UTILITY_NEED_CAP, needs.getHunger(), 0.01f);
    }

    @Test
    void applyPoiUseComplete_atRestaurantOnInnPlot_fillsToFull() {
        UUID townId = UUID.randomUUID();
        UUID innPlotId = UUID.randomUUID();
        UUID villagerUuid = UUID.randomUUID();
        TownRecord town = townWithInn(townId, innPlotId);
        ConstructionCatalog catalog = houseInnCatalog();
        PoiEntry restaurantEat =
            poi(
                innPlotId,
                townId,
                2,
                64,
                2,
                List.of("EAT", AetherhavenConstants.POI_TAG_RESTAURANT),
                PoiInteractionKind.USE_BENCH
            );

        VillagerNeeds needs = VillagerNeeds.full();
        needs.setHunger(75f);

        PoiScoring.applyPoiUseComplete(needs, restaurantEat, null, town, catalog, villagerUuid);

        assertEquals(VillagerNeeds.MAX, needs.getHunger(), 0.01f);
    }

    @Test
    void applyPoiUseComplete_atHomeBed_fillsToFull() {
        UUID townId = UUID.randomUUID();
        UUID homePlotId = UUID.randomUUID();
        UUID villagerUuid = UUID.randomUUID();
        TownRecord town = townWithHouse(townId, homePlotId, villagerUuid);
        ConstructionCatalog catalog = houseInnCatalog();
        PoiEntry homeBed =
            poi(homePlotId, townId, 5, 64, 5, List.of("SLEEP", "ENERGY"), PoiInteractionKind.SLEEP);

        VillagerNeeds needs = VillagerNeeds.full();
        needs.setEnergy(75f);

        PoiScoring.applyPoiUseComplete(needs, homeBed, null, town, catalog, villagerUuid);

        assertEquals(VillagerNeeds.MAX, needs.getEnergy(), 0.01f);
    }

    @Test
    void isNeedMeterFilledForPoi_innEatStopsAt80() {
        UUID townId = UUID.randomUUID();
        UUID innPlotId = UUID.randomUUID();
        UUID villagerUuid = UUID.randomUUID();
        TownRecord town = townWithInn(townId, innPlotId);
        ConstructionCatalog catalog = houseInnCatalog();
        PoiEntry innEat = poi(innPlotId, townId, 1, 64, 1, List.of("EAT"), PoiInteractionKind.NONE);

        VillagerNeeds needs = VillagerNeeds.full();
        needs.setHunger(79.5f);
        assertFalse(
            PoiScoring.isNeedMeterFilledForPoi(innEat, needs, town, catalog, villagerUuid)
        );

        needs.setHunger(PoiScoring.INN_UTILITY_NEED_CAP);
        assertTrue(
            PoiScoring.isNeedMeterFilledForPoi(innEat, needs, town, catalog, villagerUuid)
        );
    }

    @Test
    void withoutNeedCapReachedPois_dropsInnEatWhenHungerAt80() {
        UUID townId = UUID.randomUUID();
        UUID innPlotId = UUID.randomUUID();
        UUID villagerUuid = UUID.randomUUID();
        TownRecord town = townWithInn(townId, innPlotId);
        ConstructionCatalog catalog = houseInnCatalog();
        PoiEntry innEat = poi(innPlotId, townId, 1, 64, 1, List.of("EAT"), PoiInteractionKind.NONE);
        PoiEntry innSit = poi(innPlotId, townId, 2, 64, 2, List.of("FUN", "SIT"), PoiInteractionKind.SIT);

        VillagerNeeds needs = VillagerNeeds.full();
        needs.setHunger(PoiScoring.INN_UTILITY_NEED_CAP);

        List<PoiEntry> filtered =
            PoiScoring.withoutNeedCapReachedPois(List.of(innEat, innSit), needs, town, catalog, villagerUuid);

        assertEquals(1, filtered.size());
        assertEquals(innSit.getId(), filtered.get(0).getId());
        assertFalse(
            PoiScoring.hasSatisfiableHungerPoi(
                List.of(innEat, innSit), Map.of(), needs, town, catalog, villagerUuid
            )
        );
    }

    @Test
    void withoutNeedCapReachedPois_keepsInnEatWhenHungry() {
        UUID townId = UUID.randomUUID();
        UUID innPlotId = UUID.randomUUID();
        UUID villagerUuid = UUID.randomUUID();
        TownRecord town = townWithInn(townId, innPlotId);
        ConstructionCatalog catalog = houseInnCatalog();
        PoiEntry innEat = poi(innPlotId, townId, 1, 64, 1, List.of("EAT"), PoiInteractionKind.NONE);

        VillagerNeeds needs = VillagerNeeds.full();
        needs.setHunger(40f);

        List<PoiEntry> filtered =
            PoiScoring.withoutNeedCapReachedPois(List.of(innEat), needs, town, catalog, villagerUuid);

        assertEquals(1, filtered.size());
        assertTrue(
            PoiScoring.hasSatisfiableHungerPoi(
                List.of(innEat), Map.of(), needs, town, catalog, villagerUuid
            )
        );
    }

    @Test
    void withoutNeedCapReachedPois_dropsInnRestWhenEnergyAt80() {
        UUID townId = UUID.randomUUID();
        UUID innPlotId = UUID.randomUUID();
        UUID villagerUuid = UUID.randomUUID();
        TownRecord town = townWithInn(townId, innPlotId);
        ConstructionCatalog catalog = houseInnCatalog();
        PoiEntry innBed =
            poi(innPlotId, townId, 1, 64, 1, List.of("SLEEP", "ENERGY"), PoiInteractionKind.SLEEP);

        VillagerNeeds needs = VillagerNeeds.full();
        needs.setEnergy(PoiScoring.INN_UTILITY_NEED_CAP);

        List<PoiEntry> filtered =
            PoiScoring.withoutNeedCapReachedPois(List.of(innBed), needs, town, catalog, villagerUuid);

        assertTrue(filtered.isEmpty());
        assertFalse(
            PoiScoring.hasSatisfiableEnergyPoi(
                filtered, Map.of(), villagerUuid, town, catalog
            )
        );
    }

    @Test
    void isInnUtilityNeedCapPoi_onlyForSharedInnUtilitySpots() {
        UUID townId = UUID.randomUUID();
        UUID innPlotId = UUID.randomUUID();
        UUID homePlotId = UUID.randomUUID();
        UUID villagerUuid = UUID.randomUUID();
        TownRecord town = townWithHouseAndInn(townId, homePlotId, innPlotId, villagerUuid);
        ConstructionCatalog catalog = houseInnCatalog();

        PoiEntry innEat = poi(innPlotId, townId, 1, 64, 1, List.of("EAT"), PoiInteractionKind.NONE);
        PoiEntry restaurantEat =
            poi(
                innPlotId,
                townId,
                2,
                64,
                2,
                List.of("EAT", AetherhavenConstants.POI_TAG_RESTAURANT),
                PoiInteractionKind.USE_BENCH
            );
        PoiEntry homeBed =
            poi(homePlotId, townId, 5, 64, 5, List.of("SLEEP", "ENERGY"), PoiInteractionKind.SLEEP);

        assertTrue(PoiScoring.isInnUtilityNeedCapPoi(innEat, town, catalog, villagerUuid));
        assertFalse(PoiScoring.isInnUtilityNeedCapPoi(restaurantEat, town, catalog, villagerUuid));
        assertFalse(PoiScoring.isInnUtilityNeedCapPoi(homeBed, town, catalog, villagerUuid));
    }

    private static TownRecord townWithInn(UUID townId, UUID innPlotId) {
        TownRecord town = new TownRecord();
        town.addPlotInstance(completePlot(innPlotId, "plot_inn"));
        return town;
    }

    private static TownRecord townWithHouse(UUID townId, UUID homePlotId, UUID villagerUuid) {
        TownRecord town = new TownRecord();
        PlotInstance home = completePlot(homePlotId, "plot_house");
        home.setHomeResidentEntityUuid(villagerUuid);
        town.addPlotInstance(home);
        return town;
    }

    private static TownRecord townWithHouseAndInn(
        UUID townId,
        UUID homePlotId,
        UUID innPlotId,
        UUID villagerUuid
    ) {
        TownRecord town = new TownRecord();
        PlotInstance home = completePlot(homePlotId, "plot_house");
        home.setHomeResidentEntityUuid(villagerUuid);
        town.addPlotInstance(home);
        town.addPlotInstance(completePlot(innPlotId, "plot_inn"));
        return town;
    }

    private static PlotInstance completePlot(UUID plotId, String constructionId) {
        PlotInstance plot = new PlotInstance();
        plot.setPlotId(plotId);
        plot.setConstructionId(constructionId);
        plot.setState(PlotInstanceState.COMPLETE);
        return plot;
    }

    private static ConstructionCatalog houseInnCatalog() {
        return ConstructionCatalog.forTests(
            Map.of(
                "plot_house",
                GSON.fromJson("{ \"id\": \"plot_house\" }", ConstructionDefinition.class),
                "plot_inn",
                GSON.fromJson("{ \"id\": \"plot_inn\" }", ConstructionDefinition.class)
            )
        );
    }

    private static PoiEntry poi(
        UUID plotId,
        UUID townId,
        int x,
        int y,
        int z,
        List<String> tags,
        PoiInteractionKind kind
    ) {
        return new PoiEntry(
            UUID.randomUUID(),
            townId,
            x,
            y,
            z,
            Set.copyOf(tags),
            1,
            plotId,
            null,
            kind
        );
    }
}
