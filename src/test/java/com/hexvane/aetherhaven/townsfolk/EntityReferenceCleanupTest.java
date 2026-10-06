package com.hexvane.aetherhaven.townsfolk;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.universe.world.chunk.section.EntitySection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class EntityReferenceCleanupTest {
    private static final class Fixture implements AutoCloseable {
        final EntityChunkStaleReferenceCleanupSystem cleanup = new EntityChunkStaleReferenceCleanupSystem();
        final ComponentRegistry<EntityStore> entities = new ComponentRegistry<>();
        final ComponentRegistry<ChunkStore> sections = new ComponentRegistry<>();
        final ComponentType<ChunkStore, EntitySection> type = sections.registerComponent(EntitySection.class, EntitySection::new);
        final Map<Store<EntityStore>, Store<ChunkStore>> worlds = new IdentityHashMap<>();
        int scans;
        Fixture() {
            entities.registerSystem(cleanup.removals(worlds::get));
            sections.registerSystem(cleanup.sections(type));
            sections.registerSystem(cleanup.sectionChanges(type));
        }
        Store<EntityStore> world() {
            var entityStore = entities.addStore(null, EmptyResourceStorage.get());
            worlds.put(entityStore, sections.addStore(null, EmptyResourceStorage.get()));
            return entityStore;
        }
        Ref<ChunkStore> section(Store<EntityStore> world, EntitySection section) {
            var holder = sections.newHolder(); holder.putComponent(type, section);
            return worlds.get(world).addEntity(holder, AddReason.LOAD);
        }
        Ref<EntityStore> entity(Store<EntityStore> world) {
            // Deliberately no Transform: native chunk unlinking cannot repair this case.
            return world.addEntity(entities.newHolder(), AddReason.SPAWN);
        }
        void sweep(Store<EntityStore> world) {
            var chunks = worlds.get(world);
            cleanup.sweepIfNeeded(chunks, () -> { scans++; PendingEntityRemovalService.pruneInvalidEntityReferences(chunks, type); });
        }
        @Override public void close() {
            for(var pair : worlds.entrySet()) { pair.getKey().shutdown(); pair.getValue().shutdown(); }
        }
    }

    @Test void unchangedTicksSkipSweepsAndNativeSingleRemovalStillCleansBeforeSerialization() {
        try(var f = new Fixture()) {
            var world = f.world(); var section = new EntitySection(); f.section(world, section);
            var alive = f.entity(world); var dead = f.entity(world);
            section.addEntityReference(alive); section.addEntityReference(dead);
            f.sweep(world); assertEquals(1, f.scans);
            for(int i = 0; i < 1000; i++) f.sweep(world);
            assertEquals(1, f.scans); assertEquals(2, section.getEntityReferences().size());
            world.removeEntity(dead, RemoveReason.REMOVE);
            assertFalse(dead.isValid()); assertTrue(section.getEntityReferences().contains(dead));
            section.consumeNeedsSaving(); f.sweep(world);
            assertEquals(2, f.scans); assertEquals(Set.of(alive), section.getEntityReferences());
            assertTrue(section.needsSaving());
            // Exercise the reference-copying part of native section serialization with this isolated registry.
            assertDoesNotThrow(() -> section.getEntityReferences().forEach(ref -> ref.getStore().copySerializableEntity(ref)));
            f.sweep(world); assertEquals(2, f.scans);
        }
    }

    @Test void bulkRemovalAndUnloadCoalesceAndCannotHideAnotherWorldsChanges() {
        try(var f = new Fixture()) {
            var first = f.world(); var second = f.world();
            var a = new EntitySection(); var b = new EntitySection(); f.section(first, a); f.section(second, b);
            var one = f.entity(first); var two = f.entity(first); var other = f.entity(second);
            a.addEntityReference(one); a.addEntityReference(two); b.addEntityReference(other);
            f.sweep(first); f.sweep(second); assertEquals(2, f.scans);
            first.removeEntities(new Ref[]{one, two}, RemoveReason.UNLOAD);
            f.sweep(second); assertEquals(2, f.scans); assertEquals(Set.of(other), b.getEntityReferences());
            f.sweep(first); assertEquals(3, f.scans); assertTrue(a.getEntityReferences().isEmpty());
            assertDoesNotThrow(a::clone);
            second.removeEntity(other, RemoveReason.BUILDER_TOOLS_UNDO);
            f.sweep(first); assertEquals(3, f.scans);
            f.sweep(second); assertEquals(4, f.scans); assertTrue(b.getEntityReferences().isEmpty());
        }
    }

    @Test void loadedAndReplacedSectionsAreCheckedEvenAfterTheRemovalWasAlreadySwept() {
        try(var f = new Fixture()) {
            var world = f.world(); var dead = f.entity(world); world.removeEntity(dead, RemoveReason.REMOVE);
            f.sweep(world);
            var loaded = new EntitySection(); loaded.loadEntityReference(dead);
            var ref = f.section(world, loaded); f.sweep(world);
            assertEquals(2, f.scans); assertTrue(loaded.getEntityReferences().isEmpty());
            var replacement = new EntitySection(); replacement.loadEntityReference(dead);
            f.worlds.get(world).putComponent(ref, f.type, replacement); f.sweep(world);
            assertEquals(3, f.scans); assertTrue(replacement.getEntityReferences().isEmpty());
            f.worlds.get(world).removeComponent(ref, f.type); f.sweep(world);
            var added = new EntitySection(); added.loadEntityReference(dead);
            f.worlds.get(world).putComponent(ref, f.type, added); f.sweep(world);
            assertEquals(5, f.scans); assertTrue(added.getEntityReferences().isEmpty());
        }
    }

    @Test void failedSweepsRetryAndAChangeDuringSweepIsNotLost() {
        try(var f = new Fixture()) {
            var world = f.world(); var chunks = f.worlds.get(world);
            assertThrows(IllegalStateException.class, () -> f.cleanup.sweepIfNeeded(chunks, () -> { throw new IllegalStateException(); }));
            f.sweep(world); assertEquals(1, f.scans);
            f.cleanup.changed(chunks);
            f.cleanup.sweepIfNeeded(chunks, () -> f.cleanup.changed(chunks));
            f.sweep(world); assertEquals(2, f.scans);
        }
    }
}
