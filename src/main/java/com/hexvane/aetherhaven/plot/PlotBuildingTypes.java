package com.hexvane.aetherhaven.plot;

import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Plot crafting Type filter keys: core gameplay building ids (what a variant counts as) or
 * {@link #DECORATION}.
 */
public final class PlotBuildingTypes {
    /** Sentinel filter id for decoration plots. */
    public static final String DECORATION = "decoration";
    /** Sentinel filter id for wall pieces, which are grouped into styles rather than crafted one at a time. */
    public static final String WALLS = "walls";
    /** Sentinel filter id for festival looks, which are chosen at the bench rather than crafted as tokens. */
    public static final String FESTIVALS = "festivals";

    private PlotBuildingTypes() {}

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
        for (String id : raw) {
            String normalized = normalize(id);
            if (normalized != null) {
                out.add(normalized);
            }
        }
        return new ArrayList<>(out);
    }

    /** Type keys for a local craftable definition. */
    @Nonnull
    public static Set<String> typeIdsOf(@Nonnull ConstructionDefinition def) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (def.isWallSegment()) {
            out.add(WALLS);
            return out;
        }
        if (def.isDecorationPlot()) {
            out.add(DECORATION);
            return out;
        }
        List<String> countsAs = def.getCountsAsConstructionIds();
        if (!countsAs.isEmpty()) {
            for (String id : countsAs) {
                String normalized = normalize(id);
                if (normalized != null) {
                    out.add(normalized);
                }
            }
            return out;
        }
        String self = normalize(def.getId());
        // Community download ids without countsAs are untyped until meta is present.
        if (self != null && !self.startsWith("plot_community_")) {
            out.add(self);
        }
        return out;
    }

    /**
     * Type keys for a community manifest entry.
     *
     * @param decorationPlot from building JSON / manifest
     * @param countsAsConstructionIds alias targets; empty when the build is a canonical core type
     * @param constructionId entry id (fallback when not decoration and no countsAs)
     */
    @Nonnull
    public static Set<String> typeIdsOf(
        boolean decorationPlot,
        @Nullable Collection<String> countsAsConstructionIds,
        @Nullable String constructionId
    ) {
        return typeIdsOf(decorationPlot, false, countsAsConstructionIds, constructionId);
    }

    /**
     * @param wallSegment true when the entry is one piece of a wall style
     */
    @Nonnull
    public static Set<String> typeIdsOf(
        boolean decorationPlot,
        boolean wallSegment,
        @Nullable Collection<String> countsAsConstructionIds,
        @Nullable String constructionId
    ) {
        return typeIdsOf(decorationPlot, wallSegment, false, countsAsConstructionIds, constructionId);
    }

    /**
     * @param festivalVariant true when the entry is a festival look rather than a plot token
     */
    @Nonnull
    public static Set<String> typeIdsOf(
        boolean decorationPlot,
        boolean wallSegment,
        boolean festivalVariant,
        @Nullable Collection<String> countsAsConstructionIds,
        @Nullable String constructionId
    ) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (wallSegment) {
            out.add(WALLS);
            return out;
        }
        if (festivalVariant) {
            out.add(FESTIVALS);
            return out;
        }
        if (decorationPlot
            || (constructionId != null && constructionId.trim().toLowerCase(Locale.ROOT).startsWith("plot_decoration"))) {
            out.add(DECORATION);
            return out;
        }
        List<String> countsAs = normalizeAll(countsAsConstructionIds);
        if (!countsAs.isEmpty()) {
            out.addAll(countsAs);
            return out;
        }
        String self = normalize(constructionId);
        // Do not treat unique community ids as core types.
        if (self != null && !self.startsWith("plot_community_")) {
            out.add(self);
        }
        return out;
    }

    /**
     * Empty filter set means show everything; otherwise the building must have at least one matching type
     * (OR across selected types). Buildings with no type meta yet stay visible so Community browse is not
     * emptied before the marketplace publishes countsAs / decoration flags.
     */
    public static boolean matchesFilter(@Nullable Collection<String> buildingTypeIds, @Nonnull Set<String> activeTypeFilters) {
        if (activeTypeFilters.isEmpty()) {
            return true;
        }
        if (buildingTypeIds == null || buildingTypeIds.isEmpty()) {
            return true;
        }
        for (String typeId : buildingTypeIds) {
            String normalized = normalize(typeId);
            if (normalized != null && activeTypeFilters.contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    public static boolean matchesDefinition(@Nonnull ConstructionDefinition def, @Nonnull Set<String> activeTypeFilters) {
        return matchesFilter(typeIdsOf(def), activeTypeFilters);
    }

    /**
     * Distinct Type filter options: Decorations plus every canonical craftable core building
     * (eligible "variant of" bases — not style variants themselves).
     */
    @Nonnull
    public static List<String> craftableTypeIds(@Nonnull ConstructionCatalog catalog) {
        TreeSet<String> ids = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        boolean anyDecoration = false;
        boolean anyWall = false;
        for (ConstructionDefinition def : catalog.list()) {
            // Wall pieces are not craftable on their own, but the marketplace still needs a Walls filter.
            if (def.isWallSegment()) {
                anyWall = true;
                continue;
            }
            if (!PlotCraftingCatalog.isCraftable(def)) {
                continue;
            }
            if (def.isDecorationPlot()) {
                anyDecoration = true;
                continue;
            }
            // Canonical cores only (no countsAs): House, Inn, Town Hall, …
            if (!def.getCountsAsConstructionIds().isEmpty()) {
                continue;
            }
            String self = normalize(def.getId());
            if (self != null && !self.startsWith("plot_community_")) {
                ids.add(self);
            }
        }
        List<String> out = new ArrayList<>();
        if (anyDecoration) {
            out.add(DECORATION);
        }
        if (anyWall) {
            out.add(WALLS);
        }
        out.add(FESTIVALS);
        out.addAll(ids);
        return out;
    }

    @Nonnull
    public static String displayLabel(@Nonnull ConstructionCatalog catalog, @Nonnull String typeId) {
        if (DECORATION.equalsIgnoreCase(typeId)) {
            return "Decorations";
        }
        if (WALLS.equalsIgnoreCase(typeId)) {
            return "Walls";
        }
        if (FESTIVALS.equalsIgnoreCase(typeId)) {
            return "Festivals";
        }
        ConstructionDefinition def = catalog.get(typeId);
        if (def != null && def.getDisplayName() != null && !def.getDisplayName().isBlank()) {
            return def.getDisplayName();
        }
        String[] parts = typeId.replace('_', ' ').trim().split("\\s+");
        StringBuilder label = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (label.length() > 0) {
                label.append(' ');
            }
            if (part.equalsIgnoreCase("plot")) {
                continue;
            }
            label.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                label.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return label.length() > 0 ? label.toString() : typeId;
    }
}
