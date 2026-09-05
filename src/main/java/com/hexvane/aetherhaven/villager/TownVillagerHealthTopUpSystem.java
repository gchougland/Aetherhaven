package com.hexvane.aetherhaven.villager;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.HolderSystem;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatsSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * After armor/stat Recalculate, fill town villager Health when a top-up was requested (chunk load or equipment apply).
 */
public final class TownVillagerHealthTopUpSystem {
    private TownVillagerHealthTopUpSystem() {}

    /** Queues a top-up when a town villager is spawned or loaded into the world. */
    public static final class OnAdd extends HolderSystem<EntityStore> {
        @Nonnull
        @Override
        public Query<EntityStore> getQuery() {
            return Query.and(
                TownVillagerBinding.getComponentType(),
                NPCEntity.getComponentType(),
                EntityStatMap.getComponentType(),
                UUIDComponent.getComponentType()
            );
        }

        @Override
        public void onEntityAdd(
            @Nonnull Holder<EntityStore> holder,
            @Nonnull AddReason reason,
            @Nonnull Store<EntityStore> store
        ) {
            if (reason != AddReason.SPAWN && reason != AddReason.LOAD) {
                return;
            }
            UUIDComponent uc = holder.getComponent(UUIDComponent.getComponentType());
            if (uc != null) {
                TownVillagerHealthTopUp.request(uc.getUuid());
            }
        }

        @Override
        public void onEntityRemoved(
            @Nonnull Holder<EntityStore> holder,
            @Nonnull RemoveReason reason,
            @Nonnull Store<EntityStore> store
        ) {}
    }

    /** Applies queued top-ups after vanilla max-stat Recalculate (armor Health modifiers included). */
    public static final class AfterRecalculate extends EntityTickingSystem<EntityStore>
        implements EntityStatsSystems.StatModifyingSystem {
        @Nonnull
        private final Set<Dependency<EntityStore>> dependencies =
            Set.of(new SystemDependency<>(Order.AFTER, EntityStatsSystems.Recalculate.class));

        @Nonnull
        @Override
        public Set<Dependency<EntityStore>> getDependencies() {
            return dependencies;
        }

        @Nonnull
        @Override
        public Query<EntityStore> getQuery() {
            return Query.and(
                TownVillagerBinding.getComponentType(),
                EntityStatMap.getComponentType(),
                UUIDComponent.getComponentType()
            );
        }

        @Override
        public boolean isParallel(int archetypeChunkSize, int taskCount) {
            return false;
        }

        @Override
        public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer
        ) {
            UUIDComponent uc = archetypeChunk.getComponent(index, UUIDComponent.getComponentType());
            if (uc == null || !TownVillagerHealthTopUp.consume(uc.getUuid())) {
                return;
            }
            Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
            if (ref == null || !ref.isValid()) {
                return;
            }
            TownVillagerHealthTopUp.maximizeHealth(ref, store, commandBuffer);
        }
    }
}
