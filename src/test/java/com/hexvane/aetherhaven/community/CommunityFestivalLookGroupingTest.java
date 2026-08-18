package com.hexvane.aetherhaven.community;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.plot.PlotBuildingTypes;
import com.hexvane.aetherhaven.plot.PlotCraftingCatalog;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("construction")
final class CommunityFestivalLookGroupingTest {
    private static final Gson GSON = new Gson();

    @Test
    void looksGroupUnderTheBaseHolidayAndUseTheFestivalsType() {
        CommunityManifestEntry neon = entry("plot_community_ab_neon", "Neon Carnival", "carnival");
        assertTrue(neon.isFestivalVariant());
        assertEquals("carnival", neon.getCountsAsFestivalId());
        assertEquals(java.util.Set.of(PlotBuildingTypes.FESTIVALS), neon.getTypeIds());
        assertEquals("Festivals/Festival_plot_community_ab_neon.prefab.json", neon.prefabPathKey());

        Map<String, List<CommunityManifestEntry>> byBase = new LinkedHashMap<>();
        byBase.put(CommunityFestivalLookGrouping.groupKeyFor(neon), List.of(neon));
        List<PlotCraftingCatalog.GroupEntry> groups =
            CommunityFestivalLookGrouping.toGroups(byBase, Map.of("carnival", "Carnival Festival"));
        assertEquals(1, groups.size());
        assertEquals("Carnival Festival", groups.get(0).displayName());
        assertEquals("festivallook:carnival", groups.get(0).groupKey());
        assertEquals("plot_community_ab_neon", groups.get(0).variants().get(0).constructionId());
    }

    private static CommunityManifestEntry entry(String id, String displayName, String countsAsFestivalId) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", id);
        json.put("displayName", displayName);
        json.put("festivalVariant", true);
        json.put("countsAsFestivalId", countsAsFestivalId);
        return GSON.fromJson(GSON.toJson(json), CommunityManifestEntry.class);
    }
}
