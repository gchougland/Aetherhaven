package com.hexvane.aetherhaven.prop;

import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hexvane.aetherhaven.prefab.PrefabSidecarCache;
import com.hypixel.hytale.logger.HytaleLogger;
import java.nio.file.Path;
import javax.annotation.Nonnull;

/** Drops stale prop prefab LPF sidecars so packaging overlays use current JSON bounds. */
public final class PropPrefabCache {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private PropPrefabCache() {}

    public static void invalidateCatalog(@Nonnull PropCatalog catalog) {
        int n = 0;
        for (PropDefinition def : catalog.list()) {
            Path path = PrefabResolveUtil.resolvePrefabPath(def.getPrefabPath());
            if (path == null) {
                continue;
            }
            PrefabSidecarCache.invalidate(path);
            n++;
        }
        LOGGER.atInfo().log("Invalidated prefab LPF cache for %s prop(s)", n);
    }
}
