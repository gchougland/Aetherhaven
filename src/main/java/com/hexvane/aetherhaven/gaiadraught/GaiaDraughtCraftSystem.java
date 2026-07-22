package com.hexvane.aetherhaven.gaiadraught;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.CraftRecipeEvent;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Initializes crafted Gaia's Draught stacks with full charges and item metadata. */
public final class GaiaDraughtCraftSystem extends EntityEventSystem<EntityStore, CraftRecipeEvent.Post> {
    public GaiaDraughtCraftSystem() {
        super(CraftRecipeEvent.Post.class);
    }

    @Override
    public void handle(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull CraftRecipeEvent.Post event
    ) {
        if (!isGaiaDraughtRecipe(event.getCraftedRecipe())) {
            return;
        }
        Ref<EntityStore> playerRef = archetypeChunk.getReferenceTo(index);
        if (store.getComponent(playerRef, UUIDComponent.getComponentType()) == null) {
            return;
        }
        GaiaDraughtService.initializeCraftedDraughtStacksInInventory(playerRef, store);
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }

    private static boolean isGaiaDraughtRecipe(@Nonnull CraftingRecipe recipe) {
        MaterialQuantity primary = recipe.getPrimaryOutput();
        if (primary == null) {
            return false;
        }
        return AetherhavenConstants.ITEM_GAIAS_DRAUGHT.equals(primary.getItemId());
    }
}
