package com.hexvane.aetherhaven.production;

import static org.junit.jupiter.api.Assertions.*;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** P11: one owned barn animal must never be shown, parked or saved more than once. */
@Tag("town")
class BarnDisplayDuplicationTest {
    private static final ComponentRegistry<EntityStore> REGISTRY = new ComponentRegistry<>();
    private static final ComponentType<EntityStore, NPCEntity> NPC = REGISTRY.registerComponent(NPCEntity.class, NPCEntity::new);
    private static final List<Holder<EntityStore>> PARKED = new ArrayList<>();
    private static boolean sectionInactive;

    @BeforeAll
    static void register() {
        BarnAnimalBinding.register(new ComponentRegistryProxy<>(new ArrayList<>(), REGISTRY));
        REGISTRY.registerSystem(new BarnDisplayLoadCleanupSystem(NPC));
        // UpdateLocationSystems does this when the destination section is not ticking: the new entity is
        // unloaded into the section's storage and Store.addEntity reports that nothing was spawned.
        REGISTRY.registerSystem(new RefSystem<EntityStore>() {
            public Query<EntityStore> getQuery() { return NPC; }
            public void onEntityAdded(Ref<EntityStore> ref, AddReason reason, Store<EntityStore> store,
                                      CommandBuffer<EntityStore> buffer) {
                if (reason != AddReason.SPAWN || !sectionInactive) return;
                var holder = REGISTRY.newHolder();
                buffer.removeEntity(ref, holder, RemoveReason.UNLOAD);
                PARKED.add(holder);
            }
            public void onEntityRemove(Ref<EntityStore> ref, RemoveReason reason, Store<EntityStore> store,
                                       CommandBuffer<EntityStore> buffer) {}
        });
    }

    @AfterAll
    static void shutdown() { REGISTRY.shutdown(); }

    private static Holder<EntityStore> animal(String role) {
        var holder = REGISTRY.newHolder();
        var npc = new NPCEntity();
        npc.setRoleName(role);
        holder.addComponent(NPC, npc);
        return holder;
    }

    private static boolean saved(Holder<EntityStore> holder) {
        return !holder.getArchetype().contains(REGISTRY.getNonSerializedComponentType());
    }

    @Test
    void anInactivePenSectionNoLongerLeavesAnUntrackedSavedAnimalBehindEachMinute() {
        var store = REGISTRY.addStore(null, EmptyResourceStorage.get());
        try {
            UUID town = UUID.randomUUID(), plot = UUID.randomUUID(), sheep = UUID.randomUUID();
            sectionInactive = true;
            PARKED.clear();

            // What the barn did before: spawn the plain animal, then tag it once the engine handed it back.
            // The engine parks it instead, so nothing comes back to tag and the next minute tries again.
            for (int minute = 0; minute < 3; minute++) assertNull(store.addEntity(animal("Aetherhaven_Barn_Sheep"), AddReason.SPAWN));
            assertEquals(3, PARKED.size(), "Each failed attempt left one more animal in the chunk");
            for (var copy : PARKED) {
                assertNull(copy.getComponent(BarnAnimalBinding.getComponentType()), "Nothing ties the copy to an owned animal");
                assertTrue(saved(copy), "The copy is an ordinary animal that the chunk saves");
            }
            PARKED.clear();

            // Now: the display names its animal and is unsaved before it is added.
            var display = animal("Aetherhaven_Barn_Sheep");
            NativeHerdService.prepareDisplay(display, new BarnAnimalBinding(town, plot, sheep), REGISTRY);
            assertNull(store.addEntity(display, AddReason.SPAWN));
            assertEquals(1, PARKED.size());
            assertEquals(sheep, PARKED.getFirst().getComponent(BarnAnimalBinding.getComponentType()).animal);
            assertFalse(saved(PARKED.getFirst()), "A parked display is never written to the chunk");
        } finally {
            sectionInactive = false;
            PARKED.clear();
            store.shutdown();
        }
    }

    @Test
    void copiesLeftByOlderBuildsAreDiscardedAsTheirChunkLoads() {
        var store = REGISTRY.addStore(null, EmptyResourceStorage.get());
        try {
            // Three saved copies of one sheep, a wild sheep and an apprentice ranch animal come back from disk.
            for (int i = 0; i < 3; i++) assertNull(store.addEntity(animal("Aetherhaven_Barn_Sheep"), AddReason.LOAD));
            var wild = store.addEntity(animal("Sheep"), AddReason.LOAD);
            var ranch = store.addEntity(animal("Aetherhaven_Ranch_Sheep"), AddReason.LOAD);
            assertNotNull(wild);
            assertNotNull(ranch);
            assertEquals(2, store.getEntityCount(), "Only the barn's own display role is cleared");
        } finally {
            store.shutdown();
        }
    }

    @Test
    void theMinuteSweepRemovesUnboundDisplaysAndKeepsTheRealOne() {
        var store = REGISTRY.addStore(null, EmptyResourceStorage.get());
        try {
            UUID town = UUID.randomUUID(), plot = UUID.randomUUID(), cow = UUID.randomUUID();
            var real = animal("Aetherhaven_Barn_Cow");
            NativeHerdService.prepareDisplay(real, new BarnAnimalBinding(town, plot, cow), REGISTRY);
            var realRef = store.addEntity(real, AddReason.SPAWN);
            var strayA = store.addEntity(animal("Aetherhaven_Barn_Cow"), AddReason.SPAWN);
            var strayB = store.addEntity(animal("Aetherhaven_Barn_Chicken"), AddReason.SPAWN);
            var wild = store.addEntity(animal("Cow"), AddReason.SPAWN);

            assertEquals(2, NativeHerdService.removeStrays(store, NPC));
            assertTrue(realRef.isValid());
            assertTrue(wild.isValid());
            assertFalse(strayA.isValid());
            assertFalse(strayB.isValid());
            assertEquals(0, NativeHerdService.removeStrays(store, NPC), "A second sweep finds nothing");
        } finally {
            store.shutdown();
        }
    }

    @Test
    void onlyOneDisplayIsKeptForEachPennedAnimal() {
        UUID barn = UUID.randomUUID(), otherBarn = UUID.randomUUID();
        UUID sheep = UUID.randomUUID(), cow = UUID.randomUUID(), resting = UUID.randomUUID();
        var shown = List.of(
            new HerdDisplayPlan.Shown<>("sheep-1", barn, sheep),
            new HerdDisplayPlan.Shown<>("sheep-2", barn, sheep),
            new HerdDisplayPlan.Shown<>("sheep-3", barn, sheep),
            new HerdDisplayPlan.Shown<>("cow-1", barn, cow),
            new HerdDisplayPlan.Shown<>("resting-1", barn, resting),
            new HerdDisplayPlan.Shown<>("unnamed", barn, (UUID) null),
            new HerdDisplayPlan.Shown<>("neighbour-sheep", otherBarn, sheep));
        var plan = HerdDisplayPlan.reconcile(barn, shown, id -> id.equals(sheep) ? 0 : id.equals(cow) ? 1 : -1);

        assertEquals("sheep-1", plan.keep().get(sheep));
        assertEquals("cow-1", plan.keep().get(cow));
        assertEquals(2, plan.keep().size());
        assertEquals(List.of("sheep-2", "sheep-3", "resting-1", "unnamed"), plan.remove());
    }

    @Test
    void onlyBarnDisplayRolesWithoutABindingCountAsStrays() {
        for (var species : BarnAnimalCatalog.all()) {
            assertTrue(HerdDisplayPlan.stray(species.role(), false), species.id());
            assertFalse(HerdDisplayPlan.stray(species.role(), true), species.id());
        }
        for (String role : new String[] {"Cow", "Tamed_Cow", "Aetherhaven_Ranch_Cow", "Aetherhaven_Barn_Dragon", "Aetherhaven_Rancher", null})
            assertFalse(HerdDisplayPlan.stray(role, false), String.valueOf(role));
    }
}
