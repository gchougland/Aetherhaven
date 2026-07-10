package com.hexvane.aetherhaven.plot;

import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Style ids from building JSON {@code styleId} fields for plot crafting bench filters. */
public final class PlotBuildingStyles {
    private PlotBuildingStyles() {}

    @Nullable
    public static String normalize(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    @Nullable
    public static String styleIdOf(@Nonnull ConstructionDefinition def) {
        return normalize(def.getStyleId());
    }

    /** Empty filter set means show everything; otherwise require a matching normalized style id. */
    public static boolean matchesFilter(@Nullable String styleId, @Nonnull Set<String> activeStyleFilters) {
        if (activeStyleFilters.isEmpty()) {
            return true;
        }
        String normalized = normalize(styleId);
        return normalized != null && activeStyleFilters.contains(normalized);
    }

    @Nonnull
    public static List<String> craftableStyleIds(@Nonnull ConstructionCatalog catalog) {
        TreeSet<String> ids = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (ConstructionDefinition def : catalog.list()) {
            if (!PlotCraftingCatalog.isCraftable(def)) {
                continue;
            }
            String styleId = styleIdOf(def);
            if (styleId != null) {
                ids.add(styleId);
            }
        }
        return new ArrayList<>(ids);
    }
}
