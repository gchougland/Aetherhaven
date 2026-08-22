package com.hexvane.aetherhaven.plot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.plot.PlotCraftingCatalog.GroupEntry;
import com.hexvane.aetherhaven.plot.PlotCraftingCatalog.VariantEntry;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("construction")
class PlotCraftingCatalogMultiCountsAsTest {
    private static final Gson GSON = new Gson();

    @Test
    void groupsForTabListsMultiAliasBuildingUnderEveryCountsAsGroup() {
        ConstructionDefinition inn =
            GSON.fromJson(
                """
                {"id":"plot_inn","displayName":"Inn","prefabPath":"Inn.prefab.json","plotTokenItemId":"Aetherhaven_Plot_Token_Inn"}
                """,
                ConstructionDefinition.class
            );
        ConstructionDefinition guildHall =
            GSON.fromJson(
                """
                {"id":"plot_guild_hall","displayName":"Guild hall","prefabPath":"GuildHall.prefab.json","plotTokenItemId":"Aetherhaven_Plot_Token_Guild_Hall"}
                """,
                ConstructionDefinition.class
            );
        ConstructionDefinition multi =
            GSON.fromJson(
                """
                {"id":"plot_combo_tavern","displayName":"Combo tavern","prefabPath":"Combo.prefab.json","plotTokenItemId":"Aetherhaven_Plot_Token_Inn","countsAsConstructionId":["plot_inn","plot_guild_hall"]}
                """,
                ConstructionDefinition.class
            );
        ConstructionCatalog catalog =
            ConstructionCatalog.forTests(
                Map.of(inn.getId(), inn, guildHall.getId(), guildHall, multi.getId(), multi)
            );

        List<GroupEntry> groups =
            PlotCraftingCatalog.groupsForTab(
                catalog,
                PlotCraftingCatalog.Tab.CORE,
                getClass().getClassLoader(),
                Set.of()
            );

        GroupEntry innGroup = findGroup(groups, "plot_inn");
        GroupEntry guildGroup = findGroup(groups, "plot_guild_hall");
        assertTrue(containsVariant(innGroup, "plot_combo_tavern"));
        assertTrue(containsVariant(guildGroup, "plot_combo_tavern"));
    }

    @Test
    void favoritesGroupsOnlyAddsMultiAliasUnderFavoritedGroupKeys() {
        ConstructionDefinition inn =
            GSON.fromJson(
                """
                {"id":"plot_inn","displayName":"Inn","prefabPath":"Inn.prefab.json","plotTokenItemId":"Aetherhaven_Plot_Token_Inn"}
                """,
                ConstructionDefinition.class
            );
        ConstructionDefinition guildHall =
            GSON.fromJson(
                """
                {"id":"plot_guild_hall","displayName":"Guild hall","prefabPath":"GuildHall.prefab.json","plotTokenItemId":"Aetherhaven_Plot_Token_Guild_Hall"}
                """,
                ConstructionDefinition.class
            );
        ConstructionDefinition multi =
            GSON.fromJson(
                """
                {"id":"plot_combo_tavern","displayName":"Combo tavern","prefabPath":"Combo.prefab.json","plotTokenItemId":"Aetherhaven_Plot_Token_Inn","countsAsConstructionId":["plot_inn","plot_guild_hall"]}
                """,
                ConstructionDefinition.class
            );
        ConstructionCatalog catalog =
            ConstructionCatalog.forTests(
                Map.of(inn.getId(), inn, guildHall.getId(), guildHall, multi.getId(), multi)
            );

        List<GroupEntry> innOnly = PlotCraftingCatalog.favoritesGroups(catalog, Set.of("plot_inn"), Set.of());
        assertEquals(1, innOnly.size());
        assertEquals("plot_inn", innOnly.get(0).groupKey());
        assertTrue(containsVariant(innOnly.get(0), "plot_combo_tavern"));

        List<GroupEntry> favoritedBuilding =
            PlotCraftingCatalog.favoritesGroups(catalog, Set.of("plot_combo_tavern"), Set.of());
        assertEquals(2, favoritedBuilding.size());
        assertTrue(containsVariant(findGroup(favoritedBuilding, "plot_inn"), "plot_combo_tavern"));
        assertTrue(containsVariant(findGroup(favoritedBuilding, "plot_guild_hall"), "plot_combo_tavern"));
    }

    @Nonnull
    private static GroupEntry findGroup(@Nonnull List<GroupEntry> groups, @Nonnull String groupKey) {
        for (GroupEntry group : groups) {
            if (groupKey.equals(group.groupKey())) {
                return group;
            }
        }
        throw new AssertionError("missing group " + groupKey);
    }

    private static boolean containsVariant(@Nonnull GroupEntry group, @Nonnull String constructionId) {
        for (VariantEntry variant : group.variants()) {
            if (constructionId.equals(variant.constructionId())) {
                return true;
            }
        }
        return false;
    }
}
