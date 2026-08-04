package com.hexvane.aetherhaven.entity;

import com.hypixel.hytale.component.AddReason;
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
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.system.ModelSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

/**
 * Repairs non-finite body/head rotations before {@link ModelSystems.ModelSpawned} applies bounding boxes,
 * and marks the chunk dirty so repaired roll values persist on save.
 */
public final class EntityRotationRepairSystem {
    private static final Set<UUID> PENDING_CHUNK_DIRTY = ConcurrentHashMap.newKeySet();

    private EntityRotationRepairSystem() {}

    public static final class OnHolderAdd extends HolderSystem<EntityStore> {
        @Nonnull
        private final Set<Dependency<EntityStore>> dependencies =
            Set.of(new SystemDependency<>(Order.BEFORE, ModelSystems.ModelSpawned.class));
        @Nonnull
        private final Query<EntityStore> query = TransformComponent.getComponentType();

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
        public void onEntityAdd(@Nonnull Holder<EntityStore> holder, @Nonnull AddReason reason, @Nonnull Store<EntityStore> store) {
            boolean repaired = false;
            TransformComponent transform = holder.getComponent(TransformComponent.getComponentType());
            if (transform != null) {
                repaired |= EntityRotationUtil.repairInPlace(transform.getRotation());
            }
            HeadRotation head = holder.getComponent(HeadRotation.getComponentType());
            if (head != null) {
                repaired |= EntityRotationUtil.repairInPlace(head.getRotation());
            }
            if (repaired) {
                UUIDComponent uuidComponent = holder.getComponent(UUIDComponent.getComponentType());
                if (uuidComponent != null) {
                    PENDING_CHUNK_DIRTY.add(uuidComponent.getUuid());
                }
            }
        }

        @Override
        public void onEntityRemoved(
            @Nonnull Holder<EntityStore> holder,
            @Nonnull RemoveReason reason,
            @Nonnull Store<EntityStore> store
        ) {}
    }

    public static final class OnRefAdded extends RefSystem<EntityStore> {
        @Nonnull
        private final Query<EntityStore> query = TransformComponent.getComponentType();

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
            UUIDComponent uuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uuidComponent == null || !PENDING_CHUNK_DIRTY.remove(uuidComponent.getUuid())) {
                return;
            }
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform != null) {
                transform.markChunkDirty(store);
            }
        }

        @Override
        public void onEntityRemove(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull RemoveReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer
        ) {}
    }
}
