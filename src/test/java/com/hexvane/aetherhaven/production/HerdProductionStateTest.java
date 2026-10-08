package com.hexvane.aetherhaven.production;

import com.google.gson.Gson;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class HerdProductionStateTest {
    private static final String CARROT="Plant_Crop_Carrot_Item";
    private UUID buy(HerdProductionState herd,String species) {
        assertNull(herd.buy(herd.revision(),species,10,()->true));
        return herd.animals().getLast().id();
    }
    private UUID active(PlotProductionState stock,String species) {
        var herd=stock.herd();var id=buy(herd,species);
        assertTrue(herd.assign(herd.revision(),0,id,1));return id;
    }
    private void feed(HerdProductionState herd,UUID animal,long day) {
        assertTrue(herd.supply(BarnAnimalCatalog.get(herd.animal(animal).species()).food(),()->true));
        assertTrue(herd.feedAnimal(animal,day,5));
    }
    private void advance(PlotProductionState stock,long start,long end,UUID id) {
        for(long m=start;m<=end;m++)stock.herd().advance(m,1,Set.of(id),stock,1000,1);
    }
    @Test void barnStorageGroupsSharedProductsAndCapacityDoesNotRequireSpeed(){
        var state=new PlotProductionState();buy(state.herd(),"Rabbit");buy(state.herd(),"Pig");
        var items=BarnAnimalCatalog.storageItems(state);
        assertEquals(1,Collections.frequency(items,"Ingredient_Hide_Light"));assertTrue(items.contains("Ingredient_Poop"));
        state.setIronUpgrade(1);state.setCobaltLevel(1);
        assertTrue(WorkplaceProductionUpgrades.prerequisitesMet(state,WorkplaceProductionUpgrades.Branch.ADAMANTITE,true));
        assertFalse(WorkplaceProductionUpgrades.prerequisitesMet(state,WorkplaceProductionUpgrades.Branch.THORIUM,true));
        for(int tier=0;tier<=3;tier++){state.setAdamantineLevel(tier);assertEquals(10+10*tier,WorkplaceProductionUpgrades.barnCapacity(state));}
        var tree=com.hexvane.aetherhaven.workplace.BuildingUpgradeTree.production(true);
        assertTrue(tree.stream().noneMatch(n->n.branch().equals("THORIUM")));
        assertTrue(tree.stream().flatMap(n->n.parents().stream()).allMatch(id->tree.stream().anyMatch(n->n.id().equals(id))));
    }
    @Test void purchasesValidateMasteryRevisionAndPaymentBeforeCreatingOwnership() {
        var h=new HerdProductionState();
        assertNotNull(h.buy(0,"Bear",6,()->fail("Locked purchase cannot charge")));
        assertNotNull(h.buy(1,"Rabbit",1,()->fail("Stale purchase cannot charge")));
        assertNotNull(h.buy(0,"Rabbit",1,()->false));assertTrue(h.animals().isEmpty());
        assertNull(h.buy(0,"Rabbit",1,()->true));assertEquals(1,h.animals().size());
        assertNotNull(h.buy(0,"Rabbit",1,()->fail("Repeated button cannot charge")));
    }
    @Test void switchingPensSwapsAnimalsAndRestingPreservesNamesAndHearts() {
        var h=new HerdProductionState();var rabbit=buy(h,"Rabbit");var bear=buy(h,"Bear");
        assertFalse(h.assign(h.revision(),1,rabbit,1));
        assertTrue(h.assign(h.revision(),0,rabbit,2));assertTrue(h.assign(h.revision(),1,bear,2));
        assertTrue(h.rename(h.revision(),rabbit,"  Clover  "));assertEquals("Clover",h.animal(rabbit).name());
        assertTrue(h.pet(rabbit,0,2));
        assertTrue(h.assign(h.revision(),1,rabbit,2));assertEquals(bear,h.slot(0).id());
        assertEquals(rabbit,h.slot(1).id());assertTrue(h.assign(h.revision(),1,null,2));
        assertFalse(h.pet(rabbit,1,2));assertFalse(h.lost(rabbit));
        var saved=new Gson().fromJson(new Gson().toJson(h),HerdProductionState.class);
        assertEquals("Clover",saved.animal(rabbit).name());assertEquals(5,saved.animal(rabbit).friendship());
        assertEquals(-1,saved.assignedSlot(rabbit));
    }
    @Test void feedingAndPettingAreOncePerDayAndUseTheSpecificFood() {
        var stock=new PlotProductionState();var rabbit=active(stock,"Rabbit");var h=stock.herd();
        assertFalse(h.supply(CARROT,()->false));
        assertTrue(h.supply("Plant_Crop_Potato_Item",()->true));assertFalse(h.feedAnimal(rabbit,0,1));
        feed(h,rabbit,0);assertEquals(1,h.feed("Plant_Crop_Potato_Item"));
        assertTrue(h.supply(CARROT,()->true));assertFalse(h.feedAnimal(rabbit,0,1));assertEquals(1,h.feed(CARROT));
        assertTrue(h.pet(rabbit,0,1));assertFalse(h.pet(rabbit,0,1));assertFalse(h.pet(rabbit,-1,1));
        assertFalse(h.feedAnimal(rabbit,-1,1));assertEquals(25,h.animal(rabbit).friendship());
        assertTrue(h.feedAnimal(rabbit,1,1));assertTrue(h.pet(rabbit,1,1));assertEquals(50,h.animal(rabbit).friendship());
        assertFalse(h.supply(HerdProductionState.FEED,()->fail("Wheat is no longer animal food")));
    }
    @Test void foodPaymentsAreBoundedAndEachBinIsIndependent() {
        var h=new HerdProductionState();
        for(int i=0;i<75;i++)assertTrue(h.supply(CARROT,()->true));
        assertFalse(h.supply(CARROT,()->fail("Full bin cannot charge")));
        assertTrue(h.supply("Food_Wildmeat_Raw",()->true));
        assertFalse(h.takeFeed(CARROT,()->false));assertEquals(75,h.feed(CARROT));
        assertTrue(h.takeFeed(CARROT,()->true));assertEquals(74,h.feed(CARROT));
        assertFalse(h.receiveCourierFeed("Wrong",1));assertFalse(h.receiveCourierFeed(CARROT,1));
    }
    @Test void bulkSupplyChargesOnlyFreeSpaceAndLeavesStockUnchangedWhenPaymentFails() {
        var h=new HerdProductionState();var paid=new java.util.concurrent.atomic.AtomicInteger();
        assertEquals(50,h.supply(CARROT,50,n->{paid.addAndGet(n);return true;}));
        assertEquals(25,h.supply(CARROT,200,n->{paid.addAndGet(n);return true;}));
        assertEquals(75,paid.get());assertEquals(75,h.feed(CARROT));
        assertEquals(0,h.supply(CARROT,10,n->{fail("Full bin cannot remove inventory");return true;}));
        assertEquals(0,h.supply("Food_Wildmeat_Raw",20,n->false));assertEquals(0,h.feed("Food_Wildmeat_Raw"));
        assertEquals(0,h.supply("Wrong",20,n->{fail("Invalid food cannot remove inventory");return true;}));
        assertEquals(0,h.supply("Food_Wildmeat_Raw",0,n->{fail("Empty inventory cannot remove food");return true;}));
    }
    @Test void onlyFedLoadedActiveAnimalsProduceAllProductsAndSlowPoop() {
        var stock=new PlotProductionState();var cow=active(stock,"Cow");var h=stock.herd();
        advance(stock,0,360,cow);assertTrue(stock.getAmounts().isEmpty());
        feed(h,cow,0);advance(stock,361,720,cow);
        var products=BarnAnimalCatalog.get("Cow").products();
        for(var p:products.entrySet())assertEquals(p.getValue().longValue(),stock.getAmount(p.getKey()));
        assertEquals(0,stock.getAmount("Ingredient_Poop"));
        for(long m=721;m<=1080;m++)h.advance(m,1,Set.of(),stock,1000,1);
        assertEquals(1,stock.getAmount(ProductionWithdrawal.MILK_BUCKET_ITEM_ID));
        advance(stock,1081,1440,cow);feed(h,cow,1);advance(stock,1441,2160,cow);
        assertEquals(4,stock.getAmount(ProductionWithdrawal.MILK_BUCKET_ITEM_ID));assertEquals(1,stock.getAmount("Ingredient_Poop"));
        advance(stock,2160,2160,cow);assertEquals(4,stock.getAmount(ProductionWithdrawal.MILK_BUCKET_ITEM_ID));
    }
    @Test void clockJumpsAndUnfedDaysDoNotGrantCatchupProduction() {
        var stock=new PlotProductionState();var rabbit=active(stock,"Rabbit");var h=stock.herd();feed(h,rabbit,0);
        advance(stock,0,359,rabbit);assertTrue(stock.getAmounts().isEmpty());
        h.advance(20000,1,Set.of(rabbit),stock,1000,1);assertTrue(stock.getAmounts().isEmpty());
        assertFalse(h.advance(360,1,Set.of(rabbit),stock,1000,1));
        assertFalse(h.feedAnimal(rabbit,-1,1));
    }
    @Test void fullStorageWaitsForTheWholeBundleAndReloadKeepsProgress() {
        var stock=new PlotProductionState();var rabbit=active(stock,"Rabbit");var h=stock.herd();feed(h,rabbit,0);
        stock.addAmount("Ingredient_Hide_Light",1000,1000);advance(stock,0,360,rabbit);
        assertEquals(0,stock.getAmount("Food_Wildmeat_Raw"));
        stock=new Gson().fromJson(new Gson().toJson(stock),PlotProductionState.class);
        stock.removeAmountUpTo("Ingredient_Hide_Light",1);advance(stock,361,361,rabbit);
        assertEquals(1,stock.getAmount("Food_Wildmeat_Raw"));assertEquals(1000,stock.getAmount("Ingredient_Hide_Light"));
    }
    @Test void fractionalHeartBonusesAccumulateWithoutRoundingEveryProductUp() {
        var stock=new PlotProductionState();var rabbit=active(stock,"Rabbit");var h=stock.herd();
        for(int day=0;day<5;day++)feed(h,rabbit,day);
        assertEquals(1,h.animal(rabbit).hearts());
        advance(stock,5760,7200,rabbit);assertEquals(4,stock.getAmount("Food_Wildmeat_Raw"));
        feed(h,rabbit,5);advance(stock,7201,7560,rabbit);assertEquals(6,stock.getAmount("Food_Wildmeat_Raw"));
    }
    @Test void foodProgressionIncludesLateCropsAndTheRequestedExceptions() {
        assertEquals(15,BarnAnimalCatalog.all().size());
        assertEquals(CARROT,BarnAnimalCatalog.get("Rabbit").food());
        assertEquals("Food_Wildmeat_Raw",BarnAnimalCatalog.get("Bear").food());
        for(var s:BarnAnimalCatalog.all()) {
            if(s.rarity()==BarnAnimalCatalog.Rarity.COMMON)assertTrue(s.farmingTier()<=3);
            if(s.rarity()==BarnAnimalCatalog.Rarity.UNCOMMON)assertTrue(s.farmingTier()>=4);
            if(s.rarity()==BarnAnimalCatalog.Rarity.RARE&&!s.id().equals("Bear"))assertEquals(7,s.farmingTier());
        }
        assertTrue(BarnAnimalCatalog.get("Chicken").products().containsKey("Food_Egg"));
        assertTrue(BarnAnimalCatalog.get("Skrill").products().containsKey("Food_Egg"));
    }
    @Test void legacyFoodAndStoredProductsSurviveTheUnreleasedSystemReplacement() {
        var h=new Gson().fromJson("{\"feed\":12,\"adopted\":true,\"herds\":{}}",HerdProductionState.class);
        assertEquals(12,h.feed(HerdProductionState.FEED));assertTrue(h.requestedFoods(5).isEmpty());
        assertTrue(h.takeFeed(HerdProductionState.FEED,()->true));assertEquals(11,h.feed(HerdProductionState.FEED));
        assertTrue(h.animals().isEmpty());assertNull(h.slot(0));
    }
}
