package com.hexvane.aetherhaven.festival.carnival;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Spins the carnival wheel face during a game. Dialogue only flips session phase; transform writes happen here via
 * {@link CommandBuffer}.
 */
public final class CarnivalWheelSystem extends EntityTickingSystem<EntityStore> {
    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(
            CarnivalWheelFaceComponent.getComponentType(),
            TransformComponent.getComponentType(),
            HeadRotation.getComponentType(),
            UUIDComponent.getComponentType()
        );
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        CarnivalWheelFaceComponent face =
            archetypeChunk.getComponent(index, CarnivalWheelFaceComponent.getComponentType());
        TransformComponent transform = archetypeChunk.getComponent(index, TransformComponent.getComponentType());
        HeadRotation head = archetypeChunk.getComponent(index, HeadRotation.getComponentType());
        UUIDComponent uuid = archetypeChunk.getComponent(index, UUIDComponent.getComponentType());
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        if (face == null || transform == null || head == null || uuid == null || ref == null) {
            return;
        }
        UUID townId = face.getTownId();
        if (townId == null) {
            return;
        }
        CarnivalWheelSession session = CarnivalWheelSessionIndex.get(townId);
        if (session == null || session.getPhase() != CarnivalWheelSession.Phase.SPINNING) {
            return;
        }

        session.addSpinElapsed(dt);
        float t = Math.min(1f, session.getSpinElapsed() / Math.max(0.01f, session.getSpinDuration()));
        // Ease-out cubic so the wheel slows near the end.
        float eased = 1f - (1f - t) * (1f - t) * (1f - t);
        float roll = session.getStartRoll() + (session.getTargetRoll() - session.getStartRoll()) * eased;
        face.setRoll(roll);
        Rotation3f rot = new Rotation3f(0f, face.getBaseYaw(), roll);
        commandBuffer.putComponent(
            ref,
            TransformComponent.getComponentType(),
            new TransformComponent(transform.getPosition(), rot)
        );
        commandBuffer.putComponent(ref, HeadRotation.getComponentType(), new HeadRotation(rot));
        commandBuffer.putComponent(ref, CarnivalWheelFaceComponent.getComponentType(), face);

        session.setTickSfxAccum(session.getTickSfxAccum() + dt);
        if (session.getTickSfxAccum() >= CarnivalIds.WHEEL_TICK_SFX_INTERVAL) {
            session.setTickSfxAccum(0f);
            CarnivalAudio.playWheelTick(store, transform.getPosition());
        }

        if (session.isSpinComplete()) {
            session.finishSpin(roll);
        }
    }
}
