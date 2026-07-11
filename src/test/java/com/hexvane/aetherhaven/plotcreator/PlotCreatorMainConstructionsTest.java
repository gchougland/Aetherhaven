package com.hexvane.aetherhaven.plotcreator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("crossmod")
class PlotCreatorMainConstructionsTest {

    private final Gson gson = new Gson();

    @Test
    void eligibleVariantBase_requiresCanonicalNonDecoration() {
        ConstructionDefinition shop = gson.fromJson(
            """
            {
              "id": "plot_fishing_shop",
              "prefabPath": "x.prefab.json",
              "tags": ["shop"]
            }
            """,
            ConstructionDefinition.class
        );
        assertTrue(PlotCreatorMainConstructions.isEligibleVariantBase(shop));

        ConstructionDefinition variant = gson.fromJson(
            """
            {
              "id": "plot_fishing_shop_fancy",
              "prefabPath": "x.prefab.json",
              "countsAsConstructionId": "plot_fishing_shop"
            }
            """,
            ConstructionDefinition.class
        );
        assertFalse(PlotCreatorMainConstructions.isEligibleVariantBase(variant));

        ConstructionDefinition decoration = gson.fromJson(
            """
            {
              "id": "plot_decoration_bench",
              "prefabPath": "x.prefab.json",
              "decorationPlot": true
            }
            """,
            ConstructionDefinition.class
        );
        assertFalse(PlotCreatorMainConstructions.isEligibleVariantBase(decoration));
    }
}
