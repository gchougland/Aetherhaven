package com.hexvane.aetherhaven.halloween;

import com.hexvane.aetherhaven.production.TimberHarvestPlan.Position;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class HalloweenHidingSearchTest {
    private static final HalloweenHidingSearch.Cell AIR = new HalloweenHidingSearch.Cell(true,true,true,false);
    private static final HalloweenHidingSearch.Cell SOLID = new HalloweenHidingSearch.Cell(true,false,false,true);
    private static final HalloweenHidingSearch.Cell DOOR = new HalloweenHidingSearch.Cell(true,false,true,false);
    private static final PlotFootprintRecord ROOM = new PlotFootprintRecord(0,0,0,6,4,6);
    private static final Position HEAD = new Position(3,1,3);
    private static HalloweenHidingSearch.Cell room(Position at) {
        if (at.y() == 0) return SOLID;
        boolean inside = at.x()>=0 && at.x()<=6 && at.z()>=0 && at.z()<=6;
        if (inside && at.y()==4) return SOLID;
        if (inside && at.y()>=1 && at.y()<=3 && (at.x()==0 || at.x()==6 || at.z()==0 || at.z()==6)) return SOLID;
        return AIR;
    }
    private static boolean doorway(Position at) { return at.x()==0 && at.z()==3 && at.y()>=1 && at.y()<=2; }
    @Test void sealedRoomCannotHideHeadEvenWithClearNeighborsAndRoof() {
        var search = new HalloweenHidingSearch(HalloweenHidingSearchTest::room,8192);
        assertTrue(search.canHide(3,1,3)); assertTrue(search.hasRoof(3,1,3,4));
        assertFalse(search.reachesOutside(ROOM,HEAD));
    }
    @Test void openFrontAndOperableClosedDoorBothProvideRouteButDoorCannotHoldHead() {
        for (var opening : new HalloweenHidingSearch.Cell[]{AIR,DOOR}) {
            var search = new HalloweenHidingSearch(at -> doorway(at) ? opening : room(at),8192);
            assertTrue(search.reachesOutside(ROOM,HEAD));
            assertEquals(opening==AIR,search.canHide(0,1,3));
        }
    }
    @Test void oneBlockHoleAndUnloadedExteriorDoNotProveReachability() {
        var hole = new HalloweenHidingSearch(at -> doorway(at) && at.y()==1 ? AIR : room(at),8192);
        assertFalse(hole.reachesOutside(ROOM,HEAD));
        var unloaded = new HalloweenHidingSearch(at -> at.x()<0 ? HalloweenHidingSearch.Cell.UNKNOWN
            : doorway(at) ? AIR : room(at),8192);
        assertFalse(unloaded.reachesOutside(ROOM,HEAD));
    }
    @Test void oneBlockStepRequiresClearanceAboveThePlayersHead() {
        Function<Position,HalloweenHidingSearch.Cell> stairs = at -> {
            if (at.z()!=0) return SOLID;
            return at.y() <= (at.x()>=2 ? 1 : 0) ? SOLID : AIR;
        };
        var bounds = new PlotFootprintRecord(0,0,-1,4,4,1);
        // Seal the other end so the only exit is over the step.
        Function<Position,HalloweenHidingSearch.Cell> onlyStep = at -> at.x()<=0 ? SOLID : stairs.apply(at);
        assertTrue(new HalloweenHidingSearch(onlyStep,8192).reachesOutside(bounds,new Position(1,1,0)));
        var lowCeiling = new HalloweenHidingSearch(at -> at.x()==1 && at.y()==3 ? SOLID : onlyStep.apply(at),8192);
        assertFalse(lowCeiling.reachesOutside(bounds,new Position(1,1,0)));
    }
    @Test void allAttemptsShareReadBudgetAndCachedCellsDoNotRepeatWorldReads() {
        var reads = new AtomicInteger();
        var search = new HalloweenHidingSearch(at -> { reads.incrementAndGet(); return room(at); },20);
        assertFalse(search.reachesOutside(ROOM,HEAD));
        for (int i=0;i<100;i++) search.reachesOutside(ROOM,HEAD);
        assertEquals(20,reads.get()); assertTrue(search.exhausted());
    }
}
