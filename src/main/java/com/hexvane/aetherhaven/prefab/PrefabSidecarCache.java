package com.hexvane.aetherhaven.prefab;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Drops Hytale's {@code .prefab.json.lpf} sidecar next to a JSON prefab and clears the in-memory buffer cache.
 * When JSON and LPF share the same mtime (common after {@code processResources}), the engine prefers the LPF —
 * so edited JSON never applies until the sidecar is removed.
 */
public final class PrefabSidecarCache {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private PrefabSidecarCache() {}

    /** Invalidates memory + deletes {@code name.prefab.json.lpf} beside {@code jsonOrAnyPrefabPath}. */
    public static void invalidate(@Nullable Path jsonOrAnyPrefabPath) {
        if (jsonOrAnyPrefabPath == null) {
            return;
        }
        Path path = jsonOrAnyPrefabPath.toAbsolutePath().normalize();
        PrefabBufferUtil.removeCached(path);
        Path lpf = sidecarLpfPath(path);
        if (lpf == null) {
            return;
        }
        try {
            if (Files.deleteIfExists(lpf)) {
                LOGGER.atFine().log("Removed stale prefab LPF cache %s", lpf);
            }
        } catch (IOException e) {
            LOGGER.atWarning().log("Could not delete prefab LPF cache %s: %s", lpf, e.getMessage());
        }
        PrefabBufferUtil.removeCached(lpf);
    }

    @Nullable
    private static Path sidecarLpfPath(@Nonnull Path path) {
        String name = path.getFileName().toString();
        if (name.endsWith(PrefabBufferUtil.JSON_LPF_FILE_SUFFIX)) {
            return path;
        }
        if (name.endsWith(PrefabBufferUtil.JSON_FILE_SUFFIX)) {
            return path.resolveSibling(name + ".lpf");
        }
        if (name.endsWith(PrefabBufferUtil.LPF_FILE_SUFFIX)) {
            return path;
        }
        return path.resolveSibling(name + PrefabBufferUtil.JSON_LPF_FILE_SUFFIX);
    }
}
