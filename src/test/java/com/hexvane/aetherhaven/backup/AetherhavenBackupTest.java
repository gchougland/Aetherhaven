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
        write(data, "Server/Prefabs/木.prefab.json", "{\"name\":\"café\"}");
        write(data, "config.json", "{\"setting\":true}");
        byte[] binary = new byte[] {0, -1, 2, 3, 100};
        Path icon = data.resolve("Common/Icons/ItemsGenerated/Aetherhaven_Token_custom.png");
        Files.createDirectories(icon.getParent());
        Files.write(icon, binary);
        write(data, "worlds/default/towns.json.tmp", "half written");
        write(data, "LOCK", "locked");
        String hash = AetherhavenBackupArchive.writeSnapshot(data, archive());
        byte[] bytes = Files.readAllBytes(archive());
        assertEquals(hash, AetherhavenBackupArchive.sha256(bytes));
        var entries = unzip(bytes);
        assertEquals(7, entries.size());
        assertArrayEquals(binary, entries.get("data/Common/Icons/ItemsGenerated/Aetherhaven_Token_custom.png"));
        assertTrue(entries.containsKey("data/Server/Prefabs/木.prefab.json"));
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

    @Test void unexpectedFilesAreExcludedWithoutBlockingOrRetriggeringBackup() throws Exception {
        Path data = data();
        write(data, "worlds/default/towns.json", "{\"towns\":[]}");
        for (String name : List.of("test.json", "valid-but-unrelated.json", "TEST.JSON",
            "worlds/default/test.json", "unrelated/config.json", "Server/Prefabs/test.json",
            "notes.txt", "random.bin", "icon.png", "test.json.bak", "worlds/default/notes.txt",
            "Server/Aetherhaven/Buildings/notes.txt", "Community/unknown.bin", "config.json.tmp", "LOCK")) {
            write(data, name, name.startsWith("valid") ? "{}" : "not valid JSON");
        }
        var fixture = resourceFixture(data);
        prepareAndFlush(fixture);
        var entries = unzip(Files.readAllBytes(archive()));
        assertEquals(java.util.Set.of(AetherhavenBackupArchive.MANIFEST, "data/worlds/default/towns.json"), entries.keySet());
        var manifest = JsonParser.parseString(new String(entries.get(AetherhavenBackupArchive.MANIFEST), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(java.util.Set.of("data/worlds/default/towns.json"), manifest.getAsJsonObject("files").keySet());
        write(data, "test.json", "changed but still invalid JSON");
        assertNull(fixture.worker.request().get(10, TimeUnit.SECONDS));
        write(data, "worlds/default/towns.json", "{\"towns\":[],\"revision\":2}");
        prepareAndFlush(fixture);
        assertEquals("{\"towns\":[],\"revision\":2}", new String(unzip(Files.readAllBytes(archive()))
            .get("data/worlds/default/towns.json"), StandardCharsets.UTF_8));
    }

    @Test void expectedPersistenceAndAuthoredJsonRemainIncluded() throws Exception {
        Path data = data();
        List<String> expected = List.of(
            "config.json", "server_difficulty.json", "shop_prices.json", "geode_loot.json",
            "floating_gift_loot.json", "prop_loot_exclusions.json", "community_install_instance.json",
            "worlds/default/towns.json", "worlds/default/difficulty.json", "worlds/default/props.json",
            "worlds/default/pois.json", "worlds/default/patrol_routes.json", "worlds/default/path_commits.json",
            "worlds/default/townsfolk_pool.json", "worlds/default/shop_spots.json", "worlds/default/tourist_portals.json",
            "worlds/default/world_npcs.json", "worlds/default/world_npc_routes.json", "worlds/default/world_npc_players.json",
            "worlds/default/tree_climb_leaderboard.json", "worlds/default/snowball_leaderboard.json",
            "worlds/default/market_leaderboard.json", "worlds/default/hallows_eve_leaderboard.json",
            "Server/Aetherhaven/Buildings/custom.json", "Server/Aetherhaven/Props/custom.json",
            "Server/Aetherhaven/Festivals/custom.json", "Server/Prefabs/Props/custom.prefab.json",
            "Community/Server/Aetherhaven/Buildings/custom.json", "Community/Server/Prefabs/custom.prefab.json",
            "Community/.install-meta/custom.json", "Community/.preview/custom.prefab.json",
            "Community/.moderation-preview/custom.prefab.json", "shop_loot/custom.json", "avatar_exports/custom.json",
            "npc_telemetry/default/report.json"
        );
        for (String name : expected) write(data, name, "{}");
        var entries = unzip(AetherhavenBackupArchive.capture(data));
        assertEquals(expected.size() + 1, entries.size());
        for (String name : expected) assertTrue(entries.containsKey("data/" + name), name);
    }

    @Test void knownNonJsonContentAndRecoveryCopiesRemainIncluded() throws Exception {
        Path data = data();
        List<String> expected = List.of("worlds/default/towns.json.bak", "shop_prices.json.bak",
            "Server/Prefabs/custom.prefab.json.bak", "Common/Icons/ItemsGenerated/custom.png",
            "Community/Common/Icons/ItemsGenerated/custom.png", "Server/Aetherhaven/GuideTopics/en-US/custom.md",
            "villager_audit/default/audit.jsonl");
        for (String name : expected) write(data, name, "raw content");
        var entries = unzip(AetherhavenBackupArchive.capture(data));
        assertEquals(expected.size() + 1, entries.size());
        for (String name : expected) assertEquals("raw content", new String(entries.get("data/" + name), StandardCharsets.UTF_8));
    }

    @Test void corruptAuthoredContentStillPreservesPreviousSnapshot() throws Exception {
        Path data = data();
        write(data, "Server/Aetherhaven/Buildings/custom.json", "{}");
        AetherhavenBackupArchive.writeSnapshot(data, archive());
        byte[] before = Files.readAllBytes(archive());
        write(data, "Server/Aetherhaven/Buildings/custom.json", "{");
        assertThrows(IOException.class, () -> AetherhavenBackupArchive.writeSnapshot(data, archive()));
        assertArrayEquals(before, Files.readAllBytes(archive()));
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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void shutdownDuringIncrementalBackupDoesNotInterruptZipOrPublishLate(boolean interruptCaller) throws Exception {
        Path data = data();
        write(data, "config.json", "{}");
        write(data, "worlds/default/towns.json", "{\"towns\":[]}");
        Path scratchParent = Files.createDirectory(temp.resolve("scratch"));
        var notifications = new java.util.concurrent.atomic.AtomicInteger();
        var worker = new AetherhavenBackupWorker(data, archive(), scratchParent, notifications::incrementAndGet);
        workers.add(worker);
        worker.request().get(10, TimeUnit.SECONDS);
        worker.publishReady();
        awaitCondition(() -> notifications.get() == 1);
        byte[] published = Files.readAllBytes(archive());
        write(data, "worlds/default/towns.json", "{\"towns\":[],\"revision\":2}");

        var stopped = new java.util.concurrent.CompletableFuture<Boolean>();
        java.util.concurrent.CompletableFuture<AetherhavenBackupArchive.Prepared> preparing;
        synchronized (TownWorldFile.class) {
            preparing = worker.request();
            // Hold preparation inside an open incremental ZIP, before copying the changed town.
            // Existing config.json must be copied by zipfs when it commits on close.
            var backupThread = new java.util.concurrent.atomic.AtomicReference<Thread>();
            awaitCondition(() -> {
                for (var entry : Thread.getAllStackTraces().entrySet()) {
                    if (entry.getKey().getState() == Thread.State.BLOCKED
                        && java.util.Arrays.stream(entry.getValue()).anyMatch(frame ->
                            frame.getClassName().equals(AetherhavenBackupArchive.class.getName())
                                && frame.getMethodName().equals("copyStable"))) {
                        backupThread.set(entry.getKey());
                        return true;
                    }
                }
                return false;
            });
            Thread closer = new Thread(() -> {
                try {
                    if (interruptCaller) Thread.currentThread().interrupt();
                    worker.close();
                    stopped.complete(Thread.currentThread().isInterrupted());
                } catch (Throwable e) { stopped.completeExceptionally(e); }
            }, "Backup-shutdown-test");
            closer.setDaemon(true);
            closer.start();
            awaitCondition(() -> worker.request().isDone());
            assertNull(worker.request().get(1, TimeUnit.SECONDS));
            if (interruptCaller) assertTrue(stopped.get(10, TimeUnit.SECONDS));
            assertFalse(backupThread.get().isInterrupted(), "Shutdown must not interrupt the open ZIP");
            assertFalse(preparing.isDone());
        }
        assertNull(preparing.get(10, TimeUnit.SECONDS));
        assertEquals(interruptCaller, stopped.get(10, TimeUnit.SECONDS));
        awaitCondition(() -> {
            try (var files = Files.list(scratchParent)) { return files.findAny().isEmpty(); }
            catch (IOException e) { throw new java.io.UncheckedIOException(e); }
        });
        assertEquals(1, notifications.get(), "Shutdown must discard the newly prepared snapshot");
        assertNull(worker.publishReady());
        assertArrayEquals(published, Files.readAllBytes(archive()));
    }

    @Test void interruptedShutdownStillCleansAnUnpublishedReadySnapshot() throws Exception {
        Path data = data();
        write(data, "config.json", "{}");
        Path scratchParent = Files.createDirectory(temp.resolve("scratch"));
        var worker = new AetherhavenBackupWorker(data, archive(), scratchParent, () -> {});
        workers.add(worker);
        assertNotNull(worker.request().get(10, TimeUnit.SECONDS));
        try {
            Thread.currentThread().interrupt();
            worker.close();
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
        awaitCondition(() -> {
            try (var files = Files.list(scratchParent)) { return files.findAny().isEmpty(); }
            catch (IOException e) { throw new java.io.UncheckedIOException(e); }
        });
        assertNull(worker.publishReady());
        assertNull(worker.request().get(1, TimeUnit.SECONDS));
        assertFalse(Files.exists(archive()));
        worker.close(); // Repeated lifecycle cleanup is harmless.
    }

    private static void awaitCondition(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean()) {
            assertTrue(System.nanoTime() < deadline, "Timed out waiting for backup worker");
            Thread.sleep(10);
        }
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
        Path icons = Files.createDirectories(data.resolve("Common/Icons/ItemsGenerated"));
        for (int i = 0; i < 96; i++) {
            random.nextBytes(block);
            Files.write(icons.resolve("content-" + i + ".png"), block);
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
            assertEquals(1024 * 1024, zip.getEntry("data/Common/Icons/ItemsGenerated/content-95.png").getSize());
            try (var input = zip.getInputStream(zip.getEntry("data/Common/Icons/ItemsGenerated/content-95.png"))) {
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
