package com.hexvane.aetherhaven.rescue;

import com.hexvane.aetherhaven.production.TimberHarvestPlan.Position;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("entity")
class HalloweenCoffinOpeningQueueTest {
    private final UUID player=UUID.randomUUID();
    private final Position at=new Position(2,30,4);
    private HalloweenCoffinOpeningQueue queue() {
        var queue=new HalloweenCoffinOpeningQueue();assertTrue(queue.offer(player,at,"Coffin","Coffin_Open",0));return queue;
    }
    @Test void waitsForNativeStateChangeAndDeliversExactlyOnce() {
        var queue=queue();var calls=new AtomicInteger();
        queue.poll(10,true,p->"Coffin",o->calls.incrementAndGet());assertEquals(1,queue.size());
        queue.poll(2_000_000_000L,true,p->"Coffin_Open",o->{assertEquals(player,o.player());calls.incrementAndGet();});
        queue.poll(3_000_000_000L,true,p->"Coffin_Open",o->calls.incrementAndGet());
        assertEquals(1,calls.get());assertTrue(queue.isEmpty());
    }
    @Test void changedBrokenUnloadedOrWrongFamilyCoffinsAreDiscarded() {
        for(String actual:Arrays.asList("Stone","Empty",null,"Other_Coffin_Open")) {
            var queue=queue();queue.poll(1,true,p->actual,o->fail("Unrelated block spawned rescue"));
            assertTrue(queue.isEmpty());
        }
    }
    @Test void expiryAndNovemberDiscardPendingOpenings() {
        var expired=queue();expired.poll(HalloweenCoffinOpeningQueue.WAIT_NANOS,true,p->"Coffin_Open",o->fail());
        assertTrue(expired.isEmpty());
        var november=queue();november.poll(1,false,p->{fail("Off-season request read the world");return null;},o->fail());
        assertTrue(november.isEmpty());
    }
    @Test void duplicateUseDoesNotRefreshDeadlineOrReplaceOriginalPlayer() {
        var queue=queue();
        assertFalse(queue.offer(UUID.randomUUID(),at,"Coffin","Coffin_Open",1_000_000_000L));
        queue.poll(HalloweenCoffinOpeningQueue.WAIT_NANOS,true,p->"Coffin_Open",o->fail());
        assertTrue(queue.isEmpty());
    }
    @Test void requestLimitsBoundWorkAndMissingOpenStateCannotEnterQueue() {
        var queue=new HalloweenCoffinOpeningQueue();
        assertFalse(queue.offer(player,at,"Coffin",null,0));
        assertFalse(queue.offer(player,at,"Coffin","Coffin",0));
        for(int i=0;i<8;i++)assertTrue(queue.offer(player,new Position(i,30,0),"Coffin","Open",0));
        assertFalse(queue.offer(player,new Position(9,30,0),"Coffin","Open",0));
        for(int i=8;i<256;i++)assertTrue(queue.offer(UUID.randomUUID(),new Position(i,30,0),"Coffin","Open",0));
        assertFalse(queue.offer(UUID.randomUUID(),new Position(257,30,0),"Coffin","Open",0));
        assertEquals(256,queue.size());
    }
}
