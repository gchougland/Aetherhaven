package com.hexvane.aetherhaven.autonomy;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

@org.junit.jupiter.api.Tag("autonomy")
class VillagerLoopPlaybackTest {
    @Test void repeatedBeatsAndReadingVoicesKeepTheSameBodyLoop() {
        String active = null;
        int starts = 0;
        for (String gesture : new String[]{"Read", "ReadLoop", "Read", "Sweep", "Sweep", "Mix", "Mix"}) {
            String next = VillagerLifeVisuals.loopGesture(gesture);
            if (VillagerLifeVisuals.startsLoop(active, next)) starts++;
            active = next;
        }
        assertEquals(3, starts);
        assertNull(VillagerLifeVisuals.loopGesture("LookAround"));
        assertTrue(VillagerLifeVisuals.startsLoop(null, "ReadLoop"));
    }

    @Test void clonedLifeStateRetainsTheRunningLoop() {
        var life = new VillagerLifeState();
        life.activeLoopGesture = "Sweep";
        assertEquals("Sweep", ((VillagerLifeState) life.clone()).activeLoopGesture);
    }
}
