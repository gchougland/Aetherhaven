package com.hexvane.aetherhaven.warehouse;

import static org.junit.jupiter.api.Assertions.*;
import com.hexvane.aetherhaven.alchemy.AlchemyFlask;
import com.hexvane.aetherhaven.alchemy.AlchemyInventory;
import com.hexvane.aetherhaven.alchemy.AlchemyTownStock;
import com.google.gson.Gson;
import com.hypixel.hytale.assetstore.*;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.IndexedLookupTableAssetMap;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality;
import com.hypixel.hytale.event.*;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import java.nio.file.Path;
import java.util.*;
import org.bson.BsonDocument;
import org.junit.jupiter.api.*;

@Tag("town")
class TownWarehouseTest {
    @Test void workplaceRemovalWaitsForCourierReturnsWithoutLosingMaterials() {
        var owner=UUID.randomUUID();var town=new com.hexvane.aetherhaven.town.TownRecord();town.setOwner(owner,"Owner");
        town.getClaimedTerritoryChunks().add(com.hexvane.aetherhaven.town.ClaimedTerritoryChunkRecord.of(0,0));
        town.getResidentNpcRecords().add(new com.hexvane.aetherhaven.town.ResidentNpcRecord("Smith","blacksmith",null,UUID.randomUUID()));
        var sites=town.getApprenticeWorkplaces();var type=com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType.SMELTING;
        assertNull(sites.unlock(town,type));assertNull(sites.save(town,null,type,new com.hexvane.aetherhaven.workplace.WorkplaceArea(0,100,0,4,4),"Smeltery"));
        var site=sites.sites().getFirst();var warehouse=town.getWarehouse();supply(warehouse,stock("ore",6));
        var batch=warehouse.reserve(site.id(),UUID.randomUUID(),"test",List.of(ingredient("ore",6)),List.of(stock("kit",1)));
        assertNotNull(batch);assertEquals(6,warehouse.pickup(batch.id(),"courier",1));
        assertNotNull(sites.remove(town,owner,site.id()));assertSame(site,sites.find(site.id()));
        assertEquals(6,warehouse.batch(batch.id()).cargoItems().getFirst().getQuantity());
        assertTrue(warehouse.returnCargo(batch.id(),"courier"));assertEquals(6,warehouse.count(TownWarehouse.Page.GENERAL,"ore"));
        assertNull(sites.remove(town,owner,site.id()));assertNull(sites.find(site.id()));assertTrue(warehouse.batches().isEmpty());
    }
    @Test void FailedFittingPlacementCannotConsumeOrChangeStock() {
        var town=new TownWarehouse();supply(town,stock("wood",3));var before=town.craftingStock();
        assertTrue(town.hasUnreservedStock("wood"));assertFalse(town.hasUnreservedStock("missing"));
        assertFalse(town.placeFromStock("wood",()->false));assertEquals(before,town.craftingStock());
        assertFalse(town.placeFromStock("missing",()->fail("No material")));assertEquals(before,town.craftingStock());
        assertThrows(IllegalStateException.class,()->town.placeFromStock("wood",()->{throw new IllegalStateException("Placement failed");}));
        assertEquals(before,town.craftingStock());
    }
    @Test void FittingsSpendExactlyOneUnreservedItemAndPreserveMetadataAcrossReload() {
        var town=new TownWarehouse();var stack=new ItemStack("wood",3,12.0,40.0,BsonDocument.parse("{custom: {name:'Saved'}}"));supply(town,stack);
        assertTrue(town.placeFromStock("wood",()->true));
        assertEquals(stack.withQuantity(2),reload(town).stack(TownWarehouse.Page.GENERAL,(short)0));
        var batch=town.reserve(UUID.randomUUID(),UUID.randomUUID(),"recipe",List.of(new MaterialQuantity("wood",null,null,2,com.hexvane.aetherhaven.inventory.ItemStackData.metadata(stack))),List.of(stock("kit",1)));
        assertNotNull(batch);assertEquals(2,town.reserved(TownWarehouse.Page.GENERAL,(short)0));
        assertFalse(town.hasUnreservedStock("wood"));
        assertFalse(town.placeFromStock("wood",()->fail("Reserved stock")));
        assertTrue(town.acceptCompletedOutput(stock("wood",1)));assertTrue(town.hasUnreservedStock("wood"));assertTrue(town.placeFromStock("wood",()->true));
        assertEquals(0,town.count(TownWarehouse.Page.OUTPUT,"wood"));assertEquals(2,town.count(TownWarehouse.Page.GENERAL,"wood"));
        assertFalse(town.placeFromStock("wood",()->fail("No free stock remains")));
        assertEquals(2,town.pickup(batch.id(),"courier",1));
    }
    @Test void loggerCharringPreservesCourierReservationsAndRechecksAfterSelection() {
        String wood="Wood_Beech_Trunk", burnt=com.hexvane.aetherhaven.production.TimberCharring.OUTPUT;
        var p=new Gson().fromJson("{\"timber\":{\"adopted\":true}}",com.hexvane.aetherhaven.production.PlotProductionState.class);
        p.timber().setCharWood(wood);p.addAmount(wood,5,100);
        var entry=new Gson().fromJson("{\"itemIds\":[\"Wood_Beech_Trunk\",\"Wood_Burnt_Trunk\"],\"ticksByIndex\":[1,1],\"maxStorageByIndex\":[100,100]}",com.hexvane.aetherhaven.production.ProductionCatalog.Entry.class);
        var stock=new TownWarehouse();var plot=UUID.randomUUID();var point=new WarehouseBatch.Point(3,100,4);
        var source=new TownWarehouse.ProductionStock(plot,point,p);
        assertEquals(wood,com.hexvane.aetherhaven.production.TimberCharring.input(p,entry,stock,plot));
        var batch=stock.reserve(UUID.randomUUID(),UUID.randomUUID(),"recipe",List.of(ingredient(wood,4)),List.of(stock("kit",1)),List.of(source));
        assertNotNull(batch);assertEquals(4,stock.reservedProduction(plot,wood));
        var position=new com.hexvane.aetherhaven.production.TimberHarvestPlan.Position(3,100,4);
        assertFalse(com.hexvane.aetherhaven.production.TimberCharring.process(p,entry,stock,plot,wood,position));
        assertEquals(5,p.getAmount(wood));assertNull(p.timber().cargo());
        p.addAmount(wood,4,100);
        assertTrue(com.hexvane.aetherhaven.production.TimberCharring.process(p,entry,stock,plot,wood,position));
        assertEquals(4,p.getAmount(wood));assertEquals(4,stock.reservedProduction(plot,wood));
    }

    private static WarehouseCraftingContainer craftingView(TownWarehouse stock,java.util.function.BooleanSupplier allowed,TownWarehouse.ProductionStock... production) {
        var slots=new ArrayList<WarehouseCraftingContainer.Slot>();WarehouseCraftingContainer.addStorage(slots,stock);
        for(var source:production)WarehouseCraftingContainer.addProduction(slots,stock,source.plot(),source.state(),()->true);
        return new WarehouseCraftingContainer(slots,allowed,()->{});
    }
    @Test void nativeCraftingConsumesAcrossPlayerGeneralCraftedAndProductionWithoutCopyingStock() {
        var warehouse=new TownWarehouse();supply(warehouse,stock("ore",5));warehouse.acceptCompletedOutput(stock("ore",5));
        var production=production("ore",5,10);var player=bag(2);put(player,0,stock("ore",5));
        var view=craftingView(warehouse,()->true,production);assertFalse(view.isEmpty());
        var combined=new com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer(player,view);
        assertTrue(combined.removeMaterials(List.of(ingredient("ore",18))).succeeded());
        assertTrue(player.isEmpty());assertEquals(0,warehouse.count(TownWarehouse.Page.GENERAL,"ore"));
        assertEquals(0,warehouse.count(TownWarehouse.Page.OUTPUT,"ore"));assertEquals(2,production.state().getAmount("ore"));
        assertEquals(0,reload(warehouse).count(TownWarehouse.Page.OUTPUT,"ore"));
    }
    @Test void nativeCraftingInsufficientMixedMaterialsDoesNotPartiallyConsumeAnything() {
        var warehouse=new TownWarehouse();supply(warehouse,stock("ore",5));var player=bag(2);put(player,0,stock("wood",3));
        var combined=new com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer(player,craftingView(warehouse,()->true));
        assertFalse(combined.removeMaterials(List.of(ingredient("wood",3),ingredient("ore",6))).succeeded());
        assertEquals(3,player.getItemStack((short)0).getQuantity());assertEquals(5,warehouse.count(TownWarehouse.Page.GENERAL,"ore"));
    }
    @Test void nativeCraftingLeavesCourierReservationsInGeneralAndProduction() {
        var warehouse=new TownWarehouse();supply(warehouse,stock("ore",5));var production=production("ore",10,10);
        assertNotNull(warehouse.reserve(UUID.randomUUID(),UUID.randomUUID(),"general",List.of(ingredient("ore",3)),List.of(stock("kit",1))));
        var batch=warehouse.reserve(UUID.randomUUID(),UUID.randomUUID(),"production",List.of(ingredient("ore",7)),List.of(stock("kit",1)),List.of(production));assertNotNull(batch);
        var view=craftingView(warehouse,()->true,production);
        assertFalse(view.removeMaterials(List.of(ingredient("ore",6))).succeeded());
        assertTrue(view.removeMaterials(List.of(ingredient("ore",5))).succeeded());
        assertEquals(3,warehouse.count(TownWarehouse.Page.GENERAL,"ore"));assertEquals(7,production.state().getAmount("ore"));
        assertTrue(view.isEmpty());
    }
    @Test void twoOpenCraftingViewsObserveTheSameLiveStockAndCannotDoubleSpend() {
        var warehouse=new TownWarehouse();supply(warehouse,stock("ore",5));
        var first=craftingView(warehouse,()->true);var second=craftingView(warehouse,()->true);
        assertTrue(first.removeMaterials(List.of(ingredient("ore",4))).succeeded());
        assertFalse(second.removeMaterials(List.of(ingredient("ore",2))).succeeded());
        assertTrue(second.removeMaterials(List.of(ingredient("ore",1))).succeeded());assertTrue(first.isEmpty());
    }
    @Test void revokedAccessOrRemovedLinkPreventsQueuedCraftConsumption() {
        var allowed=new java.util.concurrent.atomic.AtomicBoolean(true);var warehouse=new TownWarehouse();supply(warehouse,stock("ore",5));
        var view=craftingView(warehouse,allowed::get);assertTrue(view.canRemoveMaterials(List.of(ingredient("ore",5))));
        allowed.set(false);assertTrue(view.isEmpty());assertFalse(view.removeMaterials(List.of(ingredient("ore",5))).succeeded());
        assertEquals(5,warehouse.count(TownWarehouse.Page.GENERAL,"ore"));
    }
    @Test void nativeCraftingViewsAreOutputOnlyAndPreserveMetadata() {
        var warehouse=new TownWarehouse();var item=new ItemStack("ore",5,BsonDocument.parse("{special:true}"));supply(warehouse,item);
        var view=craftingView(warehouse,()->true);
        assertFalse(view.removeMaterials(List.of(ingredient("ore",1))).succeeded());
        assertTrue(view.removeItemStack(item.withQuantity(2)).succeeded());
        assertEquals(item.withQuantity(3),reload(warehouse).stack(TownWarehouse.Page.GENERAL,(short)0));
        assertFalse(view.addItemStack(stock("wood",1)).succeeded());assertFalse(view.clear().succeeded());
        assertEquals(3,warehouse.count(TownWarehouse.Page.GENERAL,"ore"));
    }
    @Test void rawBarnMilkCannotCreateFreeBucketsThroughNativeCrafting() {
        var warehouse=new TownWarehouse();String milk=com.hexvane.aetherhaven.production.ProductionWithdrawal.MILK_BUCKET_ITEM_ID;
        var view=craftingView(warehouse,()->true,production(milk,5,10));
        assertTrue(view.isEmpty());assertFalse(view.canRemoveMaterials(List.of(ingredient(milk,1))));
        warehouse.acceptCompletedOutput(stock(milk,1));assertTrue(view.removeMaterials(List.of(ingredient(milk,1))).succeeded());
    }
    @Test void nativeCraftingConsumesOnlyUnreservedFurnaceOutputAndPersistsIt() {
        var town=new TownWarehouse();var smelt=town.smelting();var origin=UUID.randomUUID();var job=UUID.randomUUID();
        var furnace=bag(2);put(furnace,0,stock("ore",10));assertTrue(smelt.reserve(job,List.of(stock("ore",10))));
        assertEquals(10,smelt.collect(job,origin,new WarehouseBatch.Point(10,100,10),furnace));smelt.release(job);
        var batch=reserve(town,5);assertNotNull(batch);
        var view=craftingView(town,()->true);
        assertFalse(view.removeMaterials(List.of(ingredient("ore",6))).succeeded());
        assertTrue(view.removeMaterials(List.of(ingredient("ore",5))).succeeded());
        assertEquals(5,reload(town).smelting().count("ore"));assertEquals(5,town.reservedSmelting(origin,(short)0));
    }
    @Test void removedProductionBuildingCannotBeConsumedByAnOldCraftingQueue() {
        var town=new TownWarehouse();var production=production("ore",5,10);var present=new java.util.concurrent.atomic.AtomicBoolean(true);
        var slots=new ArrayList<WarehouseCraftingContainer.Slot>();
        WarehouseCraftingContainer.addProduction(slots,town,production.plot(),production.state(),present::get);
        var view=new WarehouseCraftingContainer(slots,()->true,()->{});
        assertTrue(view.canRemoveMaterials(List.of(ingredient("ore",5))));present.set(false);
        assertFalse(view.removeMaterials(List.of(ingredient("ore",5))).succeeded());assertEquals(5,production.state().getAmount("ore"));
    }

    private static ItemStack alchemyFlask(int capacity) {
        var flask=new ItemStack(AlchemyFlask.ITEM,1);
        for(int n=1;n<capacity;n++)flask=AlchemyFlask.upgrade(flask);
        return flask;
    }
    @Test void bottlingConsumesAcrossBagAndBothWarehousePagesAndSurvivesReload() {
        var town=new TownWarehouse();String health="Plant_Crop_Health1",mana="Plant_Crop_Mana1";
        supply(town,stock(health,1));assertTrue(town.acceptCompletedOutput(stock(health,1)));assertTrue(town.acceptCompletedOutput(stock(mana,2)));
        var backpack=bag(2);var hotbar=bag(2);var player=new com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer(backpack,hotbar);
        var flask=alchemyFlask(3);put(backpack,0,stock(health,1));put(backpack,1,stock(mana,1));put(hotbar,0,flask);
        var mix=List.of(health,mana);var costs=AlchemyInventory.brewCosts(mix,3);var filled=AlchemyFlask.fill(flask,mix);var snapshot=town.craftingStock();
        assertEquals(2,snapshot.count(health));assertEquals(2,snapshot.count(mana));
        assertTrue(town.exchangeForCrafting(player,(short)2,flask,filled,costs,snapshot));
        assertEquals(3,AlchemyFlask.charges(hotbar.getItemStack((short)0)));assertNull(backpack.getItemStack((short)0));assertNull(backpack.getItemStack((short)1));
        assertEquals(0,town.count(TownWarehouse.Page.GENERAL,health));assertEquals(0,town.count(TownWarehouse.Page.OUTPUT,health));assertEquals(0,town.count(TownWarehouse.Page.OUTPUT,mana));
        assertFalse(town.exchangeForCrafting(player,(short)2,flask,filled,costs,snapshot));
        assertEquals(0,reload(town).craftingStock().count(mana));assertEquals(0,reload(town).craftingStock().count(health));
    }
    @Test void incompleteBottlingDoesNotPartiallySpendAnySource() {
        var town=new TownWarehouse();String health="Plant_Crop_Health1",mana="Plant_Crop_Mana1";
        supply(town,stock(health,3));assertTrue(town.acceptCompletedOutput(stock(mana,1)));
        var player=bag(3);var flask=alchemyFlask(3);put(player,0,flask);put(player,1,stock(health,2));
        var snapshot=town.craftingStock();var mix=List.of(health,mana);
        assertFalse(town.exchangeForCrafting(player,(short)0,flask,AlchemyFlask.fill(flask,mix),AlchemyInventory.brewCosts(mix,3),snapshot));
        assertEquals(snapshot,town.craftingStock());assertEquals(flask,player.getItemStack((short)0));assertEquals(2,player.getItemStack((short)1).getQuantity());
    }
    @Test void staleStorageOrMovedFlaskCannotSpendBottlingMaterials() {
        var town=new TownWarehouse();String health="Plant_Crop_Health1";supply(town,stock(health,4));
        var player=bag(3);var flask=alchemyFlask(2);put(player,0,flask);var mix=List.of(health);var filled=AlchemyFlask.fill(flask,mix);
        var snapshot=town.craftingStock();supply(town,stock("unrelated",1));
        assertFalse(town.exchangeForCrafting(player,(short)0,flask,filled,Map.of(health,2),snapshot));
        assertEquals(4,town.count(TownWarehouse.Page.GENERAL,health));assertEquals(flask,player.getItemStack((short)0));
        put(player,0,null);put(player,1,flask);
        assertFalse(town.exchangeForCrafting(player,(short)0,flask,filled,Map.of(health,2),town.craftingStock()));
        assertEquals(4,town.count(TownWarehouse.Page.GENERAL,health));assertEquals(flask,player.getItemStack((short)1));
    }
    @Test void bottlingLeavesCourierReservationsIntact() {
        var town=new TownWarehouse();String health="Plant_Crop_Health1";supply(town,stock(health,5));
        var batch=town.reserve(UUID.randomUUID(),UUID.randomUUID(),"recipe",List.of(ingredient(health,3)),List.of(stock("kit",1)));assertNotNull(batch);
        var player=bag(2);var flask=alchemyFlask(3);put(player,0,flask);put(player,1,stock(health,1));var snapshot=town.craftingStock();
        assertEquals(2,snapshot.count(health));
        assertTrue(town.exchangeForCrafting(player,(short)0,flask,AlchemyFlask.fill(flask,List.of(health)),Map.of(health,3),snapshot));
        assertEquals(3,town.count(TownWarehouse.Page.GENERAL,health));assertEquals(3,town.reserved(TownWarehouse.Page.GENERAL,(short)0));
        assertEquals(0,town.craftingStock().count(health));assertEquals(3,town.pickup(batch.id(),"courier",1));
    }
    @Test void bagOnlyBottlingDoesNotChangeTownStockOrItsRevision() {
        var town=new TownWarehouse();String health="Plant_Crop_Health1";supply(town,stock(health,4));
        var player=bag(2);var flask=alchemyFlask(2);put(player,0,flask);put(player,1,stock(health,2));var snapshot=town.craftingStock();
        assertTrue(town.exchangeForCrafting(player,(short)0,flask,AlchemyFlask.fill(flask,List.of(health)),Map.of(health,2),snapshot));
        assertEquals(snapshot,town.craftingStock());assertNull(player.getItemStack((short)1));assertEquals(2,AlchemyFlask.charges(player.getItemStack((short)0)));
    }
    @Test void bottlingCanCombineBagWarehouseAndDistinctProductionBuffersWithoutDoubleCounting() {
        var town=new TownWarehouse();String health="Plant_Crop_Health1",mana="Plant_Crop_Mana1";supply(town,stock(health,1));
        var first=new com.hexvane.aetherhaven.production.PlotProductionState();first.addAmount(health,2,100);
        var second=new com.hexvane.aetherhaven.production.PlotProductionState();second.addAmount(health,2,100);second.addAmount(mana,5,100);
        var source=new AlchemyTownStock.Source(UUID.randomUUID(),first);
        var sources=List.of(source,source,new AlchemyTownStock.Source(UUID.randomUUID(),second));
        var seen=AlchemyTownStock.snapshot(town,sources);assertEquals(5,seen.count(health));
        var player=bag(2);var flask=alchemyFlask(5);put(player,0,flask);put(player,1,stock(health,1));var mix=List.of(health,mana);
        assertTrue(AlchemyTownStock.brew(town,()->sources,seen,player,(short)0,flask,AlchemyFlask.fill(flask,mix),AlchemyInventory.brewCosts(mix,5)));
        assertNull(player.getItemStack((short)1));assertEquals(0,town.count(TownWarehouse.Page.GENERAL,health));assertEquals(0,first.getAmount(health));assertEquals(1,second.getAmount(health));assertEquals(0,second.getAmount(mana));
        assertEquals(5,AlchemyFlask.charges(player.getItemStack((short)0)));assertFalse(AlchemyTownStock.brew(town,()->sources,seen,player,(short)0,flask,AlchemyFlask.fill(flask,mix),AlchemyInventory.brewCosts(mix,5)));
        assertEquals(1,new Gson().fromJson(new Gson().toJson(second),com.hexvane.aetherhaven.production.PlotProductionState.class).getAmount(health));
    }
    @Test void missingProductionIngredientLeavesEveryOtherSourceUntouched() {
        var town=new TownWarehouse();String health="Plant_Crop_Health1",mana="Plant_Crop_Mana1";supply(town,stock(health,1));
        var state=new com.hexvane.aetherhaven.production.PlotProductionState();state.addAmount(health,3,100);state.addAmount(mana,2,100);
        var sources=List.of(new AlchemyTownStock.Source(UUID.randomUUID(),state));var seen=AlchemyTownStock.snapshot(town,sources);
        var player=bag(2);var flask=alchemyFlask(5);put(player,0,flask);put(player,1,stock(health,1));var mix=List.of(health,mana);
        assertFalse(AlchemyTownStock.brew(town,()->sources,seen,player,(short)0,flask,AlchemyFlask.fill(flask,mix),AlchemyInventory.brewCosts(mix,5)));
        assertEquals(seen,AlchemyTownStock.snapshot(town,sources));assertEquals(flask,player.getItemStack((short)0));assertEquals(1,player.getItemStack((short)1).getQuantity());
    }
    @Test void staleProductionSnapshotRejectsWithoutSpendingBagOrGeneralStock() {
        var town=new TownWarehouse();String health="Plant_Crop_Health1";supply(town,stock(health,1));
        var state=new com.hexvane.aetherhaven.production.PlotProductionState();state.addAmount(health,3,100);
        var sources=List.of(new AlchemyTownStock.Source(UUID.randomUUID(),state));var seen=AlchemyTownStock.snapshot(town,sources);
        var player=bag(2);var flask=alchemyFlask(5);put(player,0,flask);put(player,1,stock(health,1));
        state.removeAmountUpTo(health,1);assertEquals(seen.storage(),town.craftingStock());
        assertFalse(AlchemyTownStock.brew(town,()->sources,seen,player,(short)0,flask,AlchemyFlask.fill(flask,List.of(health)),Map.of(health,5)));
        assertEquals(1,town.count(TownWarehouse.Page.GENERAL,health));assertEquals(2,state.getAmount(health));assertEquals(1,player.getItemStack((short)1).getQuantity());
        assertEquals(flask,player.getItemStack((short)0));
    }
    @Test void bottlingLeavesProductionCourierReservationsAvailableForTheirOriginalPickup() {
        var town=new TownWarehouse();String health="Plant_Crop_Health1";
        var state=new com.hexvane.aetherhaven.production.PlotProductionState();state.addAmount(health,5,100);var plot=UUID.randomUUID();
        var pickup=new WarehouseBatch.Point(2,100,2);var physical=new TownWarehouse.ProductionStock(plot,pickup,state);
        var batch=town.reserve(UUID.randomUUID(),UUID.randomUUID(),"recipe",List.of(ingredient(health,3)),List.of(stock("kit",1)),List.of(physical));assertNotNull(batch);
        var sources=List.of(new AlchemyTownStock.Source(plot,state));var seen=AlchemyTownStock.snapshot(town,sources);assertEquals(2,seen.count(health));
        var player=bag(2);var flask=alchemyFlask(3);put(player,0,flask);put(player,1,stock(health,1));
        assertTrue(AlchemyTownStock.brew(town,()->sources,seen,player,(short)0,flask,AlchemyFlask.fill(flask,List.of(health)),Map.of(health,3)));
        assertEquals(3,state.getAmount(health));assertEquals(3,town.reservedProduction(plot,health));assertEquals(0,AlchemyTownStock.snapshot(town,sources).count(health));
        assertEquals(3,town.pickupAt(batch.id(),"courier",1,physical,pickup));
    }
    @Test void profileTrainingUsesTheSameProtectedTransactionForItemsAndFreePoints() {
        var owner=UUID.randomUUID();var resident=UUID.randomUUID();
        var town=new com.hexvane.aetherhaven.town.TownRecord();town.setOwner(owner,"Owner");
        var home=new Gson().fromJson("{\"plotId\":\""+UUID.randomUUID()+"\",\"constructionId\":\"plot_house\",\"state\":\"COMPLETE\"}",com.hexvane.aetherhaven.town.PlotInstance.class);
        home.setHomeResidentAt(0,resident);town.getPlotInstances().add(home);
        var progress=new com.hexvane.aetherhaven.townsfolk.TownsfolkProgress();progress.earnMasteryXp(700);
        var skill=com.hexvane.aetherhaven.townsfolk.TownsfolkSkill.COOKING;
        var inventory=bag(1);put(inventory,0,stock(com.hexvane.aetherhaven.AetherhavenConstants.ITEM_VERDANT_CATALYST,2));
        assertEquals(com.hexvane.aetherhaven.townsfolk.TownsfolkTraining.Result.FORBIDDEN,
            com.hexvane.aetherhaven.townsfolk.TownsfolkTraining.train(town,UUID.randomUUID(),resident,progress,null,skill,1,false,inventory));
        assertEquals(com.hexvane.aetherhaven.townsfolk.TownsfolkTraining.Result.TRAINED,
            com.hexvane.aetherhaven.townsfolk.TownsfolkTraining.train(town,owner,resident,progress,null,skill,1,false,inventory));
        assertEquals(com.hexvane.aetherhaven.townsfolk.TownsfolkTraining.Result.STALE,
            com.hexvane.aetherhaven.townsfolk.TownsfolkTraining.train(town,owner,resident,progress,null,skill,1,false,inventory));
        assertEquals(1,inventory.getItemStack((short)0).getQuantity());assertEquals(2,progress.skill(skill));assertEquals(4,progress.masterySkillPoints());
        assertEquals(com.hexvane.aetherhaven.townsfolk.TownsfolkTraining.Result.TRAINED,
            com.hexvane.aetherhaven.townsfolk.TownsfolkTraining.train(town,owner,resident,progress,null,skill,2,true,null));
        assertEquals(com.hexvane.aetherhaven.townsfolk.TownsfolkTraining.Result.STALE,
            com.hexvane.aetherhaven.townsfolk.TownsfolkTraining.train(town,owner,resident,progress,null,skill,2,true,null));
        progress=new Gson().fromJson(new Gson().toJson(progress),com.hexvane.aetherhaven.townsfolk.TownsfolkProgress.class);
        assertEquals(3,progress.skill(skill));assertEquals(3,progress.masterySkillPoints());assertEquals(2,progress.masteryAttributePoints());
        assertEquals(com.hexvane.aetherhaven.townsfolk.TownsfolkTraining.Result.MISSING_MATERIALS,
            com.hexvane.aetherhaven.townsfolk.TownsfolkTraining.train(town,owner,resident,progress,null,skill,3,false,bag(1)));
        assertEquals(3,progress.skill(skill));assertEquals(3,progress.masterySkillPoints());
    }
    @Test void masteryFollowsCommittedDeliveryAndCraftingWithoutRepeatingAfterTownReload() {
        var townRecord = new com.hexvane.aetherhaven.town.TownRecord();
        var warehouse = townRecord.getWarehouse();
        var progress = new com.hexvane.aetherhaven.townsfolk.TownsfolkProgress();
        townRecord.getTownsfolkProgress().put("worker", progress);
        supply(warehouse, stock("ore", 2));
        var orders = new com.hexvane.aetherhaven.workplace.WorkplaceOrders();
        orders.add(0, "recipe", com.hexvane.aetherhaven.workplace.WorkplaceOrders.Mode.COUNT, 1);
        var order = orders.list().getFirst();
        var batch = warehouse.reserve(UUID.randomUUID(), order.id(), "recipe", List.of(ingredient("ore", 2)), List.of(stock("kit", 1)));
        assertEquals(0, com.hexvane.aetherhaven.townsfolk.TownsfolkMastery.completed(progress,
            com.hexvane.aetherhaven.townsfolk.TownsfolkMastery.Work.DELIVERY, warehouse.deliver(batch.id(), "worker")));
        warehouse.pickup(batch.id(), "worker", 1);
        assertEquals(5, com.hexvane.aetherhaven.townsfolk.TownsfolkMastery.completed(progress,
            com.hexvane.aetherhaven.townsfolk.TownsfolkMastery.Work.DELIVERY, warehouse.deliver(batch.id(), "worker")));
        assertEquals(0, com.hexvane.aetherhaven.townsfolk.TownsfolkMastery.completed(progress,
            com.hexvane.aetherhaven.townsfolk.TownsfolkMastery.Work.DELIVERY, warehouse.deliver(batch.id(), "worker")));
        assertEquals(0, com.hexvane.aetherhaven.townsfolk.TownsfolkMastery.completed(progress,
            com.hexvane.aetherhaven.townsfolk.TownsfolkMastery.Work.CRAFT, warehouse.finishWork(batch.id(), orders, 6)));
        for (int i = 0; i < 24; i++) warehouse.advanceWork(batch.id(), .25);
        assertEquals(5, progress.masteryXp(), "Attended progress does not award an uncommitted batch");
        var restored = new Gson().fromJson(new Gson().toJson(townRecord), com.hexvane.aetherhaven.town.TownRecord.class);
        var restoredProgress = restored.getTownsfolkProgress().get("worker");
        assertEquals(10, com.hexvane.aetherhaven.townsfolk.TownsfolkMastery.completed(restoredProgress,
            com.hexvane.aetherhaven.townsfolk.TownsfolkMastery.Work.CRAFT, restored.getWarehouse().finishWork(batch.id(), orders, 6)));
        var reloaded = new Gson().fromJson(new Gson().toJson(restored), com.hexvane.aetherhaven.town.TownRecord.class);
        assertEquals(0, com.hexvane.aetherhaven.townsfolk.TownsfolkMastery.completed(reloaded.getTownsfolkProgress().get("worker"),
            com.hexvane.aetherhaven.townsfolk.TownsfolkMastery.Work.CRAFT, reloaded.getWarehouse().finishWork(batch.id(), orders, 6)));
        assertEquals(15, reloaded.getTownsfolkProgress().get("worker").masteryXp());
        assertEquals(1, reloaded.getWarehouse().count(TownWarehouse.Page.OUTPUT, "kit"));
    }
    private static final String CARROT="Plant_Crop_Carrot_Item";
    private static com.hexvane.aetherhaven.production.HerdProductionState feedHerd(int target) {
        var herd=new com.hexvane.aetherhaven.production.HerdProductionState();
        assertNull(herd.buy(0,"Rabbit",1,()->true));
        assertTrue(herd.assign(herd.revision(),0,herd.animals().getFirst().id(),1));
        if(target!=herd.courierFeedTarget())assertTrue(herd.configureCourierFeed(0,target));return herd;
    }

    @Test void feedReservationsStayAtTheFarmUntilTheCorrectPhysicalPickupAndDelivery() {
        var stock=new TownWarehouse();var herd=feedHerd(25);
        var farm=new com.hexvane.aetherhaven.production.PlotProductionState();farm.addAmount(CARROT,6,100);
        var pickup=new WarehouseBatch.Point(0,100,0);var destination=new WarehouseBatch.Point(20,100,20);
        var source=new TownWarehouse.ProductionStock(UUID.randomUUID(),pickup,farm);
        assertEquals(6,HerdFeedSupplyPlanner.availableFood(stock,List.of(source,source),CARROT));
        var batch=stock.reserveHerdFeed(UUID.randomUUID(),destination,CARROT,6,List.of(source));assertNotNull(batch);
        assertTrue(batch.herdFeed());assertEquals(6,farm.getAmount(CARROT));assertEquals(0,herd.feed(CARROT));
        assertTrue(batch.outputItems().isEmpty());assertEquals(0,stock.incoming(CARROT));assertFalse(stock.hasOutputRoom(batch.id()));
        assertEquals(0,HerdFeedSupplyPlanner.availableFood(stock,List.of(source),CARROT));
        assertEquals(0,stock.pickup(batch.id(),"courier",1));
        assertEquals(0,stock.pickupAt(batch.id(),"courier",1,source,destination));
        assertEquals(6,stock.pickupAt(batch.id(),"courier",1,source,pickup));assertEquals(0,farm.getAmount(CARROT));
        stock=reload(stock);batch=stock.batch(batch.id());assertTrue(batch.herdFeed());
        assertFalse(stock.deliver(batch.id(),"courier"));assertFalse(stock.finish(batch.id()));
        assertFalse(stock.deliverHerdFeed(batch.id(),"other",herd,destination));
        assertFalse(stock.deliverHerdFeed(batch.id(),"courier",herd,pickup));assertEquals(0,herd.feed(CARROT));
        assertTrue(stock.deliverHerdFeed(batch.id(),"courier",herd,destination));assertEquals(6,herd.feed(CARROT));
        assertFalse(stock.deliverHerdFeed(batch.id(),"courier",herd,destination));assertTrue(stock.batches().isEmpty());
        assertEquals(0,stock.count(TownWarehouse.Page.OUTPUT,CARROT));
    }

    @Test void mixedFeedSourcesRequireSeparateTripsAndDoNotReserveASecondBatchForTheBarn() {
        var stock=new TownWarehouse();var herd=feedHerd(25);var barn=UUID.randomUUID();
        var farm=new com.hexvane.aetherhaven.production.PlotProductionState();farm.addAmount(CARROT,5,100);
        var point=new WarehouseBatch.Point(0,100,0);var destination=new WarehouseBatch.Point(20,100,20);
        var source=new TownWarehouse.ProductionStock(UUID.randomUUID(),point,farm);
        supply(stock,stock(CARROT,7));
        assertEquals(12,HerdFeedSupplyPlanner.availableFood(stock,List.of(source),CARROT));
        var batch=stock.reserveHerdFeed(barn,destination,CARROT,12,List.of(source));assertNotNull(batch);
        assertNull(stock.reserveHerdFeed(barn,destination,CARROT,1,List.of(source)));
        assertEquals(5,stock.pickupAt(batch.id(),"courier",1,source,point));
        assertTrue(stock.deliverHerdFeed(batch.id(),"courier",herd,destination));assertEquals(5,herd.feed(CARROT));
        assertNotNull(stock.batch(batch.id()));assertEquals(7,stock.count(TownWarehouse.Page.GENERAL,CARROT));
        assertEquals(7,stock.pickup(batch.id(),"courier",1));assertTrue(stock.deliverHerdFeed(batch.id(),"courier",herd,destination));
        assertEquals(12,herd.feed(CARROT));assertTrue(stock.batches().isEmpty());
    }

    @Test void manualFeedDuringTransportCapsDeliveryAndReturnsExcessWithoutLoss() {
        var stock=new TownWarehouse();var herd=feedHerd(25);var destination=new WarehouseBatch.Point(20,100,20);
        var wheat=stock(CARROT,25);supply(stock,wheat);
        var batch=stock.reserveHerdFeed(UUID.randomUUID(),destination,CARROT,25,List.of());assertNotNull(batch);
        assertEquals(25,stock.pickup(batch.id(),"courier",1));
        for(int i=0;i<10;i++)assertTrue(herd.supply(CARROT,()->true));
        assertTrue(stock.deliverHerdFeed(batch.id(),"courier",herd,destination));
        assertEquals(25,herd.feed(CARROT));assertTrue(batch.cancelled());assertEquals(10,batch.cargoItems().getFirst().getQuantity());
        assertEquals(wheat.withQuantity(10),batch.cargoItems().getFirst());
        for(int i=0;i<TownWarehouse.BASE_GENERAL_SLOTS;i++)supply(stock,stock("full_"+i,1));
        stock=reload(stock);assertFalse(stock.returnCargo(batch.id(),"courier"));
        assertEquals(10,stock.batch(batch.id()).cargoItems().getFirst().getQuantity());
        var player=bag(1);var held=stock.stack(TownWarehouse.Page.GENERAL,(short)0);
        assertEquals(1,stock.withdraw(TownWarehouse.Page.GENERAL,player,(short)0,held,TownWarehouse.Amount.STACK,stock.revision()).moved());
        assertTrue(stock.returnCargo(batch.id(),"courier"));assertEquals(10,stock.count(TownWarehouse.Page.GENERAL,CARROT));
        assertEquals(25,herd.feed(CARROT));assertTrue(stock.batches().isEmpty());
    }

    @Test void cancellingFeedReleasesUnpickedStockAndKeepsCarriedStockForReturn() {
        var stock=new TownWarehouse();var destination=new WarehouseBatch.Point(20,100,20);supply(stock,stock(CARROT,16));
        var batch=stock.reserveHerdFeed(UUID.randomUUID(),destination,CARROT,8,List.of());assertNotNull(batch);
        assertEquals(8,HerdFeedSupplyPlanner.availableFood(stock,List.of(),CARROT));assertTrue(stock.cancel(batch.id()));
        assertEquals(16,HerdFeedSupplyPlanner.availableFood(stock,List.of(),CARROT));
        batch=stock.reserveHerdFeed(UUID.randomUUID(),destination,CARROT,8,List.of());stock.pickup(batch.id(),"courier",1);
        var herd=feedHerd(10);assertTrue(herd.configureCourierFeed(herd.feedOrderRevision(),0));
        assertTrue(stock.deliverHerdFeed(batch.id(),"courier",herd,destination));
        assertEquals(0,herd.feed(CARROT));assertTrue(batch.cancelled());assertTrue(stock.returnCargo(batch.id(),"courier"));
        assertEquals(16,stock.count(TownWarehouse.Page.GENERAL,CARROT));
    }

    @Test void fullCraftedGoodsDoNotBlockFeedAndEmptyBarnsRequestNoFoods() {
        var stock=new TownWarehouse();
        for(int i=0;i<TownWarehouse.OUTPUT_SLOTS;i++)assertTrue(stock.acceptCompletedOutput(stock("output_"+i,1)));
        supply(stock,stock(CARROT,8));assertNotNull(stock.reserveHerdFeed(UUID.randomUUID(),new WarehouseBatch.Point(2,100,2),CARROT,8,List.of()));
        var type=com.hexvane.aetherhaven.production.HerdProductionState.class;
        var old=new Gson().fromJson("{}",type);assertEquals(10,old.courierFeedTarget());
        assertTrue(old.configureCourierFeed(0,25));assertTrue(old.requestedFoods(5).isEmpty(),"Empty barns cannot request food");
        var herd=feedHerd(50);assertFalse(herd.configureCourierFeed(0,75));assertFalse(herd.configureCourierFeed(1,999));
        assertEquals(50,new Gson().fromJson(new Gson().toJson(herd),type).courierFeedTarget());
        assertFalse(herd.receiveCourierFeed(CARROT,51));assertFalse(herd.receiveCourierFeed(CARROT,-1));
    }

    @Test void specialCarrotsDoesNotBlockTheAvailableOrdinaryFeedDelivery() {
        var stock=new TownWarehouse();
        supply(stock,new ItemStack(CARROT,16,BsonDocument.parse("{customName:'Saved seeds'}")),stock(CARROT,3));
        assertEquals(3,HerdFeedSupplyPlanner.availableFood(stock,List.of(),CARROT));
        var batch=stock.reserveHerdFeed(UUID.randomUUID(),new WarehouseBatch.Point(2,100,2),CARROT,3,List.of());assertNotNull(batch);
        assertEquals(3,stock.pickup(batch.id(),"courier",1));
        assertEquals(16,stock.count(TownWarehouse.Page.GENERAL,CARROT));
        assertEquals(0,HerdFeedSupplyPlanner.availableFood(stock,List.of(),CARROT));
    }

    @Test void retiringGardensReturnsReservedDeliveredAndCarriedSeedsWithoutDuplication() {
        var crop=new com.hexvane.aetherhaven.workplace.AlchemicalCrop("Health",1);
        for(int stage=0;stage<3;stage++) {
            var stock=new TownWarehouse();supply(stock,stock(crop.seed(),1));
            var batch=stock.reserveCultivation(UUID.randomUUID(),UUID.randomUUID(),"grow",crop,List.of());
            if(stage>=1)assertEquals(1,stock.pickup(batch.id(),"courier",1));
            if(stage==2)assertTrue(stock.deliver(batch.id(),"courier"));
            stock=reload(stock);
            var sites=new com.hexvane.aetherhaven.workplace.TownWorkplaces();
            assertTrue(sites.retireCultivation(stock));
            stock=reload(stock);
            assertFalse(sites.retireCultivation(stock));
            if(stage==2)assertEquals(1,stock.collectReturn(batch.id(),"courier",1));
            if(stage>=1)assertTrue(stock.returnCargo(batch.id(),"courier"));
            assertEquals(1,stock.count(TownWarehouse.Page.GENERAL,crop.seed()));
            assertTrue(stock.batches().isEmpty());
            assertFalse(stock.returnCargo(batch.id(),"courier"));
        }
    }
    @Test void cultivationUsesSeveralBedsButPreparesOnlyOneSeedAtATime() {
        var town=new TownWarehouse();var site=UUID.randomUUID();var order=UUID.randomUUID();
        var crop=new com.hexvane.aetherhaven.workplace.AlchemicalCrop("Health",1);
        supply(town,stock(crop.seed(),8));
        for(int bed=0;bed<TownWarehouse.CULTIVATION_BEDS;bed++) {
            assertTrue(town.canPrepare(site,false,true));
            var batch=town.reserveCultivation(site,order,"grow",crop,List.of());assertNotNull(batch);
            assertTrue(town.setDestination(batch.id(),new WarehouseBatch.Point(bed,100,0)));
            assertFalse(town.canPrepare(site,false,true));
            assertNull(town.reserveCultivation(site,order,"grow",crop,List.of()));
            town.pickup(batch.id(),"courier",1);town.deliver(batch.id(),"courier");
            for(int i=0;i<24;i++)town.advanceWork(batch.id(),.25);
            assertTrue(town.plantCrop(batch.id(),()->true));
            assertFalse(town.canPrepare(site,false));assertFalse(town.canPrepare(site,true));
            town=reload(town);
        }
        assertFalse(town.canPrepare(site,false,true));assertNull(town.reserveCultivation(site,order,"grow",crop,List.of()));
        assertEquals(6,town.queuedForOrder(order));assertEquals(6,town.incoming(crop.item()));
        assertEquals(2,town.count(TownWarehouse.Page.GENERAL,crop.seed()));
        assertEquals(0,town.count(TownWarehouse.Page.OUTPUT,crop.item()));
        assertTrue(town.cancel(town.batches().getFirst().id()));
        assertTrue(town.canPrepare(site,false,true));assertEquals(2,town.count(TownWarehouse.Page.GENERAL,crop.seed()));
    }
    @Test void countedAndMaintainedCropOrdersIncludeEveryGrowingPlant() {
        var town=new TownWarehouse();var site=UUID.randomUUID();
        var crop=new com.hexvane.aetherhaven.workplace.AlchemicalCrop("Health",1);
        var orders=new com.hexvane.aetherhaven.workplace.WorkplaceOrders();
        orders.add(0,"grow",com.hexvane.aetherhaven.workplace.WorkplaceOrders.Mode.COUNT,2);
        orders.add(1,"grow",com.hexvane.aetherhaven.workplace.WorkplaceOrders.Mode.MAINTAIN,2);
        var counted=orders.list().get(0);var maintained=orders.list().get(1);
        supply(town,stock(crop.seed(),3));
        for(int i=0;i<2;i++) {
            var batch=town.reserveCultivation(site,counted.id(),"grow",crop,List.of());
            town.pickup(batch.id(),"courier",1);town.deliver(batch.id(),"courier");
            for(int j=0;j<24;j++)town.advanceWork(batch.id(),.25);
            assertTrue(town.plantCrop(batch.id(),()->true));
        }
        assertEquals(0,counted.batchesNeeded(0,town.incoming(crop.item()),1,town.queuedForOrder(counted.id())));
        assertEquals(0,maintained.batchesNeeded(0,town.incoming(crop.item()),1,0));
        var batch=town.batches().getFirst();
        for(int j=0;j<24;j++)town.advanceWork(batch.id(),.25);
        assertTrue(town.finishCultivation(batch.id(),orders,12,()->true));
        assertEquals(1,counted.completed());assertEquals(1,town.queuedForOrder(counted.id()));
        assertEquals(0,counted.batchesNeeded(1,town.incoming(crop.item()),1,town.queuedForOrder(counted.id())));
        assertEquals(0,maintained.batchesNeeded(1,town.incoming(crop.item()),1,0));
    }
    @Test void plantedSeedsStayConsumedAcrossRestartAndCancellation() {
        var town=new TownWarehouse();var crop=new com.hexvane.aetherhaven.workplace.AlchemicalCrop("Health",1);
        supply(town,stock(crop.seed(),1));
        var batch=town.reserveCultivation(UUID.randomUUID(),UUID.randomUUID(),"grow",crop,List.of());
        assertNotNull(batch);assertEquals(1,town.pickup(batch.id(),"courier",1));assertTrue(town.deliver(batch.id(),"courier"));
        assertFalse(town.plantCrop(batch.id(),()->{fail("No attendance yet");return true;}));
        for(int i=0;i<24;i++)town.advanceWork(batch.id(),.25);
        assertFalse(town.plantCrop(batch.id(),()->false));assertEquals(1,batch.deliveredItems().size());
        assertTrue(town.plantCrop(batch.id(),()->true));assertTrue(batch.deliveredItems().isEmpty());
        assertFalse(town.plantCrop(batch.id(),()->{fail("Already planted");return true;}));
        var restored=reload(town);var saved=restored.batch(batch.id());
        assertTrue(saved.cultivation());assertTrue(saved.cropPlanted());assertEquals(6,saved.workSeconds());
        assertTrue(restored.cancel(saved.id()));assertNull(restored.batch(saved.id()));
        assertEquals(0,restored.count(TownWarehouse.Page.GENERAL,crop.seed()));
        assertEquals(0,restored.count(TownWarehouse.Page.OUTPUT,crop.item()));
    }
    @Test void cultivationRequiresARealHarvestAndCompletesOnce() {
        var town=new TownWarehouse();var crop=new com.hexvane.aetherhaven.workplace.AlchemicalCrop("Mana",2);
        supply(town,stock(crop.seed(),1));
        var orders=new com.hexvane.aetherhaven.workplace.WorkplaceOrders();
        orders.add(0,"grow",com.hexvane.aetherhaven.workplace.WorkplaceOrders.Mode.COUNT,1);var order=orders.list().getFirst();
        var batch=town.reserveCultivation(UUID.randomUUID(),order.id(),"grow",crop,List.of());
        town.pickup(batch.id(),"courier",1);town.deliver(batch.id(),"courier");
        for(int i=0;i<24;i++)town.advanceWork(batch.id(),.25);
        assertTrue(town.plantCrop(batch.id(),()->true));
        assertFalse(town.finishCultivation(batch.id(),orders,12,()->{fail("Harvest needs attendance");return true;}));
        for(int i=0;i<24;i++)town.advanceWork(batch.id(),.25);
        assertFalse(town.finish(batch.id()));assertFalse(town.finishWork(batch.id(),orders,12));
        assertFalse(town.finishCultivation(batch.id(),orders,12,()->false));assertEquals(0,order.completed());
        assertTrue(town.finishCultivation(batch.id(),orders,12,()->true));
        assertEquals(1,order.completed());assertEquals(1,town.count(TownWarehouse.Page.OUTPUT,crop.item()));
        assertFalse(town.finishCultivation(batch.id(),orders,12,()->{fail("Duplicate harvest");return true;}));
    }
    @Test void fullOutputDoesNotTouchTheMaturePlant() {
        var town=new TownWarehouse();var crop=new com.hexvane.aetherhaven.workplace.AlchemicalCrop("Stamina",3);
        supply(town,stock(crop.seed(),1));var orders=new com.hexvane.aetherhaven.workplace.WorkplaceOrders();
        orders.add(0,"grow",com.hexvane.aetherhaven.workplace.WorkplaceOrders.Mode.COUNT,1);var order=orders.list().getFirst();
        var batch=town.reserveCultivation(UUID.randomUUID(),order.id(),"grow",crop,List.of());
        town.pickup(batch.id(),"courier",1);town.deliver(batch.id(),"courier");
        for(int i=0;i<24;i++)town.advanceWork(batch.id(),.25);
        assertTrue(town.plantCrop(batch.id(),()->true));
        for(int i=0;i<24;i++)town.advanceWork(batch.id(),.25);
        // Simulate output space being occupied after a batch was prepared.
        var saved=com.google.gson.JsonParser.parseString(new Gson().toJson(town)).getAsJsonObject();
        var full=bag(TownWarehouse.OUTPUT_SLOTS);
        for(short i=0;i<full.getCapacity();i++)put(full,i,stock("blocked"+i,100));
        saved.addProperty("outputData",SimpleItemContainer.CODEC.encode(full,new com.hypixel.hytale.codec.ExtraInfo()).asDocument().toJson());
        var restored=new Gson().fromJson(saved,TownWarehouse.class);
        assertEquals(12,restored.batch(batch.id()).workSeconds());
        assertFalse(restored.hasOutputRoom(batch.id()));
        assertFalse(restored.advanceWork(batch.id(),.25));
        assertFalse(restored.finishCultivation(batch.id(),orders,12,()->{fail("No room to harvest");return true;}));
        assertNotNull(restored.batch(batch.id()));assertEquals(0,order.completed());
        var player=bag(2);var occupied=restored.stack(TownWarehouse.Page.OUTPUT,(short)0);
        assertTrue(restored.withdraw(TownWarehouse.Page.OUTPUT,player,(short)0,occupied,TownWarehouse.Amount.STACK,restored.revision()).moved()>0);
        assertTrue(restored.hasOutputRoom(batch.id()));
        var harvested=new java.util.concurrent.atomic.AtomicInteger();
        assertTrue(restored.finishCultivation(batch.id(),orders,12,()->{harvested.incrementAndGet();return true;}));
        assertEquals(1,harvested.get());assertEquals(1,order.completed());
    }
    private static com.hexvane.aetherhaven.workplace.WorkplaceRecipe jewelryRecipe(String rarity) {
        return new com.hexvane.aetherhaven.workplace.WorkplaceRecipe("jewel_"+rarity,"Jewelry","jewelry","workplace:jewel_"+rarity,
            "Aetherhaven_Jewelry_Crafting_Bench",1,1,List.of(new com.hexvane.aetherhaven.workplace.WorkplaceRecipe.Ingredient("ore",null,1)),
            "Aetherhaven_Ring_Silver_Ruby",1,6,false,rarity);
    }
    @Test void jewelryGradeAndAppraisalAreIndependentAndMaintainCountsTheChosenGrade() {
        var common=jewelryRecipe("common");var legendary=jewelryRecipe("legendary");
        var planned=stock(common.output(),1);
        var hidden=com.hexvane.aetherhaven.workplace.JewelryWorkplaceOutput.finish(legendary,planned,false);
        var appraised=com.hexvane.aetherhaven.workplace.JewelryWorkplaceOutput.finish(common,planned,true);
        assertFalse(com.hexvane.aetherhaven.jewelry.JewelryMetadata.isAppraised(hidden));
        assertTrue(com.hexvane.aetherhaven.jewelry.JewelryMetadata.isAppraised(appraised));
        assertEquals(com.hexvane.aetherhaven.jewelry.JewelryRarity.LEGENDARY,com.hexvane.aetherhaven.jewelry.JewelryMetadata.readRarity(hidden));
        assertEquals(3,com.hexvane.aetherhaven.jewelry.JewelryMetadata.readTraits(hidden).size());
        var town=new TownWarehouse();assertTrue(town.acceptCompletedOutput(hidden));assertTrue(town.acceptCompletedOutput(appraised));
        assertEquals(1,legendary.stored(town,planned.getItemId()));assertEquals(1,common.stored(town,planned.getItemId()));
        var restored=reload(town);assertEquals(hidden,restored.stack(TownWarehouse.Page.OUTPUT,(short)0));
        supply(town,stock("ore",2));
        town.reserve(UUID.randomUUID(),UUID.randomUUID(),legendary.id(),List.of(ingredient("ore",1)),List.of(planned));
        assertEquals(1,legendary.incoming(town,planned.getItemId()));assertEquals(0,common.incoming(town,planned.getItemId()));
    }
    @Test void outputDecorationCannotDuplicateOrReplacePromisedItems() {
        var town=new TownWarehouse();supply(town,stock("ore",2));
        var orders=new com.hexvane.aetherhaven.workplace.WorkplaceOrders();
        orders.add(0,"recipe",com.hexvane.aetherhaven.workplace.WorkplaceOrders.Mode.COUNT,1);var order=orders.list().getFirst();
        var batch=town.reserve(UUID.randomUUID(),order.id(),"recipe",List.of(ingredient("ore",2)),List.of(stock("kit",1)));
        town.pickup(batch.id(),"courier",1);town.deliver(batch.id(),"courier");
        for(int i=0;i<24;i++)town.advanceWork(batch.id(),.25);
        assertFalse(town.finishWork(batch.id(),orders,6,s->stock("other",1)));
        assertFalse(town.finishWork(batch.id(),orders,6,s->s.withQuantity(2)));
        assertEquals(0,order.completed());assertEquals(2,batch.deliveredItems().getFirst().getQuantity());
        var result=stock("kit",1).withMetadata("crafted",new org.bson.BsonBoolean(true));
        assertTrue(town.finishWork(batch.id(),orders,6,s->result));
        assertEquals(result,town.stack(TownWarehouse.Page.OUTPUT,(short)0));assertEquals(1,order.completed());
        assertFalse(town.finishWork(batch.id(),orders,6,s->result));
    }
    private static com.hexvane.aetherhaven.equipment.ProfessionKitCatalog kits() {
        return new com.hexvane.aetherhaven.equipment.ProfessionKitCatalog(List.of(
            new com.hexvane.aetherhaven.equipment.ProfessionKit("courierKit","Courier satchel","logistics",0,1),
            new com.hexvane.aetherhaven.equipment.ProfessionKit("smithKit","Smith tools","blacksmithing",.15,0)));
    }
    @Test void equippedKitExchangesInAFullInventoryAndPreservesMetadataAcrossReload() {
        var progress=new com.hexvane.aetherhaven.townsfolk.TownsfolkProgress();var bag=bag(1);
        var courier=new ItemStack("courierKit",1,BsonDocument.parse("{maker:'Mara'}"));put(bag,0,courier);
        assertNull(com.hexvane.aetherhaven.equipment.ProfessionKitService.equip(progress,kits(),bag,(short)0,courier,null));
        assertTrue(ItemStack.isEmpty(bag.getItemStack((short)0)));
        var smith=stock("smithKit",1);put(bag,0,smith);
        assertNull(com.hexvane.aetherhaven.equipment.ProfessionKitService.equip(progress,kits(),bag,(short)0,smith,courier));
        assertEquals(courier,bag.getItemStack((short)0));
        var loaded=new Gson().fromJson(new Gson().toJson(progress),com.hexvane.aetherhaven.townsfolk.TownsfolkProgress.class);
        assertEquals(smith,loaded.professionKit());
        assertNotNull(com.hexvane.aetherhaven.equipment.ProfessionKitService.unequip(loaded,bag,smith));
        assertEquals(smith,loaded.professionKit());
        bag.setItemStackForSlot((short)0,null,false);
        assertNull(com.hexvane.aetherhaven.equipment.ProfessionKitService.unequip(loaded,bag,smith));
        assertNull(loaded.professionKit());assertEquals(smith,bag.getItemStack((short)0));
    }
    @Test void warehouseKitExchangeRejectsStaleClicksAndPersistsBothOwners() {
        var warehouse=new TownWarehouse();var progress=new com.hexvane.aetherhaven.townsfolk.TownsfolkProgress();
        var smith=stock("smithKit",1);var courier=new ItemStack("courierKit",1,BsonDocument.parse("{signed:true}"));
        progress.setProfessionKitData(com.hexvane.aetherhaven.townsfolk.TownsfolkProgress.encodeProfessionKit(courier));
        assertTrue(warehouse.acceptCompletedOutput(smith));long revision=warehouse.revision();
        assertNull(warehouse.equipProfessionKit(TownWarehouse.Page.OUTPUT,(short)0,smith,revision,progress,kits(),courier));
        assertEquals(courier,warehouse.stack(TownWarehouse.Page.OUTPUT,(short)0));assertEquals(smith,progress.professionKit());
        assertNotNull(warehouse.equipProfessionKit(TownWarehouse.Page.OUTPUT,(short)0,smith,revision,progress,kits(),courier));
        var loaded=new Gson().fromJson(new Gson().toJson(warehouse),TownWarehouse.class);
        assertEquals(courier,loaded.stack(TownWarehouse.Page.OUTPUT,(short)0));
    }
    @Test void kitReservationsAndIncapableProfessionPreventEquipping() {
        var warehouse=new TownWarehouse();var progress=new com.hexvane.aetherhaven.townsfolk.TownsfolkProgress();
        var courier=stock("courierKit",1);var stockBag=bag(1);put(stockBag,0,courier);deposit(warehouse,stockBag,0,TownWarehouse.Amount.STACK);
        var batch=warehouse.reserve(UUID.randomUUID(),UUID.randomUUID(),"test",List.of(new MaterialQuantity("courierKit",null,null,1,null)),List.of(stock("result",1)));
        assertNotNull(batch);
        assertNotNull(warehouse.equipProfessionKit(TownWarehouse.Page.GENERAL,(short)0,courier,warehouse.revision(),progress,kits(),null));
        assertNull(progress.professionKit());assertEquals(courier,warehouse.stack(TownWarehouse.Page.GENERAL,(short)0));
        progress.setSkill(com.hexvane.aetherhaven.townsfolk.TownsfolkSkill.LOGISTICS,0);var bag=bag(1);put(bag,0,courier);
        assertNotNull(com.hexvane.aetherhaven.equipment.ProfessionKitService.equip(progress,kits(),bag,(short)0,courier,null));
        assertEquals(courier,bag.getItemStack((short)0));
    }
    @Test void gearBonusesDoNotChangeSkillsWagesOrEnableIncapableWork() {
        var progress=new com.hexvane.aetherhaven.townsfolk.TownsfolkProgress();
        progress.setProfessionKitData(com.hexvane.aetherhaven.townsfolk.TownsfolkProgress.encodeProfessionKit(stock("courierKit",1)));
        assertEquals(2,CourierAssignments.carryingStacks(progress,kits()));
        assertEquals(1,progress.skill(com.hexvane.aetherhaven.townsfolk.TownsfolkSkill.LOGISTICS));
        progress.setSkill(com.hexvane.aetherhaven.townsfolk.TownsfolkSkill.LOGISTICS,0);
        assertEquals(0,CourierAssignments.carryingStacks(progress,kits()));
        progress.setProfessionKitData(com.hexvane.aetherhaven.townsfolk.TownsfolkProgress.encodeProfessionKit(stock("smithKit",1)));
        assertEquals(1.15,com.hexvane.aetherhaven.equipment.ProfessionKitService.workMultiplier(progress,com.hexvane.aetherhaven.townsfolk.TownsfolkSkill.BLACKSMITHING,kits()),.00001);
        assertEquals(1,com.hexvane.aetherhaven.equipment.ProfessionKitService.workMultiplier(progress,com.hexvane.aetherhaven.townsfolk.TownsfolkSkill.ALCHEMY,kits()));
        assertFalse(progress.isEmployed());
    }
    private static ItemStore itemStore;
    private static Object previousItemStore;
    private static Object previousQualityStore;
    private static class QualityStore extends AssetStore<String,ItemQuality,IndexedLookupTableAssetMap<String,ItemQuality>> {
        QualityStore(Config config){super(config);}
        @Override protected IEventBus getEventBus(){return new EventBus(false);}
        @Override public void addFileMonitor(String pack,Path path){}
        @Override public void removeFileMonitor(Path path){}
        @Override protected void handleRemoveOrUpdate(Set<String> removed,Map<String,ItemQuality> updated,AssetUpdateQuery query){}
        private static class Config extends Builder<String,ItemQuality,IndexedLookupTableAssetMap<String,ItemQuality>,Config> {
            Config(){super(String.class,ItemQuality.class,new IndexedLookupTableAssetMap<>(ItemQuality[]::new));
                setPath("ItemQualities");setCodec(ItemQuality.CODEC);setKeyFunction(ItemQuality::getId);setReplaceOnRemove(id->ItemQuality.DEFAULT_ITEM_QUALITY);}
            @Override public QualityStore build(){return new QualityStore(this);}
        }
    }
    // Supply an asset map so real ItemStacks and inventory transactions work without starting a server.
    private static class ItemStore extends AssetStore<String, Item, DefaultAssetMap<String, Item>> {
        private final EventBus events = new EventBus(false);
        ItemStore(Config config) { super(config); }
        @Override protected IEventBus getEventBus() { return events; }
        @Override public void addFileMonitor(String pack, Path path) {}
        @Override public void removeFileMonitor(Path path) {}
        @Override protected void handleRemoveOrUpdate(Set<String> removed, Map<String, Item> updated, AssetUpdateQuery query) {}
        private static class Config extends Builder<String, Item, DefaultAssetMap<String, Item>, Config> {
            Config() {
                super(String.class, Item.class, new DefaultAssetMap<>());
                setPath("Items"); setCodec(Item.CODEC); setKeyFunction(Item::getId);
            }
            @Override public ItemStore build() { return new ItemStore(this); }
        }
    }

    private static com.hexvane.aetherhaven.equipment.GuardKitCatalog guardKits() {
        return new com.hexvane.aetherhaven.equipment.GuardKitCatalog(List.of(
            new com.hexvane.aetherhaven.equipment.GuardKit("smith","Iron knight","guard_knight","guard_knight_iron","Strength"),
            new com.hexvane.aetherhaven.equipment.GuardKit("courier","Thorium knight","guard_knight","guard_knight_thorium","Strength")));
    }
    @Test void guardKitsSwapInFullBagsKeepMetadataAndSurviveDutyChangesAndSave() {
        var progress=new com.hexvane.aetherhaven.townsfolk.TownsfolkProgress();
        var old=stock("smith",1).withMetadata("maker",new org.bson.BsonString("Ada"));var next=stock("courier",1);
        progress.setGuardKitData(com.hexvane.aetherhaven.townsfolk.TownsfolkProgress.encodeProfessionKit(old));
        progress.setProfessionKitData(com.hexvane.aetherhaven.townsfolk.TownsfolkProgress.encodeProfessionKit(next));
        var bag=bag(1);bag.setItemStackForSlot((short)0,next);
        assertNotNull(com.hexvane.aetherhaven.equipment.GuardKitService.equip(progress,guardKits(),"guard_archer",bag,(short)0,next,old));
        assertEquals(next,bag.getItemStack((short)0));assertEquals(old,progress.guardKit());
        assertNull(com.hexvane.aetherhaven.equipment.GuardKitService.equip(progress,guardKits(),"guard_knight",bag,(short)0,next,old));
        assertEquals(old,bag.getItemStack((short)0));assertEquals(next,progress.guardKit());assertEquals(next,progress.professionKit());
        var loaded=new Gson().fromJson(new Gson().toJson(progress),com.hexvane.aetherhaven.townsfolk.TownsfolkProgress.class);
        assertEquals(next,loaded.guardKit());
        assertEquals("guard_knight_thorium",com.hexvane.aetherhaven.equipment.GuardKitService.profileFor(loaded,"guard_knight",guardKits()));
        assertEquals("guard_archer",com.hexvane.aetherhaven.equipment.GuardKitService.profileFor(loaded,"guard_archer",guardKits()));
        assertNotNull(com.hexvane.aetherhaven.equipment.GuardKitService.unequip(loaded,bag,next));assertEquals(next,loaded.guardKit());
        bag.removeItemStackFromSlot((short)0);
        assertNull(com.hexvane.aetherhaven.equipment.GuardKitService.unequip(loaded,bag,next));assertNull(loaded.guardKit());assertEquals(next,bag.getItemStack((short)0));
    }
    @Test void guardKitWarehouseSwapRejectsReservationsAndStaleViews() {
        var town=new TownWarehouse();var progress=new com.hexvane.aetherhaven.townsfolk.TownsfolkProgress();var kit=stock("smith",1);
        supply(town,kit);
        var batch=town.reserve(UUID.randomUUID(),UUID.randomUUID(),"recipe",List.of(ingredient("smith",1)),List.of(stock("kit",1)));
        assertNotNull(batch);
        assertNotNull(town.equipGuardKit(TownWarehouse.Page.GENERAL,(short)0,kit,town.revision(),progress,guardKits(),"guard_knight",null));
        assertNull(progress.guardKit());town.cancel(batch.id());
        long seen=town.revision();
        assertNull(town.equipGuardKit(TownWarehouse.Page.GENERAL,(short)0,kit,seen,progress,guardKits(),"guard_knight",null));
        assertEquals(kit,progress.guardKit());assertTrue(ItemStack.isEmpty(town.stack(TownWarehouse.Page.GENERAL,(short)0)));
        assertNotNull(town.equipGuardKit(TownWarehouse.Page.GENERAL,(short)0,kit,seen,progress,guardKits(),"guard_knight",null));
        var next=stock("courier",1);assertTrue(town.acceptCompletedOutput(next));
        assertNull(town.equipGuardKit(TownWarehouse.Page.OUTPUT,(short)0,next,town.revision(),progress,guardKits(),"guard_knight",kit));
        assertEquals(kit,reload(town).stack(TownWarehouse.Page.OUTPUT,(short)0));assertEquals(next,progress.guardKit());
    }

    @BeforeAll static void setUpAssets() throws Exception {
        var field = Item.class.getDeclaredField("ASSET_STORE");
        field.setAccessible(true);
        previousItemStore = field.get(null);
        itemStore = new ItemStore.Config().build();
        // Avoid changing the global registry used by other test fixtures.
        field.set(null, itemStore);
        var qualityField=ItemQuality.class.getDeclaredField("ASSET_STORE");qualityField.setAccessible(true);
        previousQualityStore=qualityField.get(null);qualityField.set(null,new QualityStore.Config().build());
    }

    @AfterAll static void restoreAssets() throws Exception {
        var field = Item.class.getDeclaredField("ASSET_STORE");
        field.setAccessible(true);
        field.set(null, previousItemStore);
        var qualityField=ItemQuality.class.getDeclaredField("ASSET_STORE");qualityField.setAccessible(true);
        qualityField.set(null,previousQualityStore);
    }


    private static SimpleItemContainer bag(int slots) { return new SimpleItemContainer((short)slots); }
    private static ItemStack stock(String id,int count) { return new ItemStack(id,count); }
    private static void put(SimpleItemContainer bag,int slot,ItemStack item) { bag.setItemStackForSlot((short)slot,item,false); }
    private static TownWarehouse.Transfer deposit(TownWarehouse town,SimpleItemContainer bag,int slot,TownWarehouse.Amount amount) {
        return town.deposit(TownWarehouse.Page.GENERAL,bag,(short)slot,bag.getItemStack((short)slot),amount,town.revision());
    }
    @Test void oneItemThenStackAndRoundTripPreserveProperties() {
        var town=new TownWarehouse();var bag=bag(2);
        var item=new ItemStack("test",9,12.0,40.0,BsonDocument.parse("{custom: {name: 'Precious', tier: 4}}"));
        put(bag,0,item);
        assertEquals(1,deposit(town,bag,0,TownWarehouse.Amount.ONE).moved());
        assertEquals(8,bag.getItemStack((short)0).getQuantity());
        assertEquals(8,deposit(town,bag,0,TownWarehouse.Amount.STACK).moved());
        var restored=new Gson().fromJson(new Gson().toJson(town),TownWarehouse.class);
        assertEquals(item,restored.stack(TownWarehouse.Page.GENERAL,(short)0));
        assertEquals(9,restored.withdraw(TownWarehouse.Page.GENERAL,bag,(short)0,item,TownWarehouse.Amount.STACK,restored.revision()).moved());
        assertEquals(item,bag.getItemStack((short)0));
        assertEquals(0,restored.used(TownWarehouse.Page.GENERAL));
    }
    @Test void fullPlayerInventoryLeavesSourceUntouched() {
        var town=new TownWarehouse();var bag=bag(1);
        assertTrue(town.acceptCompletedOutput(stock("result",10)));
        put(bag,0,stock("other",100));
        long rev=town.revision();var expected=town.stack(TownWarehouse.Page.OUTPUT,(short)0);
        assertEquals(0,town.withdraw(TownWarehouse.Page.OUTPUT,bag,(short)0,expected,TownWarehouse.Amount.STACK,rev).moved());
        assertEquals(10,town.count(TownWarehouse.Page.OUTPUT,"result"));assertEquals(rev,town.revision());
    }
    @Test void partialFitLeavesRemainderAtSource() {
        var town=new TownWarehouse();var bag=bag(1);
        town.acceptCompletedOutput(stock("result",20));put(bag,0,stock("result",97));
        assertEquals(3,town.withdraw(TownWarehouse.Page.OUTPUT,bag,(short)0,town.stack(TownWarehouse.Page.OUTPUT,(short)0),TownWarehouse.Amount.STACK,town.revision()).moved());
        assertEquals(17,town.count(TownWarehouse.Page.OUTPUT,"result"));assertEquals(100,bag.getItemStack((short)0).getQuantity());
    }
    @Test void stalePlayerStackAndWarehouseRevisionRejectDuplicateClicks() {
        var town=new TownWarehouse();var bag=bag(2);var expected=stock("test",4);put(bag,0,expected);
        assertEquals(4,deposit(town,bag,0,TownWarehouse.Amount.STACK).moved());
        put(bag,0,stock("different",7));
        assertEquals(0,town.deposit(TownWarehouse.Page.GENERAL,bag,(short)0,expected,TownWarehouse.Amount.STACK,town.revision()).moved());
        assertEquals(0,town.withdraw(TownWarehouse.Page.GENERAL,bag,(short)0,expected,TownWarehouse.Amount.ONE,0).moved());
        assertEquals(4,town.count(TownWarehouse.Page.GENERAL,"test"));
    }
    @Test void outputRejectsPlayerDepositsAndFullOutputRejectsWholeCraft() {
        var town=new TownWarehouse();var bag=bag(1);put(bag,0,stock("test",4));
        assertEquals(0,town.deposit(TownWarehouse.Page.OUTPUT,bag,(short)0,bag.getItemStack((short)0),TownWarehouse.Amount.STACK,0).moved());
        for(int i=0;i<TownWarehouse.OUTPUT_SLOTS;i++)assertTrue(town.acceptCompletedOutput(stock("item"+i,100)));
        assertFalse(town.acceptCompletedOutput(stock("extra",4)));
        assertEquals(0,town.count(TownWarehouse.Page.OUTPUT,"extra"));assertEquals(4,bag.getItemStack((short)0).getQuantity());
    }
    @Test void allMatchingIncludesOtherSlotsButNotDifferentMetadata() {
        var town=new TownWarehouse();var bag=bag(4);
        put(bag,0,stock("test",20));put(bag,1,stock("test",35));
        put(bag,2,new ItemStack("test",3,BsonDocument.parse("{special:true}")));put(bag,3,stock("other",8));
        assertEquals(55,deposit(town,bag,0,TownWarehouse.Amount.ALL_MATCHING).moved());
        assertEquals(55,town.count(TownWarehouse.Page.GENERAL,"test"));
        assertEquals(3,bag.getItemStack((short)2).getQuantity());assertEquals(8,bag.getItemStack((short)3).getQuantity());
    }
    @Test void upgradePreservesBothInventoriesAndSurvivesReload() {
        var town=new TownWarehouse();var bag=bag(1);put(bag,0,stock("test",40));deposit(town,bag,0,TownWarehouse.Amount.STACK);
        town.acceptCompletedOutput(stock("result",5));long rev=town.revision();
        assertTrue(town.upgrade(rev));assertFalse(town.upgrade(rev));
        var restored=new Gson().fromJson(new Gson().toJson(town),TownWarehouse.class);
        assertEquals(60,restored.capacity());assertEquals(40,restored.count(TownWarehouse.Page.GENERAL,"test"));assertEquals(5,restored.count(TownWarehouse.Page.OUTPUT,"result"));
        for(int i=1;i<TownWarehouse.MAX_UPGRADES;i++) assertTrue(restored.upgrade(restored.revision()));
        assertFalse(restored.upgrade(restored.revision()));
    }
    @Test void previousDevelopmentCapacityExpandsWithoutMovingReservedSlotsOrLosingMetadata() {
        var legacy=bag(54);
        var special=new ItemStack("test",7,BsonDocument.parse("{engraved:'Mertie'}"));
        put(legacy,53,special);
        var saved=new com.google.gson.JsonObject();saved.addProperty("storageUpgrades",1);
        saved.addProperty("generalData",SimpleItemContainer.CODEC.encode((SimpleItemContainer)legacy,new com.hypixel.hytale.codec.ExtraInfo()).asDocument().toJson());
        var town=new Gson().fromJson(saved,TownWarehouse.class);
        assertEquals(60,town.slots(TownWarehouse.Page.GENERAL));
        assertEquals(special,town.stack(TownWarehouse.Page.GENERAL,(short)53));
        assertEquals(1,town.used(TownWarehouse.Page.GENERAL));
        assertTrue(town.upgrade(town.revision()));
        assertEquals(special,reload(town).stack(TownWarehouse.Page.GENERAL,(short)53));
    }
    @Test void fullGeneralStorageRejectsDepositWithoutDiscardingItems() {
        var town=new TownWarehouse();var bag=bag(1);
        for(int i=0;i<TownWarehouse.BASE_GENERAL_SLOTS;i++){put(bag,0,stock("item"+i,100));assertEquals(100,deposit(town,bag,0,TownWarehouse.Amount.STACK).moved());}
        put(bag,0,stock("extra",5));assertEquals(0,deposit(town,bag,0,TownWarehouse.Amount.STACK).moved());
        assertEquals(5,bag.getItemStack((short)0).getQuantity());
    }
    @Test void corruptDataFailsClosedInsteadOfResettingStorage() {
        var town=new Gson().fromJson("{\"generalData\":\"invalid bson\"}",TownWarehouse.class);
        assertThrows(RuntimeException.class,()->town.used(TownWarehouse.Page.GENERAL));
        assertTrue(new Gson().toJson(town).contains("invalid bson"));
    }
    @Test void workProgressRequiresDeliveredSuppliesAndCannotCatchUpOffline() {
        var town=new TownWarehouse();supply(town,stock("ore",2));var batch=reserve(town,2);
        assertFalse(town.advanceWork(batch.id(),.25));
        town.pickup(batch.id(),"courier",1);assertFalse(town.advanceWork(batch.id(),.25));
        town.deliver(batch.id(),"courier");
        for(double invalid:new double[]{0,-1,1,86400,Double.NaN,Double.POSITIVE_INFINITY})assertFalse(town.advanceWork(batch.id(),invalid));
        for(int i=0;i<8;i++)assertTrue(town.advanceWork(batch.id(),.25));
        var restored=reload(town);assertEquals(2,restored.batch(batch.id()).workSeconds());
        assertEquals(0,restored.count(TownWarehouse.Page.OUTPUT,"kit"));
    }
    @Test void preparedCraftPausesForFullOutputAndResumesAfterWithdrawal() {
        var town=new TownWarehouse();supply(town,stock("ore",2));var batch=reserve(town,2);
        town.pickup(batch.id(),"courier",1);town.deliver(batch.id(),"courier");
        for(int i=0;i<8;i++)assertTrue(town.advanceWork(batch.id(),.25));
        assertTrue(town.hasOutputRoom(batch.id()));
        // A loaded town can contain output that now occupies a previously reserved slot.
        var saved=com.google.gson.JsonParser.parseString(new Gson().toJson(town)).getAsJsonObject();
        var full=bag(TownWarehouse.OUTPUT_SLOTS);
        for(short i=0;i<full.getCapacity();i++)put(full,i,stock("blocked"+i,100));
        saved.addProperty("outputData",SimpleItemContainer.CODEC.encode(full,new com.hypixel.hytale.codec.ExtraInfo()).asDocument().toJson());
        var restored=new Gson().fromJson(saved,TownWarehouse.class);
        assertFalse(restored.hasOutputRoom(batch.id()));assertFalse(restored.advanceWork(batch.id(),.25));
        assertEquals(2,restored.batch(batch.id()).workSeconds());
        assertEquals(2,restored.batch(batch.id()).deliveredItems().getFirst().getQuantity());
        assertFalse(restored.finish(batch.id()));
        var player=bag(2);var occupied=restored.stack(TownWarehouse.Page.OUTPUT,(short)0);
        assertTrue(restored.withdraw(TownWarehouse.Page.OUTPUT,player,(short)0,occupied,TownWarehouse.Amount.STACK,restored.revision()).moved()>0);
        assertTrue(restored.hasOutputRoom(batch.id()));assertTrue(restored.advanceWork(batch.id(),.25));
        assertEquals(2.25,restored.batch(batch.id()).workSeconds());
        assertTrue(restored.finish(batch.id()));assertNull(restored.batch(batch.id()));
    }
    @Test void equippedWorkBonusAppliesToSlowTicksWithoutAllowingOfflineCatchUp() {
        var town=new TownWarehouse();supply(town,stock("ore",2));var batch=reserve(town,2);
        var progress=new com.hexvane.aetherhaven.townsfolk.TownsfolkProgress();
        progress.setProfessionKitData(com.hexvane.aetherhaven.townsfolk.TownsfolkProgress.encodeProfessionKit(stock("smithKit",1)));
        double multiplier=com.hexvane.aetherhaven.equipment.ProfessionKitService.workMultiplier(progress,
            com.hexvane.aetherhaven.townsfolk.TownsfolkSkill.BLACKSMITHING,kits());
        assertFalse(town.advanceWork(batch.id(),.25,multiplier));
        town.pickup(batch.id(),"courier",1);town.deliver(batch.id(),"courier");
        for(int i=0;i<80;i++)assertTrue(town.advanceWork(batch.id(),.25,multiplier));
        assertEquals(23,batch.workSeconds(),.00001);
        for(double invalid:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY,2.1})
            assertFalse(town.advanceWork(batch.id(),.25,invalid));
        assertFalse(town.advanceWork(batch.id(),86400,multiplier));
        assertEquals(23,reload(town).batch(batch.id()).workSeconds(),.00001);
    }
    @Test void courierSatchelCarriesThirdDistinctStackAndPreservesItAcrossReload() {
        var town=new TownWarehouse();supply(town,stock("ore",2),stock("wood",3),stock("fibre",4));
        var batch=town.reserve(UUID.randomUUID(),UUID.randomUUID(),"recipe",
            List.of(ingredient("ore",2),ingredient("wood",3),ingredient("fibre",4)),List.of(stock("kit",1)));
        var progress=new com.hexvane.aetherhaven.townsfolk.TownsfolkProgress();
        progress.setSkill(com.hexvane.aetherhaven.townsfolk.TownsfolkSkill.LOGISTICS,3);
        assertEquals(2,CourierAssignments.carryingStacks(progress,kits()));
        progress.setProfessionKitData(com.hexvane.aetherhaven.townsfolk.TownsfolkProgress.encodeProfessionKit(stock("courierKit",1)));
        assertEquals(9,town.pickup(batch.id(),"courier",CourierAssignments.carryingStacks(progress,kits())));
        assertEquals(3,batch.cargoItems().size());assertTrue(batch.pendingItems().isEmpty());
        var restored=reload(town);
        assertEquals(3,restored.batch(batch.id()).cargoItems().size());
        assertTrue(restored.deliver(batch.id(),"courier"));assertFalse(restored.deliver(batch.id(),"courier"));
        assertEquals(3,restored.batch(batch.id()).deliveredItems().size());
    }
    @Test void highSkillEquipmentCapacityExceedsFiveAndOldCargoStillLoads() {
        var town=new TownWarehouse();
        var inputs=new ArrayList<MaterialQuantity>();
        for(int i=0;i<8;i++){supply(town,stock("material"+i,1));inputs.add(ingredient("material"+i,1));}
        var batch=town.reserve(UUID.randomUUID(),UUID.randomUUID(),"recipe",inputs,List.of(stock("kit",1)));
        assertEquals(8,town.pickup(batch.id(),"courier",8));
        var restored=reload(town);assertEquals(8,restored.batch(batch.id()).cargoItems().size());
        assertTrue(restored.deliver(batch.id(),"courier"));

        var legacy=bag(5);put(legacy,4,new ItemStack("heirloom",1,BsonDocument.parse("{signed:true}")));
        var json=new Gson().toJsonTree(batch).getAsJsonObject();
        json.addProperty("cargoData",SimpleItemContainer.CODEC.encode(legacy,new com.hypixel.hytale.codec.ExtraInfo()).asDocument().toJson());
        var migrated=new Gson().fromJson(json,WarehouseBatch.class);
        assertEquals(10,migrated.cargo().getCapacity());
        assertEquals(legacy.getItemStack((short)4),migrated.cargoItems().getFirst());
        assertEquals(batch.courierId(),migrated.courierId());
    }
    @Test void oneDeliveryCoversSeveralCraftsAndCommitsThemTogether() {
        var town=new TownWarehouse();supply(town,stock("ore",9));
        var orders=new com.hexvane.aetherhaven.workplace.WorkplaceOrders();
        orders.add(0,"recipe",com.hexvane.aetherhaven.workplace.WorkplaceOrders.Mode.COUNT,6);var order=orders.list().getFirst();
        var site=UUID.randomUUID();
        // Five crafts would need ten ore. Four fit what is in stock.
        assertNull(town.reserveCrafts(site,order.id(),"recipe",List.of(ingredient("ore",2)),List.of(stock("kit",1)),List.of(),5,1));
        assertEquals(0,town.queuedForOrder(order.id()),"A refused delivery reserves nothing");
        var batch=town.reserveCrafts(site,order.id(),"recipe",List.of(ingredient("ore",2)),List.of(stock("kit",1)),List.of(),4,1);
        assertNotNull(batch);assertEquals(4,batch.crafts());
        assertEquals(4,town.queuedForOrder(order.id()),"Queued work is counted in crafts");
        assertEquals(2,order.batchesNeeded(0,0,1,town.queuedForOrder(order.id())));
        assertEquals(4,town.incoming("kit"));
        assertNull(town.reserveCrafts(site,order.id(),"recipe",List.of(ingredient("ore",2)),List.of(stock("kit",1)),List.of(),1,1),
            "The bench is held by one delivery at a time");
        // One trip carries all of it, and it survives a reload.
        assertEquals(8,town.pickup(batch.id(),"courier",1));town.deliver(batch.id(),"courier");
        town=reload(town);batch=town.batch(batch.id());assertEquals(4,batch.crafts());
        assertEquals(1,town.count(TownWarehouse.Page.GENERAL,"ore"));
        // Four crafts need four times the work. Nothing is made part way.
        for(int i=0;i<24*4-1;i++)assertTrue(town.advanceWork(batch.id(),.25));
        assertFalse(town.finishWork(batch.id(),orders,6));assertEquals(0,town.count(TownWarehouse.Page.OUTPUT,"kit"));
        assertTrue(town.advanceWork(batch.id(),.25));
        assertTrue(town.finishWork(batch.id(),orders,6));
        assertEquals(4,town.count(TownWarehouse.Page.OUTPUT,"kit"));assertEquals(4,order.completed());
        assertEquals(0,town.queuedForOrder(order.id()));assertEquals(2,order.batchesNeeded(0,0,1,0));
        assertFalse(town.finishWork(batch.id(),orders,6));assertEquals(4,town.count(TownWarehouse.Page.OUTPUT,"kit"));
    }
    @Test void aDeliveryOfSeveralCraftsIsRefusedWhenItNeedsMoreStacksThanAllowedAndCancelReturnsEverything() {
        var town=new TownWarehouse();supply(town,stock("ore",8),stock("wood",8));
        var orders=new com.hexvane.aetherhaven.workplace.WorkplaceOrders();
        orders.add(0,"recipe",com.hexvane.aetherhaven.workplace.WorkplaceOrders.Mode.FOREVER,1);var order=orders.list().getFirst();
        var site=UUID.randomUUID();var inputs=List.of(ingredient("ore",2),ingredient("wood",2));
        assertNull(town.reserveCrafts(site,order.id(),"recipe",inputs,List.of(stock("kit",1)),List.of(),4,1),"Two kinds of material need two stacks");
        assertNull(town.reserveCrafts(site,order.id(),"recipe",inputs,List.of(stock("kit",1)),List.of(),17,9),"More crafts than one delivery may cover");
        var batch=town.reserveCrafts(site,order.id(),"recipe",inputs,List.of(stock("kit",1)),List.of(),4,2);
        assertNotNull(batch);assertEquals(4,batch.crafts());
        // A single craft keeps today's behaviour: any number of trips, no stack limit.
        town.cancel(batch.id());assertEquals(0,town.queuedForOrder(order.id()));
        assertEquals(8,town.count(TownWarehouse.Page.GENERAL,"ore"));assertEquals(8,town.count(TownWarehouse.Page.GENERAL,"wood"));
        var single=town.reserveCrafts(site,order.id(),"recipe",inputs,List.of(stock("kit",1)),List.of(),1,1);
        assertNotNull(single);assertEquals(1,single.crafts());
    }
    @Test void aFurnaceLoadCoversAWholeStackAndPromisesRoomForAllItsCharcoal() {
        var town=new TownWarehouse();supply(town,stock("ore",10),stock("wood",12));
        var site=UUID.randomUUID();var orders=new com.hexvane.aetherhaven.workplace.WorkplaceOrders();
        orders.add(0,"smelt",com.hexvane.aetherhaven.workplace.WorkplaceOrders.Mode.COUNT,10);var order=orders.list().getFirst();
        assertNull(town.reserveSmeltingCrafts(site,order.id(),"smelt",List.of(ingredient("ore",1)),List.of(stock("ingot",1)),List.of(),"wood",8,11,8),
            "There is not that much ore");
        // Eight ore with six new logs, on top of four logs already in the furnace.
        var load=town.reserveSmeltingCrafts(site,order.id(),"smelt",List.of(ingredient("ore",1)),List.of(stock("ingot",1)),List.of(),"wood",6,8,10);
        assertNotNull(load);assertEquals(8,load.crafts());assertEquals(8,town.queuedForOrder(order.id()));
        assertEquals(2,order.batchesNeeded(0,0,1,town.queuedForOrder(order.id())));
        assertEquals(8,town.smelting().promised("ingot"));
        assertEquals(5,town.smelting().promised("Ingredient_Charcoal"),"Ten logs leave five charcoal, and all of it needs room");
        town.pickup(load.id(),"courier",2);town.deliver(load.id(),"courier");
        var input=bag(2);var fuel=bag(1);
        assertTrue(town.loadFurnace(load.id(),input,fuel,bag(4)));
        assertEquals(8,input.getItemStack((short)0).getQuantity(),"The whole load goes in at once");
        assertEquals(6,fuel.getItemStack((short)0).getQuantity());
        town=reload(town);assertEquals(8,town.batch(load.id()).crafts());
        assertTrue(town.finishFurnace(load.id(),orders));assertEquals(8,order.completed());
        assertEquals(2,order.batchesNeeded(0,0,1,town.queuedForOrder(order.id())));
    }
    @Test void deliverySizesAreBoundedAndShrinkWhenTheyDoNotFit() {
        assertEquals(1,WorkplaceBatchSize.crafts(1,false));assertEquals(5,WorkplaceBatchSize.crafts(5,false));
        assertEquals(WorkplaceBatchSize.MAX_CRAFTS,WorkplaceBatchSize.crafts(500,false));
        assertEquals(WorkplaceBatchSize.FOREVER_CRAFTS,WorkplaceBatchSize.crafts(Integer.MAX_VALUE,true),"A task with no end takes only some at a time");
        assertEquals(25,WorkplaceBatchSize.furnaceCrafts(Integer.MAX_VALUE,true,25),"A furnace takes a whole stack even for an endless task");
        assertEquals(3,WorkplaceBatchSize.furnaceCrafts(3,false,25));assertEquals(25,WorkplaceBatchSize.furnaceCrafts(60,false,25));
        assertEquals(1,WorkplaceBatchSize.furnaceCrafts(0,false,25));
        assertEquals(8,WorkplaceBatchSize.smaller(16));assertEquals(1,WorkplaceBatchSize.smaller(3));assertEquals(0,WorkplaceBatchSize.smaller(1));
        assertEquals(0,WorkplaceBatchSize.stacks(0,25));assertEquals(1,WorkplaceBatchSize.stacks(25,25));assertEquals(2,WorkplaceBatchSize.stacks(26,25));
    }
    @Test void finishingWorkCommitsOutputAndOrderOnceAndHonorsPause() {
        var town=new TownWarehouse();supply(town,stock("ore",2));
        var orders=new com.hexvane.aetherhaven.workplace.WorkplaceOrders();
        orders.add(0,"recipe",com.hexvane.aetherhaven.workplace.WorkplaceOrders.Mode.COUNT,1);var order=orders.list().getFirst();
        var batch=town.reserve(UUID.randomUUID(),order.id(),"recipe",List.of(ingredient("ore",2)),List.of(stock("kit",1)));
        town.pickup(batch.id(),"courier",1);town.deliver(batch.id(),"courier");
        for(int i=0;i<23;i++)town.advanceWork(batch.id(),.25);
        assertFalse(town.finishWork(batch.id(),orders,6));assertEquals(0,order.completed());
        town.advanceWork(batch.id(),.25);orders.change(orders.revision(),order.id(),"Pause");
        assertFalse(town.finishWork(batch.id(),orders,6));assertEquals(0,town.count(TownWarehouse.Page.OUTPUT,"kit"));
        orders.change(orders.revision(),order.id(),"Pause");assertTrue(town.finishWork(batch.id(),orders,6));
        assertEquals(1,order.completed());assertEquals(0,order.batchesNeeded(0,0,1));
        assertFalse(town.finishWork(batch.id(),orders,6));assertEquals(1,town.count(TownWarehouse.Page.OUTPUT,"kit"));
        assertFalse(orders.canComplete(order.id(),"recipe"));
    }
    @Test void removingAnOrderCannotFinishOrConsumeItsDeliveredMaterials() {
        var town=new TownWarehouse();supply(town,stock("ore",2));
        var orders=new com.hexvane.aetherhaven.workplace.WorkplaceOrders();
        orders.add(0,"recipe",com.hexvane.aetherhaven.workplace.WorkplaceOrders.Mode.COUNT,1);
        var order=orders.list().getFirst();
        var batch=town.reserve(UUID.randomUUID(),order.id(),"recipe",List.of(ingredient("ore",2)),List.of(stock("kit",1)));
        town.pickup(batch.id(),"courier",1);town.deliver(batch.id(),"courier");
        for(int i=0;i<24;i++)town.advanceWork(batch.id(),.25);
        orders.change(orders.revision(),order.id(),"Remove");
        assertFalse(town.finishWork(batch.id(),orders,6));
        assertEquals(2,town.batch(batch.id()).deliveredItems().getFirst().getQuantity());
        assertEquals(0,town.count(TownWarehouse.Page.OUTPUT,"kit"));
    }
    @Test void processingFurnaceFreesSitePreparationButNeverAllowsTwoPendingDeliveries() {
        var town=new TownWarehouse();supply(town,stock("ore",4),stock("wood",8));
        var site=UUID.randomUUID();var orders=new com.hexvane.aetherhaven.workplace.WorkplaceOrders();
        orders.add(0,"smelt",com.hexvane.aetherhaven.workplace.WorkplaceOrders.Mode.COUNT,2);var order=orders.list().getFirst();
        var first=town.reserveSmelting(site,order.id(),"smelt",List.of(ingredient("ore",1)),List.of(stock("ingot",1)),List.of(),"wood",2);
        assertNotNull(first);assertFalse(town.canPrepare(site,true));
        assertEquals(1,order.batchesNeeded(0,1,1,town.queuedForOrder(order.id())));
        var point=new WarehouseBatch.Point(20,123,30);assertTrue(town.setDestination(first.id(),point));
        assertTrue(town.destinationReserved(point));
        assertNull(town.reserveSmelting(site,order.id(),"smelt",List.of(ingredient("ore",1)),List.of(stock("ingot",1)),List.of(),"wood",2));
        town.pickup(first.id(),"courier",2);town.deliver(first.id(),"courier");
        assertTrue(town.loadFurnace(first.id(),bag(2),bag(1),bag(4)));town=reload(town);
        assertTrue(town.canPrepare(site,true));assertFalse(town.canPrepare(site,false));
        var second=town.reserveSmelting(site,order.id(),"smelt",List.of(ingredient("ore",1)),List.of(stock("ingot",1)),List.of(),"wood",2);
        assertNotNull(second);assertFalse(town.canPrepare(site,true));
        assertEquals(0,order.batchesNeeded(0,2,1,town.queuedForOrder(order.id())));
        assertEquals(2,town.smelting().promised("ingot"));
        town.cancel(second.id());assertEquals(1,town.queuedForOrder(order.id()));
        assertEquals(1,order.batchesNeeded(0,1,1,town.queuedForOrder(order.id())));
        assertTrue(town.destinationReserved(point));
        town.finishFurnace(first.id(),orders);assertFalse(town.destinationReserved(point));
    }
    @Test void maintainTargetCountsIncomingItemsWithoutDoubleCountingQueuedFurnaces() {
        var orders=new com.hexvane.aetherhaven.workplace.WorkplaceOrders();
        orders.add(0,"smelt",com.hexvane.aetherhaven.workplace.WorkplaceOrders.Mode.MAINTAIN,10);var order=orders.list().getFirst();
        assertEquals(2,order.batchesNeeded(4,4,1,4));assertEquals(0,order.batchesNeeded(4,6,1,6));
    }
    @Test void charcoalOrderLoadsOnlyFuelAndDoesNotDoubleReserveItsByproduct() {
        var town=new TownWarehouse();supply(town,stock("wood",2));
        var job=town.reserveSmelting(UUID.randomUUID(),UUID.randomUUID(),"smelt_charcoal",List.of(),List.of(stock("Ingredient_Charcoal",1)),List.of(),"wood",2);
        assertNotNull(job);assertEquals(1,town.smelting().promised("Ingredient_Charcoal"));
        town.pickup(job.id(),"courier",1);town.deliver(job.id(),"courier");
        var input=bag(2);var fuel=bag(1);var output=bag(4);
        assertTrue(town.loadFurnace(job.id(),input,fuel,output));assertTrue(input.isEmpty());
        assertEquals(2,fuel.getItemStack((short)0).getQuantity());assertTrue(output.isEmpty());
        assertEquals(0,town.smelting().count("Ingredient_Charcoal"));
    }
    @Test void smeltingLimitsAreSharedAcrossFurnacesAndSurviveReload() {
        var store=new SmeltingOutputStore();var first=UUID.randomUUID();var second=UUID.randomUUID();
        assertTrue(store.reserve(first,List.of(stock("ingot",60),stock("coal",1))));
        assertFalse(store.reserve(second,List.of(stock("ingot",41))));
        assertTrue(store.reserve(second,List.of(stock("ingot",40))));
        var furnace=bag(4);put(furnace,0,stock("ingot",60));put(furnace,1,stock("other",4));
        var origin=UUID.randomUUID();var point=new WarehouseBatch.Point(10,123,20);
        assertEquals(60,store.collect(first,origin,point,furnace));
        assertEquals(4,furnace.getItemStack((short)1).getQuantity());
        store=new Gson().fromJson(new Gson().toJson(store),SmeltingOutputStore.class);
        assertEquals(60,store.count("ingot"));assertEquals(40,store.promised("ingot"));
        assertEquals(point,store.origin(origin).location());
        assertFalse(store.reserve(UUID.randomUUID(),List.of(stock("ingot",1))));
        assertTrue(store.release(first));assertEquals(0,store.promised("coal"));
        assertTrue(store.release(second));assertTrue(store.reserve(UUID.randomUUID(),List.of(stock("ingot",40))));
    }
    @Test void smeltingCollectionUsesOnlyActualOutputAndKeepsItsProperties() {
        var store=new SmeltingOutputStore();var job=UUID.randomUUID();var origin=UUID.randomUUID();
        var point=new WarehouseBatch.Point(10,123,20);var furnace=bag(4);
        assertTrue(store.reserve(job,List.of(stock("ingot",2))));
        assertEquals(0,store.collect(job,origin,point,furnace));assertEquals(0,store.count("ingot"));
        var special=new ItemStack("ingot",2,BsonDocument.parse("{qualityNote:'test'}"));put(furnace,0,special);
        assertEquals(2,store.collect(job,origin,point,furnace));assertEquals(special,store.origin(origin).stack((short)0));
        put(furnace,0,stock("ingot",1));assertEquals(0,store.collect(job,origin,point,furnace));
        var recipient=bag(1);put(recipient,0,stock("unrelated",100));
        assertEquals(0,store.withdraw(origin,(short)0,special,2,recipient,store.revision()));
        assertEquals(2,store.count("ingot"));recipient=bag(1);
        long version=store.revision();assertEquals(1,store.withdraw(origin,(short)0,special,1,recipient,version));
        assertEquals(0,store.withdraw(origin,(short)0,special,1,recipient,version));assertEquals(1,store.count("ingot"));
        assertEquals(special.withQuantity(1),recipient.getItemStack((short)0));
    }
    @Test void nativeFurnaceLoadingConsumesDeliveredSuppliesOnceWithoutCreatingOutput() {
        var town=new TownWarehouse();supply(town,stock("ore",1),stock("wood",2));
        var job=town.reserveSmelting(UUID.randomUUID(),UUID.randomUUID(),"smelt",List.of(ingredient("ore",1)),List.of(stock("ingot",1)),List.of(),"wood",2);
        assertNotNull(job);assertEquals(1,town.smelting().promised("ingot"));
        var input=bag(2);var fuel=bag(1);var output=bag(4);
        assertFalse(town.loadFurnace(job.id(),input,fuel,output));
        town.pickup(job.id(),"courier",3);town.deliver(job.id(),"courier");
        put(fuel,0,stock("other",100));assertFalse(town.loadFurnace(job.id(),input,fuel,output));assertTrue(input.isEmpty());
        fuel=bag(1);assertTrue(town.loadFurnace(job.id(),input,fuel,output));
        assertEquals(1,input.getItemStack((short)0).getQuantity());assertEquals(2,fuel.getItemStack((short)0).getQuantity());
        assertTrue(output.isEmpty());assertEquals(0,town.count(TownWarehouse.Page.OUTPUT,"ingot"));
        assertFalse(town.loadFurnace(job.id(),input,fuel,output));assertFalse(town.finish(job.id()));
        town=reload(town);assertTrue(town.batch(job.id()).nativeLoaded());
        assertTrue(town.cancel(job.id()));assertNotNull(town.batch(job.id()));assertEquals(1,town.smelting().promised("ingot"));
        assertTrue(town.finishFurnace(job.id(),null));assertNull(town.batch(job.id()));assertEquals(0,town.smelting().promised("ingot"));
        assertTrue(output.isEmpty());assertEquals(0,town.smelting().count("ingot"));
    }
    @Test void cancellingUndeliveredFurnaceSuppliesReleasesCapacityWithoutRemovingStock() {
        var town=new TownWarehouse();supply(town,stock("ore",1),stock("wood",2));
        var job=town.reserveSmelting(UUID.randomUUID(),UUID.randomUUID(),"smelt",List.of(ingredient("ore",1)),List.of(stock("ingot",1)),List.of(),"wood",2);
        assertTrue(town.cancel(job.id()));assertEquals(0,town.smelting().promised("ingot"));
        assertEquals(1,town.count(TownWarehouse.Page.GENERAL,"ore"));assertEquals(2,town.count(TownWarehouse.Page.GENERAL,"wood"));
    }
    @Test void couriersCollectReservedIngotsAtTheirFurnaceInsteadOfTheLedger() {
        var town=new TownWarehouse();var smelt=town.smelting();var origin=UUID.randomUUID();var point=new WarehouseBatch.Point(20,123,30);
        var job=UUID.randomUUID();assertTrue(smelt.reserve(job,List.of(stock("ore",8))));
        var furnace=bag(4);put(furnace,0,stock("ore",8));assertEquals(8,smelt.collect(job,origin,point,furnace));smelt.release(job);
        var batch=reserve(town,6);assertNotNull(batch);assertEquals(6,town.reservedSmelting(origin,(short)0));
        assertEquals(0,town.pickup(batch.id(),"courier",1));
        assertEquals(0,town.pickupSmeltingAt(batch.id(),"courier",1,origin,new WarehouseBatch.Point(0,0,0)));
        town=reload(town);assertEquals(6,town.pickupSmeltingAt(batch.id(),"courier",1,origin,point));
        assertEquals(2,town.smelting().count("ore"));assertTrue(town.deliver(batch.id(),"courier"));assertTrue(town.finish(batch.id()));
        assertEquals(1,town.count(TownWarehouse.Page.OUTPUT,"kit"));
    }
    @Test void pausingAfterNativeLoadingStillCountsTheFinishedBatchExactlyOnce() {
        var town=new TownWarehouse();supply(town,stock("ore",1),stock("wood",1));
        var orders=new com.hexvane.aetherhaven.workplace.WorkplaceOrders();
        orders.add(0,"smelt",com.hexvane.aetherhaven.workplace.WorkplaceOrders.Mode.COUNT,1);var order=orders.list().getFirst();
        var job=town.reserveSmelting(UUID.randomUUID(),order.id(),"smelt",List.of(ingredient("ore",1)),List.of(stock("ingot",1)),List.of(),"wood",1);
        town.pickup(job.id(),"courier",2);town.deliver(job.id(),"courier");
        assertTrue(town.loadFurnace(job.id(),bag(2),bag(1),bag(4)));orders.change(orders.revision(),order.id(),"Pause");
        assertTrue(town.finishFurnace(job.id(),orders));assertEquals(1,order.completed());
        assertFalse(town.finishFurnace(job.id(),orders));assertEquals(1,order.completed());
        assertEquals(0,town.count(TownWarehouse.Page.OUTPUT,"ingot"));
    }
    private static MaterialQuantity ingredient(String id,int quantity) { return new MaterialQuantity(id,null,null,quantity,null); }
    private static TownWarehouse reload(TownWarehouse town) { return new Gson().fromJson(new Gson().toJson(town),TownWarehouse.class); }
    private static void supply(TownWarehouse town,ItemStack... stacks) {
        var bag=bag(stacks.length);
        for(int i=0;i<stacks.length;i++) {put(bag,i,stacks[i]);assertEquals(stacks[i].getQuantity(),deposit(town,bag,i,TownWarehouse.Amount.STACK).moved());}
    }
    private static WarehouseBatch reserve(TownWarehouse town,int quantity) {
        return town.reserve(UUID.randomUUID(),UUID.randomUUID(),"recipe",List.of(ingredient("ore",quantity)),List.of(stock("kit",1)));
    }
    @Test void reservedSuppliesStayAtSourceAndOnlyUnreservedStockCanBeWithdrawn() {
        var town=new TownWarehouse();supply(town,stock("ore",12));var batch=reserve(town,8);
        assertNotNull(batch);assertEquals(12,town.count(TownWarehouse.Page.GENERAL,"ore"));
        assertEquals(8,town.reserved(TownWarehouse.Page.GENERAL,(short)0));assertEquals(1,town.incoming("kit"));
        var player=bag(2);
        assertEquals(4,town.withdraw(TownWarehouse.Page.GENERAL,player,(short)0,town.stack(TownWarehouse.Page.GENERAL,(short)0),TownWarehouse.Amount.STACK,town.revision()).moved());
        assertEquals(0,town.withdraw(TownWarehouse.Page.GENERAL,player,(short)0,town.stack(TownWarehouse.Page.GENERAL,(short)0),TownWarehouse.Amount.ONE,town.revision()).moved());
        assertEquals(8,town.count(TownWarehouse.Page.GENERAL,"ore"));
        assertNull(reserve(town,1));
        assertNull(town.reserve(batch.workplaceId(),UUID.randomUUID(),"recipe",List.of(),List.of(stock("kit",1))));
    }
    @Test void overlappingIngredientQueriesCannotPartiallyReserveOrCreateFreeCrafts() {
        var town=new TownWarehouse();supply(town,stock("ore",6));long revision=town.revision();
        assertNull(town.reserve(UUID.randomUUID(),UUID.randomUUID(),"recipe",List.of(ingredient("ore",4),ingredient("ore",4)),List.of(stock("kit",1))));
        assertEquals(6,town.count(TownWarehouse.Page.GENERAL,"ore"));assertEquals(revision,town.revision());assertTrue(town.batches().isEmpty());
    }
    @Test void twoCouriersCannotTakeTheSameBatchOrCarryTwoBatchesAtOnce() {
        var town=new TownWarehouse();supply(town,stock("ore",20));var first=reserve(town,8);var second=reserve(town,8);
        assertNotNull(first);assertNotNull(second);
        assertFalse(town.finish(first.id()));assertEquals(8,town.pickup(first.id(),"courier",1));
        assertEquals(0,town.pickup(first.id(),"other",1));assertEquals(0,town.pickup(second.id(),"courier",1));
        assertFalse(town.deliver(first.id(),"other"));assertFalse(town.finish(first.id()));
        assertTrue(town.deliver(first.id(),"courier"));assertFalse(town.deliver(first.id(),"courier"));
        assertTrue(town.finish(first.id()));assertFalse(town.finish(first.id()));
        assertEquals(1,town.count(TownWarehouse.Page.OUTPUT,"kit"));assertEquals(8,town.pickup(second.id(),"courier",1));
    }
    @Test void stackCapacityRequiresMultipleTripsAndEveryStageSurvivesReload() {
        var town=new TownWarehouse();supply(town,stock("ore",150),stock("wood",30));
        var batch=town.reserve(UUID.randomUUID(),UUID.randomUUID(),"recipe",List.of(ingredient("ore",150),ingredient("wood",30)),List.of(stock("kit",2)));
        assertNotNull(batch);var id=batch.id();town=reload(town);
        assertEquals(100,town.pickup(id,"courier",1));town=reload(town);
        assertEquals(100,town.batch(id).cargoItems().getFirst().getQuantity());assertEquals("courier",town.batch(id).courierId());
        assertEquals(WarehouseBatch.State.IN_TRANSIT,town.batch(id).state());assertTrue(town.deliver(id,"courier"));
        town=reload(town);assertEquals(WarehouseBatch.State.AWAITING_PICKUP,town.batch(id).state());assertFalse(town.finish(id));
        assertEquals(50,town.pickup(id,"courier",1));assertTrue(town.deliver(id,"courier"));
        assertEquals(30,town.pickup(id,"courier",1));assertTrue(town.deliver(id,"courier"));
        town=reload(town);assertEquals(WarehouseBatch.State.READY,town.batch(id).state());
        assertTrue(town.finish(id));assertEquals(2,town.count(TownWarehouse.Page.OUTPUT,"kit"));assertEquals(0,town.used(TownWarehouse.Page.GENERAL));
    }
    @Test void cancellationDoesNotTeleportCargoOrLoseDeliveredSupplies() {
        var town=new TownWarehouse();supply(town,stock("ore",150),stock("wood",30));
        var batch=town.reserve(UUID.randomUUID(),UUID.randomUUID(),"recipe",List.of(ingredient("ore",150),ingredient("wood",30)),List.of(stock("kit",1)));
        var id=batch.id();assertEquals(100,town.pickup(id,"courier",1));assertTrue(town.deliver(id,"courier"));
        assertEquals(50,town.pickup(id,"courier",1));assertTrue(town.cancel(id));town=reload(town);
        assertEquals(0,town.incoming("kit"));assertEquals(0,town.reserved(TownWarehouse.Page.GENERAL,(short)2));
        assertEquals(0,town.count(TownWarehouse.Page.GENERAL,"ore"));assertEquals(30,town.count(TownWarehouse.Page.GENERAL,"wood"));
        assertFalse(town.deliver(id,"courier"));assertFalse(town.finish(id));assertTrue(town.returnCargo(id,"courier"));
        assertEquals(50,town.count(TownWarehouse.Page.GENERAL,"ore"));assertNotNull(town.batch(id));
        assertEquals(100,town.collectReturn(id,"courier",1));assertTrue(town.returnCargo(id,"courier"));
        assertEquals(150,town.count(TownWarehouse.Page.GENERAL,"ore"));assertNull(town.batch(id));
        assertFalse(town.returnCargo(id,"courier"));assertEquals(0,town.used(TownWarehouse.Page.OUTPUT));
    }
    @Test void fullStorageLeavesCancelledCargoWithCourierUntilSpaceIsAvailable() {
        var town=new TownWarehouse();supply(town,stock("ore",4));var batch=reserve(town,4);var id=batch.id();
        town.pickup(id,"courier",1);town.cancel(id);
        for(int i=0;i<TownWarehouse.BASE_GENERAL_SLOTS;i++)supply(town,stock("filler"+i,100));
        assertFalse(town.returnCargo(id,"courier"));town=reload(town);
        assertEquals(4,town.batch(id).cargoItems().getFirst().getQuantity());
        assertEquals(100,town.withdraw(TownWarehouse.Page.GENERAL,bag(1),(short)0,town.stack(TownWarehouse.Page.GENERAL,(short)0),TownWarehouse.Amount.STACK,town.revision()).moved());
        assertTrue(town.returnCargo(id,"courier"));assertEquals(4,town.count(TownWarehouse.Page.GENERAL,"ore"));
    }
    @Test void outputSpaceIsReservedAcrossSitesAndMultiOutputMustFitInFull() {
        var town=new TownWarehouse();supply(town,stock("ore",10));
        for(int i=0;i<TownWarehouse.OUTPUT_SLOTS-1;i++) assertTrue(town.acceptCompletedOutput(stock("filler"+i,100)));
        assertNull(town.reserve(UUID.randomUUID(),UUID.randomUUID(),"recipe",List.of(ingredient("ore",2)),List.of(stock("one",100),stock("two",100))));
        assertEquals(0,town.reserved(TownWarehouse.Page.GENERAL,(short)0));
        var first=town.reserve(UUID.randomUUID(),UUID.randomUUID(),"recipe",List.of(ingredient("ore",2)),List.of(stock("kit",100)));
        assertNotNull(first);assertNull(reserve(town,2));assertFalse(town.acceptCompletedOutput(stock("unrelated",1)));
        town.pickup(first.id(),"courier",1);town.deliver(first.id(),"courier");assertTrue(town.finish(first.id()));
        assertEquals(100,town.count(TownWarehouse.Page.OUTPUT,"kit"));
    }
    @Test void metadataSurvivesPickupCancellationAndReturn() {
        var town=new TownWarehouse();
        var special=new ItemStack("ore",7,12.0,40.0,BsonDocument.parse("{custom: {name:'Precious'}}"));supply(town,special);
        var batch=town.reserve(UUID.randomUUID(),UUID.randomUUID(),"recipe",List.of(new MaterialQuantity("ore",null,null,7,BsonDocument.parse("{custom: {name:'Precious'}}"))),List.of(stock("kit",1)));
        assertNotNull(batch);town.pickup(batch.id(),"courier",1);town=reload(town);
        assertEquals(special,town.batch(batch.id()).cargoItems().getFirst());town.cancel(batch.id());
        assertTrue(town.returnCargo(batch.id(),"courier"));assertEquals(special,town.stack(TownWarehouse.Page.GENERAL,(short)0));
    }
    @Test void craftedGoodsAreNotCollectedThroughTheLedgerDesk() {
        var town=new TownWarehouse();supply(town,stock("ore",4));town.acceptCompletedOutput(stock("ore",6));
        assertNull(reserve(town,10));
        var batch=reserve(town,4);assertNotNull(batch);
        assertEquals(0,town.reserved(TownWarehouse.Page.OUTPUT,(short)0));assertTrue(town.upgrade(town.revision()));town=reload(town);
        assertEquals(4,town.pickup(batch.id(),"courier",1));assertEquals(6,town.count(TownWarehouse.Page.OUTPUT,"ore"));
        assertTrue(town.deliver(batch.id(),"courier"));assertTrue(town.finish(batch.id()));
        assertEquals(1,town.count(TownWarehouse.Page.OUTPUT,"kit"));
    }
    private static com.hexvane.aetherhaven.hunter.TownHunts huntStock() {
        var hunts=new com.hexvane.aetherhaven.hunter.TownHunts();
        hunts.unlock("bone");var run=hunts.begin();
        for(int i=0;i<6;i++)assertTrue(hunts.collect(run,i,hunts.next()));
        hunts.end("Test run completed");return hunts;
    }
    private static WarehouseBatch reserveBones(TownWarehouse stock,int count,TownWarehouse.ProductionStock lodge) {
        return stock.reserve(UUID.randomUUID(),UUID.randomUUID(),"bone_recipe",
            List.of(ingredient("Ingredient_Bone_Fragment",count)),List.of(stock("kit",1)),List.of(lodge));
    }
    @Test void lodgeSpoilsRemainAtLodgeUntilPhysicalPickupAndSurviveReload() {
        var town=new TownWarehouse();var hunts=huntStock();
        var lodge=new TownWarehouse.ProductionStock(UUID.randomUUID(),new WarehouseBatch.Point(20,100,30),hunts);
        var batch=reserveBones(town,8,lodge);assertNotNull(batch);
        assertEquals(12,hunts.amount("Ingredient_Bone_Fragment"));assertEquals(8,town.reservedProduction(lodge.plot(),"Ingredient_Bone_Fragment"));
        assertEquals(0,town.pickup(batch.id(),"courier",2));
        assertEquals(0,town.pickupAt(batch.id(),"courier",2,lodge,new WarehouseBatch.Point(21,100,30)));
        town=reload(town);hunts=new Gson().fromJson(new Gson().toJson(hunts),com.hexvane.aetherhaven.hunter.TownHunts.class);
        lodge=new TownWarehouse.ProductionStock(lodge.plot(),lodge.pickup(),hunts);
        assertEquals(lodge.pickup(),town.batch(batch.id()).pendingSources().getFirst().pickup());
        long before=hunts.revision();assertEquals(8,town.pickupAt(batch.id(),"courier",2,lodge,lodge.pickup()));
        assertEquals(4,hunts.amount("Ingredient_Bone_Fragment"));assertTrue(hunts.revision()>before);
        assertEquals(0,town.pickupAt(batch.id(),"courier",2,lodge,lodge.pickup()));
        assertTrue(town.deliver(batch.id(),"courier"));assertTrue(town.finish(batch.id()));
        assertEquals(1,town.count(TownWarehouse.Page.OUTPUT,"kit"));assertEquals(0,town.count(TownWarehouse.Page.GENERAL,"Ingredient_Bone_Fragment"));
    }
    @Test void cancelledLodgeReservationsReleaseOnlyUncollectedStock() {
        var town=new TownWarehouse();var hunts=huntStock();
        var lodge=new TownWarehouse.ProductionStock(UUID.randomUUID(),new WarehouseBatch.Point(20,100,30),hunts);
        var first=reserveBones(town,8,lodge);assertNotNull(first);assertNull(reserveBones(town,8,lodge));
        assertTrue(town.cancel(first.id()));assertEquals(12,hunts.amount("Ingredient_Bone_Fragment"));
        var second=reserveBones(town,8,lodge);assertNotNull(second);
        assertEquals(8,town.pickupAt(second.id(),"courier",1,lodge,lodge.pickup()));assertTrue(town.cancel(second.id()));
        assertEquals(4,hunts.amount("Ingredient_Bone_Fragment"));assertEquals(0,town.reservedProduction(lodge.plot(),"Ingredient_Bone_Fragment"));
        assertTrue(town.returnCargo(second.id(),"courier"));assertFalse(town.returnCargo(second.id(),"courier"));
        assertEquals(8,town.count(TownWarehouse.Page.GENERAL,"Ingredient_Bone_Fragment"));
    }
    @Test void depletedLodgeStockCannotCreateCargo() {
        var town=new TownWarehouse();var hunts=huntStock();
        var lodge=new TownWarehouse.ProductionStock(UUID.randomUUID(),new WarehouseBatch.Point(20,100,30),hunts);
        var batch=reserveBones(town,8,lodge);assertNotNull(batch);assertTrue(hunts.withdraw("Ingredient_Bone_Fragment",8));
        assertEquals(0,town.pickupAt(batch.id(),"courier",1,lodge,lodge.pickup()));
        assertEquals(4,hunts.amount("Ingredient_Bone_Fragment"));assertTrue(batch.cargoItems().isEmpty());
        assertEquals(8,town.reservedProduction(lodge.plot(),"Ingredient_Bone_Fragment"));
    }
    @Test void productionInventoryRejectsWholeWithdrawalWhenAnyItemIsMissing() {
        ProductionInventory hunts=huntStock();long before=((com.hexvane.aetherhaven.hunter.TownHunts)hunts).revision();
        assertFalse(hunts.withdrawAll(Map.of("Ingredient_Bone_Fragment",3L,"missing",1L)));
        assertFalse(hunts.withdrawAll(Map.of("Ingredient_Bone_Fragment",-1L)));
        assertEquals(12,hunts.getAmount("Ingredient_Bone_Fragment"));assertEquals(before,((com.hexvane.aetherhaven.hunter.TownHunts)hunts).revision());
        assertTrue(hunts.withdrawAll(Map.of("Ingredient_Bone_Fragment",3L)));assertEquals(9,hunts.getAmount("Ingredient_Bone_Fragment"));
    }
    private static TownWarehouse.ProductionStock production(String item,int amount,int x) {
        var state=new com.hexvane.aetherhaven.production.PlotProductionState();state.addAmount(item,amount,1000);
        return new TownWarehouse.ProductionStock(UUID.randomUUID(),new WarehouseBatch.Point(x,100,20),state);
    }
    private static WarehouseBatch reserve(TownWarehouse warehouse,int count,List<TownWarehouse.ProductionStock> sources) {
        return warehouse.reserve(UUID.randomUUID(),UUID.randomUUID(),"recipe",List.of(ingredient("ore",count)),List.of(stock("kit",1)),sources);
    }
    @Test void theWarehouseListsOnlyUnlockedOutputSlots() {
        assertEquals(1,WarehousePage.visibleSlots(1),"A new farm or mine shows its one slot and no locked placeholders");
        assertEquals(3,WarehousePage.visibleSlots(3));
        assertEquals(com.hexvane.aetherhaven.production.WorkplaceProductionUpgrades.MAX_SLOT_COUNT,WarehousePage.visibleSlots(99));
        assertEquals(0,WarehousePage.visibleSlots(-1));
    }
    @Test void aMaterialIsFetchedWhollyFromOnePlaceThatHoldsIt() {
        // The barn has a little hide and the warehouse has plenty: one trip to the warehouse, none to the barn.
        var town=new TownWarehouse();var barn=production("hide",2,10);supply(town,stock("hide",20));
        var batch=town.reserve(UUID.randomUUID(),UUID.randomUUID(),"recipe",List.of(ingredient("hide",8)),List.of(stock("kit",1)),List.of(barn));
        assertNotNull(batch);assertEquals(1,batch.pendingSources().size());
        assertEquals(TownWarehouse.Page.GENERAL,batch.pendingSources().getFirst().page());
        assertEquals(0,town.reservedProduction(barn.plot(),"hide"),"The barn is left alone");
        assertEquals(8,town.pickup(batch.id(),"courier",5));assertEquals(2,barn.state().getAmount("hide"));

        // The barn has enough: it is the place that makes hide, so the courier goes there and nowhere else.
        town=new TownWarehouse();var full=production("hide",10,10);supply(town,stock("hide",20));
        batch=town.reserve(UUID.randomUUID(),UUID.randomUUID(),"recipe",List.of(ingredient("hide",8)),List.of(stock("kit",1)),List.of(full));
        assertNotNull(batch);assertEquals(1,batch.pendingSources().size());
        assertEquals(full.plot(),batch.pendingSources().getFirst().plot());assertEquals(8,town.reservedProduction(full.plot(),"hide"));
        assertEquals(20,town.count(TownWarehouse.Page.GENERAL,"hide"));

        // Two materials each go to the one place that has them, never to a building that has neither.
        town=new TownWarehouse();var ranch=production("hide",9,10);var mine=production("ore",9,40);supply(town,stock("hide",1),stock("ore",1));
        batch=town.reserve(UUID.randomUUID(),UUID.randomUUID(),"recipe",List.of(ingredient("hide",4),ingredient("ore",4)),List.of(stock("kit",1)),List.of(ranch,mine));
        assertNotNull(batch);assertEquals(2,batch.pendingSources().size());
        assertEquals(4,town.reservedProduction(ranch.plot(),"hide"));assertEquals(4,town.reservedProduction(mine.plot(),"ore"));
        assertEquals(0,town.reservedProduction(ranch.plot(),"ore"));assertEquals(0,town.reservedProduction(mine.plot(),"hide"));

        // Nowhere holds enough alone: it is still gathered, from several places as before.
        town=new TownWarehouse();var small=production("hide",3,10);supply(town,stock("hide",6));
        batch=town.reserve(UUID.randomUUID(),UUID.randomUUID(),"recipe",List.of(ingredient("hide",8)),List.of(stock("kit",1)),List.of(small));
        assertNotNull(batch);assertEquals(2,batch.pendingSources().size());
    }
    @Test void aDeliveryWhoseSourceWasEmptiedIsNoticedAndPreparedAgainFromElsewhere() {
        var town=new TownWarehouse();var barn=production("hide",10,10);supply(town,stock("hide",20));
        var site=UUID.randomUUID();var order=UUID.randomUUID();
        var batch=town.reserve(site,order,"recipe",List.of(ingredient("hide",8)),List.of(stock("kit",1)),List.of(barn));
        assertEquals(barn.plot(),batch.pendingSources().getFirst().plot());
        assertFalse(town.sourceLost(batch.id(),List.of(barn),id->true));
        assertFalse(town.sourceLost(batch.id(),List.of(),id->true),"A barn in unloaded terrain is not judged");
        // The barn's stock goes while the courier is on the way.
        barn.state().removeAmountUpTo("hide",7);
        assertTrue(town.sourceLost(batch.id(),List.of(barn),id->true));
        assertEquals(3,barn.state().getAmount("hide"),"Checking moves nothing");assertEquals(20,town.count(TownWarehouse.Page.GENERAL,"hide"));
        assertEquals(1,town.queuedForOrder(order));
        assertTrue(town.cancel(batch.id()));assertEquals(0,town.queuedForOrder(order));assertEquals(0,town.reservedProduction(barn.plot(),"hide"));
        var again=town.reserve(site,order,"recipe",List.of(ingredient("hide",8)),List.of(stock("kit",1)),List.of(barn));
        assertNotNull(again);assertEquals(TownWarehouse.Page.GENERAL,again.pendingSources().getFirst().page());
        // A building that has been removed loses the delivery too, and so does a store that was moved.
        var gone=production("ore",9,40);var other=town.reserve(UUID.randomUUID(),UUID.randomUUID(),"recipe",List.of(ingredient("ore",4)),List.of(stock("kit",1)),List.of(gone));
        assertNotNull(other);assertFalse(town.sourceLost(other.id(),List.of(gone),id->true));
        assertTrue(town.sourceLost(other.id(),List.of(),id->false));
        var moved=new TownWarehouse.ProductionStock(gone.plot(),new WarehouseBatch.Point(99,100,20),gone.state());
        assertTrue(town.sourceLost(other.id(),List.of(moved),id->true));
        // Warehouse stock that is taken by hand is noticed as well.
        assertFalse(town.sourceLost(again.id(),List.of(),id->true));
    }
    @Test void directProductionPickupPreservesRealSourceAndDoesNotSweepOtherBuildings() {
        var town=new TownWarehouse();var mine=production("ore",6,10);var secondMine=production("ore",7,40);supply(town,stock("ore",8));
        var batch=reserve(town,16,List.of(mine,secondMine));assertNotNull(batch);
        assertEquals(6,mine.state().getAmount("ore"));assertEquals(7,secondMine.state().getAmount("ore"));
        assertEquals(6,town.reservedProduction(mine.plot(),"ore"));assertEquals(7,town.reservedProduction(secondMine.plot(),"ore"));
        // At the first mine only that mine's six items can leave storage.
        assertEquals(6,town.pickupAt(batch.id(),"courier",5,mine,mine.pickup()));
        assertEquals(0,mine.state().getAmount("ore"));assertEquals(7,secondMine.state().getAmount("ore"));assertEquals(8,town.count(TownWarehouse.Page.GENERAL,"ore"));
        assertTrue(town.deliver(batch.id(),"courier"));
        town=reload(town);batch=town.batch(batch.id());assertEquals(secondMine.pickup(),batch.pendingSources().getFirst().pickup());
        assertEquals(0,town.pickupAt(batch.id(),"courier",5,secondMine,mine.pickup()));
        assertEquals(7,town.pickupAt(batch.id(),"courier",5,secondMine,secondMine.pickup()));assertTrue(town.deliver(batch.id(),"courier"));
        assertEquals(3,town.pickup(batch.id(),"courier",5));assertTrue(town.deliver(batch.id(),"courier"));assertTrue(town.finish(batch.id()));
        assertEquals(5,town.count(TownWarehouse.Page.GENERAL,"ore"));assertEquals(1,town.count(TownWarehouse.Page.OUTPUT,"kit"));
    }
    @Test void ledgerPickupCannotRemoveProductionResources() {
        var town=new TownWarehouse();var mine=production("ore",8,10);var batch=reserve(town,8,List.of(mine));
        assertEquals(0,town.pickup(batch.id(),"courier",5));assertEquals(8,mine.state().getAmount("ore"));assertTrue(batch.cargoItems().isEmpty());
        assertNull(reserve(town,1,List.of(mine)));assertEquals(8,town.reservedProduction(mine.plot(),"ore"));
        assertTrue(town.cancel(batch.id()));assertEquals(0,town.reservedProduction(mine.plot(),"ore"));assertEquals(8,mine.state().getAmount("ore"));
    }
    @Test void productionCargoHonorsStackCapacityAndSurvivesCancellation() {
        var town=new TownWarehouse();var mine=production("ore",250,10);var batch=reserve(town,250,List.of(mine));
        assertEquals(100,town.pickupAt(batch.id(),"courier",1,mine,mine.pickup()));assertEquals(150,mine.state().getAmount("ore"));
        assertEquals(150,town.reservedProduction(mine.plot(),"ore"));town=reload(town);
        assertEquals(100,town.batch(batch.id()).cargoItems().getFirst().getQuantity());assertTrue(town.cancel(batch.id()));
        assertEquals(0,town.reservedProduction(mine.plot(),"ore"));assertTrue(town.returnCargo(batch.id(),"courier"));
        assertEquals(100,town.count(TownWarehouse.Page.GENERAL,"ore"));assertEquals(150,mine.state().getAmount("ore"));
    }
    @Test void lostProductionStockCannotPartiallyCommitAPickup() {
        var town=new TownWarehouse();var mine=production("ore",8,10);((com.hexvane.aetherhaven.production.PlotProductionState)mine.state()).addAmount("wood",8,1000);
        var batch=town.reserve(UUID.randomUUID(),UUID.randomUUID(),"recipe",List.of(ingredient("ore",8),ingredient("wood",8)),List.of(stock("kit",1)),List.of(mine));
        mine.state().removeAmountUpTo("wood",8);
        assertEquals(0,town.pickupAt(batch.id(),"courier",5,mine,mine.pickup()));
        assertEquals(8,mine.state().getAmount("ore"));assertTrue(batch.cargoItems().isEmpty());assertEquals(2,batch.pendingItems().size());
    }
    @Test void milkProductionNeedsBucketsAndIsNotFreeCourierStock() {
        var town=new TownWarehouse();String milk=com.hexvane.aetherhaven.production.ProductionWithdrawal.MILK_BUCKET_ITEM_ID;
        var ranch=production(milk,8,10);
        assertNull(town.reserve(UUID.randomUUID(),UUID.randomUUID(),"recipe",List.of(ingredient(milk,1)),List.of(stock("kit",1)),List.of(ranch)));
        assertEquals(8,ranch.state().getAmount(milk));
    }
    @Test void cancelBeforePickupReleasesAllReservationsWithoutMovingAnything() {
        var town=new TownWarehouse();supply(town,stock("ore",6));var batch=reserve(town,6);assertTrue(town.cancel(batch.id()));
        assertTrue(town.batches().isEmpty());assertEquals(6,town.count(TownWarehouse.Page.GENERAL,"ore"));
        assertEquals(0,town.reserved(TownWarehouse.Page.GENERAL,(short)0));assertNotNull(reserve(town,6));
    }
    @Test void allMatchingWithdrawalsSkipReservedAmountsAcrossSources() {
        var town=new TownWarehouse();supply(town,stock("ore",220));var batch=reserve(town,140);assertNotNull(batch);
        var player=bag(3);
        assertEquals(80,town.withdraw(TownWarehouse.Page.GENERAL,player,(short)0,town.stack(TownWarehouse.Page.GENERAL,(short)0),TownWarehouse.Amount.ALL_MATCHING,town.revision()).moved());
        assertEquals(140,town.count(TownWarehouse.Page.GENERAL,"ore"));
        assertEquals(0,town.pickup(batch.id(),"courier",0));assertEquals(0,town.pickup(batch.id(),"courier",WarehouseBatch.CARGO_SLOTS+1));
        assertEquals(0,town.pickup(batch.id(),"",1));assertEquals(140,town.pickup(batch.id(),"courier",2));
    }
    @Test void ordinaryRecipeDoesNotConsumeAnItemWithDifferentMetadata() {
        var town=new TownWarehouse();supply(town,new ItemStack("ore",6,BsonDocument.parse("{special:true}")));
        assertNull(reserve(town,6));assertEquals(6,town.count(TownWarehouse.Page.GENERAL,"ore"));
    }
    @Test void physicalDestinationIsPinnedAcrossCancellationAndReload() {
        var town=new TownWarehouse();supply(town,stock("ore",6));var batch=reserve(town,6);
        var origin=new WarehouseBatch.Point(12,90,-7);
        assertTrue(town.setDestination(batch.id(),origin));
        assertFalse(town.setDestination(batch.id(),new WarehouseBatch.Point(100,90,100)));
        town.pickup(batch.id(),"courier",1);town.deliver(batch.id(),"courier");town.cancel(batch.id());
        assertEquals(origin,reload(town).batch(batch.id()).destination());
    }
    @Test void mixedDeliveryOperationsConserveItemsAcrossSaveReloadAndCancellation() {
        var town=new TownWarehouse();supply(town,stock("ore",1000));
        var random=new Random(42861);var amounts=new HashMap<UUID,Integer>();
        int consumed=0,withdrawn=0,crafted=0;
        for(int step=0;step<600;step++) {
            var jobs=List.copyOf(town.batches());
            var job=jobs.isEmpty()?null:jobs.get(random.nextInt(jobs.size()));
            String courier="courier"+random.nextInt(3);
            switch(random.nextInt(9)) {
                case 0 -> { int quantity=1+random.nextInt(120);var batch=reserve(town,quantity);if(batch!=null)amounts.put(batch.id(),quantity); }
                case 1 -> { if(job!=null)town.pickup(job.id(),courier,1+random.nextInt(5)); }
                case 2 -> { if(job!=null)town.deliver(job.id(),job.courierId()); }
                case 3 -> { if(job!=null)town.cancel(job.id()); }
                case 4 -> { if(job!=null)town.returnCargo(job.id(),job.courierId()); }
                case 5 -> { if(job!=null)town.collectReturn(job.id(),courier,1+random.nextInt(5)); }
                case 6 -> { if(job!=null&&town.finish(job.id())) { consumed+=amounts.get(job.id());crafted++; } }
                case 7 -> town=reload(town);
                case 8 -> {
                    for(short slot=0;slot<town.slots(TownWarehouse.Page.GENERAL);slot++) {
                        var stack=town.stack(TownWarehouse.Page.GENERAL,slot);
                        if(!ItemStack.isEmpty(stack)&&"ore".equals(stack.getItemId())) {
                            withdrawn+=town.withdraw(TownWarehouse.Page.GENERAL,bag(1),slot,stack,TownWarehouse.Amount.ONE,town.revision()).moved();break;
                        }
                    }
                }
            }
            long inMotion=0;
            for(var batch:town.batches()) {
                inMotion+=batch.cargoItems().stream().mapToInt(ItemStack::getQuantity).sum();
                inMotion+=batch.deliveredItems().stream().mapToInt(ItemStack::getQuantity).sum();
            }
            assertEquals(1000,town.count(TownWarehouse.Page.GENERAL,"ore")+inMotion+consumed+withdrawn,"Step "+step);
            assertEquals(crafted,town.count(TownWarehouse.Page.OUTPUT,"kit"),"Step "+step);
            for(short slot=0;slot<town.slots(TownWarehouse.Page.GENERAL);slot++) {
                var stack=town.stack(TownWarehouse.Page.GENERAL,slot);
                assertTrue(town.reserved(TownWarehouse.Page.GENERAL,slot)<=(ItemStack.isEmpty(stack)?0:stack.getQuantity()));
            }
        }
        assertTrue(crafted>0,"Exercise real completions as well as cancellation");
    }

    @Test void courierReservesAndCollectsStandaloneMineshaftOutput(){
        var t=new com.hexvane.aetherhaven.town.TownRecord(UUID.randomUUID(),UUID.randomUUID(),"mine",16,100,16,1,1,0);
        t.getResidentNpcRecords().add(new com.hexvane.aetherhaven.town.ResidentNpcRecord("Aetherhaven_Miner","miner",null,UUID.randomUUID()));
        var sites=t.getApprenticeWorkplaces();assertNull(sites.unlock(t,com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType.MINING));
        var area=new com.hexvane.aetherhaven.workplace.WorkplaceArea(8,100,8,14,14);assertNull(sites.save(t,null,com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType.MINING,area,"Mine"));
        var base=sites.sites().getFirst();var plan=base.excavation();var at=new WarehouseBatch.Point(11,100,11);
        assertNull(plan.start(0,t.getOwnerUuid(),base.id(),area,com.hexvane.aetherhaven.workplace.ExcavationPlan.Entrance.NORTH,List.of(new com.hexvane.aetherhaven.workplace.ExcavationPlan.Cell(at,"Rock_Stone",0,1))));
        assertTrue(plan.prepare(Map.of("ore",6)));for(int n=0;n<12;n++)plan.attend(.5,true,1);assertTrue(plan.begin(plan.id(),6,at,()->true));assertTrue(plan.settle(plan.id(),true));assertTrue(plan.deposit(plan.id()));
        var inventory=com.hexvane.aetherhaven.workplace.MineshaftOutput.find(t,base.id());var source=new TownWarehouse.ProductionStock(base.id(),at,inventory);var warehouse=t.getWarehouse();
        var batch=warehouse.reserve(UUID.randomUUID(),UUID.randomUUID(),"mine-test",List.of(ingredient("ore",4)),List.of(stock("kit",1)),List.of(source));
        assertNotNull(batch);assertEquals(4,warehouse.reservedProduction(base.id(),"ore"));
        assertNull(warehouse.reserve(UUID.randomUUID(),UUID.randomUUID(),"overbook",List.of(ingredient("ore",3)),List.of(stock("kit",1)),List.of(source)));
        assertEquals(0,warehouse.pickupAt(batch.id(),"courier",1,source,new WarehouseBatch.Point(0,0,0)));
        assertEquals(4,warehouse.pickupAt(batch.id(),"courier",1,source,at));assertEquals(2,inventory.getAmount("ore"));
        assertEquals(0,warehouse.pickupAt(batch.id(),"courier",1,source,at));
    }
}
