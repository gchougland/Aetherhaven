package com.hexvane.aetherhaven.production;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch;
import com.hexvane.aetherhaven.workplace.*;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class DruidProductionTest {
    private final Gson gson=new Gson();
    @Test void conservatoryCatalogContainsEveryNativeAlchemicalCropAndUsesProductionStorage() {
        var catalog=ProductionCatalog.loadFromClasspath(getClass().getClassLoader());
        var entry=catalog.get(NativeDruidCrops.CONSTRUCTION);
        assertNotNull(entry);assertEquals(9,entry.catalogSize());
        assertTrue(ProductionCatalog.isProductionWorkplaceConstruction(NativeDruidCrops.CONSTRUCTION));
        for(String family:List.of("Health","Stamina","Mana"))for(int tier=1;tier<=3;tier++) {
            var crop=new AlchemicalCrop(family,tier);
            assertTrue(entry.containsItemId(crop.item()));
            assertTrue(NativeFarmCrops.supportedOutput(crop.item()));
            assertEquals(25,entry.maxStorageForItem(crop.item()));
            assertTrue(NativeDruidCrops.supported(crop.plantedBlock()));
        }
        assertFalse(NativeDruidCrops.supported("Plant_Crop_Wheat_Item"));
        assertFalse(NativeDruidCrops.supported("Plant_Crop_Health1_Item"));
    }

    @Test void allNineBedTypesPreservePendingRegrowthAndDeliverExactlyOnceAcrossReload() {
        for(String family:List.of("Health","Stamina","Mana"))for(int tier=1;tier<=3;tier++) {
            var crop=new AlchemicalCrop(family,tier);
            var bed=new FieldProductionState.Bed(new WarehouseBatch.Point(1,100,1),crop.plantedBlock(),crop.item());
            var state=new PlotProductionState();
            assertNull(state.field().adopt(0,List.of(bed)));
            assertTrue(state.field().beginHarvest(bed,crop.plantedBlock(),Map.of(crop.item(),1),()->true));
            assertEquals(0,state.getAmount(crop.item()));
            assertFalse(state.field().beginHarvest(bed,crop.plantedBlock(),()->fail("Pending harvest cannot repeat")));
            var saved=gson.fromJson(gson.toJson(state),PlotProductionState.class);
            assertTrue(saved.field().settleHarvest(saved.field().pending().id(),crop.plantedBlock()));
            assertEquals(1,saved.field().heldQuantity());
            var cargo=saved.field().cargo();
            assertEquals(0,saved.field().deliver(saved,cargo.id(),0));
            assertEquals(1,saved.field().deliver(saved,cargo.id(),25));
            var delivered=gson.fromJson(gson.toJson(saved),PlotProductionState.class);
            assertEquals(0,delivered.field().deliver(delivered,cargo.id(),25));
            assertEquals(1,delivered.getAmount(crop.item()));
            assertTrue(PhysicalProductionOutputPolicy.available(delivered,crop.item()));
        }
    }

    @Test void conservatoryNeverAccruesTimedOrOfflineIngredients() {
        var entry=ProductionCatalog.loadFromClasspath(getClass().getClassLoader()).get(NativeDruidCrops.CONSTRUCTION);
        var state=new PlotProductionState();
        state.setSlotTickAccum(0,entry.ticksAtCursor(0)-1);
        var before=gson.toJson(state);
        var town=new TownRecord(UUID.randomUUID(),UUID.randomUUID(),"test",0,100,0,1,1,0);
        assertFalse(ProductionAccrualEngine.applyEntityTicks(state,entry,town,NativeDruidCrops.CONSTRUCTION,null,null,null,0,0,Integer.MAX_VALUE));
        assertEquals(before,gson.toJson(state));
    }

    @Test void lockedNativeHarvestRejectsBeforeWorldMutationAndLeavesSavedCargoUntouched() {
        var town=new TownRecord(UUID.randomUUID(),UUID.randomUUID(),"test",0,100,0,1,1,0);
        var plot=new PlotInstance(UUID.randomUUID(),NativeDruidCrops.CONSTRUCTION,PlotInstanceState.COMPLETE,
            new PlotFootprintRecord(0,99,0,4,104,4),0,100,0,0);
        var crop=new AlchemicalCrop("Health",3);
        var bed=new FieldProductionState.Bed(new WarehouseBatch.Point(1,100,1),crop.plantedBlock(),crop.item());
        var state=new PlotProductionState();
        assertNull(state.field().adopt(0,List.of(bed)));
        assertFalse(NativeFarmCrops.harvest(null,town,plot,state,bed));
        assertNull(state.field().pending());
        assertTrue(state.field().beginHarvest(bed,crop.plantedBlock(),Map.of(crop.item(),1),()->true));
        assertTrue(state.field().settleHarvest(state.field().pending().id(),crop.plantedBlock()));
        assertFalse(NativeFarmCrops.harvest(null,town,plot,state,bed));
        assertEquals(1,state.field().heldQuantity());
        assertEquals(1,state.field().deliver(state,state.field().cargo().id(),25));
        assertEquals(1,state.getAmount(crop.item()));
    }

    @Test void retiredCultivationIsRemovedWhenSavedTownLoads() {
        var town=new TownRecord(UUID.randomUUID(),UUID.randomUUID(),"test",0,100,0,1,1,0);
        var druid=new ResidentNpcRecord();druid.setKind("druid");town.getResidentNpcRecords().add(druid);
        var type=TownsfolkWorkType.CULTIVATION;
        assertFalse(type.availableForNewWork());
        assertTrue(type.requiresWorkplace(),"Saved cultivation recipes and batches still decode");
        assertNotNull(town.getApprenticeWorkplaces().unlock(town,type));
        assertNotNull(town.getApprenticeWorkplaces().save(town,null,type,new WorkplaceArea(0,100,0,4,4),"New garden"));
        var old=new ApprenticeWorkplace("Saved garden",type,new WorkplaceArea(0,100,0,4,4));
        old.orders().add(0,"cultivate_health1",WorkplaceOrders.Mode.FOREVER,1);
        var data=gson.toJsonTree(town.getApprenticeWorkplaces()).getAsJsonObject();
        data.getAsJsonArray("sites").add(gson.toJsonTree(old));
        data.getAsJsonArray("unlockedTypes").add(type.id);
        var restored=gson.fromJson(data,TownWorkplaces.class);
        var townData=gson.toJsonTree(town).getAsJsonObject();
        townData.add("apprenticeWorkplaces",gson.toJsonTree(restored));
        var loaded=gson.fromJson(townData,TownRecord.class);
        loaded.migrateInnFieldsIfNeeded();
        assertNull(loaded.getApprenticeWorkplaces().find(old.id()));
        assertFalse(loaded.getApprenticeWorkplaces().unlocked(type));
        assertFalse(loaded.getApprenticeWorkplaces().retireCultivation(loaded.getWarehouse()),"Migration is idempotent");
        assertTrue(BuildingUpgradeTree.workplaces("druid").stream().noneMatch(node->node.kind()==BuildingUpgradeTree.Kind.WORKPLACE));
        assertTrue(WorkplaceUpgradesPage.supports("druid"),"Druid production upgrades remain available");
    }
}
