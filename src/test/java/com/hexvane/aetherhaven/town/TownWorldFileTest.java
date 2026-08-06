package com.hexvane.aetherhaven.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TownWorldFileTest {

    @TempDir
    Path tempDir;

    @Test
    void compactJsonRoundTripAndWriteBytesAtomic() throws Exception {
        TownRecord town = new TownRecord(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "hexvane",
            0,
            64,
            0,
            1,
            4,
            1_700_000_000_000L
        );
        town.setDisplayName("Testville");

        byte[] json = TownWorldFile.toJsonBytes(List.of(town));
        assertFalse(new String(json, StandardCharsets.UTF_8).contains("\n  "));

        Path out = tempDir.resolve("towns.json");
        TownWorldFile.writeBytesAtomic(out, json);
        assertTrue(Files.isRegularFile(out));

        TownWorldFile loaded = TownWorldFile.readOrEmpty(out);
        assertEquals(1, loaded.getTowns().size());
        assertEquals("Testville", loaded.getTowns().get(0).getDisplayName());
    }
}
