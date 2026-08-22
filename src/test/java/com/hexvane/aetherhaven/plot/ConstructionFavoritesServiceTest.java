package com.hexvane.aetherhaven.plot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("construction")
class ConstructionFavoritesServiceTest {
    @Test
    void toggleNormalizesCaseAndPreservesOrder() {
        PlayerConstructionFavoritesState state = new PlayerConstructionFavoritesState();
        assertTrue(state.toggle("Plot_House_A"));
        assertTrue(state.isFavorite("plot_house_a"));
        assertFalse(state.toggle("PLOT_HOUSE_A"));
        assertFalse(state.isFavorite("plot_house_a"));
        assertTrue(state.toggle("plot_house_b"));
        assertTrue(state.toggle("plot_house_a"));
        assertEquals(List.of("plot_house_b", "plot_house_a"), state.favoritesOrdered());
    }

    @Test
    void mergeFromAddsNormalizedIds() {
        PlayerConstructionFavoritesState state = new PlayerConstructionFavoritesState();
        state.mergeFrom(List.of("Plot_Community_AAA", " ", "plot_community_bbb"));
        assertTrue(state.isFavorite("plot_community_aaa"));
        assertTrue(state.isFavorite("plot_community_bbb"));
    }

    @Test
    void syncCommunityFavoritesReplacesCommunityIdsButKeepsCore() {
        PlayerConstructionFavoritesState state = new PlayerConstructionFavoritesState();
        state.add("plot_town_hall");
        state.add("plot_community_old");
        state.add("plot_community_keep");

        state.syncCommunityFavorites(List.of("plot_community_keep", "plot_community_new"));

        assertTrue(state.isFavorite("plot_town_hall"));
        assertFalse(state.isFavorite("plot_community_old"));
        assertTrue(state.isFavorite("plot_community_keep"));
        assertTrue(state.isFavorite("plot_community_new"));
        assertEquals(
            List.of("plot_town_hall", "plot_community_keep", "plot_community_new"),
            state.favoritesOrdered()
        );
    }

    @Test
    void syncCommunityFavoritesClearsAllCommunityIdsWhenRemoteEmpty() {
        PlayerConstructionFavoritesState state = new PlayerConstructionFavoritesState();
        state.add("plot_house");
        state.add("plot_community_a");

        state.syncCommunityFavorites(List.of());

        assertTrue(state.isFavorite("plot_house"));
        assertFalse(state.isFavorite("plot_community_a"));
    }

    @Test
    void retainKnownRemovesUnknownIds() {
        PlayerConstructionFavoritesState state = new PlayerConstructionFavoritesState();
        state.add("plot_a");
        state.add("plot_b");
        state.retainKnown(Set.of("plot_b", "plot_c"));
        assertFalse(state.isFavorite("plot_a"));
        assertTrue(state.isFavorite("plot_b"));
    }

    @Test
    void isCommunityBuildingIdDetectsPrefix() {
        assertTrue(ConstructionFavoritesService.isCommunityBuildingId("plot_community_abc"));
        assertTrue(ConstructionFavoritesService.isCommunityBuildingId("PLOT_COMMUNITY_X"));
        assertFalse(ConstructionFavoritesService.isCommunityBuildingId("plot_house"));
    }

    @Test
    void favoritesCopyIsImmutable() {
        PlayerConstructionFavoritesState state = new PlayerConstructionFavoritesState();
        state.add("plot_a");
        Set<String> copy = state.favorites();
        assertThrowsOnMutate(copy);
    }

    private static void assertThrowsOnMutate(Set<String> copy) {
        try {
            copy.add("plot_b");
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("Expected immutable favorites view");
    }
}
