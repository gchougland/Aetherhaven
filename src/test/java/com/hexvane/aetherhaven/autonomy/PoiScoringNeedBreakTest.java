package com.hexvane.aetherhaven.autonomy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.poi.PoiInteractionKind;
import com.hexvane.aetherhaven.schedule.VillagerScheduleResolver;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("autonomy")
class PoiScoringNeedBreakTest {

    private static final Gson GSON = new Gson();

    @Test
    void pickEnergyRestPoi_prefersHomeBedOverInnBed() {
        UUID townId = UUID.randomUUID();
        UUID homePlotId = UUID.randomUUID();
        UUID innPlotId = UUID.randomUUID();
        UUID villagerUuid = UUID.randomUUID();
        PoiEntry homeBed = poi(homePlotId, townId, 5, 64, 5, List.of("SLEEP", "ENERGY"), PoiInteractionKind.SLEEP);
        PoiEntry innBed = poi(innPlotId, townId, 50, 64, 50, List.of("SLEEP", "ENERGY"), PoiInteractionKind.SLEEP);

        TownRecord town = townWithHouseAndInn(townId, homePlotId, innPlotId, villagerUuid);
        ConstructionCatalog catalog = houseInnCatalog();

        PoiEntry pick =
            PoiScoring.pickEnergyRestPoi(
                List.of(innBed, homeBed),
                Map.of(),
                0.0,
                0.0,
                PoiScoring.resolveHomePlotId(town, villagerUuid, catalog),
                PoiScoring.resolveInnPlotIds(town, catalog)
            );
        assertEquals(homeBed.getId(), pick.getId());
    }

    @Test
    void pickEnergyRestPoi_usesInnWhenNoHome() {
        UUID townId = UUID.randomUUID();
        UUID innPlotId = UUID.randomUUID();
        PoiEntry innBed = poi(innPlotId, townId, 50, 64, 50, List.of("SLEEP", "ENERGY"), PoiInteractionKind.SLEEP);

        TownRecord town = new TownRecord();
        PlotInstance inn = completePlot(innPlotId, "plot_inn");
        town.addPlotInstance(inn);

        PoiEntry pick =
            PoiScoring.pickEnergyRestPoi(
                List.of(innBed),
                Map.of(),
                0.0,
                0.0,
                null,
                PoiScoring.resolveInnPlotIds(town, houseInnCatalog())
            );
        assertEquals(innBed.getId(), pick.getId());
    }

    @Test
    void pickEnergyRestPoi_returnsNullWhenNoInnOrHomeBed() {
        UUID townId = UUID.randomUUID();
        UUID workPlotId = UUID.randomUUID();
        PoiEntry work =
            poi(workPlotId, townId, 10, 64, 10, List.of("WORK"), PoiInteractionKind.WORK_SURFACE);

        assertNull(
            PoiScoring.pickEnergyRestPoi(List.of(work), Map.of(), 0.0, 0.0, null, List.of())
        );
    }

    @Test
    void pickEnergyRestPoi_ignoresNonInnBedsWhenHomeless() {
        UUID townId = UUID.randomUUID();
        UUID shopPlotId = UUID.randomUUID();
        UUID innPlotId = UUID.randomUUID();
        PoiEntry shopBed =
            poi(shopPlotId, townId, 12, 64, 12, List.of("SLEEP", "ENERGY"), PoiInteractionKind.SLEEP);
        PoiEntry innBed = poi(innPlotId, townId, 50, 64, 50, List.of("SLEEP", "ENERGY"), PoiInteractionKind.SLEEP);

        TownRecord town = new TownRecord();
        town.addPlotInstance(completePlot(shopPlotId, "plot_market_stall"));
        town.addPlotInstance(completePlot(innPlotId, "plot_inn"));

        PoiEntry pick =
            PoiScoring.pickEnergyRestPoi(
                List.of(shopBed, innBed),
                Map.of(),
                0.0,
                0.0,
                null,
                PoiScoring.resolveInnPlotIds(town, houseInnCatalog())
            );
        assertEquals(innBed.getId(), pick.getId());
    }

    @Test
    void pickFunBreakPoi_ignoresFunPoiOnNonParkPlots() {
        UUID townId = UUID.randomUUID();
        UUID parkPlotId = UUID.randomUUID();
        UUID shopPlotId = UUID.randomUUID();

        TownRecord town = new TownRecord();
        PlotInstance park = completePlot(parkPlotId, "plot_park");
        park.applySignAndFootprint(0, 64, 0, new PlotFootprintRecord(0, 60, 0, 4, 70, 4));
        town.addPlotInstance(park);
        town.addPlotInstance(completePlot(shopPlotId, "plot_market_stall"));

        PoiEntry shopBench =
            poi(shopPlotId, townId, 1, 64, 1, List.of("FUN", "SIT"), PoiInteractionKind.SIT);
        PoiEntry parkBench =
            poi(parkPlotId, townId, 2, 64, 2, List.of("FUN", "SIT"), PoiInteractionKind.SIT);

        PoiEntry pick =
            PoiScoring.pickFunBreakPoi(
                List.of(shopBench, parkBench),
                Map.of(),
                0.0,
                0.0,
                town,
                houseInnCatalog()
            );
        assertEquals(parkBench.getId(), pick.getId());
    }

    @Test
    void pickBest_prefersFunOverHungerWhenFunIsLower() {
        UUID townId = UUID.randomUUID();
        UUID workPlotId = UUID.randomUUID();
        UUID parkPlotId = UUID.randomUUID();
        TownVillagerBinding binding =
            new TownVillagerBinding(townId, TownVillagerBinding.KIND_BUILDER, workPlotId, workPlotId);

        TownRecord town = new TownRecord();
        town.addPlotInstance(completePlot(parkPlotId, "plot_park"));

        PoiEntry eat = poi(workPlotId, townId, 1, 64, 1, List.of("EAT"), PoiInteractionKind.USE_CONTAINER);
        PoiEntry bench = poi(parkPlotId, townId, 30, 64, 30, List.of("FUN", "SIT"), PoiInteractionKind.SIT);

        VillagerNeeds needs = VillagerNeeds.full();
        needs.setHunger(45f);
        needs.setFun(0f);

        Set<UUID> breakPlots =
            PoiScoring.resolveUrgentBreakPlotAllowlist(
                needs, false, false, true, true, town, UUID.randomUUID(), houseInnCatalog());

        PoiEntry pick =
            PoiScoring.pickBest(
                List.of(eat, bench),
                needs,
                binding,
                Map.of(),
                0.0,
                0.0,
                VillagerScheduleResolver.LOC_HOME,
                false,
                false,
                false,
                true,
                true,
                null,
                breakPlots
            );
        assertEquals(bench.getId(), pick.getId());
    }

    @Test
    void pickFunBreakPoi_picksNearestPlotWithFunPoi() {
        UUID townId = UUID.randomUUID();
        UUID nearPlotId = UUID.randomUUID();
        UUID farPlotId = UUID.randomUUID();

        TownRecord town = new TownRecord();
        PlotInstance nearPlot = completePlot(nearPlotId, "plot_park");
        nearPlot.applySignAndFootprint(0, 64, 0, new PlotFootprintRecord(0, 60, 0, 4, 70, 4));
        PlotInstance farPlot = completePlot(farPlotId, "plot_park");
        farPlot.applySignAndFootprint(200, 64, 200, new PlotFootprintRecord(200, 60, 200, 210, 70, 210));
        town.addPlotInstance(nearPlot);
        town.addPlotInstance(farPlot);

        PoiEntry nearBench = poi(nearPlotId, townId, 2, 64, 2, List.of("FUN", "SIT"), PoiInteractionKind.SIT);
        PoiEntry farBench = poi(farPlotId, townId, 205, 64, 205, List.of("FUN", "SIT"), PoiInteractionKind.SIT);

        PoiEntry pick =
            PoiScoring.pickFunBreakPoi(
                List.of(farBench, nearBench),
                Map.of(),
                1.0,
                1.0,
                town,
                houseInnCatalog()
            );
        assertEquals(nearBench.getId(), pick.getId());
    }

    @Test
    void pickFunBreakPoi_returnsNullWhenNoFunBuilding() {
        UUID townId = UUID.randomUUID();
        UUID plotId = UUID.randomUUID();
        PoiEntry bed = poi(plotId, townId, 1, 64, 1, List.of("SLEEP", "ENERGY"), PoiInteractionKind.SLEEP);

        assertNull(
            PoiScoring.pickFunBreakPoi(
                List.of(bed), Map.of(), 0.0, 0.0, new TownRecord(), houseInnCatalog()
            )
        );
    }

    @Test
    void resolveMostUrgentSatisfiableNeed_picksLowestMeter() {
        UUID townId = UUID.randomUUID();
        UUID homePlotId = UUID.randomUUID();
        UUID funPlotId = UUID.randomUUID();
        UUID villagerUuid = UUID.randomUUID();

        TownRecord town = townWithHouseAndInn(townId, homePlotId, UUID.randomUUID(), villagerUuid);
        PlotInstance funPlot = completePlot(funPlotId, "plot_park");
        funPlot.applySignAndFootprint(30, 64, 30, new PlotFootprintRecord(30, 60, 30, 40, 70, 40));
        town.addPlotInstance(funPlot);

        PoiEntry homeBed = poi(homePlotId, townId, 5, 64, 5, List.of("SLEEP", "ENERGY"), PoiInteractionKind.SLEEP);
        PoiEntry funBench = poi(funPlotId, townId, 35, 64, 35, List.of("FUN", "SIT"), PoiInteractionKind.SIT);

        VillagerNeeds needs = VillagerNeeds.full();
        needs.setHunger(45f);
        needs.setEnergy(25f);
        needs.setFun(10f);

        PoiScoring.UrgentNeedKind kind =
            PoiScoring.resolveMostUrgentSatisfiableNeed(
                needs,
                false,
                false,
                false,
                true,
                List.of(homeBed, funBench),
                Map.of(),
                0.0,
                0.0,
                villagerUuid,
                town,
                houseInnCatalog()
            );
        assertEquals(PoiScoring.UrgentNeedKind.FUN, kind);
    }

    @Test
    void hasSatisfiableEnergyPoi_falseWhenNoBeds() {
        UUID townId = UUID.randomUUID();
        UUID plotId = UUID.randomUUID();
        PoiEntry work =
            poi(plotId, townId, 1, 64, 1, List.of("WORK"), PoiInteractionKind.WORK_SURFACE);
        TownRecord town = new TownRecord();
        town.addPlotInstance(completePlot(plotId, "plot_market_stall"));

        assertTrue(
            !PoiScoring.hasSatisfiableEnergyPoi(
                List.of(work),
                Map.of(),
                UUID.randomUUID(),
                town,
                houseInnCatalog()
            )
        );
    }

    @Test
    void hasSatisfiableEnergyPoi_trueForInnBed() {
        UUID townId = UUID.randomUUID();
        UUID innPlotId = UUID.randomUUID();
        PoiEntry bed = poi(innPlotId, townId, 1, 64, 1, List.of("SLEEP", "ENERGY"), PoiInteractionKind.SLEEP);
        TownRecord town = new TownRecord();
        town.addPlotInstance(completePlot(innPlotId, "plot_inn"));

        assertTrue(
            PoiScoring.hasSatisfiableEnergyPoi(
                List.of(bed),
                Map.of(),
                UUID.randomUUID(),
                town,
                houseInnCatalog()
            )
        );
    }

    @Test
    void resolveInnPlotIds_matchesInnVariants() {
        UUID innPlotId = UUID.randomUUID();
        TownRecord town = new TownRecord();
        town.addPlotInstance(completePlot(innPlotId, "plot_stormwind_inn"));

        ConstructionCatalog catalog = variantCatalog();

        List<UUID> ids = PoiScoring.resolveInnPlotIds(town, catalog);
        assertEquals(1, ids.size());
        assertEquals(innPlotId, ids.get(0));
    }

    @Test
    void resolveParkPlotIds_matchesParkVariants() {
        UUID parkPlotId = UUID.randomUUID();
        TownRecord town = new TownRecord();
        town.addPlotInstance(completePlot(parkPlotId, "plot_community_park_variant"));

        ConstructionCatalog catalog = variantCatalog();

        List<UUID> ids = PoiScoring.resolveParkPlotIds(town, catalog);
        assertEquals(1, ids.size());
        assertEquals(parkPlotId, ids.get(0));
    }

    @Test
    void resolveHomePlotId_matchesHouseVariants() {
        UUID expectedHomePlotId = UUID.randomUUID();
        UUID villagerUuid = UUID.randomUUID();
        TownRecord town = new TownRecord();
        PlotInstance home = completePlot(expectedHomePlotId, "plot_house_hytiny_cozy_cottage");
        home.setHomeResidentEntityUuid(villagerUuid);
        town.addPlotInstance(home);

        UUID resolved =
            PoiScoring.resolveHomePlotId(town, villagerUuid, variantCatalog());
        assertEquals(expectedHomePlotId, resolved);
    }

    @Test
    void pickEnergyRestPoi_usesInnVariantBed() {
        UUID townId = UUID.randomUUID();
        UUID innPlotId = UUID.randomUUID();
        PoiEntry innBed = poi(innPlotId, townId, 50, 64, 50, List.of("SLEEP", "ENERGY"), PoiInteractionKind.SLEEP);

        TownRecord town = new TownRecord();
        town.addPlotInstance(completePlot(innPlotId, "plot_stormwind_inn"));

        PoiEntry pick =
            PoiScoring.pickEnergyRestPoi(
                List.of(innBed),
                Map.of(),
                0.0,
                0.0,
                null,
                PoiScoring.resolveInnPlotIds(town, variantCatalog())
            );
        assertEquals(innBed.getId(), pick.getId());
    }

    @Test
    void pickFunBreakPoi_usesParkVariantBench() {
        UUID townId = UUID.randomUUID();
        UUID parkPlotId = UUID.randomUUID();
        TownRecord town = new TownRecord();
        PlotInstance park = completePlot(parkPlotId, "plot_community_park_variant");
        park.applySignAndFootprint(0, 64, 0, new PlotFootprintRecord(0, 60, 0, 4, 70, 4));
        town.addPlotInstance(park);

        PoiEntry bench =
            poi(parkPlotId, townId, 2, 64, 2, List.of("FUN", "SIT"), PoiInteractionKind.SIT);

        PoiEntry pick =
            PoiScoring.pickFunBreakPoi(
                List.of(bench),
                Map.of(),
                0.0,
                0.0,
                town,
                variantCatalog()
            );
        assertEquals(bench.getId(), pick.getId());
    }

    private static ConstructionCatalog variantCatalog() {
        return ConstructionCatalog.forTests(
            Map.of(
                "plot_stormwind_inn",
                GSON.fromJson(
                    """
                    {"id":"plot_stormwind_inn","countsAsConstructionId":"plot_inn"}
                    """,
                    ConstructionDefinition.class
                ),
                "plot_community_park_variant",
                GSON.fromJson(
                    """
                    {"id":"plot_community_park_variant","countsAsConstructionId":"plot_park"}
                    """,
                    ConstructionDefinition.class
                ),
                "plot_house_hytiny_cozy_cottage",
                GSON.fromJson(
                    """
                    {"id":"plot_house_hytiny_cozy_cottage","countsAsConstructionId":"plot_house"}
                    """,
                    ConstructionDefinition.class
                ),
                "plot_inn",
                GSON.fromJson("{ \"id\": \"plot_inn\" }", ConstructionDefinition.class),
                "plot_park",
                GSON.fromJson("{ \"id\": \"plot_park\" }", ConstructionDefinition.class),
                "plot_house",
                GSON.fromJson("{ \"id\": \"plot_house\" }", ConstructionDefinition.class)
            )
        );
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
        PlotInstance inn = completePlot(innPlotId, "plot_inn");
        town.addPlotInstance(home);
        town.addPlotInstance(inn);
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
                GSON.fromJson(
                    "{ \"id\": \"plot_house\" }",
                    ConstructionDefinition.class
                ),
                "plot_inn",
                GSON.fromJson(
                    "{ \"id\": \"plot_inn\" }",
                    ConstructionDefinition.class
                ),
                "plot_park",
                GSON.fromJson(
                    "{ \"id\": \"plot_park\" }",
                    ConstructionDefinition.class
                ),
                "plot_market_stall",
                GSON.fromJson(
                    "{ \"id\": \"plot_market_stall\" }",
                    ConstructionDefinition.class
                )
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
