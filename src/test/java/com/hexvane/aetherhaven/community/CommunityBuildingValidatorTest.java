package com.hexvane.aetherhaven.community;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("crossmod")
class CommunityBuildingValidatorTest {
    private static final UUID CREATOR = UUID.fromString("58d4dd63-e0e7-4552-8d5b-3f7ed8ec0063");

    @Test
    void assignCatalogIdUsesDisplayNameSlug() {
        String id = CommunityBuildingValidator.assignCatalogId("plot_my_house", "My House", CREATOR);
        assertEquals("plot_community_58d4dd63_my_house", id);
    }

    @Test
    void assignCatalogIdFallsBackToLocalPlotIdSlug() {
        String id = CommunityBuildingValidator.assignCatalogId("plot_cozy_cottage", "", CREATOR);
        assertEquals("plot_community_58d4dd63_cozy_cottage", id);
    }

    @Test
    void assignCatalogIdKeepsExistingCommunityId() {
        String existing = "plot_community_58d4dd63_my_house";
        String id = CommunityBuildingValidator.assignCatalogId(existing, "Other Name", CREATOR);
        assertEquals(existing, id);
    }

    @Test
    void normalizeCommunityIdRejectsPlotPrefixIds() {
        assertNull(CommunityBuildingValidator.normalizeCommunityId("plot_my_house"));
    }

    @Test
    void prefabPathKeyMatchesInstalledLayout() {
        assertEquals(
            "plot_community_58d4dd63_my_house.prefab.json",
            CommunityBuildingValidator.prefabPathKeyForCommunityId("plot_community_58d4dd63_my_house")
        );
    }

    @Test
    void proposeIdMatchesShortUuidSlice() {
        String id = CommunityBuildingValidator.proposeId(CREATOR.toString(), "building");
        assertTrue(id.startsWith("plot_community_58d4dd63_"));
    }
}
