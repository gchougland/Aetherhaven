package com.hexvane.aetherhaven.pathtool;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.command.AetherhavenPathCommand;
import com.hexvane.aetherhaven.plugin.AetherhavenFeatureBootstrap;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import javax.annotation.Nonnull;

public final class PathDesignerBootstrap {
    private PathDesignerBootstrap() {}

    public static void registerAssetCodecs(@Nonnull AetherhavenPlugin core) {
        core
            .getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenPathToolSelect", PathToolSelectInteraction.class, PathToolSelectInteraction.CODEC);
        core
            .getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenPathToolAddNode", PathToolAddNodeInteraction.class, PathToolAddNodeInteraction.CODEC);
        core
            .getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenPathToolUse", PathToolUseInteraction.class, PathToolUseInteraction.CODEC);
        core
            .getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenPathToolModeCycle", PathToolModeCycleInteraction.class, PathToolModeCycleInteraction.CODEC);
        core
            .getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenPathToolWidthCycle", PathToolWidthCycleInteraction.class, PathToolWidthCycleInteraction.CODEC);
        core
            .getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenPathToolStyleCycle", PathToolStyleCycleInteraction.class, PathToolStyleCycleInteraction.CODEC);
    }

    public static void register(@Nonnull AetherhavenPlugin core, @Nonnull JavaPlugin plugin) {
        PathToolPlayerComponent.register(plugin.getEntityStoreRegistry());
        plugin.getEntityStoreRegistry().registerSystem(new PathToolPreviewSystem(core));
        core.registerAetherhavenSubcommand(new AetherhavenPathCommand());
        AetherhavenFeatureBootstrap.registerShutdownHook(PathNavViz::shutdown);
    }
}
