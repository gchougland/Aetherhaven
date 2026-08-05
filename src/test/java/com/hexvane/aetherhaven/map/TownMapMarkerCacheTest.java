package com.hexvane.aetherhaven.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("map-marker")
class TownMapMarkerCacheTest {

    @AfterEach
    void tearDown() {
        TownMapMarkerCache.clearAllForTesting();
    }

    @Test
    void markersForWorld_returnsEmptyWhenUnset() {
        assertTrue(TownMapMarkerCache.markersForWorld("missing-world").isEmpty());
    }

    @Test
    void clearWorld_removesCachedMarkers() {
        MapMarker marker = new MapMarker();
        marker.id = TownMapMarkerProvider.markerId(UUID.randomUUID());
        TownMapMarkerCache.putMarkersForTesting("hexvane", List.of(marker));

        assertEquals(1, TownMapMarkerCache.markersForWorld("hexvane").size());

        TownMapMarkerCache.clearWorld("hexvane");

        assertTrue(TownMapMarkerCache.markersForWorld("hexvane").isEmpty());
    }

    @Test
    void filterTownsForWorld_keepsOnlyMatchingWorld() {
        UUID owner = UUID.randomUUID();
        TownRecord inWorld =
            new TownRecord(UUID.randomUUID(), owner, "hexvane", 0, 64, 0, 1, 1, 0L);
        TownRecord otherWorld =
            new TownRecord(UUID.randomUUID(), owner, "other", 10, 64, 10, 1, 1, 0L);

        List<TownRecord> filtered =
            TownMapMarkerCache.filterTownsForWorld(List.of(inWorld, otherWorld), "hexvane");

        assertEquals(1, filtered.size());
        assertEquals(inWorld.getTownId(), filtered.getFirst().getTownId());
    }
}
