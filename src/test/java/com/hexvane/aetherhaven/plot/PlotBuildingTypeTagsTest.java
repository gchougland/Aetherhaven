package com.hexvane.aetherhaven.plot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class PlotBuildingTypeTagsTest {
    @Test
    void emptyFiltersMatchEverything() {
        assertTrue(PlotBuildingTypeTags.matchesFilter(List.of(), Set.of()));
        assertTrue(PlotBuildingTypeTags.matchesFilter(List.of("shop"), Set.of()));
        assertTrue(PlotBuildingTypeTags.matchesFilter(null, Set.of()));
    }

    @Test
    void activeFiltersRequireMatchingTag() {
        assertTrue(PlotBuildingTypeTags.matchesFilter(List.of("shop", "merchant"), Set.of("shop")));
        assertTrue(PlotBuildingTypeTags.matchesFilter(List.of("Home"), Set.of("home")));
        assertFalse(PlotBuildingTypeTags.matchesFilter(List.of("shop"), Set.of("inn")));
        assertFalse(PlotBuildingTypeTags.matchesFilter(List.of(), Set.of("shop")));
        assertFalse(PlotBuildingTypeTags.matchesFilter(null, Set.of("shop")));
    }
}
