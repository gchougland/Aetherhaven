package com.hexvane.aetherhaven.pathtool;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("autonomy")
class PathCommitRecordTest {
    @Test
    void missingVillagerNavFieldDefaultsTrue() {
        PathCommitRecord rec =
            new Gson()
                .fromJson(
                    "{\"id\":\"x\",\"townId\":\"t\",\"navNodes\":[{\"x\":0.0,\"y\":0.0,\"z\":0.0},{\"x\":1.0,\"y\":0.0,\"z\":0.0}]}",
                    PathCommitRecord.class
                );
        assertTrue(rec.villagerNav);
        assertTrue(rec.includeInTownsfolkGraph());
    }

    @Test
    void villagerNavFalseIsExcludedFromTownsfolkGraph() {
        PathCommitRecord rec = new PathCommitRecord();
        rec.id = UUID.randomUUID().toString();
        rec.townId = UUID.randomUUID().toString();
        rec.navNodes = List.of(new PathNavPoint(0, 0, 0), new PathNavPoint(3, 0, 0));
        rec.villagerNav = false;
        assertFalse(rec.includeInTownsfolkGraph());
        rec.villagerNav = true;
        assertTrue(rec.includeInTownsfolkGraph());
    }
}
