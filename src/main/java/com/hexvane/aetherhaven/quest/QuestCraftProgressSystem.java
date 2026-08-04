package com.hexvane.aetherhaven.quest;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.hud.AetherhavenHudRefreshSystem;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.CraftRecipeEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Advances save wide player quest craft objectives when a player finishes a bench recipe. */
public final class QuestCraftProgressSystem extends EntityEventSystem<EntityStore, CraftRecipeEvent.Post> {
    @Nonnull
    private final AetherhavenPlugin plugin;

    public QuestCraftProgressSystem(@Nonnull AetherhavenPlugin plugin) {
        super(CraftRecipeEvent.Post.class);
        this.plugin = plugin;
    }

    @Override
    public void handle(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull CraftRecipeEvent.Post event
    ) {
        CraftingRecipe recipe = event.getCraftedRecipe();
        if (recipe == null) {
            return;
        }
        Ref<EntityStore> playerRef = archetypeChunk.getReferenceTo(index);
        if (!playerRef.isValid()) {
            return;
        }
        PlayerQuestProgress progress = store.getComponent(playerRef, PlayerQuestProgress.getComponentType());
        if (progress == null) {
            return;
        }
        boolean changed = false;
        for (var output : recipe.getOutputs()) {
            if (output == null || output.getItemId() == null || output.getItemId().isBlank()) {
                continue;
            }
            changed |= PlayerQuestProgressionService.onItemCrafted(plugin, progress, output.getItemId().trim());
        }
        if (!changed) {
            return;
        }
        changed |= PlayerQuestProgressionService.tryCompleteActiveQuests(plugin, progress);
        store.putComponent(playerRef, PlayerQuestProgress.getComponentType(), progress);
        World world = store.getExternalData().getWorld();
        if (world != null) {
            AetherhavenHudRefreshSystem.requestRefresh(world);
        }
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }
}
