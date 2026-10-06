package com.hexvane.aetherhaven.poi;

import com.hexvane.aetherhaven.autonomy.VillagerAutonomyState;
import com.hexvane.aetherhaven.tourist.TouristAutonomyState;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class PoiOccupancyTest {
    private static final ComponentRegistry<EntityStore> REGISTRY = new ComponentRegistry<>();
    private static final Map<java.lang.reflect.Field, Object> ORIGINAL_TYPES = new LinkedHashMap<>();

    @BeforeAll static void register() throws ReflectiveOperationException {
        // Production registration is intentionally one time. Isolate this native
        // ECS fixture and restore the bindings used by other test registries.
        for (var type : List.of(VillagerAutonomyState.class, TouristAutonomyState.class, TownVillagerBinding.class)) {
            var field = type.getDeclaredField("componentType");
            field.setAccessible(true);
            ORIGINAL_TYPES.put(field, field.get(null));
            field.set(null, null);
        }
        var proxy = new ComponentRegistryProxy<>(new ArrayList<>(), REGISTRY);
        VillagerAutonomyState.register(proxy);
        TouristAutonomyState.register(proxy);
        TownVillagerBinding.register(proxy);
    }

    @AfterAll static void restoreBindings() throws IllegalAccessException {
        for (var entry : ORIGINAL_TYPES.entrySet()) entry.getKey().set(null, entry.getValue());
    }

    @Test @Timeout(15)
    void countsMixedResidentsExactlyDuringNestedWorldThreadScans() {
        var store = REGISTRY.addStore(null, EmptyResourceStorage.get());
        try {
            var town = UUID.randomUUID();
            var other = UUID.randomUUID();
            var poi = new PoiEntry(UUID.randomUUID(), town, 10, 80, 20, Set.of("REST"),
                1, null, null, PoiInteractionKind.WORK_SURFACE, 11.5, 80.0, 20.5);
            var registry = new PoiRegistry(null);
            registry.registerEphemeral(poi);
            for (int i = 0; i < 300; i++) {
                add(store, town, poi, false, VillagerAutonomyState.PHASE_TRAVEL);
                add(store, town, poi, true, TouristAutonomyState.PHASE_POI);
                add(store, town, poi, false, VillagerAutonomyState.PHASE_IDLE);
                add(store, other, poi, true, TouristAutonomyState.PHASE_TRAVEL);
            }
            var expected = Map.of(PoiOccupancy.standCellKey(poi), 600);
            // Occupancy is queried from within NPC ticking. Exercise nested store
            // traversal repeatedly with multiple archetypes and exact counts.
            for (int pass = 0; pass < 20; pass++) {
                int[] scans = {0};
                store.forEachChunk(Query.and(TownVillagerBinding.getComponentType()), (chunk, commands) -> {
                    assertEquals(expected, PoiOccupancy.buildCellCounts(store, town, registry));
                    scans[0]++;
                });
                assertTrue(scans[0] >= 2);
            }
        } finally {
            store.shutdown();
        }
    }

    @Test void emptyTownAndRemovedPoiUseCurrentOccupancy() {
        var store = REGISTRY.addStore(null, EmptyResourceStorage.get());
        try {
            var town = UUID.randomUUID();
            var registry = new PoiRegistry(null);
            assertTrue(PoiOccupancy.buildCellCounts(store, town, registry).isEmpty());
            var poi = new PoiEntry(UUID.randomUUID(), town, 10, 80, 20, Set.of("REST"),
                1, null, null, PoiInteractionKind.WORK_SURFACE);
            registry.registerEphemeral(poi);
            add(store, town, poi, false, VillagerAutonomyState.PHASE_USE);
            add(store, town, poi, true, TouristAutonomyState.PHASE_RETURNING);
            registry.unregisterEphemeral(poi.getId());
            assertEquals(Map.of("11,80,20", 2), PoiOccupancy.buildCellCounts(store, town, registry));
            assertTrue(PoiOccupancy.buildCellCounts(store, UUID.randomUUID(), registry).isEmpty());
        } finally {
            store.shutdown();
        }
    }

    @Test void otherWorldTicksDoNotEraseSameTickClaimsOrShareCounts() {
        var first = REGISTRY.addStore(null, EmptyResourceStorage.get());
        var second = REGISTRY.addStore(null, EmptyResourceStorage.get());
        try {
            var town = UUID.randomUUID();
            var registry = new PoiRegistry(null);
            var firstCounts = PoiOccupancy.cellOccupancyAtTick(first, town, registry, 20);
            assertTrue(PoiOccupancy.tryClaimCell(firstCounts, "11,80,20", 1));
            var secondCounts = PoiOccupancy.cellOccupancyAtTick(second, town, registry, 7);
            assertTrue(secondCounts.isEmpty());
            assertTrue(PoiOccupancy.tryClaimCell(secondCounts, "11,80,20", 1));
            assertSame(firstCounts, PoiOccupancy.cellOccupancyAtTick(first, town, registry, 20));
            assertFalse(PoiOccupancy.tryClaimCell(firstCounts, "11,80,20", 1));
            assertTrue(PoiOccupancy.cellOccupancyAtTick(first, UUID.randomUUID(), registry, 20).isEmpty());
            assertTrue(PoiOccupancy.cellOccupancyAtTick(second, town, registry, 8).isEmpty());
            assertSame(firstCounts, PoiOccupancy.cellOccupancyAtTick(first, town, registry, 20));
            assertEquals(Map.of("11,80,20", 1), firstCounts);
            var nextTick = PoiOccupancy.cellOccupancyAtTick(first, town, registry, 21);
            assertNotSame(firstCounts, nextTick);
            assertTrue(nextTick.isEmpty());
        } finally {
            first.shutdown();
            second.shutdown();
        }
    }

    private static void add(Store<EntityStore> store, UUID town, PoiEntry poi, boolean tourist, int phase) {
        var holder = REGISTRY.newHolder();
        holder.putComponent(TownVillagerBinding.getComponentType(), new TownVillagerBinding(town, "logger", null));
        if (tourist) {
            var state = TouristAutonomyState.fresh(0);
            state.setPhase(phase);
            state.setTravelTarget(11.5, 80, 20.5, poi.getId());
            holder.putComponent(TouristAutonomyState.getComponentType(), state);
        } else {
            var state = new VillagerAutonomyState();
            state.setPhase(phase);
            state.setTravelTarget(11.5, 80, 20.5, poi.getId());
            holder.putComponent(VillagerAutonomyState.getComponentType(), state);
        }
        store.addEntity(holder, AddReason.SPAWN);
    }
}
