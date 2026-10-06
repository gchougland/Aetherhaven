package com.hexvane.aetherhaven.townsfolk;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.StoreSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;

/** Thread-safe requests keyed by live store identity; values never retain a store or world. */
final class PendingEntityRemovalQueue {
    record Removal(UUID entity, String source) {}
    private static final String DEFAULT_SOURCE = "pending_removal_queue";
    private static final class State {
        final LinkedHashMap<UUID, String> requests = new LinkedHashMap<>();
        boolean closed;
    }
    private final Map<Store<EntityStore>, State> worlds = new WeakHashMap<>();

    StoreSystem<EntityStore> lifecycle() {
        return new StoreSystem<>() {
            @Override public void onSystemAddedToStore(Store<EntityStore> store) {
                synchronized (PendingEntityRemovalQueue.this) {
                    var state = worlds.get(store);
                    if (state != null && state.closed) worlds.remove(store);
                }
            }
            @Override public void onSystemRemovedFromStore(Store<EntityStore> store) { close(store); }
        };
    }

    synchronized void add(Store<EntityStore> store, Iterable<UUID> entities, String source) {
        var state = worlds.get(store);
        if (state != null && state.closed) return;
        for (UUID entity : entities) {
            if (entity == null) continue;
            if (state == null) { state = new State(); worlds.put(store, state); }
            if (source != null && !source.isBlank()) state.requests.put(entity, source);
            else state.requests.putIfAbsent(entity, DEFAULT_SOURCE);
        }
    }

    synchronized Removal poll(Store<EntityStore> store) {
        var state = worlds.get(store);
        if (state == null || state.closed) return null;
        var iterator = state.requests.entrySet().iterator();
        if (!iterator.hasNext()) { worlds.remove(store); return null; }
        var entry = iterator.next(); var removal = new Removal(entry.getKey(), entry.getValue());
        iterator.remove();
        if (state.requests.isEmpty()) worlds.remove(store);
        return removal;
    }

    synchronized void close(Store<EntityStore> store) {
        var state = worlds.computeIfAbsent(store, ignored -> new State());
        state.requests.clear(); state.closed = true;
    }

    synchronized int pendingWorldCount() {
        return (int) worlds.values().stream().filter(state -> !state.requests.isEmpty()).count();
    }
}
