package com.hexvane.aetherhaven.patrol;

import com.hexvane.aetherhaven.rts.RtsHostileQuery;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSettings;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Records combat provocations for guard AI: player hits on NPCs, and NPC hits on players or hired guards.
 */
public final class GuardCombatProvocationDamageSystem extends DamageEventSystem {
    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.or(Player.getComponentType(), NPCEntity.getComponentType());
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getInspectDamageGroup();
    }

    @Override
    public void handle(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Damage damage
    ) {
        if (damage.isCancelled() || damage.getAmount() <= 0.0) {
            return;
        }
        if (!(damage.getSource() instanceof Damage.EntitySource entitySource)) {
            return;
        }
        Ref<EntityStore> attackerRef = entitySource.getRef();
        if (!attackerRef.isValid()) {
            return;
        }
        Ref<EntityStore> victimRef = archetypeChunk.getReferenceTo(index);
        long nowMs = GuardCombatClock.nowMs(store);

        Player victimPlayer = archetypeChunk.getComponent(index, Player.getComponentType());
        if (victimPlayer != null) {
            if (commandBuffer.getComponent(attackerRef, NPCEntity.getComponentType()) != null
                && RtsHostileQuery.isGuardAttackableTarget(attackerRef, store)) {
                markNpcAttacker(store, attackerRef, nowMs);
            }
            return;
        }

        NPCEntity victimNpc = archetypeChunk.getComponent(index, NPCEntity.getComponentType());
        if (victimNpc == null) {
            return;
        }

        Player attackerPlayer = commandBuffer.getComponent(attackerRef, Player.getComponentType());
        if (attackerPlayer != null) {
            if (attackerPlayer.getGameMode() == GameMode.Creative) {
                PlayerSettings settings = commandBuffer.getComponent(attackerRef, PlayerSettings.getComponentType());
                if (settings == null || !settings.creativeSettings().allowNPCDetection()) {
                    return;
                }
            }
            UUIDComponent playerUuid = commandBuffer.getComponent(attackerRef, UUIDComponent.getComponentType());
            GuardPlayerProvokedTargets playerProvoked = store.getResource(GuardPlayerProvokedTargets.getResourceType());
            if (playerUuid != null && playerProvoked != null) {
                playerProvoked.markPlayerHit(playerUuid.getUuid(), victimRef, nowMs);
            }
            return;
        }

        if (commandBuffer.getComponent(attackerRef, NPCEntity.getComponentType()) == null) {
            return;
        }
        TownVillagerBinding binding = store.getComponent(victimRef, TownVillagerBinding.getComponentType());
        if (binding == null || !TownVillagerBinding.KIND_GUARD.equals(binding.getKind())) {
            return;
        }
        if (!RtsHostileQuery.isGuardAttackableTarget(attackerRef, store)) {
            return;
        }
        markNpcAttacker(store, attackerRef, nowMs);
    }

    private static void markNpcAttacker(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> attackerRef,
        long nowMs
    ) {
        GuardNpcAttackerMemory memory = store.getResource(GuardNpcAttackerMemory.getResourceType());
        if (memory != null) {
            memory.markAttacker(attackerRef, nowMs);
        }
    }
}
