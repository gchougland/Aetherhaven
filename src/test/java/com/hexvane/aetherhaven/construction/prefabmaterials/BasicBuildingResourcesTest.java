package com.hexvane.aetherhaven.construction.prefabmaterials;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.Gson;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.MaterialRequirement;
import com.hexvane.aetherhaven.construction.PrefabMaterialsCatalog;
import com.hexvane.aetherhaven.difficulty.DifficultyPreset;
import com.hexvane.aetherhaven.difficulty.EffectiveBuildingCosts;
import com.hexvane.aetherhaven.difficulty.TownDifficultySettings;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("construction")
class BasicBuildingResourcesTest {
    private static final Gson GSON = new Gson();

    @Test void conversionSharesSuggestRulesAndMergesSpecificItemsWithBroadResources() {
        var generator = SuggestedResourceMaterialsGenerator.fromClasspath(getClass().getClassLoader());
        var costs = generator.simplifyRequirements(List.of(
            MaterialRequirement.ofItem("Wood_Oak_Planks", 3),
            MaterialRequirement.ofResourceType("Wood_Trunk", 4),
            MaterialRequirement.ofItem("Rock_Stone_Cobble", 5),
            MaterialRequirement.ofResourceType("Rock_Stone", 2),
            MaterialRequirement.ofItem("Bench_Armour", 1),
            MaterialRequirement.ofItem("Aetherhaven_Management_Block", 1),
            MaterialRequirement.ofItem("Cloth_Block_Wool_White", 9)));
        assertEquals(3, costs.size());
        assertTrue(costs.stream().anyMatch(m -> "Wood_All".equals(m.getResourceTypeId()) && m.getCount() == 7));
        assertTrue(costs.stream().anyMatch(m -> "Rock".equals(m.getResourceTypeId()) && m.getCount() == 7));
        assertTrue(costs.stream().anyMatch(m -> "Bench_Armour".equals(m.getItemId()) && m.getCount() == 1));
    }

    @Test void easyDefaultsApplyToNewAndExistingSavesAndExplicitCustomChoicePersists() {
        var settings = new TownDifficultySettings();
        settings.applyPreset(DifficultyPreset.EASY);
        assertTrue(settings.isSimplifyBuildingResources());
        assertTrue(GSON.fromJson("{\"preset\":\"easy\"}", TownDifficultySettings.class).isSimplifyBuildingResources());
        settings.setPreset(DifficultyPreset.CUSTOM);
        settings.setSimplifyBuildingResources(false);
        var restored = GSON.fromJson(GSON.toJson(settings), TownDifficultySettings.class);
        assertFalse(restored.isSimplifyBuildingResources());
        var copy = new TownDifficultySettings();
        copy.copyFrom(restored);
        assertFalse(copy.isSimplifyBuildingResources());
        copy.applyPreset(DifficultyPreset.NORMAL);
        assertFalse(copy.isSimplifyBuildingResources());
        copy.applyPreset(DifficultyPreset.HARD);
        assertFalse(copy.isSimplifyBuildingResources());
    }

    @Test void difficultyConvertsBeforeScalingWithoutMutatingTheBuilding() {
        var definition = GSON.fromJson("""
            {"id":"test", "materials":[
              {"itemId":"Wood_Oak_Planks","count":1},
              {"itemId":"Wood_Beech_Trunk","count":1}], "treasuryGoldCoinCost":10}
            """, ConstructionDefinition.class);
        var settings = new TownDifficultySettings();
        settings.applyPreset(DifficultyPreset.EASY);
        settings.setDifficultyChosen(true);
        var costs = EffectiveBuildingCosts.forDefinition(definition, settings, PrefabMaterialsCatalog.empty());
        assertEquals(1, costs.getMaterials().size());
        assertEquals("Wood_All", costs.getMaterials().getFirst().getResourceTypeId());
        assertEquals(1, costs.getMaterials().getFirst().getCount());
        assertEquals(5, costs.getTreasuryGoldCoinCost());
        assertEquals(2, definition.getMaterials().size());
        assertEquals("Wood_Oak_Planks", definition.getMaterials().getFirst().getItemId());
        settings.setRequireAllPrefabBlocks(true);
        costs = EffectiveBuildingCosts.forDefinition(definition, settings, PrefabMaterialsCatalog.empty());
        assertEquals(2, costs.getMaterials().getFirst().getCount());
        settings.setSimplifyBuildingResources(false);
        settings.setRequireAllPrefabBlocks(false);
        settings.setResourceCostMultiplier(1);
        costs = EffectiveBuildingCosts.forDefinition(definition, settings, PrefabMaterialsCatalog.empty());
        assertEquals("Wood_Oak_Planks", costs.getMaterials().getFirst().getItemId());
    }
}
