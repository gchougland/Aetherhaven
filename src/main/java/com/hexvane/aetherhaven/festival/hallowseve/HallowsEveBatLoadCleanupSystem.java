package com.hexvane.aetherhaven.festival.hallowseve;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import javax.annotation.Nonnull;

/** Old saved bats are discarded on chunk load; active festivals recreate their temporary flock. */
public final class HallowsEveBatLoadCleanupSystem extends RefSystem<EntityStore> {
    private final ComponentType<EntityStore, NPCEntity> npcType;

    public HallowsEveBatLoadCleanupSystem() { this(NPCEntity.getComponentType()); }

    HallowsEveBatLoadCleanupSystem(ComponentType<EntityStore, NPCEntity> npcType) { this.npcType = npcType; }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.or(HallowsEveBatComponent.getComponentType(), npcType);
    }

    @Override
    public void onEntityAdded(@Nonnull Ref<EntityStore> ref, @Nonnull AddReason reason,
        @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commands) {
        if (reason == AddReason.LOAD && HallowsEveBatCleanup.isFestivalBat(
            commands.getComponent(ref, HallowsEveBatComponent.getComponentType()), commands.getComponent(ref, npcType))) {
            commands.tryRemoveEntity(ref, RemoveReason.REMOVE);
        }
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<EntityStore> ref, @Nonnull RemoveReason reason,
        @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commands) {}
}
