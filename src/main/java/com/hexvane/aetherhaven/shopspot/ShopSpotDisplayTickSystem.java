package com.hexvane.aetherhaven.shopspot;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/** Keeps shop spot item displays spawned in loaded chunks (open + in stock), independent of player look-at. */
public final class ShopSpotDisplayTickSystem extends TickingSystem<EntityStore> {
    private static final float SYNC_INTERVAL_SEC = 2.0f;

    private float timer;
    private final AetherhavenPlugin plugin;

    public ShopSpotDisplayTickSystem(@Nonnull AetherhavenPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        timer += dt;
        if (timer < SYNC_INTERVAL_SEC) {
            return;
        }
        timer = 0.0f;
        World world = store.getExternalData().getWorld();
        if (!world.isAlive()) {
            return;
        }
        ShopSpotRegistry registry = AetherhavenWorldRegistries.getOrCreateShopSpotRegistry(world, plugin);
        ShopSpotDisplayService.syncAllInWorld(world, store, plugin, registry);
    }
}
