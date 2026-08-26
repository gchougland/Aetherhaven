package com.hexvane.aetherhaven.rts;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.npc.NpcSupportUtil;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.role.support.StateSupport;
import com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Returns a guard NPC to normal patrol/dialogue after RTS command ends. */
public final class RtsGuardRelease {
    private RtsGuardRelease() {}

    public static void release(
        @Nonnull Ref<EntityStore> guardRef,
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nullable CommandBuffer<EntityStore> commandBuffer
    ) {
        if (accessor.getComponent(guardRef, GuardRtsCommandState.getComponentType()) != null) {
            if (commandBuffer != null) {
                commandBuffer.removeComponent(guardRef, GuardRtsCommandState.getComponentType());
            } else {
                accessor.removeComponent(guardRef, GuardRtsCommandState.getComponentType());
            }
        }
        NPCEntity npc = accessor.getComponent(guardRef, NPCEntity.getComponentType());
        if (npc == null || npc.getRole() == null) {
            return;
        }
        Role role = npc.getRole();
        RtsGuardCombatSupport.clearCombatTarget(npc, accessor);
        MarkedEntitySupport marked = NpcSupportUtil.markedEntitySupport(guardRef, accessor);
        for (int i = 0; i < marked.getMarkedEntitySlotCount(); i++) {
            marked.clearMarkedEntity(i);
        }
        TransformComponent tc = accessor.getComponent(guardRef, TransformComponent.getComponentType());
        if (tc != null) {
            Vector3d pos = tc.getPosition();
            npc.setLeashPoint(new Vector3d(pos.x, pos.y, pos.z));
        }
        String state = NpcSupportUtil.stateName(guardRef.getStore(), guardRef);
        if (needsReleaseReset(state)) {
            if (commandBuffer != null) {
                NpcSupportUtil.setState(guardRef, "Idle", null, commandBuffer);
                commandBuffer.putComponent(guardRef, NPCEntity.getComponentType(), npc);
            } else {
                StateSupport stateSupport = NpcSupportUtil.stateSupport(guardRef.getStore(), guardRef);
                if (stateSupport != null) {
                    stateSupport.setState(guardRef, "Idle", null, accessor);
                }
                accessor.putComponent(guardRef, NPCEntity.getComponentType(), npc);
            }
        } else if (commandBuffer != null) {
            commandBuffer.putComponent(guardRef, NPCEntity.getComponentType(), npc);
        } else {
            accessor.putComponent(guardRef, NPCEntity.getComponentType(), npc);
        }
    }

    private static boolean needsReleaseReset(@Nonnull String state) {
        return state.contains("Combat")
            || state.contains(AetherhavenConstants.NPC_STATE_GUARD_RTS_COMMAND)
            || state.contains("RtsCommand")
            || state.contains("Interaction");
    }
}
