package com.hexvane.aetherhaven.workplace;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.autonomy.CourierWorkService;
import com.hexvane.aetherhaven.construction.assembly.PlotAssemblyPhase;
import com.hypixel.hytale.math.shape.Box;
import java.util.UUID;
import org.joml.Vector3d;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("construction")
class NativeBuildingWorkTest {
    @Test void apprenticesAcceptBothSiteClearingAndConstruction() {
        assertTrue(NativeBuildingWork.workPhase(PlotAssemblyPhase.CLEARING));
        assertTrue(NativeBuildingWork.workPhase(PlotAssemblyPhase.PLACING));
        assertFalse(NativeBuildingWork.workPhase(null));
    }

    @Test void arrivingNearAStandDoesNotAbandonAPieceWhileTheWorkerStillOverlapsIt() {
        var piece=new BuildingOrders.Cell(UUID.randomUUID(),0,0,64,0,"Rock_Stone",0);
        var body=new Box(-.5,0,-.5,.5,2,.5);
        var stand=new Vector3d(2.5,64,.5);
        // A large resident reaches the courier's arrival radius before reaching safe hammering space.
        var edge=new Vector3d(1.85,64,.5);
        var widePiece=new Box(0,0,0,1.5,1,1);
        assertTrue(CourierWorkService.arrived(edge,stand));
        assertFalse(NativeBuildingWork.readyToWork(edge,stand,body,piece,widePiece));
        assertTrue(NativeBuildingWork.readyToWork(stand,stand,body,piece,widePiece));
        assertFalse(NativeBuildingWork.readyToWork(new Vector3d(8,64,.5),stand,body,piece,widePiece));
        // A foundation may be worked from the ground one block above its cell.
        assertTrue(NativeBuildingWork.readyToWork(new Vector3d(1.5,65,.5),new Vector3d(1.5,65,.5),body,piece,Box.UNIT));
    }

    @Test void standSearchCanReachGroundAboveFoundationsAndBelowHighWalls() {
        var piece=new BuildingOrders.Cell(UUID.randomUUID(),0,10,64,10,"Rock_Stone",0);
        var target=new NativeBuildingWork.Target(piece,null);
        for(int feetY:new int[]{65,64,63,62,61}) {
            var stand=new Vector3d(11.5,feetY,10.5);
            assertEquals(stand,NativeBuildingWork.findStand(target,point->point.y()==feetY?stand:null));
        }
        assertNull(NativeBuildingWork.findStand(target,point->null));
        assertNull(NativeBuildingWork.findStand(target,point->point.y()==70?new Vector3d(11.5,70,10.5):null));
    }

    @Test void clearingEffortCannotBecomeFreePlacementEffortOrBlockTheNextPhase() {
        var plan=new BuildingOrders();
        assertNull(plan.add(UUID.randomUUID(),UUID.randomUUID()));
        var order=plan.list().getFirst().id();
        var clear=new BuildingOrders.Cell(order,0,10,64,10,"Rock_Stone",0,true);
        var build=new BuildingOrders.Cell(order,0,10,64,10,"Rock_Stone",0);
        assertTrue(plan.focus("anna",clear,1000));
        for(int i=0;i<10;i++)plan.attend("anna",clear,.5,true,1,1000);
        assertTrue(plan.ready("anna",clear,4));
        assertFalse(plan.ready("anna",build,4));
        assertTrue(plan.heldByAnother("boris",clear,1000));
        assertFalse(plan.heldByAnother("boris",build,1000));
        assertTrue(plan.focus("boris",build,1000));
        assertTrue(plan.beginPlacement("anna",clear));
        assertTrue(plan.committed("anna",clear));
        plan.endPlacement();
        assertEquals(build,plan.cell("boris"));
        assertFalse(plan.committed("anna",clear));
        assertEquals(1,plan.contributions());
        assertTrue(plan.focus("anna",build,10_000));
        assertEquals(0,plan.work("anna"));
    }

    @Test void clearingPhaseSurvivesSaveAndOlderSavedCellsStillMeanBuilding() {
        var gson=new Gson();
        var plan=new BuildingOrders();
        plan.add(UUID.randomUUID(),UUID.randomUUID());
        var clearing=new BuildingOrders.Cell(plan.list().getFirst().id(),3,10,64,10,"Rock_Stone",0,true);
        plan.focus("anna",clearing,1000);
        plan.attend("anna",clearing,.5,true,1,1000);
        var loaded=gson.fromJson(gson.toJson(plan),BuildingOrders.class);
        assertEquals(clearing,loaded.cell("anna"));
        assertEquals(.5,loaded.work("anna"));
        String legacy=gson.toJson(new BuildingOrders.Cell(clearing.order(),3,10,64,10,"Rock_Stone",0)).replace(",\"clearing\":false","");
        assertFalse(gson.fromJson(legacy,BuildingOrders.Cell.class).clearing());
    }
}
