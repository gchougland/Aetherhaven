package com.hexvane.aetherhaven.npc;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.autonomy.VillagerAutonomySystem;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.StateSupport;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * Soft stand-still via role {@code BodyMotion: Nothing} (cooperates with AI). Hard freeze via {@link Frozen} for
 * non-interactive preview/statue entities only.
 */
public final class NpcStandStill {
    private NpcStandStill() {}

    public static boolean supportsStandStillState(@Nonnull NPCEntity npc) {
        if (npc.getRole() == null) {
            return false;
        }
        return npc.getRole().getStateSupport().getStateHelper().getStateIndex(AetherhavenConstants.NPC_STATE_STAND_STILL)
            >= 0;
    }

    /**
     * Puts the NPC into a cooperative stand-still role state. Prefers {@link AetherhavenConstants#NPC_STATE_STAND_STILL},
     * then {@link AetherhavenConstants#NPC_STATE_AUTONOMY_POI} with leash at feet (Seek falls through to Nothing).
     */
    public static void hold(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull NPCEntity npc,
        @Nonnull Vector3d leashPoint,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        if (npc.getRole() == null || NpcFaceVisuals.isInInteractionDialogue(npc)) {
            return;
        }
        npc.setLeashPoint(new Vector3d(leashPoint));
        if (supportsStandStillState(npc)) {
            applyStandStillStateIfNeeded(ref, npc, commandBuffer);
        } else if (VillagerAutonomySystem.supportsAutonomyPoiRoleState(npc)) {
            VillagerAutonomySystem.applyAutonomyRoleState(ref, npc, commandBuffer);
        }
        commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
        clearResidualMotion(store, ref, commandBuffer);
        NpcAnimationPlayback.stop(ref, AnimationSlot.Movement, commandBuffer);
    }

    /** Returns from stand-still or autonomy seek back to Idle wander. Never touches {@link Frozen}. */
    public static void release(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull NPCEntity npc,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        if (npc.getRole() == null) {
            return;
        }
        StateSupport stateSupport = npc.getRole().getStateSupport();
        int standStill = stateSupport.getStateHelper().getStateIndex(AetherhavenConstants.NPC_STATE_STAND_STILL);
        if (standStill >= 0 && stateSupport.inState(standStill)) {
            stateSupport.setState(ref, "Idle", null, commandBuffer);
            NpcAnimationPlayback.clearOverlaySlots(ref, npc, commandBuffer);
            commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
            return;
        }
        VillagerAutonomySystem.clearAutonomySeekState(ref, npc, commandBuffer);
    }

    /** Clears leftover walk velocity and movement flags after a state change (not a per-tick AI fight). */
    public static void clearResidualMotion(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        Velocity velocity = store.getComponent(ref, Velocity.getComponentType());
        if (velocity != null
            && (velocity.getX() != 0d || velocity.getY() != 0d || velocity.getZ() != 0d)) {
            velocity.setZero();
            commandBuffer.putComponent(ref, Velocity.getComponentType(), velocity);
        }
        forceIdleMovementStates(store, ref, commandBuffer);
    }

    public static void freeze(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        if (store.getComponent(ref, Frozen.getComponentType()) == null) {
            commandBuffer.putComponent(ref, Frozen.getComponentType(), Frozen.get());
        }
    }

    /** Hard pin for plot-creator previews and decorative statues. Skips role/steering ticks. */
    public static void freeze(@Nonnull Ref<EntityStore> ref, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        commandBuffer.putComponent(ref, Frozen.getComponentType(), Frozen.get());
    }

    public static void thaw(@Nonnull Ref<EntityStore> ref, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        commandBuffer.tryRemoveComponent(ref, Frozen.getComponentType());
    }

    /** Same as {@link #forceIdleMovementStates(Store, Ref, CommandBuffer)} for spawn/load paths that write the store directly. */
    public static void forceIdleMovementStates(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        MovementStatesComponent ms = store.getComponent(ref, MovementStatesComponent.getComponentType());
        if (ms == null || ms.getMovementStates() == null) {
            return;
        }
        var states = ms.getMovementStates();
        if (!states.walking && !states.running && !states.sprinting && !states.jumping && !states.falling) {
            if (states.idle && states.horizontalIdle) {
                return;
            }
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
    }

    public static void forceIdleMovementStates(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        MovementStatesComponent ms = store.getComponent(ref, MovementStatesComponent.getComponentType());
        if (ms == null || ms.getMovementStates() == null) {
            return;
        }
        var states = ms.getMovementStates();
        if (!states.walking && !states.running && !states.sprinting && !states.jumping && !states.falling) {
            if (states.idle && states.horizontalIdle) {
                return;
            }
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

    private static void applyStandStillStateIfNeeded(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull NPCEntity npc,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        StateSupport stateSupport = npc.getRole().getStateSupport();
        int standStill = stateSupport.getStateHelper().getStateIndex(AetherhavenConstants.NPC_STATE_STAND_STILL);
        if (standStill >= 0 && stateSupport.inState(standStill)) {
            return;
        }
        stateSupport.setState(ref, AetherhavenConstants.NPC_STATE_STAND_STILL, null, commandBuffer);
    }
}
