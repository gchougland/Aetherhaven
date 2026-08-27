package com.hexvane.aetherhaven.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.PrefabMaterialsCatalog;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
final class RetiredBuiltInPlotMigrationTest {
    private static final Gson GSON = new Gson();

    @Test
    void completeLegacyPlotReturnsFullGoldOnce() {
        ConstructionCatalog catalog = catalogWithHouseStub();
        TownRecord town = new TownRecord();
        PlotInstance plot = plot("plot_jszzas_house", PlotInstanceState.COMPLETE);
        town.addPlotInstance(plot);

        assertTrue(RetiredBuiltInPlotMigration.applyToTown(town, catalog, PrefabMaterialsCatalog.empty()));
        assertEquals(48L, town.getTreasuryGoldCoinCount());
        assertEquals(48L, town.getPendingRetiredBuildingGoldNotice());
        assertTrue(town.reimbursedRetiredPlotIds().contains(plot.getPlotId().toString()));

        assertFalse(RetiredBuiltInPlotMigration.applyToTown(town, catalog, PrefabMaterialsCatalog.empty()));
        assertEquals(48L, town.getTreasuryGoldCoinCount());
    }

    @Test
    void blueprintingLegacyPlotIsMarkedButPaysNoGold() {
        ConstructionCatalog catalog = catalogWithHouseStub();
        TownRecord town = new TownRecord();
        town.addPlotInstance(plot("plot_jszzas_house", PlotInstanceState.BLUEPRINTING));

        assertTrue(RetiredBuiltInPlotMigration.applyToTown(town, catalog, PrefabMaterialsCatalog.empty()));
        assertEquals(0L, town.getTreasuryGoldCoinCount());
        assertEquals(0L, town.getPendingRetiredBuildingGoldNotice());
    }

    @Test
    void stillCountsAsHouseAndIsNotCraftable() {
        ConstructionCatalog catalog = catalogWithHouseStub();
        ConstructionDefinition stub = catalog.get("plot_jszzas_house");
        assertTrue(catalog.matchesGameplayConstruction("plot_jszzas_house", "plot_house"));
        assertTrue(stub.isLegacyPlotSupport());
        assertTrue(stub.getPrefabPath() == null || stub.getPrefabPath().isBlank());
    }

    @Nonnull
    private static ConstructionCatalog catalogWithHouseStub() {
        ConstructionDefinition stub =
            GSON.fromJson(
                """
                {"id":"plot_jszzas_house","displayName":"Jszza's House","legacyPlotSupport":true,"countsAsConstructionId":"plot_house","treasuryGoldCoinCost":48,"maxHomeResidents":1}
                """,
                ConstructionDefinition.class
            );
        return ConstructionCatalog.forTests(Map.of(stub.getId(), stub));
    }

    @Nonnull
    private static PlotInstance plot(@Nonnull String constructionId, @Nonnull PlotInstanceState state) {
        return new PlotInstance(
            UUID.randomUUID(),
            constructionId,
            state,
            new PlotFootprintRecord(0, 0, 0, 1, 1, 1),
            0,
            0,
            0,
            0L
        );
    }
}
