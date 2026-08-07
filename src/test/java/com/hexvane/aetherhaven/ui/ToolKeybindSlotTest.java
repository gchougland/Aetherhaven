package com.hexvane.aetherhaven.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolKeybindSlotTest {

    @Test
    void rowUiPath_usesSharedAppendTemplate() {
        assertEquals("PrimaryItemAction", ToolKeybindSlot.PRIMARY.inputBindingKey());
        assertEquals("Aetherhaven/ToolHudHotkeyRow.ui", ToolKeybindSlot.PRIMARY.rowUiPath());
        assertEquals("Aetherhaven/ToolHudModifierHotkeyRow.ui", ToolKeybindSlot.SECONDARY.modifierRowUiPath());
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

    @Test
    void parseSettings_prefersKeyboardAndIgnoresGamepad() {
        String json =
            """
            {
              "InputActions": {
                "Ability1ItemAction": {
                  "Name": "Ability1ItemAction",
                  "Bindings": [
                    { "SourceType": 2, "GamepadButton": 3 },
                    { "SourceType": 0, "Scancode": 53 }
                  ]
                }
              }
            }
            """;
        Map<String, String> overrides = ToolKeybindDisplay.parseSettingsOverrides(json);
        assertEquals("`", overrides.get("Ability1ItemAction"));
    }

    @Test
    void scancodeToLabel_mapsGrave() {
        assertEquals("`", ToolKeybindDisplay.scancodeToLabel(53));
        assertEquals("Q", ToolKeybindDisplay.scancodeToLabel(20));
        assertNull(ToolKeybindDisplay.scancodeToLabel(-1));
    }
}
