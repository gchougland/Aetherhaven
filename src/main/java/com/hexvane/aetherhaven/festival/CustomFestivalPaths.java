package com.hexvane.aetherhaven.festival;

import com.hexvane.aetherhaven.asset.AetherhavenAssetPaths;
import java.nio.file.Path;
import java.util.Locale;
import javax.annotation.Nonnull;

/** Paths under the plugin data directory ({@code mods/Hexvane_Aetherhaven}) for player authored festivals. */
public final class CustomFestivalPaths {
    public static final String PREFABS_RELATIVE = "Server/Prefabs/Festivals";

    /** Base prefab every new festival starts from; the plot creator must never write over it. */
    public static final String BASE_PREFAB_PATH = "Festivals/Festival_Square.prefab.json";
    public static final String BASE_PREFAB_FILE_NAME = "Festival_Square.prefab.json";
    public static final String RESERVED_FESTIVAL_ID = "festival_square";

    private CustomFestivalPaths() {}

    @Nonnull
    public static Path festivalsDirectory(@Nonnull Path dataDirectory) {
        return dataDirectory.resolve(AetherhavenAssetPaths.FESTIVALS);
    }

    @Nonnull
    public static Path festivalFile(@Nonnull Path dataDirectory, @Nonnull String festivalId) {
        return festivalsDirectory(dataDirectory).resolve(festivalId.trim() + ".json");
    }

    @Nonnull
    public static Path prefabsDirectory(@Nonnull Path dataDirectory) {
        return dataDirectory.resolve(PREFABS_RELATIVE);
    }

    @Nonnull
    public static Path prefabFile(@Nonnull Path dataDirectory, @Nonnull String festivalId) {
        return prefabsDirectory(dataDirectory).resolve(prefabFileName(festivalId));
    }

    @Nonnull
    public static String prefabFileName(@Nonnull String festivalId) {
        return "Festival_" + festivalId.trim() + ".prefab.json";
    }

    /** Prefab key stored in festival JSON, e.g. {@code Festivals/Festival_new_life.prefab.json}. */
    @Nonnull
    public static String prefabPathKey(@Nonnull String festivalId) {
        return "Festivals/" + prefabFileName(festivalId);
    }

    /** Finds a player saved festival prefab on disk for a prefab key such as {@code Festivals/Festival_x.prefab.json}. */
    @javax.annotation.Nullable
    public static Path resolvePrefabFile(@Nonnull Path dataDirectory, @javax.annotation.Nullable String prefabPathKey) {
        if (prefabPathKey == null || prefabPathKey.isBlank()) {
            return null;
        }
        String key = prefabPathKey.trim().replace('\\', '/');
        String rawName = key.contains("/") ? key.substring(key.lastIndexOf('/') + 1) : key;
        final String fileName = rawName.endsWith(".prefab.json") ? rawName : rawName + ".prefab.json";
        Path dir = prefabsDirectory(dataDirectory);
        Path candidate = dir.resolve(fileName);
        if (java.nio.file.Files.isRegularFile(candidate)) {
            return candidate;
        }
        // Match Festival_Carnival vs Festival_carnival when the catalog key casing differs from the save.
        if (!java.nio.file.Files.isDirectory(dir)) {
            return null;
        }
        try (var stream = java.nio.file.Files.list(dir)) {
            return stream
                .filter(java.nio.file.Files::isRegularFile)
                .filter(p -> p.getFileName().toString().equalsIgnoreCase(fileName))
                .findFirst()
                .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /** True when saving under this id or prefab name would clobber the shared base festival square prefab. */
    public static boolean isReserved(@Nonnull String festivalIdOrPrefabName) {
        String v = festivalIdOrPrefabName.trim().toLowerCase(Locale.ROOT);
        if (v.isEmpty()) {
            return true;
        }
        return v.equals(RESERVED_FESTIVAL_ID)
            || v.equals("square")
            || v.equals(BASE_PREFAB_FILE_NAME.toLowerCase(Locale.ROOT))
            || v.equals("festival_square.prefab.json")
            || v.equals(BASE_PREFAB_PATH.toLowerCase(Locale.ROOT));
    }
}
