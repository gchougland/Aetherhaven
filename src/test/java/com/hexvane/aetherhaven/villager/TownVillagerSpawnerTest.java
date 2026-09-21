package com.hexvane.aetherhaven.villager;

import static org.junit.jupiter.api.Assertions.*;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
class TownVillagerSpawnerTest {
    private static final ComponentRegistry<EntityStore> REGISTRY = new ComponentRegistry<>();
    private static final ComponentType<EntityStore, NPCEntity> NPC = REGISTRY.registerComponent(NPCEntity.class, NPCEntity::new);

    @BeforeAll
    static void register() {
        var proxy = new ComponentRegistryProxy<EntityStore>(new ArrayList<>(), REGISTRY);
        TownVillagerBinding.register(proxy);
        VillagerNeeds.register(proxy);
        AetherhavenVillagerHandle.register(proxy);
        AetherhavenNpcSpawnOrigin.register(proxy);
    }

    @Test
    void townIdentitySurvivesTheEngineUnloadingDuringSpawn() {
        var parked = new ArrayList<Holder<EntityStore>>();
        var unloadOnSpawn = new RefSystem<EntityStore>() {
            public Query<EntityStore> getQuery() { return NPC; }
            public void onEntityAdded(Ref<EntityStore> ref, AddReason reason, Store<EntityStore> store,
                                      CommandBuffer<EntityStore> buffer) {
                if (reason != AddReason.SPAWN) return;
                // UpdateLocationSystems does this when the destination section is not ticking.
                var holder = REGISTRY.newHolder();
                buffer.removeEntity(ref, holder, RemoveReason.UNLOAD);
                parked.add(holder);
            }
            public void onEntityRemove(Ref<EntityStore> ref, RemoveReason reason, Store<EntityStore> store,
                                       CommandBuffer<EntityStore> buffer) {}
        };
        REGISTRY.registerSystem(unloadOnSpawn);
        var store = REGISTRY.addStore(null, EmptyResourceStorage.get());
        try {
            UUID townId = UUID.randomUUID();
            UUID innId = UUID.randomUUID();
            var holder = REGISTRY.newHolder();
            var npc = new NPCEntity();
            npc.setRoleName("Aetherhaven_Blacksmith");
            holder.addComponent(NPC, npc);
            TownVillagerSpawner.prepare(holder, new TownVillagerBinding(townId, "visitor_blacksmith", innId),
                new AetherhavenNpcSpawnOrigin("INN_MORNING_FILL", "blacksmith", "default", 1, 2, 3, 4, 0));

            assertNull(store.addEntity(holder, AddReason.SPAWN));
            assertEquals(1, parked.size(), "A null spawn result need not mean the NPC was destroyed");
            Ref<EntityStore> loaded = store.addEntity(parked.removeFirst(), AddReason.LOAD);
            assertNotNull(loaded);
            assertTrue(loaded.isValid());
            var binding = store.getComponent(loaded, TownVillagerBinding.getComponentType());
            assertEquals(townId, binding.getTownId());
            assertEquals("visitor_blacksmith", binding.getKind());
            assertEquals("Aetherhaven_Blacksmith", store.getComponent(loaded, NPC).getRoleName());
            assertNotNull(store.getComponent(loaded, VillagerNeeds.getComponentType()));
            assertEquals(TownVillagerSpawner.handle(binding),
                store.getComponent(loaded, AetherhavenVillagerHandle.getComponentType()).getHandle());
            assertNotNull(store.getComponent(loaded, AetherhavenNpcSpawnOrigin.getComponentType()));
        } finally {
            store.shutdown();
        }
    }
}
