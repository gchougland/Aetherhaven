package com.hexvane.aetherhaven.plugin;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.VillagerAutonomyState;
import com.hexvane.aetherhaven.autonomy.VillagerFollowPlayerState;
import com.hexvane.aetherhaven.patrol.GuardFollowPlayerState;
import com.hexvane.aetherhaven.builder.BuilderConstructionAssistState;
import com.hexvane.aetherhaven.monument.FounderMonumentStatueSkin;
import com.hexvane.aetherhaven.purification.PurificationPowderPlayerComponent;
import com.hexvane.aetherhaven.tourist.TouristAutonomyState;
import com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding;
import com.hexvane.aetherhaven.villager.AetherhavenAllowedTeleport;
import com.hexvane.aetherhaven.villager.AetherhavenNpcSpawnOrigin;
import com.hexvane.aetherhaven.villager.AetherhavenVillagerHandle;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import com.hexvane.aetherhaven.worldnpc.WorldNpcBinding;
import javax.annotation.Nonnull;

/**
 * Entity components queried by multiple feature packs at setup time. Registered on the parent plugin so registration
 * order between packs does not matter.
 */
public final class AetherhavenSharedEntityComponents {
    private AetherhavenSharedEntityComponents() {}

    public static void register(@Nonnull AetherhavenPlugin plugin) {
        var registry = plugin.getEntityStoreRegistry();
        VillagerNeeds.register(registry);
        AetherhavenVillagerHandle.register(registry);
        AetherhavenAllowedTeleport.register(registry);
        AetherhavenNpcSpawnOrigin.register(registry);
        TownVillagerBinding.register(registry);
        WorldNpcBinding.register(registry);
        TownsfolkCharacterBinding.register(registry);
        VillagerAutonomyState.register(registry);
        VillagerFollowPlayerState.register(registry);
        GuardFollowPlayerState.register(registry);
        TouristAutonomyState.register(registry);
        // Villagers (doorway bypass) and Construction (builder assist) both need this at system register time.
        BuilderConstructionAssistState.register(registry);
        // Villagers (NPC model resync excludes statues) and Construction (monument) both need this.
        FounderMonumentStatueSkin.register(registry);
        // Registered on core so /plugin unload of ReputationUnlocks does not unregister while players still have it.
        PurificationPowderPlayerComponent.register(registry);
    }
}
