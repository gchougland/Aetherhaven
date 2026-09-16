package com.hexvane.aetherhaven.prop;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import java.util.function.Function;
import javax.annotation.Nonnull;

/** Finishes pickup for companions that were unloaded when their prop was packaged. */
public final class PropRemovedEntityCleanupSystem extends RefSystem<EntityStore> {
    private final Function<Store<EntityStore>, PropRegistry> registryForStore;

    public PropRemovedEntityCleanupSystem(Function<Store<EntityStore>, PropRegistry> registryForStore) {
        this.registryForStore = registryForStore;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return AetherhavenPlacedInstance.getComponentType();
    }

    @Override
    public void onEntityAdded(@Nonnull Ref<EntityStore> ref, @Nonnull AddReason reason,
        @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        var tag = commandBuffer.getComponent(ref, AetherhavenPlacedInstance.getComponentType());
        if (tag == null || tag.getKind() != AetherhavenPlacedInstance.Kind.PROP) return;
        final UUID instanceId;
        try {
            instanceId = UUID.fromString(tag.getInstanceId());
        } catch (IllegalArgumentException ignored) {
            return;
        }
        // Only an explicit removal record authorizes deletion. A missing registry entry could
        // instead mean a new placement is still being registered, or a registry failed to load.
        if (registryForStore.apply(store).wasRemoved(instanceId)) {
            commandBuffer.tryRemoveEntity(ref, RemoveReason.REMOVE);
        }
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<EntityStore> ref, @Nonnull RemoveReason reason,
        @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {}
}
