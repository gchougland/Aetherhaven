package com.hexvane.aetherhaven.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.rts.ui.RtsHudHotkeyHints;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.packets.interface_.CustomUICommand;
import com.hypixel.hytale.protocol.packets.interface_.CustomUICommandType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** What each tool input slot is bound to, the action name shown for it, and the rows built from it. */
class ToolKeybindSlotTest {

    private static List<String> shape(UICommandBuilder builder) {
        List<String> out = new ArrayList<>();
        for (CustomUICommand command : builder.getCommands()) {
            out.add(command.type + " " + command.selector + (command.type == CustomUICommandType.Append ? " " + command.text : ""));
        }
        return out;
    }

    @Test
    void eachToolSlotIsTheGameInputTheToolItemsBind() {
        assertEquals(InteractionType.Primary, ToolKeybindSlot.PRIMARY.interactionType());
        assertEquals(InteractionType.Secondary, ToolKeybindSlot.SECONDARY.interactionType());
        assertEquals(InteractionType.Use, ToolKeybindSlot.USE.interactionType());
        assertEquals(InteractionType.Pick, ToolKeybindSlot.PICK.interactionType());
        // The extra tool actions follow ToolAbilityKeys, the one place that says which inputs a held item still owns.
        assertEquals(ToolAbilityKeys.FIRST, ToolKeybindSlot.ABILITY1.interactionType());
        assertEquals(ToolAbilityKeys.SECOND, ToolKeybindSlot.ABILITY2.interactionType());
        assertEquals(ToolAbilityKeys.THIRD, ToolKeybindSlot.ABILITY3.interactionType());
    }

    @Test
    void theClientHasAKeyPromptForEveryToolSlotAndNoneForPlainGameInputs() {
        // Read from the 0.7.0-pre.5.1 client: its HUD lists a prompt only for these eight interaction types.
        Set<InteractionType> prompted = EnumSet.of(
            InteractionType.Primary,
            InteractionType.Secondary,
            InteractionType.Ability1,
            InteractionType.Ability2,
            InteractionType.Ability3,
            InteractionType.Ability4,
            InteractionType.Use,
            InteractionType.Pick
        );
        Set<ToolKeybindSlot> plain = EnumSet.of(
            ToolKeybindSlot.ESCAPE, ToolKeybindSlot.SHIFT, ToolKeybindSlot.CTRL,
            ToolKeybindSlot.SPACE, ToolKeybindSlot.MOVEMENT, ToolKeybindSlot.CROUCH);
        for (ToolKeybindSlot slot : ToolKeybindSlot.values()) {
            if (plain.contains(slot)) {
                assertFalse(slot.hasKeyPrompt(), slot + " is not an item interaction");
                assertNull(slot.interactionType(), slot.name());
                continue;
            }
            assertTrue(slot.hasKeyPrompt(), slot.name());
            assertTrue(prompted.contains(slot.interactionType()), slot + " has no key prompt in the client");
            assertFalse(
                ToolAbilityKeys.RUNE_OWNED.contains(slot.interactionType()),
                slot + " is an input the game gives to rune abilities"
            );
        }
    }

    @Test
    void onlyTheThirdActionIsACrouchChordAndItSharesTheFirstInput() {
        for (ToolKeybindSlot slot : ToolKeybindSlot.values()) {
            if (slot == ToolKeybindSlot.ABILITY3) {
                assertTrue(slot.isCrouchChord());
                assertSame(ToolKeybindSlot.ABILITY1, slot.chordBase());
                assertSame(ToolKeybindSlot.CROUCH, slot.chordModifier());
            } else {
                assertFalse(slot.isCrouchChord(), slot.name());
                assertNull(slot.chordBase(), slot.name());
                assertNull(slot.chordModifier(), slot.name());
            }
        }
    }

    @Test
    void actionNameKeysFollowTheInputNotAKey() {
        assertEquals("primary", ToolKeybindSlot.PRIMARY.langSuffix());
        assertEquals("secondary", ToolKeybindSlot.SECONDARY.langSuffix());
        assertEquals("use", ToolKeybindSlot.USE.langSuffix());
        assertEquals("ability1", ToolKeybindSlot.ABILITY1.langSuffix());
        assertEquals("ability4", ToolKeybindSlot.ABILITY2.langSuffix());
        assertEquals("pick", ToolKeybindSlot.PICK.langSuffix());
        // A chord has no prompt of its own. It is named after the input it shares.
        assertEquals(ToolKeybindSlot.ABILITY1.langSuffix(), ToolKeybindSlot.ABILITY3.langSuffix());

        assertEquals(
            "aetherhaven_tool_keys.aetherhaven.toolKeys.pathTool.ability4",
            ToolKeyPrompts.PATH_TOOL.promptKey(ToolKeybindSlot.ABILITY2)
        );
        assertEquals(
            ToolKeyPrompts.PATH_TOOL.promptKey(InteractionType.Ability1),
            ToolKeyPrompts.PATH_TOOL.promptKey(ToolKeybindSlot.ABILITY3)
        );
        assertEquals(
            "aetherhaven_tool_keys.aetherhaven.toolKeys.buildingStaff.use",
            ToolKeyPrompts.BUILDING_STAFF.promptKey(InteractionType.Use)
        );
        // A plain game input is named the same way for every tool: by the game's own name for it.
        assertEquals(
            "aetherhaven_tool_keys.aetherhaven.toolKeys.generic.sprint",
            ToolKeyPrompts.PATROL_WAND.promptKey(ToolKeybindSlot.SHIFT)
        );
    }

    @Test
    void aRowShowsTheToolsActionNameAndADescription() {
        UICommandBuilder b = new UICommandBuilder();
        ToolHudHotkeyRows.appendRow(b, "#Rows", 0, ToolKeyPrompts.PATROL_WAND, ToolKeybindSlot.SECONDARY, Message.raw("Add a point"));
        assertEquals(
            List.of(
                "Append #Rows " + ToolHudHotkeyRows.ROW_UI,
                "Set #Rows[0] #KeyLabel.TextSpans",
                "Set #Rows[0] #DescLabel.TextSpans"),
            shape(b));
        String label = b.getCommands()[1].data;
        assertTrue(label.contains(ToolKeyPrompts.PATROL_WAND.promptKey(InteractionType.Secondary)), label);
    }

    @Test
    void aChordRowShowsCrouchAndTheActionNameNeverAKey() {
        UICommandBuilder b = new UICommandBuilder();
        ToolHudHotkeyRows.appendRow(b, "#Rows", 2, ToolKeyPrompts.PATH_TOOL, ToolKeybindSlot.ABILITY3, Message.raw("Choose path style"));
        assertEquals(
            List.of(
                "Append #Rows " + ToolHudHotkeyRows.MODIFIER_ROW_UI,
                "Set #Rows[2] #ModifierLabel.TextSpans",
                "Set #Rows[2] #KeyLabel.TextSpans",
                "Set #Rows[2] #DescLabel.TextSpans"),
            shape(b));
        CustomUICommand[] commands = b.getCommands();
        assertTrue(commands[1].data.contains(ToolKeyPrompts.CROUCH_MODIFIER_KEY), commands[1].data);
        assertTrue(commands[2].data.contains(ToolKeyPrompts.PATH_TOOL.promptKey(InteractionType.Ability1)), commands[2].data);
    }

    @Test
    void aCallerThatNamesNoToolStillGetsARowThatIsNeverWrong() {
        // The signature other code already calls. Its label is the game's own name for the input.
        UICommandBuilder b = new UICommandBuilder();
        int next = ToolHudHotkeyRows.appendHotkeyRow(b, "#Rows", 0, ToolKeybindSlot.ABILITY2, "some.description", null);
        next = ToolHudHotkeyRows.appendHotkeyRow(b, "#Rows", next, ToolKeybindSlot.ABILITY3, "some.description", null);
        next = ToolHudHotkeyRows.appendHotkeyRow(b, "#Rows", next, ToolKeybindSlot.SHIFT, "some.description", null);
        assertEquals(3, next);
        CustomUICommand[] commands = b.getCommands();
        assertEquals(
            List.of(
                "Append #Rows " + ToolHudHotkeyRows.ROW_UI,
                "Set #Rows[0] #KeyLabel.TextSpans",
                "Set #Rows[0] #DescLabel.TextSpans",
                "Append #Rows " + ToolHudHotkeyRows.MODIFIER_ROW_UI,
                "Set #Rows[1] #ModifierLabel.TextSpans",
                "Set #Rows[1] #KeyLabel.TextSpans",
                "Set #Rows[1] #DescLabel.TextSpans",
                "Append #Rows " + ToolHudHotkeyRows.ROW_UI,
                "Set #Rows[2] #KeyLabel.TextSpans",
                "Set #Rows[2] #DescLabel.TextSpans"),
            shape(b));
        assertTrue(commands[1].data.contains("toolKeys.generic.ability4"), commands[1].data);
        assertTrue(commands[5].data.contains("toolKeys.generic.ability1"), commands[5].data);
        assertTrue(commands[8].data.contains("toolKeys.generic.sprint"), commands[8].data);
    }

    @Test
    void theCommandFlagInHandGetsARowForEveryInputWithTheFlagsActionNames() {
        // The in-hand Command Flag panel: secondary, first key, second key, first key while crouching, primary.
        UICommandBuilder b = new UICommandBuilder();
        RtsHudHotkeyHints.appendFieldFlagRows(b, "#ToolHelpRows", null);
        List<String> expected = new ArrayList<>(List.of("Clear #ToolHelpRows"));
        for (int row = 0; row < 5; row++) {
            boolean chord = row == 3;
            expected.add("Append #ToolHelpRows " + (chord ? ToolHudHotkeyRows.MODIFIER_ROW_UI : ToolHudHotkeyRows.ROW_UI));
            if (chord) {
                expected.add("Set #ToolHelpRows[" + row + "] #ModifierLabel.TextSpans");
            }
            expected.add("Set #ToolHelpRows[" + row + "] #KeyLabel.TextSpans");
            expected.add("Set #ToolHelpRows[" + row + "] #DescLabel.TextSpans");
        }
        assertEquals(expected, shape(b));
        List<String> labels = new ArrayList<>();
        List<String> descriptions = new ArrayList<>();
        for (CustomUICommand command : b.getCommands()) {
            if (command.selector != null && command.selector.endsWith("#KeyLabel.TextSpans")) {
                labels.add(command.data);
            }
            if (command.selector != null && command.selector.endsWith("#DescLabel.TextSpans")) {
                descriptions.add(command.data);
            }
        }
        List<InteractionType> inputs = List.of(
            InteractionType.Secondary, InteractionType.Ability1, InteractionType.Ability4, InteractionType.Ability1, InteractionType.Primary);
        List<String> texts = List.of("send", "orderMode", "fieldStop", "fieldRelease", "fieldReady");
        for (int row = 0; row < 5; row++) {
            assertTrue(labels.get(row).contains(ToolKeyPrompts.RTS_FLAG.promptKey(inputs.get(row))), labels.get(row));
            assertTrue(
                descriptions.get(row).contains("aetherhaven_rts.aetherhaven.rts.helpFlag." + texts.get(row)),
                descriptions.get(row));
        }
    }
}
