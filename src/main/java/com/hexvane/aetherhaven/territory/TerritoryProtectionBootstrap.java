package com.hexvane.aetherhaven.territory;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

public final class TerritoryProtectionBootstrap {
    private TerritoryProtectionBootstrap() {}

    public static void register(@Nonnull AetherhavenPlugin plugin) {
        plugin.getEntityStoreRegistry().registerSystem(new TownTerritoryBreakBlockSystem(plugin));
        plugin.getEntityStoreRegistry().registerSystem(new TownTerritoryPlaceBlockSystem(plugin));
        plugin.getEntityStoreRegistry().registerSystem(new TownTerritoryUseBlockSystem(plugin));
    }
}
