package com.hexvane.aetherhaven.production;

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

/**
 * A barn display animal is never meant to come back from storage: the barn recreates it from the town's owned
 * animals. Any that loads is a copy an older build left in a chunk, so it is discarded as its chunk loads.
 */
public final class BarnDisplayLoadCleanupSystem extends RefSystem<EntityStore> {
    private final ComponentType<EntityStore, NPCEntity> npcType;

    public BarnDisplayLoadCleanupSystem() { this(NPCEntity.getComponentType()); }

    BarnDisplayLoadCleanupSystem(ComponentType<EntityStore, NPCEntity> npcType) { this.npcType = npcType; }

    @Override
    public Query<EntityStore> getQuery() { return npcType; }

    @Override
    public void onEntityAdded(@Nonnull Ref<EntityStore> ref, @Nonnull AddReason reason,
        @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commands) {
        if (reason != AddReason.LOAD) return;
        var npc = commands.getComponent(ref, npcType);
        // REMOVE, not UNLOAD, so the saved copy is deleted with it.
        if (npc != null && BarnAnimalCatalog.displayRole(npc.getRoleName())) commands.tryRemoveEntity(ref, RemoveReason.REMOVE);
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<EntityStore> ref, @Nonnull RemoveReason reason,
        @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commands) {}
}
