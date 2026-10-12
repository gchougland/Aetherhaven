package com.hexvane.aetherhaven.production;

import java.util.*;
import java.util.function.Predicate;
import static com.hexvane.aetherhaven.production.TimberHarvestPlan.*;

/**
 * Saved predictions for a freshly planted native sapling. Native growth removes
 * its FarmingBlock at maturity; this record retains the exact selected geometry.
 * It is evidence of planned geometry, not proof that planting or growth occurred.
 */
public record TimberPlantingReceipt(UUID id, String sapling, String output, Position root,
        int initialGeneration, List<Stage> stages, Set<Position> clearance) {
    public TimberPlantingReceipt(UUID id, String sapling, String output, Position root, int initialGeneration, List<Stage> stages) {
        this(id, sapling, output, root, initialGeneration, stages, Set.of());
    }
    public record Stage(int index, String source, List<Cell> cells) {
        public Stage { cells = cells == null ? List.of() : List.copyOf(cells); }
    }
    public TimberPlantingReceipt {
        stages = stages == null ? List.of() : List.copyOf(stages);
        clearance = clearance == null ? Set.of() : Set.copyOf(clearance);
    }

    public String issue(Predicate<Position> withinGrove) {
        if (id == null || root == null || sapling == null || sapling.isBlank() || output == null || output.isBlank()
                || initialGeneration < 0 || stages.isEmpty() || stages.size() > 32)
            return "This planting has an invalid tree record.";
        if (!withinGrove.test(root)) return "The tree root must be inside its grove.";
        Set<Position> footprint = new HashSet<>(clearance);
        if (footprint.size() > MAX_TREE_CELLS) return "This tree needs too much growing space.";
        for (Position p : clearance) if (!withinGrove.test(p)) return "A tree part would reach outside its grove.";
        for (int index = 0; index < stages.size(); index++) {
            Stage stage = stages.get(index);
            if (stage == null || stage.index() != index || stage.cells().isEmpty()
                    || stage.cells().size() > MAX_TREE_CELLS || stage.source() == null || stage.source().isBlank())
                return "This planting has an invalid growth stage.";
            Set<Position> unique = new HashSet<>();
            for (Cell cell : stage.cells()) {
                if (cell == null || cell.position() == null || cell.blockId() == null || cell.blockId().isBlank()
                        || cell.kind() == null || cell.rotation() < 0 || !unique.add(cell.position()))
                    return "This planting has an invalid tree cell.";
                if (!withinGrove.test(cell.position())) return "A growing tree would reach outside its grove.";
                footprint.add(cell.position());
                if (footprint.size() > MAX_TREE_CELLS) return "This tree needs too much growing space.";
            }
            // Some native mature prefabs (including Ash) leave the planting
            // origin empty. Its saved identity must outlive that origin block.
            if (index == 0 && !unique.contains(root)) return "The planted sapling needs its original root.";
        }
        if (mature().cells().stream().noneMatch(c -> c.kind() == Kind.TIMBER && c.filler() == 0))
            return "This tree has no supported timber yield.";
        return null;
    }

    /**
     * Whether two predictions describe the same tree: the same stages from the same prefabs, with the same cells and
     * the same clearance. The cells are compared as sets. The engine hands a prefab's columns over in hash table
     * order, and that order is different once it has reloaded the prefab from its binary cache, so two predictions
     * of an unchanged tree can list the same cells in a different order.
     */
    public boolean sameGrowth(TimberPlantingReceipt other) {
        if (other == null || stages.size() != other.stages.size() || !clearance.equals(other.clearance)) return false;
        for (int index = 0; index < stages.size(); index++) {
            Stage mine = stages.get(index), theirs = other.stages.get(index);
            if (mine == null || theirs == null || mine.index() != theirs.index()
                    || !Objects.equals(mine.source(), theirs.source()) || mine.cells().size() != theirs.cells().size()
                    || !new HashSet<>(mine.cells()).equals(new HashSet<>(theirs.cells()))) return false;
        }
        return true;
    }

    /** Includes underground roots and intermediate canopies, not just the final tree. */
    public Set<Position> footprint() {
        Set<Position> result = new LinkedHashSet<>(clearance);
        for (Stage stage : stages) for (Cell cell : stage.cells()) result.add(cell.position());
        return Collections.unmodifiableSet(result);
    }
    public Stage mature() { return stages.getLast(); }
}
