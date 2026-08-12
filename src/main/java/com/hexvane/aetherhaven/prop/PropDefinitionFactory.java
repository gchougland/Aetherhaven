package com.hexvane.aetherhaven.prop;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import java.nio.file.Path;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Builds a {@link PropDefinition} from a prefab picked in the prefab browser. */
public final class PropDefinitionFactory {
    private PropDefinitionFactory() {}

    /** Derives a stable prop id from a prefab path key (last segment, lowercase, non-alphanumerics collapsed). */
    @Nonnull
    public static String idFromPrefabPath(@Nonnull String prefabPathKey) {
        String base = lastSegmentWithoutExtension(prefabPathKey);
        StringBuilder sb = new StringBuilder();
        boolean lastWasUnderscore = false;
        for (int i = 0; i < base.length(); i++) {
            char c = base.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
                lastWasUnderscore = false;
            } else if (!lastWasUnderscore && sb.length() > 0) {
                sb.append('_');
                lastWasUnderscore = true;
            }
        }
        while (sb.length() > 0 && sb.charAt(sb.length() - 1) == '_') {
            sb.setLength(sb.length() - 1);
        }
        return sb.length() > 0 ? sb.toString() : "prop";
    }

    /** Title-cased display name derived from the prefab file name, e.g. {@code market_stall} -> {@code Market Stall}. */
    @Nonnull
    public static String displayNameFromPrefabPath(@Nonnull String prefabPathKey) {
        String base = lastSegmentWithoutExtension(prefabPathKey);
        String[] words = base.split("[_\\s]+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                sb.append(word.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return sb.length() > 0 ? sb.toString() : "Prop";
    }

    /** Forward-slash key relative to {@code prefabsRoot}, as expected by {@code PrefabStore.findAssetPrefabPath}. */
    @Nullable
    public static String prefabPathKeyFromResolved(@Nonnull Path resolvedAbsolutePath, @Nonnull Path prefabsRoot) {
        try {
            Path abs = resolvedAbsolutePath.toAbsolutePath().normalize();
            Path root = prefabsRoot.toAbsolutePath().normalize();
            if (!abs.startsWith(root)) {
                return null;
            }
            Path rel = root.relativize(abs);
            String key = rel.toString().replace('\\', '/');
            return key.isBlank() ? null : key;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** A prop prefab must contain at least one solid voxel; a purely-air prefab has nothing to place/protect. */
    public static boolean validatePropPrefab(@Nonnull IPrefabBuffer buffer) {
        return PropPrefabOps.hasOriginSolids(Rotation.None, buffer);
    }

    @Nonnull
    public static PropDefinition buildPropDefinition(@Nonnull String prefabPathKey, @Nullable String displayNameOverride) {
        String id = idFromPrefabPath(prefabPathKey);
        String displayName =
            displayNameOverride != null && !displayNameOverride.isBlank()
                ? displayNameOverride.trim()
                : displayNameFromPrefabPath(prefabPathKey);
        return PropDefinition.create(id, displayName, prefabPathKey);
    }

    /**
     * Resolves a store-relative prefab path key from an absolute file path by checking each browsable pack root
     * (same approach as Eternia's prefab browser).
     */
    @Nonnull
    public static String prefabPathKeyFromResolved(@Nonnull Path resolvedFile) {
        String fileName = resolvedFile.getFileName().toString();
        PrefabStore store = PrefabStore.get();
        if (store.findAssetPrefabPath(fileName) != null) {
            return fileName;
        }
        for (var packPath : store.getAllBrowsablePrefabPaths()) {
            Path prefabsRoot = packPath.prefabsPath();
            try {
                Path relative = prefabsRoot.relativize(resolvedFile);
                if (!relative.toString().startsWith("..")) {
                    String key = relative.toString().replace('\\', '/');
                    if (store.findAssetPrefabPath(key) != null) {
                        return key;
                    }
                }
            } catch (IllegalArgumentException ignored) {
                // Not under this pack root.
            }
        }
        return fileName;
    }

    @Nonnull
    public static PropDefinition buildPropDefinitionFromVirtual(@Nonnull String virtualPath, @Nonnull Path resolvedFile) {
        String id = idFromPrefabPath(virtualPath);
        String displayName = displayNameFromPrefabPath(virtualPath);
        String prefabPath = prefabPathKeyFromResolved(resolvedFile);
        return PropDefinition.create(id, displayName, prefabPath);
    }

    @Nonnull
    private static String lastSegmentWithoutExtension(@Nonnull String path) {
        String trimmed = path.trim();
        int a = trimmed.lastIndexOf('/');
        int b = trimmed.lastIndexOf('\\');
        int i = Math.max(a, b);
        String base = i >= 0 ? trimmed.substring(i + 1) : trimmed;
        if (base.endsWith(".prefab.json")) {
            return base.substring(0, base.length() - ".prefab.json".length());
        }
        if (base.endsWith(".json")) {
            return base.substring(0, base.length() - ".json".length());
        }
        return base;
    }
}
