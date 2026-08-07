package com.hexvane.aetherhaven.plot;

import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Building {@code tags} for plot crafting bench type filters (community / favorites). */
public final class PlotBuildingTypeTags {
    private PlotBuildingTypeTags() {}

    @Nullable
    public static String normalize(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    @Nonnull
    public static List<String> normalizeAll(@Nullable Collection<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        TreeSet<String> out = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String tag : raw) {
            String normalized = normalize(tag);
            if (normalized != null) {
                out.add(normalized);
            }
        }
        return new ArrayList<>(out);
    }

    /**
     * Empty filter set means show everything; otherwise the building must have at least one matching tag
     * (OR across selected types).
     */
    public static boolean matchesFilter(@Nullable Collection<String> buildingTags, @Nonnull Set<String> activeTypeFilters) {
        if (activeTypeFilters.isEmpty()) {
            return true;
        }
        if (buildingTags == null || buildingTags.isEmpty()) {
            return false;
        }
        for (String tag : buildingTags) {
            String normalized = normalize(tag);
            if (normalized != null && activeTypeFilters.contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    @Nonnull
    public static List<String> craftableTypeTags(@Nonnull ConstructionCatalog catalog) {
        TreeSet<String> ids = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (ConstructionDefinition def : catalog.list()) {
            if (!PlotCraftingCatalog.isCraftable(def)) {
                continue;
            }
            ids.addAll(def.getBuildingTags());
        }
        return new ArrayList<>(ids);
    }
}
