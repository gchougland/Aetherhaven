package com.hexvane.aetherhaven.questboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.questboard.RaidSpawnPositionFinder.Result;
import com.hexvane.aetherhaven.questboard.RaidSpawnPositionFinder.Tier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.joml.Vector3d;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * A raid is one or the other: started, with every raider in the world counted by the raid, or failed, with no
 * raider left in the world.
 *
 * <p>{@link FakeWorld} stands in for the game. Like the game, it can refuse a spawn by returning nothing while
 * still keeping the creature, asleep, in stored terrain: that is what the game does with anything added to land
 * that is not being simulated, and it is how a raid used to be reported as failed with its raiders in the world.
 */
@Tag("raid")
class RaidWaveStartTest {
    private static final List<String> ROSTER = List.of("Goblin_A", "Goblin_B", "Trork_A", "Goblin_A", "Trork_B");

    @Test
    void withoutAPlaceForEveryRaiderNothingIsSpawned() {
        FakeWorld world = new FakeWorld();
        RaidWaveStart.Outcome none = RaidWaveStart.run(ROSTER, List.of(), world);
        assertFalse(none.started());
        assertTrue(none.raiders().isEmpty());
        assertEquals(0, world.spawnCalls, "no plan, no spawning");

        RaidWaveStart.Outcome short_ = RaidWaveStart.run(ROSTER, plan(ROSTER.size() - 1), world);
        assertFalse(short_.started());
        assertEquals(0, world.spawnCalls, "a plan for some of the raiders is not a plan");
        assertTrue(world.everything().isEmpty());

        assertFalse(RaidWaveStart.run(List.of(), plan(3), world).started());
    }

    @Test
    void aStartedRaidCountsExactlyTheRaidersThatExist() {
        FakeWorld world = new FakeWorld();
        RaidWaveStart.Outcome outcome = RaidWaveStart.run(ROSTER, plan(ROSTER.size()), world);
        assertTrue(outcome.started());
        assertNull(outcome.error());
        assertEquals(ROSTER.size(), outcome.raiders().size());
        assertEquals(world.awake.keySet(), new HashSet<>(outcome.raiders()));
        assertTrue(world.asleep.isEmpty());
        assertEquals(0, world.removeCalls);
    }

    @Test
    void aRaiderThatCannotBePlacedShrinksTheRaidButNeverLeavesAnUncountedOne() {
        FakeWorld world = new FakeWorld();
        world.refuse.add(1);
        world.refuse.add(3);
        RaidWaveStart.Outcome outcome = RaidWaveStart.run(ROSTER, plan(ROSTER.size()), world);
        assertTrue(outcome.started());
        assertEquals(3, outcome.raiders().size());
        assertEquals(world.everything(), new HashSet<>(outcome.raiders()), "what exists is what is counted");
    }

    @Test
    void whenNoRaiderCanBePlacedTheRaidHasNotStarted() {
        FakeWorld world = new FakeWorld();
        for (int i = 0; i < ROSTER.size(); i++) {
            world.refuse.add(i);
        }
        RaidWaveStart.Outcome outcome = RaidWaveStart.run(ROSTER, plan(ROSTER.size()), world);
        assertFalse(outcome.started());
        assertTrue(outcome.raiders().isEmpty());
        assertTrue(world.everything().isEmpty());
    }

    @Test
    void anErrorPartWayTakesOutEveryRaiderAlreadyPlaced() {
        FakeWorld world = new FakeWorld();
        world.explodeAt = 3;
        RaidWaveStart.Outcome outcome = RaidWaveStart.run(ROSTER, plan(ROSTER.size()), world);
        assertFalse(outcome.started());
        assertTrue(outcome.raiders().isEmpty(), "a failed raid counts nobody");
        assertNotNull(outcome.error());
        assertEquals(3, world.removeCalls);
        assertTrue(world.everything().isEmpty(), "the three placed before the error are gone");
    }

    /**
     * The reported bug. The game kept the raiders, asleep, and told the caller nothing was spawned. The old start
     * believed it, reported failure and walked away from them.
     */
    @Test
    void raidersTheGamePutToSleepDoNotOutliveAFailedRaid() {
        FakeWorld old = new FakeWorld();
        old.park.addAll(Set.of(0, 1, 2, 3, 4));
        assertFalse(legacyStart(ROSTER, old), "the old start reported failure");
        assertEquals(ROSTER.size(), old.asleep.size(), "and left every raider in the world, asleep and unmarked");
        old.wakeEverything(false, Set.of());
        assertEquals(ROSTER.size(), old.awake.size(), "they walked out when a player came near: failed, yet spawned");

        FakeWorld world = new FakeWorld();
        world.park.addAll(Set.of(0, 1, 2, 3, 4));
        RaidWaveStart.Outcome outcome = RaidWaveStart.run(ROSTER, plan(ROSTER.size()), world);
        assertFalse(outcome.started(), "nothing the raid can count, so it has not started");
        assertEquals(ROSTER.size(), world.asleep.size());
        assertTrue(world.asleep.values().stream().allMatch(tagged -> tagged), "but each one carries the raid's mark");
        // The offer went back on the board. Nothing of the raid is running when they wake.
        world.wakeEverything(false, Set.of());
        assertTrue(world.everything().isEmpty(), "so they are removed the moment they wake");
    }

    @Test
    void sleepersFromAFailedTryDoNotJoinTheRaidThatStartsOnTheNextTry() {
        FakeWorld world = new FakeWorld();
        world.park.addAll(Set.of(0, 1, 2, 3, 4));
        assertFalse(RaidWaveStart.run(ROSTER, plan(ROSTER.size()), world).started());
        Set<String> sleepers = new HashSet<>(world.asleep.keySet());

        world.park.clear();
        RaidWaveStart.Outcome second = RaidWaveStart.run(ROSTER, plan(ROSTER.size()), world);
        assertTrue(second.started());

        // The same offer is now running, but it does not list the sleepers.
        world.wakeEverything(true, new HashSet<>(second.raiders()));
        assertEquals(new HashSet<>(second.raiders()), world.everything(), "only the counted raiders remain");
        assertTrue(sleepers.stream().noneMatch(world.everything()::contains));
    }

    @Test
    void theLeftoverRuleSparesCountedRaidersAndTheDying() {
        assertFalse(RaidWaveStart.isLeftover(true, true, false), "a counted raider of a running raid stays");
        assertTrue(RaidWaveStart.isLeftover(true, false, false), "one the running raid does not list goes");
        assertTrue(RaidWaveStart.isLeftover(false, false, false), "one whose raid is not running goes");
        assertFalse(RaidWaveStart.isLeftover(true, false, true), "a raider just killed is no longer listed but must finish dying");
        assertFalse(RaidWaveStart.isLeftover(false, false, true));
    }

    // ---------------------------------------------------------------- harness

    private static List<Result> plan(int count) {
        List<Result> plan = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            plan.add(new Result(new Vector3d(200 + i * 2, 65, 0), RaidApproachDirection.EAST, Tier.OUTSKIRTS));
        }
        return plan;
    }

    /** The start as it was: a spawn that returned nothing was skipped, and no raiders counted meant failure. */
    private static boolean legacyStart(List<String> roster, FakeWorld world) {
        List<String> spawned = new ArrayList<>();
        for (int i = 0; i < roster.size(); i++) {
            String id = world.spawnUntagged(i);
            if (id == null) {
                continue;
            }
            spawned.add(id);
        }
        return !spawned.isEmpty();
    }

    private static final class FakeWorld implements RaidWaveStart.Spawner {
        /** Raiders in the simulated world. */
        final Map<String, Boolean> awake = new LinkedHashMap<>();
        /** Raiders the game stored instead. The value says whether the raid's mark was on them when they went in. */
        final Map<String, Boolean> asleep = new LinkedHashMap<>();
        /** Roster positions that cannot be placed at all: nothing is created. */
        final Set<Integer> refuse = new HashSet<>();
        /** Roster positions the game creates, stores asleep, and reports as not spawned. */
        final Set<Integer> park = new HashSet<>();
        int explodeAt = -1;
        int spawnCalls;
        int removeCalls;
        private int next;

        @Override
        public String spawn(int index, String roleId, Result spot) {
            return create(index, true);
        }

        /** The old start marked a raider only after the game handed it back, so a stored one had no mark. */
        String spawnUntagged(int index) {
            return create(index, false);
        }

        private String create(int index, boolean tagged) {
            spawnCalls++;
            if (index == explodeAt) {
                throw new IllegalStateException("spawn failed part way");
            }
            if (refuse.contains(index)) {
                return null;
            }
            String id = "raider-" + next++;
            if (park.contains(index)) {
                asleep.put(id, tagged);
                return null;
            }
            awake.put(id, tagged);
            return id;
        }

        @Override
        public void remove(String raiderId) {
            removeCalls++;
            awake.remove(raiderId);
        }

        /** A player walks up: stored raiders wake, and those the leftover rule names are taken out. */
        void wakeEverything(boolean raidRunning, Set<String> counted) {
            for (Map.Entry<String, Boolean> sleeper : asleep.entrySet()) {
                boolean tagged = sleeper.getValue();
                // Only a raider that carries the raid's mark can be recognised when it wakes.
                if (tagged && RaidWaveStart.isLeftover(raidRunning, counted.contains(sleeper.getKey()), false)) {
                    continue;
                }
                awake.put(sleeper.getKey(), tagged);
            }
            asleep.clear();
        }

        Set<String> everything() {
            Set<String> all = new HashSet<>(awake.keySet());
            all.addAll(asleep.keySet());
            return all;
        }
    }
}
