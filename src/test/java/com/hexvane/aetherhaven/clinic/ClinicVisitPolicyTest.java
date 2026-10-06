package com.hexvane.aetherhaven.clinic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import static org.junit.jupiter.api.Assertions.*;

@Tag("autonomy")
class ClinicVisitPolicyTest {
    @Test void ordinaryActivitiesCanAccumulateQuietTimeButDamageAndTimeJumpsCannot() {
        double quiet=0;
        for(int i=0;i<120;i++)quiet=ClinicVisitPolicy.advanceQuiet(quiet,.25,false);
        assertTrue(ClinicVisitPolicy.needsVisit(120,200,false,quiet));
        assertEquals(30,ClinicVisitPolicy.advanceQuiet(quiet,10,false));
        quiet=ClinicVisitPolicy.advanceQuiet(quiet,.25,true);
        assertEquals(0,quiet);
        assertFalse(ClinicVisitPolicy.needsVisit(120,200,false,ClinicVisitPolicy.advanceQuiet(quiet,86400,false)));
        assertEquals(0,ClinicVisitPolicy.advanceQuiet(20,Double.NaN,false));
    }
    @Test void injuryNeedsQuietTimeAndDoesNotSendMinorScratchesAwayFromWork() {
        assertFalse(ClinicVisitPolicy.needsVisit(60,100,false,29.9));
        assertTrue(ClinicVisitPolicy.needsVisit(75,100,false,30));
        assertFalse(ClinicVisitPolicy.needsVisit(76,100,false,30));
        assertFalse(ClinicVisitPolicy.needsVisit(12,20,false,30));
        assertTrue(ClinicVisitPolicy.needsVisit(10,20,false,30));
    }
    @Test void admittedPatientFinishesRecoveryWithoutThresholdOscillation() {
        assertTrue(ClinicVisitPolicy.needsVisit(98,100,true,40));
        assertFalse(ClinicVisitPolicy.needsVisit(99.5f,100,true,40));
        assertFalse(ClinicVisitPolicy.needsVisit(100,100,true,40));
        assertFalse(ClinicVisitPolicy.needsVisit(90,100,true,0));
    }
    @Test void deadAndInvalidHealthNeverRequestCare() {
        for(float hp:new float[]{0,-1,Float.NaN,Float.POSITIVE_INFINITY})
            assertFalse(ClinicVisitPolicy.needsVisit(hp,100,true,50));
        assertFalse(ClinicVisitPolicy.needsVisit(5,Float.NaN,false,50));
        assertFalse(ClinicVisitPolicy.needsVisit(5,0,false,50));
        assertFalse(ClinicVisitPolicy.needsVisit(5,100,false,Double.NaN));
        assertFalse(ClinicVisitPolicy.needsVisit(5,100,false,Double.POSITIVE_INFINITY));
    }
}
