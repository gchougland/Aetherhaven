package com.hexvane.aetherhaven.economy;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/**
 * Game-time hooks for town economy (treasury tithe). Not related to the inn visitor pool; those use
 * {@link com.hexvane.aetherhaven.inn.InnPoolService} separately, though both may share the same configured morning hours.
 */
public final class TownEconomyTimeService {
    private TownEconomyTimeService() {}

    /**
     * Invoked from {@link com.hexvane.aetherhaven.time.AetherhavenGameTimeBridgeSubscriber} on the entity-store thread
     * when game time advances (smooth minute or forward discontinuity).
     */
    public static void onGameTimeFromHub(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull WorldTimeResource wtr,
        @Nonnull Store<EntityStore> store
    ) {
        TownTaxService.applyDailyTreasuryTithe(world, plugin, wtr, store);
    }
}
