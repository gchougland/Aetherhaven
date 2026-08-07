package com.hexvane.aetherhaven.plot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("construction")
final class PlotBuildingTypesTest {
    @Test
    void emptyFiltersMatchEverything() {
        assertTrue(PlotBuildingTypes.matchesFilter(List.of(), Set.of()));
        assertTrue(PlotBuildingTypes.matchesFilter(List.of("plot_house"), Set.of()));
        assertTrue(PlotBuildingTypes.matchesFilter(null, Set.of()));
    }

    @Test
    void activeFiltersRequireMatchingType() {
        assertTrue(PlotBuildingTypes.matchesFilter(List.of("plot_house", "plot_inn"), Set.of("plot_house")));
        assertTrue(PlotBuildingTypes.matchesFilter(List.of("decoration"), Set.of("decoration")));
        assertFalse(PlotBuildingTypes.matchesFilter(List.of("plot_house"), Set.of("plot_inn")));
        // Untyped builds stay visible until marketplace type meta is available.
        assertTrue(PlotBuildingTypes.matchesFilter(List.of(), Set.of("plot_house")));
    }

    @Test
    void communityDecorationUsesSentinel() {
        Set<String> ids = PlotBuildingTypes.typeIdsOf(true, List.of("plot_house"), "plot_community_x");
        assertEquals(Set.of(PlotBuildingTypes.DECORATION), ids);
    }

    @Test
    void communityVariantUsesCountsAs() {
        Set<String> ids = PlotBuildingTypes.typeIdsOf(false, List.of("plot_house"), "plot_community_x");
        assertEquals(Set.of("plot_house"), ids);
    }

    @Test
    void communityWithoutCountsAsIsUntyped() {
        Set<String> ids = PlotBuildingTypes.typeIdsOf(false, List.of(), "plot_community_abc_house");
        assertTrue(ids.isEmpty());
    }

    @Test
    void coreCanonicalUsesSelfId() {
        Set<String> ids = PlotBuildingTypes.typeIdsOf(false, List.of(), "plot_town_hall");
        assertEquals(Set.of("plot_town_hall"), ids);
    }
}
