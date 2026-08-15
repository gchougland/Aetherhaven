package com.hexvane.aetherhaven.community;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.plot.PlotBuildingTypes;
import com.hexvane.aetherhaven.plot.PlotCraftingCatalog;
import com.hexvane.aetherhaven.wall.WallPieceRole;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Wall pieces from the marketplace collapse into one card per style, straight run first. */
@Tag("construction")
final class CommunityWallStyleGroupingTest {
    private static final Gson GSON = new Gson();

    @Test
    void piecesOfOneStyleBecomeASingleGroupInAuthoringOrder() {
        Map<String, List<CommunityManifestEntry>> byStyle = new LinkedHashMap<>();
        byStyle.put(
            "wallstyle:mossy",
            List.of(
                entry("plot_community_a_mossy_tower_corner", "Mossy wall corner tower", "mossy", "tower_corner"),
                entry("plot_community_a_mossy_segment", "Mossy wall", "mossy", "segment"),
                entry("plot_community_a_mossy_gate", "Mossy wall gate", "mossy", "gate")
            )
        );
        List<PlotCraftingCatalog.GroupEntry> groups = CommunityWallStyleGrouping.toGroups(byStyle);
        assertEquals(1, groups.size());
        assertEquals("Mossy wall", groups.get(0).displayName());
        assertEquals(
            List.of(
                "plot_community_a_mossy_segment",
                "plot_community_a_mossy_gate",
                "plot_community_a_mossy_tower_corner"
            ),
            groups.get(0).variants().stream().map(PlotCraftingCatalog.VariantEntry::constructionId).toList()
        );
    }

    @Test
    void twoStylesStayApart() {
        assertEquals(
            "wallstyle:mossy",
            CommunityWallStyleGrouping.groupKeyFor(entry("plot_community_a_x", "X", "mossy", "segment"))
        );
        assertEquals(
            "wallstyle:brick",
            CommunityWallStyleGrouping.groupKeyFor(entry("plot_community_a_y", "Y", "brick", "segment"))
        );
    }

    @Test
    void aPieceWithNoStyleIdKeepsItsOwnCard() {
        assertEquals(
            "wallstyle:plot_community_a_lonely",
            CommunityWallStyleGrouping.groupKeyFor(entry("plot_community_a_lonely", "Lonely", null, "segment"))
        );
    }

    @Test
    void wallEntriesFilterUnderWalls() {
        CommunityManifestEntry piece = entry("plot_community_a_mossy_gate", "Mossy wall gate", "mossy", "gate");
        assertTrue(piece.isWallSegment());
        assertEquals(WallPieceRole.GATE, piece.getWallPieceRole());
        assertEquals(java.util.Set.of(PlotBuildingTypes.WALLS), piece.getTypeIds());
    }

    @Test
    void onlyWallGroupKeysAreTreatedAsStyles() {
        assertTrue(CommunityWallStyleGrouping.isWallStyleGroupKey("wallstyle:mossy"));
        assertFalse(CommunityWallStyleGrouping.isWallStyleGroupKey("plot_community_a_house"));
        assertFalse(CommunityWallStyleGrouping.isWallStyleGroupKey(null));
    }

    @Nonnull
    private static CommunityManifestEntry entry(
        @Nonnull String id, @Nonnull String displayName, String styleId, @Nonnull String role
    ) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", id);
        json.put("displayName", displayName);
        json.put("wallSegment", true);
        json.put("wallPieceRole", role);
        if (styleId != null) {
            json.put("styleId", styleId);
        }
        return GSON.fromJson(GSON.toJson(json), CommunityManifestEntry.class);
    }
}
