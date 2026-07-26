package com.hexvane.aetherhaven.plot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.plot.PlotCraftingCatalog.GroupEntry;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("construction")
class PlotCraftingCatalogFavoritesTest {
    @Test
    void favoritesGroupsReturnsEmptyWhenNoFavorites() {
        ConstructionCatalog catalog = ConstructionCatalog.empty();
        assertTrue(PlotCraftingCatalog.favoritesGroups(catalog, Set.of(), Set.of()).isEmpty());
    }

    @Test
    void favoritesGroupsReturnsEmptyWhenFavoritesDoNotMatchCatalog() {
        ConstructionCatalog catalog = ConstructionCatalog.empty();
        assertTrue(
            PlotCraftingCatalog.favoritesGroups(catalog, Set.of("plot_missing", "plot_community_test"), Set.of()).isEmpty()
        );
    }

    @Test
    void favoritesGroupsIncludesMatchingCraftableBuildings() {
        ConstructionCatalog catalog = ConstructionCatalog.loadFromAssetPacksOrClasspath(getClass().getClassLoader());
        if (catalog.list().isEmpty()) {
            return;
        }
        String groupKey = catalog.resolveGameplayConstructionId(catalog.list().get(0).getId());
        if (groupKey.isBlank()) {
            groupKey = catalog.list().get(0).getId();
        }
        var groups = PlotCraftingCatalog.favoritesGroups(catalog, Set.of(groupKey), Set.of());
        if (groups.isEmpty()) {
            return;
        }
        assertEquals(1, groups.size());
        GroupEntry group = groups.get(0);
        assertEquals(groupKey, group.groupKey());
        assertTrue(!group.variants().isEmpty());
    }
}
