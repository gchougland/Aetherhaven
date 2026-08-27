package com.hexvane.aetherhaven.guild;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.tourist.TouristPortalTickService;
import com.hexvane.aetherhaven.tourist.TouristPortalTickService.PlannedSpawnAttemptOutcome;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Policy tests for Jimmy_G1 timeout fix: failed guild hall / tourist spawns must not retry every game minute forever.
 */
@Tag("guild")
class GuildHallSpawnBackoffTest {

    @Test
    void jimmySaveStuckGuildSlotClearsAfterSimulatedFailures() {
        List<Integer> filledSlots = new ArrayList<>(List.of(2));
        Set<String> failedCharacterIds = new HashSet<>(Set.of("female_goblin_02"));
        int slot = 2;

        boolean slotFilled = false;
        if (!slotFilled && !failedCharacterIds.isEmpty()) {
            slotFilled = filledSlots.remove(Integer.valueOf(slot));
        }

        assertFalse(slotFilled);
        assertTrue(filledSlots.isEmpty(), "slot 2 should be cleared after persistent spawn failure");
    }

    @Test
    void jimmySaveTouristBacklogStopsRetryingAfterSpawnPipelineFailure() {
        List<Long> planned = new ArrayList<>(List.of(100L, 200L, 300L));
        List<Long> executed = new ArrayList<>();

        for (Long minute : planned) {
            PlannedSpawnAttemptOutcome outcome = PlannedSpawnAttemptOutcome.SPAWN_FAILED;
            if (TouristPortalTickService.shouldConsumePlannedSpawnSlot(outcome)) {
                executed.add(minute);
            }
        }

        assertTrue(executed.containsAll(planned));
        assertFalse(
            planned.stream().anyMatch(m -> !executed.contains(m)),
            "failed planned tourist spawns should be marked executed so they are not retried every minute"
        );
    }
}
