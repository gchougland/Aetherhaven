package com.hexvane.aetherhaven.workplace;
import com.google.gson.Gson;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town") class MineshaftNetworkTest {
    @Test void deepStairIntermediateStepsCannotStartARoom(){
        var t=town();var sites=t.getApprenticeWorkplaces();var base=sites.sites().getFirst();
        var stair=tunnel(t,11,100,13,MineshaftSelection.Direction.SOUTH,ExcavationPlan.Layout.DEEP_STAIRS_DOWN);
        assertNull(sites.addTunnel(t,t.getOwnerUuid(),base.id(),stair,null));
        var connections=MineshaftNetwork.mapConnections(sites,base);
        assertTrue(connections.keySet().stream().allMatch(p->p.y()==100||p.y()==93));
        var intermediate=MineshaftNetwork.mapPoints(stair.area(),stair.excavation().entrance(),stair.excavation().layout()).stream().filter(p->p.y()==99).findFirst().orElseThrow();
        assertNull(MineshaftNetwork.mapStart(connections,intermediate));
        var wrong=tunnel(t,intermediate.x(),intermediate.y(),intermediate.z(),MineshaftSelection.Direction.SOUTH,ExcavationPlan.Layout.ROOM);
        assertNotNull(sites.addTunnel(t,t.getOwnerUuid(),base.id(),wrong,stair.id()));
        wrong.attachTunnel(base.id(),stair.id()); // Reproduce an order stored by the old planner.
        var restored=new Gson().fromJson(new Gson().toJson(wrong),ApprenticeWorkplace.class);
        assertNotNull(MineshaftNetwork.connectionIssue(sites,restored));
        assertEquals(99,restored.area().floorY());assertEquals(0,restored.excavation().completed());
    }
    @Test void roomsExtendBeyondLowerLandingWithoutChangingFloorOrCuttingStairs(){
        for(var direction:MineshaftSelection.Direction.values())for(var layout:List.of(ExcavationPlan.Layout.STAIRS_DOWN,ExcavationPlan.Layout.DEEP_STAIRS_DOWN)){
            var t=town();var sites=t.getApprenticeWorkplaces();var base=sites.sites().getFirst();
            var stair=tunnel(t,11,100,13,direction,layout);assertNull(sites.addTunnel(t,t.getOwnerUuid(),base.id(),stair,null));
            var points=MineshaftNetwork.mapPoints(stair.area(),stair.excavation().entrance(),layout);
            int dx=direction==MineshaftSelection.Direction.EAST?1:direction==MineshaftSelection.Direction.WEST?-1:0;
            int dz=direction==MineshaftSelection.Direction.SOUTH?1:direction==MineshaftSelection.Direction.NORTH?-1:0;
            var end=points.stream().max(Comparator.comparingInt(p->p.x()*dx+p.z()*dz)).orElseThrow();
            assertEquals(end.y(),MineshaftNetwork.adjacentFloor(sites,base,100,false));
            var empty=new Point(end.x()+dx*3,end.y(),end.z()+dz*3);
            var selected=MineshaftNetwork.mapStart(MineshaftNetwork.mapConnections(sites,base),empty);
            assertNotNull(selected);assertEquals(end,selected.anchor());assertEquals(direction,selected.direction());
            var area=MineshaftSelection.area(end.x(),end.y()-1,end.z(),direction,9,ExcavationPlan.Layout.ROOM);
            var room=ExcavationPlan.tunnel(area,MineshaftSelection.entrance(direction),ExcavationPlan.Layout.ROOM);
            assertEquals(end.y(),area.floorY());assertEquals(405,room.size());
            assertTrue(Collections.disjoint(room,MineshaftNetwork.protectedFloors(sites,base)));
            assertTrue(room.stream().allMatch(p->(p.x()-end.x())*dx+(p.z()-end.z())*dz>0));
            assertNull(MineshaftNetwork.mapStart(MineshaftNetwork.mapConnections(sites,base),new Point(empty.x(),end.y()-1,empty.z())));
        }
    }
    @Test void removingTunnelRetainsOutputAtBaseAcrossReloadAndPreservesPlannerPreferences(){
        var t=town();var sites=t.getApprenticeWorkplaces();var base=sites.sites().getFirst();
        var child=tunnel(t,11,100,13,MineshaftSelection.Direction.SOUTH,ExcavationPlan.Layout.STRAIGHT);
        assertNull(sites.addTunnel(t,t.getOwnerUuid(),base.id(),child,null));dig(child.excavation(),"miner",4);
        assertNotNull(sites.remove(t,t.getOwnerUuid(),child.id()));assertTrue(child.excavation().deposit(child.excavation().id()));
        base.minePreferences(new ApprenticeWorkplace.MinePreferences(MineshaftSelection.Direction.WEST,ExcavationPlan.Layout.DEEP_STAIRS_DOWN,12,ExcavationFittings.Mode.SUPPORTS_LANTERNS));
        assertNull(sites.remove(t,t.getOwnerUuid(),child.id()));assertNull(sites.find(child.id()));
        var copy=new Gson().fromJson(new Gson().toJson(t),TownRecord.class);var output=MineshaftOutput.find(copy,base.id());
        assertEquals(4,output.getAmount("ore"));assertEquals(base.minePreferences(),copy.getApprenticeWorkplaces().find(base.id()).minePreferences());
        assertNotNull(copy.getApprenticeWorkplaces().remove(copy,copy.getOwnerUuid(),base.id()));
        assertEquals(4,output.removeAmountUpTo("ore",9));assertEquals(0,output.getAmount("ore"));
        assertNull(copy.getApprenticeWorkplaces().remove(copy,copy.getOwnerUuid(),base.id()));
    }
    @Test void newStairsHaveFourBlocksOfHeadroomAndUpperFaceIsReachableFromPreviousStep(){
        for(var direction:MineshaftSelection.Direction.values())for(var layout:List.of(ExcavationPlan.Layout.DEEP_STAIRS_UP,ExcavationPlan.Layout.DEEP_STAIRS_DOWN)){
            var t=town();var s=tunnel(t,11,100,13,direction,layout);var p=s.excavation();
            assertEquals(4,ExcavationPlan.passageHeight(s.area(),layout));assertEquals(138,p.total());
            assertTrue(p.withinWorkReach(new Point(11,100,13),new Point(11,104,14)));
            var legacy=new WorkplaceArea(s.area().minX(),s.area().floorY(),s.area().minZ(),s.area().maxX(),s.area().maxZ(),10);
            assertEquals(108,ExcavationPlan.tunnel(legacy,p.entrance(),layout).size());
        }
    }
    @Test void finishedDiggingAllowsConnectingWorkWhileFittingsAwaitSupplies(){
        var t=town();var site=tunnel(t,11,100,13,MineshaftSelection.Direction.SOUTH,ExcavationPlan.Layout.STRAIGHT);
        var plan=site.excavation();var cells=List.of(plan.next());
        assertNull(plan.start(plan.revision(),t.getOwnerUuid(),plan.mine(),site.area(),plan.entrance(),plan.layout(),cells,ExcavationFittings.Mode.SUPPORTS_LANTERNS));
        assertFalse(plan.passageReady());dig(plan,"miner",1);assertFalse(plan.passageReady());
        assertTrue(plan.deposit(plan.id()));assertNotNull(plan.nextFitting());assertTrue(plan.passageReady());
        assertTrue(new Gson().fromJson(new Gson().toJson(plan),ExcavationPlan.class).passageReady());
    }
    @Test void coarseMapUsesCenterlineAcrossOffGridEndsInEveryDirection(){
        for(var direction:MineshaftSelection.Direction.values())for(int offset=0;offset<3;offset++){
            var area=MineshaftSelection.area(1175+offset,113,345+offset,direction,9,ExcavationPlan.Layout.STRAIGHT);
            var entrance=MineshaftSelection.entrance(direction);var points=MineshaftNetwork.mapPoints(area,entrance,ExcavationPlan.Layout.STRAIGHT);
            assertEquals(9,points.size());var connections=new LinkedHashMap<Point,UUID>();points.forEach(p->connections.put(p,UUID.randomUUID()));
            boolean alongZ=direction==MineshaftSelection.Direction.NORTH||direction==MineshaftSelection.Direction.SOUTH;
            for(var p:points){var tile=new Point(p.x()+(alongZ?1:0),p.y(),p.z()+(alongZ?0:1));var chosen=MineshaftNetwork.mapConnection(connections,tile);
                assertNotNull(chosen);assertEquals(alongZ?1175+offset:345+offset,alongZ?chosen.x():chosen.z());
                var next=MineshaftSelection.area(chosen.x(),chosen.y()-1,chosen.z(),direction,9,ExcavationPlan.Layout.STRAIGHT);
                assertTrue(MineshaftNetwork.mapPoints(next,entrance,ExcavationPlan.Layout.STRAIGHT).stream().allMatch(q->(alongZ?q.x():q.z())==(alongZ?chosen.x():chosen.z())));
            }
        }
    }
    private TownRecord town(){var t=new TownRecord(UUID.randomUUID(),UUID.randomUUID(),"test",16,100,16,1,1,0);t.getResidentNpcRecords().add(new ResidentNpcRecord("Aetherhaven_Miner","miner",null,UUID.randomUUID()));assertNull(t.getApprenticeWorkplaces().unlock(t,TownsfolkWorkType.MINING));assertNull(t.getApprenticeWorkplaces().save(t,null,TownsfolkWorkType.MINING,new WorkplaceArea(8,100,8,14,14),"Base"));return t;}
    private ApprenticeWorkplace tunnel(TownRecord t,int x,int y,int z,MineshaftSelection.Direction direction,ExcavationPlan.Layout layout){
        var a=MineshaftSelection.area(x,y-1,z,direction,12,layout);var s=new ApprenticeWorkplace("Tunnel",TownsfolkWorkType.MINING,a);var e=MineshaftSelection.entrance(direction);s.setMineshaftConfiguration(e,layout);
        var cells=ExcavationPlan.tunnel(a,e,layout).stream().map(p->new ExcavationPlan.Cell(p,"Rock_Stone",0,1)).toList();assertNull(s.excavation().start(0,t.getOwnerUuid(),UUID.randomUUID(),a,e,layout,cells));return s;
    }
    @Test void connectedFloorsAndDependenciesSurviveSaveAndUseOneAllowance(){
        var t=town();var sites=t.getApprenticeWorkplaces();var base=sites.sites().getFirst();var stair=tunnel(t,11,100,13,MineshaftSelection.Direction.SOUTH,ExcavationPlan.Layout.STAIRS_DOWN);
        assertNull(sites.addTunnel(t,t.getOwnerUuid(),base.id(),stair,null));
        assertEquals(stair.id(),MineshaftNetwork.floors(sites,base).get(new Point(11,98,25)));
        var branch=tunnel(t,11,98,25,MineshaftSelection.Direction.EAST,ExcavationPlan.Layout.STRAIGHT);
        assertNull(sites.addTunnel(t,t.getOwnerUuid(),base.id(),branch,stair.id()));assertEquals(1,sites.used("miner"));
        var copy=new Gson().fromJson(new Gson().toJson(sites),TownWorkplaces.class);
        assertEquals(2,copy.tunnels(base.id()).size());assertEquals(stair.id(),copy.find(branch.id()).minePrerequisiteId());assertEquals(base.id(),copy.mineBase(copy.find(branch.id())).id());
        assertNotNull(sites.remove(t,t.getOwnerUuid(),stair.id()));assertNotNull(sites.validate(t,base.id(),TownsfolkWorkType.MINING,new WorkplaceArea(8,100,8,15,15),"Moved"));
        assertNull(sites.remove(t,t.getOwnerUuid(),branch.id()));assertNull(sites.remove(t,t.getOwnerUuid(),base.id()));assertTrue(sites.sites().isEmpty());
    }
    @Test void rejectsDisconnectedWrongFloorWrongDependencyAndOutsider(){
        var t=town();var sites=t.getApprenticeWorkplaces();var base=sites.sites().getFirst();
        assertNotNull(sites.addTunnel(t,t.getOwnerUuid(),base.id(),tunnel(t,20,100,20,MineshaftSelection.Direction.SOUTH,ExcavationPlan.Layout.STRAIGHT),null));
        assertNotNull(sites.addTunnel(t,t.getOwnerUuid(),base.id(),tunnel(t,11,99,13,MineshaftSelection.Direction.SOUTH,ExcavationPlan.Layout.STRAIGHT),null));
        assertNotNull(sites.addTunnel(t,UUID.randomUUID(),base.id(),tunnel(t,11,100,13,MineshaftSelection.Direction.SOUTH,ExcavationPlan.Layout.STRAIGHT),null));
        assertEquals(1,sites.sites().size());
    }
    @Test void childReviewExcludesAlreadyScheduledAncestorBlocks(){
        var t=town();var sites=t.getApprenticeWorkplaces();var base=sites.sites().getFirst();var parent=tunnel(t,11,100,13,MineshaftSelection.Direction.SOUTH,ExcavationPlan.Layout.STRAIGHT);assertNull(sites.addTunnel(t,t.getOwnerUuid(),base.id(),parent,null));
        var covered=MineshaftNetwork.ancestorCells(sites,parent.id());var inside=parent.excavation().next();var outside=new ExcavationPlan.Cell(new Point(20,100,25),"Rock_Stone",0,1);
        assertEquals(List.of(outside),MineshaftNetwork.remaining(List.of(inside,outside),covered));
    }
    @Test void baseRemovalRetainsHeldChildCargo(){
        var t=town();var sites=t.getApprenticeWorkplaces();var base=sites.sites().getFirst();var child=tunnel(t,11,100,13,MineshaftSelection.Direction.SOUTH,ExcavationPlan.Layout.STRAIGHT);assertNull(sites.addTunnel(t,t.getOwnerUuid(),base.id(),child,null));
        var p=child.excavation();assertTrue(p.prepare(Map.of("Rock_Stone_Cobble",1)));for(int i=0;i<12;i++)p.attend(.5,true,1);assertTrue(p.begin(p.id(),6,p.next().point(),()->true));assertTrue(p.settle(p.id(),true));
        assertNotNull(sites.remove(t,t.getOwnerUuid(),base.id()));assertNotNull(sites.find(base.id()));assertEquals(Map.of("Rock_Stone_Cobble",1),p.cargo());assertTrue(p.paused());
    }

    @Test void roomsAndSevenBlockStairsFitAllDirectionsAndKeepLegacyBounds() {
        for(var direction:MineshaftSelection.Direction.values())for(var layout:List.of(ExcavationPlan.Layout.ROOM,ExcavationPlan.Layout.DEEP_STAIRS_UP,ExcavationPlan.Layout.DEEP_STAIRS_DOWN))for(int length:new int[]{9,12,21,30}) {
            var a=MineshaftSelection.area(10,99,10,direction,length,layout);var e=MineshaftSelection.entrance(direction);var cells=ExcavationPlan.tunnel(a,e,layout);
            assertEquals(layout==ExcavationPlan.Layout.ROOM?405:length*12-6,cells.size());
            assertTrue(cells.stream().allMatch(p->a.contains(p.x(),p.y(),p.z())));
            assertEquals(100,ExcavationPlan.floorAt(a,e,layout,cells.getFirst()));
            assertEquals(layout==ExcavationPlan.Layout.ROOM?100:layout==ExcavationPlan.Layout.DEEP_STAIRS_DOWN?93:107,ExcavationPlan.floorAt(a,e,layout,cells.getLast()));
        }
        var old=new Gson().fromJson("{\"minX\":0,\"floorY\":100,\"minZ\":0,\"maxX\":5,\"maxZ\":8}",WorkplaceArea.class);
        assertEquals(new WorkplaceArea(0,100,0,5,8),old);
    }
    private static void dig(ExcavationPlan p,String worker,int count){for(int i=0;i<count;i++){
        var stand=new Point(p.next().point().x(),p.floorAt(p.next().point()),p.next().point().z());
        assertTrue(p.prepare(Map.of("ore",1)));for(int tick=0;tick<12;tick++)p.attend(.5,true,1);
        assertTrue(p.begin(p.id(),6,stand,()->true));assertTrue(p.settle(p.id(),true));assertTrue(p.pickup(p.id(),worker,stand));
    }}
    @Test void standaloneOutputSurvivesReloadAndCannotBeDeletedOrDoubleWithdrawn(){
        var t=town();var sites=t.getApprenticeWorkplaces();var base=sites.sites().getFirst();var child=tunnel(t,11,100,13,MineshaftSelection.Direction.SOUTH,ExcavationPlan.Layout.STRAIGHT);
        assertNull(sites.addTunnel(t,t.getOwnerUuid(),base.id(),child,null));var p=child.excavation();dig(p,"miner",4);
        assertEquals(0,MineshaftOutput.find(t,base.id()).getAmount("ore"));assertTrue(p.deposit(p.id()));
        var copy=new Gson().fromJson(new Gson().toJson(t),TownRecord.class);var output=MineshaftOutput.find(copy,base.id());
        assertEquals(4,output.getAmount("ore"));assertNull(MineshaftOutput.find(copy,child.id()));
        assertNotNull(copy.getApprenticeWorkplaces().remove(copy,copy.getOwnerUuid(),base.id()));
        assertFalse(output.withdrawAll(Map.of("ore",5L)));assertEquals(4,output.getAmount("ore"));
        assertEquals(3,output.removeAmountUpTo("ore",3));assertEquals(1,output.removeAmountUpTo("ore",100));assertEquals(0,output.removeAmountUpTo("ore",100));
    }
    @Test void workerKeepsPartialBagAcrossOrdersAndCapacityStillApplies(){
        var t=town();var first=tunnel(t,11,100,13,MineshaftSelection.Direction.SOUTH,ExcavationPlan.Layout.STRAIGHT).excavation();
        var second=tunnel(t,11,100,25,MineshaftSelection.Direction.SOUTH,ExcavationPlan.Layout.STRAIGHT).excavation();
        // Model a short reviewed order containing only the final three rocks.
        var shortPlan=new ExcavationPlan();assertNull(shortPlan.start(0,t.getOwnerUuid(),first.mine(),first.area(),first.entrance(),first.layout(),List.of(first.next(),new ExcavationPlan.Cell(new Point(11,101,14),"Rock_Stone",0,1),new ExcavationPlan.Cell(new Point(11,100,14),"Rock_Stone",0,1))));
        shortPlan.carryLevel(5);dig(shortPlan,"senior",3);assertFalse(shortPlan.continueLoad(second,"junior"));assertTrue(shortPlan.continueLoad(second,"senior"));
        assertEquals(0,shortPlan.cargoCount());assertEquals(3,second.cargoWork());dig(second,"senior",62);
        assertEquals(65,second.cargoCount());assertTrue(second.loadReady());assertEquals(65,second.cargoWork());
        assertTrue(second.deposit(second.id()));assertEquals(65,second.outputCount());assertEquals(0,second.cargoWork());
    }
}
