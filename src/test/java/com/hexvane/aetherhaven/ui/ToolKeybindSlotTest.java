package com.hexvane.aetherhaven.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class ToolKeybindSlotTest {

    @Test
    void rowUiPath_usesSharedAppendTemplate() {
        assertEquals("PrimaryItemAction", ToolKeybindSlot.PRIMARY.inputBindingKey());
        assertEquals("Aetherhaven/ToolHudHotkeyRow.ui", ToolKeybindSlot.PRIMARY.rowUiPath());
        assertEquals("LMB", ToolKeybindSlot.PRIMARY.defaultLabel());
        assertEquals("BlockInteractAction", ToolKeybindSlot.USE.inputBindingKey());
        assertEquals("F", ToolKeybindSlot.USE.defaultLabel());
    }

    @Test
    void fromAbstractKey_mapsLegacyHintTokens() {
        assertEquals(ToolKeybindSlot.USE, ToolKeybindSlot.fromAbstractKey("F"));
        assertEquals(ToolKeybindSlot.MOVEMENT, ToolKeybindSlot.fromAbstractKey("WASD"));
        assertNotNull(ToolKeybindSlot.fromAbstractKey("Primary"));
    }
}
