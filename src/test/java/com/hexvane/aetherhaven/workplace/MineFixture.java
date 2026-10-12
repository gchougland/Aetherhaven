package com.hexvane.aetherhaven.workplace;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.town.ResidentNpcRecord;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import com.hexvane.aetherhaven.workplace.MineshaftSelection.Direction;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A mine in solid rock for planner tests, around the owner's coordinates. The base room is open, and so is every
 * tunnel marked as dug. Routes are judged the way the planner judges them: the rock review lists each solid block of
 * the route that is this tunnel's to dig, then {@link MineshaftNetwork#route} applies the mine's rules.
 */
final class MineFixture {
    final TownRecord town = new TownRecord(UUID.randomUUID(), UUID.randomUUID(), "test", 512, 104, -370, 1, 2, 0);
    final TownWorkplaces sites = town.getApprenticeWorkplaces();
    final ApprenticeWorkplace base;
    final Set<Point> open = new HashSet<>();
    /** Blocks standing in dug passages, such as support beams. */
    final Map<Point, String> standing = new HashMap<>();

    MineFixture(WorkplaceArea baseArea) {
        town.getResidentNpcRecords().add(new ResidentNpcRecord("Aetherhaven_Miner", "miner", null, UUID.randomUUID()));
        assertNull(sites.unlock(town, TownsfolkWorkType.MINING));
        assertNull(sites.save(town, null, TownsfolkWorkType.MINING, baseArea, "Base"));
        base = sites.sites().getFirst();
        for (int x = baseArea.minX(); x < baseArea.maxX(); x++) {
            for (int z = baseArea.minZ(); z < baseArea.maxZ(); z++) {
                for (int y = 0; y < 3; y++) {
                    open.add(new Point(x, baseArea.floorY() + y, z));
                }
            }
        }
    }

    /** A seven by seven base with {@code anchor} on the edge that faces {@code facing}. */
    static MineFixture facing(Point anchor, Direction facing) {
        int cx = anchor.x() - 3 * dx(facing);
        int cz = anchor.z() - 3 * dz(facing);
        return new MineFixture(new WorkplaceArea(cx - 3, anchor.y(), cz - 3, cx + 4, cz + 4));
    }

    static int dx(Direction direction) {
        return direction == Direction.EAST ? 1 : direction == Direction.WEST ? -1 : 0;
    }

    static int dz(Direction direction) {
        return direction == Direction.SOUTH ? 1 : direction == Direction.NORTH ? -1 : 0;
    }

    static Point step(Point from, Direction direction, int blocks) {
        return new Point(from.x() + dx(direction) * blocks, from.y(), from.z() + dz(direction) * blocks);
    }

    static Set<Point> points(List<ExcavationPlan.Cell> cells) {
        var points = new HashSet<Point>();
        for (var cell : cells) {
            points.add(cell.point());
        }
        return points;
    }

    List<Point> volume(Point start, Direction travel, ExcavationPlan.Layout layout, int length) {
        var area = MineshaftSelection.area(start.x(), start.y() - 1, start.z(), travel, length, layout);
        return ExcavationPlan.tunnel(area, MineshaftSelection.entrance(travel), layout);
    }

    MineshaftNetwork.Route route(Point start, Direction travel, ExcavationPlan.Layout layout, int length) {
        var prerequisite = MineshaftNetwork.prerequisiteAt(sites, base, start);
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
    ApprenticeWorkplace add(String name, Point start, Direction travel, ExcavationPlan.Layout layout, int length, ExcavationFittings.Mode fittings) {
        var route = route(start, travel, layout, length);
        assertTrue(route.ready(), name + " " + travel + ": " + route.issue());
        var area = MineshaftSelection.area(start.x(), start.y() - 1, start.z(), travel, length, layout);
        var site = new ApprenticeWorkplace(name, TownsfolkWorkType.MINING, area);
        var entrance = MineshaftSelection.entrance(travel);
        site.setMineshaftConfiguration(entrance, layout);
        assertNull(site.excavation().start(0, town.getOwnerUuid(), UUID.randomUUID(), area, entrance, layout, route.cells(), fittings));
        assertNull(sites.addTunnel(town, town.getOwnerUuid(), base.id(), site, MineshaftNetwork.prerequisiteAt(sites, base, start)), name + " " + travel);
        return site;
    }

    ApprenticeWorkplace add(String name, Point start, Direction travel, ExcavationPlan.Layout layout, int length) {
        return add(name, start, travel, layout, length, ExcavationFittings.Mode.NONE);
    }

    /** Opens a tunnel and lets its order notice, as it does when miners finish or the rock is taken out by hand. */
    void dig(ApprenticeWorkplace site) {
        var plan = site.excavation();
        open.addAll(ExcavationPlan.tunnel(site.area(), plan.entrance(), plan.layout()));
        plan.survey(p -> ExcavationPlan.Found.EMPTY, 0);
    }

    /** Places every fitting of a dug tunnel, as miners do with the supplies in hand. */
    void fit(ApprenticeWorkplace site) {
        var plan = site.excavation();
        while (plan.nextFitting() != null) {
            var fitting = plan.nextFitting();
            for (int tick = 0; tick < 6; tick++) {
                plan.attend(.5, true, 1);
            }
            assertTrue(plan.fit(plan.id(), fitting, () -> true), fitting.toString());
            standing.put(fitting.point(), fitting.block());
        }
    }
}
