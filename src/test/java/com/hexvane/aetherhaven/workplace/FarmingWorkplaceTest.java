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
        return FoodSiteService.scan(site,(x,y,z)->cells.get(new Point(x,y,z)));
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
        assertTrue(food.completeCare(()->true));assertEquals(10,food.foodPoints());assertEquals(1,food.completedVisits());
    }
    @Test void setupListsEveryMissingRequirementAndTheExactHeight() {
        var cells=new HashMap<Point,FoodSiteService.Cell>();cells.put(new Point(0,116,0),SOIL);cells.put(new Point(1,116,0),CROP);
        var habitat=scan(site,cells);assertFalse(habitat.ready());
        assertTrue(habitat.issue().contains("Missing Farmer's Workbench"));
        assertTrue(habitat.issue().contains("found 2 (1 crops, 1 clear soil spaces)"));
        assertTrue(habitat.issue().contains("Y=116 to 120"));
        cells.put(new Point(4,115,4),BENCH);assertTrue(scan(site,cells).issue().contains("Missing Farmer's Workbench"));
        assertTrue(scan(site,cells).issue().contains("Bench found at (4, 115, 4) outside that height"));
        cells.put(new Point(4,116,4),BENCH);assertFalse(scan(site,cells).issue().contains("Missing Farmer's Workbench"));
        assertTrue(scan(site,cells).issue().contains("Needs 4 growing spaces"));
    }
    @Test void oldSelectionAbovePlantsExplainsTheirActualHeightWithoutCountingOutsideCrops() {
        var cells=new HashMap<Point,FoodSiteService.Cell>();cells.put(new Point(4,115,4),BENCH);
        for(int x=0;x<4;x++)cells.put(new Point(x,115,0),CROP);
        var habitat=scan(site,cells);
        assertFalse(habitat.ready());assertTrue(habitat.targets().isEmpty());
        assertTrue(habitat.issue().contains("4 crops found outside that height at Y=115"));
        assertTrue(habitat.issue().contains("Resize the workplace from the ground beneath them"));
        var corrected=new ApprenticeWorkplace("Farm",TownsfolkWorkType.FARMING,new WorkplaceArea(0,115,0,5,5));
        assertTrue(scan(corrected,cells).ready());
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
        assertTrue(scan(tall,cells).targets().stream().allMatch(p->cells.get(p)==CROP),"Do not fill the whole area once four crops exist");
    }
    @Test void failedPlantingCannotGrantFoodOrEraseAttendedWork() {
        var food=new WorkplaceFood();for(int i=0;i<48;i++)food.work(.25,true,true);
        assertFalse(food.completePlanting(()->false));assertEquals(12,food.workSeconds());assertEquals(0,food.foodPoints());
        for(int i=0;i<100;i++)food.work(.25,false,true);
        assertEquals(12,food.workSeconds());assertEquals(0,food.completedVisits());
    }
}
