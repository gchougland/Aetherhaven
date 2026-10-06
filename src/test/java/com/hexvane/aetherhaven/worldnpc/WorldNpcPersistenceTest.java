package com.hexvane.aetherhaven.worldnpc;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("worldnpc")
class WorldNpcPersistenceTest {
    @TempDir Path dir;

    @ParameterizedTest
    @ValueSource(strings = {"world_npcs.json", "world_npc_routes.json", "world_npc_players.json"})
    void malformedFileDoesNotEscapeOrOverwriteAnyNpcFiles(String brokenFile) throws Exception {
        List<String> names = List.of("world_npcs.json", "world_npc_routes.json", "world_npc_players.json");
        for (String name : names) {
            Files.writeString(dir.resolve(name), name.equals(brokenFile) ? "damaged save" : "{}");
        }
        var registry = new WorldNpcRegistry(null);
        assertDoesNotThrow(() -> WorldNpcPersistence.load(dir, "default", registry));
        assertTrue(registry.isPersistenceBlocked());
        assertTrue(registry.allPlacements().isEmpty());

        var placement = new WorldNpcPlacementRecord();
        placement.setPlacementId("new-placement");
        registry.upsertPlacement(placement);
        WorldNpcPersistence.save(dir, "default", registry);
        for (String name : names) {
            assertEquals(name.equals(brokenFile) ? "damaged save" : "{}", Files.readString(dir.resolve(name)));
        }
        assertFalse(Files.exists(dir.resolve("world_npcs.json.tmp")));
    }

    @Test
    void healthyRegistryStillSavesAndSuccessfulReloadUnblocksSaving() throws Exception {
        Path file = dir.resolve("world_npcs.json");
        Files.writeString(file, "[]");
        var broken = new WorldNpcRegistry(null);
        WorldNpcPersistence.load(dir, "default", broken);
        assertTrue(broken.isPersistenceBlocked());

        Path healthyDir = Files.createDirectory(dir.resolve("healthy"));
        var healthy = new WorldNpcRegistry(null);
        WorldNpcPersistence.load(healthyDir, "healthy", healthy);
        var placement = new WorldNpcPlacementRecord();
        placement.setPlacementId("test-npc");
        healthy.upsertPlacement(placement);
        WorldNpcPersistence.save(healthyDir, "healthy", healthy);
        assertEquals("test-npc", WorldNpcsWorldFile.readOrEmpty(healthyDir.resolve("world_npcs.json"))
            .getPlacements().getFirst().placementIdOrEmpty());

        Files.writeString(file, "{\"placements\":[{\"placementId\":\"restored\"}]}");
        WorldNpcPersistence.load(dir, "default", broken);
        assertFalse(broken.isPersistenceBlocked());
        assertNotNull(broken.findPlacement("restored"));
        WorldNpcPersistence.save(dir, "default", broken);
        assertEquals("restored", WorldNpcsWorldFile.readOrEmpty(file).getPlacements().getFirst().placementIdOrEmpty());
    }
}
