package com.hexvane.aetherhaven.halloween;

import com.hexvane.aetherhaven.production.TimberHarvestPlan.Position;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import java.util.*;
import java.util.function.Function;

/** Conservative walking check. One search request shares a strict budget of loaded-cell observations. */
final class HalloweenHidingSearch {
    record Cell(boolean loaded, boolean empty, boolean passable, boolean support) {
        static final Cell UNKNOWN = new Cell(false, false, false, false);
    }
    private static final int[][] SIDES = {{1,0},{-1,0},{0,1},{0,-1}};
    private final Function<Position, Cell> reader;
    private final int limit;
    private final Map<Position, Cell> cells = new HashMap<>();

    HalloweenHidingSearch(Function<Position, Cell> reader, int limit) {
        this.reader = reader; this.limit = Math.max(0, limit);
    }
    boolean exhausted() { return cells.size() >= limit; }
    Cell cell(int x, int y, int z) {
        var at = new Position(x,y,z);
        var existing = cells.get(at);
        if (existing != null) return existing;
        if (exhausted()) return Cell.UNKNOWN;
        var observed = reader.apply(at);
        cells.put(at, observed);
        return observed;
    }
    boolean canHide(int x, int y, int z) {
        return cell(x,y,z).empty() && cell(x,y+1,z).empty() && cell(x,y-1,z).support();
    }
    boolean hasRoof(int x, int y, int z, int ceiling) {
        for (int top = y+2; top <= Math.min(ceiling,y+16); top++) {
            var block = cell(x,top,z);
            if (block.loaded() && !block.empty()) return true;
        }
        return false;
    }
    private boolean stand(int x, int y, int z) {
        return cell(x,y,z).passable() && cell(x,y+1,z).passable() && cell(x,y-1,z).support();
    }
    boolean reachesOutside(PlotFootprintRecord bounds, Position start) {
        if (!stand(start.x(),start.y(),start.z())) return false;
        var seen = new HashSet<Position>(); var queue = new ArrayDeque<Position>();
        seen.add(start); queue.add(start);
        while (!queue.isEmpty()) {
            var from = queue.removeFirst();
            // Require a supported, clear cell beyond the wall, not simply air inside the boundary.
            if (from.x() < bounds.getMinX() || from.x() > bounds.getMaxX()
                || from.z() < bounds.getMinZ() || from.z() > bounds.getMaxZ()) return true;
            for (var side : SIDES) for (int step : new int[]{0,1,-1}) {
                int x=from.x()+side[0], y=from.y()+step, z=from.z()+side[1];
                if (y < Math.max(1,bounds.getMinY()) || y > bounds.getMaxY()) continue;
                var to = new Position(x,y,z);
                if (seen.contains(to) || !stand(x,y,z)) continue;
                // A one-block step still needs clearance above the higher of both feet.
                if (step > 0 && !cell(from.x(),from.y()+2,from.z()).passable()) continue;
                if (step < 0 && !cell(x,from.y()+1,z).passable()) continue;
                seen.add(to); queue.addLast(to);
            }
        }
        return false;
    }
}
