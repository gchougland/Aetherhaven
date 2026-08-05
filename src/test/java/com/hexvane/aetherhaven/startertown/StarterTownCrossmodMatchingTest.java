package com.hexvane.aetherhaven.startertown;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.quest.data.QuestDefinition;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("crossmod")
class StarterTownCrossmodMatchingTest {
    private static final Gson GSON = new Gson();

    @Test
    void questAutoComplete_doesNotMatchHobbitShopQuestToGenericHouse() {
        ConstructionDefinition hobbitShop =
            GSON.fromJson(
                """
                {"id":"plot_hobbit_shop","displayName":"Hobbit Shop","prefabPath":"Hobbit.prefab.json","plotTokenItemId":"Token","countsAsConstructionId":"plot_house"}
                """,
                ConstructionDefinition.class
            );
        ConstructionCatalog catalog = ConstructionCatalog.forTests(Map.of(hobbitShop.getId(), hobbitShop));
        QuestDefinition quest =
            GSON.fromJson(
                """
                {"schemaVersion":1,"id":"q_lotr_hobbit_shop","category":"town","grantPlotTokenConstructionId":"plot_hobbit_shop"}
                """,
                QuestDefinition.class
            );

        assertFalse(StarterTownQuestService.matchesBuiltConstruction(catalog, quest, "plot_house"));
        assertTrue(StarterTownQuestService.matchesBuiltConstruction(catalog, quest, "plot_hobbit_shop"));
    }

    @Test
    void villagerProvisioner_doesNotAssignHobbitShopkeepToGenericHouse() {
        ConstructionDefinition hobbitShop =
            GSON.fromJson(
                """
                {"id":"plot_hobbit_shop","displayName":"Hobbit Shop","prefabPath":"Hobbit.prefab.json","plotTokenItemId":"Token","countsAsConstructionId":"plot_house"}
                """,
                ConstructionDefinition.class
            );
        ConstructionCatalog catalog = ConstructionCatalog.forTests(Map.of(hobbitShop.getId(), hobbitShop));

        assertFalse(StarterTownVillagerProvisioner.workplaceMatches(catalog, "plot_house", "plot_hobbit_shop"));
        assertTrue(StarterTownVillagerProvisioner.workplaceMatches(catalog, "plot_hobbit_shop", "plot_hobbit_shop"));
    }
}
