package com.hexvane.aetherhaven.town;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
class TownSaveTimingTest {
    @Test void saveBecomesDueAfterThreeSeconds() {
        var pending = new TownSaveCoordinator.PendingSave(1000);
        assertFalse(pending.isDue(3999));
        assertTrue(pending.isDue(4000));
        assertTrue(pending.isDue(30_000));
    }

    @Test void immediateSaveDoesNotWaitForDebounce() {
        var pending = new TownSaveCoordinator.PendingSave(1000);
        pending.immediate = true;
        assertTrue(pending.isDue(1000));
    }
}
