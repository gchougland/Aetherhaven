package com.hexvane.aetherhaven.townsfolk;

import com.hexvane.aetherhaven.npc.NpcSupportUtil;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Keeps phase 1 townsfolk assignments on rect-wander Idle by exiting {@link AetherhavenConstants#NPC_STATE_AUTONOMY_POI}
 * when checked out for idle/tourist/guard placeholder behavior.
 */
public final class TownsfolkAssignmentSystem extends EntityTickingSystem<EntityStore> {
    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = RootDependency.firstSet();

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(TownsfolkCharacterBinding.getComponentType(), NPCEntity.getComponentType());
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        TownsfolkCharacterBinding binding = archetypeChunk.getComponent(index, TownsfolkCharacterBinding.getComponentType());
        NPCEntity npc = archetypeChunk.getComponent(index, NPCEntity.getComponentType());
        if (binding == null || npc == null || npc.getRole() == null) {
            return;
        }
        if (!TownsfolkAssignmentKinds.usesIdleStandAround(binding.getAssignmentKind())) {
            return;
        }
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        String state = NpcSupportUtil.stateName(store, ref);
        if (state.startsWith(AetherhavenConstants.NPC_STATE_AUTONOMY_POI)) {
            NpcSupportUtil.setState(ref, "Idle", null, commandBuffer);
        }
    }
}
