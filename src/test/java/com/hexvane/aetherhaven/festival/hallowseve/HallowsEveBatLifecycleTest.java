package com.hexvane.aetherhaven.festival.hallowseve;

import static org.junit.jupiter.api.Assertions.*;

import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("town")
class HallowsEveBatLifecycleTest {
    private static final class Fixture implements AutoCloseable {
        final ComponentRegistry<EntityStore> registry = new ComponentRegistry<>();
        final ComponentType<EntityStore, NPCEntity> npcType = registry.registerComponent(NPCEntity.class, NPCEntity::new);
        final Store<EntityStore> store;

        Fixture() {
            HallowsEveBatComponent.register(new ComponentRegistryProxy<>(new ArrayList<>(), registry));
            registry.registerSystem(new HallowsEveBatLoadCleanupSystem(npcType));
            store = registry.addStore(null, EmptyResourceStorage.get());
        }

        Holder<EntityStore> bat(UUID town, boolean temporary) {
            var holder = npc(HallowsEveIds.BAT_NPC_ROLE);
            if (town != null) {
                var marker = new HallowsEveBatComponent();
                marker.setTownId(town);
                holder.addComponent(HallowsEveBatComponent.getComponentType(), marker);
            }
            if (temporary) HallowsEveBatSpawnService.prepareTemporaryBat(holder, town, registry);
            return holder;
        }

        Holder<EntityStore> npc(String role) {
            var holder = registry.newHolder();
            NPCEntity npc = new NPCEntity();
            npc.setRoleName(role);
            holder.addComponent(npcType, npc);
            return holder;
        }

        Ref<EntityStore> spawn(UUID town, boolean temporary) {
            return store.addEntity(bat(town, temporary), AddReason.SPAWN);
        }

        Map<UUID, Integer> reconcile(Set<UUID> active) {
            return HallowsEveBatCleanup.reconcile(store, active, npcType);
        }

        public void close() { registry.shutdown(); }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 6, 7, 100, 2000})
    void capsEachTownAndRemovesEntireFlockAtFestivalEnd(int population) {
        try (var f = new Fixture()) {
            UUID town = UUID.randomUUID(), neighbor = UUID.randomUUID();
            for (int i = 0; i < population; i++) f.spawn(town, true);
            for (int i = 0; i < 6; i++) f.spawn(neighbor, true);
            var wild = f.store.addEntity(f.npc("Bat"), AddReason.SPAWN);
            var villager = f.store.addEntity(f.npc("Aetherhaven_Elder"), AddReason.SPAWN);
            assertEquals(Math.min(6, population), f.reconcile(Set.of(town, neighbor)).getOrDefault(town, 0));
            HallowsEveBatCleanup.removeTown(f.store, town);
            assertEquals(Map.of(neighbor, 6), HallowsEveBatSpawnService.countByTown(f.store));
            assertTrue(f.reconcile(Set.of()).isEmpty());
            assertEquals(2, f.store.getEntityCount());
            assertTrue(wild.isValid());
            assertTrue(villager.isValid());
        }
    }

    @Test
    void oldPersistentUnownedAndInvalidTownBatsAreRemovedWithoutTouchingWildBats() {
        try (var f = new Fixture()) {
            UUID active = UUID.randomUUID();
            for (int i = 0; i < 250; i++) {
                f.spawn(active, false);
                f.spawn(null, false); // Older/incompletely spawned NPC with only the festival role.
                f.spawn(null, true); // Marker with no valid town.
            }
            f.spawn(UUID.randomUUID(), true); // Removed town or another world.
            var wild = f.store.addEntity(f.npc("Bat"), AddReason.SPAWN);
            assertTrue(f.reconcile(Set.of(active)).isEmpty());
            assertEquals(1, f.store.getEntityCount());
            assertTrue(wild.isValid());
        }
    }

    @Test
    void legacySavedBatsAreRemovedAsTheirChunksLoadEvenDuringAnotherFestival() {
        try (var f = new Fixture()) {
            UUID oldTown = UUID.randomUUID();
            for (int i = 0; i < 100; i++) {
                var ref = f.spawn(oldTown, false);
                var saved = f.store.removeEntity(ref, RemoveReason.UNLOAD);
                assertTrue(saved.hasSerializableComponents(f.registry.getData()));
                assertNull(f.store.addEntity(saved, AddReason.LOAD));
                assertNull(f.store.addEntity(f.bat(null, false), AddReason.LOAD));
            }
            assertEquals(0, f.store.getEntityCount());
            assertTrue(f.store.addEntity(f.npc("Bat"), AddReason.LOAD).isValid());
        }
    }

    @Test
    void repeatedUnloadAndRestockCannotAccumulateSavedBats() {
        try (var f = new Fixture()) {
            UUID town = UUID.randomUUID();
            for (int cycle = 0; cycle < 100; cycle++) {
                var refs = new ArrayList<Ref<EntityStore>>();
                for (int i = 0; i < 6; i++) refs.add(f.spawn(town, true));
                assertEquals(Map.of(town, 6), f.reconcile(Set.of(town)));
                for (var ref : refs) {
                    var parked = f.store.removeEntity(ref, RemoveReason.UNLOAD);
                    // EntitySection uses this exact predicate to decide whether an unloaded holder is kept.
                    assertFalse(parked.hasSerializableComponents(f.registry.getData()));
                }
                assertEquals(0, f.store.getEntityCount());
            }
        }
    }

    @Test
    void spawnHolderAlreadyHasOwnershipAndCannotBeSaved() {
        try (var f = new Fixture()) {
            UUID town = UUID.randomUUID();
            var holder = f.bat(town, true);
            assertEquals(town, holder.getComponent(HallowsEveBatComponent.getComponentType()).getTownId());
            assertFalse(holder.hasSerializableComponents(f.registry.getData()));
            assertTrue(f.store.addEntity(holder, AddReason.SPAWN).isValid());
        }
    }

    @Test
    void batHeightUsesSquareBaseInsteadOfTopOfReservedAirspace() {
        PlotInstance square = new PlotInstance();
        square.applySignAndFootprint(0, 100, 0, new PlotFootprintRecord(-14, 100, -14, 15, 154, 15));
        assertEquals(120, HallowsEveBatSpawnService.flockAnchor(square).y);
        for (int i = 0; i < 1000; i++) {
            var position = HallowsEveBatSpawnService.randomAirPosition(square);
            assertTrue(position.y >= 120 && position.y < 124);
            assertTrue(position.x >= -12 && position.x < 14);
            assertTrue(position.z >= -12 && position.z < 14);
        }
    }
}
