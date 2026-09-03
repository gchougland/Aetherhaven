package com.hexvane.aetherhaven.plugin;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.HStats;
import com.hexvane.aetherhaven.dialogue.DialogueBootstrap;
import com.hexvane.aetherhaven.command.AetherhavenCommand;
import com.hexvane.aetherhaven.command.AetherhavenCommunityCommand;
import com.hexvane.aetherhaven.command.AetherhavenSupportCommand;
import com.hexvane.aetherhaven.command.AetherhavenHudCommand;
import com.hexvane.aetherhaven.command.AetherhavenJournalCommand;
import com.hexvane.aetherhaven.generated.HstatsBuildMetadata;
import com.hexvane.aetherhaven.hud.AetherhavenHudRefreshSystem;
import com.hexvane.aetherhaven.hud.AetherhavenHudSupport;
import com.hexvane.aetherhaven.entity.BlockEntityScaleMigrated;
import com.hexvane.aetherhaven.entity.BlockEntityScaleRepairSystem;
import com.hexvane.aetherhaven.entity.EntityRotationRepairSystem;
import com.hexvane.aetherhaven.map.TeleporterWarpSanitizer;
import com.hexvane.aetherhaven.npc.AetherhavenNpcRoleLoader;
import com.hexvane.aetherhaven.plot.GaiaStatueBlock;
import com.hexvane.aetherhaven.plot.ConstructionFavoritesPlayerInitSystem;
import com.hexvane.aetherhaven.plot.PlayerConstructionFavoritesState;
import com.hexvane.aetherhaven.plot.PlayerPlotTokenUnlockState;
import com.hexvane.aetherhaven.plot.PlotBlueprintSalvageBenchSystem;
import com.hexvane.aetherhaven.plot.PlotTokenUnlockPageUseInteraction;
import com.hexvane.aetherhaven.plot.PlotTokenUnlockPlayerInitSystem;
import com.hexvane.aetherhaven.placement.PlotConstructionBlockResolver;
import com.hexvane.aetherhaven.prop.PropsBootstrap;
import com.hexvane.aetherhaven.quest.IntroQuestPromptService;
import com.hexvane.aetherhaven.questboard.QuestBoardOnlineDawnService;
import com.hexvane.aetherhaven.rts.RtsCommandService;
import com.hexvane.aetherhaven.time.AetherhavenGameTimeBridgeSubscriber;
import com.hexvane.aetherhaven.time.AetherhavenGameTimeCoordinatorSystem;
import com.hexvane.aetherhaven.time.AetherhavenGameTimeCursorResource;
import com.hexvane.aetherhaven.time.AetherhavenGameTimeHub;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotLocatePlayerComponent;
import com.hexvane.aetherhaven.town.PlotLocatePlayerInitSystem;
import com.hexvane.aetherhaven.town.PlotLocateTrailSystem;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownMemberBlockAccess;
import com.hexvane.aetherhaven.ui.PlayerTownJournalState;
import com.hexvane.aetherhaven.ui.TownJournalPlayerInitSystem;
import com.hexvane.aetherhaven.villager.VillagerLocatePlayerComponent;
import com.hexvane.aetherhaven.villager.VillagerLocatePlayerInitSystem;
import com.hexvane.aetherhaven.territory.TerritoryProtectionBootstrap;
import com.hexvane.aetherhaven.tourist.TouristReconcileService;
import com.hexvane.aetherhaven.town.TownResidentReconcileService;
import com.hexvane.aetherhaven.town.RetiredBuiltInPlotMigration;
import com.hexvane.aetherhaven.festival.wintertide.WintertideGiftService;
import com.hexvane.aetherhaven.ui.GaiaStatueRevivePage;
import com.hexvane.aetherhaven.ui.QuestJournalPage;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.permissions.provider.HytalePermissionsProvider;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.events.AddWorldEvent;
import com.hypixel.hytale.server.core.universe.world.events.AllWorldsLoadedEvent;
import com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent;
import com.hypixel.hytale.server.core.universe.world.events.StartWorldEvent;
import com.hypixel.hytale.server.core.asset.LoadAssetEvent;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import java.util.UUID;
import javax.annotation.Nonnull;

/** Parent-plugin registrations shared by all Aetherhaven subplugins. */
public final class AetherhavenCoreBootstrap {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private AetherhavenCoreBootstrap() {}

    public static void register(@Nonnull AetherhavenPlugin plugin) {
        registerPermissions();
        logHstats(plugin);
        plugin.loadAndMigrateConfig();
        plugin.registerModCommonAssetDelivery();
        plugin.registerPlotTokenIconPackets();
        plugin.registerPropIconPackets();
        plugin.registerBlockPaletteIconPackets();
        com.hexvane.aetherhaven.net.AetherhavenInboundPackets.register();

        AetherhavenSharedEntityComponents.register(plugin);
        AetherhavenSharedChunkComponents.register(plugin);

        plugin
            .getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenPlotTokenUnlockPageUse",
                PlotTokenUnlockPageUseInteraction.class,
                PlotTokenUnlockPageUseInteraction.CODEC
            );
        registerGaiaStatueOpenUi(plugin);
        AetherhavenSubpluginAssetCodecs.registerAll(plugin);
        PropsBootstrap.registerAssetCodecs(plugin);
        com.hexvane.aetherhaven.blockpalette.BlockPalettesBootstrap.registerAssetCodecs(plugin);
        DialogueBootstrap.registerLoadHooks(plugin);
        AetherhavenNpcRoleLoader.register(plugin);
        registerTownsfolkRoleValidation(plugin);
        AetherhavenEmbeddedSubpluginPacks.registerEnabled(plugin);

        GameTimeTickListenerRegistry tickRegistry = plugin.getGameTimeTickListenerRegistry();
        AetherhavenGameTimeBridgeSubscriber bridgeSubscriber =
            new AetherhavenGameTimeBridgeSubscriber(plugin, tickRegistry);
        plugin.getGameTimeHub().register(bridgeSubscriber);

        ResourceType<EntityStore, AetherhavenGameTimeCursorResource> cursorType =
            plugin
                .getEntityStoreRegistry()
                .registerResource(AetherhavenGameTimeCursorResource.class, AetherhavenGameTimeCursorResource::new);
        plugin.setGameTimeCursorResourceType(cursorType);
        plugin
            .getEntityStoreRegistry()
            .registerSystem(new AetherhavenGameTimeCoordinatorSystem(plugin.getGameTimeHub(), cursorType));

        PlayerTownJournalState.register(plugin.getEntityStoreRegistry());
        VillagerLocatePlayerComponent.register(plugin.getEntityStoreRegistry());
        PlotLocatePlayerComponent.register(plugin.getEntityStoreRegistry());
        PlayerPlotTokenUnlockState.register(plugin.getEntityStoreRegistry());
        PlayerConstructionFavoritesState.register(plugin.getEntityStoreRegistry());
        BlockEntityScaleMigrated.register(plugin.getEntityStoreRegistry());
        plugin.getEntityStoreRegistry().registerSystem(new EntityRotationRepairSystem.OnHolderAdd());
        plugin.getEntityStoreRegistry().registerSystem(new EntityRotationRepairSystem.OnRefAdded());
        plugin.getEntityStoreRegistry().registerSystem(new BlockEntityScaleRepairSystem());
        plugin.getEntityStoreRegistry().registerSystem(new TownJournalPlayerInitSystem());
        plugin.getEntityStoreRegistry().registerSystem(new VillagerLocatePlayerInitSystem());
        plugin.getEntityStoreRegistry().registerSystem(new PlotLocatePlayerInitSystem());
        plugin.getEntityStoreRegistry().registerSystem(new PlotLocateTrailSystem(plugin));
        plugin.getEntityStoreRegistry().registerSystem(new PlotTokenUnlockPlayerInitSystem());
        plugin.getChunkStoreRegistry().registerSystem(new PlotBlueprintSalvageBenchSystem());
        plugin.getEntityStoreRegistry().registerSystem(new ConstructionFavoritesPlayerInitSystem());
        TerritoryProtectionBootstrap.register(plugin);

        AetherhavenHudRefreshSystem hudRefreshSystem = new AetherhavenHudRefreshSystem(plugin);
        plugin.getEntityStoreRegistry().registerSystem(hudRefreshSystem);
        plugin.getGameTimeHub().register(hudRefreshSystem);

        registerCorePlayerLifecycleHooks(plugin, hudRefreshSystem);

        plugin
            .getEventRegistry()
            .registerGlobal(StartWorldEvent.class, e -> AetherhavenWorldRegistries.bootstrapWorld(e.getWorld(), plugin));
        plugin
            .getEventRegistry()
            .registerGlobal(AddWorldEvent.class, e -> AetherhavenWorldRegistries.bootstrapWorld(e.getWorld(), plugin));
        plugin
            .getEventRegistry()
            .registerGlobal(
                RemoveWorldEvent.class,
                e -> {
                    hudRefreshSystem.clearWorld(e.getWorld().getName());
                    AetherhavenWorldRegistries.unloadWorld(e.getWorld());
                }
            );
        plugin
            .getEventRegistry()
            .registerGlobal(AllWorldsLoadedEvent.class, e -> scheduleTeleporterWarpSanitizeAfterLoad());

        OpenCustomUIInteraction.registerSimple(
            plugin,
            QuestJournalPage.class,
            AetherhavenConstants.PAGE_QUEST_JOURNAL,
            QuestJournalPage::new
        );

        CalendarBootstrap.register(plugin);
        com.hexvane.aetherhaven.villagercosmetic.VillagerCosmeticsBootstrap.register(plugin);

        plugin.initAetherhavenCommand(new AetherhavenCommand());
        CalendarBootstrap.registerCommands(plugin);
        plugin.registerAetherhavenSubcommand(new AetherhavenJournalCommand());
        plugin.registerAetherhavenSubcommand(new AetherhavenHudCommand());
        plugin.registerAetherhavenSubcommand(new AetherhavenCommunityCommand());
        plugin.registerAetherhavenSubcommand(new AetherhavenSupportCommand());
        // After /ah command tree — props register systems and the prop subcommand.
        PropsBootstrap.register(plugin, plugin);
        com.hexvane.aetherhaven.blockpalette.BlockPalettesBootstrap.register(plugin, plugin);
        // After shared components and the /ah command tree — feature packs register systems and subcommands.
        AetherhavenFeatureBootstrap.registerEnabled(plugin);
        LOGGER.atInfo().log("Aetherhaven core v%s setup complete", plugin.getManifest().getVersion().toString());
    }

    private static void registerPermissions() {
        PermissionsModule.registerPermission(
            AetherhavenConstants.PERMISSION_TOWN_TERRITORY_BYPASS,
            HytalePermissionsProvider.GROUP_ADMIN
        );
        PermissionsModule.registerPermission(
            AetherhavenConstants.PERMISSION_TOWN_ADMIN,
            HytalePermissionsProvider.GROUP_ADMIN
        );
        PermissionsModule.registerPermission(
            AetherhavenConstants.PERMISSION_FESTIVAL_SQUARE_BUILD,
            HytalePermissionsProvider.GROUP_ADMIN
        );
        PermissionsModule.registerPermission(
            AetherhavenConstants.PERMISSION_PROP_BREAK,
            HytalePermissionsProvider.GROUP_ADMIN
        );
    }

    /** After {@link NPCPlugin#PRIORITY_LOAD_NPC}; logs whether {@link AetherhavenConstants#NPC_TOWNSFOLK} validated. */
    private static void registerTownsfolkRoleValidation(@Nonnull AetherhavenPlugin plugin) {
        plugin
            .getEventRegistry()
            .register((short) -7, LoadAssetEvent.class, event -> {
                NPCPlugin npc = NPCPlugin.get();
                if (npc == null) {
                    LOGGER.atWarning().log("Townsfolk role check skipped: NPCPlugin not loaded");
                    return;
                }
                String roleName = AetherhavenConstants.NPC_TOWNSFOLK;
                int roleIndex = npc.getIndex(roleName);
                if (roleIndex < 0) {
                    LOGGER.atWarning().log("Townsfolk role %s is not registered after NPC asset load", roleName);
                    return;
                }
                if (npc.tryGetCachedValidRole(roleIndex) == null) {
                    LOGGER.atWarning().log(
                        "Townsfolk role %s (index %s) failed NPC validation; tourist/guild spawns will fail until fixed",
                        roleName,
                        roleIndex
                    );
                    return;
                }
                try {
                    npc.validateSpawnableRole(roleName);
                    LOGGER.atInfo().log("Townsfolk role %s validated for spawn", roleName);
                } catch (RuntimeException e) {
                    LOGGER.atWarning().withCause(e).log("Townsfolk role %s is not spawnable", roleName);
                }
            });
    }

    private static void scheduleTeleporterWarpSanitizeAfterLoad() {
        World world = Universe.get().getDefaultWorld();
        if (world == null) {
            TeleporterWarpSanitizer.sanitizeAllTeleporterWarpsOnStartup();
            return;
        }
        // Defer until after other AllWorldsLoaded handlers (e.g. TeleportPlugin.loadWarps) finish.
        world.execute(TeleporterWarpSanitizer::sanitizeAllTeleporterWarpsOnStartup);
    }

    private static void registerGaiaStatueOpenUi(@Nonnull AetherhavenPlugin plugin) {
        OpenCustomUIInteraction.registerCustomPageSupplier(
            plugin,
            GaiaStatueRevivePage.class,
            AetherhavenConstants.PAGE_GAIA_STATUE,
            (ref, componentAccessor, playerRef, context) -> {
                BlockPosition targetBlock = context.getTargetBlock();
                if (targetBlock == null) {
                    return null;
                }
                Store<EntityStore> store = ref.getStore();
                World world = store.getExternalData().getWorld();
                PlotConstructionBlockResolver.PlotConstructionTarget target =
                    PlotConstructionBlockResolver.resolveForPlotUi(world, targetBlock, GaiaStatueBlock.getComponentType());
                if (target == null) {
                    return null;
                }
                UUID playerUuid = playerRef.getUuid();
                if (playerUuid == null) {
                    return null;
                }
                Ref<ChunkStore> blockRef = target.blockRef();
                GaiaStatueBlock gb = blockRef.getStore().getComponent(blockRef, GaiaStatueBlock.getComponentType());
                TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
                if (gb != null && TownMemberBlockAccess.denyIfNotMember(playerRef, tm, gb.getTownId(), playerUuid)) {
                    return null;
                }
                return new GaiaStatueRevivePage(playerRef, blockRef, target.blockWorldPos());
            }
        );
    }

    private static void logHstats(@Nonnull AetherhavenPlugin plugin) {
        String hstatsModUuid = HstatsBuildMetadata.HSTATS_MOD_UUID;
        String modVersion = plugin.getManifest().getVersion().toString();
        if (!hstatsModUuid.isBlank()) {
            new HStats(hstatsModUuid, modVersion);
            LOGGER.atInfo().log("HStats metrics enabled for Aetherhaven v%s.", modVersion);
        } else {
            LOGGER
                .atInfo()
                .log(
                    "HStats metrics disabled: set AETHERHAVEN_HSTATS_MOD_UUID when building, or Gradle property hstats_mod_uuid (gradle.properties / -Phstats_mod_uuid=...), to your hstats.dev mod UUID at build time to enable."
                );
        }
    }

    private static void registerCorePlayerLifecycleHooks(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull AetherhavenHudRefreshSystem hudRefreshSystem
    ) {
        plugin
            .getEventRegistry()
            .registerGlobal(
                PlayerReadyEvent.class,
                event -> {
                    Player player = event.getPlayer();
                    if (player == null || player.getWorld() == null || player.getReference() == null) {
                        return;
                    }
                    player
                        .getWorld()
                        .execute(
                            () -> {
                                Ref<EntityStore> ref = player.getReference();
                                Store<EntityStore> store = ref.getStore();
                                PlayerRef readyPlayerRef = store.getComponent(ref, PlayerRef.getComponentType());
                                PlayerTownJournalState hudPreferences =
                                    store.getComponent(ref, PlayerTownJournalState.getComponentType());
                                if (
                                    readyPlayerRef != null
                                        && (hudPreferences == null || hudPreferences.isHudEnabled())
                                ) {
                                    AetherhavenHudSupport.obtain(player, readyPlayerRef);
                                }
                                QuestBoardOnlineDawnService.onPlayerReady(ref, store, AetherhavenPlugin.get());
                                UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
                                if (uc != null) {
                                    TouristReconcileService.onTownMemberPlayerReady(
                                        player.getWorld(),
                                        AetherhavenPlugin.get(),
                                        uc.getUuid()
                                    );
                                    TownResidentReconcileService.onTownMemberPlayerReady(
                                        player.getWorld(),
                                        AetherhavenPlugin.get(),
                                        uc.getUuid()
                                    );
                                    RetiredBuiltInPlotMigration.notifyOwnerIfNeeded(
                                        player.getWorld(),
                                        AetherhavenPlugin.get(),
                                        readyPlayerRef,
                                        uc.getUuid()
                                    );
                                    WintertideGiftService.onTownMemberPlayerReady(
                                        player.getWorld(),
                                        store,
                                        uc.getUuid()
                                    );
                                }
                                RtsCommandService.exit(ref, store);
                                IntroQuestPromptService.maybeShow(
                                    AetherhavenPlugin.get(),
                                    ref,
                                    store,
                                    readyPlayerRef,
                                    player.getWorld()
                                );
                                if (readyPlayerRef != null) {
                                    if (plugin.getPropIconPacketAdapter() != null) {
                                        plugin.getPropIconPacketAdapter()
                                            .preloadCatalogIcons(readyPlayerRef, plugin.getPropCatalog());
                                    }
                                    if (plugin.getBlockPaletteIconPacketAdapter() != null) {
                                        plugin.getBlockPaletteIconPacketAdapter()
                                            .preloadCatalogIcons(readyPlayerRef, plugin.getBlockPaletteCatalog());
                                    }
                                }
                            });
                });
        plugin
            .getEventRegistry()
            .registerGlobal(
                PlayerDisconnectEvent.class,
                event -> {
                    Ref<EntityStore> ref = event.getPlayerRef().getReference();
                    if (ref != null && ref.isValid()) {
                        Store<EntityStore> store = ref.getStore();
                        World world = store.getExternalData().getWorld();
                        if (world != null) {
                            world.execute(() -> RtsCommandService.exit(ref, store));
                        }
                    }
                    if (plugin.getPlotTokenIconPacketAdapter() != null) {
                        plugin.getPlotTokenIconPacketAdapter().onPlayerLeave(event.getPlayerRef().getUuid());
                    }
                    if (plugin.getPropIconPacketAdapter() != null) {
                        plugin.getPropIconPacketAdapter().onPlayerLeave(event.getPlayerRef().getUuid());
                    }
                    if (plugin.getBlockPaletteIconPacketAdapter() != null) {
                        plugin.getBlockPaletteIconPacketAdapter().onPlayerLeave(event.getPlayerRef().getUuid());
                    }
                    QuestBoardOnlineDawnService.clearPlayer(event.getPlayerRef().getUuid());
                    hudRefreshSystem.clearPlayer(event.getPlayerRef().getUuid());
                }
            );
    }
}
