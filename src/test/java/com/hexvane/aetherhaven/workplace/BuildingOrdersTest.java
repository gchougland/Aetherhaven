package com.hexvane.aetherhaven.workplace;

import com.google.gson.Gson;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class BuildingOrdersTest {
    private BuildingOrders orders(){var plan=new BuildingOrders();assertNull(plan.add(0,UUID.randomUUID(),UUID.randomUUID()));return plan;}
    private BuildingOrders.Cell cell(BuildingOrders p,int index){return new BuildingOrders.Cell(p.list().getFirst().id(),index,10,128,12,"Rock_Stone",0);}
    @Test void StaleEditsCannotChangeTheProjectQueue() {
        var p=orders();var first=p.list().getFirst();assertNotNull(p.add(0,UUID.randomUUID(),UUID.randomUUID()));
        assertNotNull(p.add(1,first.plot(),first.author()));assertEquals(1,p.list().size());
        assertNull(p.add(1,UUID.randomUUID(),UUID.randomUUID()));var second=p.list().getLast();
        assertTrue(p.move(2,second.id(),-1));assertEquals(second,p.list().getFirst());assertFalse(p.remove(2,first.id()));
        assertFalse(p.move(3,second.id(),-1));assertTrue(p.remove(3,first.id()));assertEquals(List.of(second),p.list());
    }
    @Test void AttendanceCannotAccrueWhileTravelingOrPausedAndClampsLongFrames() {
        var p=orders();var c=cell(p,0);assertTrue(p.focus(c));p.attend(c,60,false,1);assertEquals(0,p.work());
        p.attend(c,Double.NaN,true,1);p.attend(c,1,true,Double.POSITIVE_INFINITY);assertEquals(0,p.work());
        p.attend(c,120,true,1);assertEquals(.5,p.work());assertFalse(p.ready(c,4));
        assertTrue(p.pause(1,true));p.attend(c,.5,true,5);assertEquals(.5,p.work());assertFalse(p.ready(c,.1));assertFalse(p.committed(c));
        assertTrue(p.pause(2,false));p.attend(c,.5,true,1);assertEquals(1,p.work());
    }
    @Test void ChangingTheActualCellDiscardsOldEffortAndCommitCannotRepeat() {
        var p=orders();var first=cell(p,0);var next=cell(p,1);assertTrue(p.focus(first));
        for(int i=0;i<10;i++)p.attend(first,.5,true,1);assertTrue(p.ready(first,4));
        assertTrue(p.focus(next));assertEquals(0,p.work());assertFalse(p.ready(first,4));assertFalse(p.committed(first));
        p.attend(first,.5,true,1);assertEquals(0,p.work());
        for(int i=0;i<10;i++)p.attend(next,.5,true,1);assertTrue(p.committed(next));assertFalse(p.committed(next));assertEquals(1,p.contributions());
        assertFalse(p.committed(null));assertFalse(p.ready(null,4));p.attend(null,.5,true,1);assertEquals(0,p.work());
    }
    @Test void RemovedOrdersCannotContributeEvenAfterAttendance() {
        var p=orders();var c=cell(p,0);p.focus(c);for(int i=0;i<10;i++)p.attend(c,.5,true,1);
        assertTrue(p.remove(p.revision(),c.order()));assertFalse(p.ready(c,4));assertFalse(p.committed(c));assertFalse(p.focus(c));assertEquals(0,p.work());
    }
    @Test void SavedProgressRetainsExactCellAndCannotBeAppliedToAnother() {
        var p=orders();var c=cell(p,12);p.focus(c);p.attend(c,.5,true,1);p.pause(p.revision(),true);
        var gson=new Gson();var copy=gson.fromJson(gson.toJson(p),BuildingOrders.class);
        assertEquals(p.list(),copy.list());assertEquals(c,copy.cell());assertEquals(.5,copy.work());assertTrue(copy.paused());
        copy.pause(copy.revision(),false);assertTrue(copy.ready(c,.5));assertFalse(copy.ready(cell(copy,13),.5));
    }
    @Test void SkillAndStationImprovePaceWithoutUnboundedInstantWork() {
        assertTrue(BuildingOrders.duration(1,1)>BuildingOrders.duration(5,1));
        assertTrue(BuildingOrders.duration(5,1)>BuildingOrders.duration(5,3));
        assertEquals(BuildingOrders.duration(10,3),BuildingOrders.duration(Integer.MAX_VALUE,Integer.MAX_VALUE));
        assertTrue(BuildingOrders.duration(10,3)>=1.25);
    }
}
