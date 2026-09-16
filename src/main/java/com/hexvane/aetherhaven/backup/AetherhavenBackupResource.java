package com.hexvane.aetherhaven.backup;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.resources.UniverseResourceType;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.function.Supplier;
import org.bson.BsonDocument;

/** Publishes a completed background snapshot under vanilla's backup lock. Never waits for preparation. */
public final class AetherhavenBackupResource {
    public static final String ID = "AetherhavenBackup";
    public static final String ARCHIVE_PATH = "aetherhaven-backup/Aetherhaven-data.zip";
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static Supplier<Path> sourceDirectory;
    private static UniverseResourceType<AetherhavenBackupResource> type;
    private static AetherhavenBackupWorker worker;
    private static boolean stopping;
    public static final BuilderCodec<AetherhavenBackupResource> CODEC = codec(AetherhavenBackupResource::getWorker);
    private String snapshotSha256 = "";
    private boolean warnedNotReady;

    static BuilderCodec<AetherhavenBackupResource> codec(Supplier<AetherhavenBackupWorker> workers) {
        var builder = BuilderCodec.builder(AetherhavenBackupResource.class, AetherhavenBackupResource::new)
            .append(new KeyedCodec<>("SnapshotSha256", Codec.STRING), (resource, value) -> resource.snapshotSha256 = value,
                resource -> resource.snapshotSha256).add();
        return new BuilderCodec<>(builder) {
            @Override public BsonDocument encode(AetherhavenBackupResource resource, ExtraInfo extraInfo) {
                var worker = workers.get();
                if (worker != null) resource.publish(worker);
                return super.encode(resource, extraInfo);
            }
        };
    }

    public static synchronized void register(Supplier<Path> dataDirectory) {
        if (type == null) type = Universe.registerResource(AetherhavenBackupResource.class, ID, CODEC);
        sourceDirectory = dataDirectory;
        stopping = false;
        LOGGER.atInfo().log("Aetherhaven background snapshots will be included in vanilla backups as %s", ARCHIVE_PATH);
    }

    public static void startPreparing() {
        Universe.get().getUniverseReady().thenRun(() -> {
            var current = getWorker();
            if (current != null) current.request();
        });
    }

    private static synchronized AetherhavenBackupWorker getWorker() {
        if (stopping || sourceDirectory == null) return null;
        if (worker == null) {
            Path universe = Universe.get().getPath().toAbsolutePath().normalize();
            worker = new AetherhavenBackupWorker(sourceDirectory.get(), universe.resolve(ARCHIVE_PATH),
                universe.getParent(), () -> Universe.get().flushResource(type).exceptionally(error -> {
                    LOGGER.atSevere().withCause(error).log("Could not publish prepared Aetherhaven backup");
                    return null;
                }));
        }
        return worker;
    }

    private void publish(AetherhavenBackupWorker worker) {
        try {
            String hash = worker.publishReady();
            if (hash != null) snapshotSha256 = hash;
            else worker.requestIfDue(); // Coalesces repeated flushes; never waits for disk/compression work.
            if (snapshotSha256.isEmpty() && !warnedNotReady) {
                warnedNotReady = true;
                LOGGER.atWarning().log("The first Aetherhaven backup snapshot is still preparing; backups made before it finishes may not contain town data");
            }
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Could not publish Aetherhaven snapshot; previous archive retained");
            throw new UncheckedIOException(e);
        }
    }

    public static void shutdown() {
        AetherhavenBackupWorker old;
        synchronized (AetherhavenBackupResource.class) {
            stopping = true;
            old = worker;
            worker = null;
        }
        if (old != null) old.close();
    }
}
