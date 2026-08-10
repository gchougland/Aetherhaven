package com.hexvane.aetherhaven.plotcreator;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.components.SpawnBeaconReference;
import com.hypixel.hytale.server.npc.components.SpawnMarkerReference;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Keeps plot-creator preview NPCs out of ambient spawn/despawn rules and stops walk/run cycles while Frozen.
 * Frozen entities do not refresh {@link MovementStatesComponent}; without forcing idle they keep a walk cycle
 * and look like they are running in place.
 */
public final class PlotCreatorSpotPreviewSanitize {
    private PlotCreatorSpotPreviewSanitize() {}

    public static void applyOnSpawn(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        store.putComponent(ref, Frozen.getComponentType(), Frozen.get());
        store.tryRemoveComponent(ref, SpawnBeaconReference.getComponentType());
        store.tryRemoveComponent(ref, SpawnMarkerReference.getComponentType());
        Velocity velocity = store.getComponent(ref, Velocity.getComponentType());
        if (velocity != null) {
            velocity.setZero();
        }
        forceIdleMovementStates(store, ref);
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (npc == null) {
            return;
        }
        npc.setSpawnConfiguration(Integer.MIN_VALUE);
        npc.setEnvironment(Integer.MIN_VALUE);
        npc.setDespawning(false);
        npc.setPlayingDespawnAnim(false);
        npc.playAnimation(ref, AnimationSlot.Movement, null, store);
        AnimationUtils.stopAnimation(ref, AnimationSlot.Movement, store);
    }

    public static void applyEachTick(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        commandBuffer.putComponent(ref, Frozen.getComponentType(), Frozen.get());
        commandBuffer.tryRemoveComponent(ref, SpawnBeaconReference.getComponentType());
        commandBuffer.tryRemoveComponent(ref, SpawnMarkerReference.getComponentType());
        Velocity velocity = store.getComponent(ref, Velocity.getComponentType());
        if (velocity != null) {
            velocity.setZero();
        }
        forceIdleMovementStates(store, ref);
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (npc == null) {
            return;
        }
        if (npc.getSpawnConfiguration() != Integer.MIN_VALUE) {
            npc.setSpawnConfiguration(Integer.MIN_VALUE);
        }
        if (npc.getEnvironment() != Integer.MIN_VALUE) {
            npc.setEnvironment(Integer.MIN_VALUE);
        }
        if (npc.isDespawning() || npc.isPlayingDespawnAnim()) {
            npc.setDespawning(false);
            npc.setPlayingDespawnAnim(false);
        }
    }

    public static void clearMovementAnim(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nullable CommandBuffer<EntityStore> commandBuffer
    ) {
        forceIdleMovementStates(store, ref);
        if (commandBuffer != null) {
            commandBuffer.run(s -> {
                if (!ref.isValid()) {
                    return;
                }
                forceIdleMovementStates(s, ref);
                NPCEntity live = s.getComponent(ref, NPCEntity.getComponentType());
                if (live != null) {
                    live.playAnimation(ref, AnimationSlot.Movement, null, s);
                }
                AnimationUtils.stopAnimation(ref, AnimationSlot.Movement, s);
            });
            return;
        }
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (npc != null) {
            npc.playAnimation(ref, AnimationSlot.Movement, null, store);
        }
        AnimationUtils.stopAnimation(ref, AnimationSlot.Movement, store);
    }

    private static void forceIdleMovementStates(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        MovementStatesComponent ms = store.getComponent(ref, MovementStatesComponent.getComponentType());
        if (ms == null || ms.getMovementStates() == null) {
            return;
        }
        var states = ms.getMovementStates();
        states.idle = true;
        states.horizontalIdle = true;
        states.walking = false;
        states.running = false;
        states.sprinting = false;
        states.jumping = false;
        states.falling = false;
        states.fallingFar = false;
        states.onGround = true;
    }
}
