package com.hexvane.aetherhaven.prefab;

import com.hypixel.hytale.server.core.prefab.PrefabStore;
import java.nio.file.Path;
import javax.annotation.Nullable;

/** Resolves mod / asset prefab paths (shared by placement, construction UI, validators). */
public final class PrefabResolveUtil {
    private PrefabResolveUtil() {}

    @Nullable
    public static Path resolvePrefabPath(@Nullable String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String k = key.trim();
        PrefabStore ps = PrefabStore.get();
        Path p = ps.findAssetPrefabPath(k);
        if (p != null) {
            return p;
        }
        if (!k.endsWith(".prefab.json")) {
            p = ps.findAssetPrefabPath(k + ".prefab.json");
            if (p != null) {
                return p;
            }
        }
        String dotted = k.replace('.', '/');
        if (!dotted.equals(k)) {
            p = ps.findAssetPrefabPath(dotted);
            if (p != null) {
                return p;
            }
            if (!dotted.endsWith(".prefab.json")) {
                p = ps.findAssetPrefabPath(dotted + ".prefab.json");
                if (p != null) {
                    return p;
                }
            }
        }
        return null;
    }
}
