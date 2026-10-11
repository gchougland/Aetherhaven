package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import java.util.HashSet;
import java.util.Set;

/** Tunnels reserve their excavated passage and supporting floor, not the enclosing work band. */
final class WorkplaceOverlap {
    private WorkplaceOverlap() {}

    static boolean workplace(WorkplaceArea area, ApprenticeWorkplace existing) {
        return overlaps(new Volume(area, null), saved(existing));
    }

    static boolean tunnel(ApprenticeWorkplace draft, ApprenticeWorkplace existing) {
        return overlaps(passage(draft.area(), draft.mineshaftEntrance(), draft.mineshaftLayout()), saved(existing));
    }

    private record Volume(WorkplaceArea bounds, Set<Point> cells) {
        boolean contains(Point point) {
            return cells == null ? bounds.contains(point.x(), point.y(), point.z()) : cells.contains(point);
        }
    }

    private static Volume saved(ApprenticeWorkplace site) {
        var plan = site.existingExcavation();
        if (site.type() == TownsfolkWorkType.MINING && plan != null && plan.id() != null
                && site.area().equals(plan.area()))
            return passage(plan.area(), plan.entrance(), plan.layout());
        return new Volume(site.area(), null);
    }

    private static Volume passage(WorkplaceArea area, ExcavationPlan.Entrance entrance, ExcavationPlan.Layout layout) {
        var cells = new HashSet<>(ExcavationPlan.tunnel(area, entrance, layout));
        // Retain conservative validation for malformed legacy designations.
        if (cells.isEmpty()) return new Volume(area, null);
        for (var point : Set.copyOf(cells))
            cells.add(new Point(point.x(), ExcavationPlan.floorAt(area, entrance, layout, point) - 1, point.z()));
        int minY = cells.stream().mapToInt(Point::y).min().orElseThrow();
        int maxY = cells.stream().mapToInt(Point::y).max().orElseThrow();
        return new Volume(new WorkplaceArea(area.minX(), minY, area.minZ(), area.maxX(), area.maxZ(), maxY - minY + 1), cells);
    }

    private static boolean overlaps(Volume first, Volume second) {
        if (!first.bounds().overlaps(second.bounds())) return false;
        if (first.cells() != null) return first.cells().stream().anyMatch(second::contains);
        if (second.cells() != null) return second.cells().stream().anyMatch(first::contains);
        return true;
    }
}
