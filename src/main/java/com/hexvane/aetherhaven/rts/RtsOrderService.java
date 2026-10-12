package com.hexvane.aetherhaven.rts;

import com.hexvane.aetherhaven.npc.NpcSupportUtil;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.role.support.StateSupport;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

public final class RtsOrderService {
    private RtsOrderService() {}

    /**
     * Sends the session's selected guards to a point.
     *
     * @return how many guards took the order (a selected guard that is not in the world takes none)
     */
    public static int issueMoveOrder(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull RtsCommandPlayerComponent session,
        double groundX,
        double groundY,
        double groundZ
    ) {
        Store<EntityStore> store = playerRef.getStore();
        List<UUID> selected = new ArrayList<>(session.getSelectedGuardUuids());
        if (selected.isEmpty()) {
            return 0;
        }
        UUID commander = commanderUuid(playerRef, accessor);
        // An order given outside the command post belongs to the flag, and outlives no session.
        boolean fieldOrder = !session.isActive();
        RtsFocusTargetVisuals.clear(commander);
        List<Vector3d> offsets = RtsFormationMath.lineOffsets(selected.size());
        List<UUID> ordered = new ArrayList<>(selected.size());
        for (int i = 0; i < selected.size(); i++) {
            Ref<EntityStore> guardRef = RtsGuardDirectory.findByUuid(store, selected.get(i));
            if (guardRef == null) {
                continue;
            }
            Vector3d off = i < offsets.size() ? offsets.get(i) : new Vector3d();
            GuardRtsCommandState cmd = accessor.getComponent(guardRef, GuardRtsCommandState.getComponentType());
            if (cmd == null) {
                cmd = new GuardRtsCommandState();
            }
            applyMoveOrder(
                cmd,
                session.getOrderMode(),
                session.getStanceMode(),
                groundX + off.x,
                groundY + off.y,
                groundZ + off.z,
                commander,
                fieldOrder
            );
            accessor.putComponent(guardRef, GuardRtsCommandState.getComponentType(), cmd);
            ordered.add(selected.get(i));
            NPCEntity guardNpc = accessor.getComponent(guardRef, NPCEntity.getComponentType());
            if (guardNpc != null) {
                RtsGuardCombatSupport.resumeTravel(
                    guardRef,
                    guardNpc,
                    accessor,
                    cmd.getHoldX(),
                    cmd.getHoldY(),
                    cmd.getHoldZ()
                );
            }
        }
        if (ordered.isEmpty()) {
            return 0;
        }
        long worldTick = store.getExternalData().getWorld().getTick();
        RtsMoveOrderVisuals.spawn(
            store,
            playerRef,
            groundX,
            groundY,
            groundZ,
            ordered,
            worldTick,
            fieldOrder ? worldTick + RtsMoveOrderVisuals.FIELD_MARKER_TICKS : Long.MAX_VALUE
        );
        RtsCommandFeedback.playMoveOrder(playerRef, accessor, groundX, groundY, groundZ);
        return ordered.size();
    }

    /** Writes a move order onto a guard's command state. No world needed, so the rules can be tested alone. */
    static void applyMoveOrder(
        @Nonnull GuardRtsCommandState cmd,
        @Nonnull RtsOrderMode orderMode,
        @Nonnull RtsCombatStance stance,
        double holdX,
        double holdY,
        double holdZ,
        @Nonnull UUID commander,
        boolean fieldOrder
    ) {
        cmd.setOrderMode(orderMode);
        cmd.setCombatStance(stance);
        cmd.setHold(holdX, holdY, holdZ);
        cmd.setPhase(RtsCommandPhase.TRAVELING);
        cmd.setFocusFire(false);
        cmd.setTargetEntityUuid(null);
        cmd.setCommanderPlayerUuid(commander);
        cmd.setFieldOrder(fieldOrder);
    }

    /** Writes a stop order: hold where the guard stands and defend that spot. */
    static void applyStopOrder(
        @Nonnull GuardRtsCommandState cmd,
        @Nonnull RtsOrderMode orderMode,
        double standX,
        double standY,
        double standZ,
        @Nonnull UUID commander,
        boolean fieldOrder
    ) {
        cmd.setHold(standX, standY, standZ);
        cmd.setPhase(RtsCommandPhase.HOLDING);
        cmd.setOrderMode(orderMode);
        cmd.setCombatStance(RtsCombatStance.AGGRESSIVE);
        cmd.setFocusFire(false);
        cmd.setTargetEntityUuid(null);
        cmd.setCommanderPlayerUuid(commander);
        cmd.setFieldOrder(fieldOrder);
    }

    public static boolean issueFocusAttack(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull RtsCommandPlayerComponent session,
        @Nonnull Ref<EntityStore> targetRef
    ) {
        Store<EntityStore> store = playerRef.getStore();
        List<UUID> selected = new ArrayList<>(session.getSelectedGuardUuids());
        if (selected.isEmpty()) {
            return false;
        }
        if (!RtsHostileQuery.isGuardAttackableTarget(targetRef, store)) {
            return false;
        }
        UUID targetUuid = RtsHostileQuery.entityUuid(targetRef, store);
        if (targetUuid == null) {
            return false;
        }
        var targetTc = accessor.getComponent(
            targetRef,
            com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType()
        );
        if (targetTc == null) {
            return false;
        }
        Vector3d targetPos = targetTc.getPosition();
        UUID commander = commanderUuid(playerRef, accessor);
        List<Vector3d> offsets = RtsFormationMath.lineOffsets(selected.size());
        int issued = 0;
        for (int i = 0; i < selected.size(); i++) {
            Ref<EntityStore> guardRef = RtsGuardDirectory.findByUuid(store, selected.get(i));
            if (guardRef == null) {
                continue;
            }
            NPCEntity guardNpc = accessor.getComponent(guardRef, com.hypixel.hytale.server.npc.entities.NPCEntity.getComponentType());
            if (guardNpc == null) {
                continue;
            }
            Vector3d off = i < offsets.size() ? offsets.get(i) : new Vector3d();
            GuardRtsCommandState cmd = accessor.getComponent(guardRef, GuardRtsCommandState.getComponentType());
            if (cmd == null) {
                cmd = new GuardRtsCommandState();
            }
            var guardTc = accessor.getComponent(
                guardRef,
                com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType()
            );
            cmd.setOrderMode(RtsOrderMode.ATTACK_MOVE);
            cmd.setCombatStance(RtsCombatStance.AGGRESSIVE);
            cmd.setFocusFire(true);
            cmd.setTargetEntityUuid(targetUuid);
            cmd.setHold(targetPos.x + off.x, targetPos.y, targetPos.z + off.z);
            cmd.setCommanderPlayerUuid(commander);
            cmd.setFieldOrder(!session.isActive());
            double engageRange = RtsGuardCombatRanges.attackEngageRange(guardNpc);
            if (guardTc != null && withinHorizontalRange(guardTc.getPosition(), targetPos, engageRange * 1.25)) {
                cmd.setPhase(RtsCommandPhase.ENGAGING);
                RtsGuardCombatSupport.lockCombatTarget(guardNpc, targetRef, accessor);
                StateSupport stateSupport = NpcSupportUtil.stateSupport(store, guardRef);
                if (stateSupport != null) {
                    stateSupport.setState(guardRef, "Combat", null, accessor);
                }
            } else {
                cmd.setPhase(RtsCommandPhase.TRAVELING);
                RtsGuardCombatSupport.lockCombatTarget(guardNpc, targetRef, accessor);
                guardNpc.setLeashPoint(new Vector3d(cmd.getHoldX(), cmd.getHoldY(), cmd.getHoldZ()));
                StateSupport rtsStateSupport = NpcSupportUtil.stateSupport(store, guardRef);
                if (rtsStateSupport != null) {
                    rtsStateSupport.setState(
                        guardRef,
                        AetherhavenConstants.NPC_STATE_GUARD_RTS_COMMAND,
                        null,
                        accessor
                    );
                }
            }
            accessor.putComponent(guardRef, GuardRtsCommandState.getComponentType(), cmd);
            accessor.putComponent(guardRef, NPCEntity.getComponentType(), guardNpc);
            issued++;
        }
        if (issued == 0) {
            return false;
        }
        RtsFocusTargetVisuals.register(commander, targetUuid);
        RtsCommandFeedback.playFocusAttack(playerRef, targetRef, accessor);
        return true;
    }

    /**
     * Stops the session's selected guards where they stand.
     *
     * @return how many guards were stopped
     */
    public static int stopSelected(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull RtsCommandPlayerComponent session
    ) {
        Store<EntityStore> store = playerRef.getStore();
        UUID commander = commanderUuid(playerRef, accessor);
        boolean fieldOrder = !session.isActive();
        RtsFocusTargetVisuals.clear(commander);
        int stopped = 0;
        for (UUID guardId : session.getSelectedGuardUuids()) {
            Ref<EntityStore> guardRef = RtsGuardDirectory.findByUuid(store, guardId);
            if (guardRef == null) {
                continue;
            }
            var tc = accessor.getComponent(guardRef, com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType());
            if (tc == null) {
                continue;
            }
            var p = tc.getPosition();
            GuardRtsCommandState cmd = accessor.getComponent(guardRef, GuardRtsCommandState.getComponentType());
            if (cmd == null) {
                cmd = new GuardRtsCommandState();
            }
            applyStopOrder(cmd, session.getOrderMode(), p.x, p.y, p.z, commander, fieldOrder);
            accessor.putComponent(guardRef, GuardRtsCommandState.getComponentType(), cmd);
            stopped++;
        }
        return stopped;
    }

    public static void applyStanceToSelected(
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull RtsCommandPlayerComponent session
    ) {
        Store<EntityStore> store = playerRef.getStore();
        for (UUID guardId : session.getSelectedGuardUuids()) {
            Ref<EntityStore> guardRef = RtsGuardDirectory.findByUuid(store, guardId);
            if (guardRef == null) {
                continue;
            }
            GuardRtsCommandState cmd = accessor.getComponent(guardRef, GuardRtsCommandState.getComponentType());
            if (cmd != null) {
                cmd.setCombatStance(session.getStanceMode());
                accessor.putComponent(guardRef, GuardRtsCommandState.getComponentType(), cmd);
            }
        }
    }

    public static void freeSelected(
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull RtsCommandPlayerComponent session
    ) {
        Store<EntityStore> store = playerRef.getStore();
        for (UUID guardId : new ArrayList<>(session.getSelectedGuardUuids())) {
            Ref<EntityStore> guardRef = RtsGuardDirectory.findByUuid(store, guardId);
            if (guardRef != null) {
                RtsCommandService.freeGuard(guardRef, accessor);
            }
        }
    }

    @Nonnull
    private static UUID commanderUuid(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        UUIDComponent uc = accessor.getComponent(playerRef, UUIDComponent.getComponentType());
        return uc != null ? uc.getUuid() : new UUID(0L, 0L);
    }

    private static boolean withinHorizontalRange(
        @Nonnull Vector3d from,
        @Nonnull Vector3d to,
        double range
    ) {
        double dx = from.x - to.x;
        double dz = from.z - to.z;
        return dx * dx + dz * dz <= range * range;
    }
}
