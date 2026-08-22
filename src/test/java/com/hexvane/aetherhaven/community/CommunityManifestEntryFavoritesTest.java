package com.hexvane.aetherhaven.community;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("construction")
class CommunityManifestEntryFavoritesTest {
    private static final Gson GSON = new Gson();

    @Test
    void clearUserHasFavoritedRemovesPerPlayerFlag() {
        CommunityManifestEntry entry =
            GSON.fromJson(
                """
                {"id":"plot_community_test","displayName":"Test","userHasFavorited":true}
                """,
                CommunityManifestEntry.class
            );

        assertTrue(entry.isUserHasFavorited());
        entry.clearUserHasFavorited();
        assertFalse(entry.isUserHasFavorited());
    }
}
