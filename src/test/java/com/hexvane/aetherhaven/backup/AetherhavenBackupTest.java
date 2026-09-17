package com.hexvane.aetherhaven.backup;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownWorldFile;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.server.core.universe.StorageManager;
import com.hypixel.hytale.server.core.universe.resources.DiskUniverseResourceStorageProvider.DiskUniverseResourceStorage;
import com.hypixel.hytale.server.core.universe.resources.UniverseResources;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipInputStream;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("town")
class AetherhavenBackupTest {
    @TempDir Path temp;
    private final java.util.List<AetherhavenBackupWorker> workers = new java.util.ArrayList<>();
    @org.junit.jupiter.api.AfterEach void stopWorkers() { workers.forEach(AetherhavenBackupWorker::close); }

    private Path data() throws IOException { return Files.createDirectories(temp.resolve("mods/Hexvane_Aetherhaven")); }
    private Path archive() { return temp.resolve("universe").resolve(AetherhavenBackupResource.ARCHIVE_PATH); }

    private static void write(Path root, String name, String value) throws IOException {
        Path path = root.resolve(name);
        Files.createDirectories(path.getParent());
        Files.writeString(path, value);
    }

    private static Map<String, byte[]> unzip(byte[] zip) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (var input = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
                if (entry.isDirectory()) continue;
                assertNull(entries.put(entry.getName(), input.readAllBytes()), "Duplicate ZIP entry");
            }
        }
        return entries;
    }

    @Test void capturesAllWorldsCustomContentBinaryFilesAndBackupCopies() throws Exception {
        Path data = data();
        write(data, "worlds/default/towns.json", "{\"towns\":[]}");
        write(data, "worlds/second/props.json", "{\"props\":[]}");
        write(data, "worlds/default/towns.json.bak", "previous town data");
        write(data, "custom/木.prefab.json", "{\"name\":\"café\"}");
        write(data, "config.json", "{\"setting\":true}");
        byte[] binary = new byte[] {0, -1, 2, 3, 100};
        Files.write(data.resolve("icon.png"), binary);
        write(data, "worlds/default/towns.json.tmp", "half written");
        write(data, "LOCK", "locked");
        String hash = AetherhavenBackupArchive.writeSnapshot(data, archive());
        byte[] bytes = Files.readAllBytes(archive());
        assertEquals(hash, AetherhavenBackupArchive.sha256(bytes));
        var entries = unzip(bytes);
        assertEquals(7, entries.size());
        assertArrayEquals(binary, entries.get("data/icon.png"));
        assertTrue(entries.containsKey("data/custom/木.prefab.json"));
        assertTrue(entries.containsKey("data/worlds/second/props.json"));
        assertTrue(entries.containsKey("data/worlds/default/towns.json.bak"));
        assertFalse(entries.containsKey("data/LOCK"));
        assertFalse(entries.containsKey("data/worlds/default/towns.json.tmp"));
        var manifest = JsonParser.parseString(new String(entries.get(AetherhavenBackupArchive.MANIFEST), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(1, manifest.get("formatVersion").getAsInt());
        assertNotNull(java.time.Instant.parse(manifest.get("capturedAtUtc").getAsString()));
        for (var file : manifest.getAsJsonObject("files").entrySet()) {
            assertEquals(file.getValue().getAsString(), AetherhavenBackupArchive.sha256(entries.get(file.getKey())));
        }
    }

    @Test void replacesChangedFilesAndDoesNotResurrectDeletedFiles() throws Exception {
        Path data = data();
        write(data, "worlds/old/towns.json", "{\"towns\":[]}");
        write(data, "config.json", "{\"revision\":1}");
        AetherhavenBackupArchive.writeSnapshot(data, archive());
        Files.delete(data.resolve("worlds/old/towns.json"));
        write(data, "config.json", "{\"revision\":2}");
        AetherhavenBackupArchive.writeSnapshot(data, archive());
        var entries = unzip(Files.readAllBytes(archive()));
        assertFalse(entries.containsKey("data/worlds/old/towns.json"));
        assertEquals("{\"revision\":2}", new String(entries.get("data/config.json"), StandardCharsets.UTF_8));
    }

    @Test void corruptSourcePreservesPreviousUsableSnapshot() throws Exception {
        Path data = data();
        write(data, "worlds/default/towns.json", "{\"towns\":[]}");
        AetherhavenBackupArchive.writeSnapshot(data, archive());
        byte[] before = Files.readAllBytes(archive());
        write(data, "worlds/default/towns.json", "{\"towns\":[");
        assertThrows(IOException.class, () -> AetherhavenBackupArchive.writeSnapshot(data, archive()));
        assertArrayEquals(before, Files.readAllBytes(archive()));
        try (var paths = Files.list(archive().getParent())) {
            assertEquals(List.of(archive()), paths.toList());
        }
    }

    @Test void missingDataDirectoryCannotReplaceSnapshotWithEmptyData() throws Exception {
        Path data = data();
        write(data, "config.json", "{}");
        AetherhavenBackupArchive.writeSnapshot(data, archive());
        byte[] before = Files.readAllBytes(archive());
        assertThrows(IOException.class, () -> AetherhavenBackupArchive.writeSnapshot(temp.resolve("missing"), archive()));
        assertArrayEquals(before, Files.readAllBytes(archive()));
    }

    @Test void emptyNewModDirectoryProducesAValidEmptyArchive() throws Exception {
        var entries = unzip(AetherhavenBackupArchive.capture(data()));
        assertEquals(List.of(AetherhavenBackupArchive.MANIFEST), List.copyOf(entries.keySet()));
    }

    @Test void rejectsRecursiveDestination() throws Exception {
        Path data = data();
        assertThrows(IOException.class, () -> AetherhavenBackupArchive.writeSnapshot(data, data.resolve("backups/copy.zip")));
        assertFalse(Files.exists(data.resolve("backups")));
    }

    @Test void snapshotWaitsForTownAtomicWriteLock() throws Exception {
        Path data = data();
        Path town = data.resolve("worlds/default/towns.json");
        write(data, "worlds/default/towns.json", "{\"towns\":[]}");
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            java.util.concurrent.Future<byte[]> snapshot;
            var started = new java.util.concurrent.CountDownLatch(1);
            synchronized (TownWorldFile.class) {
                snapshot = executor.submit(() -> { started.countDown(); return AetherhavenBackupArchive.capture(data); });
                assertTrue(started.await(5, TimeUnit.SECONDS));
                assertFalse(snapshot.isDone());
                TownWorldFile.writeBytesAtomic(town, "{\"towns\":[],\"revision\":2}".getBytes(StandardCharsets.UTF_8));
            }
            assertTrue(new String(unzip(snapshot.get(5, TimeUnit.SECONDS)).get("data/worlds/default/towns.json"), StandardCharsets.UTF_8).contains("\"revision\":2"));
        }
    }

    private record Fixture(UniverseResources resources, StorageManager storage, AtomicBoolean locked, Path marker,
                           AetherhavenBackupWorker worker) {}

    private static void prepareAndFlush(Fixture fixture) throws Exception {
        fixture.worker.request().get(30, TimeUnit.SECONDS);
        fixture.resources.flushAll().get(10, TimeUnit.SECONDS);
    }

    private Fixture resourceFixture(Path data) {
        var worker = new AetherhavenBackupWorker(data, archive(), temp, () -> {});
        workers.add(worker);
        var codec = AetherhavenBackupResource.codec(() -> worker);
        String id = "AetherhavenBackupTest" + UUID.randomUUID().toString().replace("-", "");
        var type = UniverseResources.register(AetherhavenBackupResource.class, id, codec);
        AtomicBoolean locked = new AtomicBoolean();
        var storage = new StorageManager(locked::get);
        Path resourcesPath = temp.resolve("universe/resources");
        var resources = new UniverseResources(new DiskUniverseResourceStorage(resourcesPath, storage, true));
        resources.load(List.of(type));
        return new Fixture(resources, storage, locked, resourcesPath.resolve(id + ".json"), worker);
    }

    @Test void vanillaResourceFlushPublishesCompletedSnapshot() throws Exception {
        Path data = data();
        write(data, "worlds/default/towns.json", "{\"towns\":[]}");
        var fixture = resourceFixture(data);
        prepareAndFlush(fixture);
        assertTrue(Files.isRegularFile(archive()));
        var marker = JsonParser.parseString(Files.readString(fixture.marker)).getAsJsonObject();
        assertEquals(AetherhavenBackupArchive.sha256(Files.readAllBytes(archive())), marker.get("SnapshotSha256").getAsString());
    }

    @Test void vanillaSavingLockDefersTheEntireSnapshotUntilBackupFinishes() throws Exception {
        Path data = data();
        write(data, "config.json", "{\"revision\":1}");
        var fixture = resourceFixture(data);
        prepareAndFlush(fixture);
        byte[] before = Files.readAllBytes(archive());
        fixture.locked.set(true);
        write(data, "config.json", "{\"revision\":2}");
        fixture.worker.request().get(10, TimeUnit.SECONDS);
        var queued = fixture.resources.flushAll();
        assertFalse(queued.isDone());
        assertArrayEquals(before, Files.readAllBytes(archive()));
        fixture.storage.pendingOperations().get(5, TimeUnit.SECONDS);
        fixture.locked.set(false);
        fixture.storage.onSavingRelease();
        queued.get(10, TimeUnit.SECONDS);
        assertEquals("{\"revision\":2}", new String(unzip(Files.readAllBytes(archive())).get("data/config.json"), StandardCharsets.UTF_8));
    }

    @Test void failedBackgroundPreparationPreservesArchiveAndMarker() throws Exception {
        Path data = data();
        write(data, "config.json", "{}");
        var fixture = resourceFixture(data);
        prepareAndFlush(fixture);
        byte[] before = Files.readAllBytes(archive());
        String marker = Files.readString(fixture.marker);
        write(data, "config.json", "{");
        var logger = com.hypixel.hytale.logger.HytaleLogger.get("AetherhavenBackupWorker");
        var previousLevel = logger.getLevel();
        try {
            // This deliberately corrupt input should fail without printing a misleading SEVERE stack trace.
            logger.setLevel(java.util.logging.Level.OFF);
            var failure = assertThrows(java.util.concurrent.ExecutionException.class,
                () -> fixture.worker.request().get(10, TimeUnit.SECONDS));
            var cause = assertInstanceOf(IOException.class, failure.getCause());
            assertEquals("Invalid JSON in backup source: " + data.resolve("config.json"), cause.getMessage());
            assertInstanceOf(java.io.EOFException.class, cause.getCause());
        } finally {
            // The future completes before the worker logs; drain it before restoring the logger.
            try {
                fixture.worker.close();
            } finally {
                logger.setLevel(previousLevel);
            }
        }
        assertArrayEquals(before, Files.readAllBytes(archive()));
        assertEquals(marker, Files.readString(fixture.marker));
    }

    @Test void readingRestoredResourceDoesNotOverwriteLiveModFiles() throws Exception {
        Path data = data();
        write(data, "config.json", "{\"live\":true}");
        var codec = AetherhavenBackupResource.codec(() -> null);
        assertNotNull(codec.decode(BsonDocument.parse("{\"SnapshotSha256\":\"old-backup\"}"), new ExtraInfo()));
        assertEquals("{\"live\":true}", Files.readString(data.resolve("config.json")));
        assertFalse(Files.exists(archive()));
    }

    @Test void codecValidationDoesNotCreateASnapshot() throws Exception {
        Path data = data();
        write(data, "config.json", "{}");
        var codec = AetherhavenBackupResource.codec(() -> null);
        codec.validateDefaults(new ExtraInfo(), new java.util.HashSet<>());
        assertFalse(Files.exists(archive()));
    }

    @Test void unchangedFlushReusesZipButMissingZipIsRebuilt() throws Exception {
        Path data = data();
        write(data, "config.json", "{}");
        var fixture = resourceFixture(data);
        prepareAndFlush(fixture);
        byte[] first = Files.readAllBytes(archive());
        var modified = Files.getLastModifiedTime(archive());
        prepareAndFlush(fixture);
        assertEquals(modified, Files.getLastModifiedTime(archive()));
        assertArrayEquals(first, Files.readAllBytes(archive()));
        Files.delete(archive());
        prepareAndFlush(fixture);
        assertEquals("{}", new String(unzip(Files.readAllBytes(archive())).get("data/config.json"), StandardCharsets.UTF_8));
    }

    @Test void damagedArchiveIsRebuiltOnNextFlush() throws Exception {
        Path data = data();
        write(data, "config.json", "{}");
        var fixture = resourceFixture(data);
        prepareAndFlush(fixture);
        Files.writeString(archive(), "broken");
        prepareAndFlush(fixture);
        assertTrue(unzip(Files.readAllBytes(archive())).containsKey("data/config.json"));
    }

    @Test void resourceFlushNeverWaitsForBlockedBackgroundPreparationAndRequestsCoalesce() throws Exception {
        Path data = data();
        write(data, "worlds/default/towns.json", "{\"towns\":[]}");
        var fixture = resourceFixture(data);
        java.util.concurrent.CompletableFuture<AetherhavenBackupArchive.Prepared> preparing;
        synchronized (TownWorldFile.class) {
            preparing = fixture.worker.request();
            for (int i = 0; i < 100; i++) assertSame(preparing, fixture.worker.request());
            // The worker cannot copy towns.json while this monitor is held. The vanilla flush
            // MUST still return: joining the worker here would deadlock the game/save thread.
            fixture.resources.flushAll().get(2, TimeUnit.SECONDS);
            assertFalse(preparing.isDone());
            assertFalse(Files.exists(archive()));
        }
        preparing.get(10, TimeUnit.SECONDS);
        fixture.resources.flushAll().get(10, TimeUnit.SECONDS);
        assertTrue(Files.isRegularFile(archive()));
    }

    @Test void incrementalSnapshotRemovesDeletedEntriesAndAddsNewFiles() throws Exception {
        Path data = data();
        write(data, "worlds/removed/towns.json", "{\"towns\":[]}");
        write(data, "config.json", "{}");
        var fixture = resourceFixture(data);
        prepareAndFlush(fixture);
        Files.delete(data.resolve("worlds/removed/towns.json"));
        write(data, "worlds/new/towns.json", "{\"towns\":[]}");
        var delta = fixture.worker.request().get(10, TimeUnit.SECONDS);
        assertEquals(1, delta.changedFiles());
        fixture.resources.flushAll().get(10, TimeUnit.SECONDS);
        var entries = unzip(Files.readAllBytes(archive()));
        assertFalse(entries.containsKey("data/worlds/removed/towns.json"));
        assertTrue(entries.containsKey("data/worlds/new/towns.json"));
        assertTrue(entries.containsKey("data/config.json"));
    }

    @Test void largeContentBenchmarkOnlyProcessesChangedSourceFiles() throws Exception {
        Path data = data();
        var random = new java.util.Random(731);
        byte[] block = new byte[1024 * 1024];
        for (int i = 0; i < 96; i++) {
            random.nextBytes(block);
            Files.write(data.resolve("content-" + i + ".bin"), block);
        }
        write(data, "worlds/default/towns.json", "{\"towns\":[],\"revision\":1}");
        var fixture = resourceFixture(data);
        long started = System.nanoTime();
        var first = fixture.worker.request().get(90, TimeUnit.SECONDS);
        double coldMs = (System.nanoTime() - started) / 1_000_000.0;
        assertEquals(97, first.changedFiles());
        started = System.nanoTime();
        fixture.resources.flushAll().get(10, TimeUnit.SECONDS);
        double publishMs = (System.nanoTime() - started) / 1_000_000.0;
        started = System.nanoTime();
        assertNull(fixture.worker.request().get(10, TimeUnit.SECONDS));
        double unchangedMs = (System.nanoTime() - started) / 1_000_000.0;
        write(data, "worlds/default/towns.json", "{\"towns\":[],\"revision\":22}");
        started = System.nanoTime();
        var delta = fixture.worker.request().get(60, TimeUnit.SECONDS);
        double changedMs = (System.nanoTime() - started) / 1_000_000.0;
        assertEquals(1, delta.changedFiles());
        assertEquals(Files.size(data.resolve("worlds/default/towns.json")), delta.sourceBytes());
        fixture.resources.flushAll().get(10, TimeUnit.SECONDS);
        try (var zip = new java.util.zip.ZipFile(archive().toFile())) {
            assertEquals(1024 * 1024, zip.getEntry("data/content-95.bin").getSize());
            try (var input = zip.getInputStream(zip.getEntry("data/content-95.bin"))) {
                assertArrayEquals(block, input.readAllBytes());
            }
        }
        String report = String.format(java.util.Locale.ROOT,
            "96 MiB incompressible content, 97 files%nBackground initial: %.2f ms%nBackground unchanged scan: %.2f ms%nBackground one-file update: %.2f ms%nPublish + vanilla marker disk write: %.2f ms%nSource bytes read on update: %d%n",
            coldMs, unchangedMs, changedMs, publishMs, delta.sourceBytes());
        Files.writeString(Path.of("build/backup-performance.txt"), report);
        System.out.println(report);
    }

    @Test void actualVanillaZipContainsRecoverableTownAndPropData() throws Exception {
        Path data = data();
        var town = new TownRecord(UUID.randomUUID(), UUID.randomUUID(), "default", 10, 64, 20, 1, 4, 12345L);
        town.setDisplayName("Recovered Haven");
        town.setTreasuryGoldCoinCount(123);
        town.unlockBlockPalette("stone");
        TownWorldFile.writeBytesAtomic(data.resolve("worlds/default/towns.json"), TownWorldFile.toJsonBytes(List.of(town)));
        write(data, "worlds/default/props.json", "{\"props\":[],\"removedInstanceIds\":[\"test-id\"]}");
        var fixture = resourceFixture(data);
        prepareAndFlush(fixture);
        Path worldZip = temp.resolve("vanilla-backup.zip");
        var zipMethod = Class.forName("com.hypixel.hytale.server.core.util.backup.BackupUtil")
            .getDeclaredMethod("walkFileTreeAndZip", Path.class, Path.class);
        zipMethod.setAccessible(true);
        zipMethod.invoke(null, temp.resolve("universe"), worldZip);
        var vanilla = unzip(Files.readAllBytes(worldZip));
        var modFiles = unzip(vanilla.get(AetherhavenBackupResource.ARCHIVE_PATH));
        Path restoredTownFile = temp.resolve("recovered/towns.json");
        Files.createDirectories(restoredTownFile.getParent());
        Files.write(restoredTownFile, modFiles.get("data/worlds/default/towns.json"));
        var restored = TownWorldFile.readOrEmpty(restoredTownFile).getTowns().getFirst();
        assertEquals(town.getTownId(), restored.getTownId());
        assertEquals("Recovered Haven", restored.getDisplayName());
        assertEquals(123, restored.getTreasuryGoldCoinCount());
        assertTrue(restored.hasBlockPaletteUnlocked("stone"));
        assertArrayEquals(Files.readAllBytes(data.resolve("worlds/default/props.json")), modFiles.get("data/worlds/default/props.json"));
    }
}
