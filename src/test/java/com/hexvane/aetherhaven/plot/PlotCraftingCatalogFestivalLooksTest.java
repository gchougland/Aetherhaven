package com.hexvane.aetherhaven.plot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.festival.FestivalCatalog;
import com.hexvane.aetherhaven.festival.FestivalDefinition;
import com.hexvane.aetherhaven.plot.PlotCraftingCatalog.GroupEntry;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("construction")
final class PlotCraftingCatalogFestivalLooksTest {
    private static final Gson GSON = new Gson();

    @Test
    void localLooksGroupUnderTheHolidayWithoutATypeFilter() {
        FestivalCatalog catalog = FestivalCatalog.forTests(List.of(base("carnival", "Carnival Festival"), look()));
        List<GroupEntry> groups = PlotCraftingCatalog.festivalLookGroups(catalog, Set.of());
        assertEquals(1, groups.size());
        assertEquals("Carnival Festival", groups.get(0).displayName());
        assertEquals("festivallook:carnival", groups.get(0).groupKey());
        assertEquals("carnival_neon", groups.get(0).variants().get(0).constructionId());
    }

    @Test
    void styleFilterHidesLooksThatDoNotMatch() {
        FestivalCatalog catalog = FestivalCatalog.forTests(List.of(base("carnival", "Carnival Festival"), look()));
        assertTrue(PlotCraftingCatalog.festivalLookGroups(catalog, Set.of("stormwind")).isEmpty());
        List<GroupEntry> neon = PlotCraftingCatalog.festivalLookGroups(catalog, Set.of("neon"));
        assertEquals(1, neon.size());
        assertEquals("carnival_neon", neon.get(0).variants().get(0).constructionId());
    }

    @Test
    void houseTypeFilterHidesLooksOnFavorites() {
        FestivalCatalog catalog = FestivalCatalog.forTests(List.of(base("carnival", "Carnival Festival"), look()));
        assertTrue(
            PlotCraftingCatalog.festivalLookGroups(catalog, Set.of(), Set.of("plot_house")).isEmpty()
        );
    }

    private static FestivalDefinition base(String id, String displayName) {
        return GSON.fromJson(
            """
            {"id":"%s","displayName":"%s","prefabPath":"Festivals/Festival_%s.prefab.json","season":"Summer","dayOfSeason":21}
            """.formatted(id, displayName, id),
            FestivalDefinition.class
        );
    }

    private static FestivalDefinition look() {
        return GSON.fromJson(
            """
            {
              "id": "carnival_neon",
              "displayName": "Neon Carnival",
              "prefabPath": "Festivals/Festival_carnival_neon.prefab.json",
              "season": "Summer",
              "dayOfSeason": 21,
              "festivalVariant": true,
              "countsAsFestivalId": "carnival",
              "styleId": "Neon"
            }
            """,
            FestivalDefinition.class
        );
    }
}
