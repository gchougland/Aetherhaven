package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What each square of the Mineshaft Planner's map shows on one floor, and what choosing it does.
 *
 * <p>A square covers three blocks by three. Besides the base, the passages of this floor and the draft, the map
 * draws every passage that goes through this floor at another height. Stairs used to appear only on their two
 * landings, so from the lower floor the squares under the first steps looked like free rock and a tunnel could be
 * aimed straight into them.
 */
public final class MineshaftMap {
    public static final int SIZE = 21;
    public static final int STEP = 3;

    private MineshaftMap() {}

    public enum Kind {
        /** Undug ground a route may go through. */
        ROCK,
        BASE,
        /** A walkable passage of this floor. */
        PASSAGE,
        /** A passage at another height goes through here: stairs, or a tunnel a block or two higher or lower. */
        STAIRWELL,
        DRAFT,
        /** The draft would have to go through a passage's floor or over its open space here. */
        CLASH,
        START
    }

    /**
     * @param landing 1 when stairs lead up from this square, 2 when they lead down, 3 for both, 0 for none
     * @param climbs  for a stairway going through, the way its floor rises
     */
    public record Cell(Point point, Kind kind, int landing, MineshaftSelection.Direction climbs, String tooltip) {}

    /** The route being planned, with the blocks of it that clash with passages already there. */
    public record Draft(Point start, MineshaftSelection.Direction direction, int length, ExcavationPlan.Layout layout,
                        List<MineshaftNetwork.Clash> clashes) {
        public Draft {
            clashes = clashes == null ? List.of() : List.copyOf(clashes);
        }
    }

    /** Either the start a chosen square gives, or why it gives none. */
    public record Choice(MineshaftNetwork.MapStart start, String refusal) {}

    private static MineshaftNetwork.Passing through(Map<Point, MineshaftNetwork.Passing> passing, int px, int floor, int pz) {
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                var found = passing.get(new Point(px + dx, floor, pz + dz));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Whether a square next to a passage can really be dug into from it. A square covers three blocks each way and a
     * stairway is three wide, so a square beside a landing may hold one lane of the stairs and two of free rock: it is
     * the route from the passage into the square that decides, not the square's corner.
     */
    private static boolean extendable(TownWorkplaces sites, ApprenticeWorkplace base, MineshaftNetwork.MapStart start) {
        if (start == null || start.direction() == null) {
            return false;
        }
        try {
            var anchor = start.anchor();
            var area = MineshaftSelection.area(anchor.x(), anchor.y() - 1, anchor.z(), start.direction(), MineshaftSelection.MINIMUM_LENGTH, ExcavationPlan.Layout.STRAIGHT);
            return MineshaftNetwork.clashes(sites, base, MineshaftNetwork.prerequisiteAt(sites, base, anchor), area,
                MineshaftSelection.entrance(start.direction()), ExcavationPlan.Layout.STRAIGHT).isEmpty();
        } catch (IllegalArgumentException outside) {
            return false;
        }
    }

    /** All squares of the map, row by row from the north west corner. */
    public static List<Cell> cells(TownWorkplaces sites, ApprenticeWorkplace base, int centerX, int centerZ, int floor, Draft draft) {
        var tunnels = sites.tunnels(base.id());
        var stairs = new HashMap<Point, Integer>();
        for (var tunnel : tunnels) {
            MineshaftNetwork.stairs(tunnel.area(), tunnel.excavation().entrance(), tunnel.excavation().layout())
                .forEach((point, bits) -> stairs.merge(point, bits, (left, right) -> left | right));
        }
        Map<Point, UUID> floors = MineshaftNetwork.mapConnections(sites, base);
        Set<Point> draftFloors = new HashSet<>();
        Set<Point> clashing = new HashSet<>();
        if (draft != null) {
            try {
                var area = MineshaftSelection.area(draft.start().x(), draft.start().y() - 1, draft.start().z(), draft.direction(), draft.length(), draft.layout());
                var entrance = MineshaftSelection.entrance(draft.direction());
                MineshaftNetwork.stairs(area, entrance, draft.layout()).forEach((point, bits) -> stairs.merge(point, bits, (left, right) -> left | right));
                draftFloors.addAll(MineshaftNetwork.mapPoints(area, entrance, draft.layout()));
                if (floor == draft.start().y()) {
                    for (var clash : draft.clashes()) {
                        clashing.add(new Point(clash.at().x(), floor, clash.at().z()));
                    }
                }
            } catch (IllegalArgumentException outside) {
                // A draft that does not fit the world is simply not drawn; the planner says why.
            }
        }
        var passing = MineshaftNetwork.passing(sites, base, floor);
        var result = new ArrayList<Cell>(SIZE * SIZE);
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                int px = centerX + (col - SIZE / 2) * STEP;
                int pz = centerZ + (row - SIZE / 2) * STEP;
                var point = new Point(px, floor, pz);
                var connected = MineshaftNetwork.mapConnection(floors, point);
                UUID owner = connected == null ? null : floors.get(connected);
                var start = MineshaftNetwork.mapStart(floors, point);
                boolean draftHere = false;
                boolean clashHere = false;
                int landing = 0;
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        var sample = new Point(px + dx, floor, pz + dz);
                        draftHere |= draftFloors.contains(sample);
                        clashHere |= clashing.contains(sample);
                        if (floors.containsKey(sample) || draftFloors.contains(sample)) {
                            landing |= stairs.getOrDefault(sample, 0);
                        }
                    }
                }
                var through = through(passing, px, floor, pz);
                // Stairs go through this square and no tunnel can be dug into it from the passage beside it.
                boolean stairsHere = through != null && owner == null && !extendable(sites, base, start);
                boolean selected = draft != null && floor == draft.start().y()
                    && Math.abs((long) px - draft.start().x()) <= 1 && Math.abs((long) pz - draft.start().z()) <= 1;
                Kind kind = selected ? Kind.START
                    : clashHere ? Kind.CLASH
                    : draftHere ? Kind.DRAFT
                    : owner != null ? (owner.equals(base.id()) ? Kind.BASE : Kind.PASSAGE)
                    : stairsHere ? Kind.STAIRWELL
                    : Kind.ROCK;
                String where = (landing == 0 ? "" : landing == 1 ? "Stairs up | " : landing == 2 ? "Stairs down | " : "Stairs up and down | ")
                    + px + ", " + floor + ", " + pz;
                String what;
                if (kind == Kind.CLASH) {
                    what = (through != null ? through.hover(floor) : "Another passage is in the way") + ". The draft cannot go through";
                } else if (owner != null) {
                    what = "Start a branch here";
                } else if (stairsHere) {
                    // Never offered as a place to extend into: the stairs are in the way of any tunnel from next door.
                    what = through.hover(floor);
                } else {
                    what = start == null ? "No connecting passage" : "Extend the nearby passage";
                }
                boolean shown = through != null && through.stairs() && (stairsHere || kind == Kind.CLASH);
                result.add(new Cell(point, kind, landing, shown ? through.climbs() : null, where + " | " + what));
            }
        }
        return result;
    }

    /**
     * What choosing a square does. A square a stairway goes through gives no start, even next to a landing, where the
     * map used to offer "Extend the nearby passage" and aim the new tunnel into the steps.
     */
    public static Choice choose(TownWorkplaces sites, ApprenticeWorkplace base, int floor, int px, int pz) {
        var floors = MineshaftNetwork.mapConnections(sites, base);
        var point = new Point(px, floor, pz);
        var start = MineshaftNetwork.mapStart(floors, point);
        if (MineshaftNetwork.mapConnection(floors, point) == null) {
            var through = through(MineshaftNetwork.passing(sites, base, floor), px, floor, pz);
            if (through != null && !extendable(sites, base, start)) {
                return new Choice(null, through.hover(floor) + ". Choose a square beside or beyond " + (through.stairs() ? "them." : "it."));
            }
        }
        return start == null ? new Choice(null, "Choose a passage or the square just beyond its end.") : new Choice(start, null);
    }
}
