package com.hexvane.aetherhaven.plugin;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.VillagerAutonomyState;
import com.hexvane.aetherhaven.purification.PurificationPowderPlayerComponent;
import com.hexvane.aetherhaven.tourist.TouristAutonomyState;
import com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding;
import com.hexvane.aetherhaven.villager.AetherhavenVillagerHandle;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import javax.annotation.Nonnull;

/**
 * Entity components queried by multiple Aetherhaven subplugins at setup time. Registered on the parent plugin so they
 * survive subplugin load order (Hytale uses dependency-based {@code Mod.calculateLoadOrder}, not {@code SubPlugins[]}
 * order) and are not torn down when a subplugin's setup fails.
 */
public final class AetherhavenSharedEntityComponents {
    private AetherhavenSharedEntityComponents() {}

    public static void register(@Nonnull AetherhavenPlugin plugin) {
        var registry = plugin.getEntityStoreRegistry();
        VillagerNeeds.register(registry);
        AetherhavenVillagerHandle.register(registry);
        TownVillagerBinding.register(registry);
        TownsfolkCharacterBinding.register(registry);
        VillagerAutonomyState.register(registry);
        TouristAutonomyState.register(registry);
        // Registered on core so /plugin unload of ReputationUnlocks does not unregister while players still have it.
        PurificationPowderPlayerComponent.register(registry);
    }
}
