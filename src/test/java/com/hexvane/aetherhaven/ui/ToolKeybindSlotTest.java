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

    @Test
    void scancodeToLabel_mapsKeypad() {
        assertEquals("Num3", ToolKeybindDisplay.scancodeToLabel(91));
        assertEquals("Num0", ToolKeybindDisplay.scancodeToLabel(98));
        assertEquals("NumEnter", ToolKeybindDisplay.scancodeToLabel(88));
    }

    @Test
    void parseSettings_format7SparseRemapUsesKeypadLabel() {
        String json =
            """
            {
              "FormatVersion": 7,
              "InputActions": {
                "BlockInteractAction": {
                  "Name": "BlockInteractAction",
                  "Id": 15,
                  "Bindings": [
                    { "SourceType": 2, "GamepadButton": 2 },
                    { "SourceType": 0, "Scancode": 91 }
                  ]
                }
              }
            }
            """;
        Map<String, String> overrides = ToolKeybindDisplay.parseSettingsOverrides(json);
        assertEquals("Num3", overrides.get("BlockInteractAction"));
    }
}
