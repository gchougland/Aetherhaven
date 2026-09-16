package com.hexvane.aetherhaven.shopspot;

import static org.junit.jupiter.api.Assertions.*;
import com.hexvane.aetherhaven.town.TownRecord;
import java.util.ArrayList;
import java.util.UUID;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("autonomy")
class ShopSpotDisplayRefreshTest {
    @Test void overlappingRefreshesQueueOneTaskAndOneUpdatePerStall() {
        var queue = new ShopSpotDisplayQueue();
        var town = new TownRecord();
        var records = new ArrayList<ShopSpotRecord>();
        for (int i = 0; i < 1000; i++) records.add(new ShopSpotRecord());
        int scheduled = 0;
        // Two-second refresh, game-minute refresh, and a stock update overlap.
        for (int refresh = 0; refresh < 3; refresh++)
            for (var record : records) if (queue.offer(record, town)) scheduled++;
        assertEquals(1, scheduled);
        var updates = queue.drain();
        assertEquals(1000, updates.size());
        for (int i = 0; i < updates.size(); i++) {
            assertSame(records.get(i), updates.get(i).record());
            assertSame(town, updates.get(i).town());
        }
        assertTrue(queue.offer(records.getFirst(), town), "The next refresh must schedule again");
    }

    @Test void latestRequestWinsAndImmediateRemovalCancelsPendingSpawn() {
        var queue = new ShopSpotDisplayQueue();
        var town = new TownRecord();
        var soldOut = new ShopSpotRecord();
        var restocked = new ShopSpotRecord();
        var moved = new ShopSpotRecord();
        queue.offer(soldOut, town);
        queue.offer(soldOut, null);
        queue.offer(restocked, null);
        queue.offer(restocked, town);
        queue.offer(moved, town);
        queue.cancel(moved);
        var updates = queue.drain();
        assertEquals(2, updates.size());
        assertTrue(updates.get(0).removal());
        assertFalse(updates.get(1).removal());
    }

    @Test void replacementRecordDoesNotEraseOldEntityTeardown() {
        var queue = new ShopSpotDisplayQueue();
        var id = UUID.randomUUID();
        var old = new ShopSpotRecord(); old.setSpotId(id);
        var replacement = new ShopSpotRecord(); replacement.setSpotId(id);
        queue.offer(old, null);
        queue.offer(replacement, new TownRecord());
        var updates = queue.drain();
        assertEquals(2, updates.size());
        assertSame(old, updates.getFirst().record());
        assertTrue(updates.getFirst().removal());
        assertSame(replacement, updates.getLast().record());
    }

    @Test void cancelledBatchCanStillAcceptNewWorkBeforeItsTaskRuns() {
        var queue = new ShopSpotDisplayQueue();
        var record = new ShopSpotRecord();
        assertTrue(queue.offer(record, new TownRecord()));
        queue.cancel(record);
        assertFalse(queue.offer(record, null), "The already queued task owns this batch");
        assertTrue(queue.drain().getFirst().removal());
        assertTrue(queue.offer(record, new TownRecord()));
    }

    @Test void cleanupRunsOncePerLoadedChunkAndResetsOnUnloadOrRelocation() {
        var record = new ShopSpotRecord();
        record.setBlockPosition(new Vector3i(-32, 100, 31));
        Object chunk = new Object();
        assertFalse(record.needsDisplayReconciliation(null));
        assertTrue(record.needsDisplayReconciliation(chunk));
        record.markDisplayReconciled(chunk);
        for (int refresh = 0; refresh < 1000; refresh++)
            assertFalse(record.needsDisplayReconciliation(chunk), "Closed stalls must not repeatedly rescan entities");
        record.setStock(0);
        record.setDisplayEntityUuid(null);
        assertFalse(record.needsDisplayReconciliation(chunk));
        assertTrue(record.needsDisplayReconciliation(new Object()), "Reloaded chunks need cleanup even without an observed unload");
        record.markDisplayReconciled(null);
        assertTrue(record.needsDisplayReconciliation(chunk));
        record.markDisplayReconciled(chunk);
        record.setBlockPosition(new Vector3i(-31, 100, 31));
        assertTrue(record.needsDisplayReconciliation(chunk), "Moving within the same chunk still needs cleanup");
    }

    @Test void cleanupStateIsNotPersistedWithTheListing() {
        var record = new ShopSpotRecord();
        record.setItemId("Ingredient_Apple");
        record.setStock(3);
        Object chunk = new Object();
        record.markDisplayReconciled(chunk);
        var file = ShopSpotWorldFile.fromRecords(java.util.List.of(record));
        var reloaded = ShopSpotWorldFile.toRecords(file).getFirst();
        assertEquals(record.getItemId(), reloaded.getItemId());
        assertEquals(3, reloaded.getStock());
        assertTrue(reloaded.needsDisplayReconciliation(chunk));
    }

    @Test void orphanCleanupRequiresShopMarkersAndRespectsStallBounds() {
        assertTrue(ShopSpotDisplayService.isRuntimeShopDisplayPropHolder(true, true, true, true));
        assertFalse(ShopSpotDisplayService.isRuntimeShopDisplayPropHolder(true, true, true, false), "Keep entity-tool decorations");
        assertFalse(ShopSpotDisplayService.isRuntimeShopDisplayPropHolder(true, false, false, false), "Keep dropped items");
        assertFalse(ShopSpotDisplayService.isRuntimeShopDisplayPropHolder(false, true, true, true));
        var record = new ShopSpotRecord();
        record.setBlockPosition(new Vector3i(-32, 100, 31));
        assertTrue(ShopSpotDisplayService.isOrphanAtSpot(record, new Vector3d(-31.5, 100.22, 31.5)));
        assertTrue(ShopSpotDisplayService.isOrphanAtSpot(record, new Vector3d(-32.75, 102, 32.75)), "Cleanup crosses chunk boundaries");
        assertFalse(ShopSpotDisplayService.isOrphanAtSpot(record, new Vector3d(-31.5, 103, 31.5)), "Keep the display on the floor above");
        assertFalse(ShopSpotDisplayService.isOrphanAtSpot(record, new Vector3d(-28, 100, 31.5)));
    }
}
