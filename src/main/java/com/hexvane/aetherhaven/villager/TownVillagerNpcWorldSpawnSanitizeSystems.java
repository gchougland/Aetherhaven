package com.hexvane.aetherhaven.villager;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.systems.NPCPreTickSystem;
import java.util.Set;
import javax.annotation.Nonnull;

/** Keeps town villagers out of the base game's NPC despawn checks and clears stray spawn linkage on load or tick. */
public final class TownVillagerNpcWorldSpawnSanitizeSystems {
    private TownVillagerNpcWorldSpawnSanitizeSystems() {}

    public static final class OnAdd extends RefSystem<EntityStore> {
        @Nonnull
        private final Set<Dependency<EntityStore>> dependencies =
            Set.of(new SystemDependency<>(Order.BEFORE, NPCPreTickSystem.class));
        @Nonnull
        private final Query<EntityStore> query = Query.and(TownVillagerBinding.getComponentType(), NPCEntity.getComponentType());

        @Nonnull
        @Override
        public Set<Dependency<EntityStore>> getDependencies() {
            return dependencies;
        }

        @Nonnull
        @Override
        public Query<EntityStore> getQuery() {
            return query;
        }

        @Override
        public void onEntityAdded(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull AddReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer
        ) {
            NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
            if (npc == null) {
                return;
            }
            if (!TownVillagerNpcWorldSpawnSanitizeUtil.needsSanitize(ref, npc, store)) {
                return;
            }
            TownVillagerNpcWorldSpawnSanitizeUtil.sanitize(ref, npc, store, commandBuffer);
        }

        @Override
        public void onEntityRemove(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull RemoveReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer
        ) {}
    }

    public static final class EachTick extends EntityTickingSystem<EntityStore> {
        @Nonnull
        private final Set<Dependency<EntityStore>> dependencies =
            Set.of(new SystemDependency<>(Order.BEFORE, NPCPreTickSystem.class));
        @Nonnull
        private final Query<EntityStore> query = Query.and(TownVillagerBinding.getComponentType(), NPCEntity.getComponentType());

        @Nonnull
        @Override
        public Set<Dependency<EntityStore>> getDependencies() {
            return dependencies;
        }

        @Nonnull
        @Override
        public Query<EntityStore> getQuery() {
            return query;
        }

        @Override
        public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer
        ) {
            Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
            NPCEntity npc = archetypeChunk.getComponent(index, NPCEntity.getComponentType());
            if (npc == null) {
                return;
            }
            if (!TownVillagerNpcWorldSpawnSanitizeUtil.needsSanitize(ref, npc, store)) {
                return;
            }
            TownVillagerNpcWorldSpawnSanitizeUtil.sanitize(ref, npc, store, commandBuffer);
        }
    }
}
