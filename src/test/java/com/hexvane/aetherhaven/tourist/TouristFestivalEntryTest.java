package com.hexvane.aetherhaven.tourist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.festival.FestivalDefinition;
import com.hexvane.aetherhaven.town.TownRecord;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
final class TouristFestivalEntryTest {
    private static final Gson GSON = new Gson();

    @Test
    void pigRaceVisitorStandIsPreferredOverEmptyFestival() {
        FestivalDefinition pigRace =
            GSON.fromJson(
                """
                {"id":"pig_race","touristSpots":[
                  {"localX":-13,"localY":6,"localZ":-5,"yawDegrees":90.0},
                  {"localX":14,"localY":6,"localZ":5,"yawDegrees":270.0}
                ]}
                """,
                FestivalDefinition.class
            );
        FestivalDefinition.TouristSpotRow spot = TouristDestinationResolver.firstFestivalTouristSpot(pigRace);
        assertNotNull(spot);
        assertEquals(-13, spot.getLocalX());
        assertEquals(6, spot.getLocalY());
        assertEquals(-5, spot.getLocalZ());
    }

    @Test
    void festivalWithoutVisitorStandsHasNoEntrySpot() {
        FestivalDefinition newLife = GSON.fromJson("{\"id\":\"new_life\"}", FestivalDefinition.class);
        assertNull(TouristDestinationResolver.firstFestivalTouristSpot(newLife));
        assertNull(TouristDestinationResolver.firstFestivalTouristSpot((FestivalDefinition) null));
    }

    @Test
    void activeFestivalPlotRequiresMatchingPlotId() {
        UUID plotId = UUID.randomUUID();
        TownRecord town = new TownRecord();
        town.setActiveFestivalId("pig_race");
        town.setActiveFestivalPlotId(plotId);

        assertTrue(TouristDestinationResolver.isActiveFestivalPlot(town, plotId));
        assertFalse(TouristDestinationResolver.isActiveFestivalPlot(town, UUID.randomUUID()));
        assertFalse(TouristDestinationResolver.isActiveFestivalPlot(town, null));
    }
}
