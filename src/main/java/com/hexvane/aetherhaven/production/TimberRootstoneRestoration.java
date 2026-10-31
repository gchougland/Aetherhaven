package com.hexvane.aetherhaven.production;

import java.util.function.BiPredicate;
import java.util.function.Function;
import static com.hexvane.aetherhaven.production.TimberHarvestPlan.*;

/** Returns only the stone consumed by native roots, without touching the standing tree or player edits. */
public final class TimberRootstoneRestoration {
    private TimberRootstoneRestoration() {}

    public static String restore(TimberProductionState.Planting planting, Function<Position, Observed> read,
            BiPredicate<Position, Observed> write) {
        if (planting == null) return null;
        var root = planting.tree().root();
        var below = new Position(root.x(), root.y() - 1, root.z());
        var original = planting.originals().stream().filter(o -> o.position().equals(below)).findFirst().orElse(null);
        // Old grove plans could grow on ordinary soil. They never paid for a Rootstone.
        if (original == null || !Rootstone.is(original.block())) return null;
        var current = read.apply(below);
        if (current == null || !current.loaded()) return "Move closer so the Rootstone can be restored before uprooting.";
        if (Rootstone.is(current.blockId())) return null;
        boolean treeCell = planting.tree().stages().stream().flatMap(s -> s.cells().stream())
            .anyMatch(c -> c.position().equals(below) && c.blockId().equals(current.blockId())
                && c.rotation() == current.rotation() && c.filler() == current.filler());
        if(!treeCell && current.filler()!=0 && planting.tree().clearance().contains(below)) {
            var anchor=TimberGrowingSpace.fillerAnchor(below,current.filler());
            treeCell=planting.tree().stages().stream().flatMap(s->s.cells().stream()).anyMatch(c ->
                c.position().equals(anchor)&&c.filler()==0&&c.blockId().equals(current.blockId())&&c.rotation()==current.rotation());
        }
        if (current.protectedCell() || !(treeCell || "Empty".equals(current.blockId()) && current.filler() == 0))
            return "A block is covering this tree's Rootstone. Clear the block beneath the tree before uprooting.";
        var target = new Observed(true, original.block(), original.rotation(), 0, false);
        return write.test(below, target) ? null : "The Rootstone could not be restored. Try uprooting again.";
    }
}
