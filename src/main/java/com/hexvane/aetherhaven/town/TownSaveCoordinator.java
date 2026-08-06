package com.hexvane.aetherhaven.town;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;

/**
 * Debounced, async {@code towns.json} persistence. World-thread callers update {@link TownManager} in memory and
 * request a save; Gson serialization runs on the world thread once per debounce window and file IO runs on a
 * background thread.
 */
public final class TownSaveCoordinator {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    static final long DEBOUNCE_MS = 3_000L;

    private static final ConcurrentHashMap<TownManager, PendingSave> PENDING = new ConcurrentHashMap<>();
    private static volatile ExecutorService ioExecutor;

    private TownSaveCoordinator() {}

    public static void requestSave(@Nonnull TownManager manager) {
        if (!manager.shouldPersist()) {
            return;
        }
        PendingSave pending = PENDING.computeIfAbsent(manager, ignored -> new PendingSave());
        pending.requestedAtMs = System.currentTimeMillis();
        pending.immediate = false;
    }

    public static void requestImmediateSave(@Nonnull TownManager manager) {
        if (!manager.shouldPersist()) {
            return;
        }
        PendingSave pending = PENDING.computeIfAbsent(manager, ignored -> new PendingSave());
        pending.requestedAtMs = System.currentTimeMillis();
        pending.immediate = true;
    }

    /** Called once per world tick from {@link com.hexvane.aetherhaven.time.AetherhavenGameTimeCoordinatorSystem}. */
    public static void tickWorld(@Nonnull World world) {
        TownManager manager = AetherhavenWorldRegistries.getTownManagerIfLoaded(world);
        if (manager == null) {
            return;
        }
        PendingSave pending = PENDING.get(manager);
        if (pending == null) {
            return;
        }
        long nowMs = System.currentTimeMillis();
        if (!pending.immediate && nowMs - pending.requestedAtMs < DEBOUNCE_MS) {
            return;
        }
        pending.immediate = false;
        submitAsyncSave(manager);
    }

    /** Writes the current in-memory town index to disk on the calling thread. */
    public static void flushSync(@Nonnull TownManager manager) {
        if (!manager.shouldPersist()) {
            PENDING.remove(manager);
            return;
        }
        try {
            writeSnapshot(manager);
            PENDING.remove(manager);
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to save towns for world %s", manager.getWorldName());
        }
    }

    public static void flushAllSync() {
        for (TownManager manager : AetherhavenWorldRegistries.allLoadedTownManagers()) {
            flushSync(manager);
        }
        awaitIoIdle();
    }

    public static void shutdown() {
        ExecutorService executor = ioExecutor;
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        ioExecutor = null;
    }

    private static void submitAsyncSave(@Nonnull TownManager manager) {
        byte[] payload;
        Path saveFile;
        try {
            payload = serializeSnapshot(manager);
            saveFile = manager.getSaveFilePath();
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to serialize towns for world %s", manager.getWorldName());
            return;
        }
        PENDING.remove(manager);
        ioExecutor().execute(
            () -> {
                try {
                    TownWorldFile.writeBytesAtomic(saveFile, payload);
                    manager.notifySavedToDisk(System.currentTimeMillis());
                } catch (IOException e) {
                    LOGGER.atSevere().withCause(e).log("Failed to save towns for world %s", manager.getWorldName());
                    requestSave(manager);
                }
            }
        );
    }

    private static void writeSnapshot(@Nonnull TownManager manager) throws IOException {
        byte[] payload = serializeSnapshot(manager);
        TownWorldFile.writeBytesAtomic(manager.getSaveFilePath(), payload);
        manager.notifySavedToDisk(System.currentTimeMillis());
    }

    @Nonnull
    private static byte[] serializeSnapshot(@Nonnull TownManager manager) throws IOException {
        List<TownRecord> snapshot = manager.snapshotTownsForSave();
        return TownWorldFile.toJsonBytes(snapshot);
    }

    private static void awaitIoIdle() {
        ExecutorService executor = ioExecutor;
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        ioExecutor = null;
    }

    @Nonnull
    private static ExecutorService ioExecutor() {
        ExecutorService executor = ioExecutor;
        if (executor != null) {
            return executor;
        }
        synchronized (TownSaveCoordinator.class) {
            executor = ioExecutor;
            if (executor == null) {
                ThreadFactory factory = r -> {
                    Thread thread = new Thread(r, "Aetherhaven-TownSave");
                    thread.setDaemon(true);
                    return thread;
                };
                executor = Executors.newSingleThreadExecutor(factory);
                ioExecutor = executor;
            }
            return executor;
        }
    }

    private static final class PendingSave {
        volatile long requestedAtMs;
        volatile boolean immediate;
    }
}
