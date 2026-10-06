package com.hexvane.aetherhaven.tourist;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("tourist")
class TouristPortalPersistenceTest {
    @TempDir Path dir;

    @ParameterizedTest
    @ValueSource(strings = {"damaged save", "\"string instead of object\"", "[]", "{\"portals\":["})
    void malformedSaveDoesNotEscapeAndRemainsUntouched(String content) throws Exception {
        Path file = dir.resolve("tourist_portals.json");
        Files.writeString(file, content);
        var registry = new TouristPortalRegistry(null);
        assertDoesNotThrow(() -> TouristPortalPersistence.load(file, "default", registry));
        assertTrue(registry.isPersistenceBlocked());
        assertTrue(registry.allRecords().isEmpty());

        registry.put(new TouristPortalRecord());
        TouristPortalPersistence.save(file, "default", registry);
        assertEquals(content, Files.readString(file));
        assertFalse(Files.exists(dir.resolve("tourist_portals.json.tmp")));
    }

    @Test
    void missingFileAllowsSavingAndSuccessfulReloadUnblocksSaving() throws Exception {
        Path file = dir.resolve("tourist_portals.json");
        var registry = new TouristPortalRegistry(null);
        TouristPortalPersistence.load(file, "default", registry);
        assertFalse(registry.isPersistenceBlocked());
        var portal = new TouristPortalRecord();
        registry.put(portal);
        TouristPortalPersistence.save(file, "default", registry);
        assertEquals(portal.getPortalId().toString(), TouristPortalWorldFile.readOrEmpty(file).getPortals().getFirst().portalId);

        Files.writeString(file, "damaged save");
        TouristPortalPersistence.load(file, "default", registry);
        assertTrue(registry.isPersistenceBlocked());
        Files.writeString(file, "{\"portals\":[]}");
        TouristPortalPersistence.load(file, "default", registry);
        assertFalse(registry.isPersistenceBlocked());
        assertTrue(registry.allRecords().isEmpty());
        registry.put(portal);
        TouristPortalPersistence.save(file, "default", registry);
        assertEquals(1, TouristPortalWorldFile.readOrEmpty(file).getPortals().size());
    }
}
