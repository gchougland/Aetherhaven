package com.hexvane.aetherhaven.rts;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.builtin.npccombatactionevaluator.memory.TargetMemory;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.components.messaging.BeaconSupport;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport;
import com.hypixel.hytale.server.npc.role.support.StateSupport;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Bridges Java RTS engage logic to guard role combat (LockedTarget + hostile memory). */
public final class RtsGuardCombatSupport {
    /** Default marked-entity slot used by guard {@code Target} sensors in combat instructions. */
    public static final String LOCKED_TARGET_SLOT = "LockedTarget";

    private RtsGuardCombatSupport() {}

    /** Clears combat targeting and resumes RTS pathing toward a new hold point. */
    public static void resumeTravel(
        @Nonnull Ref<EntityStore> guardRef,
        @Nonnull NPCEntity npc,
        @Nonnull ComponentAccessor<EntityStore> accessor,
        double holdX,
        double holdY,
        double holdZ
    ) {
        clearCombatTarget(npc, accessor);
        npc.setLeashPoint(new Vector3d(holdX, holdY, holdZ));
        Role role = npc.getRole();
        if (role != null && role.getStateSupport().getStateName().contains("Combat")) {
            role.getStateSupport().setState(guardRef, AetherhavenConstants.NPC_STATE_GUARD_RTS_COMMAND, null, accessor);
        }
        accessor.putComponent(guardRef, NPCEntity.getComponentType(), npc);
    }

    /** Locks a threat and enters combat for patrol, follow, and idle guards. */
    public static void engageAutonomousThreat(
        @Nonnull Ref<EntityStore> guardRef,
        @Nonnull NPCEntity npc,
        @Nonnull Ref<EntityStore> threatRef,
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nullable CommandBuffer<EntityStore> commandBuffer
    ) {
        lockCombatTarget(npc, threatRef, accessor);
        promptCounterAttack(guardRef, threatRef, accessor, commandBuffer);
        Role role = npc.getRole();
        if (role == null) {
            return;
        }
        ComponentAccessor<EntityStore> stateAccessor = commandBuffer != null ? commandBuffer : accessor;
        if (!role.getStateSupport().getStateName().contains("Combat")) {
            role.getStateSupport().setState(guardRef, "Combat", null, stateAccessor);
        }
        if (commandBuffer != null) {
            commandBuffer.putComponent(guardRef, NPCEntity.getComponentType(), npc);
        } else {
            accessor.putComponent(guardRef, NPCEntity.getComponentType(), npc);
        }
    }

    public static void lockCombatTarget(
        @Nonnull NPCEntity npc,
        @Nonnull Ref<EntityStore> targetRef,
        @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        Role role = npc.getRole();
        if (role == null) {
            return;
        }
        role.getMarkedEntitySupport().setMarkedEntity(LOCKED_TARGET_SLOT, targetRef);
        rememberHostile(npc.getReference(), targetRef, accessor);
        Ref<EntityStore> guardRef = npc.getReference();
        Store<EntityStore> store = guardRef != null ? guardRef.getStore() : null;
        if (store != null && !RtsHostileQuery.isAggressiveNpc(targetRef, store)) {
            try {
                role.getWorldSupport().overrideAttitude(targetRef, Attitude.HOSTILE, 300.0);
            } catch (NullPointerException ignored) {
                // Role build did not allocate attitude override memory.
            }
        }
    }

    public static void clearCombatTarget(@Nonnull NPCEntity npc, @Nonnull ComponentAccessor<EntityStore> accessor) {
        Role role = npc.getRole();
        if (role == null) {
            return;
        }
        MarkedEntitySupport marked = role.getMarkedEntitySupport();
        marked.setMarkedEntity(LOCKED_TARGET_SLOT, null);
        marked.setMarkedEntity("CombatTargets", null);
        Ref<EntityStore> guardRef = npc.getReference();
        if (guardRef != null && guardRef.isValid()) {
            clearHostileMemory(guardRef, accessor);
        }
    }

    public static void rememberHostile(
        @Nullable Ref<EntityStore> guardRef,
        @Nonnull Ref<EntityStore> targetRef,
        @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        if (guardRef == null || !guardRef.isValid()) {
            return;
        }
        TargetMemory memory = accessor.getComponent(guardRef, TargetMemory.getComponentType());
        if (memory == null) {
            return;
        }
        Int2FloatOpenHashMap hostiles = memory.getKnownHostiles();
        if (hostiles.put(targetRef.getIndex(), memory.getRememberFor()) <= 0.0F) {
            memory.getKnownHostilesList().add(targetRef);
        }
        memory.setClosestHostile(targetRef);
    }

    public static void clearHostileMemory(
        @Nonnull Ref<EntityStore> guardRef,
        @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        TargetMemory memory = accessor.getComponent(guardRef, TargetMemory.getComponentType());
        if (memory == null) {
            return;
        }
        memory.getKnownHostiles().clear();
        memory.getKnownHostilesList().clear();
        memory.setClosestHostile(null);
    }

    /** Marks a hostile creature so it will fight the engaging guard. Skips passive prey without a combat state. */
    public static void promptCounterAttack(
        @Nonnull Ref<EntityStore> guardRef,
        @Nonnull Ref<EntityStore> hostileRef,
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nullable CommandBuffer<EntityStore> commandBuffer
    ) {
        if (guardRef.equals(hostileRef)) {
            return;
        }
        Store<EntityStore> store = guardRef.getStore();
        if (!RtsHostileQuery.isGuardThreatTarget(guardRef, hostileRef, store)) {
            return;
        }
        NPCEntity hostile = accessor.getComponent(hostileRef, NPCEntity.getComponentType());
        if (hostile == null || hostile.getRole() == null) {
            return;
        }
        Role role = hostile.getRole();
        Ref<EntityStore> lockedTarget = role.getMarkedEntitySupport().getMarkedEntityRef(LOCKED_TARGET_SLOT);
        if (guardRef.equals(lockedTarget)) {
            return;
        }
        String state = role.getStateSupport().getStateName();
        if (isEngagedInExternalCombat(state)) {
            return;
        }
        rememberHostile(hostileRef, guardRef, accessor);
        role.getMarkedEntitySupport().setMarkedEntity(LOCKED_TARGET_SLOT, guardRef);
        ComponentAccessor<EntityStore> stateAccessor = commandBuffer != null ? commandBuffer : accessor;
        if (tryEnterExternalCombat(hostileRef, guardRef, role, stateAccessor)) {
            if (commandBuffer != null) {
                commandBuffer.putComponent(hostileRef, NPCEntity.getComponentType(), hostile);
            } else {
                accessor.putComponent(hostileRef, NPCEntity.getComponentType(), hostile);
            }
        }
    }

    /** True when the hostile is already in a role-defined combat/attack state. */
    static boolean isEngagedInExternalCombat(@Nonnull String stateName) {
        return stateName.contains("Combat")
            || stateName.startsWith("Attack.")
            || stateName.startsWith("Chase.");
    }

    /**
     * Transitions a hostile into its role-specific combat flow. Many vanilla mobs (e.g. Trork companion wolves)
     * use {@code Attack} or beacon messages instead of a top-level {@code Combat} state.
     */
    static boolean tryEnterExternalCombat(
        @Nonnull Ref<EntityStore> hostileRef,
        @Nonnull Ref<EntityStore> guardRef,
        @Nonnull Role role,
        @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        StateSupport stateSupport = role.getStateSupport();
        if (stateSupport.getStateHelper().getStateIndex("Combat") >= 0) {
            stateSupport.setState(hostileRef, "Combat", null, accessor);
            return true;
        }
        if (stateSupport.getStateHelper().getStateIndex("Attack") >= 0) {
            stateSupport.setState(hostileRef, "Attack", null, accessor);
            return true;
        }
        BeaconSupport beacon = accessor.getComponent(hostileRef, BeaconSupport.getComponentType());
        if (beacon != null) {
            beacon.postMessage("CompanionAttack", guardRef, 1.0);
            return true;
        }
        return false;
    }
}
