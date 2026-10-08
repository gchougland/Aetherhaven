package com.hexvane.aetherhaven.workplace;

import static org.junit.jupiter.api.Assertions.*;

import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import java.util.HashSet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@Tag("town")
class MineshaftSelectionTest {
    @ParameterizedTest
    @CsvSource({
        "NORTH, SOUTH, 98, 194, 103, 203",
        "EAST, WEST, 98, 198, 107, 203",
        "SOUTH, NORTH, 98, 198, 103, 207",
        "WEST, EAST, 94, 198, 103, 203"
    })
    void cardinalSelectionsKeepTheMarkerAtTheLastApproachRow(MineshaftSelection.Direction direction,
            ExcavationPlan.Entrance entrance, int minX, int minZ, int maxX, int maxZ) {
        var area = MineshaftSelection.area(100, 70, 200, direction, 6, ExcavationPlan.Layout.STRAIGHT);
        assertEquals(new WorkplaceArea(minX, 71, minZ, maxX, maxZ), area);
        assertEquals(entrance, MineshaftSelection.entrance(direction));
        assertTrue(area.valid());
    }

    @Test
    void everyDirectionAndLayoutMatchesExistingTunnelGeometryAtAllLengths() {
        int x = -100, groundY = 70, z = -200;
        for (var direction : MineshaftSelection.Direction.values()) {
            int dx = switch (direction) { case EAST -> 1; case WEST -> -1; default -> 0; };
            int dz = switch (direction) { case SOUTH -> 1; case NORTH -> -1; default -> 0; };
            for (var layout : new ExcavationPlan.Layout[]{ExcavationPlan.Layout.STRAIGHT,ExcavationPlan.Layout.BRANCHES,ExcavationPlan.Layout.STAIRS_UP,ExcavationPlan.Layout.STAIRS_DOWN}) {
                for (int length = 3; length <= MineshaftSelection.maximumLength(layout); length++) {
                    var area = MineshaftSelection.area(x, groundY, z, direction, length, layout);
                    var entrance = MineshaftSelection.entrance(direction);
                    assertEquals(direction, MineshaftSelection.direction(entrance));
                    assertEquals(new Point(x, groundY, z), MineshaftSelection.ground(area, entrance, layout));
                    var cells = ExcavationPlan.tunnel(area, entrance, layout);
                    assertTrue(area.valid());
                    int faceSize = 3 * ExcavationPlan.passageHeight(area, layout);
                    assertEquals(length * faceSize + (layout == ExcavationPlan.Layout.BRANCHES ? 36 : 0) - (faceSize==12?6:0), cells.size());
                    assertEquals(cells.size(), new HashSet<>(cells).size());
                    assertTrue(cells.stream().allMatch(p -> area.contains(p.x(), p.y(), p.z())));
                    // The original tunnel order contains all main faces before its branch arms.
                    for (int row = 1; row <= length; row++) {
                        int first = (row - 1) * faceSize - (faceSize==12?3*Math.min(row-1,2):0);
                        int rowSize = faceSize==12&&row<=2?9:faceSize;
                        for (var cell : cells.subList(first, first + rowSize)) {
                            assertEquals(row, (cell.x() - x) * dx + (cell.z() - z) * dz);
                            assertTrue(Math.abs((cell.x() - x) * dz - (cell.z() - z) * dx) <= 1);
                        }
                    }
                    // Three clear rows end at the marker; none is scheduled for excavation.
                    for (int back = 0; back <= 2; back++) {
                        var approach = new Point(x - dx * back, groundY + 1, z - dz * back);
                        assertTrue(area.contains(approach.x(), approach.y(), approach.z()));
                        assertEquals(groundY + 1, ExcavationPlan.floorAt(area, entrance, layout, approach));
                        assertFalse(cells.contains(approach));
                    }
                }
            }
        }
    }

    @Test
    void stairsBeginAtTheSelectedGroundThenChangeByOneBlockEveryFourRows() {
        for (var layout : new ExcavationPlan.Layout[]{ExcavationPlan.Layout.STAIRS_UP, ExcavationPlan.Layout.STAIRS_DOWN}) {
            var area = MineshaftSelection.area(-20, 99, -30, MineshaftSelection.Direction.SOUTH, 12, layout);
            var entrance = MineshaftSelection.entrance(MineshaftSelection.Direction.SOUTH);
            assertEquals(layout == ExcavationPlan.Layout.STAIRS_DOWN ? 98 : 100, area.floorY());
            var cells = ExcavationPlan.tunnel(area, entrance, layout);
            for (int row = 1; row <= 12; row++) {
                int rise = Math.min(2, (row - 1) / 4);
                int floor = 100 + (layout == ExcavationPlan.Layout.STAIRS_DOWN ? -rise : rise);
                int first = (row - 1) * 12 - 3*Math.min(row-1,2);
                int rowSize = row<=2?9:12;
                assertEquals(floor, cells.subList(first, first + rowSize).stream().mapToInt(Point::y).min().orElseThrow());
                assertEquals(floor + (row<=2?2:3), cells.subList(first, first + rowSize).stream().mapToInt(Point::y).max().orElseThrow());
            }
        }
    }

    @Test
    void invalidLengthsAndMissingChoicesAreRejectedWithoutSilentClamping() {
        assertEquals(3, MineshaftSelection.MINIMUM_LENGTH);
        assertEquals(3, MineshaftSelection.minimumLength());
        for (var layout : ExcavationPlan.Layout.values()) {
            assertEquals(layout == ExcavationPlan.Layout.BRANCHES ? 16 : 32, MineshaftSelection.maximumLength(layout));
            for (int length : new int[]{Integer.MIN_VALUE, 0, 2, MineshaftSelection.maximumLength(layout) + 1, Integer.MAX_VALUE}) {
                assertThrows(IllegalArgumentException.class, () -> MineshaftSelection.area(0, 80, 0,
                    MineshaftSelection.Direction.NORTH, length, layout));
            }
        }
        assertThrows(IllegalArgumentException.class, () -> MineshaftSelection.entrance(null));
        assertThrows(IllegalArgumentException.class, () -> MineshaftSelection.direction(null));
        assertThrows(IllegalArgumentException.class, () -> MineshaftSelection.maximumLength(null));
        assertThrows(IllegalArgumentException.class, () -> MineshaftSelection.area(0, 80, 0, null, 3, ExcavationPlan.Layout.STRAIGHT));
        assertThrows(IllegalArgumentException.class, () -> MineshaftSelection.area(0, 80, 0, MineshaftSelection.Direction.NORTH, 3, null));
    }

    @Test
    void inverseGroundUsesLegacyWidthAndRejectsMissingOrInvalidAreas() {
        var legacy = new WorkplaceArea(-20, 100, -30, -12, -10);
        assertEquals(new Point(-17, 99, -28), MineshaftSelection.ground(legacy,
            ExcavationPlan.Entrance.NORTH, ExcavationPlan.Layout.STRAIGHT));
        assertEquals(new Point(-17, 101, -13), MineshaftSelection.ground(legacy,
            ExcavationPlan.Entrance.SOUTH, ExcavationPlan.Layout.STAIRS_DOWN));
        assertThrows(IllegalArgumentException.class, () -> MineshaftSelection.ground(null,
            ExcavationPlan.Entrance.NORTH, ExcavationPlan.Layout.STRAIGHT));
        assertThrows(IllegalArgumentException.class, () -> MineshaftSelection.ground(legacy,
            null, ExcavationPlan.Layout.STRAIGHT));
        assertThrows(IllegalArgumentException.class, () -> MineshaftSelection.ground(legacy,
            ExcavationPlan.Entrance.NORTH, null));
        assertThrows(IllegalArgumentException.class, () -> MineshaftSelection.ground(
            new WorkplaceArea(0, 100, 0, 5, 2), ExcavationPlan.Entrance.NORTH, ExcavationPlan.Layout.STRAIGHT));
        assertThrows(IllegalArgumentException.class, () -> MineshaftSelection.ground(
            new WorkplaceArea(Integer.MIN_VALUE, 100, 0, Integer.MAX_VALUE, 6),
            ExcavationPlan.Entrance.NORTH, ExcavationPlan.Layout.STRAIGHT));
    }

    @Test
    void worldHeightBandRejectsOverflowAndKeepsBothStairExtremesInsideTheWorld() {
        for (var layout : ExcavationPlan.Layout.values()) {
            int low = layout == ExcavationPlan.Layout.DEEP_STAIRS_DOWN ? 6 : layout == ExcavationPlan.Layout.STAIRS_DOWN ? 1 : 0;
            int high = layout == ExcavationPlan.Layout.DEEP_STAIRS_DOWN ? 315 : layout == ExcavationPlan.Layout.DEEP_STAIRS_UP ? 308 : layout == ExcavationPlan.Layout.STAIRS_DOWN ? 315 : layout == ExcavationPlan.Layout.STAIRS_UP ? 313 : 314;
            for (int y : new int[]{low, high}) {
                var area = MineshaftSelection.area(0, y, 0, MineshaftSelection.Direction.SOUTH, 12, layout);
                assertTrue(area.valid());
                assertTrue(ExcavationPlan.tunnel(area, ExcavationPlan.Entrance.NORTH, layout).stream()
                    .allMatch(p -> p.y() >= 0 && p.y() < 320));
            }
            for (int y : new int[]{Integer.MIN_VALUE, -1, low - 1, high + 1, Integer.MAX_VALUE}) {
                assertThrows(IllegalArgumentException.class, () -> MineshaftSelection.area(0, y, 0,
                    MineshaftSelection.Direction.SOUTH, 12, layout));
            }
        }
    }

    @Test
    void extremeCoordinatesCannotWrapButRepresentableBoundarySelectionsRemainValid() {
        for (var direction : MineshaftSelection.Direction.values()) {
            for (var layout : ExcavationPlan.Layout.values()) {
                int length = MineshaftSelection.maximumLength(layout);
                for (int edge : new int[]{Integer.MIN_VALUE, Integer.MAX_VALUE}) {
                    assertThrows(IllegalArgumentException.class, () -> MineshaftSelection.area(edge, 80, 0, direction, length, layout));
                    assertThrows(IllegalArgumentException.class, () -> MineshaftSelection.area(0, 80, edge, direction, length, layout));
                }
                for (int edge : new int[]{Integer.MIN_VALUE + 40, Integer.MAX_VALUE - 40}) {
                    var area = MineshaftSelection.area(edge, 80, edge, direction, length, layout);
                    assertEquals(new Point(edge, 80, edge), MineshaftSelection.ground(area,
                        MineshaftSelection.entrance(direction), layout));
                    assertTrue(area.valid());
                    assertTrue(ExcavationPlan.tunnel(area, MineshaftSelection.entrance(direction), layout).stream()
                        .allMatch(p -> area.contains(p.x(), p.y(), p.z())));
                }
            }
        }
    }
}
