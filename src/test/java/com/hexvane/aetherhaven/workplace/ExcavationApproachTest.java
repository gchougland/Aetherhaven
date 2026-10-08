package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import java.util.*;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.Tag("town") class ExcavationApproachTest {
    @Test void firstLoadCanBePickedUpFromTheMiningStandWithNoAdjacentFreeColumns(){
        var cargo=new Point(1120,116,418);var worker=new Vector3d(1120.5,116,418.5);var plan=new ExcavationPlan();
        var chosen=ExcavationApproach.choose(plan,cargo,worker,0,p->p.equals(cargo)?worker:null,
            p->ExcavationPlan.withinPickupReach(ExcavationApproach.feet(p),cargo));
        assertEquals(worker,chosen);
    }
    @Test void abandonedCargoCanBeReachedFromTheHigherStairLanding(){
        var cargo=new Point(10,100,10);var landing=new Point(10,101,9);var worker=new Vector3d(10.5,101,6.5);
        var chosen=ExcavationApproach.choose(new ExcavationPlan(),cargo,worker,0,p->p.equals(landing)?new Vector3d(10.5,101,9.5):null,
            p->ExcavationPlan.withinPickupReach(ExcavationApproach.feet(p),cargo));
        assertEquals(new Vector3d(10.5,101,9.5),chosen);
    }
    @Test void thinRailDoesNotOccludeTheNextLowerStepButSolidTerrainDoes(){
        var from=new Vector3d(107.1,124.17,122.35);var target=new Point(106,123,123);var previous=new Point(106,124,122);
        assertFalse(ExcavationApproach.intersects(from,target,previous,new com.hypixel.hytale.math.shape.Box(0,0,0,1,.15,1)));
        assertTrue(ExcavationApproach.intersects(from,target,previous,new com.hypixel.hytale.math.shape.Box(0,0,0,1,1,1)));
        assertFalse(ExcavationApproach.intersects(from,target,new Point(106,122,124),new com.hypixel.hytale.math.shape.Box(0,0,0,1,1,1)));
    }
    @Test void slopeRequiresTheWorkerToReachTheSideOfTheStep(){
        var target=new Point(106,123,123);var slope=new Point(106,124,122);
        var lower=new com.hypixel.hytale.math.shape.Box(0,0,0,1,.5,1);
        var upper=new com.hypixel.hytale.math.shape.Box(0,.5,0,1,1,.5);
        assertTrue(ExcavationApproach.intersects(new Vector3d(107.1,124.17,122.35),target,slope,lower));
        var beside=new Vector3d(107.5,124.17,122.5);
        assertFalse(ExcavationApproach.intersects(beside,target,slope,lower));
        assertFalse(ExcavationApproach.intersects(beside,target,slope,upper));
    }
    @Test void CloseWorkerCannotMineThroughWallsOrFromTheFloorAbove(){
        var worker=new Vector3d(10.5,100,10.5);var target=new Point(10,101,13);
        assertTrue(ExcavationApproach.clearReach(worker,target,p->!p.equals(target)));
        assertFalse(ExcavationApproach.clearReach(worker,target,p->p.z()!=12));
        assertFalse(ExcavationApproach.clearReach(new Vector3d(10.5,104,13.5),target,p->p.y()!=103));
    }
    @Test void ClearDescendingFaceRemainsReachableFromTheUpperStep(){
        var worker=new Vector3d(10.5,100,10.5);var target=new Point(10,99,11);
        assertTrue(ExcavationApproach.clearReach(worker,target,p->p.z()>=11||p.y()>=100));
    }
    @Test void railInstallerMovesOffTheTargetAndLeavesArrivalClearance(){
        var plan=new ExcavationPlan();var rail=new Point(106,127,117);var from=new Vector3d(106.5,127,117.5);
        var selected=ExcavationApproach.choose(plan,rail,from,0,p->p.y()==127?new Vector3d(p.x()+.5,127,p.z()+.5):null,
            p->ExcavationApproach.besideRail(rail,p));
        assertNotNull(selected);
        assertTrue(ExcavationApproach.besideRail(rail,selected));
        assertTrue(plan.withinWorkReach(ExcavationApproach.feet(selected),rail));
        assertFalse(ExcavationApproach.besideRail(rail,from));
        assertTrue(ExcavationApproach.besideRail(rail,new Vector3d(107.5,127,117.5)));
        assertFalse(ExcavationApproach.besideRail(rail,new Vector3d(107.1,127,117.5)));
    }
    @Test void lastStairRailCanBeInstalledFromItsSideLane(){
        var area=MineshaftSelection.area(10,100,20,MineshaftSelection.Direction.SOUTH,9,ExcavationPlan.Layout.DEEP_STAIRS_DOWN);
        var entrance=ExcavationPlan.Entrance.NORTH;var rail=ExcavationFittings.plan(area,entrance,ExcavationPlan.Layout.DEEP_STAIRS_DOWN,ExcavationFittings.Mode.RAILS).getLast().point();
        var plan=new ExcavationPlan();assertNull(plan.start(0,UUID.randomUUID(),UUID.randomUUID(),area,entrance,ExcavationPlan.Layout.DEEP_STAIRS_DOWN,List.of(new ExcavationPlan.Cell(rail,"Rock_Stone",0,1))));
        var lane=new Point(rail.x()+1,rail.y(),rail.z());
        var chosen=ExcavationApproach.choose(plan,rail,new Vector3d(rail.x()+.5,rail.y()+1,rail.z()-.5),0,p->p.equals(lane)?new Vector3d(p.x()+.5,p.y(),p.z()+.5):null,p->ExcavationApproach.besideRail(rail,p));
        assertEquals(new Vector3d(lane.x()+.5,lane.y(),lane.z()+.5),chosen);
    }
    @Test void retryDoesNotKeepTheFailedCurrentStandingPosition(){
        var plan=new ExcavationPlan();var target=new Point(10,100,10);var from=new Vector3d(11.5,100,10.5);
        java.util.function.Function<Point,Vector3d> stand=p->p.y()==100&&p.x()==11&&p.z()>=10&&p.z()<=11?new Vector3d(p.x()+.5,100,p.z()+.5):null;
        assertEquals(from,ExcavationApproach.choose(plan,target,from,0,stand));
        assertNotEquals(from,ExcavationApproach.choose(plan,target,from,1,stand));
    }
    @Test void deliveryArrivalToleranceCannotStartMiningBeyondBlockReach(){
        var plan=new ExcavationPlan();var rock=new Point(105,129,118);var approach=new Vector3d(106.5,127,115.5);
        assertFalse(ExcavationApproach.arrived(plan,rock,new Vector3d(106.5,127,114.9),approach));
        assertTrue(ExcavationApproach.arrived(plan,rock,new Vector3d(106.5,126.99999,115.2),approach));
    }
    @Test void descendingFaceCanBeMinedFromTheCurrentUpperStep(){
        var plan=new ExcavationPlan();var feet=new Point(10,100,10);var position=new Vector3d(10.3,100,10.7);
        // The next floor is lower; walking to it before clearing this face is impossible.
        var target=new Point(10,98,12);
        assertEquals(position,ExcavationApproach.choose(plan,target,position,0,p->p.equals(feet)?new Vector3d(10.5,100,10.5):null));
    }
    @Test void nativeWalkingMayStopOnThePreviousStairEdgeIfTheBlockIsReachable(){
        var plan=new ExcavationPlan();var rock=new Point(107,124,122);var approach=new Vector3d(106.5,125,120.5);
        assertTrue(ExcavationApproach.arrived(plan,rock,new Vector3d(106.3,126,120.2),approach));
        assertFalse(ExcavationApproach.arrived(plan,rock,new Vector3d(106.3,127,120.2),approach));
        assertFalse(ExcavationApproach.arrived(plan,new Point(107,121,122),new Vector3d(106.3,126,120.2),approach));
    }
    @Test void feetStraddlingAStepUseTheirPhysicalFootprintWithoutAllowingFloatingWork(){
        var edge=new Vector3d(106.3,125.99999,120.05);
        java.util.function.Predicate<Point> supported=p->p.y()==126&&p.z()==119;
        assertTrue(ExcavationApproach.supported(edge,-.3,.3,-.3,.3,supported));
        assertFalse(ExcavationApproach.supported(new Vector3d(106.3,126,120.5),-.3,.3,-.3,.3,supported));
        assertFalse(ExcavationApproach.supported(new Vector3d(106.3,127,120.05),-.3,.3,-.3,.3,supported));
    }
    @Test void rejectsDistantWorkAndUsesAStandWithinActualReach(){
        var plan=new ExcavationPlan();var target=new Point(10,98,12);var valid=new Point(10,99,11);
        var selected=ExcavationApproach.choose(plan,target,new Vector3d(10.5,100,0.5),0,p->p.equals(valid)?new Vector3d(10.5,99,11.5):null);
        assertEquals(new Vector3d(10.5,99,11.5),selected);
        assertNull(ExcavationApproach.choose(plan,target,new Vector3d(10.5,100,0.5),0,p->null));
    }
}
