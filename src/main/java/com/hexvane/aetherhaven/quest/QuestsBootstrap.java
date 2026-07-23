package com.hexvane.aetherhaven.quest;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.command.AetherhavenQuestBoardDebugCommand;
import com.hexvane.aetherhaven.command.AetherhavenQuestDebugCommand;
import com.hexvane.aetherhaven.command.AetherhavenTownRankDebugCommand;
import com.hexvane.aetherhaven.plugin.AetherhavenFeatures;
import com.hexvane.aetherhaven.plugin.AetherhavenPluginIds;
import com.hexvane.aetherhaven.plugin.GameTimeTickListener;
import com.hexvane.aetherhaven.questboard.QuestBoardOnlineDawnService;
import com.hexvane.aetherhaven.questboard.RaidHealthBarHudRefreshSystem;
import com.hexvane.aetherhaven.questboard.RaidQuestMarchSystem;
import com.hexvane.aetherhaven.questboard.RaidQuestMobBinding;
import com.hexvane.aetherhaven.ui.QuestBoardPage;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.time.Instant;
import java.time.LocalDateTime;
import javax.annotation.Nonnull;

public final class QuestsBootstrap {
    private QuestsBootstrap() {}

    public static void registerAssetCodecs(@Nonnull AetherhavenPlugin core) {
        OpenCustomUIInteraction.registerSimple(
            core,
            QuestBoardPage.class,
            AetherhavenConstants.PAGE_QUEST_BOARD,
            playerRef ->
                AetherhavenFeatures.isLoaded(AetherhavenPluginIds.QUESTS) ? new QuestBoardPage(playerRef) : null
        );
    }

    public static void register(@Nonnull AetherhavenPlugin core, @Nonnull JavaPlugin plugin) {
        RaidQuestMobBinding.register(plugin.getEntityStoreRegistry());
        plugin.getEntityStoreRegistry().registerSystem(new QuestKillProgressSystem(core));
        plugin.getEntityStoreRegistry().registerSystem(new RaidQuestMarchSystem(core));
        plugin.getEntityStoreRegistry().registerSystem(new RaidHealthBarHudRefreshSystem(core));
        core.registerAetherhavenSubcommand(new AetherhavenQuestDebugCommand());
        core.registerAetherhavenSubcommand(new AetherhavenQuestBoardDebugCommand());
        core.registerAetherhavenSubcommand(new AetherhavenTownRankDebugCommand());
    }

    @Nonnull
    public static GameTimeTickListener createQuestBoardDawnListener(@Nonnull AetherhavenPlugin core) {
        return new GameTimeTickListener() {
            @Override
            public void onSmoothGameMinuteAdvanced(
                @Nonnull Store<EntityStore> store,
                @Nonnull World world,
                @Nonnull WorldTimeResource wtr,
                long prevEpochMinute,
                long newEpochMinute
            ) {
                QuestBoardOnlineDawnService.tickWorld(world, store, core, wtr);
            }

            @Override
            public void onGameTimeDiscontinuity(
                @Nonnull Store<EntityStore> store,
                @Nonnull World world,
                @Nonnull WorldTimeResource wtr,
                @Nonnull Instant from,
                @Nonnull Instant to,
                @Nonnull LocalDateTime toDateTime,
                boolean backward
            ) {
                QuestBoardOnlineDawnService.tickWorld(world, store, core, wtr);
            }
        };
    }
}
