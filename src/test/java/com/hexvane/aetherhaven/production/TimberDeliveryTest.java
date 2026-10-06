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
    @Test void recordedPlayerEditsSurviveReloadAndPreventEvenARebuiltTreeFromPayingOut() {
        var p=planted();var state=p.timber();var id=state.trees().get(0).tree().id();
        assertFalse(state.markEdited(new Position(80,100,80)));
        assertTrue(state.markEdited(new Position(ROOT.x(),ROOT.y()-1,ROOT.z())));
        assertFalse(state.markEdited(ROOT));
        var restored=new Gson().fromJson(new Gson().toJson(p),PlotProductionState.class);
        assertTrue(restored.timber().edited(id));
        assertFalse(restored.timber().fell(0,id,123,()->fail("An edited planting cannot authorize removal")));
        assertNull(restored.timber().cargo());assertEquals(1,restored.timber().trees().size());
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
    @Test void selectedExtraSlotsRespectSpeciesAndStorageWithoutMultiplyingOutput() {
        var p=planted();var tree=p.timber().trees().get(0).tree();
        var entry=ProductionCatalog.Entry.create(List.of(WOOD,"Ingredient_Fibre",tree.sapling(),"Plant_Sapling_Oak"),new int[]{1,1,1,1},new long[]{100,100,10,10});
        p.setIronUpgrade(1);p.setCobaltLevel(3);
        p.setSlotCursor(0,0);p.setSlotCursor(1,1);p.setSlotCursor(2,1);p.setSlotCursor(3,2);p.setSlotCursor(4,3);
        assertEquals(Set.of("Ingredient_Fibre",tree.sapling(),"Ingredient_Tree_Sap","Ingredient_Stick"),NativeTimberProducts.selected(p,entry,tree));
        assertTrue(NativeTimberProducts.hasRoom(p,entry,tree));p.addAmount("Ingredient_Fibre",100,100);
        assertFalse(NativeTimberProducts.hasRoom(p,entry,tree));p.setSlotCursor(1,0);p.setSlotCursor(2,0);
        assertTrue(NativeTimberProducts.hasRoom(p,entry,tree));p.addAmount(tree.sapling(),10,10);assertFalse(NativeTimberProducts.hasRoom(p,entry,tree));
        assertTrue(PhysicalProductionOutputPolicy.available(p,"Ingredient_Stick"));assertTrue(PhysicalProductionOutputPolicy.available(p,tree.sapling()));
        assertTrue(PhysicalProductionOutputPolicy.available(p,"Wood_Burnt_Trunk"));assertFalse(NativeTimberProducts.supported(null));
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
        assertFalse(NativeTimberProducts.hasRoom(p,entry,p.timber().trees().get(0).tree()));
    }
}
