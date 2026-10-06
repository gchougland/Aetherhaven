package com.hexvane.aetherhaven.townsfolk;

import com.hexvane.aetherhaven.villager.audit.VillagerAuditContext;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.EntitySection;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Queues NPC despawn until the next {@link EntityStore} tick so chunk save never sees invalidated refs. */
public final class PendingEntityRemovalService {
    private static final PendingEntityRemovalQueue PENDING = new PendingEntityRemovalQueue();

    private PendingEntityRemovalService() {}

    public static void schedule(@Nonnull World world, @Nonnull UUID entityUuid) {
        schedule(world, entityUuid, "pending_removal_queue");
    }

    public static void schedule(@Nonnull World world, @Nonnull UUID entityUuid, @Nonnull String source) {
        if (!world.isAlive()) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store != null) PENDING.add(store, List.of(entityUuid), source);
    }

    public static void scheduleAll(@Nonnull World world, @Nonnull List<UUID> entityUuids) {
        scheduleAll(world, entityUuids, "pending_removal_queue");
    }

    public static void scheduleAll(@Nonnull World world, @Nonnull List<UUID> entityUuids, @Nonnull String source) {
        if (!world.isAlive() || entityUuids.isEmpty()) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store != null) PENDING.add(store, entityUuids, source);
    }

    /** Actual store shutdown, not a cancellable world-removal request, ends the queue's lifetime. */
    public static com.hypixel.hytale.component.system.StoreSystem<EntityStore> lifecycle() {
        return PENDING.lifecycle();
    }

    /** Called from {@link PendingEntityRemovalSystem} once per entity store tick (before chunk store tick). */
    static void flush(@Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        if (!world.isAlive()) {
            PENDING.close(store);
            return;
        }
        PendingEntityRemovalQueue.Removal removal;
        while ((removal = PENDING.poll(store)) != null) {
            removeNow(world, store, removal.entity(), removal.source());
        }
    }

    private static void removeNow(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID entityUuid,
        @Nonnull String source
    ) {
        Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(entityUuid);
        if (ref == null || !ref.isValid()) {
            return;
        }
        detachFromEntityChunk(world, store, ref);
        if (ref.isValid()) {
            VillagerAuditContext.runWithSource(source, () -> store.removeEntity(ref, RemoveReason.REMOVE));
        }
    }

    private static void detachFromEntityChunk(
        @Nonnull World world, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref
    ) {
        boolean removed = false;
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform != null) {
            Ref<ChunkStore> sectionRef = transform.getSectionRef();
            if (sectionRef != null && sectionRef.isValid()) {
                removed = removeEntityReferenceFromSection(world, sectionRef, ref);
            }
        }
        if (!removed) {
            removeEntityReferenceFromLoadedSections(world, ref);
        }
    }

    private static boolean removeEntityReferenceFromSection(
        @Nonnull World world, @Nonnull Ref<ChunkStore> sectionRef, @Nonnull Ref<EntityStore> ref
    ) {
        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
        EntitySection entitySection = chunkStore.getComponent(sectionRef, EntitySection.getComponentType());
        if (entitySection == null || !entitySection.getEntityReferences().contains(ref)) {
            return false;
        }
        entitySection.removeEntityReference(ref);
        return true;
    }

    /** Fallback when {@link TransformComponent#getSectionRef()} was cleared (e.g. transform replaced without chunk linkage). */
    private static void removeEntityReferenceFromLoadedSections(@Nonnull World world, @Nonnull Ref<EntityStore> ref) {
        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
        chunkStore.forEachChunk(
            EntitySection.getComponentType(),
            (archetypeChunk, commandBuffer) -> {
                for (int i = 0; i < archetypeChunk.size(); i++) {
                    Ref<ChunkStore> sectionRef = archetypeChunk.getReferenceTo(i);
                    if (sectionRef == null || !sectionRef.isValid()) {
                        continue;
                    }
                    removeEntityReferenceFromSection(world, sectionRef, ref);
                }
            }
        );
    }

    /** Drops invalidated entity refs so chunk serialization never sees them. */
    static void pruneInvalidEntityReferences(@Nonnull Store<ChunkStore> chunkStore) {
        pruneInvalidEntityReferences(chunkStore, EntitySection.getComponentType());
    }

    static void pruneInvalidEntityReferences(@Nonnull Store<ChunkStore> chunkStore,
            com.hypixel.hytale.component.ComponentType<ChunkStore, EntitySection> type) {
        chunkStore.forEachChunk(
            type,
            (archetypeChunk, commandBuffer) -> {
                for (int i = 0; i < archetypeChunk.size(); i++) {
                    // The query already supplies live section rows on the world thread.
                    EntitySection entitySection = archetypeChunk.getComponent(i, type);
                    if (entitySection == null) {
                        continue;
                    }
                    Set<Ref<EntityStore>> entityReferences = entitySection.getEntityReferences();
                    if (entityReferences.isEmpty()) {
                        continue;
                    }
                    List<Ref<EntityStore>> stale = null;
                    for (Ref<EntityStore> entityRef : entityReferences) {
                        if (!entityRef.isValid()) {
                            if (stale == null) {
                                stale = new ArrayList<>();
                            }
                            stale.add(entityRef);
                        }
                    }
                    if (stale != null) {
                        for (Ref<EntityStore> entityRef : stale) {
                            entitySection.removeEntityReference(entityRef);
                        }
                    }
                }
            }
        );
    }
}
