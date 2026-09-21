package com.hexvane.aetherhaven.autonomy;

import static org.junit.jupiter.api.Assertions.*;
import com.hypixel.hytale.component.spatial.KDTree;
import com.hypixel.hytale.component.spatial.SpatialData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("autonomy")
class VillagerConversationSearchTest {
    private record Neighbor(Vector3d position, boolean available, boolean visible) {
        double distanceSquared() { return position.lengthSquared(); }
        boolean eligible() { return available && visible; }
    }

    @Test void crowdedTownKeepsNearestEligiblePartnerWithoutCheckingEveryone() {
        List<Neighbor> all = new ArrayList<>();
        all.add(new Neighbor(new Vector3d(1, 0, 0), false, true)); // Busy.
        all.add(new Neighbor(new Vector3d(1.5, 0, 0), true, false)); // Behind a wall.
        Neighbor expected = new Neighbor(new Vector3d(2, 0, 0), true, true);
        all.add(expected);
        for (int i = 0; i < 120; i++) all.add(new Neighbor(new Vector3d(3 + i * .1, 0, 0), true, true));
        for (int i = 0; i < 5_000; i++) all.add(new Neighbor(new Vector3d(100 + i, 0, 0), true, true));
        var data = new SpatialData<Neighbor>();
        data.addCapacity(all.size());
        for (var neighbor : all) data.append(neighbor.position(), neighbor);
        var spatial = new KDTree<Neighbor>(n -> true);
        spatial.rebuild(data);
        List<Neighbor> nearby = new ArrayList<>();
        spatial.collect(new Vector3d(), VillagerLifePolicy.SEARCH_RADIUS, nearby);
        assertEquals(123, nearby.size());

        AtomicInteger checks = new AtomicInteger();
        var selected = VillagerLifeSystem.nearestEligiblePartner(nearby, Neighbor::distanceSquared, n -> {
            checks.incrementAndGet();
            return n.eligible();
        });
        var oldSelection = all.stream()
            .filter(n -> n.distanceSquared() <= VillagerLifePolicy.SEARCH_RADIUS * VillagerLifePolicy.SEARCH_RADIUS)
            .filter(Neighbor::eligible).min(Comparator.comparingDouble(Neighbor::distanceSquared)).orElse(null);
        assertSame(expected, oldSelection);
        assertSame(oldSelection, selected);
        assertEquals(3, checks.get());
    }

    @Test void rejectsUnavailablePartnersAndKeepsTheFullSearchRadius() {
        var edge = new Neighbor(new Vector3d(VillagerLifePolicy.SEARCH_RADIUS, 0, 0), true, true);
        var blocked = new Neighbor(new Vector3d(1, 0, 0), true, false);
        assertSame(edge, VillagerLifeSystem.nearestEligiblePartner(new ArrayList<>(List.of(edge, blocked)),
            Neighbor::distanceSquared, Neighbor::eligible));
        assertNull(VillagerLifeSystem.nearestEligiblePartner(new ArrayList<>(List.of(blocked)),
            Neighbor::distanceSquared, Neighbor::eligible));
        assertNull(VillagerLifeSystem.nearestEligiblePartner(new ArrayList<Neighbor>(),
            Neighbor::distanceSquared, Neighbor::eligible));
    }

    @Test void sightChecksVisitTheSameBlocksWithoutRepeatedWorldReads() {
        var random = new java.util.Random(76121);
        for (int sample = 0; sample < 100; sample++) {
            var start = new Vector3d(random.nextDouble(-40, 40), random.nextDouble(50, 60), random.nextDouble(-40, 40));
            var end = new Vector3d(start).add(random.nextDouble(-2, 2), random.nextDouble(-1, 1), random.nextDouble(-2, 2));
            var expected = new java.util.LinkedHashSet<Vector3i>();
            int steps = Math.max(1, (int) Math.ceil(start.distance(end) * 5));
            for (int i = 1; i < steps; i++) {
                double t = (double) i / steps;
                expected.add(new Vector3i((int) Math.floor(start.x + (end.x - start.x) * t),
                    (int) Math.floor(start.y + (end.y - start.y) * t + 1.4),
                    (int) Math.floor(start.z + (end.z - start.z) * t)));
            }
            List<Vector3i> visited = new ArrayList<>();
            assertTrue(VillagerLifeSystem.clearSight(start, end, (x, y, z) -> {
                visited.add(new Vector3i(x, y, z));
                return true;
            }));
            assertEquals(new ArrayList<>(expected), visited);
            for (var obstacle : expected) {
                assertFalse(VillagerLifeSystem.clearSight(start, end,
                    (x, y, z) -> x != obstacle.x || y != obstacle.y || z != obstacle.z));
            }
        }
        AtomicInteger reads = new AtomicInteger();
        assertTrue(VillagerLifeSystem.clearSight(new Vector3d(.1, 60, .1), new Vector3d(2.7, 60, .1),
            (x, y, z) -> { reads.incrementAndGet(); return true; }));
        assertEquals(3, reads.get()); // The original walk read each block several times.
    }
}
