package com.hexvane.aetherhaven.bard;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/** Spawns note particles and ends or continues performances when the song duration elapses. */
public final class BardPerformanceTickSystem extends EntityTickingSystem<EntityStore> {
    private static final long PARTICLE_INTERVAL_MS = 1200L;

    @Nonnull
    private final AetherhavenPlugin plugin;

    public BardPerformanceTickSystem(@Nonnull AetherhavenPlugin plugin) {
        this.plugin = plugin;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return BardPerformanceComponent.getComponentType();
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        if (ref == null || !ref.isValid()) {
            return;
        }
        BardPerformanceComponent perf = archetypeChunk.getComponent(index, BardPerformanceComponent.getComponentType());
        if (perf == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now >= perf.getEndAtEpochMs()) {
            BardPerformanceService.continueOrStop(store, commandBuffer, ref, plugin, perf);
            return;
        }
        BardPerformanceService.maintainPerformanceVisuals(ref, store, commandBuffer);
        if (now - perf.getLastParticleSpawnMs() < PARTICLE_INTERVAL_MS) {
            return;
        }
        TransformComponent tc = archetypeChunk.getComponent(index, TransformComponent.getComponentType());
        if (tc == null) {
            return;
        }
        BardPerformanceService.spawnPerformanceNoteParticles(tc, store);
        perf.setLastParticleSpawnMs(now);
        commandBuffer.putComponent(ref, BardPerformanceComponent.getComponentType(), perf);
    }
}
