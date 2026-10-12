package com.hexvane.aetherhaven.workplace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.town.ResidentNpcRecord;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import com.hexvane.aetherhaven.workplace.MineshaftSelection.Direction;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Which new tunnels the planner accepts next to passages that are already there.
 *
 * <p>The rule protects one thing: the block someone stands on in an existing passage is never dug. A tunnel that
 * leaves a passage at the passage's own height does not need that block, so it is accepted in every direction; where
 * its three block width overlaps a step, the step keeps its block. A tunnel is refused only when such a block would
 * stand in its own walkway, and the refusal names the passage and the place.
 *
 * <p>The first test is the owner's report from October 11: stairs ending on a landing at 511, 104, -370, and a nine
 * block tunnel north from that landing, which the planner refused with "This route would remove the floor of an
 * existing passage".
 */
@Tag("town")
class MineshaftBranchTest {
    private static final Point LANDING = new Point(511, 104, -370);
    private static final int TOP = 111;
    private static final String ADVICE_CUT = "Choose another direction, or a start further from that passage.";
    private static final String ADVICE_COVERED = "Choose another direction, or start from the other end of that passage.";

    @Test
    void theOwnersNineBlockTunnelNorthOffTheStairLandingIsAccepted() {
        for (var stairs : List.of(Direction.WEST, Direction.EAST, Direction.NORTH)) {
            for (boolean dug : new boolean[] {false, true}) {
                var mine = Mine.withStairsDownTo(LANDING, stairs);
                if (dug) {
                    mine.dig(mine.stairs);
                }
                String where = "stairs heading " + stairs + (dug ? ", dug" : ", planned");
                assertEquals(List.of(LANDING), mine.landingsAt(LANDING.y()), where);

                var route = mine.route(LANDING, Direction.NORTH, ExcavationPlan.Layout.STRAIGHT, 9, mine.stairs.id());
                assertTrue(route.ready(), where + ": " + route.issue());
                // Straight on from the last step all eighty one blocks are new. Sideways, the stairwell is already open
                // and the step beside the landing keeps its block.
                assertEquals(stairs == Direction.NORTH ? 81 : 75, route.cells().size(), where);
                assertTrue(Collections.disjoint(points(route.cells()), MineshaftNetwork.protectedFloors(mine.sites, mine.base)), where);
                if (stairs != Direction.NORTH) {
                    // This is what the old rule refused: the route's width takes in the block under the last step.
                    var step = new Point(LANDING.x() - dx(stairs), LANDING.y(), LANDING.z() - 1);
                    assertTrue(MineshaftNetwork.protectedFloors(mine.sites, mine.base).contains(step), where);
                    assertTrue(mine.volume(LANDING, Direction.NORTH, ExcavationPlan.Layout.STRAIGHT, 9).contains(step), where);
                    assertFalse(MineshaftNetwork.ancestorCells(mine.sites, mine.stairs.id()).contains(step), where);
                    // Now that block is simply left where it is.
                    assertFalse(points(route.cells()).contains(step), where);
                }
                var tunnel = mine.add("Tunnel", LANDING, Direction.NORTH, ExcavationPlan.Layout.STRAIGHT, 9, mine.stairs.id());
                assertEquals(104, tunnel.area().floorY(), where);
                assertEquals(route.cells().size(), tunnel.excavation().total(), where);
            }
        }
    }

    @Test
    void theSameTunnelAimedBackUnderItsOwnStairsIsRefusedWithThePlace() {
        // Stairs that came down heading south leave their steps north of the landing, directly over the route.
        var mine = Mine.withStairsDownTo(LANDING, Direction.SOUTH);
        var route = mine.route(LANDING, Direction.NORTH, ExcavationPlan.Layout.STRAIGHT, 9, mine.stairs.id());
        assertEquals(MineshaftNetwork.CUT_FLOOR + "Stairs at 511, 104, -371. " + ADVICE_CUT, route.issue());
        assertFalse(route.ready());
        // The other three directions leave that same landing.
        for (var travel : List.of(Direction.EAST, Direction.WEST, Direction.SOUTH)) {
            var other = mine.route(LANDING, travel, ExcavationPlan.Layout.STRAIGHT, 9, mine.stairs.id());
            assertTrue(other.ready(), travel + ": " + other.issue());
        }
    }

    @Test
    void aLevelTunnelLeavesTheBottomLandingOfStairsDownInEveryDirectionButBackUnderThem() {
        for (var stairs : Direction.values()) {
            for (var travel : Direction.values()) {
                var mine = Mine.withStairsDownTo(LANDING, stairs);
                String where = "stairs heading " + stairs + ", tunnel " + travel;
                var route = mine.route(LANDING, travel, ExcavationPlan.Layout.STRAIGHT, 9, mine.stairs.id());
                if (travel == opposite(stairs)) {
                    var step = new Point(LANDING.x() - dx(stairs), LANDING.y(), LANDING.z() - dz(stairs));
                    assertEquals(MineshaftNetwork.CUT_FLOOR + "Stairs at " + at(step) + ". " + ADVICE_CUT, route.issue(), where);
                    continue;
                }
                assertTrue(route.ready(), where + ": " + route.issue());
                assertEquals(travel == stairs ? 81 : 75, route.cells().size(), where);
                assertLeavesEveryWalkway(mine, route, where);
                mine.add("Tunnel", LANDING, travel, ExcavationPlan.Layout.STRAIGHT, 9, mine.stairs.id());
            }
        }
    }

    @Test
    void aLevelTunnelLeavesTheMiddleOfAFlatPassageInEveryDirection() {
        for (var passage : Direction.values()) {
            for (var travel : Direction.values()) {
                var mine = new Mine(new Point(520, TOP, -370), passage);
                var gallery = mine.add("Gallery", mine.anchor, passage, ExcavationPlan.Layout.STRAIGHT, 12, null);
                var middle = step(mine.anchor, passage, 6);
                String where = "passage heading " + passage + ", tunnel " + travel;
                assertEquals(gallery.id(), MineshaftNetwork.mapConnections(mine.sites, mine.base).get(middle), where);
                var route = mine.route(middle, travel, ExcavationPlan.Layout.STRAIGHT, 9, gallery.id());
                if (travel == opposite(passage)) {
                    // Back along the passage towards the base there is nothing left to dig, and the message says so.
                    assertEquals(MineshaftNetwork.NO_NEW_ROCK, route.issue(), where);
                    continue;
                }
                assertTrue(route.ready(), where + ": " + route.issue());
                // Sideways the first row is the passage's own side lane. Straight on, three rows lie past its end.
                assertEquals(travel == passage ? 27 : 72, route.cells().size(), where);
                assertLeavesEveryWalkway(mine, route, where);
                mine.add("Branch", middle, travel, ExcavationPlan.Layout.STRAIGHT, 9, gallery.id());
            }
        }
    }

    @Test
    void aLevelTunnelLeavesEitherLandingOfStairsUpAndTheTopLandingOfStairsDown() {
        for (var stairs : Direction.values()) {
            for (var travel : Direction.values()) {
                boolean sideways = travel != stairs && travel != opposite(stairs);
                String where = "stairs heading " + stairs + ", tunnel " + travel;

                // Stairs down, from the last level row before the first step.
                var down = new Mine(new Point(520, TOP, -370), stairs);
                down.stairs = down.add("Stairs", down.anchor, stairs, ExcavationPlan.Layout.DEEP_STAIRS_DOWN, 9, null);
                var top = step(down.anchor, stairs, 2);
                var route = down.route(top, travel, ExcavationPlan.Layout.STRAIGHT, 9, down.stairs.id());
                if (travel == stairs) {
                    // Straight on would lay a floor over the stairwell.
                    var firstStep = step(down.anchor, stairs, 3);
                    assertEquals(
                        MineshaftNetwork.COVERED_PASSAGE + "Stairs at " + at(new Point(firstStep.x(), TOP - 1, firstStep.z())) + ". " + ADVICE_COVERED,
                        route.issue(),
                        where + " off the top of stairs down"
                    );
                } else {
                    assertTrue(route.ready(), where + " off the top of stairs down: " + route.issue());
                    assertLeavesEveryWalkway(down, route, where);
                }

                // Stairs up, from the last level row before the first step.
                var up = new Mine(new Point(520, TOP, -370), stairs);
                up.stairs = up.add("Stairs", up.anchor, stairs, ExcavationPlan.Layout.DEEP_STAIRS_UP, 9, null);
                var bottom = step(up.anchor, stairs, 2);
                route = up.route(bottom, travel, ExcavationPlan.Layout.STRAIGHT, 9, up.stairs.id());
                if (travel == stairs) {
                    // Straight on runs into the first step, which must keep its block.
                    assertEquals(
                        MineshaftNetwork.CUT_FLOOR + "Stairs at " + at(step(up.anchor, stairs, 3)) + ". " + ADVICE_CUT,
                        route.issue(),
                        where + " off the bottom of stairs up"
                    );
                } else {
                    assertTrue(route.ready(), where + " off the bottom of stairs up: " + route.issue());
                    assertLeavesEveryWalkway(up, route, where);
                    if (sideways) {
                        var firstStep = step(up.anchor, stairs, 3);
                        var side = new Point(firstStep.x() + dx(travel), TOP, firstStep.z() + dz(travel));
                        assertTrue(MineshaftNetwork.protectedFloors(up.sites, up.base).contains(side), where);
                        assertFalse(points(route.cells()).contains(side), where + ": the first step lost its block");
                    }
                }

                // Stairs up, from the single row at the top.
                var exit = step(up.anchor, stairs, 9);
                exit = new Point(exit.x(), TOP + 7, exit.z());
                route = up.route(exit, travel, ExcavationPlan.Layout.STRAIGHT, 9, up.stairs.id());
                if (travel == opposite(stairs)) {
                    var lastStep = step(up.anchor, stairs, 8);
                    assertEquals(
                        MineshaftNetwork.COVERED_PASSAGE + "Stairs at " + at(new Point(lastStep.x(), TOP + 6, lastStep.z())) + ". " + ADVICE_COVERED,
                        route.issue(),
                        where + " off the top of stairs up"
                    );
                } else {
                    assertTrue(route.ready(), where + " off the top of stairs up: " + route.issue());
                    assertEquals(travel == stairs ? 81 : 75, route.cells().size(), where);
                    assertLeavesEveryWalkway(up, route, where);
                }
            }
        }
    }

    @Test
    void twoPlannedTunnelsMayCrossAtTheSameHeightAndShareTheRockWhereTheyMeet() {
        var mine = new Mine(new Point(520, TOP, -370), Direction.WEST);
        var gallery = mine.add("Gallery", mine.anchor, Direction.WEST, ExcavationPlan.Layout.STRAIGHT, 12, null);
        var start = new Point(514, TOP, -376);
        var crossing = mine.volume(start, Direction.SOUTH, ExcavationPlan.Layout.STRAIGHT, 12);
        var shared = new HashSet<>(crossing);
        shared.retainAll(ExcavationPlan.tunnel(gallery.area(), gallery.excavation().entrance(), gallery.excavation().layout()));
        assertEquals(27, shared.size());

        // Neither is dug yet: both orders list the rock where they meet, and whoever gets there first digs it.
        var route = mine.route(start, Direction.SOUTH, ExcavationPlan.Layout.STRAIGHT, 12, null);
        assertTrue(route.ready(), route.issue());
        assertEquals(108, route.cells().size());
        assertTrue(points(route.cells()).containsAll(shared));

        // Once the gallery is dug the crossing only lists what is left, and leaves alone whatever stands in the gallery.
        mine.dig(gallery);
        var beam = new Point(514, TOP, -371);
        assertTrue(shared.contains(beam));
        mine.standing.put(beam, ExcavationFittings.BEAM);
        route = mine.route(start, Direction.SOUTH, ExcavationPlan.Layout.STRAIGHT, 12, null);
        assertTrue(route.ready(), route.issue());
        assertEquals(81, route.cells().size());
        assertTrue(Collections.disjoint(points(route.cells()), shared));
    }

    @Test
    void aTunnelThroughAHigherPassagesFloorIsRefusedWithThePlaceAndOneUnderItKeepsThatFloorAsItsCeiling() {
        var mine = new Mine(new Point(520, TOP, -370), Direction.WEST);
        mine.stairs = mine.add("Stairs", mine.anchor, Direction.WEST, ExcavationPlan.Layout.DEEP_STAIRS_UP, 9, null);

        // The fifth row of the stairs is one block up. A level tunnel across it would take the block under that step.
        var low = step(mine.anchor, Direction.WEST, 3);
        var route = mine.route(new Point(low.x(), TOP, -376), Direction.SOUTH, ExcavationPlan.Layout.STRAIGHT, 12, null);
        assertNotNull(route.issue());
        assertTrue(route.issue().startsWith(MineshaftNetwork.CUT_FLOOR + "Stairs at " + low.x() + ", " + TOP + ", "), route.issue());
        assertTrue(route.issue().endsWith(ADVICE_CUT), route.issue());

        // Two rows on the stairs are three blocks up: their floor is the tunnel's ceiling there, and it stays.
        var high = step(mine.anchor, Direction.WEST, 5);
        route = mine.route(new Point(high.x(), TOP, -376), Direction.SOUTH, ExcavationPlan.Layout.STRAIGHT, 12, null);
        assertTrue(route.ready(), route.issue());
        assertLeavesEveryWalkway(mine, route, "under the stairs");
        var ceiling = new Point(high.x(), TOP + 2, -370);
        assertTrue(MineshaftNetwork.protectedFloors(mine.sites, mine.base).contains(ceiling));
        assertFalse(points(route.cells()).contains(ceiling));
    }

    @Test
    void stairsDownCannotBeCutThroughTheBaseFloorAndTheRefusalNamesTheBase() {
        // Started three blocks inside the base, the first step down would take the base's own floor.
        var mine = new Mine(new Point(520, TOP, -370), Direction.WEST);
        var inside = new Point(523, TOP, -370);
        var route = mine.route(inside, Direction.WEST, ExcavationPlan.Layout.DEEP_STAIRS_DOWN, 9, null);
        assertEquals(MineshaftNetwork.CUT_FLOOR + "Base at 520, 110, -370. " + ADVICE_CUT, route.issue());
    }

    private static void assertLeavesEveryWalkway(Mine mine, MineshaftNetwork.Route route, String where) {
        assertTrue(Collections.disjoint(points(route.cells()), MineshaftNetwork.protectedFloors(mine.sites, mine.base)), where + ": a walkway would be dug");
    }

    private static Set<Point> points(List<ExcavationPlan.Cell> cells) {
        var points = new HashSet<Point>();
        for (var cell : cells) {
            points.add(cell.point());
        }
        return points;
    }

    private static String at(Point p) {
        return p.x() + ", " + p.y() + ", " + p.z();
    }

    private static int dx(Direction direction) {
        return direction == Direction.EAST ? 1 : direction == Direction.WEST ? -1 : 0;
    }

    private static int dz(Direction direction) {
        return direction == Direction.SOUTH ? 1 : direction == Direction.NORTH ? -1 : 0;
    }

    private static Point step(Point from, Direction direction, int blocks) {
        return new Point(from.x() + dx(direction) * blocks, from.y(), from.z() + dz(direction) * blocks);
    }

    private static Direction opposite(Direction direction) {
        return switch (direction) {
            case NORTH -> Direction.SOUTH;
            case SOUTH -> Direction.NORTH;
            case EAST -> Direction.WEST;
            case WEST -> Direction.EAST;
        };
    }

    /**
     * A mine in solid rock. The base room is open, and so is every tunnel marked as dug. Routes are judged the way the
     * planner judges them: the rock review lists each solid block of the route that is this tunnel's to dig, then
     * {@link MineshaftNetwork#route} applies the mine's rules.
     */
    private static final class Mine {
        final TownRecord town = new TownRecord(UUID.randomUUID(), UUID.randomUUID(), "test", 512, 104, -370, 1, 2, 0);
        final TownWorkplaces sites = town.getApprenticeWorkplaces();
        final ApprenticeWorkplace base;
        /** The start of the first passage: a floor block on the base's edge. */
        final Point anchor;
        final Set<Point> open = new HashSet<>();
        /** Blocks standing in dug passages, such as support beams. */
        final Map<Point, String> standing = new HashMap<>();
        ApprenticeWorkplace stairs;

        /** A seven by seven base with {@code anchor} on the edge that faces {@code facing}. */
        Mine(Point anchor, Direction facing) {
            this.anchor = anchor;
            int cx = anchor.x() - 3 * dx(facing);
            int cz = anchor.z() - 3 * dz(facing);
            var area = new WorkplaceArea(cx - 3, anchor.y(), cz - 3, cx + 4, cz + 4);
            town.getResidentNpcRecords().add(new ResidentNpcRecord("Aetherhaven_Miner", "miner", null, UUID.randomUUID()));
            assertNull(sites.unlock(town, TownsfolkWorkType.MINING));
            assertNull(sites.save(town, null, TownsfolkWorkType.MINING, area, "Base"));
            base = sites.sites().getFirst();
            for (int x = area.minX(); x < area.maxX(); x++) {
                for (int z = area.minZ(); z < area.maxZ(); z++) {
                    for (int y = 0; y < 3; y++) {
                        open.add(new Point(x, area.floorY() + y, z));
                    }
                }
            }
        }

        /** Nine block stairs that come down seven blocks in the given direction and end on {@code landing}. */
        static Mine withStairsDownTo(Point landing, Direction heading) {
            var mine = new Mine(new Point(landing.x() - 9 * dx(heading), landing.y() + 7, landing.z() - 9 * dz(heading)), heading);
            mine.stairs = mine.add("Stairs", mine.anchor, heading, ExcavationPlan.Layout.DEEP_STAIRS_DOWN, 9, null);
            return mine;
        }

        List<Point> landingsAt(int height) {
            return MineshaftNetwork.mapConnections(sites, base).entrySet().stream()
                .filter(e -> e.getValue().equals(stairs.id()) && e.getKey().y() == height)
                .map(Map.Entry::getKey)
                .toList();
        }

        List<Point> volume(Point start, Direction travel, ExcavationPlan.Layout layout, int length) {
            var area = MineshaftSelection.area(start.x(), start.y() - 1, start.z(), travel, length, layout);
            return ExcavationPlan.tunnel(area, MineshaftSelection.entrance(travel), layout);
        }

        MineshaftNetwork.Route route(Point start, Direction travel, ExcavationPlan.Layout layout, int length, UUID prerequisite) {
            var area = MineshaftSelection.area(start.x(), start.y() - 1, start.z(), travel, length, layout);
            var entrance = MineshaftSelection.entrance(travel);
            var left = MineshaftNetwork.leftAlone(sites, base, prerequisite);
            var reviewed = ExcavationPlan.tunnel(area, entrance, layout).stream()
                .filter(p -> !left.contains(p) && (!open.contains(p) || standing.containsKey(p)))
                .map(p -> new ExcavationPlan.Cell(p, standing.getOrDefault(p, "Rock_Stone"), 0, 1))
                .toList();
            return MineshaftNetwork.route(sites, base, prerequisite, area, entrance, layout, reviewed);
        }

        /** Adds a tunnel the way the planner's Add button does. */
        ApprenticeWorkplace add(String name, Point start, Direction travel, ExcavationPlan.Layout layout, int length, UUID prerequisite) {
            var route = route(start, travel, layout, length, prerequisite);
            assertTrue(route.ready(), name + " " + travel + ": " + route.issue());
            var area = MineshaftSelection.area(start.x(), start.y() - 1, start.z(), travel, length, layout);
            var site = new ApprenticeWorkplace(name, TownsfolkWorkType.MINING, area);
            var entrance = MineshaftSelection.entrance(travel);
            site.setMineshaftConfiguration(entrance, layout);
            assertNull(site.excavation().start(0, town.getOwnerUuid(), UUID.randomUUID(), area, entrance, layout, route.cells()));
            assertNull(sites.addTunnel(town, town.getOwnerUuid(), base.id(), site, prerequisite), name + " " + travel);
            return site;
        }

        /** Opens a tunnel and lets its order notice, as it does when miners finish or the rock is taken out by hand. */
        void dig(ApprenticeWorkplace site) {
            var plan = site.excavation();
            open.addAll(ExcavationPlan.tunnel(site.area(), plan.entrance(), plan.layout()));
            plan.survey(p -> ExcavationPlan.Found.EMPTY, 0);
            assertTrue(plan.passageReady());
        }
    }
}
