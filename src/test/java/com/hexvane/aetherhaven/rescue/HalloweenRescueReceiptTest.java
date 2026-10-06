package com.hexvane.aetherhaven.rescue;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.halloween.HalloweenResident;
import com.hexvane.aetherhaven.town.TownRecord;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.Tag("entity")
class HalloweenRescueReceiptTest {
    private final HalloweenRescueReceipt state=new HalloweenRescueReceipt();
    private HalloweenRescueReceipt.Encounter bound(String world,UUID npc) {
        var claim=state.reserve(world,10,120,30);assertNotNull(claim);assertTrue(state.bind(claim.token(),npc));return state.current();
    }
    @Test void concurrentWorldsCanOnlyReserveOneEncounter() throws Exception {
        try(var executor=Executors.newFixedThreadPool(8)) {
            var ready=new CountDownLatch(8);var start=new CountDownLatch(1);
            var tasks=new ArrayList<Future<HalloweenRescueReceipt.Encounter>>();
            for(int i=0;i<8;i++){String world="world"+i;tasks.add(executor.submit(()->{ready.countDown();start.await();return state.reserve(world,0,100,0);}));}
            assertTrue(ready.await(5,TimeUnit.SECONDS));start.countDown();int successful=0;
            for(var task:tasks)if(task.get(5,TimeUnit.SECONDS)!=null)successful++;
            assertEquals(1,successful);
        }
    }
    @Test void unloadingPreservesReceiptAndLastPosition() {
        UUID npc=UUID.randomUUID();var original=bound("a",npc);
        assertTrue(state.removed(npc,"a",true,12,121,32));
        assertEquals(npc,state.current().entity());assertEquals(12,state.current().x());
        assertNull(state.reserve("b",0,100,0));assertFalse(state.retire(original));
        assertTrue(state.authorizes(npc,"a"));assertFalse(state.authorizes(npc,"b"));
    }
    @Test void unrelatedRemovalCannotReleaseOwnedEncounter() {
        UUID npc=UUID.randomUUID();var current=bound("a",npc);
        assertFalse(state.removed(UUID.randomUUID(),"a",false,0,0,0));
        assertFalse(state.removed(npc,"b",false,0,0,0));assertEquals(current,state.current());
    }
    @Test void confirmedRemovalAllowsNewCoffinButRejectsOldReplay() {
        UUID old=UUID.randomUUID();bound("a",old);assertTrue(state.removed(old,"a",false,0,0,0));
        assertEquals(HalloweenRescueReceipt.Observation.REJECTED,state.observe(old,"a",10,120,30));
        UUID next=UUID.randomUUID();bound("b",next);
        assertFalse(state.authorizes(old,"a"));assertTrue(state.authorizes(next,"b"));
    }
    @Test void loadedLegacyJackIsAdoptedOnce() {
        UUID first=UUID.randomUUID();assertTrue(state.authorizes(first,"a"));
        assertEquals(HalloweenRescueReceipt.Observation.ADOPTED,state.observe(first,"a",10,120,30));
        assertEquals(HalloweenRescueReceipt.Observation.REJECTED,state.observe(UUID.randomUUID(),"b",10,120,30));
        assertTrue(state.authorizes(first,"a"));
    }
    @Test void retirementUsesExactSnapshotAndCannotClearReplacement() {
        UUID old=UUID.randomUUID();var saved=bound("a",old);assertTrue(state.retire(saved));
        UUID next=UUID.randomUUID();var current=bound("b",next);
        assertFalse(state.retire(saved));assertFalse(state.rollback(saved.token()));assertEquals(current,state.current());
        assertEquals(HalloweenRescueReceipt.Observation.REJECTED,state.observe(old,"a",10,120,30));
    }
    @Test void inFlightClaimCannotBeRecoveredByAnotherOpening() {
        var claim=state.reserve("a",10,120,30);assertTrue(state.inFlight(claim));
        assertFalse(state.retire(claim));assertNull(state.reserve("b",10,120,30));
        assertTrue(state.rollback(claim.token()));assertNotNull(state.reserve("b",10,120,30));
    }
    @Test void savedInterruptedSpawnRecoversWithoutTimeoutOrChunkLoading() {
        var claim=state.reserve("a",10,120,30);var loaded=new Gson().fromJson(new Gson().toJson(state),HalloweenRescueReceipt.class);
        assertEquals(claim,loaded.current());assertFalse(loaded.inFlight(loaded.current()));
        assertTrue(loaded.retire(loaded.current()));assertNotNull(loaded.reserve("b",10,120,30));
        assertFalse(loaded.bind(claim.token(),UUID.randomUUID()));
    }
    @Test void savedActiveAndRetiredFencesSurviveTownRoundTrip() {
        var town=new TownRecord();var claim=town.halloweenRescue().reserve("source",12,130,44);UUID npc=UUID.randomUUID();
        assertTrue(town.halloweenRescue().bind(claim.token(),npc));
        var loaded=new Gson().fromJson(new Gson().toJson(town),TownRecord.class);
        assertEquals(town.halloweenRescue().current(),loaded.halloweenRescue().current());
        assertNull(loaded.halloweenRescue().reserve("other",0,100,0));
        loaded.completeQuest(HalloweenResident.RESCUE_QUEST);
        var completed=new Gson().fromJson(new Gson().toJson(loaded),TownRecord.class);
        assertNull(completed.halloweenRescue().current());assertTrue(completed.hasQuestCompleted(HalloweenResident.RESCUE_QUEST));
        assertFalse(completed.halloweenRescue().authorizes(npc,"source"));
        assertEquals(HalloweenRescueReceipt.Observation.REJECTED,completed.halloweenRescue().observe(npc,"source",12,130,44));
    }
    @Test void completionDuringSpawnInvalidatesCommit() {
        var claim=state.reserve("a",10,120,30);state.complete();
        assertFalse(state.bind(claim.token(),UUID.randomUUID()));assertNull(state.current());
        assertNull(state.reserve("b",10,120,30));
    }
}
