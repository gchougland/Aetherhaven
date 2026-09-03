package com.hexvane.aetherhaven.difficulty;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.TownManager;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Loads and saves {@code server_difficulty.json} under the plugin data directory. */
public final class ServerDifficultyPersistence {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final AtomicReference<ServerDifficultyState> CACHED = new AtomicReference<>();

    private ServerDifficultyPersistence() {}

    @Nonnull
    public static Path filePath(@Nonnull AetherhavenPlugin plugin) {
        return TownManager.pluginData(plugin).resolve("server_difficulty.json");
    }

    @Nonnull
    public static ServerDifficultyState getOrLoad() {
        ServerDifficultyState cached = CACHED.get();
        if (cached != null) {
            return cached;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return new ServerDifficultyState();
        }
        return load(plugin);
    }

    @Nonnull
    public static synchronized ServerDifficultyState load(@Nonnull AetherhavenPlugin plugin) {
        Path path = filePath(plugin);
        boolean missing = !Files.isRegularFile(path);
        ServerDifficultyState state = readOrDefault(path);
        CACHED.set(state);
        if (missing) {
            try {
                writeAtomic(path, state);
                LOGGER.atInfo().log("Wrote default server difficulty to %s", path);
            } catch (IOException e) {
                LOGGER.atWarning().withCause(e).log("Failed to write default server difficulty to %s", path);
            }
        }
        return state;
    }

    public static synchronized void save(@Nonnull AetherhavenPlugin plugin, @Nonnull ServerDifficultyState state) {
        CACHED.set(state);
        Path path = filePath(plugin);
        try {
            writeAtomic(path, state);
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to save server difficulty to %s", path);
        }
    }

    public static void clearCache() {
        CACHED.set(null);
    }

    @Nonnull
    private static ServerDifficultyState readOrDefault(@Nonnull Path path) {
        if (!Files.isRegularFile(path)) {
            return new ServerDifficultyState();
        }
        try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            ServerDifficultyState state = GSON.fromJson(r, ServerDifficultyState.class);
            return state != null ? state : new ServerDifficultyState();
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to read server difficulty from %s", path);
            return new ServerDifficultyState();
        }
    }

    private static void writeAtomic(@Nonnull Path path, @Nonnull ServerDifficultyState state) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
        try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            GSON.toJson(state, w);
        }
        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Nullable
    static ServerDifficultyState peekCache() {
        return CACHED.get();
    }
}
