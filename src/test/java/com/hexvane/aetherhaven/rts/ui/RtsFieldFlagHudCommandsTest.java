package com.hexvane.aetherhaven.rts.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.rts.RtsOrderMode;
import com.hexvane.aetherhaven.rts.ui.RtsCommandStatusHud.FieldGuards;
import com.hypixel.hytale.protocol.packets.interface_.CustomUICommand;
import com.hypixel.hytale.protocol.packets.interface_.CustomUICommandType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The guard command panel as it is sent while the Command Flag is carried in hand: which commands, in which order,
 * and that every element they name is in the documents they are sent to. A selector that matches nothing disconnects
 * the player, so each one is checked against the panel and its two row templates.
 */
@Tag("entity")
class RtsFieldFlagHudCommandsTest {
    private static final Path UI = Path.of("src/main/resources/Common/UI/Custom/Aetherhaven");
    private static final Path LANG = Path.of("src/main/resources/Server/Languages/en-US/aetherhaven_rts.lang");
    private static final String ROW = "Aetherhaven/ToolHudHotkeyRow.ui";
    private static final String CHORD_ROW = "Aetherhaven/ToolHudModifierHotkeyRow.ui";
    private static final Pattern ID = Pattern.compile("#([A-Za-z0-9_\\-]+)");

    private static List<String> shape(CustomUICommand[] commands) {
        List<String> out = new ArrayList<>();
        for (CustomUICommand command : commands) {
            out.add(command.type + " " + command.selector + (command.type == CustomUICommandType.Append ? " " + command.text : ""));
        }
        return out;
    }

    @Test
    void thePanelSendsTheOrderLineTheGuardsLineAndARowForEveryInput() {
        CustomUICommand[] commands = RtsCommandStatusHud.fieldCommands(RtsOrderMode.MOVE, FieldGuards.LISTENING, 4, 2, null).getCommands();
        List<String> expected = new ArrayList<>(List.of(
            "Set #HudTitle.TextSpans",
            "Set #OrderModeLine.TextSpans",
            "Set #StanceLine.Visible",
            "Set #SelectedLine.TextSpans",
            "Clear #ToolHelpRows"
        ));
        // Secondary, first tool key, second tool key, first tool key while crouching, primary.
        for (int row = 0; row < 5; row++) {
            boolean chord = row == 3;
            expected.add("Append #ToolHelpRows " + (chord ? CHORD_ROW : ROW));
            if (chord) {
                expected.add("Set #ToolHelpRows[" + row + "] #ModifierLabel.TextSpans");
            }
            expected.add("Set #ToolHelpRows[" + row + "] #KeyLabel.TextSpans");
            expected.add("Set #ToolHelpRows[" + row + "] #DescLabel.TextSpans");
        }
        expected.add("Clear #ControlsRows");
        expected.add("Set #ControlsRows.Visible");
        assertEquals(expected, shape(commands));

        assertTrue(commands[1].data.contains("aetherhaven_rts.aetherhaven.rts.orderMove"), commands[1].data);
        assertTrue(commands[2].data.contains("false"), "the stance line is hidden: " + commands[2].data);
        assertTrue(commands[3].data.contains("aetherhaven_rts.aetherhaven.rts.hudFieldUnderOrders"), commands[3].data);
        assertTrue(commands[3].data.contains("4") && commands[3].data.contains("2"), "guards and guards under orders: " + commands[3].data);
        assertTrue(commands[commands.length - 1].data.contains("false"), "the camera rows are hidden");
    }

    @Test
    void everyRowDescribesItsKeyInEnglish() throws IOException {
        String lang = Files.readString(LANG);
        CustomUICommand[] commands = RtsCommandStatusHud.fieldCommands(RtsOrderMode.ATTACK_MOVE, FieldGuards.LISTENING, 1, 0, null).getCommands();
        List<String> described = new ArrayList<>();
        for (CustomUICommand command : commands) {
            if (command.selector != null && command.selector.endsWith("#KeyLabel.TextSpans")) {
                assertFalse(command.data == null || command.data.isBlank(), "a row with no key shown");
            }
            if (command.selector != null && command.selector.endsWith("#DescLabel.TextSpans")) {
                Matcher key = Pattern.compile("aetherhaven_rts\\.(aetherhaven\\.rts\\.helpFlag\\.[A-Za-z]+)").matcher(command.data);
                assertTrue(key.find(), "a row with no description: " + command.data);
                assertTrue(lang.contains(key.group(1) + "="), "no English text for " + key.group(1));
                described.add(key.group(1).substring("aetherhaven.rts.helpFlag.".length()));
            }
        }
        assertEquals(List.of("send", "orderMode", "fieldStop", "fieldRelease", "fieldReady"), described);
    }

    @Test
    void everyElementThePanelNamesIsInTheDocumentItIsSentTo() throws IOException {
        String panel = Files.readString(UI.resolve("RtsCommandStatusHud.ui"));
        String row = Files.readString(UI.resolve("ToolHudHotkeyRow.ui"));
        String chordRow = Files.readString(UI.resolve("ToolHudModifierHotkeyRow.ui"));
        for (FieldGuards state : FieldGuards.values()) {
            CustomUICommand[] commands = RtsCommandStatusHud.fieldCommands(RtsOrderMode.ATTACK_MOVE, state, 0, 0, null).getCommands();
            String current = row;
            int checked = 0;
            for (CustomUICommand command : commands) {
                if (command.type == CustomUICommandType.Append) {
                    current = CHORD_ROW.equals(command.text) ? chordRow : row;
                    assertTrue(CHORD_ROW.equals(command.text) || ROW.equals(command.text), "unknown row document " + command.text);
                }
                Matcher ids = ID.matcher(command.selector);
                boolean first = true;
                while (ids.find()) {
                    String id = ids.group(1);
                    assertTrue(id.matches("[A-Za-z0-9]+"), "element ids are letters and digits only: " + id);
                    // The first id of a selector is in the panel; an id after a row index is in that row's document.
                    String document = first ? panel : current;
                    assertTrue(Pattern.compile("#" + id + "\\b").matcher(document).find(), "#" + id + " is not in the document for " + command.selector);
                    first = false;
                    checked++;
                }
            }
            assertTrue(checked >= 20, state + " checked only " + checked + " ids");
        }
    }
}
