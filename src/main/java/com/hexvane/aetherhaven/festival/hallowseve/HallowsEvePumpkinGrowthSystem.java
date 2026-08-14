package com.hexvane.aetherhaven.festival.hallowseve;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;

/** Grows the jack o lantern by rebuilding its Model scale as orbs are collected. */
public final class HallowsEvePumpkinGrowthSystem extends EntityTickingSystem<EntityStore> {
    private static final float MODEL_APPLY_EPSILON = 0.04f;

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
        if (pumpkin == null || pumpkin.isBursting()) {
            return;
        }
        UUID townId = pumpkin.getTownId();
        HallowsEveSession session = townId != null ? HallowsEveSessionIndex.get(townId) : null;
        float fill = 0f;
        if (session != null && session.getTotalOrbs() > 0) {
            fill = (float) session.getCollected() / (float) session.getTotalOrbs();
        }
        float target = pumpkin.getMinScale() + (pumpkin.getMaxScale() - pumpkin.getMinScale()) * Math.min(1f, fill);
        if (pumpkin.isIdle() && (session == null || session.getPhase() == HallowsEveSession.Phase.IDLE)) {
            target = pumpkin.getMinScale();
        }
        float current = pumpkin.getAppliedModelScale();
        float next = current + (target - current) * Math.min(1.0f, dt * 6.0f);
        if (Math.abs(next - current) < 0.001f) {
            return;
        }
        pumpkin.setAppliedModelScale(next);
        if (Math.abs(next - current) < MODEL_APPLY_EPSILON && Math.abs(next - target) > MODEL_APPLY_EPSILON) {
            return;
        }
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        if (ref == null || !ref.isValid()) {
            return;
        }
        HallowsEvePumpkinSpawnService.applyModelScale(commandBuffer, ref, next);
    }
}
