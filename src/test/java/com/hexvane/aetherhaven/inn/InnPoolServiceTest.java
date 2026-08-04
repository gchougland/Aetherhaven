package com.hexvane.aetherhaven.inn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.villager.data.InnPoolEntry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
class InnPoolServiceTest {

    @Test
    void pickWeightedInnPoolEntry_prefersHigherWeight() {
        List<InnPoolEntry> pool =
            List.of(
                new InnPoolEntry("Light", "visitor_merchant", 0, 1),
                new InnPoolEntry("Heavy", "visitor_blacksmith", 1, 9)
            );
        int heavyFirst = 0;
        for (int seed = 0; seed < 500; seed++) {
            InnPoolEntry picked = InnPoolService.pickWeightedInnPoolEntry(pool, new Random(seed));
            if (picked != null && "Heavy".equals(picked.npcRoleId())) {
                heavyFirst++;
            }
        }
        assertTrue(heavyFirst > 350, "expected Heavy to win most rolls, got " + heavyFirst);
    }

    @Test
    void weightedOrderWithoutReplacement_isDeterministicForSeed() {
        List<InnPoolEntry> pool =
            List.of(
                new InnPoolEntry("A", "visitor_merchant", 0, 1),
                new InnPoolEntry("B", "visitor_blacksmith", 1, 2),
                new InnPoolEntry("C", "visitor_farmer", 2, 3)
            );
        List<String> first = weightedOrder(pool, Set.of(), 42L);
        List<String> second = weightedOrder(pool, Set.of(), 42L);
        assertEquals(first, second);
        assertEquals(3, first.size());
        assertEquals(new HashSet<>(List.of("A", "B", "C")), new HashSet<>(first));
    }

    @Test
    void weightedOrderWithoutReplacement_excludesPriorityRoles() {
        List<InnPoolEntry> pool =
            List.of(
                new InnPoolEntry("A", "visitor_merchant", 0, 1),
                new InnPoolEntry("B", "visitor_blacksmith", 1, 1)
            );
        List<String> order = weightedOrder(pool, Set.of("A"), 7L);
        assertEquals(List.of("B"), order);
    }

    private static List<String> weightedOrder(List<InnPoolEntry> pool, Set<String> exclude, long seed) {
        List<InnPoolEntry> remaining = new ArrayList<>();
        for (InnPoolEntry e : pool) {
            if (!exclude.contains(e.npcRoleId())) {
                remaining.add(e);
            }
        }
        List<String> ordered = new ArrayList<>();
        Random rng = new Random(seed);
        while (!remaining.isEmpty()) {
            InnPoolEntry picked = InnPoolService.pickWeightedInnPoolEntry(remaining, rng);
            if (picked == null) {
                break;
            }
            ordered.add(picked.npcRoleId());
            remaining.removeIf(e -> e.npcRoleId().equals(picked.npcRoleId()));
        }
        return ordered;
    }
}
