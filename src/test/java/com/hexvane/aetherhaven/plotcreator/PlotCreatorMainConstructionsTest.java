package com.hexvane.aetherhaven.plotcreator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import java.util.List;
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

    @Test
    void innVariant_requiresInnBellStep() {
        PlotCreatorDraft draft = new PlotCreatorDraft();
        draft.setKinds(List.of(PlotBuildingKind.VARIANT));
        draft.setCountsAsConstructionIds(List.of(AetherhavenConstants.CONSTRUCTION_PLOT_INN));

        assertTrue(
            PlotBuildingKindRequirements.defaultRequirements(draft, null).stream()
                .anyMatch(requirement -> requirement.type() == PlotCreatorSubstepType.INN_BELL_BLOCK)
        );
    }

    @Test
    void gaiaAltar_requiresGaiaStatueStep() {
        PlotCreatorDraft draft = new PlotCreatorDraft();
        draft.setKinds(List.of(PlotBuildingKind.AMENITY));
        draft.setConstructionId(AetherhavenConstants.CONSTRUCTION_PLOT_GAIA_ALTAR);

        List<PlotBuildingKindRequirements.SubstepRequirement> requirements =
            PlotBuildingKindRequirements.defaultRequirements(draft, null);
        assertTrue(
            requirements.stream()
                .anyMatch(requirement -> requirement.type() == PlotCreatorSubstepType.GAIA_STATUE_BLOCK)
        );
        assertTrue(
            requirements.stream()
                .anyMatch(requirement ->
                    requirement.type() == PlotCreatorSubstepType.FUN_POI && requirement.minCount() == 2)
        );
    }
}
