package com.hexvane.aetherhaven.patrol;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import javax.annotation.Nonnull;

public final class PatrolRoutesBootstrap {
    private PatrolRoutesBootstrap() {}

    public static void registerAssetCodecs(@Nonnull AetherhavenPlugin core) {
        core
            .getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenPatrolWandPrimary", PatrolWandPrimaryInteraction.class, PatrolWandPrimaryInteraction.CODEC);
        core
            .getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenPatrolWandSecondary",
                PatrolWandSecondaryInteraction.class,
                PatrolWandSecondaryInteraction.CODEC
            );
        core
            .getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenPatrolWandUse", PatrolWandUseInteraction.class, PatrolWandUseInteraction.CODEC);
        core
            .getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenPatrolWandModeCycle",
                PatrolWandModeCycleInteraction.class,
                PatrolWandModeCycleInteraction.CODEC
            );
        core
            .getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenPatrolWandNewRoute",
                PatrolWandNewRouteInteraction.class,
                PatrolWandNewRouteInteraction.CODEC
            );
        core
            .getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenPatrolWandToggleClosed",
                PatrolWandToggleClosedInteraction.class,
                PatrolWandToggleClosedInteraction.CODEC
            );
    }

    public static void register(@Nonnull AetherhavenPlugin core, @Nonnull JavaPlugin plugin) {
        PatrolWandPlayerComponent.register(plugin.getEntityStoreRegistry());
        GuardPatrolState.register(plugin.getEntityStoreRegistry());
        plugin.getEntityStoreRegistry().registerSystem(new PatrolWandPreviewSystem(core));
        plugin.getEntityStoreRegistry().registerSystem(new GuardPatrolSystem(core));
    }
}
