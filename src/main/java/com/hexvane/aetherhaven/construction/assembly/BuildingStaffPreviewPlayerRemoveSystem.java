package com.hexvane.aetherhaven.construction.assembly;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/** Removes assembly preview markers when the owning player unloads. */
public final class BuildingStaffPreviewPlayerRemoveSystem extends RefSystem<EntityStore> {
    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }

    @Override
    public void onEntityAdded(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull AddReason reason,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {}

    @Override
    public void onEntityRemove(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull RemoveReason reason,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        BuildingStaffPreviewPlayerComponent st =
            store.getComponent(ref, BuildingStaffPreviewPlayerComponent.getComponentType());
        if (st == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        UUIDComponent ownerUuid = store.getComponent(ref, UUIDComponent.getComponentType());
        if (ownerUuid != null) {
            AssemblyMarkerSpawner.removeAllForOwner(world, ownerUuid.getUuid(), commandBuffer);
        }
        st.clearAllTracking();
    }
}
