package com.hexvane.aetherhaven.workplace;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.quest.WorldProof;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
class WorkplaceOwnershipTest {
    private static final Gson GSON=new Gson();
    private static ApprenticeWorkplace farm(String name,int floor) {
        return new ApprenticeWorkplace(name,TownsfolkWorkType.FARMING,new WorkplaceArea(0,floor,0,5,5));
    }
    private static TownRecord town(ApprenticeWorkplace... sites) {
        return GSON.fromJson(GSON.toJson(Map.of("apprenticeWorkplaces",Map.of("sites",List.of(sites)))),TownRecord.class);
    }
    private static Map<Point,FoodSiteService.Cell> crops(int y) {
        var blocks=new HashMap<Point,FoodSiteService.Cell>();
        blocks.put(new Point(4,y,4),new FoodSiteService.Cell(true,false,false,false));
        for(int x=0;x<4;x++)blocks.put(new Point(x,y,0),new FoodSiteService.Cell(false,false,true,false));
        return blocks;
    }
    private static FoodSiteService.Habitat inspect(TownWorkplaces sites,ApprenticeWorkplace site,Map<Point,FoodSiteService.Cell> blocks) {
        return FoodSiteService.scan(site,(x,y,z)->blocks.get(new Point(x,y,z)),new WorkplaceOwnership.Scope(sites,site.area()));
    }

    @Test void anUpstairsMarginCannotClaimTheDownstairsBenchOrCountItsCropsTwice() {
        var bottom=farm("Downstairs",64);var top=farm("Upstairs",69);
        assertFalse(bottom.area().overlaps(top.area()),"These are valid existing stacked workplaces");
        for(var town:List.of(town(bottom,top),town(top,bottom))) {
            var sites=town.getApprenticeWorkplaces();var lower=sites.find(bottom.id());var upper=sites.find(top.id());
            assertSame(lower,sites.ownerAtWork(4,68,4));
            assertTrue(sites.ownsWorkAt(lower.area(),4.5,68.5,4.5));
            assertFalse(sites.ownsWorkAt(upper.area(),4.5,68.5,4.5));
            assertTrue(inspect(sites,lower,crops(68)).ready());
            assertFalse(inspect(sites,upper,crops(68)).ready());
            assertTrue(inspect(sites,upper,crops(68)).targets().isEmpty());
            for(var site:sites.sites()) {
                FoodSiteService.observe(site,inspect(sites,site,crops(68)));
                for(int i=0;i<48;i++)site.food().work(.25,true,site.food().equipmentReady());
                site.food().completeCare(()->true);
            }
            assertEquals(4,com.hexvane.aetherhaven.town.TownPopulation.farmAndRanchFood(town,
                com.hexvane.aetherhaven.construction.ConstructionCatalog.empty()),"Only the owner contributes crop food");
            assertSame(upper,sites.ownerAtWork(4,69,4));
            var terrain=new WorkplaceQuestSteps.Blocks() {
                public boolean loaded(int x,int z){return true;}
                public WorkplaceQuestSteps.Standing at(int x,int y,int z){return x==4&&y==68&&z==4?new WorkplaceQuestSteps.Standing("Farmingbench","Bench_Farming","Bench_Farming"):null;}
            };
            assertSame(lower,WorkplaceQuestSteps.stationSiteAt(terrain,town,null,4,68,4));
        }
    }

    @Test void aStandaloneFenceSelectionOwnsTheGroundBelowAndQuestsFindThatBench() {
        var raised=farm("Raised border",69);var town=town(raised);var sites=town.getApprenticeWorkplaces();var site=sites.find(raised.id());
        assertSame(site,sites.ownerAtWork(4,67,4));
        assertTrue(sites.ownsWorkAt(site.area(),4.5,67.5,4.5));
        assertTrue(inspect(sites,site,crops(67)).ready());
        assertNull(sites.ownerAtWork(4,66,4));
        assertFalse(sites.ownsWorkAt(site.area(),4,66,4));
        var terrain=new WorkplaceQuestSteps.Blocks() {
            public boolean loaded(int x,int z){return true;}
            public WorkplaceQuestSteps.Standing at(int x,int y,int z){return x==4&&y==67&&z==4?new WorkplaceQuestSteps.Standing("Farmingbench","Bench_Farming","Bench_Farming"):null;}
        };
        assertSame(site,WorkplaceQuestSteps.stationSiteAt(terrain,town,null,4,67,4));
        assertEquals(WorldProof.YES,WorkplaceQuestSteps.stationProof(terrain,town,TownsfolkWorkType.FARMING,null));
    }

    @Test void aDifferentWorkplaceTypeCannotBorrowAStationFromTheExactOwner() {
        var lower=new ApprenticeWorkplace("Storage crafts",TownsfolkWorkType.CRAFTING,new WorkplaceArea(0,64,0,5,5));
        var upper=farm("Upstairs",69);var town=town(upper,lower);
        var terrain=new WorkplaceQuestSteps.Blocks() {
            public boolean loaded(int x,int z){return true;}
            public WorkplaceQuestSteps.Standing at(int x,int y,int z){return x==4&&y==68&&z==4?new WorkplaceQuestSteps.Standing("Farmingbench","Bench_Farming","Bench_Farming"):null;}
        };
        assertNull(WorkplaceQuestSteps.stationSiteAt(terrain,town,null,4,68,4));
        assertEquals(WorldProof.NO,WorkplaceQuestSteps.stationProof(terrain,town,TownsfolkWorkType.FARMING,null));
    }
}
