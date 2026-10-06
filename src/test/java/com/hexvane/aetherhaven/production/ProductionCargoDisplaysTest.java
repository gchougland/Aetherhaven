package com.hexvane.aetherhaven.production;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import com.hexvane.aetherhaven.workplace.*;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class ProductionCargoDisplaysTest {
    private TownRecord town(){return new TownRecord(UUID.randomUUID(),UUID.randomUUID(),"test",16,100,16,1,1,0);}
    private ApprenticeWorkplace site(TownRecord town) {
        town.getResidentNpcRecords().add(new ResidentNpcRecord("Aetherhaven_Miner","miner",null,UUID.randomUUID()));
        var sites=town.getApprenticeWorkplaces();assertNull(sites.unlock(town,TownsfolkWorkType.MINING));
        assertNull(sites.save(town,null,TownsfolkWorkType.MINING,new WorkplaceArea(8,100,8,15,14),"Shaft"));return sites.sites().getFirst();
    }
    @Test void WaitingVisualsFollowSavedCargoAndHideDuringTransport() {
        var town=town();var site=site(town);var plan=site.excavation();var stand=new Point(9,100,11);
        assertNull(plan.start(0,town.getOwnerUuid(),UUID.randomUUID(),site.area(),ExcavationPlan.Entrance.NORTH,List.of(new ExcavationPlan.Cell(new Point(10,102,11),"Ore_Iron_Stone",0,3))));
        plan.prepare(Map.of("Ore_Iron",1,"Rock_Stone_Cobble",1));for(int i=0;i<20;i++)plan.attend(.5,true,1);
        assertTrue(plan.begin(plan.id(),10,stand,()->true));assertTrue(ProductionCargoDisplays.waiting(town,Set.of()).isEmpty());
        plan.settle(plan.id(),true);var waiting=ProductionCargoDisplays.waiting(town,Set.of());
        assertEquals(2,waiting.size());assertTrue(waiting.stream().allMatch(s->s.point().equals(stand)&&s.count()==1));
        assertTrue(ProductionCargoDisplays.waiting(town,Set.of(site.id())).isEmpty());
        var gson=new Gson();var restored=gson.fromJson(gson.toJson(town),TownRecord.class);
        assertEquals(waiting,ProductionCargoDisplays.waiting(restored,Set.of()));
        assertTrue(plan.pickup(plan.id(),"worker",stand));plan.trackCarrier("worker",new Point(20,100,20));
        assertTrue(ProductionCargoDisplays.waiting(town,Set.of()).stream().allMatch(s->s.point().equals(new Point(20,100,20))));
        plan.delivered(plan.id(),()->true);assertTrue(ProductionCargoDisplays.waiting(town,Set.of()).isEmpty());
    }
    @Test void TimberShowsOnlyTheAmountStillWaitingAtTheTree() {
        var town=town();var plot=new PlotInstance(UUID.randomUUID(),"plot_lumbermill",PlotInstanceState.COMPLETE,new PlotFootprintRecord(0,99,0,25,110,25),1,100,1,0);town.getPlotInstances().add(plot);
        var gson=new Gson();var saved=gson.toJsonTree(town).getAsJsonObject();var productions=new com.google.gson.JsonObject();
        var production=new com.google.gson.JsonObject();var timber=new com.google.gson.JsonObject();
        timber.add("cargo",gson.toJsonTree(new TimberProductionState.Cargo(UUID.randomUUID(),"Wood_Beech_Trunk",new TimberHarvestPlan.Position(8,100,8),12,4)));
        production.add("timber",timber);productions.add(plot.getPlotId().toString(),production);saved.add("plotProductionByPlotId",productions);
        var restored=gson.fromJson(saved,TownRecord.class);var waiting=ProductionCargoDisplays.waiting(restored,Set.of());
        assertEquals(1,waiting.size());assertEquals(8,waiting.getFirst().count());assertEquals("Wood_Beech_Trunk",waiting.getFirst().item());
        var state=restored.getOrCreatePlotProduction(plot.getPlotId());assertEquals(0,state.getAmount("Wood_Beech_Trunk"));
        assertEquals(4,state.timber().deliver(state,100,4));assertEquals(8,ProductionCargoDisplays.waiting(restored,Set.of()).getFirst().count());
        state.timber().pickUp(8);assertTrue(ProductionCargoDisplays.waiting(restored,Set.of()).isEmpty());
    }
    @Test void ReadingDisplaysDoesNotAdoptLegacyProductionOrCreateWorkPlans() {
        var town=town();var site=site(town);var plot=new PlotInstance(UUID.randomUUID(),"plot_lumbermill",PlotInstanceState.COMPLETE,new PlotFootprintRecord(0,99,0,5,105,5),1,100,1,0);town.getPlotInstances().add(plot);
        var production=town.getOrCreatePlotProduction(plot.getPlotId());var gson=new Gson();String before=gson.toJson(town);
        assertTrue(ProductionCargoDisplays.waiting(town,Set.of()).isEmpty());assertEquals(before,gson.toJson(town));assertNull(site.existingExcavation());assertFalse(production.usesPhysicalTimber());
    }
}
