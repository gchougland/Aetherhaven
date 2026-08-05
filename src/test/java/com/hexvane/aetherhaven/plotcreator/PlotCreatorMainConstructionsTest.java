package com.hexvane.aetherhaven.plotcreator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import java.util.List;
import javax.annotation.Nonnull;
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

    @Test
    void playerShopVariant_requiresShopNpcSpotsAndTouristDestination() {
        PlotCreatorDraft draft = new PlotCreatorDraft();
        draft.setKinds(List.of(PlotBuildingKind.VARIANT));
        draft.setCountsAsConstructionIds(List.of(AetherhavenConstants.CONSTRUCTION_PLOT_PLAYER_SHOP));
        draft.getSelectedSpots().add(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.MANAGEMENT_BLOCK, 1));

        PlotCreatorService.ensureRequiredSpots(draft);
        PlotCreatorService.applyDefaultTagsForKind(draft);

        assertTrue(hasSpot(draft, PlotCreatorSubstepType.SHOP_SAFE_BLOCK));
        assertTrue(hasSpot(draft, PlotCreatorSubstepType.WORK_POI));
        assertTrue(hasSpot(draft, PlotCreatorSubstepType.SHOP_SPOT));
        assertTrue(hasSpot(draft, PlotCreatorSubstepType.SHOP_POI));
        assertTrue(hasSpot(draft, PlotCreatorSubstepType.TOURIST_VISIT_POI));
        assertTrue(draft.isTouristDestination());
        assertTrue(draft.getBuildingTags().contains("shop"));
        assertTrue(draft.getBuildingTags().contains("work"));
    }

    @Test
    void flowerShopVariant_requiresShopNpcSpotsWithoutShopSafe() {
        PlotCreatorDraft draft = new PlotCreatorDraft();
        draft.setKinds(List.of(PlotBuildingKind.VARIANT));
        draft.setCountsAsConstructionIds(List.of(AetherhavenConstants.CONSTRUCTION_PLOT_FLOWER_SHOP));
        draft.getSelectedSpots().add(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.MANAGEMENT_BLOCK, 1));

        PlotCreatorService.ensureRequiredSpots(draft);
        PlotCreatorService.applyDefaultTagsForKind(draft);

        assertFalse(hasSpot(draft, PlotCreatorSubstepType.SHOP_SAFE_BLOCK));
        assertTrue(hasSpot(draft, PlotCreatorSubstepType.WORK_POI));
        assertTrue(hasSpot(draft, PlotCreatorSubstepType.SHOP_SPOT));
        assertTrue(hasSpot(draft, PlotCreatorSubstepType.SHOP_POI));
        assertTrue(hasSpot(draft, PlotCreatorSubstepType.TOURIST_VISIT_POI));
        assertTrue(draft.isTouristDestination());
    }

    @Test
    void shopDefaults_includeSeparateWorkAndShopPois() {
        PlotCreatorDraft draft = new PlotCreatorDraft();
        draft.setKinds(List.of(PlotBuildingKind.PLAYER_SHOP));
        List<PlotBuildingKindRequirements.SubstepRequirement> requirements =
            PlotBuildingKindRequirements.defaultRequirements(draft, null);
        assertTrue(requirements.stream().anyMatch(r -> r.type() == PlotCreatorSubstepType.WORK_POI));
        assertTrue(requirements.stream().anyMatch(r -> r.type() == PlotCreatorSubstepType.SHOP_POI));
    }

    @Test
    void shopVariant_setsTouristDestinationEvenWhenTagsAlreadyFilled() {
        PlotCreatorDraft draft = new PlotCreatorDraft();
        draft.setKinds(List.of(PlotBuildingKind.VARIANT));
        draft.setCountsAsConstructionIds(List.of(AetherhavenConstants.CONSTRUCTION_PLOT_PLAYER_SHOP));
        draft.getBuildingTags().add("shop");
        draft.getBuildingTags().add("player");
        draft.setBuildingTagsInput("shop, player");

        PlotCreatorService.applyDefaultTagsForKind(draft);

        assertTrue(draft.isTouristDestination());
        assertFalse(draft.getBuildingTags().contains("work"));
    }

    private static boolean hasSpot(@Nonnull PlotCreatorDraft draft, @Nonnull PlotCreatorSubstepType type) {
        return draft.getSelectedSpots().stream().anyMatch(spot -> spot.type() == type);
    }
}
