package com.hexvane.aetherhaven;

import com.hexvane.aetherhaven.generated.HstatsBuildMetadata;
import com.hexvane.aetherhaven.bard.BardActivePerformancesResource;
import com.hexvane.aetherhaven.bard.BardMusicProximityState;
import com.hexvane.aetherhaven.bard.BardMusicProximitySystem;
import com.hexvane.aetherhaven.bard.BardPerformanceComponent;
import com.hexvane.aetherhaven.bard.BardPerformanceTickSystem;
import com.hexvane.aetherhaven.bard.data.BardSongCatalog;
import com.hexvane.aetherhaven.charter.CharterPlaceEventSystem;
import com.hexvane.aetherhaven.command.AetherhavenCommand;
import com.hexvane.aetherhaven.command.AetherhavenPathCommand;
import com.hexvane.aetherhaven.config.AetherhavenConfigJsonMigration;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.config.PluginConfigMerge;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.PrefabMaterialsCatalog;
import com.hexvane.aetherhaven.construction.prefabmaterials.PrefabMaterialsService;
import com.hexvane.aetherhaven.construction.assembly.BuildingStaffAssemblyChannelComponent;
import com.hexvane.aetherhaven.construction.assembly.BuildingStaffFrontierTracerInteraction;
import com.hexvane.aetherhaven.construction.assembly.BuildingStaffFrontierTracerTickSystem;
import com.hexvane.aetherhaven.construction.assembly.BuildingStaffFrontierTracerComponent;
import com.hexvane.aetherhaven.construction.assembly.BuildingStaffMarkerEntity;
import com.hexvane.aetherhaven.construction.assembly.BuildingStaffPreviewPlayerComponent;
import com.hexvane.aetherhaven.construction.assembly.BuildingStaffPreviewPlayerRemoveSystem;
import com.hexvane.aetherhaven.construction.assembly.BuildingStaffHotbarManaHudSystem;
import com.hexvane.aetherhaven.construction.assembly.BuildingStaffManaRegenSystem;
import com.hexvane.aetherhaven.construction.assembly.BuildingStaffSecondaryInteraction;
import com.hexvane.aetherhaven.construction.assembly.PlotAssemblyPreviewSystem;
import com.hexvane.aetherhaven.construction.assembly.PlotAssemblyTickSystem;
import com.hexvane.aetherhaven.dialogue.AetherhavenDialogueWorldView;
import com.hexvane.aetherhaven.dialogue.DialogueCatalog;
import com.hexvane.aetherhaven.quest.QuestCatalog;
import com.hexvane.aetherhaven.quest.QuestKillProgressSystem;
import com.hexvane.aetherhaven.questboard.QuestBoardCatalog;
import com.hexvane.aetherhaven.questboard.QuestBoardOnlineDawnService;
import com.hexvane.aetherhaven.tourist.TouristReconcileService;
import com.hexvane.aetherhaven.dialogue.DialogueResolver;
import com.hexvane.aetherhaven.dialogue.DialogueWorldView;
import com.hexvane.aetherhaven.npc.BuilderActionOpenAetherhavenDialogue;
import com.hexvane.aetherhaven.npc.NpcFaceVisualState;
import com.hexvane.aetherhaven.npc.NpcReputationWaveState;
import com.hexvane.aetherhaven.npc.movement.BuilderBodyMotionWanderInRectGroundPreference;
import com.hexvane.aetherhaven.placement.PlotBlockPreviewCleanupSystem;
import com.hexvane.aetherhaven.placement.PlotConstructionBlockResolver;
import com.hexvane.aetherhaven.placement.PlotPlacementOpenHelper;
import com.hexvane.aetherhaven.plot.CharterBlock;
import com.hexvane.aetherhaven.plot.ManagementBlock;
import com.hexvane.aetherhaven.plot.ManagementBreakBlockSystem;
import com.hexvane.aetherhaven.plot.PlotSignBlock;
import com.hexvane.aetherhaven.plot.SprinklerBlock;
import com.hexvane.aetherhaven.plot.FounderMonumentBlock;
import com.hexvane.aetherhaven.plot.GaiaStatueBlock;
import com.hexvane.aetherhaven.plot.PlayerPlotTokenUnlockState;
import com.hexvane.aetherhaven.plot.PlotTokenIconPacketAdapter;
import com.hexvane.aetherhaven.plot.PlotTokenUnlockPageUseInteraction;
import com.hexvane.aetherhaven.plot.PlotTokenUnlockPlayerInitSystem;
import com.hexvane.aetherhaven.plot.PlotTokenVirtualItemRegistry;
import com.hexvane.aetherhaven.plot.ShopSafeBlock;
import com.hexvane.aetherhaven.plot.TreasuryBlock;
import com.hexvane.aetherhaven.shop.ShopSafeUseInteraction;
import com.hexvane.aetherhaven.economy.ShopSafeBreakBlockSystem;
import com.hexvane.aetherhaven.guild.GuildHallDisplayAnchor;
import com.hexvane.aetherhaven.guild.GuildHallDisplayAnchorSystem;
import com.hexvane.aetherhaven.guild.marker.AdventurerSpawnMarkerEntity;
import com.hexvane.aetherhaven.guild.marker.AdventurerSpawnMarkerSystems;
import com.hexvane.aetherhaven.poi.marker.PoiMarkerDataComponent;
import com.hexvane.aetherhaven.poi.marker.PoiMarkerEntity;
import com.hexvane.aetherhaven.poi.marker.PoiMarkerSystems;
import com.hexvane.aetherhaven.poi.tool.PoiDebugLabelEntity;
import com.hexvane.aetherhaven.poi.tool.PoiToolModeCycleInteraction;
import com.hexvane.aetherhaven.poi.tool.PoiToolMoveInteraction;
import com.hexvane.aetherhaven.poi.tool.PoiToolPlayerComponent;
import com.hexvane.aetherhaven.poi.tool.PoiToolSecondaryInteraction;
import com.hexvane.aetherhaven.purification.PurificationPowderPlayerComponent;
import com.hexvane.aetherhaven.poi.tool.PoiToolSetTargetInteraction;
import com.hexvane.aetherhaven.poi.tool.PoiToolSelectInteraction;
import com.hexvane.aetherhaven.poi.tool.PoiToolVisualizationSystem;
import com.hexvane.aetherhaven.pathtool.PathToolAddNodeInteraction;
import com.hexvane.aetherhaven.pathtool.PathToolModeCycleInteraction;
import com.hexvane.aetherhaven.pathtool.PathToolPlayerComponent;
import com.hexvane.aetherhaven.pathtool.PathToolPreviewSystem;
import com.hexvane.aetherhaven.pathtool.PathToolSelectInteraction;
import com.hexvane.aetherhaven.pathtool.PathToolStyleCycleInteraction;
import com.hexvane.aetherhaven.pathtool.PathToolUseInteraction;
import com.hexvane.aetherhaven.pathtool.PathToolWidthCycleInteraction;
import com.hexvane.aetherhaven.pathtool.PathNavViz;
import com.hexvane.aetherhaven.plotcreator.CustomBuildingIconAssetRegistry;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorBlockInteraction;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorBreakAllowSystem;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorCancelInteraction;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorPreviewSystem;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorStepBackInteraction;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorStepForwardInteraction;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorUseInteraction;
import com.hexvane.aetherhaven.patrol.GuardPatrolState;
import com.hexvane.aetherhaven.patrol.GuardPatrolSystem;
import com.hexvane.aetherhaven.rts.CommandPostBlock;
import com.hexvane.aetherhaven.rts.CommandPostPlaceEventSystem;
import com.hexvane.aetherhaven.rts.CommandPostUseInteraction;
import com.hexvane.aetherhaven.rts.GuardRtsCommandState;
import com.hexvane.aetherhaven.rts.GuardCombatCounterAttackSystem;
import com.hexvane.aetherhaven.rts.GuardRtsCommandSystem;
import com.hexvane.aetherhaven.rts.RtsCameraMousePollSystem;
import com.hexvane.aetherhaven.rts.RtsMoveOrderVisualSystem;
import com.hexvane.aetherhaven.rts.RtsCommanderCameraSystem;
import com.hexvane.aetherhaven.rts.RtsExitMovementGuardSystem;
import com.hexvane.aetherhaven.rts.RtsClientMovementPacketAdapter;
import com.hexvane.aetherhaven.rts.RtsCommandHotbarSlotInboundAdapter;
import com.hexvane.aetherhaven.rts.RtsCommandPlayerComponent;
import com.hexvane.aetherhaven.rts.RtsCommandService;
import com.hexvane.aetherhaven.rts.RtsExitInteraction;
import com.hexvane.aetherhaven.rts.RtsFlagOrderCycleInteraction;
import com.hexvane.aetherhaven.rts.RtsFlagStopInteraction;
import com.hexvane.aetherhaven.rts.RtsHudRefreshSystem;
import com.hexvane.aetherhaven.rts.RtsCommanderNpcDamageFilterSystem;
import com.hexvane.aetherhaven.rts.RtsOrphanedGuardRecoverySystem;
import com.hexvane.aetherhaven.rts.RtsUncleanSessionRecoverySystem;
import com.hexvane.aetherhaven.rts.RtsInputGuardListener;
import com.hexvane.aetherhaven.rts.RtsMarkerVisualSystem;
import com.hexvane.aetherhaven.rts.RtsMouseInputListener;
import com.hexvane.aetherhaven.rts.RtsStanceCycleInteraction;
import com.hexvane.aetherhaven.rts.RtsToolPrimaryInteraction;
import com.hexvane.aetherhaven.rts.RtsToolSecondaryInteraction;
import com.hexvane.aetherhaven.patrol.PatrolWandModeCycleInteraction;
import com.hexvane.aetherhaven.patrol.PatrolWandNewRouteInteraction;
import com.hexvane.aetherhaven.patrol.PatrolWandPlayerComponent;
import com.hexvane.aetherhaven.patrol.PatrolWandPreviewSystem;
import com.hexvane.aetherhaven.patrol.PatrolWandPrimaryInteraction;
import com.hexvane.aetherhaven.patrol.PatrolWandSecondaryInteraction;
import com.hexvane.aetherhaven.patrol.PatrolWandToggleClosedInteraction;
import com.hexvane.aetherhaven.patrol.PatrolWandUseInteraction;
import com.hexvane.aetherhaven.purification.PurificationPowderUseInteraction;
import com.hexvane.aetherhaven.growthserum.GrowthSerumUseInteraction;
import com.hexvane.aetherhaven.huntingknife.HuntingKnifeBonusDropSystem;
import com.hexvane.aetherhaven.rootremover.RootRemoverUseInteraction;
import com.hexvane.aetherhaven.purification.PurificationPowderPlayerRemoveSystem;
import com.hexvane.aetherhaven.purification.PurificationPowderVisualizationSystem;
import com.hexvane.aetherhaven.purification.PurificationPreviewEntity;
import com.hexvane.aetherhaven.autonomy.VillagerAutonomyDebugTag;
import com.hexvane.aetherhaven.autonomy.VillagerAutonomyState;
import com.hexvane.aetherhaven.autonomy.DoorwaySeparationBypassSystem;
import com.hexvane.aetherhaven.autonomy.VillagerMoodVisualSystem;
import com.hexvane.aetherhaven.autonomy.VillagerReputationWaveSystem;
import com.hexvane.aetherhaven.autonomy.VillagerAutonomySystem;
import com.hexvane.aetherhaven.builder.BuilderConstructionAssistState;
import com.hexvane.aetherhaven.builder.BuilderConstructionAssistSystem;
import com.hexvane.aetherhaven.autonomy.BlockMountDeathCleanupSystem;
import com.hexvane.aetherhaven.autonomy.ChunkUnloadMountDisconnectSystem;
import com.hexvane.aetherhaven.autonomy.VillagerBlockMountSafetySystem;
import com.hexvane.aetherhaven.scaffold.ScaffoldBreakDebugSystem;
import com.hexvane.aetherhaven.scaffold.ScaffoldColumnCascadeBreakSystem;
import com.hexvane.aetherhaven.scaffold.ScaffoldDamageBlockDebugSystem;
import com.hexvane.aetherhaven.scaffold.ScaffoldStackPlaceInteraction;
import com.hexvane.aetherhaven.scaffold.ScaffoldUseExtendInteraction;
import com.hexvane.aetherhaven.reputation.ReputationRewardCatalog;
import com.hexvane.aetherhaven.schedule.VillagerScheduleRegistry;
import com.hexvane.aetherhaven.schedule.VillagerScheduleTickState;
import com.hexvane.aetherhaven.villager.AetherhavenVillagerHandle;
import com.hexvane.aetherhaven.guild.VillagerDeathHandlerSystem;
import com.hexvane.aetherhaven.townsfolk.EntityChunkStaleReferenceCleanupSystem;
import com.hexvane.aetherhaven.world.WorldSpawnStaleChunkRefCleanupSystem;
import com.hexvane.aetherhaven.townsfolk.PendingEntityRemovalSystem;
import com.hexvane.aetherhaven.townsfolk.TownsfolkAssignmentSystem;
import com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding;
import com.hexvane.aetherhaven.townsfolk.TownsfolkPoolPersistence;
import com.hexvane.aetherhaven.townsfolk.TownsfolkSpawnService;
import com.hexvane.aetherhaven.equipment.data.EquipmentProfileCatalog;
import com.hexvane.aetherhaven.townsfolk.data.TownsfolkCharacterCatalog;
import com.hexvane.aetherhaven.townsfolk.data.TownsfolkPersonalityCatalog;
import com.hexvane.aetherhaven.villager.data.VillagerDefinitionCatalog;
import com.hexvane.aetherhaven.villager.NpcPersistentModelResyncSystem;
import com.hexvane.aetherhaven.questboard.RaidHealthBarHudRefreshSystem;
import com.hexvane.aetherhaven.questboard.RaidQuestMarchSystem;
import com.hexvane.aetherhaven.questboard.RaidQuestMobBinding;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.TownVillagerEnvironmentalDamageFilterSystem;
import com.hexvane.aetherhaven.villager.TownVillagerNpcWorldSpawnSanitizeSystems;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import com.hexvane.aetherhaven.villager.VillagerNeedsDecaySystem;
import com.hexvane.aetherhaven.economy.TreasuryBreakBlockSystem;
import com.hexvane.aetherhaven.geode.GeodeLootFiles;
import com.hexvane.aetherhaven.shopspot.ShopLootFiles;
import com.hexvane.aetherhaven.shopspot.ShopPriceCatalog;
import com.hexvane.aetherhaven.shopspot.ShopPriceTooltipMessages;
import com.hexvane.aetherhaven.shopspot.ShopPriceTooltipPacketAdapter;
import com.hexvane.aetherhaven.shopspot.ShopPriceFiles;
import com.hexvane.aetherhaven.shopspot.ShopSpotBlock;
import com.hexvane.aetherhaven.shopspot.ShopSpotBreakBlockSystem;
import com.hexvane.aetherhaven.shopspot.ShopSpotDisplayTickSystem;
import com.hexvane.aetherhaven.shopspot.ShopSpotLookAtSystem;
import com.hexvane.aetherhaven.shopspot.ShopSpotPlaceEventSystem;
import com.hexvane.aetherhaven.shopspot.ShopSpotPlayerComponent;
import com.hexvane.aetherhaven.shopspot.ShopSpotSecondaryInteraction;
import com.hexvane.aetherhaven.shopspot.ShopSpotUseInteraction;
import com.hexvane.aetherhaven.tourist.TouristAutonomyState;
import com.hexvane.aetherhaven.tourist.TouristAutonomySystem;
import com.hexvane.aetherhaven.tourist.TouristPortalBlock;
import com.hexvane.aetherhaven.tourist.TouristPortalPlaceEventSystem;
import com.hexvane.aetherhaven.floatinggift.FloatingGiftComponent;
import com.hexvane.aetherhaven.floatinggift.FloatingGiftLootFiles;
import com.hexvane.aetherhaven.floatinggift.FloatingGiftSchedulerSystem;
import com.hexvane.aetherhaven.floatinggift.FloatingGiftDamagePopSystem;
import com.hexvane.aetherhaven.floatinggift.FloatingGiftSystem;
import com.hexvane.aetherhaven.inn.InnBellUseInteraction;
import com.hexvane.aetherhaven.jewelry.JewelryGemTraits;
import com.hexvane.aetherhaven.jewelry.JewelryInventoryTooltipSync;
import com.hexvane.aetherhaven.jewelry.JewelryInventoryTooltipSyncSystem;
import com.hexvane.aetherhaven.jewelry.JewelryNativeTooltipManager;
import com.hexvane.aetherhaven.jewelry.JewelryPlayerInitSystem;
import com.hexvane.aetherhaven.jewelry.JewelryTooltipPacketAdapter;
import com.hexvane.aetherhaven.jewelry.JewelryVirtualItemRegistry;
import com.hexvane.aetherhaven.jewelry.JewelryRolling;
import com.hexvane.aetherhaven.jewelry.LootChestBonusInjectSystem;
import com.hexvane.aetherhaven.jewelry.LootrIntegration;
import com.hexvane.aetherhaven.jewelry.LootrChestProcessedPlayers;
import com.hexvane.aetherhaven.jewelry.LootChestWorldLootMarkSystem;
import com.hexvane.aetherhaven.jewelry.LootChestWorldLootPending;
import com.hexvane.aetherhaven.jewelry.JewelryLoadoutEffectSyncSystem;
import com.hexvane.aetherhaven.jewelry.PlayerJewelryLoadout;
import com.hexvane.aetherhaven.gaiadraught.GaiaDraughtCraftSystem;
import com.hexvane.aetherhaven.gaiadraught.GaiaDraughtInventoryChangeSystem;
import com.hexvane.aetherhaven.gaiadraught.GaiaDraughtInventorySyncSystem;
import com.hexvane.aetherhaven.gaiadraught.GaiasDraughtConsumeInteraction;
import com.hexvane.aetherhaven.loot.PlayerBlockBreakBonusSystem;
import com.hexvane.aetherhaven.rescue.RescueVillagerBreakBlockSystem;
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
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownNameCatalog;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.ui.CharterAmendmentsPage;
import com.hexvane.aetherhaven.ui.FeastPage;
import com.hexvane.aetherhaven.ui.CharterTownPage;
import com.hexvane.aetherhaven.ui.PlotConstructionPage;
import com.hexvane.aetherhaven.placement.WallPlacementEditHelper;
import com.hexvane.aetherhaven.placement.WallPlacementOpenHelper;
import com.hexvane.aetherhaven.ui.PlotPlacementPage;
import com.hexvane.aetherhaven.ui.WallPlacementPage;
import com.hexvane.aetherhaven.ui.GeodeOpenPage;
import com.hexvane.aetherhaven.ui.ShopSpotConfigPage;
import com.hexvane.aetherhaven.ui.OpenHandMirrorUiInteraction;
import com.hexvane.aetherhaven.ui.JewelryAppraisalPage;
import com.hexvane.aetherhaven.ui.JewelryCraftingPage;
import com.hexvane.aetherhaven.ui.PlotCraftingPage;
import com.hexvane.aetherhaven.ui.PlayerTownJournalState;
import com.hexvane.aetherhaven.ui.DifficultyPage;
import com.hexvane.aetherhaven.ui.QuestBoardPage;
import com.hexvane.aetherhaven.ui.QuestJournalPage;
import com.hexvane.aetherhaven.ui.GaiaStatueRevivePage;
import com.hexvane.aetherhaven.ui.TownJournalPlayerInitSystem;
import com.hexvane.aetherhaven.production.ProductionCatalog;
import com.hexvane.aetherhaven.production.ProductionTickSystem;
import com.hexvane.aetherhaven.production.WorkplaceUnlockCatalog;
import com.hexvane.aetherhaven.ui.ProductionStoragePage;
import com.hexvane.aetherhaven.ui.ProductionStorageUnlocksPage;
import com.hexvane.aetherhaven.ui.TreasuryPage;
import com.hexvane.aetherhaven.ui.VillagerNeedsOverviewPage;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import org.joml.Vector3i;
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
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.server.core.asset.AssetPackRegisterEvent;
import com.hypixel.hytale.server.core.asset.common.CommonAsset;
import com.hypixel.hytale.server.core.asset.common.CommonAssetModule;
import com.hypixel.hytale.server.core.asset.common.CommonAssetRegistry;
import com.hypixel.hytale.server.core.asset.common.events.SendCommonAssetsEvent;
import com.hypixel.hytale.protocol.packets.setup.RequestCommonAssetsRebuild;
import com.hypixel.hytale.server.core.universe.Universe;
import java.util.List;
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
    private final AetherhavenGameTimeBridgeSubscriber gameTimeBridgeSubscriber = new AetherhavenGameTimeBridgeSubscriber(this);
    /** Filled in {@link #setup}; used by {@link AetherhavenGameTimeCoordinatorSystem}. */
    @Nullable
    private ResourceType<EntityStore, AetherhavenGameTimeCursorResource> gameTimeCursorResourceType;

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

    public AetherhavenPlugin(JavaPluginInit init) {
        super(init);
    }

    @Nullable
    public static AetherhavenPlugin get() {
        return instance;
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

        String hstatsModUuid = HstatsBuildMetadata.HSTATS_MOD_UUID;
        String modVersion = this.getManifest().getVersion().toString();
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
            PluginConfigMerge.rewritePrettyJson(configPath);
            LOGGER.atInfo().log("Created default config at %s", configPath);
        }
        GeodeLootFiles.ensureDefaultLootFile(this);
        FloatingGiftLootFiles.ensureDefaultLootFile(this);
        ShopPriceFiles.ensureDefaultPricesFile(this);
        ShopLootFiles.ensureDefaultLootTables(this);
        this.shopPriceCatalog = ShopPriceFiles.loadCatalog(this);
        ShopPriceTooltipMessages.clearCache();
        registerModCommonAssetDelivery();
        registerJewelryNativeTooltipHooks();
        registerJewelryRarityBorderPackets();
        registerPlotTokenIconPackets();
        registerShopPriceTooltipPackets();
        registerRtsClientMovementPacketAdapter();
        registerRtsCommandHotbarSlotInboundAdapter();

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
        ShopSafeBlock.register(this.getChunkStoreRegistry());
        GaiaStatueBlock.register(this.getChunkStoreRegistry());
        SprinklerBlock.register(this.getChunkStoreRegistry());
        FounderMonumentBlock.register(this.getChunkStoreRegistry());
        ShopSpotBlock.register(this.getChunkStoreRegistry());
        TouristPortalBlock.register(this.getChunkStoreRegistry());
        CommandPostBlock.register(this.getChunkStoreRegistry());
        FounderMonumentStatueSkin.register(this.getEntityStoreRegistry());

        VillagerNeeds.register(this.getEntityStoreRegistry());
        PlayerJewelryLoadout.register(this.getEntityStoreRegistry());
        PlayerTownJournalState.register(this.getEntityStoreRegistry());
        PlayerPlotTokenUnlockState.register(this.getEntityStoreRegistry());
        this.getEntityStoreRegistry().registerSystem(new JewelryPlayerInitSystem());
        this.getEntityStoreRegistry().registerSystem(new JewelryInventoryTooltipSyncSystem());
        this.getEntityStoreRegistry().registerSystem(new TownJournalPlayerInitSystem());
        this.getEntityStoreRegistry().registerSystem(new PlotTokenUnlockPlayerInitSystem());
        this.getEntityStoreRegistry().registerSystem(new JewelryLoadoutEffectSyncSystem());
        LootChestWorldLootPending.register(this.getChunkStoreRegistry());
        LootrChestProcessedPlayers.register(this.getChunkStoreRegistry());
        this.getChunkStoreRegistry().registerSystem(new LootChestWorldLootMarkSystem());
        this.getChunkStoreRegistry().registerSystem(new LootChestBonusInjectSystem(this));
        this.getChunkStoreRegistry().registerSystem(new ChunkUnloadMountDisconnectSystem());
        this.getChunkStoreRegistry().registerSystem(new EntityChunkStaleReferenceCleanupSystem());
        this.getChunkStoreRegistry().registerSystem(new WorldSpawnStaleChunkRefCleanupSystem());
        AetherhavenVillagerHandle.register(this.getEntityStoreRegistry());
        TownVillagerBinding.register(this.getEntityStoreRegistry());
        RaidQuestMobBinding.register(this.getEntityStoreRegistry());
        TownsfolkCharacterBinding.register(this.getEntityStoreRegistry());
        GuildHallDisplayAnchor.register(this.getEntityStoreRegistry());
        PoiMarkerDataComponent.register(this.getEntityStoreRegistry());
        this.getEntityStoreRegistry().registerSystem(new TownVillagerNpcWorldSpawnSanitizeSystems.OnAdd());
        this.getEntityStoreRegistry().registerSystem(new TownVillagerNpcWorldSpawnSanitizeSystems.EachTick());
        VillagerAutonomyState.register(this.getEntityStoreRegistry());
        NpcFaceVisualState.register(this.getEntityStoreRegistry());
        NpcReputationWaveState.register(this.getEntityStoreRegistry());
        BuilderConstructionAssistState.register(this.getEntityStoreRegistry());
        TouristAutonomyState.register(this.getEntityStoreRegistry());
        VillagerScheduleTickState.register(this.getEntityStoreRegistry());
        BardPerformanceComponent.register(this.getEntityStoreRegistry());
        BardActivePerformancesResource.register(this.getEntityStoreRegistry());
        BardMusicProximityState.register(this.getEntityStoreRegistry());
        VillagerAutonomyDebugTag.register(this.getEntityStoreRegistry());
        PoiToolPlayerComponent.register(this.getEntityStoreRegistry());
        PathToolPlayerComponent.register(this.getEntityStoreRegistry());
        PatrolWandPlayerComponent.register(this.getEntityStoreRegistry());
        RtsCommandPlayerComponent.register(this.getEntityStoreRegistry());
        GuardRtsCommandState.register(this.getEntityStoreRegistry());
        GuardPatrolState.register(this.getEntityStoreRegistry());
        PurificationPowderPlayerComponent.register(this.getEntityStoreRegistry());
        ShopSpotPlayerComponent.register(this.getEntityStoreRegistry());
        BuildingStaffAssemblyChannelComponent.register(this.getEntityStoreRegistry());
        BuildingStaffFrontierTracerComponent.register(this.getEntityStoreRegistry());
        BuildingStaffPreviewPlayerComponent.register(this.getEntityStoreRegistry());
        FloatingGiftComponent.register(this.getEntityStoreRegistry());
        this.getEntityRegistry()
            .registerEntity(
                "AetherhavenAdventurerSpawnMarker",
                AdventurerSpawnMarkerEntity.class,
                world -> {
                    AdventurerSpawnMarkerEntity e = new AdventurerSpawnMarkerEntity();
                    if (world != null) {
                        e.loadIntoWorld(world);
                    }
                    return e;
                },
                AdventurerSpawnMarkerEntity.CODEC
            );
        this.getEntityRegistry()
            .registerEntity(
                "AetherhavenPoiMarker",
                PoiMarkerEntity.class,
                world -> {
                    PoiMarkerEntity e = new PoiMarkerEntity();
                    if (world != null) {
                        e.loadIntoWorld(world);
                    }
                    return e;
                },
                PoiMarkerEntity.CODEC
            );
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
        this.getEntityRegistry()
            .registerEntity(
                "AetherhavenBuildingStaffMarker",
                BuildingStaffMarkerEntity.class,
                world -> {
                    BuildingStaffMarkerEntity e = new BuildingStaffMarkerEntity();
                    if (world != null) {
                        e.loadIntoWorld(world);
                    }
                    return e;
                },
                BuildingStaffMarkerEntity.CODEC
            );
        this.getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenPoiToolSelect", PoiToolSelectInteraction.class, PoiToolSelectInteraction.CODEC);
        this.getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenPoiToolMove", PoiToolMoveInteraction.class, PoiToolMoveInteraction.CODEC);
        this.getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenPoiToolSecondary", PoiToolSecondaryInteraction.class, PoiToolSecondaryInteraction.CODEC);
        this.getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenPoiToolModeCycle", PoiToolModeCycleInteraction.class, PoiToolModeCycleInteraction.CODEC);
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
        this.getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenRootRemoverUse",
                RootRemoverUseInteraction.class,
                RootRemoverUseInteraction.CODEC
            );
        this.getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenGrowthSerumUse",
                GrowthSerumUseInteraction.class,
                GrowthSerumUseInteraction.CODEC
            );
        this.getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenPlotTokenUnlockPageUse",
                PlotTokenUnlockPageUseInteraction.class,
                PlotTokenUnlockPageUseInteraction.CODEC
            );
        this.getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenGaiasDraughtConsume", GaiasDraughtConsumeInteraction.class, GaiasDraughtConsumeInteraction.CODEC);
        this.getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenPathToolSelect", PathToolSelectInteraction.class, PathToolSelectInteraction.CODEC);
        this.getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenPathToolAddNode", PathToolAddNodeInteraction.class, PathToolAddNodeInteraction.CODEC);
        this.getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenPathToolUse", PathToolUseInteraction.class, PathToolUseInteraction.CODEC);
        this.getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenPathToolModeCycle",
                PathToolModeCycleInteraction.class,
                PathToolModeCycleInteraction.CODEC
            );
        this.getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenPathToolWidthCycle",
                PathToolWidthCycleInteraction.class,
                PathToolWidthCycleInteraction.CODEC
            );
        this.getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenPathToolStyleCycle",
                PathToolStyleCycleInteraction.class,
                PathToolStyleCycleInteraction.CODEC
            );
        this.getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenPlotCreatorUse", PlotCreatorUseInteraction.class, PlotCreatorUseInteraction.CODEC);
        this.getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenPlotCreatorBlock", PlotCreatorBlockInteraction.class, PlotCreatorBlockInteraction.CODEC);
        this.getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenPlotCreatorStepBack",
                PlotCreatorStepBackInteraction.class,
                PlotCreatorStepBackInteraction.CODEC
            );
        this.getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenPlotCreatorStepForward",
                PlotCreatorStepForwardInteraction.class,
                PlotCreatorStepForwardInteraction.CODEC
            );
        this.getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenPlotCreatorCancel",
                PlotCreatorCancelInteraction.class,
                PlotCreatorCancelInteraction.CODEC
            );
        this.getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenPatrolWandPrimary", PatrolWandPrimaryInteraction.class, PatrolWandPrimaryInteraction.CODEC);
        this.getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenPatrolWandSecondary",
                PatrolWandSecondaryInteraction.class,
                PatrolWandSecondaryInteraction.CODEC
            );
        this.getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenPatrolWandUse", PatrolWandUseInteraction.class, PatrolWandUseInteraction.CODEC);
        this.getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenPatrolWandModeCycle",
                PatrolWandModeCycleInteraction.class,
                PatrolWandModeCycleInteraction.CODEC
            );
        this.getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenPatrolWandNewRoute",
                PatrolWandNewRouteInteraction.class,
                PatrolWandNewRouteInteraction.CODEC
            );
        this.getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenPatrolWandToggleClosed",
                PatrolWandToggleClosedInteraction.class,
                PatrolWandToggleClosedInteraction.CODEC
            );
        this.getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenBuildingStaffSecondary",
                BuildingStaffSecondaryInteraction.class,
                BuildingStaffSecondaryInteraction.CODEC
            );
        this.getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenBuildingStaffFrontierTracer",
                BuildingStaffFrontierTracerInteraction.class,
                BuildingStaffFrontierTracerInteraction.CODEC
            );
        this.getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenScaffoldStackPlace",
                ScaffoldStackPlaceInteraction.class,
                ScaffoldStackPlaceInteraction.CODEC
            );
        this.getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenScaffoldUseExtend",
                ScaffoldUseExtendInteraction.class,
                ScaffoldUseExtendInteraction.CODEC
            );
        this.getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenShopSpotUse", ShopSpotUseInteraction.class, ShopSpotUseInteraction.CODEC);
        this.getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenShopSpotSecondary", ShopSpotSecondaryInteraction.class, ShopSpotSecondaryInteraction.CODEC);
        this.getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenCommandPostUse", CommandPostUseInteraction.class, CommandPostUseInteraction.CODEC);
        this.getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenInnBellUse", InnBellUseInteraction.class, InnBellUseInteraction.CODEC);
        this.getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenShopSafeUse", ShopSafeUseInteraction.class, ShopSafeUseInteraction.CODEC);
        this.getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenRtsToolPrimary", RtsToolPrimaryInteraction.class, RtsToolPrimaryInteraction.CODEC);
        this.getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenRtsToolSecondary", RtsToolSecondaryInteraction.class, RtsToolSecondaryInteraction.CODEC);
        this.getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenRtsFlagOrderCycle", RtsFlagOrderCycleInteraction.class, RtsFlagOrderCycleInteraction.CODEC);
        this.getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenRtsFlagStop", RtsFlagStopInteraction.class, RtsFlagStopInteraction.CODEC);
        this.getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenRtsStanceCycle", RtsStanceCycleInteraction.class, RtsStanceCycleInteraction.CODEC);
        this.getCodecRegistry(com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.CODEC)
            .register("AetherhavenRtsExit", RtsExitInteraction.class, RtsExitInteraction.CODEC);
        this.getEntityStoreRegistry().registerSystem(new PlotAssemblyTickSystem(this));
        this.getEntityStoreRegistry().registerSystem(new PlotAssemblyPreviewSystem(this));
        this.getEntityStoreRegistry().registerSystem(new BuildingStaffFrontierTracerTickSystem(this));
        this.getEntityStoreRegistry().registerSystem(new BuildingStaffManaRegenSystem());
        this.getEntityStoreRegistry().registerSystem(new BuildingStaffHotbarManaHudSystem.SlotChangeHandler());
        this.getEntityStoreRegistry().registerSystem(new VillagerNeedsDecaySystem(this));
        this.getEntityStoreRegistry().registerSystem(new VillagerBlockMountSafetySystem(this));
        this.getEntityStoreRegistry().registerSystem(new BlockMountDeathCleanupSystem());
        this.getEntityStoreRegistry().registerSystem(new BuilderConstructionAssistSystem(this));
        this.getEntityStoreRegistry().registerSystem(new VillagerAutonomySystem(this));
        this.getEntityStoreRegistry().registerSystem(new VillagerMoodVisualSystem(this));
        this.getEntityStoreRegistry().registerSystem(new VillagerReputationWaveSystem(this));
        this.getEntityStoreRegistry().registerSystem(new TouristAutonomySystem(this));
        this.getEntityStoreRegistry().registerSystem(new DoorwaySeparationBypassSystem());
        this.getEntityStoreRegistry().registerSystem(new PendingEntityRemovalSystem());
        this.getEntityStoreRegistry().registerSystem(new TownsfolkAssignmentSystem());
        this.getEntityStoreRegistry().registerSystem(new VillagerDeathHandlerSystem(this));
        this.getEntityStoreRegistry().registerSystem(new TownVillagerEnvironmentalDamageFilterSystem());
        this.getEntityStoreRegistry().registerSystem(new ProductionTickSystem(this));
        this.getEntityStoreRegistry().registerSystem(new CharterPlaceEventSystem(this));
        this.getEntityStoreRegistry().registerSystem(new TreasuryBreakBlockSystem(this));
        this.getEntityStoreRegistry().registerSystem(new ManagementBreakBlockSystem(this));
        this.getEntityStoreRegistry().registerSystem(new ShopSafeBreakBlockSystem(this));
        this.getEntityStoreRegistry().registerSystem(new PlotCreatorBreakAllowSystem(this));
        this.getEntityStoreRegistry().registerSystem(new PlotCreatorPreviewSystem(this));
        this.getEntityStoreRegistry().registerSystem(new PlotBlockPreviewCleanupSystem());
        this.getEntityStoreRegistry().registerSystem(new ShopSpotPlaceEventSystem(this));
        this.getEntityStoreRegistry().registerSystem(new TouristPortalPlaceEventSystem(this));
        this.getEntityStoreRegistry().registerSystem(new ShopSpotBreakBlockSystem(this));
        this.getEntityStoreRegistry().registerSystem(new ShopSpotDisplayTickSystem(this));
        this.getEntityStoreRegistry().registerSystem(new ShopSpotLookAtSystem(this));
        this.getEntityStoreRegistry().registerSystem(new ScaffoldColumnCascadeBreakSystem());
        this.getEntityStoreRegistry().registerSystem(new PlayerBlockBreakBonusSystem(this));
        this.getEntityStoreRegistry().registerSystem(new FounderMonumentPlaceSystem(this));
        this.getEntityStoreRegistry().registerSystem(new FounderMonumentStatueRestoreSystem());
        this.getEntityStoreRegistry().registerSystem(new NpcPersistentModelResyncSystem());
        this.getEntityStoreRegistry().registerSystem(new FounderMonumentBreakSystem(this));
        this.getEntityStoreRegistry().registerSystem(new RescueVillagerBreakBlockSystem(this));
        this.getEntityStoreRegistry().registerSystem(new PoiToolVisualizationSystem(this));
        this.getEntityStoreRegistry().registerSystem(new AdventurerSpawnMarkerSystems.EnsurePrefabCopyable());
        this.getEntityStoreRegistry().registerSystem(new PoiMarkerSystems.EnsurePrefabCopyable());
        this.getEntityStoreRegistry().registerSystem(new GuildHallDisplayAnchorSystem());
        this.getEntityStoreRegistry().registerSystem(new PurificationPowderVisualizationSystem(this));
        this.getEntityStoreRegistry().registerSystem(new PurificationPowderPlayerRemoveSystem());
        this.getEntityStoreRegistry().registerSystem(new BuildingStaffPreviewPlayerRemoveSystem());
        this.getEntityStoreRegistry().registerSystem(new QuestKillProgressSystem(this));
        this.getEntityStoreRegistry().registerSystem(new RaidQuestMarchSystem(this));
        this.getEntityStoreRegistry().registerSystem(new RaidHealthBarHudRefreshSystem(this));
        this.getEntityStoreRegistry().registerSystem(new HuntingKnifeBonusDropSystem());
        this.getEntityStoreRegistry().registerSystem(new BardPerformanceTickSystem());
        this.getEntityStoreRegistry().registerSystem(new BardMusicProximitySystem());
        GaiaDraughtCraftSystem gaiaDraughtCraftSystem = new GaiaDraughtCraftSystem(this);
        this.getEntityStoreRegistry().registerSystem(gaiaDraughtCraftSystem);
        this.getEntityStoreRegistry().registerSystem(new GaiaDraughtCraftSystem.Pre(gaiaDraughtCraftSystem));
        this.getEntityStoreRegistry().registerSystem(new GaiaDraughtInventoryChangeSystem());
        this.getEntityStoreRegistry().registerSystem(new GaiaDraughtInventorySyncSystem(this));
        this.getEntityStoreRegistry().registerSystem(new PathToolPreviewSystem(this));
        this.getEntityStoreRegistry().registerSystem(new PatrolWandPreviewSystem(this));
        this.getEntityStoreRegistry().registerSystem(new GuardPatrolSystem(this));
        this.getEntityStoreRegistry().registerSystem(new GuardRtsCommandSystem(this));
        this.getEntityStoreRegistry().registerSystem(new GuardCombatCounterAttackSystem());
        this.getEntityStoreRegistry().registerSystem(new RtsCommanderCameraSystem.Follow(this));
        this.getEntityStoreRegistry().registerSystem(new RtsExitMovementGuardSystem());
        this.getEntityStoreRegistry().registerSystem(new RtsCameraMousePollSystem());
        this.getEntityStoreRegistry().registerSystem(new RtsHudRefreshSystem(this));
        this.getEntityStoreRegistry().registerSystem(new RtsUncleanSessionRecoverySystem());
        this.getEntityStoreRegistry().registerSystem(new RtsOrphanedGuardRecoverySystem());
        this.getEntityStoreRegistry().registerSystem(new RtsCommanderNpcDamageFilterSystem());
        this.getEntityStoreRegistry().registerSystem(new RtsMarkerVisualSystem(this));
        this.getEntityStoreRegistry().registerSystem(new RtsMoveOrderVisualSystem(this));
        this.getEntityStoreRegistry().registerSystem(new CommandPostPlaceEventSystem(this));
        RtsMouseInputListener.register(this.getEventRegistry());
        RtsInputGuardListener.register(this.getEventRegistry());
        this.getEntityStoreRegistry().registerSystem(new FloatingGiftSchedulerSystem());
        this.getEntityStoreRegistry().registerSystem(new FloatingGiftSystem());
        this.getEntityStoreRegistry().registerSystem(new FloatingGiftDamagePopSystem());
        this.getEntityStoreRegistry().registerSystem(new ScaffoldBreakDebugSystem());
        this.getEntityStoreRegistry().registerSystem(new ScaffoldDamageBlockDebugSystem());

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
            WallPlacementPage.class,
            AetherhavenConstants.PAGE_WALL_PLACEMENT,
            WallPlacementOpenHelper::tryOpenBuild
        );
        OpenCustomUIInteraction.registerCustomPageSupplier(
            this,
            WallPlacementPage.class,
            AetherhavenConstants.PAGE_WALL_EDIT,
            WallPlacementEditHelper::tryOpenEdit
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
        OpenCustomUIInteraction.registerCustomPageSupplier(
            this,
            ProductionStoragePage.class,
            AetherhavenConstants.PAGE_PRODUCTION_STORAGE,
            (ref, componentAccessor, playerRef, context) -> {
                BlockPosition targetBlock = context.getTargetBlock();
                if (targetBlock == null) {
                    return null;
                }
                Store<EntityStore> store = ref.getStore();
                World world = store.getExternalData().getWorld();
                BlockPosition base = world.getBaseBlock(targetBlock);
                BlockType bt = world.getBlockType(base.x, base.y, base.z);
                if (bt == null
                    || bt == BlockType.EMPTY
                    || !AetherhavenConstants.BLOCK_PRODUCTION_STORAGE.equals(bt.getId())) {
                    return null;
                }
                AetherhavenPlugin p = AetherhavenPlugin.get();
                if (p == null) {
                    return null;
                }
                UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
                if (uc == null) {
                    return null;
                }
                TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, p);
                TownRecord town = tm.findTownForPlayerInWorld(uc.getUuid());
                if (town == null || !town.playerCanManageConstructions(uc.getUuid())) {
                    return null;
                }
                PlotInstance plot = null;
                for (PlotInstance pi : town.getPlotInstances()) {
                    if (pi.getState() != PlotInstanceState.COMPLETE) {
                        continue;
                    }
                    String gameplayCid = p.getConstructionCatalog().resolveGameplayConstructionId(pi.getConstructionId());
                    if (!ProductionCatalog.isProductionWorkplaceConstruction(gameplayCid)) {
                        continue;
                    }
                    if (pi.containsWorldBlock(base.x, base.y, base.z)) {
                        plot = pi;
                        break;
                    }
                }
                if (plot == null) {
                    return null;
                }
                return new ProductionStoragePage(playerRef, town.getTownId(), plot.getPlotId(), base.x, base.y, base.z);
            }
        );
        OpenCustomUIInteraction.registerCustomPageSupplier(
            this,
            ProductionStorageUnlocksPage.class,
            AetherhavenConstants.PAGE_PRODUCTION_STORAGE_UNLOCKS,
            (ref, componentAccessor, playerRef, context) -> null
        );
        OpenCustomUIInteraction.registerSimple(this, QuestJournalPage.class, AetherhavenConstants.PAGE_QUEST_JOURNAL, QuestJournalPage::new);
        OpenCustomUIInteraction.registerSimple(this, QuestBoardPage.class, AetherhavenConstants.PAGE_QUEST_BOARD, QuestBoardPage::new);
        OpenCustomUIInteraction.registerSimple(this, DifficultyPage.class, AetherhavenConstants.PAGE_DIFFICULTY, DifficultyPage::new);
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
            PlotCraftingPage.class,
            AetherhavenConstants.PAGE_PLOT_CRAFTING_BENCH,
            PlotCraftingPage::new
        );
        OpenCustomUIInteraction.registerSimple(
            this,
            GeodeOpenPage.class,
            AetherhavenConstants.PAGE_GEODE_ANVIL,
            pr -> new GeodeOpenPage(pr, false)
        );
        OpenCustomUIInteraction.registerSimple(
            this,
            ShopSpotConfigPage.class,
            AetherhavenConstants.PAGE_SHOP_SPOT_CONFIG,
            ShopSpotConfigPage::new
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
        JewelryNativeTooltipManager.refreshAllPlayers();
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
        LootrIntegration.registerIfAvailable(this);
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
        this.shopPriceCatalog = ShopPriceFiles.loadCatalog(this);
        ShopPriceTooltipMessages.clearCache();
    }

    /**
     * Ensures joining clients receive this mod's {@code Common/} blobs (icons, blockymodels, UI). Vanilla setup only
     * ships assets the client claims to have cached; a stale or empty {@code CachedAssets} folder skips mod files and
     * every {@code Icons/ItemsGenerated/*.png} lookup fails even though the server loaded them from {@code build/resources/main}.
     */
    private void registerModCommonAssetDelivery() {
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

    private void registerJewelryNativeTooltipHooks() {
        this.getEventRegistry()
            .registerGlobal(
                PlayerReadyEvent.class,
                event -> {
                    Player player = event.getPlayer();
                    if (player == null || player.getWorld() == null || player.getReference() == null) {
                        return;
                    }
                    player.getWorld()
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
                                QuestBoardOnlineDawnService.onPlayerReady(ref, store, AetherhavenPlugin.get());
                                UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
                                if (uc != null) {
                                    TouristReconcileService.onTownMemberPlayerReady(
                                        player.getWorld(),
                                        AetherhavenPlugin.get(),
                                        uc.getUuid()
                                    );
                                }
                                RtsCommandService.exit(ref, store);
                            });
                });
        this.getEventRegistry()
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
                    if (this.jewelryTooltipPacketAdapter != null) {
                        this.jewelryTooltipPacketAdapter.onPlayerLeave(event.getPlayerRef().getUuid());
                    }
                    if (this.plotTokenIconPacketAdapter != null) {
                        this.plotTokenIconPacketAdapter.onPlayerLeave(event.getPlayerRef().getUuid());
                    }
                    QuestBoardOnlineDawnService.clearPlayer(event.getPlayerRef().getUuid());
                }
            );
        LOGGER.atInfo().log("Jewelry tooltips use native ItemDisplay metadata (per-stack descriptions)");
    }

    private void registerShopPriceTooltipPackets() {
        this.shopPriceTooltipPacketAdapter = new ShopPriceTooltipPacketAdapter();
        this.shopPriceTooltipPacketAdapter.register();
    }

    private void registerJewelryRarityBorderPackets() {
        this.jewelryVirtualItemRegistry = new JewelryVirtualItemRegistry();
        this.jewelryTooltipPacketAdapter = new JewelryTooltipPacketAdapter(this.jewelryVirtualItemRegistry);
        this.jewelryTooltipPacketAdapter.register();
    }

    private void registerPlotTokenIconPackets() {
        this.plotTokenVirtualItemRegistry = new PlotTokenVirtualItemRegistry();
        this.plotTokenIconPacketAdapter = new PlotTokenIconPacketAdapter(this.plotTokenVirtualItemRegistry);
        this.plotTokenIconPacketAdapter.register();
    }

    private void registerRtsClientMovementPacketAdapter() {
        this.rtsClientMovementPacketAdapter = new RtsClientMovementPacketAdapter();
        this.rtsClientMovementPacketAdapter.register();
    }

    private void registerRtsCommandHotbarSlotInboundAdapter() {
        this.rtsCommandHotbarSlotInboundAdapter = new RtsCommandHotbarSlotInboundAdapter();
        this.rtsCommandHotbarSlotInboundAdapter.register();
    }

    @Override
    protected void shutdown() {
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
