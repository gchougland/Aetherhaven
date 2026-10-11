package com.hexvane.aetherhaven.production;

import com.google.gson.Gson;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.hexvane.aetherhaven.production.TimberHarvestPlan.*;

@Tag("town")
class TimberDeliveryTest {
    private static final String WOOD="Wood_Ash_Trunk";
    private static final Position ROOT=new Position(2,100,3);
    private PlotProductionState planted() {
        var production=new PlotProductionState();
        var tree=new TimberPlantingReceipt(UUID.randomUUID(),"Plant_Sapling_Ash",WOOD,ROOT,0,List.of(
            new TimberPlantingReceipt.Stage(0,"seed",List.of(new Cell(ROOT,"Plant_Sapling_Ash",0,Kind.FOLIAGE))),
            new TimberPlantingReceipt.Stage(1,"tree",List.of(new Cell(ROOT,WOOD,0,Kind.TIMBER)))));
        var state=production.timber();
        java.util.function.Function<Position,TimberProductionState.Ground> read=p->new TimberProductionState.Ground(true,
            p.y()<100?"Soil_Dirt":"Empty",0,0,p.y()>=100,p.y()<100,p.y()<100,false);
        var prepared=state.prepare(0,1,tree,p->true,read);
        assertNull(state.plant(0,1,prepared,p->true,read,()->true));return production;
    }
    @Test void aLargeTreeNeedsMultiplePhysicalTripsAndNeverOverflowsStorage() {
        var p=planted();var state=p.timber();var tree=state.trees().get(0).tree().id();
        p.addAmount(WOOD,95,100);
        assertTrue(state.fell(0,tree,123,()->true));assertEquals(95,p.getAmount(WOOD));
        assertEquals(0,state.deliver(p,100,16),"No credit before pickup");
        assertEquals(16,state.pickUp(16));assertEquals(0,state.pickUp(16),"Cannot pick up another armful in transit");
        assertEquals(5,state.deliver(p,100,16));assertEquals(100,p.getAmount(WOOD));
        assertEquals(118,state.cargo().remaining());assertEquals(11,state.cargo().carried());
        assertEquals(0,state.deliver(p,100,16));
        var gson=new Gson();p=gson.fromJson(gson.toJson(p),PlotProductionState.class);state=p.timber();
        assertEquals(ROOT,state.cargo().source());assertEquals(11,state.cargo().carried());
        p.removeAmountUpTo(WOOD,100);int delivered=5;
        while(state.cargo()!=null) {
            if(state.cargo().carried()==0)state.pickUp(16);
            int count=state.deliver(p,100,16);assertTrue(count>0);delivered+=count;
            p.removeAmountUpTo(WOOD,100);
        }
        assertEquals(123,delivered);assertTrue(state.adopted());assertTrue(state.trees().isEmpty());
    }
    @Test void staleOrFailedHarvestDoesNotAuthorizeCargoOrLoseOwnership() {
        var p=planted();var t=p.timber();var id=t.trees().get(0).tree().id();var attempts=new AtomicInteger();
        assertFalse(t.fell(0,UUID.randomUUID(),123,()->{attempts.incrementAndGet();return true;}));
        assertFalse(t.fell(0,id,0,()->{attempts.incrementAndGet();return true;}));
        assertFalse(t.fell(0,id,123,()->{attempts.incrementAndGet();return false;}));
        assertEquals(1,attempts.get());assertNull(t.cargo());assertEquals(1,t.trees().size());
        assertTrue(t.fell(0,id,123,()->true));
        assertFalse(t.fell(0,id,123,()->{attempts.incrementAndGet();return true;}));
        assertEquals(1,attempts.get());assertEquals(123,t.cargo().remaining());assertEquals(0,p.getAmount(WOOD));
    }
    @Test void pausedWorkAndSavedChoppingDoNotBecomeOutput() {
        var p=planted();var t=p.timber();t.work(0,.5);t.work(0,.25);t.work(0,Double.NaN);t.work(0,-1);t.work(1,1);
        assertEquals(.75,t.chopping(0));assertEquals(0,t.chopping(1));
        var restored=new Gson().fromJson(new Gson().toJson(p),PlotProductionState.class);
        assertEquals(.75,restored.timber().chopping(0));assertEquals(0,restored.getAmount(WOOD));
        assertNull(restored.timber().cargo());
    }
    @Test void breakingARootstoneGivesUpItsTreeWithoutAHarvest() {
        var p=planted();var id=p.timber().trees().get(0).tree().id();
        assertFalse(p.uprootStoneAt(new Position(80,100,80)));
        assertFalse(p.uprootStoneAt(ROOT),"Only the stone beneath the tree is its Rootstone");
        assertTrue(p.uprootStoneAt(new Position(ROOT.x(),ROOT.y()-1,ROOT.z())));
        var restored=new Gson().fromJson(new Gson().toJson(p),PlotProductionState.class);
        assertTrue(restored.timber().trees().isEmpty());assertTrue(restored.usesPhysicalTimber());
        assertFalse(restored.timber().fell(0,id,123,()->fail("An uprooted tree belongs to the player")));
        assertNull(restored.timber().cargo());
    }

    @Test void additionalProductsRequireSeparateTripsWithoutCreatingNurseryRefunds() {
        var p=planted();var t=p.timber();var tree=t.trees().get(0).tree();
        var extras=new LinkedHashMap<String,Integer>();extras.put("Ingredient_Fibre",18);extras.put(tree.sapling(),1);
        assertTrue(t.fell(0,tree.id(),2,extras,()->true));
        assertEquals(Map.of(WOOD,2,"Ingredient_Fibre",18,tree.sapling(),1),t.waitingProducts());
        assertEquals(2,t.pickUp(16));assertEquals(2,t.deliver(p,100,16));
        assertEquals("Ingredient_Fibre",t.cargo().output());assertEquals(0,t.cargo().carried());
        assertEquals(0,t.deliver(p,100,16));
        assertEquals(16,t.pickUp(16));assertEquals(5,t.deliver(p,5,16));assertEquals(11,t.cargo().carried());
        p=new Gson().fromJson(new Gson().toJson(p),PlotProductionState.class);t=p.timber();
        assertEquals(13,t.waitingProducts().get("Ingredient_Fibre"));assertEquals(0,t.deliver(p,5,16));
        assertEquals(11,t.deliver(p,100,16));assertEquals(2,t.pickUp(16));assertEquals(2,t.deliver(p,100,16));
        assertEquals(tree.sapling(),t.cargo().output());
        assertEquals(1,t.pickUp(16));assertEquals(1,t.deliver(p,10,16));
        assertNull(t.cargo());assertTrue(t.waitingProducts().isEmpty());
        assertEquals(1,p.getAmount(tree.sapling()));assertEquals(18,p.getAmount("Ingredient_Fibre"));
        assertEquals(0,t.deliver(p,100,16));
    }
    @Test void failedFellingCreatesNoProductsAndObsoleteSupplyFieldsAreDiscarded() {
        var p=planted();var t=p.timber();var tree=t.trees().get(0).tree();
        assertFalse(t.fell(0,tree.id(),2,Map.of("Ingredient_Stick",3),()->false));assertTrue(t.waitingProducts().isEmpty());
        assertFalse(t.fell(0,tree.id(),2,Map.of(WOOD,4),()->fail("Wood cannot be duplicated as a byproduct")));
        assertFalse(t.fell(0,tree.id(),2,Map.of("Ingredient_Stick",0),()->fail("Invalid quantities cannot change terrain")));
        assertTrue(t.fell(0,tree.id(),2,()->true));
        var json=com.google.gson.JsonParser.parseString(new Gson().toJson(p)).getAsJsonObject();
        // Unreleased nursery and refund fields are intentionally ignored, without granting items.
        var timberJson=json.entrySet().stream().filter(e->e.getValue().isJsonObject()&&e.getValue().getAsJsonObject().has("cargo")).findFirst().orElseThrow().getValue().getAsJsonObject();
        timberJson.add("nursery",new Gson().toJsonTree(Map.of(tree.sapling(),5)));
        timberJson.add("seedCargo",new Gson().toJsonTree(Map.of("slot",0,"plan",tree.id().toString(),"sapling",tree.sapling())));
        timberJson.getAsJsonObject("cargo").addProperty("sapling",tree.sapling());
        p=new Gson().fromJson(json,PlotProductionState.class);t=p.timber();
        assertFalse(new Gson().toJsonTree(t.cargo()).getAsJsonObject().has("sapling"));
        assertEquals(2,t.pickUp(16));assertEquals(2,t.deliver(p,100,16));assertNull(t.cargo());assertEquals(0,p.getAmount(tree.sapling()));
        var saved=new Gson().toJsonTree(t).getAsJsonObject();
        assertFalse(saved.has("nursery"));assertFalse(saved.has("seedCargo"));
    }
    @Test void fibreIsAnOptionalChoiceAndOneFullStoreNeverHoldsATreeBack() {
        var p=planted();var tree=p.timber().trees().get(0).tree();
        var entry=ProductionCatalog.Entry.create(List.of(WOOD),new int[]{1},new long[]{100});
        assertEquals(Set.of(tree.sapling(),"Ingredient_Tree_Sap","Ingredient_Stick"),NativeTimberProducts.selected(p,entry,tree));
        p.timber().setGathersFibre(true);
        assertEquals(Set.of("Ingredient_Fibre",tree.sapling(),"Ingredient_Tree_Sap","Ingredient_Stick"),NativeTimberProducts.selected(p,entry,tree));
        assertTrue(NativeTimberProducts.hasRoom(p,entry,tree));p.addAmount("Ingredient_Fibre",100,100);
        assertTrue(NativeTimberProducts.hasRoom(p,entry,tree),"Full fibre alone does not stop the grove");
        assertEquals(0,NativeTimberProducts.collectionLimits(p,entry,tree).get("Ingredient_Fibre"));
        p=new Gson().fromJson(new Gson().toJson(p),PlotProductionState.class);assertTrue(p.timber().gathersFibre());
        p.addAmount(tree.sapling(),10,10);p.addAmount("Ingredient_Tree_Sap",100,100);p.addAmount("Ingredient_Stick",100,100);
        assertTrue(NativeTimberProducts.hasRoom(p,entry,tree),"There is still room for wood");
        p.addAmount(WOOD,100,100);assertFalse(NativeTimberProducts.hasRoom(p,entry,tree),"Only completely full stores hold a tree back");
        p.removeAmountUpTo("Ingredient_Stick",1);assertTrue(NativeTimberProducts.hasRoom(p,entry,tree));
        assertTrue(PhysicalProductionOutputPolicy.available(p,"Ingredient_Stick"));assertTrue(PhysicalProductionOutputPolicy.available(p,tree.sapling()));
        assertTrue(PhysicalProductionOutputPolicy.available(p,"Wood_Burnt_Trunk"));assertFalse(NativeTimberProducts.supported(null));
    }
    @Test void aGroveStoresAnyTreeItGrowsWithoutAPurchasedUnlock() {
        var p=planted();var entry=ProductionCatalog.Entry.create(List.of(WOOD),new int[]{1},new long[]{100});
        for(String item:List.of("Wood_Maple_Trunk","Ingredient_Tree_Sap","Ingredient_Stick","Ingredient_Fibre",TimberCharring.OUTPUT))
            assertEquals(WorkplaceProductionUpgrades.GROVE_STORAGE,WorkplaceProductionUpgrades.effectiveMaxStorage(p,entry,item),item);
        p.setAdamantineLevel(2);
        assertEquals(300,WorkplaceProductionUpgrades.effectiveMaxStorage(p,entry,"Wood_Maple_Trunk"));
        assertEquals(2,WorkplaceProductionUpgrades.stoneCount(new PlotProductionState()));
        p.setIronUpgrade(1);p.setCobaltLevel(3);
        assertEquals(TimberProductionState.MAX_STONES,WorkplaceProductionUpgrades.stoneCount(p));
    }

    @Test void automaticSaplingsHaveNormalCapacityWithoutPurchasingTheirOutputUnlock() {
        var p=planted();var seed=p.timber().trees().get(0).tree().sapling();
        var entry=ProductionCatalog.Entry.create(List.of(WOOD),new int[]{1},new long[]{100});
        assertFalse(entry.containsItemId(seed));assertFalse(p.isWorkplaceOutputUnlocked(seed));
        assertEquals(10,WorkplaceProductionUpgrades.effectiveMaxStorage(p,entry,seed));
        p.setAdamantineLevel(1);
        assertEquals(20,WorkplaceProductionUpgrades.effectiveMaxStorage(p,entry,seed));
        assertTrue(NativeTimberProducts.hasRoom(p,entry,p.timber().trees().get(0).tree()));
        p.addAmount(seed,20,20);
        assertTrue(NativeTimberProducts.hasRoom(p,entry,p.timber().trees().get(0).tree()),"Full saplings alone do not stop the grove");
    }
    @Test void theWholeHarvestOfATreeComesHomeInOneTrip() {
        var p=planted();var t=p.timber();var tree=t.trees().get(0).tree();
        var extras=new LinkedHashMap<String,Integer>();extras.put(tree.sapling(),1);extras.put("Ingredient_Tree_Sap",3);
        extras.put("Ingredient_Stick",7);extras.put("Ingredient_Fibre",18);
        assertFalse(t.carryAll(),"There is nothing to carry before a tree is felled");
        assertTrue(t.fell(0,tree.id(),64,extras,()->true));
        assertEquals(0,t.cargo().carried());assertTrue(t.carryAll());assertFalse(t.carryAll());
        assertEquals(64,t.cargo().carried());
        p=new Gson().fromJson(new Gson().toJson(p),PlotProductionState.class);t=p.timber();
        assertEquals(64+1+3+7+18,t.deliverAll(p,item->100));
        assertNull(t.cargo());assertTrue(t.waitingProducts().isEmpty());
        assertEquals(64,p.getAmount(WOOD));assertEquals(1,p.getAmount(tree.sapling()));assertEquals(3,p.getAmount("Ingredient_Tree_Sap"));
        assertEquals(7,p.getAmount("Ingredient_Stick"));assertEquals(18,p.getAmount("Ingredient_Fibre"));
        assertEquals(0,t.deliverAll(p,item->100));
    }
    @Test void whateverDoesNotFitIsDiscardedSoOneFullStoreNeverBlocksTheRest() {
        var p=planted();var t=p.timber();var tree=t.trees().get(0).tree();
        p.addAmount(WOOD,90,100);p.addAmount("Ingredient_Stick",100,100);
        assertTrue(t.fell(0,tree.id(),40,Map.of("Ingredient_Stick",5,tree.sapling(),1),()->true));t.carryAll();
        assertEquals(11,t.deliverAll(p,item->100),"Ten logs and the sapling fit");
        assertEquals(100,p.getAmount(WOOD));assertEquals(100,p.getAmount("Ingredient_Stick"));assertEquals(1,p.getAmount(tree.sapling()));
        assertNull(t.cargo(),"Nothing is left waiting, so the next tree can start at once");assertTrue(t.waitingProducts().isEmpty());
        p=new Gson().fromJson(new Gson().toJson(p),PlotProductionState.class);
        assertNull(p.timber().cargo());assertEquals(0,p.timber().deliverAll(p,item->100));
    }
}
