package com.hexvane.aetherhaven.gaiadraught;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownSharedRecipeUnlockService;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/** Flushes pending town-shared craft recipe unlocks without touching inventory. */
public final class PendingCraftRecipeUnlockTickSystem extends EntityTickingSystem<EntityStore> {
    private static final int FLUSH_INTERVAL_TICKS = 40;

    @Nonnull
    private final AetherhavenPlugin plugin;

    public PendingCraftRecipeUnlockTickSystem(@Nonnull AetherhavenPlugin plugin) {
        this.plugin = plugin;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        World world = store.getExternalData().getWorld();
        if ((world.getTick() + index) % FLUSH_INTERVAL_TICKS != 0) {
            return;
        }
        Ref<EntityStore> playerRef = archetypeChunk.getReferenceTo(index);
        UUIDComponent uc = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (uc == null) {
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.findTownForPlayerInWorld(uc.getUuid());
        if (town == null || !town.hasPendingCraftRecipeUnlocks(uc.getUuid())) {
            return;
        }
        TownSharedRecipeUnlockService.tryFlushPendingCraftRecipes(store, playerRef, town, tm, uc.getUuid());
    }
}
