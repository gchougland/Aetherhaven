package com.hexvane.aetherhaven.festival.hallowseve;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/** Keeps the F prompt in sync with whether the jack o lantern is ready to pop. */
public final class HallowsEvePumpkinInteractSystem extends EntityTickingSystem<EntityStore> {
    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(HallowsEvePumpkinComponent.getComponentType());
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        HallowsEvePumpkinComponent pumpkin = chunk.getComponent(index, HallowsEvePumpkinComponent.getComponentType());
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        if (pumpkin == null || ref == null || !ref.isValid()) {
            return;
        }
        HallowsEvePumpkinInteractSync.sync(ref, pumpkin, commandBuffer);
    }
}
