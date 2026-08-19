package com.hexvane.aetherhaven.bard;

import com.hypixel.hytale.builtin.audio.components.ForcedMusicTracker;
import com.hypixel.hytale.builtin.audio.systems.ForcedMusicSystems;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Applies per-player bard {@link com.hypixel.hytale.protocol.packets.world.UpdateForcedMusic}
 * after vanilla {@link ForcedMusicSystems.Tick} so only nearby players hear the performance.
 * Clears forced music only for players this system marked as listening; does not wipe
 * trigger-volume or command forced music.
 * <p>The hearing sphere follows the bard. Listeners use a larger leave radius so standing at the
 * edge, or the bard taking a few steps, does not resend the track and restart it.
 */
public final class BardMusicProximitySystem extends EntityTickingSystem<EntityStore> {
    @Nonnull
    private final Query<EntityStore> query =
        Archetype.of(
            Player.getComponentType(),
            PlayerRef.getComponentType(),
            TransformComponent.getComponentType(),
            UUIDComponent.getComponentType(),
            ForcedMusicTracker.getComponentType()
        );

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(new SystemDependency<>(Order.AFTER, ForcedMusicSystems.Tick.class));
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        BardActivePerformancesResource performances =
            store.getResource(BardActivePerformancesResource.getResourceType());
        long worldTick = store.getExternalData().getWorld().getTick();
        performances.rebuildForTick(store, worldTick);

        Ref<EntityStore> playerEntityRef = archetypeChunk.getReferenceTo(index);
        TransformComponent transform = archetypeChunk.getComponent(index, TransformComponent.getComponentType());
        PlayerRef playerRef = archetypeChunk.getComponent(index, PlayerRef.getComponentType());
        UUIDComponent uuidComponent = archetypeChunk.getComponent(index, UUIDComponent.getComponentType());
        ForcedMusicTracker tracker = archetypeChunk.getComponent(index, ForcedMusicTracker.getComponentType());
        if (playerEntityRef == null
            || !playerEntityRef.isValid()
            || transform == null
            || playerRef == null
            || uuidComponent == null
            || tracker == null) {
            return;
        }

        UUID playerId = uuidComponent.getUuid();
        BardMusicProximityState proximityState = store.getResource(BardMusicProximityState.getResourceType());
        boolean alreadyListening = proximityState.isListening(playerId);

        var pos = transform.getPosition();
        int desiredContainer =
            performances.nearestMusic(pos.x, pos.y, pos.z, alreadyListening).musicContainerIndex();
        int have = tracker.getCurrentContainerIndex();
        BardForcedMusicOwnership.Decision decision =
            BardForcedMusicOwnership.decide(desiredContainer, have, alreadyListening);

        if (decision.updateTracker()) {
            BardEnvironmentMusic.setForcedMusic(
                playerEntityRef,
                commandBuffer,
                store,
                playerRef,
                tracker,
                decision.containerIndex(),
                !alreadyListening && decision.markListening()
            );
        }
        if (decision.markListening()) {
            proximityState.setActive(playerId, decision.containerIndex());
        } else if (decision.clearListening()) {
            proximityState.clear(playerId);
        }
    }
}
