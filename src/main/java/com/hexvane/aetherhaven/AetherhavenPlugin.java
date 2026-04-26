package com.hexvane.aetherhaven;

import com.hexvane.aetherhaven.charter.CharterPlaceEventSystem;
import com.hexvane.aetherhaven.command.AetherhavenCommand;
import com.hexvane.aetherhaven.config.AetherhavenConfigJsonMigration;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.config.PluginConfigMerge;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.dialogue.AetherhavenDialogueWorldView;
import com.hexvane.aetherhaven.dialogue.DialogueCatalog;
import com.hexvane.aetherhaven.quest.QuestCatalog;
import com.hexvane.aetherhaven.dialogue.DialogueResolver;
import com.hexvane.aetherhaven.dialogue.DialogueWorldView;
import com.hexvane.aetherhaven.npc.BuilderActionOpenAetherhavenDialogue;
import com.hexvane.aetherhaven.npc.movement.BuilderBodyMotionWanderInRectGroundPreference;
import com.hexvane.aetherhaven.placement.PlotConstructionBlockResolver;
import com.hexvane.aetherhaven.placement.PlotPlacementOpenHelper;
import com.hexvane.aetherhaven.plot.CharterBlock;
import com.hexvane.aetherhaven.plot.ManagementBlock;
import com.hexvane.aetherhaven.plot.PlotSignBlock;
import com.hexvane.aetherhaven.plot.SprinklerBlock;
import com.hexvane.aetherhaven.plot.FounderMonumentBlock;
import com.hexvane.aetherhaven.plot.GaiaStatueBlock;
import com.hexvane.aetherhaven.plot.TreasuryBlock;
import com.hexvane.aetherhaven.poi.tool.PoiDebugLabelEntity;
import com.hexvane.aetherhaven.poi.tool.PoiToolMoveInteraction;
import com.hexvane.aetherhaven.poi.tool.PoiToolPlayerComponent;
import com.hexvane.aetherhaven.purification.PurificationPowderPlayerComponent;
import com.hexvane.aetherhaven.poi.tool.PoiToolSetTargetInteraction;
import com.hexvane.aetherhaven.poi.tool.PoiToolSelectInteraction;
import com.hexvane.aetherhaven.poi.tool.PoiToolVisualizationSystem;
import com.hexvane.aetherhaven.purification.PurificationPowderUseInteraction;
import com.hexvane.aetherhaven.purification.PurificationPowderPlayerRemoveSystem;
import com.hexvane.aetherhaven.purification.PurificationPowderVisualizationSystem;
import com.hexvane.aetherhaven.purification.PurificationPreviewEntity;
import com.hexvane.aetherhaven.autonomy.VillagerAutonomyDebugTag;
import com.hexvane.aetherhaven.autonomy.VillagerAutonomyState;
import com.hexvane.aetherhaven.autonomy.VillagerAutonomySystem;
import com.hexvane.aetherhaven.autonomy.VillagerBlockMountSafetySystem;
import com.hexvane.aetherhaven.reputation.ReputationRewardCatalog;
import com.hexvane.aetherhaven.schedule.VillagerScheduleRegistry;
import com.hexvane.aetherhaven.schedule.VillagerScheduleTickState;
import com.hexvane.aetherhaven.villager.AetherhavenVillagerHandle;
import com.hexvane.aetherhaven.villager.data.VillagerDefinitionCatalog;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import com.hexvane.aetherhaven.villager.VillagerNeedsDecaySystem;
import com.hexvane.aetherhaven.economy.TreasuryBreakBlockSystem;
import com.hexvane.aetherhaven.geode.GeodeLootFiles;
import com.hexvane.aetherhaven.jewelry.TooltipBridge;
import com.hexvane.aetherhaven.jewelry.JewelryGemTraits;
import com.hexvane.aetherhaven.jewelry.JewelryPlayerInitSystem;
import com.hexvane.aetherhaven.jewelry.JewelryRolling;
import com.hexvane.aetherhaven.jewelry.LootChestBonusInjectSystem;
import com.hexvane.aetherhaven.jewelry.LootrPerPlayerLootInjectSystem;
import com.hexvane.aetherhaven.jewelry.LootrChestProcessedPlayers;
import com.hexvane.aetherhaven.jewelry.LootChestWorldLootMarkSystem;
import com.hexvane.aetherhaven.jewelry.LootChestWorldLootPending;
import com.hexvane.aetherhaven.jewelry.JewelryStatSyncSystem;
import com.hexvane.aetherhaven.jewelry.PlayerJewelryLoadout;
import com.hexvane.aetherhaven.geode.GeodeOreBreakSystem;
import com.hexvane.aetherhaven.monument.FounderMonumentBreakSystem;
import com.hexvane.aetherhaven.monument.FounderMonumentPlaceSystem;
import com.hexvane.aetherhaven.monument.FounderMonumentStatueRestoreSystem;
import com.hexvane.aetherhaven.monument.FounderMonumentStatueSkin;
import com.hexvane.aetherhaven.farming.SprinklerActivateInteraction;
import com.hexvane.aetherhaven.time.AetherhavenGameTimeBridgeSubscriber;
import com.hexvane.aetherhaven.time.AetherhavenGameTimeCoordinatorSystem;
import com.hexvane.aetherhaven.time.AetherhavenGameTimeCursorResource;
import com.hexvane.aetherhaven.time.AetherhavenGameTimeHub;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownNameCatalog;
import com.hexvane.aetherhaven.ui.CharterAmendmentsPage;
import com.hexvane.aetherhaven.ui.FeastPage;
import com.hexvane.aetherhaven.ui.CharterTownPage;
import com.hexvane.aetherhaven.ui.PlotConstructionPage;
import com.hexvane.aetherhaven.ui.PlotPlacementPage;
import com.hexvane.aetherhaven.ui.PlotSignAdminPage;
import com.hexvane.aetherhaven.ui.GeodeOpenPage;
import com.hexvane.aetherhaven.ui.OpenHandMirrorUiInteraction;
import com.hexvane.aetherhaven.ui.JewelryAppraisalPage;
import com.hexvane.aetherhaven.ui.JewelryCraftingPage;
import com.hexvane.aetherhaven.ui.QuestJournalPage;
import com.hexvane.aetherhaven.ui.GaiaStatueRevivePage;
import com.hexvane.aetherhaven.ui.TreasuryPage;
import com.hexvane.aetherhaven.ui.VillagerNeedsOverviewPage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.events.AddWorldEvent;
import com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent;
import com.hypixel.hytale.server.core.universe.world.events.StartWorldEvent;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.server.core.asset.AssetPackRegisterEvent;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
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
    private DialogueCatalog dialogueCatalog = DialogueCatalog.empty();
    private QuestCatalog questCatalog = QuestCatalog.empty();
    private VillagerScheduleRegistry villagerScheduleRegistry = VillagerScheduleRegistry.empty();
    private VillagerDefinitionCatalog villagerDefinitionCatalog = VillagerDefinitionCatalog.empty();
    private final DialogueResolver dialogueResolver = new DialogueResolver();
    private TownNameCatalog townNameCatalog = TownNameCatalog.loadFromClasspath();
    private ScheduledExecutorService constructionScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Aetherhaven-Construction");
        t.setDaemon(true);
        return t;
    });

    private final AetherhavenGameTimeHub gameTimeHub = new AetherhavenGameTimeHub();
    private final AetherhavenGameTimeBridgeSubscriber gameTimeBridgeSubscriber = new AetherhavenGameTimeBridgeSubscriber(this);
    /** Filled in {@link #setup}; used by {@link AetherhavenGameTimeCoordinatorSystem}. */
    @Nullable
    private ResourceType<EntityStore, AetherhavenGameTimeCursorResource> gameTimeCursorResourceType;

    public AetherhavenPlugin(JavaPluginInit init) {
        super(init);
    }

    @Nullable
    public static AetherhavenPlugin get() {
        return instance;
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
    public DialogueCatalog getDialogueCatalog() {
        return dialogueCatalog;
    }

    @Nonnull
    public QuestCatalog getQuestCatalog() {
        return questCatalog;
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
        JewelryRolling.bind(() -> this.getConfig().get());
        if (!Files.exists(configPath)) {
            this.config.save().join();
            LOGGER.atInfo().log("Created default config at %s", configPath);
        }
        GeodeLootFiles.ensureDefaultLootFile(this);
        TooltipBridge.register();

        this.gameTimeCursorResourceType =
            this.getEntityStoreRegistry()
                .registerResource(AetherhavenGameTimeCursorResource.class, AetherhavenGameTimeCursorResource::new);
        this.gameTimeHub.register(this.gameTimeBridgeSubscriber);
        this.getEntityStoreRegistry()
            .registerSystem(new AetherhavenGameTimeCoordinatorSystem(this.gameTimeHub, this.gameTimeCursorResourceType));

        PlotSignBlock.register(this.getChunkStoreRegistry());
        ManagementBlock.register(this.getChunkStoreRegistry());
        CharterBlock.register(this.getChunkStoreRegistry());
        TreasuryBlock.register(this.getChunkStoreRegistry());
        GaiaStatueBlock.register(this.getChunkStoreRegistry());
        SprinklerBlock.register(this.getChunkStoreRegistry());
        FounderMonumentBlock.register(this.getChunkStoreRegistry());
        FounderMonumentStatueSkin.register(this.getEntityStoreRegistry());

        VillagerNeeds.register(this.getEntityStoreRegistry());
        PlayerJewelryLoadout.register(this.getEntityStoreRegistry());
        this.getEntityStoreRegistry().registerSystem(new JewelryPlayerInitSystem());
        this.getEntityStoreRegistry().registerSystem(new JewelryStatSyncSystem());
        LootChestWorldLootPending.register(this.getChunkStoreRegistry());
        LootrChestProcessedPlayers.register(this.getChunkStoreRegistry());
        this.getChunkStoreRegistry().registerSystem(new LootChestWorldLootMarkSystem());
        this.getChunkStoreRegistry().registerSystem(new LootChestBonusInjectSystem(this));
        LootrPerPlayerLootInjectSystem lootrCompat = LootrPerPlayerLootInjectSystem.createIfAvailable(this);
        if (lootrCompat != null) {
            this.getChunkStoreRegistry().registerSystem(lootrCompat);
        }
        AetherhavenVillagerHandle.register(this.getEntityStoreRegistry());
        TownVillagerBinding.register(this.getEntityStoreRegistry());
        VillagerAutonomyState.register(this.getEntityStoreRegistry());
        VillagerScheduleTickState.register(this.getEntityStoreRegistry());
        VillagerAutonomyDebugTag.register(this.getEntityStoreRegistry());
        PoiToolPlayerComponent.register(this.getEntityStoreRegistry());
        PurificationPowderPlayerComponent.register(this.getEntityStoreRegistry());
        this.getEntityRegistry()
            .registerEntity(
                "AetherhavenPoiDebugLabel",
                PoiDebugLabelEntity.class,
                world -> {
                    PoiDebugLabelEntity e = new PoiDebugLabelEntity();
                    // Entity.clone() invokes the factory with null world; match deprecated Entity(World) behavior.
                    if (world != null) {
                        e.loadIntoWorld(world);
                    }
                    return e;
                },
                PoiDebugLabelEntity.CODEC
            );
        this.getEntityRegistry()
            .registerEntity(
                "AetherhavenPurificationPreview",
                PurificationPreviewEntity.class,
                world -> {
                    PurificationPreviewEntity e = new PurificationPreviewEntity();
                    if (world != null) {
                        e.loadIntoWorld(world);
                    }
                    return e;
                },
                PurificationPreviewEntity.CODEC
            );
        this.getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenPoiToolSelect", PoiToolSelectInteraction.class, PoiToolSelectInteraction.CODEC);
        this.getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenPoiToolMove", PoiToolMoveInteraction.class, PoiToolMoveInteraction.CODEC);
        this.getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenPoiToolSetTarget",
                PoiToolSetTargetInteraction.class,
                PoiToolSetTargetInteraction.CODEC
            );
        this.getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenSprinklerActivate",
                SprinklerActivateInteraction.class,
                SprinklerActivateInteraction.CODEC
            );
        this.getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenOpenHandMirror",
                OpenHandMirrorUiInteraction.class,
                OpenHandMirrorUiInteraction.CODEC
            );
        this.getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenPurificationPowderUse",
                PurificationPowderUseInteraction.class,
                PurificationPowderUseInteraction.CODEC
            );
        this.getEntityStoreRegistry().registerSystem(new VillagerNeedsDecaySystem(this));
        this.getEntityStoreRegistry().registerSystem(new VillagerBlockMountSafetySystem(this));
        this.getEntityStoreRegistry().registerSystem(new VillagerAutonomySystem(this));
        this.getEntityStoreRegistry().registerSystem(new CharterPlaceEventSystem(this));
        this.getEntityStoreRegistry().registerSystem(new TreasuryBreakBlockSystem(this));
        this.getEntityStoreRegistry().registerSystem(new GeodeOreBreakSystem(this));
        this.getEntityStoreRegistry().registerSystem(new FounderMonumentPlaceSystem(this));
        this.getEntityStoreRegistry().registerSystem(new FounderMonumentStatueRestoreSystem());
        this.getEntityStoreRegistry().registerSystem(new FounderMonumentBreakSystem(this));
        this.getEntityStoreRegistry().registerSystem(new PoiToolVisualizationSystem(this));
        this.getEntityStoreRegistry().registerSystem(new PurificationPowderVisualizationSystem(this));
        this.getEntityStoreRegistry().registerSystem(new PurificationPowderPlayerRemoveSystem());

        this.getEventRegistry()
            .registerGlobal(StartWorldEvent.class, e -> AetherhavenWorldRegistries.bootstrapWorld(e.getWorld(), this));
        this.getEventRegistry()
            .registerGlobal(AddWorldEvent.class, e -> AetherhavenWorldRegistries.bootstrapWorld(e.getWorld(), this));
        this.getEventRegistry().registerGlobal(RemoveWorldEvent.class, e -> AetherhavenWorldRegistries.unloadWorld(e.getWorld()));

        OpenCustomUIInteraction.registerCustomPageSupplier(
            this,
            PlotConstructionPage.class,
            AetherhavenConstants.PAGE_PLOT_CONSTRUCTION,
            (ref, componentAccessor, playerRef, context) -> {
                BlockPosition targetBlock = context.getTargetBlock();
                if (targetBlock == null) {
                    return null;
                }
                Store<EntityStore> store = ref.getStore();
                World world = store.getExternalData().getWorld();
                PlotConstructionBlockResolver.PlotConstructionTarget target =
                    PlotConstructionBlockResolver.resolveForPlotUi(world, targetBlock, PlotSignBlock.getComponentType());
                if (target == null) {
                    return null;
                }
                Ref<ChunkStore> blockRef = target.blockRef();
                Vector3i blockWorld = target.blockWorldPos();
                return new PlotConstructionPage(playerRef, blockRef, blockWorld, false);
            }
        );
        OpenCustomUIInteraction.registerCustomPageSupplier(
            this,
            PlotConstructionPage.class,
            AetherhavenConstants.PAGE_PLOT_MANAGEMENT,
            (ref, componentAccessor, playerRef, context) -> {
                BlockPosition targetBlock = context.getTargetBlock();
                if (targetBlock == null) {
                    return null;
                }
                Store<EntityStore> store = ref.getStore();
                World world = store.getExternalData().getWorld();
                PlotConstructionBlockResolver.PlotConstructionTarget target =
                    PlotConstructionBlockResolver.resolveForPlotUi(world, targetBlock, ManagementBlock.getComponentType());
                if (target == null) {
                    return null;
                }
                Ref<ChunkStore> blockRef = target.blockRef();
                Vector3i blockWorld = target.blockWorldPos();
                return new PlotConstructionPage(playerRef, blockRef, blockWorld, true);
            }
        );
        OpenCustomUIInteraction.registerCustomPageSupplier(
            this,
            TreasuryPage.class,
            AetherhavenConstants.PAGE_TREASURY,
            (ref, componentAccessor, playerRef, context) -> {
                BlockPosition targetBlock = context.getTargetBlock();
                if (targetBlock == null) {
                    return null;
                }
                Store<EntityStore> store = ref.getStore();
                World world = store.getExternalData().getWorld();
                PlotConstructionBlockResolver.PlotConstructionTarget target =
                    PlotConstructionBlockResolver.resolveForPlotUi(world, targetBlock, TreasuryBlock.getComponentType());
                if (target == null) {
                    return null;
                }
                Ref<ChunkStore> blockRef = target.blockRef();
                return new TreasuryPage(playerRef, blockRef);
            }
        );
        OpenCustomUIInteraction.registerCustomPageSupplier(
            this,
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
                return new GaiaStatueRevivePage(playerRef, target.blockRef(), target.blockWorldPos());
            }
        );
        OpenCustomUIInteraction.registerCustomPageSupplier(
            this,
            VillagerNeedsOverviewPage.class,
            AetherhavenConstants.PAGE_VILLAGER_NEEDS,
            (ref, componentAccessor, playerRef, context) -> {
                BlockPosition targetBlock = context.getTargetBlock();
                if (targetBlock == null) {
                    return null;
                }
                Store<EntityStore> store = ref.getStore();
                World world = store.getExternalData().getWorld();
                PlotConstructionBlockResolver.PlotConstructionTarget target =
                    PlotConstructionBlockResolver.resolveForPlotUi(world, targetBlock, ManagementBlock.getComponentType());
                if (target == null) {
                    return null;
                }
                Ref<ChunkStore> blockRef = target.blockRef();
                Store<ChunkStore> cs = blockRef.getStore();
                ManagementBlock mb = cs.getComponent(blockRef, ManagementBlock.getComponentType());
                if (mb == null || mb.getTownId().isBlank()) {
                    return null;
                }
                try {
                    UUID townUuid = UUID.fromString(mb.getTownId().trim());
                    return new VillagerNeedsOverviewPage(playerRef, townUuid);
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
        );
        OpenCustomUIInteraction.registerCustomPageSupplier(
            this,
            CharterTownPage.class,
            AetherhavenConstants.PAGE_CHARTER_TOWN,
            (ref, componentAccessor, playerRef, context) -> {
                BlockPosition targetBlock = context.getTargetBlock();
                if (targetBlock == null) {
                    return null;
                }
                Store<EntityStore> store = ref.getStore();
                World world = store.getExternalData().getWorld();
                WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(targetBlock.x, targetBlock.z));
                if (chunk == null) {
                    return null;
                }
                BlockPosition base = world.getBaseBlock(targetBlock);
                Ref<ChunkStore> blockRef = chunk.getBlockComponentEntity(base.x, base.y, base.z);
                if (blockRef == null || blockRef.getStore().getComponent(blockRef, CharterBlock.getComponentType()) == null) {
                    return null;
                }
                return new CharterTownPage(playerRef, blockRef);
            }
        );
        OpenCustomUIInteraction.registerCustomPageSupplier(
            this,
            PlotPlacementPage.class,
            AetherhavenConstants.PAGE_PLOT_PLACEMENT,
            PlotPlacementOpenHelper::tryOpen
        );
        OpenCustomUIInteraction.registerCustomPageSupplier(
            this,
            CharterAmendmentsPage.class,
            AetherhavenConstants.PAGE_CHARTER_AMENDMENTS,
            (ref, componentAccessor, playerRef, context) -> {
                BlockPosition targetBlock = context.getTargetBlock();
                if (targetBlock == null) {
                    return null;
                }
                Store<EntityStore> store = ref.getStore();
                World world = store.getExternalData().getWorld();
                BlockPosition base = world.getBaseBlock(targetBlock);
                BlockType bt = world.getBlockType(base.x, base.y, base.z);
                if (bt == null || bt == BlockType.EMPTY
                    || !AetherhavenConstants.ITEM_CHARTER_AMENDMENTS_TABLE.equals(bt.getId())) {
                    return null;
                }
                return new CharterAmendmentsPage(playerRef);
            }
        );
        OpenCustomUIInteraction.registerCustomPageSupplier(
            this,
            FeastPage.class,
            AetherhavenConstants.PAGE_FEASTS,
            (ref, componentAccessor, playerRef, context) -> {
                BlockPosition targetBlock = context.getTargetBlock();
                if (targetBlock == null) {
                    return null;
                }
                Store<EntityStore> store = ref.getStore();
                World world = store.getExternalData().getWorld();
                BlockPosition base = world.getBaseBlock(targetBlock);
                BlockType bt = world.getBlockType(base.x, base.y, base.z);
                if (bt == null || bt == BlockType.EMPTY
                    || !AetherhavenConstants.ITEM_BANQUET_TABLE.equals(bt.getId())) {
                    return null;
                }
                return new FeastPage(playerRef, base.x, base.y, base.z);
            }
        );
        OpenCustomUIInteraction.registerSimple(this, QuestJournalPage.class, AetherhavenConstants.PAGE_QUEST_JOURNAL, QuestJournalPage::new);
        OpenCustomUIInteraction.registerSimple(
            this,
            JewelryAppraisalPage.class,
            AetherhavenConstants.PAGE_JEWELRY_APPRAISAL_BENCH,
            pr -> new JewelryAppraisalPage(pr, false)
        );
        OpenCustomUIInteraction.registerSimple(
            this,
            JewelryCraftingPage.class,
            AetherhavenConstants.PAGE_JEWELRY_CRAFTING_BENCH,
            JewelryCraftingPage::new
        );
        OpenCustomUIInteraction.registerSimple(
            this,
            GeodeOpenPage.class,
            AetherhavenConstants.PAGE_GEODE_ANVIL,
            pr -> new GeodeOpenPage(pr, false)
        );
        OpenCustomUIInteraction.registerSimple(
            this,
            PlotSignAdminPage.class,
            AetherhavenConstants.PAGE_PLOT_SIGN_ADMIN,
            PlotSignAdminPage::new
        );
        NPCPlugin npc = NPCPlugin.get();
        if (npc != null) {
            npc.registerCoreComponentType("OpenAetherhavenDialogue", BuilderActionOpenAetherhavenDialogue::new);
            npc.registerCoreComponentType("WanderInRectGroundPreference", BuilderBodyMotionWanderInRectGroundPreference::new);
            LOGGER.atInfo().log("Registered NPC action OpenAetherhavenDialogue and body motion WanderInRectGroundPreference");
        } else {
            LOGGER.atWarning().log("NPCPlugin not loaded; OpenAetherhavenDialogue action unavailable");
        }
        this.getCommandRegistry().registerCommand(new AetherhavenCommand());
        LOGGER.atInfo().log("Aetherhaven v%s loaded", this.getManifest().getVersion().toString());
    }

    @Override
    protected void start() {
        JewelryGemTraits.validateStatIdsAtStartup();
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
            } else {
                LOGGER.atWarning().log("Asset pack %s not found in AssetModule; Asset Editor may not list this mod", packId);
            }
        }
        this.config.get();
        this.reloadAetherhavenAssetCatalogs();
        this.getEventRegistry().register(AssetPackRegisterEvent.class, e -> this.reloadAetherhavenAssetCatalogs());
        LOGGER.atInfo().log("Aetherhaven constructions loaded: %s", this.constructionCatalog.ids());
    }

    /**
     * Reloads {@code config.json} from disk and refreshes JSON-backed asset catalogs (constructions, dialogue, quests, villager definitions, villager schedules).
     */
    public void reloadConfigsAndAssetCatalogs() {
        this.config.load().join();
        this.reloadAetherhavenAssetCatalogs();
    }

    private void reloadAetherhavenAssetCatalogs() {
        ClassLoader cl = this.getClassLoader();
        this.villagerDefinitionCatalog = VillagerDefinitionCatalog.loadFromAssetPacksOrClasspath(cl);
        ReputationRewardCatalog.refreshFromVillagerCatalog(this.villagerDefinitionCatalog);
        this.dialogueResolver.reloadFromVillagerCatalog(this.villagerDefinitionCatalog);
        this.constructionCatalog = ConstructionCatalog.loadFromAssetPacksOrClasspath(cl);
        this.dialogueCatalog = DialogueCatalog.loadFromAssetPacksOrClasspath(cl);
        this.questCatalog = QuestCatalog.loadFromAssetPacksOrClasspath(cl);
        this.villagerScheduleRegistry = VillagerScheduleRegistry.loadFromAssetPacksOrClasspath(cl);
        this.townNameCatalog = TownNameCatalog.loadFromClasspath();
        LOGGER.atInfo().log(
            "Aetherhaven asset catalogs reloaded (constructions=%s, dialogue=%s, quests=%s, villagerDefs=%s, villagerSchedules=loaded)",
            this.constructionCatalog.ids(),
            this.dialogueCatalog.all().keySet(),
            this.questCatalog.all().keySet(),
            this.villagerDefinitionCatalog.allByNpcRoleId().keySet()
        );
    }

    @Override
    protected void shutdown() {
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
