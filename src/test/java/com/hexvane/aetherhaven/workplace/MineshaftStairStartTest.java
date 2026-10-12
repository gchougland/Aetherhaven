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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Where a new tunnel may start on a stairway, and at what height.
 *
 * <p>The rule is the one a tunnel off any landing follows: a start exists only on the stair's entrance landing or its
 * exit landing, and the new tunnel takes that landing's height. A click on the steps between snaps to the landing of
 * the floor being viewed. Nothing may start at a step's own height, which lies between the two floors.
 */
@Tag("town")
class MineshaftStairStartTest {
    private static final int BASE_FLOOR = 100;
    private static final List<ExcavationPlan.Layout> STAIRS =
        List.of(ExcavationPlan.Layout.DEEP_STAIRS_UP, ExcavationPlan.Layout.DEEP_STAIRS_DOWN);
    private static final List<ExcavationPlan.Layout> ORDERS = List.of(
        ExcavationPlan.Layout.STRAIGHT,
        ExcavationPlan.Layout.ROOM,
        ExcavationPlan.Layout.DEEP_STAIRS_UP,
        ExcavationPlan.Layout.DEEP_STAIRS_DOWN
    );
    private static final int[] LENGTHS = {9, 12, 21, 30};
    /** Lengths that keep a saved stair inside the one chunk test town in every direction. */
    private static final int[] SAVED_LENGTHS = {9, 12};

    @Test
    void aStairOffersOnlyItsTwoLandingsAsStartPoints() {
        for (var direction : MineshaftSelection.Direction.values()) {
            for (var layout : STAIRS) {
                for (int length : SAVED_LENGTHS) {
                    var town = town();
                    var sites = town.getApprenticeWorkplaces();
                    var base = sites.sites().getFirst();
                    var stair = tunnel(town, 11, BASE_FLOOR, 13, direction, layout, length);
                    assertNull(sites.addTunnel(town, town.getOwnerUuid(), base.id(), stair, null));
                    int exit = exitFloor(layout);
                    var connections = MineshaftNetwork.mapConnections(sites, base);
                    var steps = MineshaftNetwork.mapPoints(stair.area(), stair.excavation().entrance(), layout);
                    assertEquals(length, steps.size());
                    assertTrue(steps.stream().anyMatch(p -> p.y() == BASE_FLOOR));
                    assertTrue(steps.stream().anyMatch(p -> p.y() == exit));
                    for (var step : steps) {
                        boolean landing = step.y() == BASE_FLOOR || step.y() == exit;
                        String where = direction + " " + layout + " length " + length + " step " + step;
                        assertEquals(landing, connections.containsKey(step), where);
                        if (landing) {
                            continue;
                        }
                        // Nothing connects at a step's own height, on the map or when the order is saved.
                        assertNull(MineshaftNetwork.mapStart(connections, step), where);
                        for (var next : ORDERS) {
                            var wrong = tunnel(town, step.x(), step.y(), step.z(), direction, next, 9);
                            assertNotNull(sites.addTunnel(town, town.getOwnerUuid(), base.id(), wrong, stair.id()), where + " " + next);
                        }
                    }
                    assertEquals(1, sites.tunnels(base.id()).size());
                }
            }
        }
    }

    @Test
    void aTunnelOffALandingTakesThatLandingsHeight() {
        for (var direction : MineshaftSelection.Direction.values()) {
            for (var layout : STAIRS) {
                for (int length : LENGTHS) {
                    var town = town();
                    var stair = tunnel(town, 11, BASE_FLOOR, 13, direction, layout, length);
                    int exit = exitFloor(layout);
                    for (var landing : MineshaftNetwork.mapPoints(stair.area(), stair.excavation().entrance(), layout)) {
                        if (landing.y() != BASE_FLOOR && landing.y() != exit) {
                            continue;
                        }
                        for (var travel : MineshaftSelection.Direction.values()) {
                            for (var next : ORDERS) {
                                var area = MineshaftSelection.area(landing.x(), landing.y() - 1, landing.z(), travel, 9, next);
                                var entrance = MineshaftSelection.entrance(travel);
                                var cells = ExcavationPlan.tunnel(area, entrance, next);
                                String where = direction + " " + layout + " length " + length + " landing " + landing
                                    + " then " + travel + " " + next;
                                assertFalse(cells.isEmpty(), where);
                                // The first row dug stands on the landing's floor, and the saved order reports that start.
                                assertEquals(landing.y(), ExcavationPlan.floorAt(area, entrance, next, cells.getFirst()), where);
                                assertEquals(
                                    new Point(landing.x(), landing.y() - 1, landing.z()),
                                    MineshaftSelection.ground(area, entrance, next),
                                    where
                                );
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void aClickOnTheStepsSnapsToTheLandingOfTheFloorBeingViewed() {
        for (var direction : MineshaftSelection.Direction.values()) {
            for (var layout : STAIRS) {
                for (int length : SAVED_LENGTHS) {
                    var town = town();
                    var sites = town.getApprenticeWorkplaces();
                    var base = sites.sites().getFirst();
                    var stair = tunnel(town, 11, BASE_FLOOR, 13, direction, layout, length);
                    assertNull(sites.addTunnel(town, town.getOwnerUuid(), base.id(), stair, null));
                    int exit = exitFloor(layout);
                    var connections = MineshaftNetwork.mapConnections(sites, base);
                    int snapped = 0;
                    for (var step : MineshaftNetwork.mapPoints(stair.area(), stair.excavation().entrance(), layout)) {
                        if (step.y() == BASE_FLOOR || step.y() == exit) {
                            continue;
                        }
                        for (int viewed : new int[] {BASE_FLOOR, exit}) {
                            var start = MineshaftNetwork.mapStart(connections, new Point(step.x(), viewed, step.z()));
                            if (start == null) {
                                continue;
                            }
                            String where = direction + " " + layout + " length " + length + " step " + step + " viewed " + viewed;
                            assertEquals(viewed, start.anchor().y(), where);
                            assertTrue(connections.containsKey(start.anchor()), where);
                            snapped++;
                        }
                    }
                    // The steps next to each landing are within reach of it, so the rule is exercised on both floors.
                    assertTrue(snapped >= 2, direction + " " + layout + " length " + length);
                }
            }
        }
    }

    @Test
    void aTunnelClickedOnTheStairwayIsRefusedRatherThanLaidAcrossTheSteps() {
        for (var direction : MineshaftSelection.Direction.values()) {
            int dx = direction == MineshaftSelection.Direction.EAST ? 1 : direction == MineshaftSelection.Direction.WEST ? -1 : 0;
            int dz = direction == MineshaftSelection.Direction.SOUTH ? 1 : direction == MineshaftSelection.Direction.NORTH ? -1 : 0;

            // Stairs down, seen from the upper floor: the cells past the entrance landing lie over the steps.
            var down = new Mine(direction, ExcavationPlan.Layout.DEEP_STAIRS_DOWN);
            var entranceEdge = down.steps.stream().filter(p -> p.y() == BASE_FLOOR).reduce((first, second) -> second).orElseThrow();
            var clicked = MineshaftNetwork.mapStart(
                down.connections,
                new Point(entranceEdge.x() + dx * 3, BASE_FLOOR, entranceEdge.z() + dz * 3)
            );
            assertNotNull(clicked, direction.toString());
            assertEquals(entranceEdge, clicked.anchor(), direction.toString());
            assertEquals(direction, clicked.direction(), direction.toString());
            for (var next : ORDERS) {
                var across = tunnel(down.town, entranceEdge.x(), entranceEdge.y(), entranceEdge.z(), direction, next, 9);
                assertCovered(
                    down.sites.addTunnel(down.town, down.town.getOwnerUuid(), down.base.id(), across, down.prerequisite(entranceEdge)),
                    direction + " " + next + " across the steps going down"
                );
            }
            assertEquals(1, down.sites.tunnels(down.base.id()).size());

            // Stairs up, seen from the upper floor: the cells behind the exit landing lie over the steps.
            var up = new Mine(direction, ExcavationPlan.Layout.DEEP_STAIRS_UP);
            var exitLanding = up.steps.stream().filter(p -> p.y() == BASE_FLOOR + 7).findFirst().orElseThrow();
            var back = opposite(direction);
            var behind = MineshaftNetwork.mapStart(
                up.connections,
                new Point(exitLanding.x() - dx * 3, BASE_FLOOR + 7, exitLanding.z() - dz * 3)
            );
            assertNotNull(behind, direction.toString());
            assertEquals(exitLanding, behind.anchor(), direction.toString());
            assertEquals(back, behind.direction(), direction.toString());
            var over = tunnel(up.town, exitLanding.x(), exitLanding.y(), exitLanding.z(), back, ExcavationPlan.Layout.STRAIGHT, 9);
            assertCovered(
                up.sites.addTunnel(up.town, up.town.getOwnerUuid(), up.base.id(), over, up.prerequisite(exitLanding)),
                direction + " back over the steps going up"
            );

            // A branch sideways off the entrance landing still starts at that landing's height.
            for (var side : MineshaftSelection.Direction.values()) {
                if (side == direction || side == back) {
                    continue;
                }
                var mine = new Mine(direction, ExcavationPlan.Layout.DEEP_STAIRS_DOWN);
                var branch = tunnel(mine.town, entranceEdge.x(), entranceEdge.y(), entranceEdge.z(), side, ExcavationPlan.Layout.STRAIGHT, 3);
                assertNull(
                    mine.sites.addTunnel(mine.town, mine.town.getOwnerUuid(), mine.base.id(), branch, mine.prerequisite(entranceEdge)),
                    direction + " stairs, branch " + side
                );
                assertEquals(BASE_FLOOR, branch.area().floorY());
            }
        }
    }

    /** The refusal names the stairs it would close and the place, so the player knows what to change. */
    private static void assertCovered(String refusal, String where) {
        assertNotNull(refusal, where);
        assertTrue(refusal.startsWith(MineshaftNetwork.COVERED_PASSAGE + "Tunnel at "), where + ": " + refusal);
        assertTrue(refusal.endsWith("Choose another direction, or start from the other end of that passage."), where + ": " + refusal);
    }

    private static MineshaftSelection.Direction opposite(MineshaftSelection.Direction direction) {
        return switch (direction) {
            case NORTH -> MineshaftSelection.Direction.SOUTH;
            case SOUTH -> MineshaftSelection.Direction.NORTH;
            case EAST -> MineshaftSelection.Direction.WEST;
            case WEST -> MineshaftSelection.Direction.EAST;
        };
    }

    /**
     * A base in the middle of the one chunk test town with one saved nine block stair leaving it outward, so the
     * stair and anything planned from it fit inside the town in every direction.
     */
    private static final class Mine {
        final TownRecord town = town(new WorkplaceArea(12, BASE_FLOOR, 12, 18, 18));
        final TownWorkplaces sites = town.getApprenticeWorkplaces();
        final ApprenticeWorkplace base = sites.sites().getFirst();
        final ApprenticeWorkplace stair;
        final java.util.Map<Point, UUID> connections;
        final List<Point> steps;

        Mine(MineshaftSelection.Direction direction, ExcavationPlan.Layout layout) {
            int x = direction == MineshaftSelection.Direction.EAST ? 17 : direction == MineshaftSelection.Direction.WEST ? 12 : 15;
            int z = direction == MineshaftSelection.Direction.SOUTH ? 17 : direction == MineshaftSelection.Direction.NORTH ? 12 : 15;
            stair = tunnel(town, x, BASE_FLOOR, z, direction, layout, 9);
            assertNull(sites.addTunnel(town, town.getOwnerUuid(), base.id(), stair, null));
            connections = MineshaftNetwork.mapConnections(sites, base);
            steps = List.copyOf(MineshaftNetwork.mapPoints(stair.area(), stair.excavation().entrance(), layout));
        }

        /** The saved order a start at this point hangs from; none when the point is still the base's own floor. */
        UUID prerequisite(Point start) {
            UUID owner = connections.get(start);
            assertNotNull(owner, start.toString());
            return owner.equals(base.id()) ? null : owner;
        }
    }

    private static int exitFloor(ExcavationPlan.Layout layout) {
        return layout == ExcavationPlan.Layout.DEEP_STAIRS_UP ? BASE_FLOOR + 7 : BASE_FLOOR - 7;
    }

    private static TownRecord town() {
        return town(new WorkplaceArea(8, BASE_FLOOR, 8, 14, 14));
    }

    private static TownRecord town(WorkplaceArea baseArea) {
        var town = new TownRecord(UUID.randomUUID(), UUID.randomUUID(), "test", 16, BASE_FLOOR, 16, 1, 1, 0);
        town.getResidentNpcRecords().add(new ResidentNpcRecord("Aetherhaven_Miner", "miner", null, UUID.randomUUID()));
        assertNull(town.getApprenticeWorkplaces().unlock(town, TownsfolkWorkType.MINING));
        assertNull(town.getApprenticeWorkplaces().save(town, null, TownsfolkWorkType.MINING, baseArea, "Base"));
        return town;
    }

    private static ApprenticeWorkplace tunnel(
        TownRecord town,
        int x,
        int y,
        int z,
        MineshaftSelection.Direction direction,
        ExcavationPlan.Layout layout,
        int length
    ) {
        var area = MineshaftSelection.area(x, y - 1, z, direction, length, layout);
        var site = new ApprenticeWorkplace("Tunnel", TownsfolkWorkType.MINING, area);
        var entrance = MineshaftSelection.entrance(direction);
        site.setMineshaftConfiguration(entrance, layout);
        var cells = ExcavationPlan.tunnel(area, entrance, layout).stream()
            .map(p -> new ExcavationPlan.Cell(p, "Rock_Stone", 0, 1))
            .toList();
        assertNull(site.excavation().start(0, town.getOwnerUuid(), UUID.randomUUID(), area, entrance, layout, cells));
        return site;
    }
}
