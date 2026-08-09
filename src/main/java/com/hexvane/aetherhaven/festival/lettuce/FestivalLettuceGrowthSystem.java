package com.hexvane.aetherhaven.festival.lettuce;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/** Grows the lettuce toward its full size and gives it a wobble every time it drinks. */
public final class FestivalLettuceGrowthSystem extends EntityTickingSystem<EntityStore> {
    /** How long the squash and stretch after an absorb lasts. */
    private static final double PULSE_SECONDS = 0.9;
    private static final double PULSE_CYCLES = 2.0;
    private static final double PULSE_STRENGTH = 0.18;

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(FestivalLettuceComponent.getComponentType(), EntityScaleComponent.getComponentType());
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        FestivalLettuceComponent lettuce = chunk.getComponent(index, FestivalLettuceComponent.getComponentType());
        EntityScaleComponent scale = chunk.getComponent(index, EntityScaleComponent.getComponentType());
        if (lettuce == null || scale == null || lettuce.isBursting() || lettuce.isSpent()) {
            return;
        }
        float target = targetScale(lettuce);
        float pulsed = (float) (target * (1.0 + pulse(lettuce.getPulseStartEpochMs())));
        // Ease toward the target so growth reads as a swell rather than a snap.
        float next = scale.getScale() + (pulsed - scale.getScale()) * Math.min(1.0f, dt * 6.0f);
        if (Math.abs(next - scale.getScale()) > 0.001f) {
            scale.setScale(next);
        }
    }

    static float targetScale(@Nonnull FestivalLettuceComponent lettuce) {
        float min = lettuce.getMinScale();
        float max = lettuce.getMaxScale();
        return min + (max - min) * lettuce.fillRatio();
    }

    /** Decaying sine that restarts on every absorb; zero once the pulse has run out. */
    static double pulse(long pulseStartEpochMs) {
        if (pulseStartEpochMs <= 0L) {
            return 0.0;
        }
        double elapsed = (System.currentTimeMillis() - pulseStartEpochMs) / 1000.0;
        if (elapsed < 0.0 || elapsed >= PULSE_SECONDS) {
            return 0.0;
        }
        double decay = 1.0 - elapsed / PULSE_SECONDS;
        return Math.sin(elapsed / PULSE_SECONDS * Math.PI * 2.0 * PULSE_CYCLES) * PULSE_STRENGTH * decay;
    }
}
