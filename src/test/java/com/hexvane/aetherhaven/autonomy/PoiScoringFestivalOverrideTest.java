package com.hexvane.aetherhaven.autonomy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.poi.PoiInteractionKind;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Festival stand spots are handed out by the festival itself, so ordinary need scoring must never pick one up as a
 * spare seat or work desk.
 */
@Tag("autonomy")
final class PoiScoringFestivalOverrideTest {
    @Test
    void festivalTagsAreRecognised() {
        assertTrue(PoiScoring.isFestivalPoi(poi(Set.of(AetherhavenConstants.POI_TAG_FESTIVAL))));
        assertTrue(PoiScoring.isFestivalPoi(poi(Set.of(AetherhavenConstants.POI_TAG_FESTIVAL_EPHEMERAL))));
        assertFalse(PoiScoring.isFestivalPoi(poi(Set.of("FUN", "SIT"))));
    }

    @Test
    void festivalSpotsAreNeverPickedByNeedScoring() {
        PoiEntry festivalSpot =
            poi(Set.of("FUN", "SIT", AetherhavenConstants.POI_TAG_FESTIVAL, AetherhavenConstants.POI_TAG_FESTIVAL_EPHEMERAL));
        TownVillagerBinding binding =
            new TownVillagerBinding(UUID.randomUUID(), TownVillagerBinding.KIND_FARMER, null, null);
        VillagerNeeds needs = VillagerNeeds.full();
        needs.setFun(1f);

        assertNull(PoiScoring.pickBest(List.of(festivalSpot), needs, binding));
    }

    @Test
    void ordinaryFunSpotsAreStillPickedWhenAFestivalSpotIsAlsoNearby() {
        PoiEntry festivalSpot = poi(Set.of("FUN", "SIT", AetherhavenConstants.POI_TAG_FESTIVAL));
        PoiEntry bench = poi(Set.of("FUN", "SIT"));
        TownVillagerBinding binding =
            new TownVillagerBinding(UUID.randomUUID(), TownVillagerBinding.KIND_FARMER, null, null);
        VillagerNeeds needs = VillagerNeeds.full();
        needs.setFun(1f);

        PoiEntry picked = PoiScoring.pickBest(List.of(festivalSpot, bench), needs, binding);

        assertEquals(bench.getId(), picked != null ? picked.getId() : null);
    }

    private static PoiEntry poi(Set<String> tags) {
        return new PoiEntry(
            UUID.randomUUID(),
            UUID.randomUUID(),
            1,
            64,
            1,
            Set.copyOf(tags),
            1,
            UUID.randomUUID(),
            null,
            PoiInteractionKind.SIT
        );
    }
}
