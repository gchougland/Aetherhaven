package com.hexvane.aetherhaven.shopspot;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

public final class ShopSpotBootstrap {
    private ShopSpotBootstrap() {}

    public static void reconcileAfterWorldLoad(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        world.execute(
            () -> {
                ShopSpotRegistry registry = AetherhavenWorldRegistries.getOrCreateShopSpotRegistry(world, plugin);
                Store<EntityStore> store = world.getEntityStore().getStore();
                for (ShopSpotRecord record : registry.allRecords()) {
                    record.markDisplayReconciled(null);
                }
                // Keep recorded UUIDs until reconciliation removes their entities, so neighbors
                // cannot mistake a valid display for an orphan during startup.
                ShopSpotDisplayService.syncAllInWorld(world, store, plugin, registry);
                ShopSpotPersistence.save(world, plugin, registry);
            }
        );
    }
}
