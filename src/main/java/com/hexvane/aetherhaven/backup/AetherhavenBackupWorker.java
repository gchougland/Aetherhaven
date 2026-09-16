package com.hexvane.aetherhaven.backup;

import com.hexvane.aetherhaven.town.TownSaveCoordinator;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;

/** One coalescing, low-priority worker. It never modifies the published archive. */
final class AetherhavenBackupWorker implements AutoCloseable {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private final Path source;
    private final Path target;
    private final Path scratchParent;
    private final Runnable onReady;
    private final AetherhavenBackupArchive builder = new AetherhavenBackupArchive();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Aetherhaven-Backup");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });
    private Path scratch;
    private AetherhavenBackupArchive.Prepared ready;
    private CompletableFuture<AetherhavenBackupArchive.Prepared> inFlight;
    private boolean closed;
    private long lastRequestNanos;

    synchronized void requestIfDue() {
        if (lastRequestNanos == 0 || System.nanoTime() - lastRequestNanos >= TimeUnit.SECONDS.toNanos(5)) request();
    }

    AetherhavenBackupWorker(Path source, Path target, Path scratchParent, Runnable onReady) {
        this.source = source;
        this.target = target;
        this.scratchParent = scratchParent;
        this.onReady = onReady;
    }

    synchronized CompletableFuture<AetherhavenBackupArchive.Prepared> request() {
        if (closed) return CompletableFuture.completedFuture(null);
        if (inFlight != null && !inFlight.isDone()) return inFlight;
        if (ready != null) return CompletableFuture.completedFuture(ready);
        var completion = new CompletableFuture<AetherhavenBackupArchive.Prepared>();
        lastRequestNanos = System.nanoTime();
        inFlight = completion;
        executor.execute(() -> {
            try {
                TownSaveCoordinator.awaitPendingWrites();
                if (scratch == null) {
                    Files.createDirectories(scratchParent);
                    scratch = Files.createTempDirectory(scratchParent, ".aetherhaven-backup-");
                }
                var prepared = builder.prepare(source, target, scratch);
                synchronized (this) { ready = prepared; }
                completion.complete(prepared);
                if (prepared != null) onReady.run();
            } catch (Exception e) {
                completion.completeExceptionally(e);
                synchronized (this) {
                    if (!closed) LOGGER.atSevere().withCause(e).log("Aetherhaven background backup failed; previous snapshot retained");
                }
            }
        });
        return completion;
    }

    /** Caller owns vanilla's resource save lock. No scanning, waiting, hashing or compression here. */
    synchronized String publishReady() throws IOException {
        if (ready == null) return null;
        builder.publish(ready, target);
        String hash = ready.hash();
        ready = null;
        return hash;
    }

    @Override public void close() {
        synchronized (this) { closed = true; }
        executor.shutdownNow();
        try {
            if (executor.awaitTermination(30, TimeUnit.SECONDS)) {
                if (scratch != null) AetherhavenBackupArchive.cleanScratch(scratch);
            } else {
                LOGGER.atWarning().log("Aetherhaven backup worker is still stopping; leaving its private scratch directory intact");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Could not clean Aetherhaven backup scratch files");
        }
    }
}
