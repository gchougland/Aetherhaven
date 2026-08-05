package com.hexvane.aetherhaven.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("crossmod")
class TownRecordWorkPlotLookupTest {
    private static final Gson GSON = new Gson();

    @Test
    void findCompletePlotForWorkConstruction_prefersStoredIdOverSharedHouseAlias() {
        ConstructionDefinition hobbitShop =
            GSON.fromJson(
                """
                {"id":"plot_hobbit_shop","displayName":"Hobbit Shop","prefabPath":"Hobbit.prefab.json","plotTokenItemId":"Token","countsAsConstructionId":"plot_house"}
                """,
                ConstructionDefinition.class
            );
        ConstructionCatalog catalog = ConstructionCatalog.forTests(Map.of(hobbitShop.getId(), hobbitShop));

        TownRecord town = new TownRecord();
        town.setTownId(UUID.randomUUID());
        town.getPlotInstances().add(completePlot("plot_house", UUID.randomUUID()));
        UUID hobbitPlotId = UUID.randomUUID();
        town.getPlotInstances().add(completePlot("plot_hobbit_shop", hobbitPlotId));

        PlotInstance found = town.findCompletePlotForWorkConstruction(catalog, "plot_hobbit_shop");
        assertEquals(hobbitPlotId, found.getPlotId());
    }

    @Test
    void isPlotWorkplaceForConstruction_rejectsGenericHouseForHobbitShopWork() {
        ConstructionDefinition hobbitShop =
            GSON.fromJson(
                """
                {"id":"plot_hobbit_shop","displayName":"Hobbit Shop","prefabPath":"Hobbit.prefab.json","plotTokenItemId":"Token","countsAsConstructionId":"plot_house"}
                """,
                ConstructionDefinition.class
            );
        ConstructionCatalog catalog = ConstructionCatalog.forTests(Map.of(hobbitShop.getId(), hobbitShop));

        PlotInstance house = completePlot("plot_house", UUID.randomUUID());
        PlotInstance shop = completePlot("plot_hobbit_shop", UUID.randomUUID());

        assertFalse(TownRecord.isPlotWorkplaceForConstruction(catalog, house, "plot_hobbit_shop"));
        assertTrue(TownRecord.isPlotWorkplaceForConstruction(catalog, shop, "plot_hobbit_shop"));
    }

    @Test
    void findCompletePlotForWorkConstruction_nullWhenOnlyGenericHouseExists() {
        ConstructionDefinition hobbitShop =
            GSON.fromJson(
                """
                {"id":"plot_hobbit_shop","displayName":"Hobbit Shop","prefabPath":"Hobbit.prefab.json","plotTokenItemId":"Token","countsAsConstructionId":"plot_house"}
                """,
                ConstructionDefinition.class
            );
        ConstructionCatalog catalog = ConstructionCatalog.forTests(Map.of(hobbitShop.getId(), hobbitShop));

        TownRecord town = new TownRecord();
        town.setTownId(UUID.randomUUID());
        town.getPlotInstances().add(completePlot("plot_house", UUID.randomUUID()));

        assertNull(town.findCompletePlotForWorkConstruction(catalog, "plot_hobbit_shop"));
    }

    private static PlotInstance completePlot(@Nonnull String constructionId, @Nonnull UUID plotId) {
        PlotInstance plot = new PlotInstance();
        plot.setPlotId(plotId);
        plot.setConstructionId(constructionId);
        plot.setState(PlotInstanceState.COMPLETE);
        return plot;
    }
}
