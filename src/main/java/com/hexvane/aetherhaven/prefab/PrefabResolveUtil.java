package com.hexvane.aetherhaven.prefab;

import com.hypixel.hytale.server.core.prefab.PrefabStore;
import java.nio.file.Path;
import javax.annotation.Nonnull;
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
        p = tryUnderscoreCasePrefabAliases(ps, k);
        return p;
    }

    /**
     * PrefabStore keys are case-sensitive. Underscore prefabs are easy to get out of sync with the
     * asset pack (e.g. {@code market_stall} vs {@code Market_Stall}), which breaks on macOS / strict
     * lookups. Try common normalizations of the filename segment after exact match fails.
     */
    @Nullable
    private static Path tryUnderscoreCasePrefabAliases(@Nonnull PrefabStore ps, @Nonnull String k) {
        String baseName = lastPathSegment(k);
        if (!baseName.endsWith(".prefab.json")) {
            return null;
        }
        String pascal = pascalSnakePrefabFileName(baseName);
        String lower = lowerSnakePrefabFileName(baseName);
        for (String altBase : new String[] {pascal, lower}) {
            if (altBase == null || altBase.equals(baseName)) {
                continue;
            }
            String altKey = replaceLastSegment(k, altBase);
            Path found = ps.findAssetPrefabPath(altKey);
            if (found != null) {
                return found;
            }
            if (!altKey.endsWith(".prefab.json")) {
                found = ps.findAssetPrefabPath(altKey + ".prefab.json");
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Nonnull
    private static String lastPathSegment(@Nonnull String path) {
        int a = path.lastIndexOf('/');
        int b = path.lastIndexOf('\\');
        int i = Math.max(a, b);
        return i >= 0 ? path.substring(i + 1) : path;
    }

    @Nonnull
    private static String replaceLastSegment(@Nonnull String path, @Nonnull String newSegment) {
        int a = path.lastIndexOf('/');
        int b = path.lastIndexOf('\\');
        int i = Math.max(a, b);
        if (i < 0) {
            return newSegment;
        }
        return path.substring(0, i + 1) + newSegment;
    }

    /** {@code market_stall.prefab.json} → {@code Market_Stall.prefab.json} */
    @Nullable
    private static String pascalSnakePrefabFileName(@Nonnull String fileName) {
        if (!fileName.endsWith(".prefab.json")) {
            return null;
        }
        String base = fileName.substring(0, fileName.length() - ".prefab.json".length());
        if (base.isEmpty()) {
            return null;
        }
        String[] parts = base.split("_", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append('_');
            }
            String part = parts[i];
            if (part.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1).toLowerCase());
            }
        }
        return sb.toString() + ".prefab.json";
    }

    /** {@code Market_Stall.prefab.json} → {@code market_stall.prefab.json} */
    @Nullable
    private static String lowerSnakePrefabFileName(@Nonnull String fileName) {
        if (!fileName.endsWith(".prefab.json")) {
            return null;
        }
        String base = fileName.substring(0, fileName.length() - ".prefab.json".length());
        String[] parts = base.split("_", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append('_');
            }
            sb.append(parts[i].toLowerCase());
        }
        return sb.toString() + ".prefab.json";
    }
}
