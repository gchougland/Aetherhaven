package com.hexvane.aetherhaven.plot;

import com.hexvane.aetherhaven.community.CommunityFestivalLookGrouping;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.festival.FestivalCatalog;
import com.hexvane.aetherhaven.festival.FestivalDefinition;
import com.hexvane.aetherhaven.festival.FestivalLookSelection;
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
        FESTIVALS,
        FAVORITES,
        COMMUNITY,
        MODERATION
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
            for (String groupKey : groupKeysFor(catalog, def)) {
                byGroup.computeIfAbsent(groupKey, k -> new ObjectArrayList<>()).add(def);
            }
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

    /**
     * Local festival looks grouped under the holiday they count as. Looks are chosen at the bench, not crafted as
     * tokens.
     */
    @Nonnull
    public static List<GroupEntry> festivalLookGroups(
        @Nonnull FestivalCatalog catalog,
        @Nonnull Set<String> activeStyleFilters
    ) {
        return festivalLookGroups(catalog, activeStyleFilters, Collections.emptySet());
    }

    @Nonnull
    public static List<GroupEntry> festivalLookGroups(
        @Nonnull FestivalCatalog catalog,
        @Nonnull Set<String> activeStyleFilters,
        @Nonnull Set<String> activeTypeFilters
    ) {
        if (!PlotBuildingTypes.matchesFilter(Set.of(PlotBuildingTypes.FESTIVALS), activeTypeFilters)) {
            return List.of();
        }
        Map<String, ObjectArrayList<FestivalDefinition>> byBase = new Object2ObjectOpenHashMap<>();
        for (FestivalDefinition base : catalog.listBases()) {
            List<FestivalDefinition> looks = FestivalLookSelection.looksOf(catalog, base.getId());
            if (looks.isEmpty()) {
                continue;
            }
            ObjectArrayList<FestivalDefinition> grouped = new ObjectArrayList<>();
            for (FestivalDefinition look : looks) {
                if (!PlotBuildingStyles.matchesFilter(look.getStyleId(), activeStyleFilters)) {
                    continue;
                }
                grouped.add(look);
            }
            if (grouped.isEmpty()) {
                continue;
            }
            grouped.sort(Comparator.comparing(d -> d.getDisplayName().toLowerCase(Locale.ROOT)));
            byBase.put(base.getId(), grouped);
        }
        ObjectArrayList<GroupEntry> groups = new ObjectArrayList<>();
        for (Map.Entry<String, ObjectArrayList<FestivalDefinition>> e : byBase.entrySet()) {
            FestivalDefinition base = catalog.get(e.getKey());
            String display =
                base != null && base.getDisplayName() != null && !base.getDisplayName().isBlank()
                    ? base.getDisplayName()
                    : e.getKey();
            ObjectArrayList<VariantEntry> variants = new ObjectArrayList<>();
            for (FestivalDefinition look : e.getValue()) {
                variants.add(
                    new VariantEntry(
                        look.getId(),
                        look.getDisplayName() != null && !look.getDisplayName().isBlank()
                            ? look.getDisplayName()
                            : look.getId(),
                        look.getPrefabPath()
                    )
                );
            }
            if (!variants.isEmpty()) {
                groups.add(
                    new GroupEntry(CommunityFestivalLookGrouping.groupKeyForBase(e.getKey()), display, variants)
                );
            }
        }
        groups.sort(Comparator.comparing(g -> g.displayName().toLowerCase(Locale.ROOT)));
        return groups;
    }

    @Nonnull
    public static List<GroupEntry> favoritesGroups(
        @Nonnull ConstructionCatalog catalog,
        @Nonnull Set<String> favoriteIds,
        @Nonnull Set<String> activeStyleFilters
    ) {
        return favoritesGroups(catalog, favoriteIds, activeStyleFilters, Collections.emptySet());
    }

    @Nonnull
    public static List<GroupEntry> favoritesGroups(
        @Nonnull ConstructionCatalog catalog,
        @Nonnull Set<String> favoriteIds,
        @Nonnull Set<String> activeStyleFilters,
        @Nonnull Set<String> activeTypeFilters
    ) {
        if (favoriteIds.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new HashSet<>();
        for (String id : favoriteIds) {
            if (id != null && !id.isBlank()) {
                normalized.add(id.trim().toLowerCase(Locale.ROOT));
            }
        }
        if (normalized.isEmpty()) {
            return List.of();
        }
        Map<String, ObjectArrayList<ConstructionDefinition>> byGroup = new Object2ObjectOpenHashMap<>();
        for (ConstructionDefinition def : catalog.list()) {
            if (!isCraftable(def)) {
                continue;
            }
            if (ConstructionFavoritesService.isCommunityBuildingId(def.getId())) {
                continue;
            }
            if (!matchesStyleFilter(def, activeStyleFilters)) {
                continue;
            }
            if (!matchesTypeFilter(def, activeTypeFilters)) {
                continue;
            }
            List<String> groupKeys = groupKeysFor(catalog, def);
            String defId = def.getId().trim().toLowerCase(Locale.ROOT);
            boolean favoritedDirectly = normalized.contains(defId);
            ObjectArrayList<String> matchingKeys = new ObjectArrayList<>();
            for (String groupKey : groupKeys) {
                if (favoritedDirectly || normalized.contains(groupKey.trim().toLowerCase(Locale.ROOT))) {
                    matchingKeys.add(groupKey);
                }
            }
            if (matchingKeys.isEmpty()) {
                continue;
            }
            for (String groupKey : matchingKeys) {
                byGroup.computeIfAbsent(groupKey, k -> new ObjectArrayList<>()).add(def);
            }
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

    /**
     * Every core building group this definition should appear under. Multi {@code countsAsConstructionId} variants
     * list under each alias; canonical builds use their own id.
     */
    @Nonnull
    static List<String> groupKeysFor(@Nonnull ConstructionCatalog catalog, @Nonnull ConstructionDefinition def) {
        List<String> keys = catalog.resolveGameplayConstructionIds(def.getId());
        if (keys.isEmpty()) {
            return List.of(def.getId());
        }
        return keys;
    }

    private static boolean matchesStyleFilter(@Nonnull ConstructionDefinition def, @Nonnull Set<String> activeStyleFilters) {
        return PlotBuildingStyles.matchesFilter(def.getStyleId(), activeStyleFilters);
    }

    private static boolean matchesTypeFilter(@Nonnull ConstructionDefinition def, @Nonnull Set<String> activeTypeFilters) {
        return PlotBuildingTypes.matchesDefinition(def, activeTypeFilters);
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
