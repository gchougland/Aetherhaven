package com.hexvane.aetherhaven.command;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
class AetherhavenTestTownCommandTest {
    /**
     * The engine refuses two forms of one command that take the same number of words. {@code town},
     * {@code town <action>} and {@code town <action> <owner>} must all register.
     */
    @Test
    void theThreeFormsOfTheCommandRegisterSideBySide() {
        AetherhavenTestTownCommand command = assertDoesNotThrow(AetherhavenTestTownCommand::new);
        assertEquals("town", command.getName());
    }

    /** Like every other test command it is for operators only. */
    @Test
    void itNeedsTheOperatorPermissionGroup() {
        assertEquals(java.util.List.of("hytale:WorldEditor"), new AetherhavenTestTownCommand().getPermissionGroups());
    }

    /**
     * The test command tree cannot be built without a running plugin, so its source is read instead. This guards the one
     * line that makes {@code /ah test town} exist against being lost in a merge.
     */
    @Test
    void itIsRegisteredInTheOperatorTestCommandTree() throws IOException {
        String tree = Files.readString(Path.of("src/main/java/com/hexvane/aetherhaven/command/AetherhavenTestCommand.java"));
        assertTrue(tree.contains("addSubCommand(new AetherhavenTestTownCommand());"), "ah test town is not registered");
    }
}
