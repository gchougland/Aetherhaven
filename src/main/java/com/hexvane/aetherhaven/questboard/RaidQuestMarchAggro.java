package com.hexvane.aetherhaven.questboard;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.rts.RtsGuardCombatSupport;
import com.hexvane.aetherhaven.npc.NpcSupportUtil;
import com.hypixel.hytale.server.npc.role.support.StateSupport;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Engages nearby players for raid mobs whose roles lack built-in march aggro (e.g. LOTR intelligent NPCs in ReturnHome). */
final class RaidQuestMarchAggro {
    private static final double PLAYER_DETECT_RANGE = 20.0;

    private RaidQuestMarchAggro() {}

    static void tryEngageNearbyPlayers(
        @Nonnull Ref<EntityStore> mobRef,
        @Nonnull NPCEntity npc,
        @Nonnull Vector3d mobPos,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        if (RaidQuestMarchUtil.isEngagedInCombat(npc)) {
            return;
        }
        if (hasBuiltInMarchAggro(npc)) {
            return;
        }
        Ref<EntityStore> playerRef = nearestPlayerRef(store, mobPos, PLAYER_DETECT_RANGE);
        if (playerRef == null) {
            return;
        }
        engagePlayer(mobRef, npc, playerRef, commandBuffer);
    }

    private static boolean hasBuiltInMarchAggro(@Nonnull NPCEntity npc) {
        if (RaidQuestMarchUtil.isFlyingNpc(npc)) {
            return false;
        }
        Role role = npc.getRole();
        if (role == null) {
            return false;
        }
        Ref<EntityStore> mobRef = npc.getReference();
        if (mobRef == null) {
            return false;
        }
        StateSupport stateSupport = NpcSupportUtil.stateSupport(mobRef.getStore(), mobRef);
        return stateSupport != null
            && stateSupport.getStateHelper().getStateIndex(AetherhavenConstants.NPC_STATE_RAID_MARCH) >= 0;
    }

    @Nullable
    private static Ref<EntityStore> nearestPlayerRef(
        @Nonnull Store<EntityStore> store,
        @Nonnull Vector3d center,
        double radius
    ) {
        SpatialResource<Ref<EntityStore>, EntityStore> spatial =
            store.getResource(EntityModule.get().getPlayerSpatialResourceType());
        List<Ref<EntityStore>> hits = SpatialResource.getThreadLocalReferenceList();
        spatial.getSpatialStructure().collect(center, radius, hits);
        Ref<EntityStore> best = null;
        double bestSq = Double.MAX_VALUE;
        double radiusSq = radius * radius;
        for (Ref<EntityStore> ref : hits) {
            if (!ref.isValid() || store.getComponent(ref, Player.getComponentType()) == null) {
                continue;
            }
            TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
            if (tc == null) {
                continue;
            }
            Vector3d p = tc.getPosition();
            double dx = p.x - center.x;
            double dy = p.y - center.y;
            double dz = p.z - center.z;
            double sq = dx * dx + dy * dy + dz * dz;
            if (sq <= radiusSq && sq < bestSq) {
                bestSq = sq;
                best = ref;
            }
        }
        return best;
    }

    private static void engagePlayer(
        @Nonnull Ref<EntityStore> mobRef,
        @Nonnull NPCEntity npc,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        RtsGuardCombatSupport.lockCombatTarget(npc, playerRef, commandBuffer);
        Role role = npc.getRole();
        if (role == null) {
            return;
        }
        StateSupport stateSupport = NpcSupportUtil.stateSupport(mobRef.getStore(), mobRef);
        if (stateSupport == null) {
            return;
        }
        var helper = stateSupport.getStateHelper();
        if (helper.getStateIndex("Combat") >= 0) {
            NpcSupportUtil.setState(mobRef, "Combat", null, commandBuffer);
        } else if (helper.getStateIndex("Attack") >= 0) {
            NpcSupportUtil.setState(mobRef, "Attack", null, commandBuffer);
        } else if (helper.getStateIndex("Alerted") >= 0) {
            NpcSupportUtil.setState(mobRef, "Alerted", null, commandBuffer);
        }
        commandBuffer.putComponent(mobRef, NPCEntity.getComponentType(), npc);
    }
}
