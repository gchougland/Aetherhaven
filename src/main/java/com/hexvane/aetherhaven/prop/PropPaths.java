package com.hexvane.aetherhaven.prop;

import java.nio.file.Path;
import javax.annotation.Nonnull;

/** Asset-pack and data-directory locations for {@link PropDefinition} JSON. */
public final class PropPaths {
    /** Relative to pack root: one prop definition JSON per file (recursive). */
    public static final String PACK_RELATIVE = "Server/Aetherhaven/Props";

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
}
