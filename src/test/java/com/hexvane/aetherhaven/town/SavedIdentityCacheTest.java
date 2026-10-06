package com.hexvane.aetherhaven.town;

import com.google.gson.Gson;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class SavedIdentityCacheTest {
    private final Gson gson = new Gson();

    @Test void oldPositionRowsParseOnceWithoutChangingTheirSavedShape() {
        UUID id = UUID.randomUUID();
        String json = "{\"entityUuid\":\" " + id + " \",\"x\":3,\"y\":4,\"z\":5,\"updatedAtEpochMs\":6}";
        var row = gson.fromJson(json, ResidentLastKnownPosition.class);
        String before = gson.toJson(row);
        assertEquals(id, row.getEntityUuid());
        assertSame(row.getEntityUuid(), row.getEntityUuid());
        assertEquals(before, gson.toJson(row));
        row.setPosition(7, 8, 9, 10);
        assertEquals(id, row.getEntityUuid());
        assertEquals(7, row.getX());
        assertEquals(10, row.getUpdatedAtEpochMs());
    }

    @Test void malformedLegacyResidentIdsKeepTheirNilFallback() {
        UUID nil = new UUID(0, 0);
        for (String value : new String[]{"null", "\"\"", "\"bad-id\""}) {
            var position = gson.fromJson("{\"entityUuid\":" + value + "}", ResidentLastKnownPosition.class);
            var resident = gson.fromJson("{\"lastEntityUuid\":" + value + "}", ResidentNpcRecord.class);
            assertEquals(nil, position.getEntityUuid());
            assertSame(position.getEntityUuid(), position.getEntityUuid());
            assertEquals(nil, resident.getLastEntityUuid());
            assertSame(resident.getLastEntityUuid(), resident.getLastEntityUuid());
        }
    }

    @Test void revivalAndPlotReassignmentReplaceCachedIdentitiesAndSurviveReload() {
        UUID oldId = UUID.randomUUID(), nextId = UUID.randomUUID();
        var resident = gson.fromJson("{\"lastEntityUuid\":\"" + oldId + "\"}", ResidentNpcRecord.class);
        var plot = gson.fromJson("{\"plotId\":\"" + oldId + "\"}", PlotInstance.class);
        assertEquals(oldId, resident.getLastEntityUuid());
        assertEquals(oldId, plot.getPlotId());
        resident.setLastEntityUuid(nextId);
        plot.setPlotId(nextId);
        assertSame(nextId, resident.getLastEntityUuid());
        assertSame(nextId, plot.getPlotId());
        assertEquals(nextId, gson.fromJson(gson.toJson(resident), ResidentNpcRecord.class).getLastEntityUuid());
        assertEquals(nextId, gson.fromJson(gson.toJson(plot), PlotInstance.class).getPlotId());
        assertFalse(gson.toJson(resident).contains("parsed"));
        assertFalse(gson.toJson(plot).contains("parsed"));
    }
}
