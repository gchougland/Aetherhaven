package com.hexvane.aetherhaven.prop;

import com.hexvane.aetherhaven.plotcreator.CustomBuildingsPaths;
import java.nio.file.Path;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Asset-pack and data-directory locations for {@link PropDefinition} JSON and prop prefabs/icons. */
public final class PropPaths {
    /** Relative to pack root: one prop definition JSON per file (recursive). */
    public static final String PACK_RELATIVE = "Server/Aetherhaven/Props";
    /** Prefab store key prefix for prop prefabs. */
    public static final String PREFAB_KEY_PREFIX = "Props/";
    private static final String ICON_FILE_PREFIX = "Aetherhaven_Prop_";

    private PropPaths() {}

    @Nonnull
    public static String packPrefix() {
        return PACK_RELATIVE + "/";
    }

    /** Data-directory folder holding player/authored prop definitions (one JSON per prop id). */
    @Nonnull
    public static Path propsDirectory(@Nonnull Path dataDirectory) {
        return dataDirectory.resolve(PACK_RELATIVE.replace('/', java.io.File.separatorChar));
    }

    @Nonnull
    public static Path propFile(@Nonnull Path propsDirectory, @Nonnull String id) {
        return propsDirectory.resolve(sanitizeFileName(id) + ".json");
    }

    @Nonnull
    public static Path propFileUnderDataDir(@Nonnull Path dataDirectory, @Nonnull String id) {
        return propFile(propsDirectory(dataDirectory), id);
    }

    @Nonnull
    public static Path propPrefabsDirectory(@Nonnull Path dataDirectory) {
        return CustomBuildingsPaths.prefabsDirectory(dataDirectory).resolve("Props");
    }

    @Nonnull
    public static Path propPrefabFile(@Nonnull Path dataDirectory, @Nonnull String prefabFileName) {
        return propPrefabsDirectory(dataDirectory).resolve(prefabFileName);
    }

    @Nonnull
    public static String prefabPathKeyFromPropId(@Nonnull String propId) {
        return PREFAB_KEY_PREFIX + pascalFileStem(propId) + ".prefab.json";
    }

    @Nonnull
    public static String prefabFileNameFromKey(@Nonnull String prefabPathKey) {
        String key = prefabPathKey.trim().replace('\\', '/');
        int slash = key.lastIndexOf('/');
        return slash >= 0 ? key.substring(slash + 1) : key;
    }

    @Nonnull
    public static String iconFileName(@Nonnull String propId) {
        return ICON_FILE_PREFIX + pascalFileStem(propId) + ".png";
    }

    @Nonnull
    public static String iconAssetPath(@Nonnull String propId) {
        return "Icons/ItemsGenerated/" + iconFileName(propId);
    }

    @Nonnull
    public static Path iconFile(@Nonnull Path dataDirectory, @Nonnull String propId) {
        return CustomBuildingsPaths.iconsDirectory(dataDirectory).resolve(iconFileName(propId));
    }

    /** {@code cabbage_trough} / {@code prop_community_aaa_cabbage} → {@code Cabbage_Trough} / {@code Prop_Community_Aaa_Cabbage}. */
    @Nonnull
    public static String pascalFileStem(@Nonnull String propId) {
        String[] parts = propId.trim().split("_+");
        StringBuilder sb = new StringBuilder();
        boolean any = false;
        for (String p : parts) {
            if (p.isBlank()) {
                continue;
            }
            if (any) {
                sb.append('_');
            }
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) {
                sb.append(p.substring(1).toLowerCase(Locale.ROOT));
            }
            any = true;
        }
        return sb.isEmpty() ? "Prop" : sb.toString();
    }

    @Nonnull
    private static String sanitizeFileName(@Nonnull String id) {
        StringBuilder sb = new StringBuilder();
        String trimmed = id.trim();
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return sb.isEmpty() ? "prop" : sb.toString();
    }

    @Nullable
    public static String propIdFromIconFileName(@Nonnull String fileName) {
        String name = fileName.trim();
        if (!name.toLowerCase(Locale.ROOT).endsWith(".png")) {
            return null;
        }
        String base = name.substring(0, name.length() - 4);
        if (!base.startsWith(ICON_FILE_PREFIX) || base.length() <= ICON_FILE_PREFIX.length()) {
            return null;
        }
        return base.substring(ICON_FILE_PREFIX.length()).toLowerCase(Locale.ROOT);
    }
}
