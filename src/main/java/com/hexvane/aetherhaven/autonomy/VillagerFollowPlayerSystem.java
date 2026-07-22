package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.builder.BuilderConstructionAssistState;
import com.hexvane.aetherhaven.npc.NpcFaceVisuals;
import com.hexvane.aetherhaven.townsfolk.TownsfolkAssignmentKinds;
import com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * Keeps a following villager near the commanding player via leash Seek, skipping schedule/POI autonomy while active.
 */
public final class VillagerFollowPlayerSystem extends EntityTickingSystem<EntityStore> {
    /** Horizontal stand-off from the player (blocks). */
    public static final double FOLLOW_DISTANCE = 3.5;
    private static final double FOLLOW_STOP_SQ = FOLLOW_DISTANCE * FOLLOW_DISTANCE;
    private static final double FOLLOW_RESUME_SQ = (FOLLOW_DISTANCE + 1.5) * (FOLLOW_DISTANCE + 1.5);

    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = RootDependency.firstSet();
    @SuppressWarnings("unused")
    private final AetherhavenPlugin plugin;

    public VillagerFollowPlayerSystem(@Nonnull AetherhavenPlugin plugin) {
        this.plugin = plugin;
    }

    public static boolean shouldSkipAutonomy(@Nullable VillagerFollowPlayerState follow) {
        return follow != null && follow.isActive();
    }

    public static boolean isEligibleCitizen(@Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef) {
        if (npcRef == null || !npcRef.isValid()) {
            return false;
        }
        TownVillagerBinding binding = store.getComponent(npcRef, TownVillagerBinding.getComponentType());
        if (binding == null) {
            return false;
        }
        String kind = binding.getKind();
        if (TownVillagerBinding.isVisitorKind(kind) || TownVillagerBinding.isRescueKind(kind)) {
            return false;
        }
        if (TownVillagerBinding.KIND_GUARD.equals(kind)) {
            return false;
        }
        TownsfolkCharacterBinding townsfolk = store.getComponent(npcRef, TownsfolkCharacterBinding.getComponentType());
        if (townsfolk != null && TownsfolkAssignmentKinds.isGuildHallAdventurer(townsfolk.getAssignmentKind())) {
            return false;
        }
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        return npc != null && VillagerAutonomySystem.supportsAutonomyPoiRoleState(npc);
    }

    public static boolean isFollowingPlayer(
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef,
        @Nonnull UUID playerUuid
    ) {
        if (npcRef == null || !npcRef.isValid()) {
            return false;
        }
        VillagerFollowPlayerState follow = store.getComponent(npcRef, VillagerFollowPlayerState.getComponentType());
        return follow != null && follow.isFollowing(playerUuid);
    }

    public static void startFollow(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID playerUuid
    ) {
        if (!isEligibleCitizen(store, npcRef)) {
            return;
        }
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        if (npc == null) {
            return;
        }
        long now = VillagerAutonomySystem.resolveAutonomyNowMs(store);
        VillagerAutonomySystem.resetAutonomyForRescue(npcRef, store, now);

        BuilderConstructionAssistState assist = store.getComponent(npcRef, BuilderConstructionAssistState.getComponentType());
        if (assist != null && assist.isActive()) {
            BuilderConstructionAssistState cleared = (BuilderConstructionAssistState) assist.clone();
            cleared.clearTarget();
            store.putComponent(npcRef, BuilderConstructionAssistState.getComponentType(), cleared);
        }

        VillagerFollowPlayerState follow = store.getComponent(npcRef, VillagerFollowPlayerState.getComponentType());
        if (follow == null) {
            follow = new VillagerFollowPlayerState();
        } else {
            follow = (VillagerFollowPlayerState) follow.clone();
        }
        follow.startFollowing(playerUuid);
        store.putComponent(npcRef, VillagerFollowPlayerState.getComponentType(), follow);
        clearFollowRecoveryTracking(store, npcRef);
    }

    public static void stopFollow(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        boolean resumeSchedule
    ) {
        VillagerFollowPlayerState follow = store.getComponent(npcRef, VillagerFollowPlayerState.getComponentType());
        if (follow != null && follow.isActive()) {
            VillagerFollowPlayerState cleared = (VillagerFollowPlayerState) follow.clone();
            cleared.clear();
            store.putComponent(npcRef, VillagerFollowPlayerState.getComponentType(), cleared);
            clearFollowRecoveryTracking(store, npcRef);
        }
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        if (npc != null) {
            long now = VillagerAutonomySystem.resolveAutonomyNowMs(store);
            if (resumeSchedule) {
                VillagerAutonomySystem.promptWorkplaceTravel(npcRef, store, now);
            } else {
                VillagerAutonomySystem.resetAutonomyForRescue(npcRef, store, now);
            }
        }
    }

    private static void stopFollowFromTick(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc,
        @Nonnull VillagerFollowPlayerState follow,
        boolean resumeSchedule
    ) {
        VillagerFollowPlayerState cleared = (VillagerFollowPlayerState) follow.clone();
        cleared.clear();
        commandBuffer.putComponent(ref, VillagerFollowPlayerState.getComponentType(), cleared);
        clearFollowRecoveryTracking(store, ref);
        long now = VillagerAutonomySystem.resolveAutonomyNowMs(store);
        VillagerAutonomySystem.clearAutonomySeekState(ref, npc, commandBuffer);
        if (resumeSchedule) {
            VillagerAutonomySystem.promptWorkplaceTravel(ref, store, commandBuffer, now);
        }
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(
            VillagerFollowPlayerState.getComponentType(),
            TownVillagerBinding.getComponentType(),
            NPCEntity.getComponentType()
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
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        VillagerFollowPlayerState follow =
            archetypeChunk.getComponent(index, VillagerFollowPlayerState.getComponentType());
        NPCEntity npc = archetypeChunk.getComponent(index, NPCEntity.getComponentType());
        if (follow == null || npc == null || !follow.isActive()) {
            return;
        }
        if (NpcFaceVisuals.isInInteractionDialogue(npc)) {
            return;
        }

        UUID playerUuid = follow.getPlayerUuid();
        if (playerUuid == null) {
            stopFollowFromTick(ref, store, commandBuffer, npc, follow, true);
            return;
        }

        Ref<EntityStore> playerEntityRef = store.getExternalData().getRefFromUUID(playerUuid);
        if (playerEntityRef == null || !playerEntityRef.isValid()) {
            stopFollowFromTick(ref, store, commandBuffer, npc, follow, true);
            return;
        }
        if (store.getComponent(playerEntityRef, Player.getComponentType()) == null) {
            stopFollowFromTick(ref, store, commandBuffer, npc, follow, true);
            return;
        }

        TransformComponent playerTc = store.getComponent(playerEntityRef, TransformComponent.getComponentType());
        TransformComponent npcTc = store.getComponent(ref, TransformComponent.getComponentType());
        if (playerTc == null || npcTc == null) {
            stopFollowFromTick(ref, store, commandBuffer, npc, follow, true);
            return;
        }

        Vector3d playerPos = playerTc.getPosition();
        Vector3d npcPos = npcTc.getPosition();
        double dx = npcPos.x - playerPos.x;
        double dz = npcPos.z - playerPos.z;
        double horizSq = dx * dx + dz * dz;

        if (horizSq <= FOLLOW_STOP_SQ) {
            VillagerAutonomySystem.clearAutonomySeekState(ref, npc, commandBuffer);
            return;
        }

        if (FollowPlayerTeleportRecovery.tryRecover(
            ref,
            commandBuffer,
            store,
            npc,
            playerPos,
            npcPos,
            horizSq,
            FOLLOW_STOP_SQ,
            FOLLOW_DISTANCE
        )) {
            VillagerAutonomySystem.applyAutonomyRoleState(ref, npc, commandBuffer);
            return;
        }

        Vector3d leash = standOffLeashPoint(playerPos, npcPos);
        npc.setLeashPoint(leash);
        commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
        if (horizSq >= FOLLOW_RESUME_SQ || !isInAutonomySeek(npc)) {
            VillagerAutonomySystem.applyAutonomyRoleState(ref, npc, commandBuffer);
        }
    }

    private static boolean isInAutonomySeek(@Nonnull NPCEntity npc) {
        if (npc.getRole() == null) {
            return false;
        }
        String state = npc.getRole().getStateSupport().getStateName();
        return state != null && state.startsWith(com.hexvane.aetherhaven.AetherhavenConstants.NPC_STATE_AUTONOMY_POI);
    }

    /**
     * Point a few blocks from the player toward the NPC so Seek / Leash (1.5) settles at stand-off distance,
     * not on top of the player.
     */
    @Nonnull
    static Vector3d standOffLeashPoint(@Nonnull Vector3d playerPos, @Nonnull Vector3d npcPos) {
        double dx = npcPos.x - playerPos.x;
        double dz = npcPos.z - playerPos.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz < 0.05) {
            return new Vector3d(playerPos.x + FOLLOW_DISTANCE, playerPos.y, playerPos.z);
        }
        double scale = FOLLOW_DISTANCE / horiz;
        return new Vector3d(playerPos.x + dx * scale, playerPos.y, playerPos.z + dz * scale);
    }

    private static void clearFollowRecoveryTracking(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> npcRef
    ) {
        UUIDComponent uc = store.getComponent(npcRef, UUIDComponent.getComponentType());
        if (uc != null) {
            FollowPlayerTeleportRecovery.clearTracking(uc.getUuid());
        }
    }
}
