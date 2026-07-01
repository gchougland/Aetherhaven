package com.hexvane.aetherhaven.plugin;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.command.AetherhavenAutonomyDebugCommand;
import com.hexvane.aetherhaven.command.AetherhavenDifficultyCommand;
import com.hexvane.aetherhaven.command.AetherhavenLootChestDebugCommand;
import com.hexvane.aetherhaven.command.AetherhavenPoiCommand;
import com.hexvane.aetherhaven.command.AetherhavenRtsBoxDebugCommand;
import com.hexvane.aetherhaven.command.AetherhavenRtsRecoverInventoryCommand;
import com.hexvane.aetherhaven.command.AetherhavenTimeCommand;
import com.hexvane.aetherhaven.command.AetherhavenTouristDebugCommand;
import com.hexvane.aetherhaven.command.AetherhavenWallDebugCommand;
import com.hexvane.aetherhaven.poi.tool.PoiDebugLabelEntity;
import com.hexvane.aetherhaven.poi.tool.PoiToolModeCycleInteraction;
import com.hexvane.aetherhaven.poi.tool.PoiToolMoveInteraction;
import com.hexvane.aetherhaven.poi.tool.PoiToolPlayerComponent;
import com.hexvane.aetherhaven.poi.tool.PoiToolSecondaryInteraction;
import com.hexvane.aetherhaven.poi.tool.PoiToolSelectInteraction;
import com.hexvane.aetherhaven.poi.tool.PoiToolSetTargetInteraction;
import com.hexvane.aetherhaven.poi.tool.PoiToolVisualizationPlayerRemoveSystem;
import com.hexvane.aetherhaven.poi.tool.PoiToolVisualizationSystem;
import com.hexvane.aetherhaven.ui.DifficultyPage;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import javax.annotation.Nonnull;

public final class AdminToolsBootstrap {
    private AdminToolsBootstrap() {}

    public static void registerAssetCodecs(@Nonnull AetherhavenPlugin core) {
        core
            .getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenPoiToolSelect", PoiToolSelectInteraction.class, PoiToolSelectInteraction.CODEC);
        core
            .getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenPoiToolMove", PoiToolMoveInteraction.class, PoiToolMoveInteraction.CODEC);
        core
            .getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenPoiToolSecondary", PoiToolSecondaryInteraction.class, PoiToolSecondaryInteraction.CODEC);
        core
            .getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenPoiToolModeCycle", PoiToolModeCycleInteraction.class, PoiToolModeCycleInteraction.CODEC);
        core
            .getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenPoiToolSetTarget",
                PoiToolSetTargetInteraction.class,
                PoiToolSetTargetInteraction.CODEC
            );
        OpenCustomUIInteraction.registerSimple(
            core,
            DifficultyPage.class,
            AetherhavenConstants.PAGE_DIFFICULTY,
            playerRef ->
                AetherhavenFeatures.isLoaded(AetherhavenPluginIds.ADMIN_TOOLS) ? new DifficultyPage(playerRef) : null
        );
    }

    public static void register(@Nonnull AetherhavenPlugin core, @Nonnull JavaPlugin plugin) {
        PoiToolPlayerComponent.register(plugin.getEntityStoreRegistry());
        plugin
            .getEntityRegistry()
            .registerEntity(
                "AetherhavenPoiDebugLabel",
                PoiDebugLabelEntity.class,
                world -> {
                    PoiDebugLabelEntity e = new PoiDebugLabelEntity();
                    if (world != null) {
                        e.loadIntoWorld(world);
                    }
                    return e;
                },
                PoiDebugLabelEntity.CODEC
            );
        plugin.getEntityStoreRegistry().registerSystem(new PoiToolVisualizationSystem(core));
        plugin.getEntityStoreRegistry().registerSystem(new PoiToolVisualizationPlayerRemoveSystem());
        core.registerAetherhavenSubcommand(new AetherhavenPoiCommand());
        core.registerAetherhavenSubcommand(new AetherhavenAutonomyDebugCommand());
        core.registerAetherhavenSubcommand(new AetherhavenWallDebugCommand());
        core.registerAetherhavenSubcommand(new AetherhavenRtsBoxDebugCommand());
        core.registerAetherhavenSubcommand(new AetherhavenRtsRecoverInventoryCommand());
        core.registerAetherhavenSubcommand(new AetherhavenLootChestDebugCommand());
        core.registerAetherhavenSubcommand(new AetherhavenTouristDebugCommand());
        core.registerAetherhavenSubcommand(new AetherhavenTimeCommand());
        core.registerAetherhavenSubcommand(new AetherhavenDifficultyCommand());
    }
}
