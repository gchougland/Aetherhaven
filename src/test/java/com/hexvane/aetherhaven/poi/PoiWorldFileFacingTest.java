package com.hexvane.aetherhaven.poi;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

@org.junit.jupiter.api.Tag("autonomy")
class PoiWorldFileFacingTest {
    @TempDir Path temp;

    @Test void savesFacingAndWorkerEvenWithoutAnInteractionPosition() throws Exception {
        for (boolean target : new boolean[]{false, true}) {
            var entry = new PoiEntry(UUID.randomUUID(), UUID.randomUUID(), 13, 0, 10,
                Set.of("WORK"), 1, null, null, PoiInteractionKind.WORK_SURFACE, false, null,
                target ? 13.5 : null, target ? 0.0 : null, target ? 10.5 : null, -1.3f, "innkeeper");
            Path path = temp.resolve("pois.json");
            PoiWorldFile.fromEntries(List.of(entry)).writeAtomic(path);
            var loaded = PoiWorldFile.toEntries(PoiWorldFile.readOrEmpty(path)).getFirst();
            assertEquals(-1.3f, loaded.getInteractionTargetYawRadians());
            assertEquals("innkeeper", loaded.getWorkResidentKind());
            assertEquals(target, loaded.hasInteractionTarget());
        }
    }

    @Test void partialPositionDoesNotDiscardFacing() {
        var file = new PoiWorldFile();
        var row = new PoiWorldFile.Row();
        row.id = UUID.randomUUID().toString(); row.townId = UUID.randomUUID().toString();
        row.interactionTargetX = 1.0; row.interactionTargetYawRadians = 0f;
        row.workResidentKind = "innkeeper"; file.getPois().add(row);
        var loaded = PoiWorldFile.toEntries(file).getFirst();
        assertFalse(loaded.hasInteractionTarget());
        assertEquals(0f, loaded.getInteractionTargetYawRadians());
        assertEquals("innkeeper", loaded.getWorkResidentKind());
    }
}
