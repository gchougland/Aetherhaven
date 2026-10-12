package com.hexvane.aetherhaven.production;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.workplace.*;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class DruidProductionTest {
    private final Gson gson=new Gson();
    @Test void conservatoryCatalogStoresEveryAlchemicalPlantAndItsPetals() {
        var catalog=ProductionCatalog.loadFromClasspath(getClass().getClassLoader());
        var entry=catalog.get(SpiritGarden.CONSTRUCTION);
        assertNotNull(entry);assertEquals(12,entry.catalogSize());
        assertTrue(ProductionCatalog.isProductionWorkplaceConstruction(SpiritGarden.CONSTRUCTION));
        for(String family:List.of("Health","Stamina","Mana"))for(int tier=1;tier<=3;tier++) {
            var crop=new AlchemicalCrop(family,tier);
            assertTrue(entry.containsItemId(crop.item()));
            assertEquals(25,entry.maxStorageForItem(crop.item()));
            assertFalse(NativeFarmCrops.supportedOutput(crop.item()),"The Farmer's beds never grow the Druid's plants");
        }
        for(String petal:List.of("Plant_Petals_Blood","Plant_Petals_Storm","Plant_Petals_Azure"))assertEquals(50,entry.maxStorageForItem(petal));
        assertTrue(NativeFarmCrops.supportedOutput("Plant_Crop_Wheat_Item"));
    }

    @Test void conservatoryNeverAccruesTimedOrOfflineIngredients() {
        var entry=ProductionCatalog.loadFromClasspath(getClass().getClassLoader()).get(SpiritGarden.CONSTRUCTION);
        var state=new PlotProductionState();
        state.setSlotTickAccum(0,entry.ticksAtCursor(0)-1);
        var before=gson.toJson(state);
        var town=new TownRecord(UUID.randomUUID(),UUID.randomUUID(),"test",0,100,0,1,1,0);
        assertFalse(ProductionAccrualEngine.applyEntityTicks(state,entry,town,SpiritGarden.CONSTRUCTION,null,null,null,0,0,Integer.MAX_VALUE));
        assertEquals(before,gson.toJson(state));
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
        old.orders().add("cultivate_health1",WorkplaceOrders.Mode.FOREVER,1);
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
        // The Druid supervises the Mycologist and the Berry Patch now. The retired garden never comes back as a node.
        assertTrue(BuildingUpgradeTree.workplaces("druid").stream().noneMatch(node->node.id().equals("work:"+type.id)));
        assertEquals(2,BuildingUpgradeTree.workplaces("druid").stream().filter(node->node.kind()==BuildingUpgradeTree.Kind.WORKPLACE).count());
        assertTrue(WorkplaceUpgradesPage.supports("druid"),"Druid production upgrades remain available");
    }
}
