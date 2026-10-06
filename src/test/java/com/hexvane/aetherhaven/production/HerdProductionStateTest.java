package com.hexvane.aetherhaven.production;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch;
import com.hexvane.aetherhaven.workplace.RanchCare;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class HerdProductionStateTest {
    private final HerdProductionState.Pair pair=new HerdProductionState.Pair("Cow",List.of(UUID.randomUUID(),UUID.randomUUID()),new WarehouseBatch.Point(8,100,8));
    private final RanchProducts.Product milk=new RanchProducts.Product("Cow",ProductionWithdrawal.MILK_BUCKET_ITEM_ID,1,false,"Container_Bucket");
    private PlotProductionState adopted(){var p=new PlotProductionState();assertNull(p.herd().adopt(0,List.of(pair)));return p;}
    private UUID ready(HerdProductionState state) {
        var care=state.herds().get("Cow").care();var id=UUID.randomUUID();care.born(id,"Cow");care.matured(id);care.advance(RanchCare.GROW_MINUTES);return id;
    }
    @Test void adoptionIsExplicitAndPreservesOldStockWithoutDuplicateBreeders() {
        var p=new PlotProductionState();p.addAmount("Ingredient_Hide_Heavy",12,100);var herd=p.herd();
        assertFalse(p.usesPhysicalHerd());assertNotNull(herd.adopt(0,List.of()));
        assertNotNull(herd.adopt(0,List.of(pair,pair)));assertNotNull(herd.adopt(1,List.of(pair)));
        var bad=new HerdProductionState.Pair("Sheep",pair.breeders(),pair.trough());assertNotNull(herd.adopt(0,List.of(pair,bad)));
        assertNull(herd.adopt(0,List.of(pair)));assertTrue(p.usesPhysicalHerd());assertNotNull(herd.adopt(0,List.of(pair)));
        assertEquals(12,p.getAmount("Ingredient_Hide_Heavy"));assertTrue(PhysicalProductionOutputPolicy.available(p,"Ingredient_Hide_Heavy"));
        assertFalse(PhysicalProductionOutputPolicy.available(p,"Ingredient_Hide_Storm"));
        assertTrue(PhysicalProductionOutputPolicy.available(p,milk.output()));
    }
    @Test void FeedTransactionsAndCareRequireSuppliesAndSuccessfulAttendance() {
        var h=adopted().herd();assertFalse(h.supply(()->false));assertEquals(0,h.feed());
        assertFalse(h.fed("Cow",()->fail("No supply must not act")));
        assertTrue(h.supply(()->true));assertFalse(h.fed("Cow",()->false));assertEquals(1,h.feed());
        assertTrue(h.fed("Cow",()->true));assertEquals(0,h.feed());assertEquals(0,h.herds().get("Cow").sinceFeed());
        assertTrue(h.supply(()->true));assertFalse(h.fed("Cow",()->fail("A second immediate visit cannot spend feed")));
        assertFalse(h.takeFeed(()->false));assertEquals(1,h.feed());assertTrue(h.takeFeed(()->true));
        for(int i=0;i<HerdProductionState.FEED_CAPACITY;i++)assertTrue(h.supply(()->true));
        assertFalse(h.supply(()->fail("Full bins cannot remove player items")));
    }
    @Test void UnsimulatedTimeAndNeglectCannotContinueGrowth() {
        var h=adopted().herd();h.supply(()->true);h.fed("Cow",()->true);var care=h.herds().get("Cow").care();care.born(UUID.randomUUID(),"Cow");
        h.advance("Cow",720,false);assertEquals(0,care.ageMinutes());assertEquals(0,h.herds().get("Cow").sinceFeed());
        h.advance("Cow",360,true);assertEquals(360,care.ageMinutes());assertTrue(h.herds().get("Cow").hungry());
        h.advance("Cow",99999,true);assertEquals(1440,care.ageMinutes());assertEquals(1440,h.herds().get("Cow").sinceFeed());
        care.matured(care.offspring());h.advance("Cow",1440,true);assertEquals(0,care.ageMinutes());
        h.supply(()->true);assertTrue(h.fed("Cow",()->true));h.advance("Cow",60,true);assertEquals(60,care.ageMinutes());
    }
    @Test void BreedingStockAndFailedOrRepeatedCollectionsNeverPay() {
        var h=adopted().herd();var id=ready(h);
        assertFalse(h.collect("Cow",pair.breeders().getFirst(),milk,()->fail("Breeder selected")));
        assertFalse(h.collect("Cow",id,milk,()->false));assertNull(h.cargo());
        assertTrue(h.collect("Cow",id,milk,()->true));assertEquals(id,h.herds().get("Cow").care().offspring());
        assertEquals(0,h.herds().get("Cow").care().ageMinutes());
        assertFalse(h.collect("Cow",id,milk,()->fail("Cannot collect twice")));
    }
    @Test void SavedCargoAndPartialDeliveriesRemainBoundedAndCannotDuplicate() {
        var p=adopted();var h=p.herd();var id=ready(h);
        var product=new RanchProducts.Product("Cow","Food_Beef_Raw",2,true,"Tool_Hatchet_Crude");
        assertTrue(h.collect("Cow",id,product,()->{h.herds().get("Cow").care().harvested();return true;}));assertEquals(0,p.getAmount(product.output()));
        var copy=new Gson().fromJson(new Gson().toJson(p),PlotProductionState.class);var cargo=copy.herd().cargo();
        assertEquals(0,copy.herd().deliver(copy,UUID.randomUUID(),10));assertEquals(0,copy.herd().deliver(copy,cargo.id(),0));
        assertEquals(1,copy.herd().deliver(copy,cargo.id(),1));assertEquals(1,copy.herd().cargo().quantity());
        assertEquals(0,copy.herd().deliver(copy,cargo.id(),1));assertEquals(1,copy.herd().deliver(copy,cargo.id(),2));
        assertNull(copy.herd().cargo());assertEquals(0,copy.herd().deliver(copy,cargo.id(),20));assertEquals(2,copy.getAmount(product.output()));
    }
    @Test void RereviewAndStaleLossCannotOrphanAnExistingOffspring() {
        var h=adopted().herd();var id=ready(h);
        var sheep=new HerdProductionState.Pair("Sheep",List.of(UUID.randomUUID(),UUID.randomUUID()),pair.trough());
        assertNull(h.adopt(1,List.of(sheep)));assertEquals(id,h.herds().get("Cow").care().offspring());
        assertFalse(h.lost(UUID.randomUUID()));assertEquals(id,h.herds().get("Cow").care().offspring());
        assertTrue(h.lost(id));assertNull(h.herds().get("Cow").care().offspring());
        assertNull(h.adopt(2,List.of(sheep)));assertFalse(h.herds().containsKey("Cow"));
    }

    @Test void specialtyProductsRequireTheirRegisteredAnimalFamilyAndKeepLivingAnimals() {
        assertEquals("Mosshorn",RanchProducts.family("Tamed_Mosshorn"));
        assertEquals("Skrill",RanchProducts.family("Skrill"));
        assertNull(RanchProducts.family("Aetherhaven_Ranch_Skrill_Young"));
        var heavy=RanchProducts.product("Mosshorn","Ingredient_Hide_Heavy");
        assertTrue(heavy.slaughter());assertEquals(1,heavy.amount());
        assertNull(RanchProducts.product("Cow","Ingredient_Hide_Heavy"));
        var h=adopted().herd();var id=ready(h);
        assertFalse(h.collect("Cow",id,heavy,()->fail("Wrong family cannot act")));
        var manure=RanchProducts.product("Cow","Ingredient_Poop");
        assertFalse(manure.slaughter());assertTrue(h.collect("Cow",id,manure,()->true));
        assertEquals(id,h.herds().get("Cow").care().offspring());
        assertEquals(0,h.herds().get("Cow").care().ageMinutes());
        assertFalse(h.collect("Cow",id,manure,()->fail("Collection is not repeatable without tended growth")));
        assertEquals("Ingredient_Poop",h.cargo().output());
    }

    @Test void allNineNativeFamiliesCanBeAdoptedWithoutReusingBreeders() {
        var h=new HerdProductionState();
        var pairs=List.of("Cow","Sheep","Goat","Pig","Chicken","Mosshorn","Skrill","Tetrabird","Bluebird")
            .stream().map(f->new HerdProductionState.Pair(f,List.of(UUID.randomUUID(),UUID.randomUUID()),pair.trough())).toList();
        assertNull(h.adopt(0,pairs));assertEquals(9,h.herds().size());
        var saved=new Gson().fromJson(new Gson().toJson(h),HerdProductionState.class);
        assertEquals(h.herds().keySet(),saved.herds().keySet());
        var duplicate=new ArrayList<>(pairs);
        duplicate.set(8,new HerdProductionState.Pair("Bluebird",pairs.getFirst().breeders(),pair.trough()));
        assertNotNull(saved.adopt(saved.revision(),duplicate));assertEquals(9,saved.herds().size());
        var tooMany=new ArrayList<HerdProductionState.Pair>();
        for(int n=0;n<=HerdProductionState.MAX_FAMILIES;n++)tooMany.add(new HerdProductionState.Pair("Mod_"+n,List.of(UUID.randomUUID(),UUID.randomUUID()),pair.trough()));
        assertNotNull(saved.adopt(saved.revision(),tooMany));assertEquals(9,saved.herds().size());
    }

    @Test void featherCollectionPreservesRaisedBirdsAndCannotRepeatWithoutCare() {
        for(var row:Map.of("Tetrabird","Ingredient_Feathers_Dark","Bluebird","Ingredient_Feathers_Blue").entrySet()) {
            String family=row.getKey();var p=new PlotProductionState();var h=p.herd();
            assertEquals(family,RanchProducts.family(family));
            assertNull(RanchProducts.family("Aetherhaven_Ranch_"+family+"_Young"));
            assertNull(h.adopt(0,List.of(new HerdProductionState.Pair(family,List.of(UUID.randomUUID(),UUID.randomUUID()),pair.trough()))));
            var care=h.herds().get(family).care();var animal=UUID.randomUUID();
            care.born(animal,family);care.matured(animal);care.advance(RanchCare.GROW_MINUTES);
            var feathers=RanchProducts.product(family,row.getValue());assertFalse(feathers.slaughter());
            assertFalse(h.collect(family,animal,feathers,()->false));assertNull(h.cargo());
            assertTrue(h.collect(family,animal,feathers,()->true));assertEquals(animal,care.offspring());
            assertFalse(h.collect(family,animal,feathers,()->fail("Repeated collection must not act")));
            assertEquals(0,p.getAmount(feathers.output()));
            assertEquals(feathers.amount(),h.deliver(p,h.cargo().id(),25));assertEquals(animal,care.offspring());
            assertEquals(0,care.ageMinutes());
        }
    }

    @Test void expandedFamiliesRespectSelectedOutputSlotsInsteadOfProducingEveryNativeDrop() {
        var state=new PlotProductionState();
        var entry=ProductionCatalog.Entry.create(List.of("Ingredient_Hide_Heavy","Ingredient_Feathers_Red","Ingredient_Poop"),
            new int[]{1,1,1},new long[]{25,25,25});
        assertEquals(List.of(RanchProducts.product("Mosshorn","Ingredient_Hide_Heavy")),RanchProducts.selected("Mosshorn",state,entry));
        assertTrue(RanchProducts.selected("Skrill",state,entry).isEmpty());
        state.setSlotCursor(0,1);
        var feathers=RanchProducts.product("Skrill","Ingredient_Feathers_Red");
        assertEquals(List.of(feathers),RanchProducts.selected("Skrill",state,entry));
        assertFalse(feathers.slaughter());assertEquals(2,feathers.amount());
        assertTrue(RanchProducts.selected("Mosshorn",state,entry).isEmpty());
        state.setSlotCursor(0,2);
        for(String family:RanchProducts.families())assertEquals("Ingredient_Poop",RanchProducts.selected(family,state,entry).getFirst().output());
    }

    @Test void shearingRecoveryPersistsAndCannotFinishWithoutTendedGrowth() {
        var p=new PlotProductionState();var h=p.herd();
        var pair=new HerdProductionState.Pair("Sheep",List.of(UUID.randomUUID(),UUID.randomUUID()),this.pair.trough());
        assertNull(h.adopt(0,List.of(pair)));var herd=h.herds().get("Sheep");var id=UUID.randomUUID();
        herd.care().born(id,"Sheep");herd.care().matured(id);herd.care().advance(RanchCare.GROW_MINUTES);
        var wool=RanchProducts.product("Sheep","Ingredient_Fabric_Scrap_Wool");
        assertFalse(h.collect("Sheep",id,wool,()->false));assertNull(herd.recoveringOutput());
        assertTrue(h.collect("Sheep",id,wool,()->true));assertEquals(wool.output(),herd.recoveringOutput());
        assertFalse(herd.recovered(id));assertEquals(2,h.deliver(p,h.cargo().id(),10));
        h=new Gson().fromJson(new Gson().toJson(h),HerdProductionState.class);herd=h.herds().get("Sheep");
        assertEquals(wool.output(),herd.recoveringOutput());h.advance("Sheep",RanchCare.GROW_MINUTES,false);
        assertEquals(0,herd.care().ageMinutes());assertFalse(herd.recovered(id));
        for(int n=0;n<4;n++){assertTrue(h.supply(()->true));assertTrue(h.fed("Sheep",()->true));h.advance("Sheep",HerdProductionState.FEED_INTERVAL,true);}
        assertFalse(herd.recovered(UUID.randomUUID()));assertTrue(herd.recovered(id));assertNull(herd.recoveringOutput());
        assertFalse(herd.recovered(id));assertNull(h.cargo());
        assertTrue(h.collect("Sheep",id,wool,()->true));assertFalse(h.lost(UUID.randomUUID()));assertNotNull(herd.recoveringOutput());
        assertTrue(h.lost(id));assertNull(herd.recoveringOutput());
    }
    @Test void livingProductAppearancePreservesUnrelatedAttachmentsAndAllowsExplicitIntegrations() {
        var wool=RanchProductAppearance.get("Sheep","Ingredient_Fabric_Scrap_Wool");assertNotNull(wool);
        var original=Map.of("Fleece","Wool","Tag","Blue");
        var shorn=RanchProductAppearance.attachments(original,wool,false);
        assertEquals(Map.of("Tag","Blue"),shorn);assertEquals("Wool",original.get("Fleece"));
        assertEquals(original,RanchProductAppearance.attachments(shorn,wool,true));
        assertNull(RanchProductAppearance.get("Cow","Ingredient_Fabric_Scrap_Wool"));
        assertNull(RanchProductAppearance.get("Sheep","Ingredient_Poop"));
        var custom=new RanchProductAppearance.Appearance("Test_Animal","Coat","Grown","Short",null,null);
        RanchProductAppearance.register("Test_Animal","Test_Fiber",custom);RanchProductAppearance.register("Test_Animal","Test_Fiber",custom);
        assertEquals(Map.of("Coat","Short"),RanchProductAppearance.attachments(null,custom,false));
        assertThrows(IllegalArgumentException.class,()->RanchProductAppearance.register("Test_Animal","Test_Fiber",wool));
    }
}
