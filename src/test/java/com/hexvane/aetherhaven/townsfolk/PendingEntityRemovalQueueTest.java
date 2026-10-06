package com.hexvane.aetherhaven.townsfolk;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class PendingEntityRemovalQueueTest {
    private static Store<EntityStore> store() {
        return new ComponentRegistry<EntityStore>().addStore(null, EmptyResourceStorage.get());
    }

    @Test void duplicateRequestsKeepOrderAndLatestExplicitSourceWithoutRetainingEmptyQueues() {
        var queue = new PendingEntityRemovalQueue(); var store = store();
        try {
            UUID first = UUID.randomUUID(), second = UUID.randomUUID();
            queue.add(store, Arrays.asList(null, first, second), "original");
            queue.add(store, List.of(first), "updated");
            queue.add(store, List.of(first), " ");
            assertEquals(new PendingEntityRemovalQueue.Removal(first, "updated"), queue.poll(store));
            assertEquals(new PendingEntityRemovalQueue.Removal(second, "original"), queue.poll(store));
            assertNull(queue.poll(store)); assertEquals(0, queue.pendingWorldCount());
            queue.add(store, List.of(first), null);
            assertEquals(new PendingEntityRemovalQueue.Removal(first, "pending_removal_queue"), queue.poll(store));
            queue.add(store, Arrays.asList(null, null), null);
            assertEquals(0, queue.pendingWorldCount()); assertNull(queue.poll(store));
        } finally { store.shutdown(); }
    }

    @Test void closingOneStoreDiscardsRequestsAndCannotAffectReplacementStoreOrItsAuditSource() {
        var queue = new PendingEntityRemovalQueue();
        var registry = new ComponentRegistry<EntityStore>(); registry.registerSystem(queue.lifecycle());
        var first = registry.addStore(null, EmptyResourceStorage.get()); var replacement = registry.addStore(null, EmptyResourceStorage.get());
        try {
            UUID entity = UUID.randomUUID();
            queue.add(first, List.of(entity), "old world");
            queue.add(replacement, List.of(entity), "new world");
            first.shutdown();
            queue.add(first, List.of(entity), "late request");
            assertNull(queue.poll(first)); assertEquals(1, queue.pendingWorldCount());
            assertEquals(new PendingEntityRemovalQueue.Removal(entity, "new world"), queue.poll(replacement));
            assertEquals(0, queue.pendingWorldCount());
        } finally { if (!first.isShutdown()) first.shutdown(); replacement.shutdown(); }
    }

    @Test void concurrentSchedulingAndDrainingLoseNoRequestsWhenQueuesBecomeEmpty() throws Exception {
        var queue = new PendingEntityRemovalQueue(); var store = store();
        try (var executor = Executors.newFixedThreadPool(3)) {
            int perProducer = 2000;
            var start = new CountDownLatch(1);
            var producers = new ArrayList<Future<?>>();
            for (int producer = 1; producer <= 2; producer++) {
                final int id = producer;
                producers.add(executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < perProducer; i++) queue.add(store, List.of(new UUID(id, i)), "producer " + id);
                    return null;
                }));
            }
            var consumed = executor.submit(() -> {
                start.await();
                var seen = new HashSet<UUID>();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (seen.size() < perProducer * 2 && System.nanoTime() < deadline) {
                    var removal = queue.poll(store);
                    if (removal == null) { Thread.yield(); continue; }
                    assertTrue(seen.add(removal.entity()), "Duplicate removal");
                    assertEquals("producer " + removal.entity().getMostSignificantBits(), removal.source());
                }
                return seen.size();
            });
            start.countDown();
            for (var producer : producers) producer.get(15, TimeUnit.SECONDS);
            assertEquals(perProducer * 2, consumed.get(15, TimeUnit.SECONDS));
            assertEquals(0, queue.pendingWorldCount());
        } finally { store.shutdown(); }
    }
}
