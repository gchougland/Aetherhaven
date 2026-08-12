package com.hexvane.aetherhaven.festival.firework;

import com.hexvane.aetherhaven.entity.EntityChunkUtil;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * Rises firework rockets, leaves a smoke trail, and bursts after the fuse or on a solid block hit.
 * Mutates transform/velocity from the chunk; structural removes use {@link CommandBuffer}.
 */
public final class FireworkRocketSystem extends EntityTickingSystem<EntityStore> {
    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(
            FireworkRocketComponent.getComponentType(),
            TransformComponent.getComponentType(),
            Velocity.getComponentType()
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
        FireworkRocketComponent rocket =
            archetypeChunk.getComponent(index, FireworkRocketComponent.getComponentType());
        TransformComponent transform = archetypeChunk.getComponent(index, TransformComponent.getComponentType());
        Velocity velocity = archetypeChunk.getComponent(index, Velocity.getComponentType());
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        if (rocket == null || transform == null || velocity == null || ref == null) {
            return;
        }

        rocket.addLifeSeconds(dt);
        velocity.set(0.0, FireworkIds.RISE_SPEED, 0.0);
        Vector3d pos = transform.getPosition();
        Vector3d next = new Vector3d(pos.x, pos.y + FireworkIds.RISE_SPEED * dt, pos.z);
        World world = store.getExternalData().getWorld();
        if (!EntityChunkUtil.isPositionChunkInMemory(world, next)) {
            commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
            return;
        }
        transform.setPosition(next);

        if (rocket.consumeTrailInterval(dt, FireworkIds.TRAIL_INTERVAL_SECONDS)) {
            FireworkEffects.playTrail(commandBuffer, next);
        }

        boolean hitBlock =
            rocket.getLifeSeconds() >= FireworkIds.COLLISION_GRACE_SECONDS
                && FireworkBlockUtil.hitsSolid(world, next);
        if (hitBlock || rocket.isReadyToExplode()) {
            FireworkEffects.playBurst(commandBuffer, next);
            commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
        }
    }
}
