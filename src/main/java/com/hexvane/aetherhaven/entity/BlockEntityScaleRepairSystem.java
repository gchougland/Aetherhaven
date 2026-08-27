package com.hexvane.aetherhaven.entity;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.HolderSystem;
import com.hypixel.hytale.server.core.entity.entities.BlockEntity;
import com.hypixel.hytale.server.core.modules.entity.BlockEntitySystems;
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * After Hytale's Update 6 {@link BlockEntitySystems.PositionMigrationSystem}, repair prefab
 * block entities that still have old-convention scale (binary cache skipped the engine flag).
 */
public final class BlockEntityScaleRepairSystem extends HolderSystem<EntityStore> {
    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies =
        Set.of(new SystemDependency<>(Order.AFTER, BlockEntitySystems.PositionMigrationSystem.class));

    @Nonnull
    private final Query<EntityStore> query =
        Query.and(
            BlockEntity.getComponentType(),
            TransformComponent.getComponentType(),
            EntityScaleComponent.getComponentType()
        );

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
        BlockEntityScaleMigration.migrateLoadedIfOversized(holder);
    }

    @Override
    public void onEntityRemoved(
        @Nonnull Holder<EntityStore> holder,
        @Nonnull RemoveReason reason,
        @Nonnull Store<EntityStore> store
    ) {}
}
