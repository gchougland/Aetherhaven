package com.hexvane.aetherhaven.prop;

import static org.junit.jupiter.api.Assertions.*;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.EmptyResourceStorage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;
import java.util.Collections;
import java.util.Random;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("prop")
class PropEntityOpsTest {
    @Test
    void collectsEveryOwnedEntityAcrossArchetypesAndPreservesNeighbors() {
        var registry = new ComponentRegistry<EntityStore>();
        AetherhavenPlacedInstance.register(new ComponentRegistryProxy<>(new ArrayList<>(), registry));
        var store = registry.addStore(null, EmptyResourceStorage.get());
        try {
            UUID instance = UUID.randomUUID();
            UUID neighbor = UUID.randomUUID();
            var expected = new HashSet<Ref<EntityStore>>();
            var neighbors = new ArrayList<Ref<EntityStore>>();
            for (int i = 0; i < 4096; i++) {
                var holder = registry.newHolder();
                boolean owned = i % 3 != 0;
                holder.addComponent(AetherhavenPlacedInstance.getComponentType(),
                    new AetherhavenPlacedInstance((owned ? instance : neighbor).toString(), AetherhavenPlacedInstance.Kind.PROP));
                if (i % 2 == 0) holder.ensureComponent(registry.getNonTickingComponentType());
                var ref = store.addEntity(holder, AddReason.SPAWN);
                if (owned) expected.add(ref); else neighbors.add(ref);
            }
            var found = new HashSet<Ref<EntityStore>>();
            // The main packaging path seeds this set with UUID lookups; the tag scan must
            // fill missing lookups without duplicates, including non-ticking entities.
            found.add(expected.iterator().next());
            PropEntityOps.collectTaggedEntities(store, instance, found);
            assertEquals(expected, found);
            PropEntityOps.removeLinkedEntities(store, instance, found, null);
            assertTrue(expected.stream().noneMatch(Ref::isValid));
            assertTrue(neighbors.stream().allMatch(Ref::isValid));
            assertEquals(neighbors.size(), store.getEntityCount());
            found.clear();
            PropEntityOps.collectTaggedEntities(store, instance, found);
            assertTrue(found.isEmpty());
        } finally {
            registry.shutdown();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 31, 32, 63, 64, 127, 512, 4096})
    void pickupHandlesEntityCountsAndAlreadyRemovedReferences(int count) {
        var registry = new ComponentRegistry<EntityStore>();
        AetherhavenPlacedInstance.register(new ComponentRegistryProxy<>(new ArrayList<>(), registry));
        var store = registry.addStore(null, EmptyResourceStorage.get());
        try {
            UUID instance = UUID.randomUUID();
            var refs = new ArrayList<Ref<EntityStore>>();
            var linked = new HashSet<Ref<EntityStore>>();
            for (int i = 0; i < count; i++) {
                var holder = registry.newHolder();
                holder.addComponent(AetherhavenPlacedInstance.getComponentType(),
                    new AetherhavenPlacedInstance(instance.toString(), AetherhavenPlacedInstance.Kind.PROP));
                var ref = store.addEntity(holder, AddReason.SPAWN);
                refs.add(ref);
                if (i % 2 == 0) linked.add(ref);
                if (i % 7 == 0) store.removeEntity(ref, RemoveReason.REMOVE);
            }
            PropEntityOps.removeLinkedEntities(store, instance, linked, null);
            assertTrue(refs.stream().noneMatch(Ref::isValid));
            assertEquals(0, store.getEntityCount());
            // A second pickup must tolerate the original, now-invalid UUID lookup results.
            PropEntityOps.removeLinkedEntities(store, instance, linked, null);
            assertEquals(0, store.getEntityCount());
        } finally {
            registry.shutdown();
        }
    }

    @Test
    void repeatedRandomizedMultiPropPickupLeavesNoEntities() {
        var registry = new ComponentRegistry<EntityStore>();
        AetherhavenPlacedInstance.register(new ComponentRegistryProxy<>(new ArrayList<>(), registry));
        var store = registry.addStore(null, EmptyResourceStorage.get());
        var random = new Random(0xA37E);
        long spawned = 0;
        try {
            for (int cycle = 0; cycle < 250; cycle++) {
                var groups = new java.util.LinkedHashMap<UUID, ArrayList<Ref<EntityStore>>>();
                for (int prop = 0; prop < 6; prop++) {
                    UUID id = UUID.randomUUID();
                    var refs = new ArrayList<Ref<EntityStore>>();
                    groups.put(id, refs);
                    for (int i = random.nextInt(1, 129); i > 0; i--) {
                        var holder = registry.newHolder();
                        holder.addComponent(AetherhavenPlacedInstance.getComponentType(),
                            new AetherhavenPlacedInstance(id.toString(), AetherhavenPlacedInstance.Kind.PROP));
                        if (random.nextBoolean()) holder.ensureComponent(registry.getNonTickingComponentType());
                        var ref = store.addEntity(holder, AddReason.SPAWN);
                        // Reload some before pickup: saved UUID lookups can point to a new Ref.
                        if (random.nextInt(8) == 0) {
                            var unloaded = store.removeEntity(ref, RemoveReason.UNLOAD);
                            ref = store.addEntity(unloaded, AddReason.LOAD);
                        }
                        refs.add(ref);
                        spawned++;
                    }
                }
                var order = new ArrayList<>(groups.keySet());
                Collections.shuffle(order, random);
                for (UUID id : order) {
                    PropEntityOps.removeLinkedEntities(store, id, new HashSet<>(), null);
                    assertTrue(groups.remove(id).stream().noneMatch(Ref::isValid), "cycle " + cycle);
                    for (var survivors : groups.values()) assertTrue(survivors.stream().allMatch(Ref::isValid));
                }
                assertEquals(0, store.getEntityCount(), "cycle " + cycle);
            }
            System.out.println("Prop stress: 1,500 pickups across 250 cycles; " + spawned + " entities, zero leftovers.");
        } finally {
            registry.shutdown();
        }
    }
}
