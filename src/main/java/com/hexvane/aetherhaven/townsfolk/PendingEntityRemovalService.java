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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Queues NPC despawn until the next {@link EntityStore} tick so chunk save never sees invalidated refs. */
public final class PendingEntityRemovalService {
    private static final ConcurrentHashMap<String, ConcurrentLinkedQueue<UUID>> PENDING_BY_WORLD = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, String> PENDING_SOURCE_BY_ENTITY = new ConcurrentHashMap<>();

    private PendingEntityRemovalService() {}

    public static void schedule(@Nonnull World world, @Nonnull UUID entityUuid) {
        schedule(world, entityUuid, "pending_removal_queue");
    }

    public static void schedule(@Nonnull World world, @Nonnull UUID entityUuid, @Nonnull String source) {
        if (!world.isAlive()) {
            return;
        }
        if (source != null && !source.isBlank()) {
            PENDING_SOURCE_BY_ENTITY.put(entityUuid, source);
        }
        PENDING_BY_WORLD.computeIfAbsent(world.getName(), k -> new ConcurrentLinkedQueue<>()).add(entityUuid);
    }

    public static void scheduleAll(@Nonnull World world, @Nonnull List<UUID> entityUuids) {
        scheduleAll(world, entityUuids, "pending_removal_queue");
    }

    public static void scheduleAll(@Nonnull World world, @Nonnull List<UUID> entityUuids, @Nonnull String source) {
        if (!world.isAlive() || entityUuids.isEmpty()) {
            return;
        }
        ConcurrentLinkedQueue<UUID> queue = PENDING_BY_WORLD.computeIfAbsent(world.getName(), k -> new ConcurrentLinkedQueue<>());
        for (UUID entityUuid : entityUuids) {
            if (entityUuid != null) {
                if (source != null && !source.isBlank()) {
                    PENDING_SOURCE_BY_ENTITY.put(entityUuid, source);
                }
                queue.add(entityUuid);
            }
        }
    }

    /** Called from {@link PendingEntityRemovalSystem} once per entity store tick (before chunk store tick). */
    static void flush(@Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        String worldName = world.getName();
        if (!world.isAlive()) {
            PENDING_BY_WORLD.remove(worldName);
            return;
        }
        ConcurrentLinkedQueue<UUID> queue = PENDING_BY_WORLD.get(worldName);
        if (queue == null) {
            return;
        }
        UUID entityUuid;
        while ((entityUuid = queue.poll()) != null) {
            String source = PENDING_SOURCE_BY_ENTITY.remove(entityUuid);
            removeNow(world, store, entityUuid, source != null ? source : "pending_removal_queue");
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
        chunkStore.forEachChunk(
            EntitySection.getComponentType(),
            (archetypeChunk, commandBuffer) -> {
                for (int i = 0; i < archetypeChunk.size(); i++) {
                    Ref<ChunkStore> sectionRef = archetypeChunk.getReferenceTo(i);
                    if (sectionRef == null || !sectionRef.isValid()) {
                        continue;
                    }
                    EntitySection entitySection = chunkStore.getComponent(sectionRef, EntitySection.getComponentType());
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
