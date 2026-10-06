package com.hexvane.aetherhaven.town;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.workplace.*;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class WorkplaceFoodTest {
    @Test void alchemicalPlantsNeverCountAsFoodIncludingGrowingStates() {
        for(String family:java.util.List.of("Health","Stamina","Mana"))for(int tier=1;tier<=3;tier++) {
            String crop="Plant_Crop_"+family+tier;
            assertFalse(FoodSiteService.isFoodCrop(crop,crop));
            assertFalse(FoodSiteService.isFoodCrop(crop+"_Block","*"+crop+"_Block_State_Definitions_Stage2"));
            assertEquals(new AlchemicalCrop(family,tier),AlchemicalCrop.fromBlock(crop));
            var matched = AlchemicalCrop.fromBlock(crop);
            for (String block : java.util.List.of(crop, crop+"_Block", crop+"_Block_State_Definitions_Stage2")) {
                assertSame(matched, AlchemicalCrop.fromBlock(block));
                assertSame(matched, AlchemicalCrop.fromBlock("*"+block));
            }
            assertNull(AlchemicalCrop.fromBlock(crop+"0_Block"));
            assertNull(AlchemicalCrop.fromBlock(crop+"_Blockade"));
            assertNull(AlchemicalCrop.fromBlock("**"+crop));
        }
        assertNull(AlchemicalCrop.fromBlock("Plant_Crop_Health10"));
        assertNull(AlchemicalCrop.fromBlock("Other_Mod_Health1"));
        assertNull(AlchemicalCrop.fromBlock(null));
    }
    @Test void foodCropRecognitionSupportsStatesAndOptInModsButRejectsCottonAndDeadPlants() {
        assertTrue(FoodSiteService.isFoodCrop("Plant_Crop_Wheat_Block","*Plant_Crop_Wheat_Block_Stage2"));
        assertFalse(FoodSiteService.isFoodCrop("Plant_Crop_Cotton_Block","Plant_Crop_Cotton_Block"));
        assertFalse(FoodSiteService.isFoodCrop("Plant_Crop_Wheat_Block","Plant_Crop_Wheat_Stage_4_Burnt"));
        assertFalse(FoodSiteService.isFoodCrop("Plant_Crop_Wheat_Item","Plant_Crop_Wheat_Item"));
        assertFalse(FoodSiteService.isFoodCrop("Test_Mod_Radish","Test_Mod_Radish"));
        FoodSiteService.registerCropBlock("Test_Mod_Radish");
        assertTrue(FoodSiteService.isFoodCrop("Test_Mod_Radish","Test_Mod_Radish_Grown"));
    }
    private static void tend(WorkplaceFood food) { for(int i=0;i<48;i++)if(food.work(.25,true,true))food.completeCare(()->true); }
    @Test void equipmentSpeedsAttendedCareWithoutBypassingPresenceOrGrantingOfflineWork() {
        var equipped=new WorkplaceFood();var ordinary=new WorkplaceFood();
        for(int i=0;i<42;i++) {
            if(equipped.work(.25,true,true,1.15))equipped.completeCare(()->true);
            if(ordinary.work(.25,true,true))ordinary.completeCare(()->true);
        }
        assertEquals(10,equipped.foodPoints());assertEquals(1,equipped.completedVisits());
        assertEquals(0,ordinary.foodPoints());assertEquals(10.5,ordinary.workSeconds());
        var absent=new WorkplaceFood();
        for(int i=0;i<100;i++) {absent.work(.25,false,true,1.15);absent.work(.25,true,false,1.15);}
        assertEquals(0,absent.workSeconds());assertEquals(0,absent.foodPoints());
        absent.work(3600,true,true,1.15);assertEquals(.2875,absent.workSeconds(),.00001);
        absent.work(.25,true,true,Double.NaN);assertEquals(.5375,absent.workSeconds(),.00001);
    }
    @Test void creationTravelAndInvalidEquipmentDoNotProvideFood() {
        var food=new WorkplaceFood();
        food.advance(1,true,true,true);
        assertEquals(0,food.foodPoints());
        for(int i=0;i<100;i++){food.work(.25,false,true);food.work(.25,true,false);}
        assertEquals(0,food.workSeconds());assertEquals(0,food.foodPoints());
        assertFalse(food.work(Double.NaN,true,true));assertFalse(food.work(Double.POSITIVE_INFINITY,true,true));
        food.work(100000,true,true);assertEquals(.25,food.workSeconds());
    }
    @Test void failedWorldActionCannotRenewFoodAndSuccessfulActionRunsOnlyOnce() {
        var food=new WorkplaceFood();
        for(int i=0;i<48;i++)food.work(.25,true,true);
        assertEquals(0,food.foodPoints());
        assertFalse(food.completeCare(()->false));
        assertEquals(0,food.completedVisits());
        var loaded=new Gson().fromJson(new Gson().toJson(food),WorkplaceFood.class);
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        assertTrue(loaded.completeCare(()->{calls.incrementAndGet();return true;}));
        assertFalse(loaded.completeCare(()->{calls.incrementAndGet();return true;}));
        assertEquals(1,calls.get());assertEquals(10,loaded.foodPoints());
        loaded.advance(1440,true,true,true);
        for(int i=0;i<48;i++)loaded.work(.25,true,true);
        assertFalse(loaded.completeCare(()->false));
        assertEquals(0,loaded.foodPoints());assertEquals(1440,loaded.minutesSinceTending());
    }
    @Test void ranchGrowthIsBoundedAndPausesWithoutOnlineSimulationOrHabitat() {
        var food=new WorkplaceFood();var herd=food.ranch();
        for(int i=0;i<3;i++)herd.fed();assertFalse(herd.canBreed());
        herd.fed();assertTrue(herd.canBreed());
        var young=UUID.randomUUID();herd.born(young,"Cow");assertFalse(herd.canBreed());
        food.advance(1440,false,true,true);food.advance(1440,true,false,true);food.advance(1440,true,true,false);
        assertFalse(herd.ready());assertEquals(0,herd.ageMinutes());
        tend(food);
        food.advance(1439,true,true,true);assertFalse(herd.ready());
        food.advance(1,true,true,true);assertTrue(herd.ready());
        food=new Gson().fromJson(new Gson().toJson(food),WorkplaceFood.class);herd=food.ranch();
        assertEquals(young,herd.offspring());assertTrue(herd.ready());
        herd.matured(young);assertFalse(herd.ready());assertTrue(herd.grown());
        food.advance(1440,true,true,true);assertFalse(herd.ready());
        tend(food);
        food.advance(Long.MAX_VALUE,true,true,true);assertTrue(herd.ready());
        herd.harvested();assertNull(herd.offspring());assertFalse(herd.canBreed());
    }
    @Test void allTownRaisedLivestockSuppressItemOutputAndKeepNativeAppearanceRoles() throws Exception {
        var root=java.nio.file.Path.of("src/main/resources/Server/NPC/Roles/Aetherhaven");
        for(String adult:java.util.List.of("Cow","Pig","Sheep","Chicken","Goat","Mosshorn","Skrill","Tetrabird","Bluebird"))for(String suffix:java.util.List.of("","_Young")) {
            var role=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(root.resolve("Aetherhaven_Ranch_"+adult+suffix+".json"))).getAsJsonObject();
            assertEquals(adult.equals("Bluebird")?"Template_Birds_Passive":"Template_Animal_Neutral",role.get("Reference").getAsString());
            var changes=role.getAsJsonObject("Modify");
            assertEquals("Empty",changes.get("DropList").getAsString());
            assertFalse(changes.get("IsMemory").getAsBoolean());
            if(!adult.equals("Bluebird")) {
                assertEquals("",changes.get("ProduceItem").getAsString());
                assertFalse(changes.get("IsTameable").getAsBoolean());
            } else {
                // Native passive birds have no item production or taming parameters.
                assertEquals(0.5,changes.get("MinHeightOverGround").getAsDouble());
                assertEquals(2,changes.get("MaxHeightOverGround").getAsDouble());
            }
        }
    }
    @Test void escapedOrUnloadedOffspringRemainTrackedAndDoNotAge() {
        var food=new WorkplaceFood();var animal=UUID.randomUUID();food.ranch().born(animal,"Cow");tend(food);
        food.advance(360,true,true,true,false);
        assertEquals(animal,food.ranch().offspring());assertEquals(0,food.ranch().ageMinutes());
        assertFalse(food.ranch().canBreed());assertEquals(10,food.foodPoints());
        food.advance(120,true,true,true,true);assertEquals(120,food.ranch().ageMinutes());
    }
    @Test void confirmedLossRequiresFreshFeedingAndLateNotificationsCannotClearAnotherAnimal() {
        var herd=new RanchCare();var first=UUID.randomUUID();var second=UUID.randomUUID();
        herd.born(first,"Cow");herd.advance(1440);
        assertFalse(herd.lost(null));assertFalse(herd.lost(second));assertTrue(herd.lost(first));
        assertFalse(herd.ready());assertFalse(herd.canBreed());assertEquals(0,herd.ageMinutes());
        for(int i=0;i<RanchCare.FEEDS_PER_BIRTH;i++)herd.fed();assertTrue(herd.canBreed());
        herd.born(second,"Cow");assertFalse(herd.lost(first));assertEquals(second,herd.offspring());
        herd=new Gson().fromJson(new Gson().toJson(herd),RanchCare.class);
        assertTrue(herd.lost(second));assertNull(herd.offspring());
    }
    @Test void wildAndTamedAnimalsShareBreedingFamiliesButDifferentSpeciesCannotBreed() {
        assertNull(RanchWorkService.breedingFamily(java.util.List.of("Cow","Pig")));
        assertNull(RanchWorkService.breedingFamily(java.util.List.of("Cow","Unknown_Mod_Cow")));
        assertNull(RanchWorkService.breedingFamily(java.util.List.of("Cow","Aetherhaven_Ranch_Cow_Young")));
        var pair=RanchWorkService.breedingFamily(java.util.List.of("Tamed_Cow","Cow"));
        assertNotNull(pair);assertEquals("Aetherhaven_Ranch_Cow_Young",pair.youngRole());
        assertEquals("Aetherhaven_Ranch_Cow",pair.grownRole());
        RanchWorkService.registerFamily(new RanchWorkService.Family("Mod_Cow","Mod_Young","Mod_Adult"));
        assertNotNull(RanchWorkService.breedingFamily(java.util.List.of("Mod_Cow","Mod_Cow")));
        assertNull(RanchWorkService.breedingFamily(java.util.List.of("Mod_Cow","Cow")));
    }
    @Test void lastCareRecordsOnlySuccessfulWorldActionsAndSurvivesReload() {
        var food=new WorkplaceFood();
        for(int i=0;i<48;i++)food.work(.25,true,true);
        assertFalse(food.completeCare(()->false,"Crop soil watered."));
        assertEquals("No recorded care action yet.",food.lastCare());
        assertTrue(food.completeCare(()->true,"Crop soil watered."));
        food=new Gson().fromJson(new Gson().toJson(food),WorkplaceFood.class);
        assertEquals("Crop soil watered.",food.lastCare());
        assertFalse(food.completeCare(()->true,"Breeding animals fed."));
        assertEquals("Crop soil watered.",food.lastCare());
    }
    @Test void fullDayGraceOnlyAdvancesWhileOnlineAndSimulated() {
        var food=new WorkplaceFood();tend(food);assertEquals(10,food.foodPoints());
        food.advance(10000,false,true,true);food.advance(10000,true,false,true);
        assertEquals(0,food.minutesSinceTending());
        food.advance(360,true,true,true);assertTrue(food.needsTending());assertEquals(10,food.foodPoints());
        food.advance(1079,true,true,true);assertEquals(10,food.foodPoints());
        food.advance(1,true,true,true);assertEquals(0,food.foodPoints());
        tend(food);assertEquals(10,food.foodPoints());assertEquals(2,food.completedVisits());
    }
    @Test void missingEquipmentPausesSupportAndSavingKeepsPartialWorkAndGrace() {
        var food=new WorkplaceFood();for(int i=0;i<20;i++)food.work(.25,true,true);
        var gson=new Gson();food=gson.fromJson(gson.toJson(food),WorkplaceFood.class);
        assertEquals(5,food.workSeconds());tend(food);assertEquals(10,food.foodPoints());
        food.advance(10,true,true,false);assertEquals(0,food.foodPoints());
        food.advance(1,true,true,true);assertEquals(10,food.foodPoints());
        var restored=gson.fromJson(gson.toJson(food),WorkplaceFood.class);assertEquals(11,restored.minutesSinceTending());
        assertEquals(10,restored.foodPoints());assertEquals(1,restored.completedVisits());
    }
    @Test void foodChangesAdmissionButNeverProducesInventoryAndResizingRequiresNewCare() {
        var town=new TownRecord();town.setTownId(UUID.randomUUID());
        town.getClaimedTerritoryChunksMutable().add(ClaimedTerritoryChunkRecord.of(0,0));
        town.getResidentNpcRecords().add(new ResidentNpcRecord("Farmer","farmer",null,UUID.randomUUID()));
        var sites=town.getApprenticeWorkplaces();assertNull(sites.unlock(town,TownsfolkWorkType.FARMING));
        var area=new WorkplaceArea(0,100,0,8,8);assertNull(sites.save(town,null,TownsfolkWorkType.FARMING,area,"Garden"));
        var site=sites.sites().getFirst();assertFalse(TownPopulation.snapshot(town,ConstructionCatalog.empty()).canAdmit());
        tend(site.food());assertEquals(5,TownPopulation.snapshot(town,ConstructionCatalog.empty()).capacity());
        assertTrue(TownPopulation.snapshot(town,ConstructionCatalog.empty()).canAdmit());
        assertTrue(site.orders().list().isEmpty());assertTrue(town.getWarehouse().batches().isEmpty());
        assertNull(sites.save(town,site.id(),site.type(),area,"Renamed"));assertEquals(10,site.food().foodPoints());
        assertNull(sites.save(town,site.id(),site.type(),new WorkplaceArea(0,100,0,9,8),"Expanded"));assertEquals(0,site.food().foodPoints());
    }
}
