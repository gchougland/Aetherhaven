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
    void housingQuestFallsBackToMatchingDualWhenStyleHasOnlyDuals() {
        ConstructionCatalog catalog = dualOnlyCatalog();
        TownRecord town = stormwindTown();

        String resolved =
            QuestPlotTokenStyleResolver.resolveConstructionId(
                catalog,
                AetherhavenConstants.CONSTRUCTION_PLOT_HOUSE,
                town,
                RANDOM
            );

        assertEquals("plot_stormwind_general_store", resolved);
    }

    @Test
    void farmQuestPrefersDedicatedBuildingsOverEveryMixedUseBuilding() {
        var candidates = new LinkedHashMap<String, ConstructionDefinition>();
        put(candidates, canonical("plot_farm"));
        put(candidates, canonical("plot_house"));
        put(candidates, canonical("plot_blacksmith"));
        put(candidates, variant("farm_home", "[\"plot_farm\",\"plot_house\"]", false));
        put(candidates, variant("farm_smith", "[\"plot_farm\",\"plot_blacksmith\"]", false));
        put(candidates, variant("farm_a", "[\"plot_farm\"]", false));
        put(candidates, variant("farm_b", "[\"plot_farm\"]", false));
        assertChoices(candidates, "plot_farm", java.util.Set.of("farm_a", "farm_b"));
    }

    @Test
    void markedChoicesOverrideDedicatedBuildingsAndRandomizeAmongMarked() {
        var candidates = new LinkedHashMap<String, ConstructionDefinition>();
        put(candidates, canonical("plot_farm"));
        put(candidates, canonical("plot_house"));
        put(candidates, variant("dedicated", "[\"plot_farm\"]", false));
        put(candidates, variant("marked_a", "[\"plot_farm\",\"plot_house\"]", true));
        put(candidates, variant("marked_b", "[\"plot_farm\"]", true));
        put(candidates, variant("wrong_type", "[\"plot_house\"]", true));
        put(candidates, definition(GSON.toJson(variant("wrong_style", "[\"plot_farm\"]", true))
            .replace("stormwind", "medieval")));
        assertChoices(candidates, "plot_farm", java.util.Set.of("marked_a", "marked_b"));
    }

    @Test
    void noDedicatedBuildingRandomizesAmongMatchingMixedUseBuildings() {
        var candidates = new LinkedHashMap<String, ConstructionDefinition>();
        put(candidates, canonical("plot_farm"));
        put(candidates, canonical("plot_house"));
        put(candidates, variant("mixed_a", "[\"plot_farm\",\"plot_house\"]", false));
        put(candidates, variant("mixed_b", "[\"plot_farm\",\"plot_house\"]", false));
        assertChoices(candidates, "plot_farm", java.util.Set.of("mixed_a", "mixed_b"));
        assertEquals("plot_blacksmith", QuestPlotTokenStyleResolver.resolveConstructionId(
            ConstructionCatalog.forTests(candidates), "plot_blacksmith", stormwindTown(), new Random(1)));
    }

    private static void assertChoices(Map<String, ConstructionDefinition> candidates, String base, java.util.Set<String> expected) {
        var random = new Random(812);
        var catalog = ConstructionCatalog.forTests(candidates);
        var actual = new java.util.HashSet<String>();
        for (int i=0; i<100; i++) actual.add(QuestPlotTokenStyleResolver.resolveConstructionId(catalog, base, stormwindTown(), random));
        assertEquals(expected, actual);
    }

    private static ConstructionDefinition variant(String id, String countsAs, boolean preferred) {
        return definition("{\"id\":\""+id+"\",\"prefabPath\":\"test.prefab.json\",\"styleId\":\"stormwind\","
            +"\"countsAsConstructionId\":"+countsAs+",\"preferredStyleQuestBuilding\":"+preferred+"}");
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
