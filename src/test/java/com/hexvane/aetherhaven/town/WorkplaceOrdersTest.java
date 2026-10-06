package com.hexvane.aetherhaven.town;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.workplace.*;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.hexvane.aetherhaven.workplace.WorkplaceOrders.Mode.*;
import static com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType.*;

@Tag("town")
class WorkplaceOrdersTest {
    @Test void removedHerbGardensHaveNoPackagedRecipes() {
        assertTrue(WorkplaceRecipeCatalog.load(getClass().getClassLoader()).all().stream()
            .noneMatch(recipe -> recipe.type()==CULTIVATION));
    }
    @Test void allJewelryGradesHaveWorkplacePlansAndTheBenchNoLongerOpensPlayerCrafting() throws Exception {
        var root=java.nio.file.Path.of("subplugin-assets/Jewelry/Server");
        try(var files=java.nio.file.Files.list(root.resolve("Aetherhaven/WorkplaceRecipes"))) {
            var paths=files.filter(p->p.toString().endsWith(".json")).toList();
            var ids=new HashSet<String>();
            int gradedPlans=0;
            for(var path:paths) {
                var recipe=new Gson().fromJson(java.nio.file.Files.readString(path),WorkplaceRecipe.class);
                assertTrue(recipe.valid(),path.toString());assertTrue(ids.add(recipe.id()));
                assertEquals(JEWELRY,recipe.type());assertTrue(recipe.townOnly());
                assertTrue(java.nio.file.Files.exists(root.resolve("Item/Items/Aetherhaven/"+recipe.output()+".json")));
                if(recipe.rarity()==null) {
                    assertTrue(Set.of("craft_glow_ring","craft_large_glow_ring").contains(recipe.id()),
                        "Special jewelry plans need their own content checks");
                    continue;
                }
                gradedPlans++;
                assertEquals(switch(recipe.rarity()){case COMMON->1;case UNCOMMON->3;case RARE->5;case MYTHIC->7;case LEGENDARY->10;},recipe.skillLevel());
            }
            assertEquals(140,gradedPlans,"All existing jewelry qualities must remain available");
        }
        var bench=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(
            root.resolve("Item/Items/Aetherhaven/Aetherhaven_Jewelry_Crafting_Bench.json"))).getAsJsonObject().getAsJsonObject("BlockType");
        assertFalse(bench.has("Bench"));
        var use=bench.getAsJsonObject("Interactions").getAsJsonObject("Use").getAsJsonArray("Interactions");
        assertEquals(1,use.size());
        assertEquals("OpenCustomUI",use.get(0).getAsJsonObject().get("Type").getAsString());
        assertEquals("AetherhavenWorkplaceBench",use.get(0).getAsJsonObject().getAsJsonObject("Page").get("Id").getAsString());
    }
    @Test void reputationRecipesAreTownScopedAndRetainTheirUnlockAcrossReloads() {
        var recipe=new WorkplaceRecipe("pack","Pack","leatherworking","workplace:pack","Tannery",1,5,
            List.of(new WorkplaceRecipe.Ingredient("leather",null,12)),"pack_item",1,30,true);
        var town=new TownRecord();
        assertTrue(recipe.valid());
        assertFalse(recipe.unlocked(null));
        assertFalse(recipe.unlocked(town));
        assertFalse(WorkplaceRecipeUnlocks.grant(town,List.of(recipe),"unrelated_item"));
        assertTrue(WorkplaceRecipeUnlocks.grant(town,List.of(recipe),"pack_item"));
        assertTrue(recipe.unlocked(town));
        assertTrue(recipe.unlocked(new Gson().fromJson(new Gson().toJson(town),TownRecord.class)));
        assertFalse(recipe.unlocked(new TownRecord()));
        assertFalse(town.sharesCraftRecipeItem("pack_item"));
        assertTrue(WorkplaceRecipeUnlocks.grant(town,List.of(recipe),"pack_item"));
        assertTrue(WorkplaceRecipeUnlocks.isWorkplaceOutput(List.of(recipe),"pack_item"));
    }
    @Test void townOnlyRecipesRetainTheirCostsYieldAndWorkTimeWithoutAPlayerRecipe() {
        var recipe=new Gson().fromJson("""
            {"id":"kit","name":"Kit","workplace":"blacksmithing","sourceRecipe":"workplace:kit",
             "bench":"Weapon_Bench","benchTier":1,"skillLevel":3,"inputs":[{"itemId":"Ingredient_Bar_Iron","count":4}],
             "output":"Aetherhaven_Smith_Kit","outputCount":1,"workSeconds":20}
            """,WorkplaceRecipe.class);
        assertTrue(recipe.valid());assertTrue(recipe.townOnly());
        assertEquals(4,recipe.source().getInput()[0].getQuantity());
        assertEquals("Aetherhaven_Smith_Kit",recipe.source().getPrimaryOutput().getItemId());
        assertEquals(1,recipe.source().getOutputs()[0].getQuantity());
        assertEquals(20,recipe.source().getTimeSeconds());
        assertFalse(new WorkplaceRecipe("bad","Bad","blacksmithing","workplace:bad","Weapon_Bench",1,1).valid());
        assertFalse(new WorkplaceRecipe("bad","Bad","blacksmithing","workplace:bad","Weapon_Bench",1,1,
            List.of(new WorkplaceRecipe.Ingredient("iron","wood",2)),"kit",1,20).valid());
    }
    @Test void staleAddCannotDuplicateAnOrderAndStaleMoveCannotReorderIt() {
        var tasks=new WorkplaceOrders();
        assertNull(tasks.add(0,"iron",COUNT,3));
        assertNotNull(tasks.add(0,"iron",COUNT,3));
        assertEquals(1,tasks.list().size());
        assertNull(tasks.add(1,"copper",MAINTAIN,20));
        var id=tasks.list().getLast().id();
        assertNotNull(tasks.change(1,id,"Up"));
        assertEquals("iron",tasks.list().getFirst().recipeId());
        assertNull(tasks.change(2,id,"Up"));
        assertEquals(id,tasks.list().getFirst().id());
    }
    @Test void maintainCountsItemsAndAlreadyIncomingBatchesWithoutOverflow() {
        var tasks=new WorkplaceOrders(); tasks.add(0,"supplies",MAINTAIN,10);
        var order=tasks.list().getFirst();
        assertEquals(3,order.batchesNeeded(0,0,4));
        assertEquals(1,order.batchesNeeded(4,4,4));
        assertEquals(0,order.batchesNeeded(8,4,4));
        assertEquals(0,order.batchesNeeded(Long.MAX_VALUE,Long.MAX_VALUE,1));
        assertEquals(10,order.batchesNeeded(-3,-7,1));
        assertEquals(0,order.batchesNeeded(0,0,0));
    }
    @Test void pauseAndResumePreserveOrderIdentityAndQuantityForEveryMode() {
        for(var mode:WorkplaceOrders.Mode.values()) {
            var tasks=new WorkplaceOrders(); tasks.add(0,"recipe",mode,5);
            var order=tasks.list().getFirst(); var id=order.id();
            int needed=order.batchesNeeded(0,0,1);
            assertNull(tasks.change(1,id,"Pause")); assertEquals(0,order.batchesNeeded(0,0,1));
            assertNull(tasks.change(2,id,"Pause")); assertEquals(needed,order.batchesNeeded(0,0,1));
            assertEquals(5,order.quantity()); assertEquals(id,order.id());
        }
    }
    @Test void limitsRejectBadOrExcessiveTasksWithoutChangingRevision() {
        var tasks=new WorkplaceOrders();
        assertNotNull(tasks.add(0,"iron",COUNT,0)); assertNotNull(tasks.add(0,"iron",MAINTAIN,10000));
        assertNotNull(tasks.add(0,"",FOREVER,1)); assertNotNull(tasks.add(0,"iron",null,1));
        assertEquals(0,tasks.revision());
        for(int i=0;i<32;i++) assertNull(tasks.add(i,"iron",COUNT,1));
        assertNotNull(tasks.add(32,"iron",COUNT,1)); assertEquals(32,tasks.revision());
    }
    @Test void removedTasksCannotBeChangedThroughAnOldMenu() {
        var tasks=new WorkplaceOrders(); tasks.add(0,"iron",COUNT,1);
        UUID id=tasks.list().getFirst().id(); tasks.change(1,id,"Remove");
        assertNotNull(tasks.change(2,id,"Pause")); assertEquals(2,tasks.revision()); assertTrue(tasks.list().isEmpty());
    }
    @Test void savesRetainUnknownRecipesAndQueueOrderAcrossWorkplaceTypeAndAreaChanges() {
        var town=new TownRecord(); town.setTownId(UUID.randomUUID());
        town.getClaimedTerritoryChunksMutable().add(ClaimedTerritoryChunkRecord.of(0,0));
        town.getResidentNpcRecords().add(new ResidentNpcRecord("Smith","blacksmith",null,UUID.randomUUID()));
        var sites=town.getApprenticeWorkplaces(); sites.unlock(town,SMELTING); sites.unlock(town,BLACKSMITHING);
        sites.save(town,null,SMELTING,WorkplaceArea.corners(1,100,1,4,4),"Smeltery");
        var site=sites.sites().getFirst(); site.orders().add(0,"another_mod:missing_recipe",MAINTAIN,16);
        var order=site.orders().list().getFirst();
        sites.save(town,site.id(),BLACKSMITHING,WorkplaceArea.corners(2,100,2,5,5),"Forge");
        var restored=new Gson().fromJson(new Gson().toJson(town),TownRecord.class).getApprenticeWorkplaces().find(site.id());
        assertEquals(BLACKSMITHING,restored.type()); assertEquals("Forge",restored.name());
        assertEquals(order.id(),restored.orders().list().getFirst().id());
        assertEquals("another_mod:missing_recipe",restored.orders().list().getFirst().recipeId());
        assertEquals(MAINTAIN,restored.orders().list().getFirst().mode()); assertEquals(16,restored.orders().list().getFirst().quantity());
        assertEquals(1,restored.orders().revision());
    }
    @Test void oldSitesWithoutOrdersMigrateToAnEmptyQueue() {
        var site=new Gson().fromJson("{\"name\":\"Old forge\",\"type\":\"blacksmithing\"}",ApprenticeWorkplace.class);
        assertTrue(site.orders().list().isEmpty()); assertEquals(0,site.orders().revision());
    }
    @Test void duplicateWorkbenchesUseHighestTierRatherThanStackingLevels() {
        var equipment=new WorkplaceEquipment.Snapshot(true,List.of(new WorkplaceEquipment.Bench("Furnace",1,1,100,1),new WorkplaceEquipment.Bench("Furnace",1,2,100,1)));
        assertEquals(1,equipment.tier("Furnace")); assertNotNull(equipment.requirement("Furnace",2));
        assertNull(equipment.requirement("Furnace",1)); assertNotNull(equipment.requirement("Armor_Bench",1));
        var unloaded=new WorkplaceEquipment.Snapshot(false,equipment.benches()); assertNotNull(unloaded.requirement("Furnace",1));
    }
    @Test void changedSiteTypeOrMissingEquipmentBlocksRecipeWithoutDeletingItsOrder() {
        var recipe=new WorkplaceRecipe("iron","Iron","smelting","native_recipe","Furnace",2,3);
        var site=new ApprenticeWorkplace("Forge",BLACKSMITHING,WorkplaceArea.corners(1,100,1,3,3));
        site.orders().add(0,recipe.id(),COUNT,2);
        assertNotNull(recipe.equipmentIssue(site,new WorkplaceEquipment.Snapshot(true,List.of(new WorkplaceEquipment.Bench("Furnace",2,1,100,1)))));
        assertEquals(1,site.orders().list().size());
        assertFalse(new WorkplaceRecipe("bad","Bad","invalid","x","Furnace",1,1).valid());
        assertFalse(new WorkplaceRecipe("bad","Bad","smelting","x","Furnace",1,0).valid());
    }
}
