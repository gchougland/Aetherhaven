package com.hexvane.aetherhaven.villager.audit;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import javax.annotation.Nonnull;

/** Logs town villager entity removals to the audit JSONL file. */
public final class TownVillagerAuditRemoveSystem extends RefSystem<EntityStore> {
    private static final String UNKNOWN_SOURCE = "external_remove";

    private final AetherhavenPlugin plugin;

    public TownVillagerAuditRemoveSystem(@Nonnull AetherhavenPlugin plugin) {
        this.plugin = plugin;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(TownVillagerBinding.getComponentType(), UUIDComponent.getComponentType(), NPCEntity.getComponentType());
    }

    @Override
    public void onEntityAdded(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull AddReason reason,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {}

    @Override
    public void onEntityRemove(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull RemoveReason reason,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        if (reason == RemoveReason.UNLOAD) {
            return;
        }
        if (reason != RemoveReason.REMOVE) {
            return;
        }
        String source = VillagerAuditContext.currentSource();
        if (source == null || source.isBlank()) {
            source = UNKNOWN_SOURCE;
        }
        VillagerAuditService.logRemoved(plugin, store, ref, source);
    }
}
