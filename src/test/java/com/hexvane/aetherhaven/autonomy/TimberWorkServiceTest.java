package com.hexvane.aetherhaven.autonomy;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class TimberWorkServiceTest {
    @Test void groveShiftReleasesOldChoppingBeforeItClearsItsWorkTarget() {
        for(int phase:new int[]{VillagerAutonomyState.PHASE_USE,VillagerAutonomyState.PHASE_TRAVEL,VillagerAutonomyState.PHASE_IDLE}) {
            var state=VillagerAutonomyState.fresh(1);var poi=UUID.randomUUID();var released=new AtomicInteger();
            state.setPhase(phase);state.setTravelTarget(12,100,25,poi);
            assertTrue(TimberWorkService.claimGroveShift(state,()->{
                assertEquals(poi,state.getTargetPoiUuid(),"Release the occupied work POI and stop its animation before forgetting it");released.incrementAndGet();
            }));
            assertEquals(VillagerAutonomyState.PHASE_IDLE,state.getPhase());assertNull(state.getTargetPoiUuid());
            assertEquals(0,state.getTargetX());assertEquals(0,state.getTargetY());assertEquals(0,state.getTargetZ());
            assertEquals(1,released.get());
            assertFalse(TimberWorkService.claimGroveShift(state,()->fail("Idle shifts need no repeat release")));
        }
    }
}
