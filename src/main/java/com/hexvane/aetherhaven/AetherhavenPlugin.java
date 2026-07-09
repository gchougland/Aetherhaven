package com.hexvane.aetherhaven;

import com.hexvane.aetherhaven.bard.data.BardSongCatalog;
import com.hexvane.aetherhaven.command.AetherhavenCommand;
import com.hexvane.aetherhaven.config.AetherhavenConfigJsonMigration;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.config.PluginConfigMerge;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.community.CommunityCatalogService;
import com.hexvane.aetherhaven.community.CommunityIconRegistry;
import com.hexvane.aetherhaven.community.CommunityMarketplaceSecrets;
import com.hexvane.aetherhaven.construction.PrefabMaterialsCatalog;
import com.hexvane.aetherhaven.construction.prefabmaterials.PrefabMaterialsService;
import com.hexvane.aetherhaven.dialogue.AetherhavenDialogueWorldView;
import com.hexvane.aetherhaven.dialogue.DialogueCatalog;
import com.hexvane.aetherhaven.dialogue.DialogueResolver;
import com.hexvane.aetherhaven.dialogue.DialogueWorldView;
import com.hexvane.aetherhaven.equipment.data.EquipmentProfileCatalog;
import com.hexvane.aetherhaven.jewelry.JewelryInventoryTooltipSync;
import com.hexvane.aetherhaven.jewelry.JewelryNativeTooltipManager;
import com.hexvane.aetherhaven.jewelry.JewelryTooltipPacketAdapter;
import com.hexvane.aetherhaven.jewelry.JewelryVirtualItemRegistry;
import com.hexvane.aetherhaven.pathtool.PathNavViz;
import com.hexvane.aetherhaven.plot.PlotTokenIconPacketAdapter;
import com.hexvane.aetherhaven.plot.PlotTokenVirtualItemRegistry;
import com.hexvane.aetherhaven.plotcreator.CustomBuildingIconAssetRegistry;
import com.hexvane.aetherhaven.plugin.AetherhavenCoreBootstrap;
import com.hexvane.aetherhaven.plugin.AetherhavenFeatureBootstrap;
import com.hexvane.aetherhaven.plugin.DialogueActionRegistry;
import com.hexvane.aetherhaven.plugin.GameTimeTickListenerRegistry;
import com.hexvane.aetherhaven.production.ProductionCatalog;
import com.hexvane.aetherhaven.production.WorkplaceUnlockCatalog;
import com.hexvane.aetherhaven.quest.QuestCatalog;
import com.hexvane.aetherhaven.questboard.QuestBoardCatalog;
import com.hexvane.aetherhaven.reputation.ReputationRewardCatalog;
import com.hexvane.aetherhaven.rts.RtsClientMovementPacketAdapter;
import com.hexvane.aetherhaven.rts.RtsCommandHotbarSlotInboundAdapter;
import com.hexvane.aetherhaven.schedule.VillagerScheduleRegistry;
import com.hexvane.aetherhaven.shopspot.ShopPriceCatalog;
import com.hexvane.aetherhaven.shopspot.ShopPriceFiles;
import com.hexvane.aetherhaven.shopspot.ShopPriceTooltipMessages;
import com.hexvane.aetherhaven.shopspot.ShopPriceTooltipPacketAdapter;
import com.hexvane.aetherhaven.time.AetherhavenGameTimeCursorResource;
import com.hexvane.aetherhaven.time.AetherhavenGameTimeHub;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownNameCatalog;
import com.hexvane.aetherhaven.townsfolk.data.TownsfolkCharacterCatalog;
import com.hexvane.aetherhaven.townsfolk.data.TownsfolkPersonalityCatalog;
import com.hexvane.aetherhaven.villager.data.VillagerDefinitionCatalog;
import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.setup.RequestCommonAssetsRebuild;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.server.core.asset.AssetPackRegisterEvent;
import com.hypixel.hytale.server.core.asset.common.CommonAsset;
import com.hypixel.hytale.server.core.asset.common.CommonAssetModule;
import com.hypixel.hytale.server.core.asset.common.CommonAssetRegistry;
import com.hypixel.hytale.server.core.asset.common.events.SendCommonAssetsEvent;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class AetherhavenPlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Nullable
    private static volatile AetherhavenPlugin instance;

    private final Config<AetherhavenPluginConfig> config = this.withConfig("config", AetherhavenPluginConfig.CODEC);
    private ConstructionCatalog constructionCatalog = ConstructionCatalog.empty();
    private PrefabMaterialsCatalog prefabMaterialsCatalog = PrefabMaterialsCatalog.empty();
    private PrefabMaterialsService prefabMaterialsService = PrefabMaterialsService.fromClassLoader(
        AetherhavenPlugin.class.getClassLoader()
    );
    private DialogueCatalog dialogueCatalog = DialogueCatalog.empty();
    private QuestCatalog questCatalog = QuestCatalog.empty();
    private QuestBoardCatalog questBoardCatalog = QuestBoardCatalog.empty();
    private VillagerScheduleRegistry villagerScheduleRegistry = VillagerScheduleRegistry.empty();
    private VillagerDefinitionCatalog villagerDefinitionCatalog = VillagerDefinitionCatalog.empty();
    private TownsfolkPersonalityCatalog townsfolkPersonalityCatalog = TownsfolkPersonalityCatalog.empty();
    private TownsfolkCharacterCatalog townsfolkCharacterCatalog = TownsfolkCharacterCatalog.empty();
    private EquipmentProfileCatalog equipmentProfileCatalog = EquipmentProfileCatalog.empty();
    private ProductionCatalog productionCatalog = ProductionCatalog.empty();
    private WorkplaceUnlockCatalog workplaceUnlockCatalog = WorkplaceUnlockCatalog.empty();
    private BardSongCatalog bardSongCatalog = BardSongCatalog.empty();
    private final DialogueResolver dialogueResolver = new DialogueResolver();
    private TownNameCatalog townNameCatalog = TownNameCatalog.loadFromClasspath();
    private ScheduledExecutorService constructionScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Aetherhaven-Construction");
        t.setDaemon(true);
        return t;
    });

    private final AetherhavenGameTimeHub gameTimeHub = new AetherhavenGameTimeHub();
    private final GameTimeTickListenerRegistry gameTimeTickListenerRegistry = new GameTimeTickListenerRegistry();
    private final DialogueActionRegistry dialogueActionRegistry = new DialogueActionRegistry();
    /** Filled in {@link #setup}; used by {@link AetherhavenGameTimeCoordinatorSystem}. */
    @Nullable
    private ResourceType<EntityStore, AetherhavenGameTimeCursorResource> gameTimeCursorResourceType;

    @Nullable
    private AetherhavenCommand aetherhavenCommand;

    @Nullable
    private JewelryVirtualItemRegistry jewelryVirtualItemRegistry;

    @Nullable
    private JewelryTooltipPacketAdapter jewelryTooltipPacketAdapter;
    @Nullable
    private ShopPriceTooltipPacketAdapter shopPriceTooltipPacketAdapter;
    @Nullable
    private RtsClientMovementPacketAdapter rtsClientMovementPacketAdapter;
    @Nullable
    private RtsCommandHotbarSlotInboundAdapter rtsCommandHotbarSlotInboundAdapter;

    @Nullable
    private PlotTokenVirtualItemRegistry plotTokenVirtualItemRegistry;

    @Nullable
    private PlotTokenIconPacketAdapter plotTokenIconPacketAdapter;

    private ShopPriceCatalog shopPriceCatalog = ShopPriceCatalog.empty();

    private final CommunityCatalogService communityCatalogService = new CommunityCatalogService(this);

    public AetherhavenPlugin(JavaPluginInit init) {
        super(init);
    }

    @Nullable
    public static AetherhavenPlugin get() {
        return instance;
    }

    @Nonnull
    public AetherhavenGameTimeHub getGameTimeHub() {
        return gameTimeHub;
    }

    @Nonnull
    public GameTimeTickListenerRegistry getGameTimeTickListenerRegistry() {
        return gameTimeTickListenerRegistry;
    }

    @Nonnull
    public DialogueActionRegistry getDialogueActionRegistry() {
        return dialogueActionRegistry;
    }

    public void initAetherhavenCommand(@Nonnull AetherhavenCommand command) {
        this.aetherhavenCommand = command;
    }

    public void registerAetherhavenSubcommand(@Nonnull AbstractCommand command) {
        if (this.aetherhavenCommand == null) {
            throw new IllegalStateException("Aetherhaven command tree not initialized");
        }
        this.aetherhavenCommand.addSubCommand(command);
    }

    public void setGameTimeCursorResourceType(
        @Nonnull ResourceType<EntityStore, AetherhavenGameTimeCursorResource> gameTimeCursorResourceType
    ) {
        this.gameTimeCursorResourceType = gameTimeCursorResourceType;
    }

    public void loadAndMigrateConfig() {
        Path configPath = this.getDataDirectory().resolve("config.json");
        if (Files.exists(configPath)) {
            AetherhavenConfigJsonMigration.migrateIfNeeded(configPath);
            int merged = PluginConfigMerge.appendMissingKeys(configPath, AetherhavenPluginConfig.CODEC);
            if (merged > 0) {
                LOGGER
                    .atInfo()
                    .log("Updated %s: appended %d missing default config key(s) (existing values unchanged).", configPath, merged);
            }
        }
        this.config.get();
        if (!Files.exists(configPath)) {
            this.config.save().join();
            PluginConfigMerge.rewritePrettyJson(configPath);
            LOGGER.atInfo().log("Created default config at %s", configPath);
        }
    }

    public void reloadShopPriceCatalog() {
        this.shopPriceCatalog = ShopPriceFiles.loadCatalog(this);
        ShopPriceTooltipMessages.clearCache();
    }

    @Nullable
    public JewelryTooltipPacketAdapter getJewelryTooltipPacketAdapter() {
        return jewelryTooltipPacketAdapter;
    }

    @Nullable
    public PlotTokenIconPacketAdapter getPlotTokenIconPacketAdapter() {
        return plotTokenIconPacketAdapter;
    }

    @Nonnull
    public Config<AetherhavenPluginConfig> getConfig() {
        return config;
    }

    @Nonnull
    public ConstructionCatalog getConstructionCatalog() {
        return constructionCatalog;
    }

    @Nonnull
    public PrefabMaterialsCatalog getPrefabMaterialsCatalog() {
        return prefabMaterialsCatalog;
    }

    @Nonnull
    public PrefabMaterialsService getPrefabMaterialsService() {
        return prefabMaterialsService;
    }

    @Nonnull
    public DialogueCatalog getDialogueCatalog() {
        return dialogueCatalog;
    }

    @Nonnull
    public QuestCatalog getQuestCatalog() {
        return questCatalog;
    }

    @Nonnull
    public QuestBoardCatalog getQuestBoardCatalog() {
        return questBoardCatalog;
    }

    @Nonnull
    public DialogueResolver getDialogueResolver() {
        return dialogueResolver;
    }

    @Nonnull
    public TownNameCatalog getTownNameCatalog() {
        return townNameCatalog;
    }

    @Nonnull
    public VillagerScheduleRegistry getVillagerScheduleRegistry() {
        return villagerScheduleRegistry;
    }

    @Nonnull
    public VillagerDefinitionCatalog getVillagerDefinitionCatalog() {
        return villagerDefinitionCatalog;
    }

    @Nonnull
    public TownsfolkPersonalityCatalog getTownsfolkPersonalityCatalog() {
        return townsfolkPersonalityCatalog;
    }

    @Nonnull
    public TownsfolkCharacterCatalog getTownsfolkCharacterCatalog() {
        return townsfolkCharacterCatalog;
    }

    @Nonnull
    public EquipmentProfileCatalog getEquipmentProfileCatalog() {
        return equipmentProfileCatalog;
    }

    @Nonnull
    public ProductionCatalog getProductionCatalog() {
        return productionCatalog;
    }

    @Nonnull
    public WorkplaceUnlockCatalog getWorkplaceUnlockCatalog() {
        return workplaceUnlockCatalog;
    }

    @Nonnull
    public BardSongCatalog getBardSongCatalog() {
        return bardSongCatalog;
    }

    @Nonnull
    public ShopPriceCatalog getShopPriceCatalog() {
        return shopPriceCatalog;
    }

    @Nonnull
    public CommunityCatalogService getCommunityCatalogService() {
        return communityCatalogService;
    }

    @Nonnull
    public DialogueWorldView createDialogueWorldView(@Nonnull World world) {
        return new AetherhavenDialogueWorldView(world, this);
    }

    @Nonnull
    public DialogueWorldView createDialogueWorldView(@Nonnull World world, @Nullable Ref<EntityStore> npcRef) {
        return new AetherhavenDialogueWorldView(world, this, npcRef);
    }

    /**
     * Schedules a task on the world's thread after a delay. Used so construction batches spread across time.
     */
    public void scheduleOnWorld(@Nonnull com.hypixel.hytale.server.core.universe.world.World world, @Nonnull Runnable worldTask, long delayMs) {
        long delay = Math.max(1L, delayMs);
        constructionScheduler.schedule(() -> world.execute(worldTask), delay, TimeUnit.MILLISECONDS);
    }

    @Override
    @SuppressWarnings("removal")
    protected void setup() {
        instance = this;
        AetherhavenCoreBootstrap.register(this);
    }

    @Override
    protected void start() {
        if (this.aetherhavenCommand != null) {
            this.getCommandRegistry().registerCommand(this.aetherhavenCommand);
        }
        // Mod packs register in setup0() before LoadAssetEvent, so AssetPackRegisterEvent is not fired then;
        // Asset Editor only sees packs from that event or its early setup() pass. Re-dispatch after assets load.
        if (this.getManifest().includesAssetPack()) {
            String packId = new PluginIdentifier(this.getManifest()).toString();
            AssetPack pack = AssetModule.get().getAssetPack(packId);
            if (pack != null) {
                HytaleServer.get()
                    .getEventBus()
                    .<Void, AssetPackRegisterEvent>dispatchFor(AssetPackRegisterEvent.class)
                    .dispatch(new AssetPackRegisterEvent(pack));
                CommonAssetModule commonAssets = CommonAssetModule.get();
                if (commonAssets != null) {
                    commonAssets.loadCommonAssets(pack, System.nanoTime());
                    if (Universe.get().getPlayerCount() > 0) {
                        Universe.get().broadcastPacketNoCache(new RequestCommonAssetsRebuild());
                    }
                }
            } else {
                LOGGER.atWarning().log("Asset pack %s not found in AssetModule; Asset Editor may not list this mod", packId);
            }
        }
        this.config.get();
        this.reloadAetherhavenAssetCatalogs();
        this.getEventRegistry().register(AssetPackRegisterEvent.class, e -> this.reloadAetherhavenAssetCatalogs());
        AetherhavenFeatureBootstrap.startEnabled(this);
        if (this.config.get().getCommunityMarketplace().isEnabled() && !CommunityMarketplaceSecrets.hasApiKey()) {
            LOGGER
                .atWarning()
                .log(
                    "Community marketplace is enabled but %s is not set; browse/download works but in-game submissions are disabled.",
                    CommunityMarketplaceSecrets.API_KEY_ENV
                );
        }
        LOGGER.atInfo().log("Aetherhaven constructions loaded: %s", this.constructionCatalog.ids());
    }

    /**
     * Reloads {@code config.json} from disk and refreshes JSON-backed asset catalogs (constructions, dialogue, quests, villager definitions, villager schedules).
     */
    public void reloadConfigsAndAssetCatalogs() {
        this.config.load().join();
        this.reloadAetherhavenAssetCatalogs();
        this.shopPriceCatalog = ShopPriceFiles.loadCatalog(this);
        ShopPriceTooltipMessages.clearCache();
    }

    /**
     * Ensures joining clients receive this mod's {@code Common/} blobs (icons, blockymodels, UI). Vanilla setup only
     * ships assets the client claims to have cached; a stale or empty {@code CachedAssets} folder skips mod files and
     * every {@code Icons/ItemsGenerated/*.png} lookup fails even though the server loaded them from {@code build/resources/main}.
     */
    public void registerModCommonAssetDelivery() {
        if (!this.getManifest().includesAssetPack()) {
            return;
        }
        this.getEventRegistry()
            .registerAsyncGlobal(
                EventPriority.LAST,
                SendCommonAssetsEvent.class,
                future -> future.thenApply(this::pushModCommonAssetsToJoiningClient)
            );
    }

    @Nonnull
    private SendCommonAssetsEvent pushModCommonAssetsToJoiningClient(@Nonnull SendCommonAssetsEvent event) {
        CommonAssetModule module = CommonAssetModule.get();
        if (module == null) {
            return event;
        }
        CustomBuildingIconAssetRegistry.syncFromDataDirectory(this);
        CommunityIconRegistry.syncFromCommunityDirectory(this);
        String packId = new PluginIdentifier(this.getManifest()).toString();
        List<CommonAsset> packAssets = CommonAssetRegistry.getCommonAssetsStartingWith(packId, "");
        if (packAssets.isEmpty()) {
            LOGGER
                .atWarning()
                .log(
                    "No common assets registered for pack %s — client item icons and models will be missing. Rebuild with processResources and restart the server.",
                    packId
                );
            return event;
        }
        module.sendAssetsToPlayer(event.getPacketHandler(), packAssets, false);
        return event;
    }

    private void reloadAetherhavenAssetCatalogs() {
        ClassLoader cl = this.getClassLoader();
        this.villagerDefinitionCatalog = VillagerDefinitionCatalog.loadFromAssetPacksOrClasspath(cl);
        this.townsfolkPersonalityCatalog = TownsfolkPersonalityCatalog.loadFromAssetPacksOrClasspath(cl);
        this.townsfolkCharacterCatalog =
            TownsfolkCharacterCatalog.loadFromAssetPacksOrClasspath(cl, this.townsfolkPersonalityCatalog);
        this.equipmentProfileCatalog = EquipmentProfileCatalog.loadFromAssetPacksOrClasspath(cl);
        ReputationRewardCatalog.refreshFromVillagerCatalog(this.villagerDefinitionCatalog);
        this.dialogueResolver.reloadFromVillagerCatalog(this.villagerDefinitionCatalog);
        Path customData = this.getDataDirectory();
        this.constructionCatalog = ConstructionCatalog.loadFromAssetPacksOrClasspath(cl, customData);
        this.prefabMaterialsService.generateAllForCatalog(this.constructionCatalog, customData);
        this.prefabMaterialsCatalog = PrefabMaterialsCatalog.loadFromAssetPacksOrClasspath(cl, customData);
        this.dialogueCatalog = DialogueCatalog.loadFromAssetPacksOrClasspath(cl);
        this.questCatalog = QuestCatalog.loadFromAssetPacksOrClasspath(cl);
        this.questBoardCatalog = QuestBoardCatalog.loadFromAssetPacksOrClasspath(cl);
        this.villagerScheduleRegistry = VillagerScheduleRegistry.loadFromAssetPacksOrClasspath(cl);
        this.townNameCatalog = TownNameCatalog.loadFromClasspath();
        this.productionCatalog = ProductionCatalog.loadFromClasspath(cl);
        this.workplaceUnlockCatalog = WorkplaceUnlockCatalog.loadFromClasspath(cl);
        this.bardSongCatalog = BardSongCatalog.loadFromAssetPacksOrClasspath(cl);
        LOGGER.atInfo().log(
            "Aetherhaven asset catalogs reloaded (constructions=%s, dialogue=%s, quests=%s, villagerDefs=%s, villagerSchedules=loaded)",
            this.constructionCatalog.ids(),
            this.dialogueCatalog.all().keySet(),
            this.questCatalog.all().keySet(),
            this.villagerDefinitionCatalog.allByNpcRoleId().keySet()
        );
        CustomBuildingIconAssetRegistry.syncFromDataDirectory(this);
    }

    public void registerJewelryNativeTooltipHooks() {
        this.getEventRegistry()
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
                                PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
                                if (playerRef == null) {
                                    return;
                                }
                                JewelryInventoryTooltipSync.syncPlayerInventory(ref, store);
                                JewelryNativeTooltipManager.refreshPlayer(playerRef);
                            });
                });
        this.getEventRegistry()
            .registerGlobal(
                PlayerDisconnectEvent.class,
                event -> {
                    if (this.jewelryTooltipPacketAdapter != null) {
                        this.jewelryTooltipPacketAdapter.onPlayerLeave(event.getPlayerRef().getUuid());
                    }
                });
        LOGGER.atInfo().log("Jewelry tooltips use native ItemDisplay metadata (per-stack descriptions)");
    }

    public void registerShopPriceTooltipPackets() {
        this.shopPriceTooltipPacketAdapter = new ShopPriceTooltipPacketAdapter();
        this.shopPriceTooltipPacketAdapter.register();
    }

    public void registerJewelryRarityBorderPackets() {
        this.jewelryVirtualItemRegistry = new JewelryVirtualItemRegistry();
        this.jewelryTooltipPacketAdapter = new JewelryTooltipPacketAdapter(this.jewelryVirtualItemRegistry);
        this.jewelryTooltipPacketAdapter.register();
    }

    public void registerPlotTokenIconPackets() {
        this.plotTokenVirtualItemRegistry = new PlotTokenVirtualItemRegistry();
        this.plotTokenIconPacketAdapter = new PlotTokenIconPacketAdapter(this.plotTokenVirtualItemRegistry);
        this.plotTokenIconPacketAdapter.register();
    }

    public void registerRtsClientMovementPacketAdapter() {
        this.rtsClientMovementPacketAdapter = new RtsClientMovementPacketAdapter();
        this.rtsClientMovementPacketAdapter.register();
    }

    public void registerRtsCommandHotbarSlotInboundAdapter() {
        this.rtsCommandHotbarSlotInboundAdapter = new RtsCommandHotbarSlotInboundAdapter();
        this.rtsCommandHotbarSlotInboundAdapter.register();
    }

    @Override
    protected void shutdown() {
        AetherhavenFeatureBootstrap.shutdownEnabled();
        if (this.shopPriceTooltipPacketAdapter != null) {
            this.shopPriceTooltipPacketAdapter.deregister();
            this.shopPriceTooltipPacketAdapter = null;
        }
        if (this.jewelryTooltipPacketAdapter != null) {
            this.jewelryTooltipPacketAdapter.deregister();
            this.jewelryTooltipPacketAdapter = null;
        }
        if (this.plotTokenIconPacketAdapter != null) {
            this.plotTokenIconPacketAdapter.deregister();
            this.plotTokenIconPacketAdapter = null;
        }
        if (this.rtsClientMovementPacketAdapter != null) {
            this.rtsClientMovementPacketAdapter.deregister();
            this.rtsClientMovementPacketAdapter = null;
        }
        if (this.rtsCommandHotbarSlotInboundAdapter != null) {
            this.rtsCommandHotbarSlotInboundAdapter.deregister();
            this.rtsCommandHotbarSlotInboundAdapter = null;
        }
        this.jewelryVirtualItemRegistry = null;
        this.plotTokenVirtualItemRegistry = null;
        PathNavViz.shutdown();
        instance = null;
        AetherhavenWorldRegistries.saveAll();
        this.constructionScheduler.shutdown();
        try {
            if (!this.constructionScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                this.constructionScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            this.constructionScheduler.shutdownNow();
        }
    }
}
