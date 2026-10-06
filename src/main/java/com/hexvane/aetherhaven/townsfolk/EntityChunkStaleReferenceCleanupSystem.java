package com.hexvane.aetherhaven.townsfolk;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.component.system.tick.RunWhenPausedSystem;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.chunk.section.EntitySection;
import com.hypixel.hytale.server.core.universe.world.storage.component.ChunkSavingSystems;
import java.util.Set;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;
import javax.annotation.Nonnull;

/** Prunes stale section references before saving, only after changes that can introduce them. */
public final class EntityChunkStaleReferenceCleanupSystem extends TickingSystem<ChunkStore> implements RunWhenPausedSystem<ChunkStore> {
    // Values never retain the store/world. Shared system instances serve several world threads.
    private final Map<Store<ChunkStore>, Boolean> dirty = new WeakHashMap<>();

    synchronized void changed(Store<ChunkStore> store) { dirty.put(store, true); }

    void sweepIfNeeded(Store<ChunkStore> store, Runnable sweep) {
        synchronized (this) {
            if (Boolean.FALSE.equals(dirty.get(store))) return;
            dirty.put(store, false);
        }
        try { sweep.run(); }
        catch (RuntimeException | Error failure) { changed(store); throw failure; }
    }
    @Nonnull
    private final Set<Dependency<ChunkStore>> dependencies = Set.of(
        new SystemDependency<>(Order.BEFORE, ChunkSavingSystems.Ticking.class)
    );

    @Nonnull
    @Override
    public Set<Dependency<ChunkStore>> getDependencies() {
        return dependencies;
    }

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<ChunkStore> store) {
        sweepIfNeeded(store, () -> PendingEntityRemovalService.pruneInvalidEntityReferences(store));
    }

    public RefSystem<EntityStore> removals() {
        return removals(store -> store.getExternalData().getWorld().getChunkStore().getStore());
    }

    RefSystem<EntityStore> removals(Function<Store<EntityStore>, Store<ChunkStore>> chunks) {
        return new RefSystem<>() {
            @Override public Query<EntityStore> getQuery() { return Query.any(); }
            @Override public void onEntityAdded(Ref<EntityStore> ref, AddReason reason, Store<EntityStore> store, CommandBuffer<EntityStore> commands) {}
            @Override public void onEntityRemove(Ref<EntityStore> ref, RemoveReason reason, Store<EntityStore> store, CommandBuffer<EntityStore> commands) {
                // The ECS dispatches this before single and bulk invalidation, including unload.
                // Do not filter by Transform: a missing/replaced transform is why this guard exists.
                changed(chunks.apply(store));
            }
        };
    }

    public RefSystem<ChunkStore> sections() { return sections(EntitySection.getComponentType()); }
    RefSystem<ChunkStore> sections(ComponentType<ChunkStore, EntitySection> type) {
        return new RefSystem<>() {
            @Override public Query<ChunkStore> getQuery() { return type; }
            @Override public void onEntityAdded(Ref<ChunkStore> ref, AddReason reason, Store<ChunkStore> store, CommandBuffer<ChunkStore> commands) { changed(store); }
            @Override public void onEntityRemove(Ref<ChunkStore> ref, RemoveReason reason, Store<ChunkStore> store, CommandBuffer<ChunkStore> commands) { changed(store); }
        };
    }

    public RefChangeSystem<ChunkStore, EntitySection> sectionChanges() { return sectionChanges(EntitySection.getComponentType()); }
    RefChangeSystem<ChunkStore, EntitySection> sectionChanges(ComponentType<ChunkStore, EntitySection> type) {
        return new RefChangeSystem<>() {
            @Override public ComponentType<ChunkStore, EntitySection> componentType() { return type; }
            @Override public Query<ChunkStore> getQuery() { return Query.any(); }
            @Override public void onComponentAdded(Ref<ChunkStore> ref, EntitySection component, Store<ChunkStore> store, CommandBuffer<ChunkStore> commands) { changed(store); }
            @Override public void onComponentSet(Ref<ChunkStore> ref, EntitySection oldComponent, EntitySection newComponent, Store<ChunkStore> store, CommandBuffer<ChunkStore> commands) { changed(store); }
            @Override public void onComponentRemoved(Ref<ChunkStore> ref, EntitySection component, Store<ChunkStore> store, CommandBuffer<ChunkStore> commands) { changed(store); }
        };
    }
}
