package com.hexvane.aetherhaven.worldnpc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("worldnpc")
final class WorldNpcStationRecordTest {
    @Test
    void sameStartAndEndMeansAlwaysActive() {
        WorldNpcStationRecord station = new WorldNpcStationRecord();
        station.setTimeWindow(100, 100);
        assertTrue(station.isActiveAtSecondOfDay(0));
        assertTrue(station.isActiveAtSecondOfDay(43200));
    }

    @Test
    void wrapsPastMidnight() {
        WorldNpcStationRecord station = new WorldNpcStationRecord();
        station.setTimeWindow(80000, 1000);
        assertTrue(station.isActiveAtSecondOfDay(81000));
        assertTrue(station.isActiveAtSecondOfDay(500));
        assertFalse(station.isActiveAtSecondOfDay(12000));
    }

    @Test
    void normalDayWindow() {
        WorldNpcStationRecord station = new WorldNpcStationRecord();
        station.setTimeWindow(1000, 2000);
        assertTrue(station.isActiveAtSecondOfDay(1500));
        assertFalse(station.isActiveAtSecondOfDay(500));
        assertFalse(station.isActiveAtSecondOfDay(2000));
    }
}
