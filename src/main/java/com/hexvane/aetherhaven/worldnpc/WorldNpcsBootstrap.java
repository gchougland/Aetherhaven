package com.hexvane.aetherhaven.worldnpc;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.command.AetherhavenWorldNpcCommand;
import com.hexvane.aetherhaven.plugin.AetherhavenFeatures;
import com.hexvane.aetherhaven.plugin.AetherhavenPluginIds;
import com.hexvane.aetherhaven.plugin.GameTimeTickListener;
import com.hexvane.aetherhaven.ui.WorldQuestBoardPage;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import javax.annotation.Nonnull;

public final class WorldNpcsBootstrap {
    private WorldNpcsBootstrap() {}

    public static void registerAssetCodecs(@Nonnull AetherhavenPlugin core) {
        OpenCustomUIInteraction.registerSimple(
            core,
            WorldQuestBoardPage.class,
            AetherhavenConstants.PAGE_WORLD_QUEST_BOARD,
            playerRef ->
                AetherhavenFeatures.isLoaded(AetherhavenPluginIds.WORLD_NPCS)
                    ? new WorldQuestBoardPage(playerRef)
                    : null
        );
    }

    public static void register(@Nonnull AetherhavenPlugin core, @Nonnull JavaPlugin plugin) {
        plugin.getEntityStoreRegistry().registerSystem(new WorldNpcSanitizeSystems.OnAdd());
        plugin.getEntityStoreRegistry().registerSystem(new WorldNpcSanitizeSystems.EachTick());
        core.registerAetherhavenSubcommand(new AetherhavenWorldNpcCommand());
    }

    @Nonnull
    public static GameTimeTickListener createScheduleListener(@Nonnull AetherhavenPlugin core) {
        return WorldNpcScheduleService.createListener(core);
    }
}
