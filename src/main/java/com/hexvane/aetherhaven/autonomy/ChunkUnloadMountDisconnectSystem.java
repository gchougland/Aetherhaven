package com.hexvane.aetherhaven.autonomy;

import com.hypixel.hytale.builtin.mounts.BlockMountComponent;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.NonTicking;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.EntitySection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * When a section stops ticking, vanilla {@link EntitySection.EntitySectionLoadingSystem} unloads live
 * entities while the chunk store is still processing. If those entities still have
 * {@link com.hypixel.hytale.builtin.mounts.MountedComponent},
 * {@code MountSystems.RemoveMounted} calls {@code ChunkStore.removeComponent} and throws.
 */
public final class ChunkUnloadMountDisconnectSystem extends RefChangeSystem<ChunkStore, NonTicking<ChunkStore>> {
    @Nonnull
    private final Archetype<ChunkStore> archetype =
        Archetype.of(ChunkSection.getComponentType(), EntitySection.getComponentType());

    @Nonnull
    private final Set<Dependency<ChunkStore>> dependencies =
        Set.of(new SystemDependency<>(Order.BEFORE, EntitySection.EntitySectionLoadingSystem.class));

    @Nonnull
    @Override
    public Set<Dependency<ChunkStore>> getDependencies() {
        return dependencies;
    }

    @Nonnull
    @Override
    public Query<ChunkStore> getQuery() {
        return archetype;
    }

    @Nonnull
    @Override
    public ComponentType<ChunkStore, NonTicking<ChunkStore>> componentType() {
        return ChunkStore.REGISTRY.getNonTickingComponentType();
    }

    @Override
    public void onComponentAdded(
        @Nonnull Ref<ChunkStore> ref,
        @Nonnull NonTicking<ChunkStore> component,
        @Nonnull Store<ChunkStore> store,
        @Nonnull CommandBuffer<ChunkStore> commandBuffer
    ) {
        EntitySection entitySection = store.getComponent(ref, EntitySection.getComponentType());
        if (entitySection == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        Store<EntityStore> entityStore = world.getEntityStore().getStore();
        for (Ref<EntityStore> entityRef : entitySection.getEntityReferences()) {
            if (!entityRef.isValid()) {
                continue;
            }
            Ref<ChunkStore> deadSeatRef = BlockMountRelease.disconnectForUnload(entityRef, entityStore);
            if (deadSeatRef != null) {
                commandBuffer.tryRemoveComponent(deadSeatRef, BlockMountComponent.getComponentType());
            }
        }
    }

    @Override
    public void onComponentSet(
        @Nonnull Ref<ChunkStore> ref,
        NonTicking<ChunkStore> oldComponent,
        @Nonnull NonTicking<ChunkStore> newComponent,
        @Nonnull Store<ChunkStore> store,
        @Nonnull CommandBuffer<ChunkStore> commandBuffer
    ) {}

    @Override
    public void onComponentRemoved(
        @Nonnull Ref<ChunkStore> ref,
        @Nonnull NonTicking<ChunkStore> component,
        @Nonnull Store<ChunkStore> store,
        @Nonnull CommandBuffer<ChunkStore> commandBuffer
    ) {}
}
