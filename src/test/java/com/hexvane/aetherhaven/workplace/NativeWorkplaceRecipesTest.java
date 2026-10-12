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
    @Test void salvageRecipesGoToSalvagingAndAreNamedForWhatIsTakenApart() {
        var result=NativeWorkplaceRecipes.merge(Map.of(),Map.of("Salvage_Old_Sword",recipe("Salvagebench",1),
            "Salvage_Aetherhaven_Plot_Token_House",recipe("Salvagebench",1)));
        assertEquals(1,result.size(),"Plot tokens are not workshop salvage");
        var discovered=result.values().iterator().next();
        assertEquals("salvaging",discovered.workplace());
        assertEquals("Salvage Ingredient Fibre",discovered.name());
        assertTrue(WorkplaceBenchAccess.matches(com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType.SALVAGING,"Salvagebench","Bench_Salvage"));
        assertFalse(WorkplaceBenchAccess.matches(com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType.CRAFTING,"Salvagebench","Bench_Salvage"));
        assertEquals("crafter",WorkplaceRules.supervisor(com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType.SALVAGING));
    }
    @Test void buildersWorkbenchRecipesGoToTheBlockWorkshopAndNowhereElse() {
        var stairs=recipe(WorkplaceEquipment.BUILDERS_BENCH,1);
        stairs.getBenchRequirement()[0].type=BenchType.StructuralCrafting;
        var result=NativeWorkplaceRecipes.merge(Map.of(),Map.of("Wood_Oak_Stairs",stairs));
        assertEquals(1,result.size());
        var discovered=result.values().iterator().next();
        assertEquals("blockWorkshop",discovered.workplace());
        assertEquals("native/blockWorkshop/Wood_Oak_Stairs/Builders",discovered.id());
        assertEquals(com.hexvane.aetherhaven.townsfolk.TownsfolkSkill.BUILDING,discovered.skill());
        assertEquals(1,discovered.skillLevel());
        // The recipe is the game's own: nothing about its cost or output is restated.
        assertNull(discovered.inputs());assertNull(discovered.output());
        assertTrue(WorkplaceRecipe.acceptsNativeBench(stairs,new WorkplaceEquipment.Bench(WorkplaceEquipment.BUILDERS_BENCH,1,0,0,0)));
        assertFalse(WorkplaceRecipe.acceptsNativeBench(stairs,new WorkplaceEquipment.Bench("Workbench",3,0,0,0)));
        // A structural recipe never leaks onto an ordinary bench, and the yard's bench takes no ordinary recipes.
        var misplaced=recipe("Workbench",1);misplaced.getBenchRequirement()[0].type=BenchType.StructuralCrafting;
        assertTrue(NativeWorkplaceRecipes.merge(Map.of(),Map.of("odd",misplaced)).isEmpty());
        assertTrue(NativeWorkplaceRecipes.merge(Map.of(),Map.of("legacy",recipe(WorkplaceEquipment.BUILDERS_BENCH,1))).isEmpty());
        assertTrue(NativeWorkplaceRecipes.merge(Map.of(),Map.of("diagram",diagram())).isEmpty());
    }
    private CraftingRecipe diagram() {
        var source=recipe("Workbench",1);source.getBenchRequirement()[0].type=BenchType.DiagramCrafting;return source;
    }
    @Test void smeltingSkillClimbsWithTheMetal() {
        var bars=new LinkedHashMap<String,CraftingRecipe>();
        for(String metal:List.of("Silver","Gold","Cobalt","Adamantite","Mithril","Onyxium"))
            bars.put("Ingredient_Bar_"+metal+"_Recipe_Generated_0",recipe("Furnace",1));
        var skill=new HashMap<String,Integer>();
        NativeWorkplaceRecipes.merge(Map.of(),bars).values().forEach(r->skill.put(r.sourceRecipe().split("_")[2],r.skillLevel()));
        assertEquals(Map.of("Silver",4,"Gold",4,"Cobalt",7,"Adamantite",9,"Mithril",10,"Onyxium",1),skill);
        assertEquals(1,NativeWorkplaceRecipes.merge(Map.of(),Map.of("Ingredient_Bar_Cobalt_Recipe_Generated_0",recipe("Workbench",1)))
            .values().iterator().next().skillLevel(),"Only smelting uses the metal ladder");
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
