package com.hexvane.aetherhaven.production;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import static com.hexvane.aetherhaven.production.TimberHarvestPlan.*;

/** Uses the same complete native growth footprint and obstruction rules as planting. */
public final class TimberGrowingSpace {
    private TimberGrowingSpace() {}
    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {}
    public static Set<Position> cells(TimberPlantingReceipt tree) {
        var cells = new LinkedHashSet<>(tree.footprint());
        cells.add(new Position(tree.root().x(), tree.root().y() - 1, tree.root().z()));
        return cells;
    }
    public static Box bounds(TimberPlantingReceipt tree) {
        var cells = cells(tree);
        return new Box(cells.stream().mapToInt(Position::x).min().orElseThrow(),
            cells.stream().mapToInt(Position::y).min().orElseThrow(), cells.stream().mapToInt(Position::z).min().orElseThrow(),
            cells.stream().mapToInt(Position::x).max().orElseThrow() + 1.0,
            cells.stream().mapToInt(Position::y).max().orElseThrow() + 1.0,
            cells.stream().mapToInt(Position::z).max().orElseThrow() + 1.0);
    }
    /** The tint sits outside each face, so an opaque full block cannot conceal it. */
    public static Box marker(Position p) {
        return new Box(p.x() - .035, p.y() - .035, p.z() - .035, p.x() + 1.035, p.y() + 1.035, p.z() + 1.035);
    }
    static Position fillerAnchor(Position p, int filler) {
        return new Position(p.x()-FillerBlockUtil.unpackX(filler),p.y()-FillerBlockUtil.unpackY(filler),p.z()-FillerBlockUtil.unpackZ(filler));
    }
    public static Set<Position> obstructions(TimberPlantingReceipt tree, boolean planted,
            Predicate<Position> insideTown, Function<Position, TimberProductionState.Ground> read, Set<Position> reserved) {
        var own = new HashMap<Position, List<Cell>>();
        if (planted) for (var stage : tree.stages()) for (var cell : stage.cells())
            own.computeIfAbsent(cell.position(), ignored -> new ArrayList<>()).add(cell);
        var result = new LinkedHashSet<Position>();
        var below = new Position(tree.root().x(), tree.root().y() - 1, tree.root().z());
        var footprint=cells(tree);
        for (var p : footprint) {
            if (!insideTown.test(p) || reserved.contains(p)) { result.add(p); continue; }
            var ground = read.apply(p);
            // Native tree geometry is expected, not an obstacle to its own next growth stage.
            if (ground != null && ground.loaded() && own.getOrDefault(p, List.of()).stream().anyMatch(c ->
                    c.blockId().equals(ground.block()) && c.rotation() == ground.rotation() && c.filler() == ground.filler())) continue;
            if (ground != null && ground.loaded() && ground.filler()!=0 && tree.clearance().contains(p)
                    && own.getOrDefault(fillerAnchor(p,ground.filler()),List.of()).stream().anyMatch(c ->
                        c.filler()==0 && c.blockId().equals(ground.block()) && c.rotation()==ground.rotation())) continue;
            if (planted && p.equals(below) && ground != null && ground.loaded() && ground.empty()) continue;
            if (TimberProductionState.groundIssue(p, tree.root(), below, ground) != null
                    || TimberVegetation.issue(p,ground,insideTown,read)!=null) result.add(p);
        }
        return result;
    }
}
