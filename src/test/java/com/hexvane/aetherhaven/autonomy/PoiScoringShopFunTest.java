package com.hexvane.aetherhaven.autonomy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
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
class PoiScoringShopFunTest {

    private static final Gson GSON = new Gson();

    @Test
    void applyPoiUseComplete_duringShopping_restoresFunAtShopPoi() {
        UUID townId = UUID.randomUUID();
        UUID shopPlotId = UUID.randomUUID();
        TownRecord town = townWithPlayerShop(townId, shopPlotId);
        ConstructionCatalog catalog = shopCatalog();
        PoiEntry shopPoi = poi(shopPlotId, townId, List.of("SHOP"), PoiInteractionKind.NONE);

        VillagerNeeds needs = VillagerNeeds.full();
        needs.setFun(10f);

        PoiScoring.applyPoiUseComplete(needs, shopPoi, null, town, catalog, UUID.randomUUID(), true);

        assertEquals(32f, needs.getFun(), 0.01f);
    }

    @Test
    void applyPoiUseComplete_outsideShopping_doesNotRestoreFunAtShopPoi() {
        UUID townId = UUID.randomUUID();
        UUID shopPlotId = UUID.randomUUID();
        TownRecord town = townWithPlayerShop(townId, shopPlotId);
        ConstructionCatalog catalog = shopCatalog();
        PoiEntry shopPoi = poi(shopPlotId, townId, List.of("SHOP"), PoiInteractionKind.NONE);

        VillagerNeeds needs = VillagerNeeds.full();
        needs.setFun(10f);

        PoiScoring.applyPoiUseComplete(needs, shopPoi, null, town, catalog, UUID.randomUUID(), false);

        assertEquals(10f, needs.getFun(), 0.01f);
    }

    @Test
    void isShopFunFillPoi_requiresShopScheduleAndTag() {
        PoiEntry shopPoi = poi(UUID.randomUUID(), UUID.randomUUID(), List.of("SHOP"), PoiInteractionKind.NONE);
        PoiEntry workPoi = poi(UUID.randomUUID(), UUID.randomUUID(), List.of("WORK"), PoiInteractionKind.WORK_SURFACE);

        assertTrue(PoiScoring.isShopFunFillPoi(shopPoi, true));
        assertFalse(PoiScoring.isShopFunFillPoi(shopPoi, false));
        assertFalse(PoiScoring.isShopFunFillPoi(workPoi, true));
    }

    private static TownRecord townWithPlayerShop(UUID townId, UUID shopPlotId) {
        TownRecord town = new TownRecord();
        town.addPlotInstance(completePlot(shopPlotId, "plot_player_shop"));
        return town;
    }

    private static PlotInstance completePlot(UUID plotId, String constructionId) {
        PlotInstance plot = new PlotInstance();
        plot.setPlotId(plotId);
        plot.setConstructionId(constructionId);
        plot.setState(PlotInstanceState.COMPLETE);
        return plot;
    }

    private static ConstructionCatalog shopCatalog() {
        return ConstructionCatalog.forTests(
            Map.of(
                "plot_player_shop",
                GSON.fromJson("{ \"id\": \"plot_player_shop\" }", ConstructionDefinition.class)
            )
        );
    }

    private static PoiEntry poi(
        UUID plotId,
        UUID townId,
        List<String> tags,
        PoiInteractionKind kind
    ) {
        return new PoiEntry(
            UUID.randomUUID(),
            townId,
            1,
            64,
            1,
            Set.copyOf(tags),
            1,
            plotId,
            null,
            kind
        );
    }
}
