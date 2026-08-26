package com.hexvane.aetherhaven.patrol;

import com.hexvane.aetherhaven.npc.NpcSupportUtil;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.npc.NpcFaceVisuals;
import com.hexvane.aetherhaven.rts.GuardRtsCommandState;
import com.hexvane.aetherhaven.rts.RtsGuardCombatSupport;
import com.hexvane.aetherhaven.rts.RtsHostileQuery;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Auto engages threats for patrol and follow guards (RTS commanded guards use {@code GuardRtsCommandSystem}). */
public final class GuardAutonomousCombatSystem extends EntityTickingSystem<EntityStore> {
    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = RootDependency.firstSet();
    @SuppressWarnings("unused")
    private final AetherhavenPlugin plugin;

    public GuardAutonomousCombatSystem(@Nonnull AetherhavenPlugin plugin) {
        this.plugin = plugin;
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
            TownVillagerBinding.getComponentType(),
            NPCEntity.getComponentType(),
            Query.not(GuardRtsCommandState.getComponentType())
        );
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        TownVillagerBinding binding = chunk.getComponent(index, TownVillagerBinding.getComponentType());
        if (binding == null || !TownVillagerBinding.KIND_GUARD.equals(binding.getKind())) {
            return;
        }
        Ref<EntityStore> guardRef = chunk.getReferenceTo(index);
        NPCEntity npc = chunk.getComponent(index, NPCEntity.getComponentType());
        if (npc == null || npc.getRole() == null) {
            return;
        }
        if (NpcFaceVisuals.isInInteractionDialogue(npc)) {
            return;
        }
        String stateName = NpcSupportUtil.stateName(store, guardRef);
        if (stateName.contains("Interaction")) {
            return;
        }

        if (chunk.getComponent(index, GuardRtsCommandState.getComponentType()) != null) {
            return;
        }

        GuardFollowPlayerState follow = chunk.getComponent(index, GuardFollowPlayerState.getComponentType());
        Ref<EntityStore> playerRef = resolveFollowedPlayer(follow, store);

        if (stateName.contains("Combat")) {
            refreshCombatLock(guardRef, npc, store, commandBuffer, playerRef);
            return;
        }

        Ref<EntityStore> squadTarget = resolveFollowSquadThreat(guardRef, playerRef, store);
        Ref<EntityStore> threat = squadTarget != null ? squadTarget : resolveThreat(guardRef, playerRef, store);
        if (threat == null) {
            return;
        }
        if (!canEngage(guardRef, threat, store, playerRef, squadTarget != null && squadTarget.equals(threat))) {
            return;
        }
        RtsGuardCombatSupport.engageAutonomousThreat(guardRef, npc, threat, store, commandBuffer);
    }

    private static void refreshCombatLock(
        @Nonnull Ref<EntityStore> guardRef,
        @Nonnull NPCEntity npc,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nullable Ref<EntityStore> playerRef
    ) {
        Ref<EntityStore> locked = NpcSupportUtil.markedEntitySupport(guardRef, store)
            .getMarkedEntityRef(RtsGuardCombatSupport.LOCKED_TARGET_SLOT);
        if (locked != null && locked.isValid() && RtsHostileQuery.isGuardThreatTarget(guardRef, locked, store, playerRef)) {
            return;
        }
        Ref<EntityStore> squadTarget = resolveFollowSquadThreat(guardRef, playerRef, store);
        Ref<EntityStore> threat = squadTarget != null ? squadTarget : resolveThreat(guardRef, playerRef, store);
        if (threat == null
            || !canEngage(guardRef, threat, store, playerRef, squadTarget != null && squadTarget.equals(threat))) {
            return;
        }
        RtsGuardCombatSupport.lockCombatTarget(npc, threat, commandBuffer);
        commandBuffer.putComponent(guardRef, NPCEntity.getComponentType(), npc);
    }

    /**
     * When another guard following the same player is already fighting, pile onto that target so the whole escort
     * joins the fight (rear guards often lack line of sight to the mob).
     */
    @Nullable
    private static Ref<EntityStore> resolveFollowSquadThreat(
        @Nonnull Ref<EntityStore> guardRef,
        @Nullable Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        if (playerRef == null || !playerRef.isValid()) {
            return null;
        }
        GuardFollowPlayerState selfFollow = store.getComponent(guardRef, GuardFollowPlayerState.getComponentType());
        UUID playerUuid = selfFollow != null ? selfFollow.getPlayerUuid() : null;
        if (playerUuid == null) {
            return null;
        }
        double radius = AetherhavenConstants.RTS_DEFEND_RADIUS;
        List<Ref<EntityStore>> nearPlayer = RtsHostileQuery.collectNpcRefsNear(store, playerRef, radius);
        for (Ref<EntityStore> allyRef : nearPlayer) {
            if (allyRef.equals(guardRef) || !allyRef.isValid()) {
                continue;
            }
            TownVillagerBinding binding = store.getComponent(allyRef, TownVillagerBinding.getComponentType());
            if (binding == null || !TownVillagerBinding.KIND_GUARD.equals(binding.getKind())) {
                continue;
            }
            GuardFollowPlayerState allyFollow = store.getComponent(allyRef, GuardFollowPlayerState.getComponentType());
            if (allyFollow == null || !allyFollow.isFollowing(playerUuid)) {
                continue;
            }
            NPCEntity allyNpc = store.getComponent(allyRef, NPCEntity.getComponentType());
            if (allyNpc == null || allyNpc.getRole() == null) {
                continue;
            }
            if (!NpcSupportUtil.stateName(store, allyRef).contains("Combat")) {
                continue;
            }
            Ref<EntityStore> locked = NpcSupportUtil.markedEntitySupport(allyRef, store)
                .getMarkedEntityRef(RtsGuardCombatSupport.LOCKED_TARGET_SLOT);
            if (locked == null || !locked.isValid()) {
                continue;
            }
            if (!RtsHostileQuery.isGuardThreatTarget(guardRef, locked, store, playerRef)) {
                continue;
            }
            return locked;
        }
        return null;
    }

    @Nullable
    private static Ref<EntityStore> resolveThreat(
        @Nonnull Ref<EntityStore> guardRef,
        @Nullable Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        double radius = AetherhavenConstants.RTS_DEFEND_RADIUS;
        if (playerRef != null && playerRef.isValid()) {
            return RtsHostileQuery.resolveFollowPlayerThreat(guardRef, playerRef, store, radius);
        }
        return RtsHostileQuery.resolveAutonomousGuardThreat(guardRef, store, radius);
    }

    private static boolean canEngage(
        @Nonnull Ref<EntityStore> guardRef,
        @Nonnull Ref<EntityStore> threatRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> playerRef,
        boolean squadAssistTarget
    ) {
        if (squadAssistTarget) {
            return true;
        }
        if (RtsHostileQuery.canEngageWithoutLineOfSight(guardRef, threatRef, store, playerRef)) {
            return true;
        }
        return RtsHostileQuery.hasLineOfSight(guardRef, threatRef, store);
    }

    @Nullable
    private static Ref<EntityStore> resolveFollowedPlayer(
        @Nullable GuardFollowPlayerState follow,
        @Nonnull Store<EntityStore> store
    ) {
        if (follow == null || !follow.isActive()) {
            return null;
        }
        UUID playerUuid = follow.getPlayerUuid();
        if (playerUuid == null) {
            return null;
        }
        Ref<EntityStore> playerRef = store.getExternalData().getRefFromUUID(playerUuid);
        if (playerRef == null || !playerRef.isValid()) {
            return null;
        }
        if (store.getComponent(playerRef, Player.getComponentType()) == null) {
            return null;
        }
        return playerRef;
    }
}
