package com.hexvane.aetherhaven.festival.carnival;

import com.hexvane.aetherhaven.entity.TransformComponentUtil;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Applies spin roll to the carnival wheel face prop. Session timing lives in
 * {@link CarnivalWheelDirectorSystem}; this only updates visuals via {@link CommandBuffer}.
 */
public final class CarnivalWheelSystem extends EntityTickingSystem<EntityStore> {
    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(
            CarnivalWheelFaceComponent.getComponentType(),
            TransformComponent.getComponentType(),
            HeadRotation.getComponentType()
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
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        if (face == null || transform == null || head == null || ref == null) {
            return;
        }
        UUID townId = face.getTownId();
        if (townId == null) {
            return;
        }
        CarnivalWheelSession session = CarnivalWheelSessionIndex.get(townId);
        if (session == null) {
            return;
        }
        CarnivalWheelSession.Phase phase = session.getPhase();
        if (phase != CarnivalWheelSession.Phase.SPINNING && phase != CarnivalWheelSession.Phase.RESULTS) {
            return;
        }

        float roll = session.currentRoll();
        face.setRoll(roll);
        Rotation3f rot = new Rotation3f(0f, face.getBaseYaw(), roll);
        // Preserve chunk linkage; a bare TransformComponent put orphans the prop and stops client updates.
        TransformComponentUtil.replacePreservingChunk(ref, store, commandBuffer, transform.getPosition(), rot);
        head.teleportRotation(rot);
        commandBuffer.putComponent(ref, HeadRotation.getComponentType(), head);
        commandBuffer.putComponent(ref, CarnivalWheelFaceComponent.getComponentType(), face);
    }
}
