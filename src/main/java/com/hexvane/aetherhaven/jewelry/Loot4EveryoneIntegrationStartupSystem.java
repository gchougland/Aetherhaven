package com.hexvane.aetherhaven.jewelry;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/** Retries Loot4Everyone type resolution after that mod enables. */
public final class Loot4EveryoneIntegrationStartupSystem extends EntityTickingSystem<EntityStore> {
    public Loot4EveryoneIntegrationStartupSystem() {}

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        if (Loot4EveryoneIntegration.isHooked()) {
            return;
        }
        if (!Loot4EveryoneIntegration.isAvailable()) {
            Loot4EveryoneIntegration.tryInitialize();
        }
        if (Loot4EveryoneIntegration.isAvailable()) {
            Loot4EveryoneIntegration.registerIfAvailable();
        }
    }
}
