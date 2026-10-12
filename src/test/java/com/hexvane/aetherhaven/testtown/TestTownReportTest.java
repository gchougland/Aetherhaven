package com.hexvane.aetherhaven.testtown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
class TestTownReportTest {
    @Test
    void theSummaryCountsEverythingThatWasMade() {
        TestTownReport report = new TestTownReport();
        report.add(TestTownReport.BUILDINGS, 53);
        report.add(TestTownReport.VILLAGERS, 27);
        for (int i = 0; i < 16; i++) report.add(TestTownReport.WORKPLACES);
        report.add(TestTownReport.TOWNSFOLK, 21);
        report.add(TestTownReport.ASSIGNED, 16);
        report.add(TestTownReport.COURIERS, 2);
        report.add(TestTownReport.GUARDS, 2);
        report.add(TestTownReport.CLERKS);
        report.set(TestTownReport.SITES, 1);
        report.set(TestTownReport.ORDERS, 17);
        assertEquals("Test town built in world test_world: 53 buildings, 1 building site, 27 core villagers, 16 workplaces, "
            + "21 townsfolk hired, 16 with a workplace job, 2 couriers, 2 guards, 1 shop clerk, 0 production buildings set up, "
            + "17 orders, 0 kinds of stock, 0 paths, 0 skipped.", report.summary("test_world"));
        assertEquals(16, report.count(TestTownReport.WORKPLACES));
    }

    @Test
    void settingACountReplacesWhatWasAdded() {
        TestTownReport report = new TestTownReport();
        report.add(TestTownReport.STOCK, 37);
        report.set(TestTownReport.STOCK, 42);
        assertEquals(42, report.count(TestTownReport.STOCK));
        assertTrue(report.summary("w").contains("42 kinds of stock"));
    }

    @Test
    void aTownThatWasAlreadyThereIsFoundNotBuilt() {
        TestTownReport report = new TestTownReport();
        assertFalse(report.existing());
        report.markExisting();
        assertTrue(report.existing());
        assertTrue(report.summary("w").startsWith("Test town found in world w: "));
    }

    @Test
    void aTownThatCouldNotBeFoundedSaysSo() {
        TestTownReport report = new TestTownReport();
        report.skip("founding the town", "the charter could not be placed");
        report.markFailed();
        assertTrue(report.failed());
        assertTrue(report.summary("w").startsWith("Test town NOT built in world w: "));
        assertTrue(report.summary("w").endsWith("1 skipped."));
    }

    @Test
    void everySkipIsListedWithItsReasonAndTheSummaryComesLast() {
        TestTownReport report = new TestTownReport();
        report.skip("building plot_missing", "this game has no building with that id");
        report.skip("workplace jewelry", null);
        report.note("Town Level 6 with 48 residents.");
        List<String> lines = report.lines("w");
        assertEquals(List.of(
            "Test town skipped building plot_missing: this game has no building with that id",
            "Test town skipped workplace jewelry: no reason given",
            "Test town note: Town Level 6 with 48 residents.",
            report.summary("w")), lines);
        assertTrue(lines.getLast().endsWith("2 skipped."));
        assertEquals(2, report.skipped().size());
    }
}
