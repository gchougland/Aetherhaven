package com.hexvane.aetherhaven.production;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import static org.junit.jupiter.api.Assertions.*;
import static com.hexvane.aetherhaven.production.TimberHarvestPlan.*;

@Tag("town")
class TimberHarvestPlanTest {
    private static final String WOOD = "Wood_Beech_Trunk";
    private static final Position ROOT = new Position(10, 100, 20);
    private List<Cell> tree() {
        return new ArrayList<>(List.of(new Cell(ROOT, WOOD, 0, Kind.TIMBER),
            new Cell(new Position(10, 101, 20), WOOD, 0, Kind.TIMBER),
            new Cell(new Position(11, 101, 20), "Plant_Leaves_Beech", 1, Kind.FOLIAGE)));
    }
    private Map<Position, Observed> loaded(List<Cell> tree) {
        Map<Position, Observed> map = new HashMap<>();
        tree.forEach(c -> map.put(c.position(), new Observed(true, c.blockId(), c.rotation(), false)));
        return map;
    }

    @Test void timberComesOnlyFromExactTreeCellsAndPlanOwnsItsSnapshot() {
        var cells = tree(); var world = loaded(cells);
        // Adjacent timber from a building must not be included in a tree harvest.
        world.put(new Position(12, 101, 20), new Observed(true, WOOD, 0, false));
        var result = inspect(cells, WOOD, 2, 4, p -> true, world::get);
        assertTrue(result.ready()); assertEquals(4, result.plan().outputCount());
        assertEquals(WOOD, result.plan().output()); assertEquals(3, result.plan().cells().size());
        cells.clear(); assertEquals(3, result.plan().cells().size());
        assertThrows(UnsupportedOperationException.class, () -> result.plan().cells().clear());
        assertEquals(4, world.size(), "Planning must never mutate terrain");
    }

    @Test void capacityBoundsAndMalformedPlansRejectBeforeWorldReads() {
        var reads = new AtomicInteger(); var cells = tree();
        java.util.function.Function<Position, Observed> read = p -> { reads.incrementAndGet(); return null; };
        assertFalse(inspect(cells, WOOD, 2, 3, p -> true, read).ready());
        assertFalse(inspect(cells, WOOD, 1, 99, p -> p.equals(ROOT), read).ready());
        cells.add(cells.getFirst());
        assertFalse(inspect(cells, WOOD, 1, 99, p -> true, read).ready());
        assertEquals(0, reads.get());
        assertFalse(inspect(tree(), WOOD, Integer.MAX_VALUE, Long.MAX_VALUE, p -> true, read).ready());
        assertEquals(0, reads.get(), "Overflow must not make a harvest affordable");
    }

    @Test void partialTreesUnloadedCellsRotatedEditsAndProtectedCellsCannotPayOut() {
        var cells = tree();
        for (var changed : List.of(new Observed(false, WOOD, 0, false),
                new Observed(true, "Empty", 0, false), new Observed(true, WOOD, 1, false),
                new Observed(true, WOOD, 0, true))) {
            var world = loaded(cells); world.put(ROOT, changed);
            var result = inspect(cells, WOOD, 1, 99, p -> true, world::get);
            assertFalse(result.ready()); assertNull(result.plan()); assertNotNull(result.issue());
        }
    }

    @Test void preparedHarvestRechecksTerrainAndStorageWithoutChangingItsOriginalYield() {
        var cells = tree(); var world = loaded(cells);
        var plan = inspect(cells, WOOD, 2, 4, p -> true, world::get).plan();
        assertTrue(plan.recheck(4, p -> true, world::get).ready());
        assertFalse(plan.recheck(3, p -> true, world::get).ready());
        world.remove(ROOT);
        assertFalse(plan.recheck(4, p -> true, world::get).ready());
        assertEquals(4, plan.outputCount());
    }
}
