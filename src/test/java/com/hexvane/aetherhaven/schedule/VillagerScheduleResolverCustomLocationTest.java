package com.hexvane.aetherhaven.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("crossmod")
class VillagerScheduleResolverCustomLocationTest {

    private final Gson gson = new Gson();

    @Test
    void resolvesRegisteredCustomLocationToCompletePlot() {
        UUID plotId = UUID.randomUUID();
        TownRecord town = new TownRecord();
        PlotInstance plot = new PlotInstance();
        plot.setPlotId(plotId);
        plot.setConstructionId("plot_fishing_shop");
        plot.setState(PlotInstanceState.COMPLETE);
        town.addPlotInstance(plot);

        ConstructionCatalog constructionCatalog =
            ConstructionCatalog.forTests(
                Map.of(
                    "plot_fishing_shop",
                    gson.fromJson(
                        """
                        { "id": "plot_fishing_shop", "scheduleSharedUtilityPick": true }
                        """,
                        com.hexvane.aetherhaven.construction.ConstructionDefinition.class
                    )
                )
            );

        ScheduleLocationDefinition loc = new ScheduleLocationDefinition();
        loc.setConstructionId("plot_fishing_shop");
        ScheduleLocationCatalog locationCatalog =
            ScheduleLocationCatalog.forTests(Map.of("fishing_dock", loc));

        TownVillagerBinding binding = new TownVillagerBinding();

        VillagerScheduleResolveOutcome out =
            VillagerScheduleResolver.resolvePlot(
                town,
                binding,
                UUID.randomUUID(),
                "fishing_dock",
                null,
                constructionCatalog,
                null,
                false,
                null,
                null,
                locationCatalog
            );

        assertEquals(plotId, out.plotId());
    }

    @Test
    void skipsUnknownCustomLocation() {
        TownRecord town = new TownRecord();
        TownVillagerBinding binding = new TownVillagerBinding();

        VillagerScheduleResolveOutcome out =
            VillagerScheduleResolver.resolvePlot(
                town,
                binding,
                UUID.randomUUID(),
                "unknown_spot",
                null,
                ConstructionCatalog.empty(),
                null,
                false,
                null,
                null,
                ScheduleLocationCatalog.empty()
            );

        assertNull(out.plotId());
        assertFalse(out.clearPreferredPlot());
    }
}
