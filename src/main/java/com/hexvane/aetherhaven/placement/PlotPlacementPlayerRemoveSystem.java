package com.hexvane.aetherhaven.placement;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;

/** Clears plot placement previews for spectators when the placing player unloads. */
public final class PlotPlacementPlayerRemoveSystem extends RefSystem<EntityStore> {
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
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            return;
        }
        UUID placerUuid = uc.getUuid();
        PlotPlacementSession session = PlotPlacementSessions.get(placerUuid);
        if (session == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        PlotPlacementPreviewSync.hideSpectators(world, placerUuid, session);
        PlotPlacementClientPrefabPreview.clearSessionCache(session);
        PlotPlacementSessions.remove(placerUuid);
    }
}
