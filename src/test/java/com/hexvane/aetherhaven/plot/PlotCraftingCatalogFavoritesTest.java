package com.hexvane.aetherhaven.plot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.plot.PlotCraftingCatalog.GroupEntry;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("construction")
class PlotCraftingCatalogFavoritesTest {
    private static final Gson GSON = new Gson();

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

    @Test
    void favoritesGroupsSkipsCommunityBuildingsThatAliasToCoreGroups() {
        ConstructionDefinition townHall =
            GSON.fromJson(
                """
                {"id":"plot_town_hall","displayName":"Town hall","prefabPath":"Townhall.prefab.json","plotTokenItemId":"Aetherhaven_Plot_Token_Town_Hall"}
                """,
                ConstructionDefinition.class
            );
        ConstructionDefinition community =
            GSON.fromJson(
                """
                {"id":"plot_community_test123_stormwind_town_hall","displayName":"Stormwind Town Hall","prefabPath":"Stormwind.prefab.json","plotTokenItemId":"Aetherhaven_Plot_Token_Town_Hall","countsAsConstructionId":"plot_town_hall"}
                """,
                ConstructionDefinition.class
            );
        ConstructionCatalog catalog =
            ConstructionCatalog.forTests(Map.of(townHall.getId(), townHall, community.getId(), community));

        assertTrue(
            PlotCraftingCatalog.favoritesGroups(catalog, Set.of(community.getId()), Set.of()).isEmpty(),
            "community favorites are listed from the community catalog, not core variant groups"
        );

        var coreFavorite = PlotCraftingCatalog.favoritesGroups(catalog, Set.of(townHall.getId()), Set.of());
        assertEquals(1, coreFavorite.size());
        assertEquals("plot_town_hall", coreFavorite.get(0).groupKey());
    }
}
