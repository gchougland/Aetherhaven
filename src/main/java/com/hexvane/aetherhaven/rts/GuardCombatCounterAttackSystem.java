package com.hexvane.aetherhaven.rts;

import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.builtin.npccombatactionevaluator.memory.TargetMemory;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import javax.annotation.Nonnull;

/** Prompts combat targets to fight back when autonomous town guards engage them. */
public final class GuardCombatCounterAttackSystem extends EntityTickingSystem<EntityStore> {
    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(TownVillagerBinding.getComponentType(), NPCEntity.getComponentType());
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
        NPCEntity npc = chunk.getComponent(index, NPCEntity.getComponentType());
        if (npc == null || npc.getRole() == null) {
            return;
        }
        if (!npc.getRole().getStateSupport().getStateName().contains("Combat")) {
            return;
        }
        Ref<EntityStore> guardRef = chunk.getReferenceTo(index);
        Ref<EntityStore> targetRef = npc.getRole()
            .getMarkedEntitySupport()
            .getMarkedEntityRef(RtsGuardCombatSupport.LOCKED_TARGET_SLOT);
        if (targetRef == null || !targetRef.isValid()) {
            return;
        }
        if (!RtsHostileQuery.isGuardThreatTarget(guardRef, targetRef, store)) {
            return;
        }
        if (alreadyRemembersGuard(targetRef, guardRef, store)) {
            return;
        }
        RtsGuardCombatSupport.promptCounterAttack(guardRef, targetRef, store, commandBuffer);
    }

    private static boolean alreadyRemembersGuard(
        @Nonnull Ref<EntityStore> targetRef,
        @Nonnull Ref<EntityStore> guardRef,
        @Nonnull Store<EntityStore> store
    ) {
        TargetMemory memory = store.getComponent(targetRef, TargetMemory.getComponentType());
        if (memory != null && memory.getKnownHostiles().containsKey(guardRef.getIndex())) {
            return true;
        }
        NPCEntity hostile = store.getComponent(targetRef, NPCEntity.getComponentType());
        if (hostile == null || hostile.getRole() == null) {
            return false;
        }
        Ref<EntityStore> locked = hostile.getRole()
            .getMarkedEntitySupport()
            .getMarkedEntityRef(RtsGuardCombatSupport.LOCKED_TARGET_SLOT);
        return guardRef.equals(locked);
    }
}
