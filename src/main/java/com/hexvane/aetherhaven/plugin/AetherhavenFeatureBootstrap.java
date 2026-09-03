package com.hexvane.aetherhaven.plugin;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.VillagersBootstrap;
import com.hexvane.aetherhaven.bard.BardBootstrap;
import com.hexvane.aetherhaven.construction.ConstructionBootstrap;
import com.hexvane.aetherhaven.dialogue.DialogueBootstrap;
import com.hexvane.aetherhaven.economy.EconomyBootstrap;
import com.hexvane.aetherhaven.festival.FestivalsBootstrap;
import com.hexvane.aetherhaven.floatinggift.FloatingGiftsBootstrap;
import com.hexvane.aetherhaven.guild.GuildBootstrap;
import com.hexvane.aetherhaven.jewelry.JewelryBootstrap;
import com.hexvane.aetherhaven.jewelry.JewelryGemTraits;
import com.hexvane.aetherhaven.jewelry.JewelryNativeTooltipManager;
import com.hexvane.aetherhaven.jewelry.Loot4EveryoneIntegration;
import com.hexvane.aetherhaven.jewelry.LootrIntegration;
import com.hexvane.aetherhaven.pathtool.PathDesignerBootstrap;
import com.hexvane.aetherhaven.patrol.PatrolRoutesBootstrap;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorBootstrap;
import com.hexvane.aetherhaven.production.ProductionBootstrap;
import com.hexvane.aetherhaven.prop.PropsBootstrap;
import com.hexvane.aetherhaven.purification.PurificationPowderPlayerComponent;
import com.hexvane.aetherhaven.quest.QuestsBootstrap;
import com.hexvane.aetherhaven.reputation.ReputationBootstrap;
import com.hexvane.aetherhaven.rts.RtsBootstrap;
import com.hexvane.aetherhaven.shopspot.CommerceBootstrap;
import com.hexvane.aetherhaven.worldnpc.WorldNpcsBootstrap;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Registers optional feature packs from the parent plugin, gated by server mod config.
 *
 * <p>Features are not loaded via manifest {@code SubPlugins}: Hytale copies the parent {@code Version} into each
 * subplugin as a bare semver range, which rejects non-zero patch versions (e.g. {@code 2.1.2}). Parent-driven
 * registration keeps a normal patch version on the main mod only.
 */
public final class AetherhavenFeatureBootstrap {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final List<GameTimeTickListener> TICK_LISTENERS = new ArrayList<>();
    private static final List<Runnable> SHUTDOWN_HOOKS = new ArrayList<>();
    private static boolean jewelryStarted;

    private AetherhavenFeatureBootstrap() {}

    /** Runs on plugin shutdown after feature tick listeners are cleared. */
    public static void registerShutdownHook(@Nonnull Runnable hook) {
        SHUTDOWN_HOOKS.add(hook);
    }

    public static void registerEnabled(@Nonnull AetherhavenPlugin core) {
        TICK_LISTENERS.clear();
        SHUTDOWN_HOOKS.clear();
        jewelryStarted = false;
        // Host registries on the parent plugin (same classloader / lifecycle as core).
        JavaPlugin host = core;

        register(AetherhavenPluginIds.VILLAGERS, () -> {
            VillagersBootstrap.register(core, host);
            registerTick(core, VillagersBootstrap.createVillagerScheduleGameTimeListener(core));
        });
        register(AetherhavenPluginIds.FLOATING_GIFTS, () -> FloatingGiftsBootstrap.register(core, host));
        register(AetherhavenPluginIds.PATH_DESIGNER, () -> PathDesignerBootstrap.register(core, host));
        register(AetherhavenPluginIds.BARD, () -> BardBootstrap.register(core, host));
        register(AetherhavenPluginIds.ADMIN_TOOLS, () -> AdminToolsBootstrap.register(core, host));
        register(AetherhavenPluginIds.ECONOMY, () -> {
            EconomyBootstrap.register(core, host);
            registerTick(core, EconomyBootstrap.createEconomyGameTimeListener(core));
        });
        register(AetherhavenPluginIds.REPUTATION, () -> ReputationBootstrap.register(core, host));
        register(AetherhavenPluginIds.QUESTS, () -> {
            QuestsBootstrap.register(core, host);
            registerTick(core, QuestsBootstrap.createQuestBoardDawnListener(core));
        });
        register(AetherhavenPluginIds.JEWELRY, () -> JewelryBootstrap.register(core, host));
        register(AetherhavenPluginIds.REPUTATION_UNLOCKS, () -> {
            if (!AetherhavenFeatures.isLoaded(AetherhavenPluginIds.REPUTATION)) {
                LOGGER
                    .atWarning()
                    .log("ReputationUnlocks requires %s; skipping feature registration", AetherhavenPluginIds.REPUTATION);
                return;
            }
            ReputationUnlocksBootstrap.register(core, host);
            registerTick(core, ReputationUnlocksBootstrap.createSprinklerGameTimeListener(core));
        });
        register(AetherhavenPluginIds.RTS, () -> RtsBootstrap.register(core, host));
        register(AetherhavenPluginIds.PATROL_ROUTES, () -> PatrolRoutesBootstrap.register(core, host));
        register(AetherhavenPluginIds.PLOT_CREATOR, () -> PlotCreatorBootstrap.register(core, host));
        register(AetherhavenPluginIds.COMMERCE, () -> {
            CommerceBootstrap.register(core, host);
            registerTick(core, CommerceBootstrap.createCommerceGameTimeListener(core));
        });
        register(AetherhavenPluginIds.GUILD, () -> {
            GuildBootstrap.register(core, host);
            registerTick(core, GuildBootstrap.createGuildAdventurerPoolListener(core));
        });
        register(AetherhavenPluginIds.CONSTRUCTION, () -> {
            ConstructionBootstrap.register(core, host);
            registerTick(core, ConstructionBootstrap.createPlotAssemblyGameTimeListener(core));
        });
        register(AetherhavenPluginIds.PRODUCTION, () -> {
            ProductionBootstrap.register(core, host);
            registerTick(core, ProductionBootstrap.createProductionGameTimeListener(core));
        });
        register(AetherhavenPluginIds.DIALOGUE, () -> DialogueBootstrap.register(core, host));
        register(AetherhavenPluginIds.WORLD_NPCS, () -> {
            WorldNpcsBootstrap.register(core, host);
            registerTick(core, WorldNpcsBootstrap.createScheduleListener(core));
        });
        register(AetherhavenPluginIds.FESTIVALS, () -> {
            FestivalsBootstrap.register(core, host);
            registerTick(core, FestivalsBootstrap.createFestivalGameTimeListener(core));
        });
    }

    public static void startEnabled(@Nonnull AetherhavenPlugin core) {
        if (!jewelryStarted && AetherhavenFeatures.isLoaded(AetherhavenPluginIds.JEWELRY)) {
            JewelryNativeTooltipManager.refreshAllPlayers();
            JewelryGemTraits.validateStatIdsAtStartup();
            if (LootrIntegration.tryInitialize()) {
                LootrIntegration.registerIfAvailable(core);
            }
            if (Loot4EveryoneIntegration.tryInitialize()) {
                Loot4EveryoneIntegration.registerIfAvailable();
            }
            jewelryStarted = true;
        }
    }

    public static void shutdownEnabled() {
        if (AetherhavenFeatures.isLoaded(AetherhavenPluginIds.REPUTATION_UNLOCKS)) {
            PurificationPowderPlayerComponent.detachAllOnlinePlayers();
        }
        AetherhavenPlugin core = AetherhavenPlugin.get();
        if (core != null) {
            GameTimeTickListenerRegistry registry = core.getGameTimeTickListenerRegistry();
            for (GameTimeTickListener listener : TICK_LISTENERS) {
                registry.unregister(listener);
            }
        }
        TICK_LISTENERS.clear();
        for (Runnable hook : SHUTDOWN_HOOKS) {
            try {
                hook.run();
            } catch (Throwable t) {
                LOGGER.atWarning().withCause(t).log("Feature shutdown hook failed");
            }
        }
        SHUTDOWN_HOOKS.clear();
        jewelryStarted = false;
    }

    private static void register(@Nonnull PluginIdentifier id, @Nonnull Runnable enable) {
        if (!AetherhavenFeatures.isEnabledInServerConfig(id)) {
            LOGGER.atInfo().log("Skipping disabled feature pack %s", id);
            return;
        }
        try {
            enable.run();
            LOGGER.atInfo().log("Registered feature pack %s", id);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to register feature pack %s", id);
        }
    }

    private static void registerTick(@Nonnull AetherhavenPlugin core, @Nullable GameTimeTickListener listener) {
        if (listener == null) {
            return;
        }
        core.getGameTimeTickListenerRegistry().register(listener);
        TICK_LISTENERS.add(listener);
    }
}
