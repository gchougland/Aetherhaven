package com.hexvane.aetherhaven.festival;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.festival.market.MarketIds;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.poi.PoiInteractionKind;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
final class FestivalSpotServiceStallPinTest {
    @Test
    void marketElderBoothIsPinnedLikeATicketStall() {
        TownRecord town = new TownRecord();
        town.setActiveFestivalId(MarketIds.FESTIVAL_ID);

        assertTrue(FestivalSpotService.isStallPinPoi(poi(TownVillagerBinding.KIND_ELDER), town));
        assertTrue(FestivalSpotService.isStallPinPoi(poi(MarketIds.KIND_MARKET_SHOP), town));
        assertTrue(FestivalSpotService.isStallPinPoi(poi(MarketIds.standKind(0)), town));
    }

    @Test
    void carnivalElderWatchPadIsNotAStall() {
        TownRecord town = new TownRecord();
        town.setActiveFestivalId("carnival");

        assertFalse(FestivalSpotService.isStallPinPoi(poi(TownVillagerBinding.KIND_ELDER), town));
    }

    private static PoiEntry poi(String kind) {
        return new PoiEntry(
            UUID.randomUUID(),
            UUID.randomUUID(),
            11,
            5,
            2,
            Set.of("FESTIVAL"),
            1,
            null,
            null,
            PoiInteractionKind.NONE,
            false,
            null,
            11.5,
            5.0,
            2.5,
            0.0f,
            kind
        );
    }
}
