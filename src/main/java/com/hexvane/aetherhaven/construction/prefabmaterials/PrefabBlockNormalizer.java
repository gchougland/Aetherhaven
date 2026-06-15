package com.hexvane.aetherhaven.construction.prefabmaterials;

import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

final class PrefabBlockNormalizer {
    private static final Pattern STATE_DEFINITIONS = Pattern.compile("_State_Definitions_.*$");
    private static final String HOLLOW_SUFFIX = "_Hollow";

    private PrefabBlockNormalizer() {}

    @Nullable
    static String normalizeBlockToItemId(@Nonnull String rawName) {
        String name = rawName.strip();
        if (name.isEmpty() || "Empty".equals(name)) {
            return null;
        }
        if (name.startsWith("*")) {
            name = name.substring(1);
        }
        if (name.isEmpty()) {
            return null;
        }
        name = STATE_DEFINITIONS.matcher(name).replaceAll("");
        if (name.endsWith(HOLLOW_SUFFIX)) {
            String base = name.substring(0, name.length() - HOLLOW_SUFFIX.length());
            if (!base.isEmpty()) {
                name = base;
            }
        }
        return name.isEmpty() ? null : name;
    }
}
