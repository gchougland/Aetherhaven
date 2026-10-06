package com.hexvane.aetherhaven.workplace;

import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("crossmod")
class NativeWorkplaceRecipesTest {
    private CraftingRecipe recipe(String benchId,int tier) {
        var bench=new BenchRequirement();bench.id=benchId;bench.type=BenchType.Crafting;bench.requiredTierLevel=tier;
        var output=new MaterialQuantity("Example_Mod_Item",null,null,2,null);
        return new CraftingRecipe(new MaterialQuantity[]{new MaterialQuantity("Ingredient_Fibre",null,null,3,null)},
            output,new MaterialQuantity[]{output},2,new BenchRequirement[]{bench},4,false,1);
    }
    @Test void discoversPackRecipesByTheirNativeBenchAndTier() {
        var result=NativeWorkplaceRecipes.merge(Map.of(),Map.of("mod_recipe",recipe("Workbench",3)));
        assertEquals(1,result.size());
        var discovered=result.values().iterator().next();
        assertEquals("crafting",discovered.workplace());
        assertEquals("mod_recipe",discovered.sourceRecipe());
        assertEquals(3,discovered.benchTier());
        assertEquals(5,discovered.skillLevel());
    }
    @Test void explicitRulesKeepTheirIdsSkillRequirementsAndDoNotDuplicate() {
        var rule=new WorkplaceRecipe("existing_order_id","Example","blacksmithing","mod_recipe","Weapon_Bench",1,7);
        var result=NativeWorkplaceRecipes.merge(Map.of(rule.id(),rule),Map.of("mod_recipe",recipe("Weapon_Bench",1)));
        assertEquals(Map.of(rule.id(),rule),result);
    }
    @Test void recipeRemovalDoesNotLeaveAnAutomaticallyDiscoveredEntry() {
        assertFalse(NativeWorkplaceRecipes.merge(Map.of(),Map.of("mod_recipe",recipe("Workbench",1))).isEmpty());
        assertTrue(NativeWorkplaceRecipes.merge(Map.of(),Map.of()).isEmpty());
    }
    @Test void fieldcraftDoesNotBecomeAWorkplaceRecipe() {
        assertTrue(NativeWorkplaceRecipes.merge(Map.of(),Map.of("field_recipe",recipe("Fieldcraft",1))).isEmpty());
    }
    @Test void nativeBenchUpgradesAndTierMustBeSatisfiedByTheSameBench() {
        var source=recipe("Workbench",3);
        source.getBenchRequirement()[0].requiredAugmentTags=new String[]{"Precision"};
        assertFalse(WorkplaceRecipe.acceptsNativeBench(source,new WorkplaceEquipment.Bench("Workbench",3,0,0,0)));
        assertFalse(WorkplaceRecipe.acceptsNativeBench(source,new WorkplaceEquipment.Bench("Workbench",2,0,0,0,Set.of("Precision"))));
        assertFalse(WorkplaceRecipe.acceptsNativeBench(source,new WorkplaceEquipment.Bench("Weapon_Bench",3,0,0,0,Set.of("Precision"))));
        assertTrue(WorkplaceRecipe.acceptsNativeBench(source,new WorkplaceEquipment.Bench("Workbench",3,0,0,0,Set.of("Precision"))));
    }
    @Test void alternativeBenchRequirementsUseTheLowestAvailableTier() {
        var source=recipe("Workbench",3);var alternative=recipe("Workbench",1).getBenchRequirement()[0];
        var alternatives=new CraftingRecipe(source.getInput(),source.getPrimaryOutput(),source.getOutputs(),2,
            new BenchRequirement[]{source.getBenchRequirement()[0],alternative},4,false,1);
        assertEquals(1,NativeWorkplaceRecipes.merge(Map.of(),Map.of("mod_recipe",alternatives)).values().iterator().next().benchTier());
    }
}
