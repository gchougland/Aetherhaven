package com.hexvane.aetherhaven.townsfolk;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class TownsfolkPoolPersistenceTest {
    @TempDir Path dir;

    @Test void unloadSavesCheckoutsAndReloadReadsDiskIntoAFreshPool() throws Exception {
        String world = UUID.randomUUID().toString();
        UUID town = UUID.randomUUID();
        Path file = dir.resolve("townsfolk_pool.json");
        try {
            var original = TownsfolkPoolPersistence.getOrLoad(world, file);
            original.checkout(record("guard", town));
            assertSame(original, TownsfolkPoolPersistence.getOrLoad(world, file));
            TownsfolkPoolPersistence.unloadWorld(world, file);
            assertTrue(Files.exists(file));
            var reloaded = TownsfolkPoolPersistence.getOrLoad(world, file);
            assertNotSame(original, reloaded, "Unloading must not reinstall the saved pool in the cache");
            assertTrue(reloaded.isCheckedOut(town, "guard"));
            reloaded.releaseForTown(town);
            TownsfolkPoolPersistence.unloadWorld(world, file);
            assertFalse(TownsfolkPoolPersistence.getOrLoad(world, file).isCheckedOut(town, "guard"));
        } finally {
            TownsfolkPoolPersistence.unloadWorld(world, null);
        }
    }

    @Test void ephemeralWorldDropsItsPoolWithoutWritingAndDoesNotEvictAnotherWorld() {
        String ephemeral = UUID.randomUUID().toString();
        String other = UUID.randomUUID().toString();
        UUID town = UUID.randomUUID();
        try {
            var pool = TownsfolkPoolPersistence.getOrLoad(ephemeral, null);
            var neighbor = TownsfolkPoolPersistence.getOrLoad(other, null);
            pool.checkout(record("tourist", town));
            TownsfolkPoolPersistence.unloadWorld(ephemeral, null);
            assertNotSame(pool, TownsfolkPoolPersistence.getOrLoad(ephemeral, null));
            assertTrue(TownsfolkPoolPersistence.getOrLoad(ephemeral, null).getCheckouts().isEmpty());
            assertSame(neighbor, TownsfolkPoolPersistence.getOrLoad(other, null));
        } finally {
            TownsfolkPoolPersistence.unloadWorld(ephemeral, null);
            TownsfolkPoolPersistence.unloadWorld(other, null);
        }
    }

    private static TownsfolkPoolCheckoutRecord record(String id, UUID town) {
        return new TownsfolkPoolCheckoutRecord(id, town.toString(), UUID.randomUUID().toString(),
            TownsfolkAssignmentKinds.TOURIST, "");
    }
}
