package com.hexvane.aetherhaven.prefab;

import com.hexvane.aetherhaven.community.CommunityPrefabSafety;
import com.hypixel.hytale.math.Axis;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.prefab.selection.standard.RotateBlockMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Maps resolved prefab files (including community / data-dir paths) to keys understood by
 * {@link com.hypixel.hytale.server.core.modules.entity.component.PersistentPrefabPreview}.
 */
public final class BrowsablePrefabKeyBridge {
    private static final String RUNTIME_PREFIX = "Aetherhaven/Runtime/";

    private static final Set<String> STAGED_RUNTIME_KEYS = ConcurrentHashMap.newKeySet();

    private BrowsablePrefabKeyBridge() {}

  /**
   * Resolves a browsable prefab key, staging a server copy when needed. Rotation is baked into the staged file when
   * {@code rotationSteps != 0}.
   */
    @Nullable
    public static String resolveBrowsableKey(@Nonnull String prefabPathKey, int rotationSteps) {
        if (prefabPathKey.isBlank()) {
            return null;
        }
        Path resolved = PrefabResolveUtil.resolvePrefabPath(prefabPathKey);
        if (resolved == null || !Files.isRegularFile(resolved)) {
            return null;
        }
        try {
            if (!CommunityPrefabSafety.validate(resolved).isSafe()) {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
        int steps = (rotationSteps % 4 + 4) % 4;
        String direct = directBrowsableKey(resolved, prefabPathKey.trim());
        if (direct != null && steps == 0) {
            return direct;
        }
        return stageRuntimeCopy(resolved, prefabPathKey.trim(), steps);
    }

    /** Deletes staged runtime prefabs created by this bridge. */
    public static void releaseAllStaged() {
        PrefabStore store = PrefabStore.get();
        for (String key : STAGED_RUNTIME_KEYS) {
            try {
                Path path = store.getServerPrefabsPath().resolve(key + ".prefab.json");
                if (Files.isRegularFile(path)) {
                    store.deletePrefab(path);
                }
            } catch (Exception ignored) {
                // Best effort cleanup.
            }
        }
        STAGED_RUNTIME_KEYS.clear();
    }

    @Nullable
    private static String directBrowsableKey(@Nonnull Path resolved, @Nonnull String logicalKey) {
        PrefabStore store = PrefabStore.get();
        String normalizedLogical = logicalKey.replace('\\', '/');
        if (store.findBrowsablePrefabPath(normalizedLogical) != null) {
            return PrefabStore.stripPrefabSuffix(normalizedLogical);
        }
        try {
            String relative = store.getRelativePrefabPath(resolved.toAbsolutePath().normalize()).toString().replace('\\', '/');
            if (store.findBrowsablePrefabPath(relative) != null) {
                return PrefabStore.stripPrefabSuffix(relative);
            }
        } catch (Exception ignored) {
            // Not under a browsable root.
        }
        return null;
    }

    @Nullable
    private static String stageRuntimeCopy(@Nonnull Path source, @Nonnull String logicalKey, int rotationSteps) {
        PrefabStore store = PrefabStore.get();
        BlockSelection selection;
        try {
            selection = store.getPrefab(source);
        } catch (Exception e) {
            return null;
        }
        if (selection == null) {
            return null;
        }
        if (rotationSteps != 0) {
            selection = selection.cloneSelection().rotate(Axis.Y, 90 * rotationSteps, RotateBlockMode.ALL);
        }
        String runtimeKey = RUNTIME_PREFIX + sanitizeKey(logicalKey) + "_r" + rotationSteps;
        try {
            store.saveServerPrefab(runtimeKey, selection, true);
            STAGED_RUNTIME_KEYS.add(runtimeKey);
            return runtimeKey;
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Nonnull
    private static String sanitizeKey(@Nonnull String logicalKey) {
        String base = PrefabStore.stripPrefabSuffix(logicalKey.replace('\\', '/'));
        String sanitized = base.replaceAll("[^A-Za-z0-9_./-]", "_");
        if (sanitized.length() > 120) {
            sanitized = sanitized.substring(sanitized.length() - 120);
        }
        return sanitized.isEmpty() ? "prefab" : sanitized;
    }
}
