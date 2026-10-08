package com.hexvane.aetherhaven.workplace;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class ExcavationPlanTest {
    @Test void BlocksWithoutDropsCanBeMinedOnceAndSurviveReload(){
        var p=plan();assertTrue(p.prepare(Map.of()));assertFalse(p.prepare(Map.of("unexpected",1)));
        p=new Gson().fromJson(new Gson().toJson(p),ExcavationPlan.class);
        assertTrue(p.dropsPrepared());for(int i=0;i<12;i++)p.attend(.5,true,1);
        assertTrue(p.begin(p.id(),6,new Point(10,100,10),()->true));assertTrue(p.settle(p.id(),true));
        assertEquals(1,p.completed());assertTrue(p.cargo().isEmpty());assertFalse(p.dropsPrepared());
        assertFalse(p.settle(p.id(),true));
    }
    @Test void TallStairsHaveShortEntranceAndFloorsOutsideTheExcavation(){
        for(var side:ExcavationPlan.Entrance.values())for(var layout:List.of(ExcavationPlan.Layout.DEEP_STAIRS_UP,ExcavationPlan.Layout.DEEP_STAIRS_DOWN)){
            var area=new WorkplaceArea(0,80,0,15,15,11);
            var cells=ExcavationPlan.tunnel(area,side,layout);
            for(var p:cells){
                int depth=switch(side){case NORTH->p.z();case SOUTH->14-p.z();case WEST->p.x();case EAST->14-p.x();};
                int floor=ExcavationPlan.floorAt(area,side,layout,p);
                assertTrue(p.y()<floor+(depth<5?3:4));
            }
            for(var p:ExcavationPlan.floors(area,side,layout)){
                assertFalse(cells.contains(p));assertTrue(cells.contains(new Point(p.x(),p.y()+1,p.z())));
            }
            assertEquals(138,cells.size());
        }
    }
    private final WorkplaceArea area=new WorkplaceArea(8,100,8,15,14);
    private final ExcavationPlan.Cell cell=new ExcavationPlan.Cell(new Point(11,102,11),"Rock_Stone",0,1);
    private ExcavationPlan plan(){var p=new ExcavationPlan();assertNull(p.start(0,UUID.randomUUID(),UUID.randomUUID(),area,ExcavationPlan.Entrance.NORTH,List.of(cell)));return p;}
    private void ready(ExcavationPlan p){assertTrue(p.prepare(Map.of("Rock_Stone_Cobble",1)));for(int i=0;i<12;i++)p.attend(.5,true,1);}
    @Test void FourEntrancesPreserveFloorsAndStayBoundedWithAnApproachApron() {
        for(var side:ExcavationPlan.Entrance.values()) {
            var a=new WorkplaceArea(-20,80,-40,-5,-12);var points=ExcavationPlan.tunnel(a,side);
            assertFalse(points.isEmpty());assertEquals(points.size(),new HashSet<>(points).size());
            for(var p:points){assertTrue(a.contains(p.x(),p.y(),p.z()));assertTrue(p.y()>=80&&p.y()<=82);
                int depth=switch(side){case NORTH->p.z()-a.minZ();case SOUTH->a.maxZ()-1-p.z();case WEST->p.x()-a.minX();case EAST->a.maxX()-1-p.x();};assertTrue(depth>=3&&depth<=34);}
        }
        assertEquals(288,ExcavationPlan.tunnel(new WorkplaceArea(0,80,0,64,64),ExcavationPlan.Entrance.NORTH).size());
        assertTrue(ExcavationPlan.tunnel(new WorkplaceArea(0,80,0,4,10),ExcavationPlan.Entrance.NORTH).isEmpty());
    }
    @Test void PlanMustBeConcreteAndRevisionChecked() {
        var p=new ExcavationPlan();assertNotNull(p.start(0,UUID.randomUUID(),UUID.randomUUID(),area,ExcavationPlan.Entrance.NORTH,List.of(cell,cell)));
        assertNull(p.id());p=plan();assertNotNull(p.start(0,UUID.randomUUID(),UUID.randomUUID(),area,ExcavationPlan.Entrance.NORTH,List.of(cell)));assertEquals(1,p.total());
        assertNotNull(p.start(1,null,UUID.randomUUID(),area,ExcavationPlan.Entrance.NORTH,List.of(cell)));assertEquals(1,p.revision());
    }
    @Test void TravelPausedAndFailedWorkCannotRemoveOrAwardRock() {
        var p=plan();assertTrue(p.prepare(Map.of("Rock_Stone_Cobble",1)));p.attend(120,false,1);assertEquals(0,p.work());
        assertFalse(p.begin(p.id(),6,new Point(10,100,10),()->fail("Travel cannot excavate")));
        assertTrue(p.pause(1,true));p.attend(.5,true,5);assertEquals(0,p.work());assertTrue(p.pause(2,false));
        for(int i=0;i<12;i++)p.attend(.5,true,1);
        assertFalse(p.begin(p.id(),6,new Point(10,100,10),()->false));assertFalse(p.pending());assertTrue(p.cargo().isEmpty());
        assertFalse(p.begin(UUID.randomUUID(),6,new Point(10,100,10),()->fail("Stale plan cannot remove rock")));
    }
    @Test void DeferredConfirmationAndReloadDoNotDuplicateCargo() {
        var p=plan();ready(p);assertTrue(p.begin(p.id(),6,new Point(10,100,10),()->true));assertTrue(p.pending());assertTrue(p.cargo().isEmpty());
        assertFalse(p.settle(p.id(),null));assertTrue(p.pending());
        var copy=new Gson().fromJson(new Gson().toJson(p),ExcavationPlan.class);assertTrue(copy.settle(copy.id(),true));
        assertEquals(Map.of("Rock_Stone_Cobble",1),copy.cargo());assertEquals(1,copy.completed());assertFalse(copy.settle(copy.id(),true));
        assertFalse(copy.delivered(copy.id(),()->false));assertFalse(copy.delivered(UUID.randomUUID(),()->fail("Wrong order cannot deliver")));
        assertTrue(copy.delivered(copy.id(),()->true));assertFalse(copy.delivered(copy.id(),()->fail("Cannot deliver twice")));
    }
    @Test void ChangedTerrainStopsWithoutPayoutAndPlayerEditsCannotResumeSilently() {
        var p=plan();ready(p);assertTrue(p.begin(p.id(),6,new Point(10,100,10),()->true));assertTrue(p.settle(p.id(),false));
        assertTrue(p.edited());assertTrue(p.paused());assertTrue(p.cargo().isEmpty());assertFalse(p.pause(p.revision(),false));
        p=plan();assertFalse(p.markEdited(new Point(0,0,0)));assertTrue(p.markEdited(cell.point()));assertTrue(p.paused());
        assertFalse(p.begin(p.id(),6,new Point(10,100,10),()->fail("Edited terrain cannot be mined")));
    }
    @Test void PendingOrCarriedMaterialSurvivesAReplacementAttempt() {
        var p=plan();ready(p);assertTrue(p.begin(p.id(),6,new Point(10,100,10),()->true));
        assertNotNull(p.start(p.revision(),UUID.randomUUID(),UUID.randomUUID(),area,ExcavationPlan.Entrance.NORTH,List.of(cell)));assertTrue(p.pending());
        p.settle(p.id(),true);assertNotNull(p.start(p.revision(),UUID.randomUUID(),UUID.randomUUID(),area,ExcavationPlan.Entrance.NORTH,List.of(cell)));assertEquals(1,p.cargo().get("Rock_Stone_Cobble"));
    }
    @Test void ReplacementWorkersMustReachTheSavedCargoLocation() {
        var p=plan();ready(p);p.begin(p.id(),6,new Point(10,100,10),()->true);p.settle(p.id(),true);
        assertFalse(p.pickup(p.id(),"worker",new Point(40,100,40)));
        assertTrue(p.pickup(p.id(),"worker",new Point(11,100,10)));
        p.trackCarrier("worker",new Point(25,100,20));p.trackCarrier("outsider",new Point(100,100,100));
        var copy=new Gson().fromJson(new Gson().toJson(p),ExcavationPlan.class);
        assertEquals(new Point(25,100,20),copy.cargoPoint());
        assertFalse(copy.pickup(copy.id(),"replacement",new Point(11,100,10)));
        assertTrue(copy.pickup(copy.id(),"replacement",new Point(25,100,21)));assertEquals("replacement",copy.carrier());
        assertTrue(copy.delivered(copy.id(),()->true));assertNull(copy.cargoPoint());assertNull(copy.carrier());
    }
    @Test void OrdinaryBlocksDoNotNeedAMaterialWhitelist() {
        assertEquals(1,NativeExcavation.skill("Rock_Stone"));assertEquals(9,NativeExcavation.skill("Ore_Adamantite_Magma"));
        assertEquals(1,NativeExcavation.skill("Rock_Stone_Brick"));assertEquals(1,NativeExcavation.skill("Wood_Oak_Trunk"));
        assertTrue(WorkplaceBenchAccess.matches(com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType.MINING,"Workbench","Bench_Workbench"));
    }
    @Test void EveryLayoutLeavesConnectedWalkableFloorsInsideItsBounds() {
        for(var side:ExcavationPlan.Entrance.values())for(var layout:ExcavationPlan.Layout.values())
            for(var original:List.of(new WorkplaceArea(-21,80,-41,-6,-13),new WorkplaceArea(0,80,0,64,64),
                new WorkplaceArea(Integer.MAX_VALUE-16,80,Integer.MIN_VALUE,Integer.MAX_VALUE,Integer.MIN_VALUE+16))) {
                var a=new WorkplaceArea(original.minX(),original.floorY(),original.minZ(),original.maxX(),original.maxZ(),10);
                var points=ExcavationPlan.tunnel(a,side,layout);var unique=new HashSet<>(points);
                assertFalse(points.isEmpty());assertEquals(points.size(),unique.size());assertTrue(points.size()<=900);
                var floors=new LinkedHashSet<Point>();
                for(var point:points) {
                    assertTrue(a.contains(point.x(),point.y(),point.z()));
                    int floor=ExcavationPlan.floorAt(a,side,layout,point);
                    assertTrue(floor>=a.floorY()&&floor<=a.floorY()+7);
                    assertTrue(point.y()>=floor&&point.y()<floor+ExcavationPlan.passageHeight(a,layout));
                    assertFalse(unique.contains(new Point(point.x(),floor-1,point.z())),"Supporting floor must survive");
                    floors.add(new Point(point.x(),floor,point.z()));
                }
                // Each passage has full three block headroom, with no isolated branch or step.
                assertEquals(floors.size()*ExcavationPlan.passageHeight(a,layout)-(ExcavationPlan.passageHeight(a,layout)==4?6:0),points.size());
                var reached=new HashSet<Point>();var queue=new ArrayDeque<Point>();queue.add(floors.iterator().next());
                while(!queue.isEmpty()) {
                    var from=queue.remove();if(!reached.add(from))continue;
                    for(var candidate:floors)if(!reached.contains(candidate)
                        &&Math.abs((long)candidate.x()-from.x())+Math.abs((long)candidate.z()-from.z())==1
                        &&Math.abs(candidate.y()-from.y())<=1)queue.add(candidate);
                }
                assertEquals(floors,reached,"Every floor must be reachable by single block steps");
            }
    }
    @Test void BranchesHaveTwoArmsAndStairsChangeHeightWithoutCrossingTheWorkBand() {
        var a=new WorkplaceArea(0,100,0,21,21);
        var branches=ExcavationPlan.tunnel(a,ExcavationPlan.Entrance.NORTH,ExcavationPlan.Layout.BRANCHES);
        assertEquals(288,branches.size());
        assertEquals(1,branches.stream().mapToInt(Point::x).min().orElseThrow());
        assertEquals(19,branches.stream().mapToInt(Point::x).max().orElseThrow());
        assertTrue(ExcavationPlan.tunnel(new WorkplaceArea(0,100,0,8,21),ExcavationPlan.Entrance.NORTH,ExcavationPlan.Layout.BRANCHES).isEmpty());
        for(var layout:List.of(ExcavationPlan.Layout.STAIRS_UP,ExcavationPlan.Layout.STAIRS_DOWN)) {
            var points=ExcavationPlan.tunnel(a,ExcavationPlan.Entrance.NORTH,layout);
            int[] heights=layout==ExcavationPlan.Layout.STAIRS_UP?new int[]{100,101,102}:new int[]{102,101,100};
            for(int step=0;step<3;step++) {
                int z=3+4*step;
                assertEquals(heights[step],points.stream().filter(p->p.z()==z).mapToInt(Point::y).min().orElseThrow());
                assertEquals(heights[step]+2,points.stream().filter(p->p.z()==z).mapToInt(Point::y).max().orElseThrow());
            }
        }
    }
    @Test void LayoutSurvivesReloadAndLegacyPlansRemainStraight() {
        var a=new WorkplaceArea(0,100,0,15,15);var plan=new ExcavationPlan();
        var cells=ExcavationPlan.tunnel(a,ExcavationPlan.Entrance.WEST,ExcavationPlan.Layout.STAIRS_DOWN).stream()
            .map(p->new ExcavationPlan.Cell(p,"Rock_Stone",0,1)).toList();
        assertNull(plan.start(0,UUID.randomUUID(),UUID.randomUUID(),a,ExcavationPlan.Entrance.WEST,ExcavationPlan.Layout.STAIRS_DOWN,cells));
        var gson=new Gson();var saved=gson.toJsonTree(plan).getAsJsonObject();
        var copy=gson.fromJson(saved,ExcavationPlan.class);
        assertEquals(ExcavationPlan.Layout.STAIRS_DOWN,copy.layout());assertEquals(102,copy.floorAt(copy.next().point()));
        assertEquals(plan.next(),copy.next());assertEquals(plan.id(),copy.id());
        saved.remove("layout");copy=gson.fromJson(saved,ExcavationPlan.class);
        assertEquals(ExcavationPlan.Layout.STRAIGHT,copy.layout());assertEquals(100,copy.floorAt(copy.next().point()));
        saved.add("layout",com.google.gson.JsonNull.INSTANCE);
        assertEquals(ExcavationPlan.Layout.STRAIGHT,gson.fromJson(saved,ExcavationPlan.class).layout());
    }
    @Test void SavedPendingHarvestKeepsItsAccessiblePickupLocation() {
        var p=plan();ready(p);var stand=new Point(10,100,10);
        assertFalse(p.begin(p.id(),6,null,()->fail("Missing stand cannot remove rock")));
        assertFalse(p.begin(p.id(),6,new Point(90,100,90),()->fail("Distant stand cannot remove rock")));
        assertTrue(p.begin(p.id(),6,stand,()->true));
        var copy=new Gson().fromJson(new Gson().toJson(p),ExcavationPlan.class);assertTrue(copy.settle(copy.id(),true));
        assertEquals(stand,copy.cargoPoint());assertNotEquals(cell.point(),copy.cargoPoint());
        assertFalse(ExcavationPlan.withinPickupReach(new Point(Integer.MAX_VALUE,100,0),new Point(Integer.MIN_VALUE,100,0)));
        assertTrue(copy.pickup(copy.id(),"worker",stand));copy.trackCarrier("worker",new Point(30,100,30));
        assertFalse(copy.pickup(copy.id(),"worker",stand),"Even the former carrier must retrieve cargo where it stopped");
    }
}
