package com.hexvane.aetherhaven.purification;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * When a player unloads, removes all purification spin-preview entities registered in {@link PurificationPowderPlayerComponent}.
 * Otherwise those entities can remain in the world while the account keeps the same {@link
 * com.hypixel.hytale.server.core.entity.UUIDComponent} on rejoin, so {@link PurificationPreviewEntity#isHiddenFromLivingEntity}
 * would still show the old preview in addition to newly spawned ones.
 */
public final class PurificationPowderPlayerRemoveSystem extends RefSystem<EntityStore> {
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
        PurificationPowderPlayerComponent st = store.getComponent(ref, PurificationPowderPlayerComponent.getComponentType());
        if (st == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        for (UUID previewId : new ArrayList<>(st.getSpawnEntityIdToPreviewEntityId().values())) {
            if (previewId == null) {
                continue;
            }
            Ref<EntityStore> pr = world.getEntityRef(previewId);
            if (pr != null && pr.isValid()) {
                commandBuffer.removeEntity(pr, RemoveReason.REMOVE);
            }
        }
    }
}
