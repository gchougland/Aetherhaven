package com.hexvane.aetherhaven.patrol;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.FollowPlayerTeleportRecovery;
import com.hexvane.aetherhaven.npc.NpcFaceVisuals;
import com.hexvane.aetherhaven.rts.GuardRtsCommandState;
import com.hexvane.aetherhaven.rts.RtsGuardDirectory;
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

/** Keeps a following hired guard near the commanding player via Patrol seek and leash. */
public final class GuardFollowPlayerSystem extends EntityTickingSystem<EntityStore> {
    public static final double FOLLOW_DISTANCE = 3.5;
    private static final double FOLLOW_STOP_SQ = FOLLOW_DISTANCE * FOLLOW_DISTANCE;

    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = RootDependency.firstSet();
    @SuppressWarnings("unused")
    private final AetherhavenPlugin plugin;

    public GuardFollowPlayerSystem(@Nonnull AetherhavenPlugin plugin) {
        this.plugin = plugin;
    }

    public static boolean shouldSkipPatrol(@Nullable GuardFollowPlayerState follow) {
        return follow != null && follow.isActive();
    }

    public static boolean isEligibleGuard(
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> guardRef,
        @Nonnull UUID townId
    ) {
        return guardRef != null && guardRef.isValid() && RtsGuardDirectory.isTownGuard(guardRef, store, townId);
    }

    public static boolean isFollowingPlayer(
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> guardRef,
        @Nonnull UUID playerUuid
    ) {
        if (guardRef == null || !guardRef.isValid()) {
            return false;
        }
        GuardFollowPlayerState follow = store.getComponent(guardRef, GuardFollowPlayerState.getComponentType());
        return follow != null && follow.isFollowing(playerUuid);
    }

    public static void startFollow(
        @Nonnull Ref<EntityStore> guardRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID playerUuid
    ) {
        if (!guardRef.isValid()) {
            return;
        }
        TownVillagerBinding binding = store.getComponent(guardRef, TownVillagerBinding.getComponentType());
        if (binding == null || !TownVillagerBinding.KIND_GUARD.equals(binding.getKind())) {
            return;
        }
        NPCEntity npc = store.getComponent(guardRef, NPCEntity.getComponentType());
        if (npc == null || npc.getRole() == null) {
            return;
        }

        if (store.getComponent(guardRef, GuardRtsCommandState.getComponentType()) != null) {
            store.removeComponent(guardRef, GuardRtsCommandState.getComponentType());
        }

        GuardFollowPlayerState follow = store.getComponent(guardRef, GuardFollowPlayerState.getComponentType());
        if (follow == null) {
            follow = new GuardFollowPlayerState();
        } else {
            follow = (GuardFollowPlayerState) follow.clone();
        }
        follow.startFollowing(playerUuid);
        store.putComponent(guardRef, GuardFollowPlayerState.getComponentType(), follow);

        clearFollowRecoveryTracking(store, guardRef);

        String stateName = npc.getRole().getStateSupport().getStateName();
        if (!stateName.contains("Combat")) {
            ensurePatrolMotionStore(guardRef, npc, store);
        }
        store.putComponent(guardRef, NPCEntity.getComponentType(), npc);
    }

    public static void startFollow(
        @Nonnull Ref<EntityStore> guardRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID playerUuid
    ) {
        if (!guardRef.isValid()) {
            return;
        }
        TownVillagerBinding binding = store.getComponent(guardRef, TownVillagerBinding.getComponentType());
        if (binding == null || !TownVillagerBinding.KIND_GUARD.equals(binding.getKind())) {
            return;
        }
        NPCEntity npc = store.getComponent(guardRef, NPCEntity.getComponentType());
        if (npc == null || npc.getRole() == null) {
            return;
        }

        if (store.getComponent(guardRef, GuardRtsCommandState.getComponentType()) != null) {
            commandBuffer.removeComponent(guardRef, GuardRtsCommandState.getComponentType());
        }

        GuardFollowPlayerState follow = store.getComponent(guardRef, GuardFollowPlayerState.getComponentType());
        if (follow == null) {
            follow = new GuardFollowPlayerState();
        } else {
            follow = (GuardFollowPlayerState) follow.clone();
        }
        follow.startFollowing(playerUuid);
        commandBuffer.putComponent(guardRef, GuardFollowPlayerState.getComponentType(), follow);

        clearFollowRecoveryTracking(store, guardRef);

        String stateName = npc.getRole().getStateSupport().getStateName();
        if (!stateName.contains("Combat")) {
            ensurePatrolMotion(guardRef, npc, commandBuffer);
        }
        commandBuffer.putComponent(guardRef, NPCEntity.getComponentType(), npc);
    }

    public static void stopFollow(
        @Nonnull Ref<EntityStore> guardRef,
        @Nonnull Store<EntityStore> store,
        boolean resumePatrol
    ) {
        GuardFollowPlayerState follow = store.getComponent(guardRef, GuardFollowPlayerState.getComponentType());
        if (follow != null && follow.isActive()) {
            GuardFollowPlayerState cleared = (GuardFollowPlayerState) follow.clone();
            cleared.clear();
            store.putComponent(guardRef, GuardFollowPlayerState.getComponentType(), cleared);
            clearFollowRecoveryTracking(store, guardRef);
        }
        NPCEntity npc = store.getComponent(guardRef, NPCEntity.getComponentType());
        if (npc != null && npc.getRole() != null && resumePatrol) {
            String stateName = npc.getRole().getStateSupport().getStateName();
            if (stateName.contains("Patrol") || stateName.contains("Idle")) {
                npc.getRole().getStateSupport().setState(guardRef, "Idle", null, store);
                store.putComponent(guardRef, NPCEntity.getComponentType(), npc);
            }
        }
    }

    public static void stopFollow(
        @Nonnull Ref<EntityStore> guardRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        boolean resumePatrol
    ) {
        GuardFollowPlayerState follow = store.getComponent(guardRef, GuardFollowPlayerState.getComponentType());
        if (follow != null && follow.isActive()) {
            GuardFollowPlayerState cleared = (GuardFollowPlayerState) follow.clone();
            cleared.clear();
            commandBuffer.putComponent(guardRef, GuardFollowPlayerState.getComponentType(), cleared);
            clearFollowRecoveryTracking(store, guardRef);
        }
        NPCEntity npc = store.getComponent(guardRef, NPCEntity.getComponentType());
        if (npc != null && npc.getRole() != null && resumePatrol) {
            String stateName = npc.getRole().getStateSupport().getStateName();
            if (stateName.contains("Patrol") || stateName.contains("Idle")) {
                npc.getRole().getStateSupport().setState(guardRef, "Idle", null, commandBuffer);
                commandBuffer.putComponent(guardRef, NPCEntity.getComponentType(), npc);
            }
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
            GuardFollowPlayerState.getComponentType(),
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
        GuardFollowPlayerState follow =
            archetypeChunk.getComponent(index, GuardFollowPlayerState.getComponentType());
        NPCEntity npc = archetypeChunk.getComponent(index, NPCEntity.getComponentType());
        if (follow == null || npc == null || !follow.isActive()) {
            return;
        }
        UUIDComponent followUuid = store.getComponent(ref, UUIDComponent.getComponentType());
        if (followUuid != null
            && com.hexvane.aetherhaven.festival.snowball.SnowballSessionIndex.isLivingFighter(followUuid.getUuid())) {
            return;
        }
        if (NpcFaceVisuals.isInInteractionDialogue(npc)) {
            return;
        }

        String stateName = npc.getRole() != null ? npc.getRole().getStateSupport().getStateName() : "";
        if (stateName.contains("Combat")) {
            return;
        }

        UUID playerUuid = follow.getPlayerUuid();
        if (playerUuid == null) {
            stopFollow(ref, store, commandBuffer, true);
            return;
        }

        Ref<EntityStore> playerEntityRef = store.getExternalData().getRefFromUUID(playerUuid);
        if (playerEntityRef == null || !playerEntityRef.isValid()) {
            stopFollow(ref, store, commandBuffer, true);
            return;
        }
        if (store.getComponent(playerEntityRef, Player.getComponentType()) == null) {
            stopFollow(ref, store, commandBuffer, true);
            return;
        }

        TransformComponent playerTc = store.getComponent(playerEntityRef, TransformComponent.getComponentType());
        TransformComponent npcTc = store.getComponent(ref, TransformComponent.getComponentType());
        if (playerTc == null || npcTc == null) {
            stopFollow(ref, store, commandBuffer, true);
            return;
        }

        Vector3d playerPos = playerTc.getPosition();
        Vector3d npcPos = npcTc.getPosition();
        double dx = npcPos.x - playerPos.x;
        double dz = npcPos.z - playerPos.z;
        double horizSq = dx * dx + dz * dz;

        if (horizSq <= FOLLOW_STOP_SQ) {
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
            ensurePatrolMotion(ref, npc, commandBuffer);
            return;
        }

        Vector3d leash = standOffLeashPoint(playerPos, npcPos);
        npc.setLeashPoint(leash);
        ensurePatrolMotion(ref, npc, commandBuffer);
        commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
    }

    private static void ensurePatrolMotionStore(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull NPCEntity npc,
        @Nonnull Store<EntityStore> store
    ) {
        if (npc.getRole() == null) {
            return;
        }
        String stateName = npc.getRole().getStateSupport().getStateName();
        if (!stateName.contains(AetherhavenConstants.NPC_STATE_GUARD_PATROL)) {
            npc.getRole().getStateSupport().setState(ref, AetherhavenConstants.NPC_STATE_GUARD_PATROL, null, store);
        }
    }

    private static void ensurePatrolMotion(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull NPCEntity npc,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        if (npc.getRole() == null) {
            return;
        }
        String stateName = npc.getRole().getStateSupport().getStateName();
        if (!stateName.contains(AetherhavenConstants.NPC_STATE_GUARD_PATROL)) {
            npc.getRole().getStateSupport().setState(ref, AetherhavenConstants.NPC_STATE_GUARD_PATROL, null, commandBuffer);
        }
    }

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
