package com.hexvane.aetherhaven.inn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.town.TownRecord;
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

    @Test
    void prioritizedInnRoleOrder_doesNotHardPriorityGuildMasterAfterTownHall() {
        TownRecord town = new TownRecord();
        town.completeQuest(AetherhavenConstants.QUEST_BUILD_TOWN_HALL);
        List<String> priority = InnPoolService.prioritizedInnRoleOrderForTest(town);
        assertFalse(priority.contains(AetherhavenConstants.GUILD_MASTER_NPC_ROLE_ID));
    }

    @Test
    void prioritizedInnRoleOrder_doesNotHardPriorityGuildMasterWhenGuildHallQuestActive() {
        TownRecord town = new TownRecord();
        town.addActiveQuest(AetherhavenConstants.QUEST_BUILD_GUILD_HALL);
        List<String> priority = InnPoolService.prioritizedInnRoleOrderForTest(town);
        assertFalse(priority.contains(AetherhavenConstants.GUILD_MASTER_NPC_ROLE_ID));
    }

    @Test
    void buildDailyVisitorRoleOrder_doesNotAlwaysPreferGuildMasterAfterTownHall() {
        TownRecord town = new TownRecord();
        town.completeQuest(AetherhavenConstants.QUEST_BUILD_TOWN_HALL);
        List<InnPoolEntry> pool =
            List.of(
                new InnPoolEntry(
                    AetherhavenConstants.GUILD_MASTER_NPC_ROLE_ID,
                    "visitor_guild_master",
                    7,
                    2
                ),
                new InnPoolEntry(AetherhavenConstants.NPC_MERCHANT, "visitor_merchant", 0, 1)
            );
        int guildFirst = 0;
        int merchantFirst = 0;
        for (long seed = 0; seed < 400; seed++) {
            List<String> order = InnPoolService.buildDailyVisitorRoleOrderForTest(town, pool, seed);
            assertEquals(2, order.size());
            assertTrue(order.contains(AetherhavenConstants.GUILD_MASTER_NPC_ROLE_ID));
            assertTrue(order.contains(AetherhavenConstants.NPC_MERCHANT));
            if (AetherhavenConstants.GUILD_MASTER_NPC_ROLE_ID.equals(order.get(0))) {
                guildFirst++;
            } else {
                merchantFirst++;
            }
        }
        assertTrue(merchantFirst > 50, "expected merchant to lead sometimes, got " + merchantFirst);
        assertTrue(guildFirst > 50, "expected guild master to lead sometimes, got " + guildFirst);
        assertTrue(guildFirst < 350, "guild master should not dominate first slot, got " + guildFirst);
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
