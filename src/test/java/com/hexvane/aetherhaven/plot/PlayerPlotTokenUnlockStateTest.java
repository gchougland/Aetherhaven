package com.hexvane.aetherhaven.plot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("construction")
class PlayerPlotTokenUnlockStateTest {

    @Test
    void unlockPoints_startAtZero() {
        PlayerPlotTokenUnlockState state = new PlayerPlotTokenUnlockState();
        assertEquals(0, state.getUnlockPoints());
    }

    @Test
    void addUnlockPoints_incrementsTotal() {
        PlayerPlotTokenUnlockState state = new PlayerPlotTokenUnlockState();
        state.addUnlockPoints(2);
        state.addUnlockPoints(3);
        assertEquals(5, state.getUnlockPoints());
    }

    @Test
    void addUnlockPoints_ignoresNonPositiveAmounts() {
        PlayerPlotTokenUnlockState state = new PlayerPlotTokenUnlockState();
        state.addUnlockPoints(0);
        state.addUnlockPoints(-1);
        assertEquals(0, state.getUnlockPoints());
    }

    @Test
    void trySpendUnlockPoint_decrementsWhenAvailable() {
        PlayerPlotTokenUnlockState state = new PlayerPlotTokenUnlockState();
        state.addUnlockPoints(2);
        assertTrue(state.trySpendUnlockPoint());
        assertEquals(1, state.getUnlockPoints());
        assertTrue(state.trySpendUnlockPoint());
        assertEquals(0, state.getUnlockPoints());
    }

    @Test
    void trySpendUnlockPoint_failsWhenEmpty() {
        PlayerPlotTokenUnlockState state = new PlayerPlotTokenUnlockState();
        assertFalse(state.trySpendUnlockPoint());
        assertEquals(0, state.getUnlockPoints());
    }

    @Test
    void clone_copiesUnlockPoints() {
        PlayerPlotTokenUnlockState state = new PlayerPlotTokenUnlockState();
        state.addUnlockPoints(4);
        state.unlock("plot_house");
        PlayerPlotTokenUnlockState copy = (PlayerPlotTokenUnlockState) state.clone();
        assertEquals(4, copy.getUnlockPoints());
        assertTrue(copy.isUnlocked("plot_house"));
    }
}
