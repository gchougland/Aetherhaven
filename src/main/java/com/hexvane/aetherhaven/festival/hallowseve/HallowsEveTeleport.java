package com.hexvane.aetherhaven.festival.hallowseve;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/** Player teleport and freeze for the maze countdown. */
public final class HallowsEveTeleport {
    private HallowsEveTeleport() {}

    public static void applyStartPad(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        int index,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull HallowsEveSession session,
        boolean freeze
    ) {
        float yawRad = (float) Math.toRadians(session.getStartYawDegrees());
        Rotation3f rot = new Rotation3f(0f, yawRad, 0f);
        Vector3d dest = new Vector3d(session.getStartX(), session.getStartY(), session.getStartZ());
        commandBuffer.putComponent(ref, Teleport.getComponentType(), Teleport.createForPlayer(dest, rot));
        Velocity vel = chunk.getComponent(index, Velocity.getComponentType());
        if (vel != null) {
            vel.setZero();
            commandBuffer.putComponent(ref, Velocity.getComponentType(), vel);
        }
        if (freeze) {
            commandBuffer.putComponent(ref, Frozen.getComponentType(), Frozen.get());
        }
    }

    public static void thaw(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        commandBuffer.tryRemoveComponent(ref, Frozen.getComponentType());
    }
}
