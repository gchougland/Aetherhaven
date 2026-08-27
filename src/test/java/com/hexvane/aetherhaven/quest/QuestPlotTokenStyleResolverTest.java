package com.hexvane.aetherhaven.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.town.TownRecord;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("construction")
final class QuestPlotTokenStyleResolverTest {
    private static final Gson GSON = new Gson();
    private static final Random RANDOM = new Random(1L);

    @Test
    void housingQuestPicksHouseOnlyStyleBuildingNotDualShopHouse() {
        ConstructionCatalog catalog = stormwindCatalog();
        TownRecord town = stormwindTown();

        String resolved =
            QuestPlotTokenStyleResolver.resolveConstructionId(
                catalog,
                AetherhavenConstants.CONSTRUCTION_PLOT_HOUSE,
                town,
                RANDOM
            );

        assertEquals("plot_stormwind_townhouse", resolved);
    }

    @Test
    void workplaceQuestCanStillPickDualShopHouse() {
        ConstructionCatalog catalog = stormwindCatalog();
        TownRecord town = stormwindTown();

        String resolved =
            QuestPlotTokenStyleResolver.resolveConstructionId(
                catalog,
                AetherhavenConstants.CONSTRUCTION_PLOT_MARKET_STALL,
                town,
                RANDOM
            );

        assertEquals("plot_stormwind_general_store", resolved);
    }

    @Test
    void housingQuestFallsBackToBaseHouseWhenStyleHasOnlyDuals() {
        ConstructionCatalog catalog = dualOnlyCatalog();
        TownRecord town = stormwindTown();

        String resolved =
            QuestPlotTokenStyleResolver.resolveConstructionId(
                catalog,
                AetherhavenConstants.CONSTRUCTION_PLOT_HOUSE,
                town,
                RANDOM
            );

        assertEquals(AetherhavenConstants.CONSTRUCTION_PLOT_HOUSE, resolved);
    }

    private static TownRecord stormwindTown() {
        TownRecord town = new TownRecord();
        town.setPreferredBuildingStyleId("stormwind");
        return town;
    }

    private static ConstructionCatalog stormwindCatalog() {
        Map<String, ConstructionDefinition> byId = new LinkedHashMap<>();
        put(byId, canonical("plot_house"));
        put(byId, canonical("plot_market_stall"));
        put(
            byId,
            definition(
                """
                {"id":"plot_stormwind_townhouse","displayName":"Townhouse","prefabPath":"Townhouse.prefab.json","plotTokenItemId":"Aetherhaven_Plot_Token","countsAsConstructionId":"plot_house","styleId":"stormwind"}
                """
            )
        );
        put(
            byId,
            definition(
                """
                {"id":"plot_stormwind_general_store","displayName":"General Store","prefabPath":"Store.prefab.json","plotTokenItemId":"Aetherhaven_Plot_Token","countsAsConstructionId":["plot_market_stall","plot_house"],"styleId":"stormwind"}
                """
            )
        );
        return ConstructionCatalog.forTests(byId);
    }

    private static ConstructionCatalog dualOnlyCatalog() {
        Map<String, ConstructionDefinition> byId = new LinkedHashMap<>();
        put(byId, canonical("plot_house"));
        put(byId, canonical("plot_market_stall"));
        put(
            byId,
            definition(
                """
                {"id":"plot_stormwind_general_store","displayName":"General Store","prefabPath":"Store.prefab.json","plotTokenItemId":"Aetherhaven_Plot_Token","countsAsConstructionId":["plot_market_stall","plot_house"],"styleId":"stormwind"}
                """
            )
        );
        return ConstructionCatalog.forTests(byId);
    }

    private static ConstructionDefinition canonical(String id) {
        return definition(
            "{\"id\":\""
                + id
                + "\",\"displayName\":\""
                + id
                + "\",\"prefabPath\":\""
                + id
                + ".prefab.json\",\"plotTokenItemId\":\"Aetherhaven_Plot_Token\"}"
        );
    }

    private static ConstructionDefinition definition(String json) {
        return GSON.fromJson(json, ConstructionDefinition.class);
    }

    private static void put(Map<String, ConstructionDefinition> byId, ConstructionDefinition def) {
        byId.put(def.getId(), def);
    }
}
