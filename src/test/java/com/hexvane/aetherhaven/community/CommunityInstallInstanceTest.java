package com.hexvane.aetherhaven.community;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("construction")
class CommunityInstallInstanceTest {
    @TempDir
    Path tempDir;

    @Test
    void loadOrCreatePersistsSameId() {
        String first = CommunityInstallInstance.loadOrCreate(tempDir);
        String second = CommunityInstallInstance.loadOrCreate(tempDir);

        assertNotNull(first);
        assertEquals(first, second);
        UUID.fromString(first);
        assertTrue(Files.isRegularFile(CommunityInstallInstance.instanceFile(tempDir)));
    }

    @Test
    void differentDataDirectoriesGetDifferentIds(@TempDir Path otherDir) {
        String first = CommunityInstallInstance.loadOrCreate(tempDir);
        String second = CommunityInstallInstance.loadOrCreate(otherDir);

        assertNotNull(first);
        assertNotNull(second);
        assertNotEquals(first, second);
    }
}
