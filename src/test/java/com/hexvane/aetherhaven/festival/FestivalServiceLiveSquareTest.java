package com.hexvane.aetherhaven.festival;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.town.TownRecord;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
final class FestivalServiceLiveSquareTest {
    @Test
    void liveSquareRequiresActiveFestivalAndMatchingPlot() {
        UUID plotId = UUID.randomUUID();
        TownRecord town = new TownRecord();
        town.setActiveFestivalId("carnival");
        town.setActiveFestivalPlotId(plotId);

        assertTrue(FestivalService.isLiveFestivalSquare(town, plotId));
        assertFalse(FestivalService.isLiveFestivalSquare(town, UUID.randomUUID()));
        assertFalse(FestivalService.isLiveFestivalSquare(town, null));
    }

    @Test
    void everydaySquareIsNotLive() {
        UUID plotId = UUID.randomUUID();
        TownRecord town = new TownRecord();
        town.setActiveFestivalPlotId(plotId);

        assertFalse(FestivalService.isLiveFestivalSquare(town, plotId));
    }
}
