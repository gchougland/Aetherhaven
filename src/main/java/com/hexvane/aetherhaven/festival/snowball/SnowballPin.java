package com.hexvane.aetherhaven.festival.snowball;

import com.hexvane.aetherhaven.autonomy.BlockMountRelease;
import com.hexvane.aetherhaven.autonomy.PoiAutonomyVisuals;
import com.hexvane.aetherhaven.autonomy.VillagerAutonomySystem;
import com.hexvane.aetherhaven.npc.NpcAnimationPlayback;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * Holds a villager still for a snowball fight: Frozen plus Idle, no seek, no leftover walk velocity.
 */
final class SnowballPin {
    private SnowballPin() {}

    static void hold(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc,
        @Nonnull SnowballSession.StartPad pad
    ) {
        if (store.getComponent(ref, Frozen.getComponentType()) == null) {
            commandBuffer.putComponent(ref, Frozen.getComponentType(), Frozen.get());
        }
        Velocity velocity = store.getComponent(ref, Velocity.getComponentType());
        if (velocity != null
            && (velocity.getX() != 0d || velocity.getY() != 0d || velocity.getZ() != 0d)) {
            velocity.setZero();
            commandBuffer.putComponent(ref, Velocity.getComponentType(), velocity);
        }
        forceStillMovement(store, commandBuffer, ref);
        if (npc.getRole() == null) {
            return;
        }
        String state = npc.getRole().getStateSupport().getStateName();
        if (state == null || state.isBlank() || "Idle".equals(state) || state.contains("Interaction")) {
            return;
        }
        VillagerAutonomySystem.clearAutonomySeekState(ref, npc, commandBuffer);
        npc.getRole().getStateSupport().setState(ref, "Idle", null, commandBuffer);
        npc.setLeashPoint(new Vector3d(pad.x(), pad.y(), pad.z()));
        commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
    }

    static void start(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull NPCEntity npc,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull SnowballSession.StartPad pad
    ) {
        // Leave beds/chairs before freezing so sleep pose does not stick through the fight.
        PoiAutonomyVisuals.forceAbortUseVisuals(ref, store, commandBuffer);
        BlockMountRelease.release(ref, store, commandBuffer);
        commandBuffer.putComponent(ref, Frozen.getComponentType(), Frozen.get());
        VillagerAutonomySystem.clearAutonomySeekState(ref, npc, commandBuffer);
        NpcAnimationPlayback.stop(ref, AnimationSlot.Movement, commandBuffer);
        if (npc.getRole() != null) {
            String state = npc.getRole().getStateSupport().getStateName();
            if (state == null || !state.contains("Interaction")) {
                npc.getRole().getStateSupport().setState(ref, "Idle", null, commandBuffer);
            }
        }
        npc.setLeashPoint(new Vector3d(pad.x(), pad.y(), pad.z()));
        commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
    }

    static void unpin(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        commandBuffer.tryRemoveComponent(ref, Frozen.getComponentType());
    }

    private static void forceStillMovement(
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Ref<EntityStore> ref
    ) {
        MovementStatesComponent ms = store.getComponent(ref, MovementStatesComponent.getComponentType());
        if (ms == null || ms.getMovementStates() == null) {
            return;
        }
        var states = ms.getMovementStates();
        if (!states.walking && !states.running && !states.sprinting && !states.jumping && !states.falling) {
            return;
        }
        states.walking = false;
        states.running = false;
        states.sprinting = false;
        states.jumping = false;
        states.falling = false;
        states.fallingFar = false;
        states.idle = !states.crouching && !states.forcedCrouching;
        states.horizontalIdle = true;
        states.onGround = true;
        commandBuffer.putComponent(ref, MovementStatesComponent.getComponentType(), ms);
    }
}
