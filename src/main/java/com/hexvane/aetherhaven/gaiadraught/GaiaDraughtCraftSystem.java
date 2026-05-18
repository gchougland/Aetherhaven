package com.hexvane.aetherhaven.gaiadraught;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.CraftRecipeEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Mirrors town draught charges onto crafted flasks and refills replacement bottles. */
public final class GaiaDraughtCraftSystem extends EntityEventSystem<EntityStore, CraftRecipeEvent.Post> {
    private final Map<UUID, Boolean> hadDraughtBeforeCraft = new ConcurrentHashMap<>();

    @Nonnull
    private final AetherhavenPlugin plugin;

    public GaiaDraughtCraftSystem(@Nonnull AetherhavenPlugin plugin) {
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
        if (!isGaiaDraughtRecipe(event.getCraftedRecipe())) {
            return;
        }
        Ref<EntityStore> playerRef = archetypeChunk.getReferenceTo(index);
        UUIDComponent uc = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (uc == null) {
            return;
        }
        UUID playerUuid = uc.getUuid();
        Boolean had = hadDraughtBeforeCraft.remove(playerUuid);
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(), plugin);
        TownRecord town = tm.findTownForPlayerInWorld(playerUuid);
        if (town == null) {
            return;
        }
        GaiaDraughtService.onDraughtCrafted(
            playerRef,
            store,
            town,
            tm,
            playerUuid,
            had != null && had
        );
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }

    /** Records whether the player already carried a draught before inputs are removed. */
    public static final class Pre extends EntityEventSystem<EntityStore, CraftRecipeEvent.Pre> {
        @Nonnull
        private final GaiaDraughtCraftSystem craftSystem;

        public Pre(@Nonnull GaiaDraughtCraftSystem craftSystem) {
            super(CraftRecipeEvent.Pre.class);
            this.craftSystem = craftSystem;
        }

        @Override
        public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull CraftRecipeEvent.Pre event
        ) {
            if (!isGaiaDraughtRecipe(event.getCraftedRecipe())) {
                return;
            }
            Ref<EntityStore> playerRef = archetypeChunk.getReferenceTo(index);
            UUIDComponent uc = store.getComponent(playerRef, UUIDComponent.getComponentType());
            if (uc == null) {
                return;
            }
            craftSystem.hadDraughtBeforeCraft.put(
                uc.getUuid(),
                GaiaDraughtService.playerHasDraughtStack(
                    com.hypixel.hytale.server.core.inventory.InventoryComponent.getCombined(
                        store,
                        playerRef,
                        com.hypixel.hytale.server.core.inventory.InventoryComponent.EVERYTHING
                    )
                )
            );
        }

        @Nullable
        @Override
        public Query<EntityStore> getQuery() {
            return Player.getComponentType();
        }
    }

    private static boolean isGaiaDraughtRecipe(@Nonnull CraftingRecipe recipe) {
        MaterialQuantity primary = recipe.getPrimaryOutput();
        if (primary == null) {
            return false;
        }
        String itemId = primary.getItemId();
        return AetherhavenConstants.ITEM_GAIAS_DRAUGHT.equals(itemId);
    }
}
