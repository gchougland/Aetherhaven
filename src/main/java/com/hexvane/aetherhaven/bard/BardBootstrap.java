package com.hexvane.aetherhaven.bard;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import javax.annotation.Nonnull;

public final class BardBootstrap {
    private BardBootstrap() {}

    public static void registerAssetCodecs(@Nonnull AetherhavenPlugin core) {}

    public static void register(@Nonnull AetherhavenPlugin core, @Nonnull JavaPlugin plugin) {
        BardPerformanceComponent.register(plugin.getEntityStoreRegistry());
        BardActivePerformancesResource.register(plugin.getEntityStoreRegistry());
        BardMusicProximityState.register(plugin.getEntityStoreRegistry());
        plugin.getEntityStoreRegistry().registerSystem(new BardPerformanceTickSystem());
        plugin.getEntityStoreRegistry().registerSystem(new BardMusicProximitySystem());
    }
}
