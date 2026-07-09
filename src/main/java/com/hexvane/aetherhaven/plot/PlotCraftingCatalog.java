package com.hexvane.aetherhaven.plot;

import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Builds grouped building lists for the plot crafting bench UI. */
public final class PlotCraftingCatalog {
    public enum Tab {
        CORE,
        DECORATIONS,
        COMMUNITY
    }

    public record VariantEntry(@Nonnull String constructionId, @Nonnull String displayName, @Nullable String prefabPathKey) {}

    public record GroupEntry(@Nonnull String groupKey, @Nonnull String displayName, @Nonnull List<VariantEntry> variants) {}

    private PlotCraftingCatalog() {}

    @Nonnull
    public static List<GroupEntry> groupsForTab(@Nonnull ConstructionCatalog catalog, @Nonnull Tab tab, @Nonnull ClassLoader classLoader) {
        return groupsForTab(catalog, tab, classLoader, Collections.emptySet());
    }

    @Nonnull
    public static List<GroupEntry> groupsForTab(
        @Nonnull ConstructionCatalog catalog,
        @Nonnull Tab tab,
        @Nonnull ClassLoader classLoader,
        @Nonnull Set<String> activeStyleFilters
    ) {
        Map<String, ObjectArrayList<ConstructionDefinition>> byGroup = new Object2ObjectOpenHashMap<>();
        for (ConstructionDefinition def : catalog.list()) {
            if (!isCraftable(def)) {
                continue;
            }
            Tab defTab = tabFor(def);
            if (defTab != tab) {
                continue;
            }
            if (!matchesStyleFilter(def, activeStyleFilters)) {
                continue;
            }
            String groupKey = catalog.resolveGameplayConstructionId(def.getId());
            if (groupKey.isBlank()) {
                groupKey = def.getId();
            }
            byGroup.computeIfAbsent(groupKey, k -> new ObjectArrayList<>()).add(def);
        }

        ObjectArrayList<GroupEntry> groups = new ObjectArrayList<>();
        for (Map.Entry<String, ObjectArrayList<ConstructionDefinition>> e : byGroup.entrySet()) {
            String groupKey = e.getKey();
            List<ConstructionDefinition> defs = e.getValue();
            defs.sort(variantOrder(groupKey));
            ObjectArrayList<VariantEntry> variants = new ObjectArrayList<>();
            for (ConstructionDefinition d : defs) {
                variants.add(
                    new VariantEntry(
                        d.getId(),
                        d.getDisplayName() != null && !d.getDisplayName().isBlank() ? d.getDisplayName() : d.getId(),
                        d.getPrefabPath()
                    )
                );
            }
            if (!variants.isEmpty()) {
                groups.add(new GroupEntry(groupKey, resolveGroupDisplayName(catalog, groupKey), variants));
            }
        }
        groups.sort(Comparator.comparing(g -> g.displayName().toLowerCase(Locale.ROOT)));
        return groups;
    }

    static boolean isCraftable(@Nonnull ConstructionDefinition def) {
        if (def.isWallSegment()) {
            return false;
        }
        if (!def.consumesPlotToken()) {
            return false;
        }
        String prefab = def.getPrefabPath();
        return prefab != null && !prefab.isBlank();
    }

    private static boolean matchesStyleFilter(@Nonnull ConstructionDefinition def, @Nonnull Set<String> activeStyleFilters) {
        if (activeStyleFilters.isEmpty()) {
            return true;
        }
        String styleId = PlotBuildingStyles.styleIdOf(def);
        return styleId != null && activeStyleFilters.contains(styleId);
    }

    @Nonnull
    private static Tab tabFor(@Nonnull ConstructionDefinition def) {
        if (def.isDecorationPlot() || def.getBuildingTags().contains("decoration")) {
            return Tab.DECORATIONS;
        }
        return Tab.CORE;
    }

    @Nonnull
    private static Comparator<ConstructionDefinition> variantOrder(@Nonnull String groupKey) {
        return (a, b) -> {
            boolean aCanon = groupKey.equals(a.getId());
            boolean bCanon = groupKey.equals(b.getId());
            if (aCanon != bCanon) {
                return aCanon ? -1 : 1;
            }
            String an = a.getDisplayName() != null ? a.getDisplayName() : a.getId();
            String bn = b.getDisplayName() != null ? b.getDisplayName() : b.getId();
            return an.compareToIgnoreCase(bn);
        };
    }

    @Nonnull
    private static String resolveGroupDisplayName(@Nonnull ConstructionCatalog catalog, @Nonnull String groupKey) {
        ConstructionDefinition canon = catalog.get(groupKey);
        if (canon != null && canon.getDisplayName() != null && !canon.getDisplayName().isBlank()) {
            return canon.getDisplayName();
        }
        return groupKey;
    }
}
