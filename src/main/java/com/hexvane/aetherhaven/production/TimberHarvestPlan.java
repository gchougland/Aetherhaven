package com.hexvane.aetherhaven.production;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Read-only preflight for one known tree prefab. Never flood-fills connected wood:
 * a nearby house or another tree is not part of the supplied growth footprint.
 * Runtime callers must recheck the plan immediately before changing blocks.
 */
public final class TimberHarvestPlan {
    public static final int MAX_TREE_CELLS = 16_384;
    public enum Kind { TIMBER, FOLIAGE }
    public record Position(int x, int y, int z) {}
    public record Cell(Position position, String blockId, int rotation, int filler, Kind kind, boolean nativeFarming) {
        public Cell(Position position, String blockId, int rotation, Kind kind) {
            this(position, blockId, rotation, 0, kind, false);
        }
        public Cell(Position position, String blockId, int rotation, int filler, Kind kind) {
            this(position, blockId, rotation, filler, kind, false);
        }
    }
    /** Declared native fillers must match exactly; unrelated block entities remain protected. */
    public record Observed(boolean loaded, String blockId, int rotation, int filler, boolean protectedCell) {
        public Observed(boolean loaded, String blockId, int rotation, boolean protectedCell) {
            this(loaded, blockId, rotation, 0, protectedCell);
        }
    }
    public record Inspection(TimberHarvestPlan plan, String issue) {
        public boolean ready() { return plan != null && issue == null; }
    }

    private final List<Cell> cells;
    private final String output;
    private final int outputCount;

    private TimberHarvestPlan(List<Cell> cells, String output, int outputCount) {
        this.cells = List.copyOf(cells);
        this.output = output;
        this.outputCount = outputCount;
    }
    public List<Cell> cells() { return cells; }
    public String output() { return output; }
    public int outputCount() { return outputCount; }

    /**
     * expected must come from the selected native growth prefab, not a world scan.
     * Growth selection and maturity are separate prerequisites. Capacity includes
     * existing stock and reservations. Missing or edited cells never award timber.
     */
    public static Inspection inspect(List<Cell> expected, String output, int timberPerCell, long freeStorage,
            Predicate<Position> withinGrove, Function<Position, Observed> world) {
        if (expected == null || expected.isEmpty() || expected.size() > MAX_TREE_CELLS
                || output == null || output.isBlank() || timberPerCell < 1)
            return rejected("This tree has no supported harvest plan.");
        Set<Position> unique = new HashSet<>();
        long timber = 0;
        // Validate the complete footprint before touching world state. A malformed
        // or out of bounds prefab cannot trigger reads beyond its assigned grove.
        for (Cell cell : expected) {
            if (cell == null || cell.position() == null || cell.blockId() == null || cell.blockId().isBlank()
                    || cell.kind() == null || cell.rotation() < 0 || !unique.add(cell.position()))
                return rejected("This tree has an invalid harvest plan.");
            if (!withinGrove.test(cell.position())) return rejected("This tree reaches outside its grove.");
            if (cell.kind() == Kind.TIMBER && cell.filler() == 0) timber += timberPerCell;
        }
        if (timber < 1 || timber > Integer.MAX_VALUE) return rejected("This tree has no supported timber yield.");
        if (freeStorage < timber) return rejected("Make room in timber storage before harvesting this tree.");
        for (Cell cell : expected) {
            Observed current = world.apply(cell.position());
            if (current == null || !current.loaded()) return rejected("Part of this tree is outside the active area.");
            if (current.protectedCell()) return rejected("A protected block occupies part of this tree.");
            if (!cell.blockId().equals(current.blockId()) || cell.rotation() != current.rotation()
                    || cell.filler() != current.filler())
                return rejected("This tree has changed. Check its growing space.");
        }
        return new Inspection(new TimberHarvestPlan(expected, output, (int) timber), null);
    }

    public Inspection recheck(long freeStorage, Predicate<Position> withinGrove,
            Function<Position, Observed> world) {
        int trunks = (int) cells.stream().filter(c -> c.kind() == Kind.TIMBER && c.filler() == 0).count();
        return inspect(cells, output, outputCount / trunks, freeStorage, withinGrove, world);
    }
    private static Inspection rejected(String issue) { return new Inspection(null, issue); }
}
