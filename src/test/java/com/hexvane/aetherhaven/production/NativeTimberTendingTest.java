package com.hexvane.aetherhaven.production;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class NativeTimberTendingTest {
    @Test void attendedGrowthCannotSkipTheNativeTransitionEvenWithLargeOrLaggedSteps() {
        assertEquals(.01f,NativeTimberTending.assistedProgress(0,5,1,500),.00001);
        assertEquals(Math.nextDown(1f),NativeTimberTending.assistedProgress(.99f,5,20,1));
        assertEquals(Math.nextDown(3f),NativeTimberTending.assistedProgress(2.99f,5,20,1));
        assertEquals(5,NativeTimberTending.assistedProgress(5,5,1,30));
    }
    @Test void missingAttendanceOrInvalidInputAddsNoGrowth() {
        for(double seconds:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY})assertEquals(1.25f,NativeTimberTending.assistedProgress(1.25f,5,seconds,30));
        assertEquals(1.25f,NativeTimberTending.assistedProgress(1.25f,5,1,0));
        assertEquals(1.25f,NativeTimberTending.assistedProgress(1.25f,5,1,Double.NaN));
        assertEquals(1.25f,NativeTimberTending.assistedProgress(1.25f,0,1,30));
    }
}
