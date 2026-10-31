package com.hexvane.aetherhaven.workplace;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FarmingWorkplaceTest {
    private static final FoodSiteService.Cell BENCH=new FoodSiteService.Cell(true,false,false,false);
    private static final FoodSiteService.Cell SOIL=new FoodSiteService.Cell(false,false,false,true);
    private static final FoodSiteService.Cell CROP=new FoodSiteService.Cell(false,false,true,false);
    private final ApprenticeWorkplace site=new ApprenticeWorkplace("Farm",TownsfolkWorkType.FARMING,new WorkplaceArea(0,116,0,5,5));
    private static FoodSiteService.Habitat scan(ApprenticeWorkplace site,Map<Point,FoodSiteService.Cell> cells) {
        var habitat=FoodSiteService.scan(site,(x,y,z)->cells.get(new Point(x,y,z)));
        FoodSiteService.observe(site,habitat);return habitat;
    }
    @Test void startsOnBareSoilWithoutPlayerCropsAndPlantsFourBeforeTending() {
        var cells=new HashMap<Point,FoodSiteService.Cell>();cells.put(new Point(4,116,4),BENCH);
        for(int x=0;x<4;x++)cells.put(new Point(x,116,0),SOIL);
        var food=site.food();
        for(int planted=0;planted<4;planted++) {
            var habitat=scan(site,cells);assertTrue(habitat.ready());
            Point target=habitat.targets().getFirst();assertEquals(SOIL,cells.get(target));
            assertFalse(food.completePlanting(()->{fail("No planting before attendance");return true;}));
            for(int i=0;i<48;i++)food.work(.25,true,true);
            assertTrue(food.completePlanting(()->{cells.put(target,CROP);return true;}));
            assertEquals(0,food.foodPoints());assertEquals(0,food.workSeconds());assertEquals(0,food.completedVisits());
            food=new Gson().fromJson(new Gson().toJson(food),WorkplaceFood.class);
        }
        var habitat=scan(site,cells);assertTrue(habitat.targets().stream().allMatch(p->cells.get(p)==CROP));
        for(int i=0;i<48;i++)food.work(.25,true,true);
        food.observeFarm(habitat.crops(),habitat.emptySpaces(),habitat.ready());
        assertTrue(food.completeCare(()->true));assertEquals(4,food.foodPoints());assertEquals(1,food.completedVisits());
    }
    @Test void setupListsEveryMissingRequirementAndTheExactHeight() {
        var cells=new HashMap<Point,FoodSiteService.Cell>();cells.put(new Point(0,116,0),SOIL);cells.put(new Point(1,116,0),CROP);
        var habitat=scan(site,cells);assertFalse(habitat.ready());
        assertTrue(habitat.issue().contains("Missing Farmer's Workbench"));
        assertTrue(habitat.issue().contains("found 2 (1 crops, 1 clear soil spaces)"));
        assertTrue(habitat.issue().contains("Y=114 to 120"));
        cells.put(new Point(4,113,4),BENCH);assertTrue(scan(site,cells).issue().contains("Missing Farmer's Workbench"));
        assertTrue(scan(site,cells).issue().contains("Bench found at (4, 113, 4) outside that height"));
        cells.put(new Point(4,116,4),BENCH);assertFalse(scan(site,cells).issue().contains("Missing Farmer's Workbench"));
        assertTrue(scan(site,cells).issue().contains("Needs 4 growing spaces"));
    }
    @Test void oldSelectionAbovePlantsIncludesTwoBlocksBelowAndStillExplainsAnythingLower() {
        var cells=new HashMap<Point,FoodSiteService.Cell>();cells.put(new Point(4,113,4),BENCH);
        for(int x=0;x<4;x++)cells.put(new Point(x,113,0),CROP);
        var habitat=scan(site,cells);
        assertFalse(habitat.ready());assertTrue(habitat.targets().isEmpty());
        assertTrue(habitat.issue().contains("4 crops found outside that height at Y=113"));
        assertTrue(habitat.issue().contains("Resize the workplace from the ground beneath them"));
        var corrected=new ApprenticeWorkplace("Farm",TownsfolkWorkType.FARMING,new WorkplaceArea(0,115,0,5,5));
        assertTrue(scan(corrected,cells).ready());
    }
    @Test void fenceSelectionsFindBothGroundCropsAndSoilThroughSavedAreas() {
        for (int depth : List.of(1, 2)) {
            var raised = new ApprenticeWorkplace("Fence farm", TownsfolkWorkType.FARMING,
                new Gson().fromJson(new Gson().toJson(site.area()), WorkplaceArea.class));
            var cells = new HashMap<Point,FoodSiteService.Cell>();
            cells.put(new Point(4,116-depth,4),BENCH);
            for (int x=0;x<4;x++) cells.put(new Point(x,116-depth,0),SOIL);
            assertTrue(scan(raised,cells).ready());
            for (var target : scan(raised,cells).targets()) assertTrue(raised.area().containsWork(target.x(),target.y(),target.z()));
            for (int x=0;x<4;x++) cells.put(new Point(x,116-depth,0),CROP);
            assertTrue(scan(raised,cells).ready());
            assertEquals(4,scan(raised,cells).targets().size());
        }
        var bottom = new WorkplaceArea(0,1,0,5,5);
        assertEquals(0,bottom.workFloorY());
        assertFalse(bottom.containsWork(1,-1,1));
        assertFalse(site.area().containsWork(-1,114,0));
        assertFalse(site.area().containsWork(0,121,0));
        assertFalse(site.area().contains(0,114,0),"Exact excavation bounds must stay unchanged");
    }
    @Test void cropStatesAreRecognizedEvenWithoutABaseKeyButNonFoodIsRejected() {
        for(String crop:List.of("Wheat","Carrot","Corn","Rice")) {
            assertTrue(FoodSiteService.isFoodCrop(null,"Plant_Crop_"+crop+"_Block"));
            assertTrue(FoodSiteService.isFoodCrop(null,"*Plant_Crop_"+crop+"_Block_State_Definitions_Stage2"));
            assertTrue(FoodSiteService.isFoodCrop("Template_Crop_Block","*Plant_Crop_"+crop+"_Block_State_Definitions_StageFinal"));
            assertFalse(FoodSiteService.isFoodCrop(null,"*Plant_Crop_"+crop+"_Block_State_Definitions_Wilted"));
            assertFalse(FoodSiteService.isFoodCrop(null,"Plant_Crop_"+crop+"_Item"));
        }
        assertFalse(FoodSiteService.isFoodCrop(null,"*Plant_Crop_Cotton_Block_State_Definitions_Stage2"));
        assertFalse(FoodSiteService.isFoodCrop(null,"*Plant_Crop_Health1_Block_State_Definitions_Stage2"));
        assertFalse(FoodSiteService.isFoodCrop(null,null));
        FoodSiteService.registerCropBlock("Regression_Radish");
        assertTrue(FoodSiteService.isFoodCrop("Regression_Radish","*Regression_Radish_State_Definitions_Stage1"));
    }
    @Test void existingCropsRemainUsableAndSavedHeightIsRespected() {
        var tall=new ApprenticeWorkplace("Terraces",TownsfolkWorkType.FARMING,new WorkplaceArea(0,116,0,5,5,8));
        var cells=new HashMap<Point,FoodSiteService.Cell>();cells.put(new Point(4,122,4),BENCH);
        for(int x=0;x<4;x++)cells.put(new Point(x,122,0),CROP);
        assertTrue(scan(tall,cells).ready());assertFalse(scan(site,cells).ready());
        cells.put(new Point(3,116,4),SOIL);
        assertTrue(scan(tall,cells).targets().stream().allMatch(p->cells.get(p)==CROP),"Tend the initial patch before expanding");
        for(int i=0;i<48;i++)tall.food().work(.25,true,true);
        assertTrue(tall.food().completeCare(()->true));
        assertEquals(List.of(new Point(3,116,4)),scan(tall,cells).targets(),"Continue planting even with four established crops");
    }
    @Test void failedPlantingCannotGrantFoodOrEraseAttendedWork() {
        var food=new WorkplaceFood();for(int i=0;i<48;i++)food.work(.25,true,true);
        assertFalse(food.completePlanting(()->false));assertEquals(12,food.workSeconds());assertEquals(0,food.foodPoints());
        for(int i=0;i<100;i++)food.work(.25,false,true);
        assertEquals(12,food.workSeconds());assertEquals(0,food.completedVisits());
    }
    @Test void keepsPlantingEveryClearSpaceBetweenCareVisitsAndCountsOnlyActualCrops() {
        var cells=new HashMap<Point,FoodSiteService.Cell>();cells.put(new Point(4,116,4),BENCH);
        for(int x=0;x<4;x++)for(int z=0;z<4;z++)cells.put(new Point(x,116,z),SOIL);
        var food=site.food();int planted=0,cares=0;
        while(planted<16) {
            var habitat=scan(site,cells);assertTrue(habitat.ready());assertTrue(food.needsWork());
            assertEquals(planted,habitat.crops());assertEquals(16-planted,habitat.emptySpaces());
            assertEquals(cares==0?0:planted,food.foodPoints(),"Empty soil cannot provide food");
            var target=habitat.targets().getFirst();
            for(int i=0;i<48;i++)food.work(.25,true,habitat.ready());
            if(cells.get(target)==SOIL){assertTrue(food.completePlanting(()->{cells.put(target,CROP);return true;}));planted++;}
            else {assertEquals(4,planted);assertTrue(food.completeCare(()->true));cares++;}
        }
        scan(site,cells);assertEquals(1,cares);assertEquals(16,food.foodPoints());assertFalse(food.needsWork());
        var town=new Gson().fromJson(new Gson().toJson(Map.of("apprenticeWorkplaces",Map.of("sites",List.of(site)))),
            com.hexvane.aetherhaven.town.TownRecord.class);
        assertEquals(8,com.hexvane.aetherhaven.town.TownPopulation.snapshot(town,
            com.hexvane.aetherhaven.construction.ConstructionCatalog.empty()).capacity());
        assertTrue(FoodSitePage.farmStatus(scan(site,cells)).contains("16 edible crops growing; 0 clear soil spaces"));
        cells.put(new Point(0,116,0),SOIL);scan(site,cells);
        assertEquals(15,food.foodPoints());assertTrue(food.needsWork(),"An emptied space is replanted without waiting six hours");
        food.advance(WorkplaceFood.TENDING_INTERVAL_MINUTES,true,true,true);
        assertEquals(CROP,cells.get(scan(site,cells).targets().getFirst()),"Periodic care takes precedence over expansion");
    }

    @Test void loadedCountsReplaceLegacyFoodAndSurviveSaveWithoutPhantomSoilFood() {
        var cells=new HashMap<Point,FoodSiteService.Cell>();cells.put(new Point(4,116,4),BENCH);
        for(int x=0;x<4;x++)cells.put(new Point(x,116,0),CROP);
        var legacy=new Gson().fromJson("{\"tended\":true,\"equipmentReady\":true,\"completedVisits\":1}",WorkplaceFood.class);
        assertEquals(10,legacy.foodPoints(),"Unloaded old saves retain their existing support until inspected");
        var habitat=scan(site,cells);legacy.observeFarm(habitat.crops(),habitat.emptySpaces(),habitat.ready());
        assertEquals(4,legacy.foodPoints());
        legacy=new Gson().fromJson(new Gson().toJson(legacy),WorkplaceFood.class);assertEquals(4,legacy.foodPoints());
        for(int x=0;x<4;x++)cells.put(new Point(x,116,0),SOIL);
        habitat=scan(site,cells);legacy.observeFarm(habitat.crops(),habitat.emptySpaces(),habitat.ready());
        assertEquals(0,legacy.foodPoints());assertTrue(legacy.needsWork());assertEquals(4,legacy.emptyGrowingSpaces());
        FoodSiteService.observe(site,new FoodSiteService.Habitat(false,"Visit the workplace",List.of()));
        assertEquals(0,site.food().growingCrops());
        legacy.advance(1440,true,true,true);assertEquals(0,legacy.foodPoints());
    }
}
