package com.hexvane.aetherhaven.testtown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
class TestTownGridTest {
    private static final int STREET = 8;
    private static final int PLAZA = 12;

    private static List<TestTownGrid.Item> mixed(int count) {
        List<TestTownGrid.Item> items = new ArrayList<>();
        for (int i = 0; i < count; i++) items.add(new TestTownGrid.Item("item" + i, 7 + (i * 5) % 23, 6 + (i * 7) % 19));
        return items;
    }

    @Test
    void everyItemIsPlacedOnceWithItsOwnSize() {
        var items = mixed(40);
        var plan = TestTownGrid.pack(items, 100, -40, 200, STREET, PLAZA);
        assertEquals(items.size(), plan.cells().size());
        for (var item : items) {
            var cell = plan.find(item.key());
            assertNotNull(cell, item.key());
            assertEquals(item.width(), cell.maxX() - cell.minX() + 1);
            assertEquals(item.depth(), cell.maxZ() - cell.minZ() + 1);
        }
    }

    @Test
    void neighboursKeepAStreetBetweenThemAndNothingOverlaps() {
        var plan = TestTownGrid.pack(mixed(60), 0, 0, 180, STREET, PLAZA);
        var cells = plan.cells();
        for (int a = 0; a < cells.size(); a++)
            for (int b = a + 1; b < cells.size(); b++)
                assertFalse(TestTownGrid.tooClose(cells.get(a), cells.get(b), STREET - 1),
                    cells.get(a) + " crowds " + cells.get(b));
    }

    @Test
    void theCharterPlazaStaysClear() {
        var plan = TestTownGrid.pack(mixed(30), 10, 20, 160, STREET, PLAZA);
        for (var cell : plan.cells())
            assertTrue(cell.minZ() > 20 + PLAZA || cell.maxZ() < 20 - PLAZA, cell + " is on the plaza");
    }

    @Test
    void rowsAlternateSidesAndFrontsLineUpOnTheStreet() {
        var plan = TestTownGrid.pack(mixed(30), 0, 0, 120, STREET, PLAZA);
        int rows = plan.cells().stream().mapToInt(TestTownGrid.Cell::row).max().orElseThrow() + 1;
        assertTrue(rows >= 4, "expected several rows, got " + rows);
        for (int row = 0; row < rows; row++) {
            int index = row;
            var inRow = plan.cells().stream().filter(c -> c.row() == index).toList();
            assertFalse(inRow.isEmpty());
            assertTrue(inRow.stream().allMatch(c -> c.south() == (index % 2 == 0)));
            assertEquals(1, inRow.stream().mapToInt(TestTownGrid.Cell::frontZ).distinct().count(), "row " + row);
            int width = inRow.getLast().maxX() - inRow.getFirst().minX() + 1;
            assertTrue(width <= 120, "row " + row + " is " + width + " wide");
        }
        // The first row on each side starts right after the plaza.
        assertEquals(PLAZA + 1, plan.cells().stream().filter(c -> c.row() == 0).findFirst().orElseThrow().frontZ());
        assertEquals(-PLAZA - 1, plan.cells().stream().filter(c -> c.row() == 1).findFirst().orElseThrow().frontZ());
    }

    @Test
    void rowsAreCentredOnTheCharterAndBoundsCoverEverything() {
        var plan = TestTownGrid.pack(mixed(45), 500, -300, 200, STREET, PLAZA);
        for (var cell : plan.cells()) {
            assertTrue(cell.minX() >= plan.minX() && cell.maxX() <= plan.maxX());
            assertTrue(cell.minZ() >= plan.minZ() && cell.maxZ() <= plan.maxZ());
        }
        assertTrue(Math.abs((plan.minX() + plan.maxX()) / 2 - 500) <= 16, "rows drift off the charter");
        assertTrue(plan.minX() >= 500 - 101 && plan.maxX() <= 500 + 101);
    }

    @Test
    void streetsRunBetweenRowsAndTheFirstIsThePlaza() {
        var plan = TestTownGrid.pack(mixed(30), 0, 0, 120, STREET, PLAZA);
        assertEquals(0, plan.streets().getFirst().centerZ());
        for (var street : plan.streets())
            for (var cell : plan.cells())
                assertTrue(street.centerZ() < cell.minZ() || street.centerZ() > cell.maxZ()
                        || street.maxX() < cell.minX() || street.minX() > cell.maxX(),
                    "street at z=" + street.centerZ() + " runs through " + cell);
    }

    @Test
    void anItemWiderThanARowStillGetsARowOfItsOwn() {
        var plan = TestTownGrid.pack(List.of(new TestTownGrid.Item("small", 5, 5), new TestTownGrid.Item("huge", 90, 10),
            new TestTownGrid.Item("after", 5, 5)), 0, 0, 40, STREET, PLAZA);
        assertEquals(0, plan.find("small").row());
        assertEquals(1, plan.find("huge").row());
        assertEquals(2, plan.find("after").row());
    }

    @Test
    void badSettingsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> TestTownGrid.pack(mixed(2), 0, 0, 0, STREET, PLAZA));
        assertThrows(IllegalArgumentException.class,
            () -> TestTownGrid.pack(List.of(new TestTownGrid.Item("flat", 0, 4)), 0, 0, 50, STREET, PLAZA));
    }
}
