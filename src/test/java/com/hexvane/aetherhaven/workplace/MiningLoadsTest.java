package com.hexvane.aetherhaven.workplace;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town") class MiningLoadsTest {
    private ExcavationPlan plan(int blocks){
        var area=new WorkplaceArea(0,100,0,9,20);var p=new ExcavationPlan();
        var cells=ExcavationPlan.tunnel(area,ExcavationPlan.Entrance.NORTH).stream().limit(blocks).map(at->new ExcavationPlan.Cell(at,"Rock_Stone",0,1)).toList();
        assertNull(p.start(0,UUID.randomUUID(),UUID.randomUUID(),area,ExcavationPlan.Entrance.NORTH,cells));return p;
    }
    private void dig(ExcavationPlan p,int amount){
        assertTrue(p.prepare(Map.of("Rock_Stone_Cobble",amount)));for(int i=0;i<12;i++)p.attend(.5,true,1);
        var at=p.next().point();assertTrue(p.begin(p.id(),6,at,()->true));assertTrue(p.settle(p.id(),true));assertTrue(p.pickup(p.id(),"miner",at));
    }
    @Test void accumulatesTwentyFiveItemsThenDepositsWithoutLosingPartialLoads(){
        var p=plan(30);p.carryLevel(1);
        for(int i=0;i<25;i++){dig(p,1);assertEquals(i+1,p.cargoCount());assertEquals(i==24,p.loadReady());}
        assertFalse(p.prepare(Map.of("Rock_Stone_Cobble",1)));assertEquals(25,p.cargoWork());
        assertTrue(p.deposit(p.id()));assertEquals(25,p.outputCount());assertEquals(0,p.cargoCount());assertEquals(0,p.cargoWork());
        for(int i=0;i<5;i++)dig(p,1);
        assertTrue(p.loadReady());assertEquals(5,p.cargoWork());assertTrue(p.deposit(p.id()));assertEquals(30,p.outputCount());
        assertTrue(p.held());assertFalse(p.heldCargo());assertFalse(p.deposit(p.id()));
        assertNotNull(p.start(p.revision(),UUID.randomUUID(),UUID.randomUUID(),p.area(),p.entrance(),List.of(new ExcavationPlan.Cell(new Point(4,102,3),"Rock_Stone",0,1))));
    }
    @Test void partialCoreDeliveryAndReloadRetainOverflowWithoutPayingWorkTwice(){
        var p=plan(5);for(int i=0;i<5;i++)dig(p,5);assertTrue(p.deposit(p.id()));
        var at=new Point(4,100,1);
        assertFalse(p.pickupOutput(p.id(),"miner",at,Map.of(),1));assertEquals(25,p.outputCount());
        assertTrue(p.pickupOutput(p.id(),"miner",at,Map.of("Rock_Stone_Cobble",25),1));assertEquals(0,p.outputCount());
        assertEquals(0,p.cargoWork());assertTrue(p.haulingOutput());
        p=new Gson().fromJson(new Gson().toJson(p),ExcavationPlan.class);
        assertTrue(p.acceptOutputDelivery(p.id(),Map.of("Rock_Stone_Cobble",7)));assertEquals(18,p.cargoCount());assertEquals(0,p.cargoWork());assertTrue(p.loadReady());
        assertFalse(p.acceptOutputDelivery(p.id(),Map.of("Rock_Stone_Cobble",7)));
        assertTrue(p.deposit(p.id()));assertEquals(18,p.outputCount());assertEquals(0,p.cargoWork());
        assertTrue(p.pickupOutput(p.id(),"miner",at,Map.of("Rock_Stone_Cobble",3),1));assertEquals(15,p.outputCount());assertEquals(3,p.cargoCount());
        assertTrue(p.acceptOutputDelivery(p.id(),Map.of()));assertEquals(3,p.cargoCount());assertTrue(p.deposit(p.id()));assertEquals(18,p.outputCount());
    }
    @Test void capacityTracksMiningSkillAndCountsAllDropTypes(){
        assertEquals(25,ExcavationPlan.carryCapacity(1));assertEquals(35,ExcavationPlan.carryCapacity(2));assertEquals(115,ExcavationPlan.carryCapacity(10));
        var p=plan(3);p.carryLevel(2);dig(p,20);assertFalse(p.loadReady());
        assertTrue(p.prepare(Map.of("Ore",10,"Stone",6)));assertTrue(p.loadReady());
        for(int i=0;i<12;i++)p.attend(.5,true,1);
        assertFalse(p.begin(p.id(),6,p.next().point(),()->fail("Never exceed the carrying limit")));
        assertTrue(p.deposit(p.id()));for(int i=0;i<12;i++)p.attend(.5,true,1);
        assertTrue(p.begin(p.id(),6,p.next().point(),()->true));assertTrue(p.settle(p.id(),true));assertEquals(16,p.cargoCount());
    }
    @Test void stairsHaveOppositeMarkersOnAdjacentFloors(){
        var area=new WorkplaceArea(0,100,0,9,20);
        for(var layout:List.of(ExcavationPlan.Layout.STAIRS_UP,ExcavationPlan.Layout.STAIRS_DOWN))for(var side:ExcavationPlan.Entrance.values()){
            var markers=MineshaftNetwork.stairs(area,side,layout);assertFalse(markers.isEmpty());
            for(var entry:markers.entrySet()){
                var p=entry.getKey();int otherY=p.y()+(entry.getValue()==1?1:-1);
                assertTrue(markers.entrySet().stream().anyMatch(other->other.getValue()!=entry.getValue()&&other.getKey().y()==otherY&&Math.abs(other.getKey().x()-p.x())+Math.abs(other.getKey().z()-p.z())==1));
            }
        }
    }
    @Test void barnStorageStartsAtTenAndAddsTenPerStorageUpgrade(){
        var state=new com.hexvane.aetherhaven.production.PlotProductionState();
        for(int tier=0;tier<=3;tier++){state.setAdamantineLevel(tier);assertEquals(10+10*tier,com.hexvane.aetherhaven.production.WorkplaceProductionUpgrades.barnCapacity(state));}
    }
}
