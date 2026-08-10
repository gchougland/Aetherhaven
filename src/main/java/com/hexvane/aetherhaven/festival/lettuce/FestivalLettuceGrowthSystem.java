package com.hexvane.aetherhaven.festival.lettuce;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/**
 * Grows the lettuce by rebuilding its Model scale. That keeps mesh size and the Use / F hit volume in sync (client aim
 * uses the model box, not a separate EntityScale).
 */
public final class FestivalLettuceGrowthSystem extends EntityTickingSystem<EntityStore> {
    private static final double PULSE_SECONDS = 0.9;
    private static final double PULSE_CYCLES = 2.0;
    private static final double PULSE_STRENGTH = 0.18;
    /** Avoid rebuilding the Model every tick for tiny easing steps. */
    private static final float MODEL_APPLY_EPSILON = 0.05f;

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(FestivalLettuceComponent.getComponentType());
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
        if (lettuce == null || lettuce.isBursting() || lettuce.isSpent()) {
            return;
        }
        float target = targetScale(lettuce);
        float pulsed = (float) (target * (1.0 + pulse(lettuce.getPulseStartEpochMs())));
        float current = lettuce.getAppliedModelScale();
        float next = current + (pulsed - current) * Math.min(1.0f, dt * 6.0f);
        if (Math.abs(next - current) < 0.001f) {
            return;
        }
        lettuce.setAppliedModelScale(next);
        if (Math.abs(next - current) < MODEL_APPLY_EPSILON && Math.abs(next - pulsed) > MODEL_APPLY_EPSILON) {
            // Keep easing in the component; only push a Model update in larger steps.
            return;
        }
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        if (ref == null || !ref.isValid()) {
            return;
        }
        FestivalLettuceSpawnService.applyModelScale(commandBuffer, ref, next);
    }

    static float targetScale(@Nonnull FestivalLettuceComponent lettuce) {
        float min = lettuce.getMinScale();
        float max = lettuce.getMaxScale();
        return min + (max - min) * lettuce.fillRatio();
    }

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
