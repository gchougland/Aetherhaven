package com.hexvane.aetherhaven.production;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class FieldProductionStateTest {
    private final FieldProductionState.Bed bed=new FieldProductionState.Bed(new WarehouseBatch.Point(1,100,2),"Plant_Crop_Wheat_Block","Plant_Crop_Wheat_Item");
    @Test void adoptionIsExplicitRevisionCheckedAndCannotResetStockOrUnlocks() {
        var p=new PlotProductionState();var f=p.field();assertFalse(p.usesPhysicalField());
        p.addAmount("Ingredient_Life_Essence",20,100);p.addWorkplaceOutputUnlock("Plant_Crop_Potato_Item");
        assertNotNull(f.adopt(0,List.of()));assertFalse(f.adopted());
        assertNotNull(f.adopt(0,List.of(bed,bed)));assertFalse(f.adopted());
        assertNotNull(f.adopt(1,List.of(bed)));assertFalse(f.adopted());
        assertNull(f.adopt(0,List.of(bed)));assertTrue(p.usesPhysicalField());
        assertNotNull(f.adopt(0,List.of(bed)));assertEquals(1,f.revision());
        assertEquals(20,p.getAmount("Ingredient_Life_Essence"));assertTrue(p.isWorkplaceOutputUnlocked("Plant_Crop_Potato_Item"));
        assertTrue(PhysicalProductionOutputPolicy.available(p,bed.output()));
        assertTrue(PhysicalProductionOutputPolicy.available(p,"Ingredient_Life_Essence"));
    }
    @Test void failedOrRepeatedHarvestCannotCreateCargoOrEarlyStock() {
        var p=new PlotProductionState();var f=p.field();
        assertFalse(f.harvest(bed,1,()->fail("Unadopted fields cannot harvest")));
        assertNull(f.adopt(0,List.of(bed)));
        assertFalse(f.harvest(bed,1,()->false));assertNull(f.cargo());
        assertFalse(f.harvest(bed,0,()->fail("No work for an invalid yield")));
        assertTrue(f.harvest(bed,1,()->true));assertEquals(0,p.getAmount(bed.output()));
        assertFalse(f.harvest(bed,1,()->fail("Held harvest must first be delivered")));
        assertEquals(1,f.cargo().quantity());
    }
    @Test void reloadRetainsHeldProduceAndPartialDeliveryCannotLoseOrDuplicateIt() {
        var p=new PlotProductionState();assertNull(p.field().adopt(0,List.of(bed)));
        assertTrue(p.field().harvest(bed,4,()->true));var id=p.field().cargo().id();
        var copy=new Gson().fromJson(new Gson().toJson(p),PlotProductionState.class);
        assertEquals(0,copy.field().deliver(copy,UUID.randomUUID(),10));
        assertEquals(0,copy.field().deliver(copy,id,0));assertEquals(4,copy.field().cargo().quantity());
        assertEquals(2,copy.field().deliver(copy,id,2));assertEquals(2,copy.getAmount(bed.output()));
        assertEquals(2,copy.field().cargo().quantity());assertEquals(0,copy.field().deliver(copy,id,2));
        assertEquals(2,copy.field().deliver(copy,id,4));assertNull(copy.field().cargo());
        assertEquals(0,copy.field().deliver(copy,id,10));assertEquals(4,copy.getAmount(bed.output()));
    }
    @Test void reviewingNewBedsCannotDiscardAHarvestOrReenableLegacyTimers() {
        var p=new PlotProductionState();var f=p.field();assertNull(f.adopt(0,List.of(bed)));
        assertTrue(f.harvest(bed,1,()->true));var cargo=f.cargo();
        var other=new FieldProductionState.Bed(new WarehouseBatch.Point(5,100,2),bed.crop(),bed.output());
        assertNull(f.adopt(1,List.of(other)));assertEquals(cargo,f.cargo());assertTrue(f.adopted());
        assertNotNull(f.adopt(2,List.of()));assertEquals(List.of(other),f.beds());
        assertThrows(UnsupportedOperationException.class,()->f.beds().clear());
    }
    @Test void rotationAndExplicitCropIntegrationsSurviveTheirBoundaries() {
        var f=new FieldProductionState();var second=new FieldProductionState.Bed(new WarehouseBatch.Point(3,100,2),bed.crop(),bed.output());
        assertNull(f.adopt(0,List.of(bed,second)));f.rotateAfter(0);
        var saved=new Gson().fromJson(new Gson().toJson(f),FieldProductionState.class);assertEquals(1,saved.firstBed());
        saved.rotateAfter(1);assertEquals(0,saved.firstBed());
        assertTrue(NativeFarmCrops.supportedOutput("Plant_Crop_Cotton_Item"));
        assertFalse(NativeFarmCrops.supportedOutput("Plant_Crop_Health1_Item"));
        var custom=new NativeFarmCrops.Crop("Test_Crop","Test_Produce");NativeFarmCrops.register(custom);NativeFarmCrops.register(custom);
        assertThrows(IllegalArgumentException.class,()->NativeFarmCrops.register(new NativeFarmCrops.Crop("Test_Crop","Wrong_Output")));
        assertTrue(NativeFarmCrops.supportedOutput("Test_Produce"));
    }
    @Test void emptySpaceAndMissingAssetsDoNotBecomeCropBeds() {
        assertNull(NativeFarmCrops.crop(null));
        assertNull(NativeFarmCrops.crop(com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType.EMPTY));
    }
    @Test void deferredOrFailedNativeTransitionsCannotPayForAnUnchangedPlant() {
        var f=new FieldProductionState();assertNull(f.adopt(0,List.of(bed)));
        assertFalse(f.beginHarvest(bed,"Regrowing",()->false));assertNull(f.pending());assertNull(f.cargo());
        assertTrue(f.beginHarvest(bed,"Regrowing",()->true));var claim=f.pending().id();
        assertNull(f.cargo());assertFalse(f.beginHarvest(bed,"Regrowing",()->fail("Pending harvest cannot restart")));
        assertFalse(f.settleHarvest(claim,null));assertNotNull(f.pending());
        assertFalse(f.settleHarvest(UUID.randomUUID(),"Regrowing"));assertNotNull(f.pending());
        var copy=new Gson().fromJson(new Gson().toJson(f),FieldProductionState.class);
        assertTrue(copy.settleHarvest(claim,"StillRipe"));assertNull(copy.pending());assertNull(copy.cargo());
        assertTrue(f.settleHarvest(claim,"Regrowing"));assertEquals(1,f.cargo().quantity());
        assertFalse(f.settleHarvest(claim,"Regrowing"));assertEquals(1,f.cargo().quantity());
    }
    @Test void everyHarvestBringsLifeEssenceWhileThereIsRoomForIt() {
        var crop=new LinkedHashMap<String,Integer>();crop.put(bed.output(),3);
        // Essence is not one of the chosen outputs: the harvest still brings what the plant dropped.
        var withDrop=NativeFarmCrops.withEssence(crop,5,100);
        assertEquals(3,withDrop.get(bed.output()));assertEquals(5,withDrop.get(NativeFarmCrops.LIFE_ESSENCE));
        // A crop from another pack that drops none still gives one.
        assertEquals(1,NativeFarmCrops.withEssence(crop,0,100).get(NativeFarmCrops.LIFE_ESSENCE));
        // Never more than the farm can still hold, so the farmer is not left carrying it.
        assertEquals(2,NativeFarmCrops.withEssence(crop,5,2).get(NativeFarmCrops.LIFE_ESSENCE));
        assertFalse(NativeFarmCrops.withEssence(crop,5,0).containsKey(NativeFarmCrops.LIFE_ESSENCE),"A full store still lets the crop be harvested");
        // When essence is a chosen output its own amount stands.
        var both=new LinkedHashMap<String,Integer>(crop);both.put(NativeFarmCrops.LIFE_ESSENCE,4);
        assertEquals(4,NativeFarmCrops.withEssence(both,4,100).get(NativeFarmCrops.LIFE_ESSENCE));
        // A harvest that gives only essence is still a harvest.
        assertEquals(Map.of(NativeFarmCrops.LIFE_ESSENCE,1),NativeFarmCrops.withEssence(Map.of(),0,10));
        assertEquals(1,crop.size(),"The rolled yields are not changed in place");
    }
    @Test void multipleNativeProductsPersistAndEachDeliveryHasItsOwnClaim() {
        var p=new PlotProductionState();var f=p.field();assertNull(f.adopt(0,List.of(bed)));
        var yields=new LinkedHashMap<String,Integer>();yields.put(bed.output(),2);yields.put(NativeFarmCrops.LIFE_ESSENCE,4);
        assertTrue(f.beginHarvest(bed,"Regrowing",yields,()->true));yields.put(bed.output(),100);
        var saved=new Gson().fromJson(new Gson().toJson(p),PlotProductionState.class);
        assertTrue(saved.field().settleHarvest(saved.field().pending().id(),"Regrowing"));
        var held=saved.field();assertEquals(6,held.heldQuantity());var first=held.cargo().id();
        assertEquals(1,held.deliver(saved,first,1));assertEquals(5,held.heldQuantity());
        assertEquals(0,held.deliver(saved,first,1));assertEquals(1,held.deliver(saved,first,2));
        assertEquals(NativeFarmCrops.LIFE_ESSENCE,held.cargo().output());assertNotEquals(first,held.cargo().id());
        assertEquals(0,held.deliver(saved,first,100));
        saved=new Gson().fromJson(new Gson().toJson(saved),PlotProductionState.class);held=saved.field();
        assertEquals(4,held.heldQuantity());var second=held.cargo().id();
        assertFalse(held.harvest(bed,1,()->fail("Remaining products must be delivered first")));
        assertEquals(0,held.deliver(saved,second,0));assertEquals(4,held.deliver(saved,second,4));
        assertEquals(0,held.heldQuantity());assertNull(held.cargo());assertEquals(2,saved.getAmount(bed.output()));
        assertEquals(4,saved.getAmount(NativeFarmCrops.LIFE_ESSENCE));
    }
    @Test void oldPendingClaimsRemainRecoverableAndFailedMixedClaimsPayNothing() {
        var f=new FieldProductionState();assertNull(f.adopt(0,List.of(bed)));
        assertTrue(f.beginHarvest(bed,"Regrowing",()->true));
        var json=com.google.gson.JsonParser.parseString(new Gson().toJson(f)).getAsJsonObject();
        json.getAsJsonObject("pending").remove("yields");
        var old=new Gson().fromJson(json,FieldProductionState.class);
        assertTrue(old.settleHarvest(old.pending().id(),"Regrowing"));assertEquals(1,old.heldQuantity());
        var failed=new Gson().fromJson(new Gson().toJson(f),FieldProductionState.class);
        assertTrue(failed.settleHarvest(failed.pending().id(),"DifferentCrop"));assertEquals(0,failed.heldQuantity());
        assertFalse(failed.beginHarvest(bed,"Regrowing",Map.of(),()->fail("Empty yields must not regrow")));
        assertFalse(failed.beginHarvest(bed,"Regrowing",Map.of(bed.output(),-1),()->fail("Invalid yields must not regrow")));
    }
    @Test void essenceSelectionTendsBedsButFullSelectedStorageStopsWork() {
        var p=new PlotProductionState();
        var entry=ProductionCatalog.Entry.create(List.of(bed.output(),NativeFarmCrops.LIFE_ESSENCE),new int[]{1,1},new long[]{10,20});
        p.setSlotCursor(0,1);assertEquals(Set.of(NativeFarmCrops.LIFE_ESSENCE),NativeFarmCrops.selectedOutputs(p,entry,bed));
        assertTrue(NativeFarmCrops.canTend(p,entry,bed));p.addAmount(NativeFarmCrops.LIFE_ESSENCE,20,20);
        assertFalse(NativeFarmCrops.canTend(p,entry,bed));
        p.setSlotCursor(0,0);assertTrue(NativeFarmCrops.canTend(p,entry,bed));
        p.addAmount(bed.output(),10,10);assertFalse(NativeFarmCrops.canTend(p,entry,bed));
    }
}
