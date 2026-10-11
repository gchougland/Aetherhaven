package com.hexvane.aetherhaven.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkplaceBatchTimingsTest {
    @Test
    void stagesAreTimedSeparatelyAndReportedAsAverages() {
        var totals = new WorkplaceBatchTimings.Totals();
        String report = null;
        for (int i = 0; i < WorkplaceBatchTimings.REPORT_EVERY; i++) {
            assertNull(report, "No report before enough batches have finished");
            var batch = new WorkplaceBatchTimings.Tracked(UUID.randomUUID(), 0);
            batch.add(WarehouseBatch.State.AWAITING_PICKUP, false, 4000);
            batch.add(WarehouseBatch.State.IN_TRANSIT, false, 10000);
            batch.add(WarehouseBatch.State.READY, false, 2000);
            batch.add(WarehouseBatch.State.READY, true, 6000);
            assertEquals(2000, batch.awaitingWorker);
            assertEquals(6000, batch.working);
            report = WorkplaceBatchTimings.record(totals, batch);
        }
        assertNotNull(report);
        assertTrue(report.contains("4.0 s waiting for a courier"), report);
        assertTrue(report.contains("10.0 s being carried"), report);
        assertTrue(report.contains("2.0 s waiting for a worker"), report);
        assertTrue(report.contains("6.0 s being worked"), report);
        assertEquals(0, totals.batches, "The next report starts from nothing");
    }
}
