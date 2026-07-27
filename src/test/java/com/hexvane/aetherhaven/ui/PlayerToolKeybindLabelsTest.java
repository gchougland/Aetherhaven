package com.hexvane.aetherhaven.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
class PlayerToolKeybindLabelsTest {
    @Test
    void defaultsMatchHudStrings() {
        PlayerTownJournalState state = new PlayerTownJournalState();
        assertEquals("Primary", PlayerToolKeybindLabels.resolve(state, ToolKeybindSlot.PRIMARY));
        assertEquals("Q", PlayerToolKeybindLabels.resolve(state, ToolKeybindSlot.ABILITY1));
        assertEquals("WASD", PlayerToolKeybindLabels.resolve(state, ToolKeybindSlot.MOVEMENT));
    }

    @Test
    void blankStoredValueFallsBackToDefault() {
        PlayerTownJournalState state = new PlayerTownJournalState();
        state.setToolKeyLabel(ToolKeybindSlot.USE, "   ");
        assertEquals("F", PlayerToolKeybindLabels.resolve(state, ToolKeybindSlot.USE));
    }

    @Test
    void customLabelIsReturned() {
        PlayerTownJournalState state = new PlayerTownJournalState();
        state.setToolKeyLabel(ToolKeybindSlot.ABILITY1, "Mouse 4");
        assertEquals("Mouse 4", PlayerToolKeybindLabels.resolve(state, ToolKeybindSlot.ABILITY1));
    }

    @Test
    void resolveAbstractMapsLegacyKeys() {
        PlayerTownJournalState state = new PlayerTownJournalState();
        state.setToolKeyLabel(ToolKeybindSlot.ABILITY2, "E");
        assertEquals("E", PlayerToolKeybindLabels.resolveAbstract(state, "E"));
        assertEquals("Primary", PlayerToolKeybindLabels.resolveAbstract(state, "Primary"));
    }

    @Test
    void resetRestoresDefaults() {
        PlayerTownJournalState state = new PlayerTownJournalState();
        state.setToolKeyLabel(ToolKeybindSlot.SHIFT, "Alt");
        state.resetToolKeyLabels();
        assertEquals("Shift", PlayerToolKeybindLabels.resolve(state, ToolKeybindSlot.SHIFT));
    }

    @Test
    void sanitizeRejectsOverlongInput() {
        assertEquals("Q", PlayerToolKeybindLabels.sanitizeInput("abcdefghijklmnopqrstuvwxy", ToolKeybindSlot.ABILITY1));
    }
}
