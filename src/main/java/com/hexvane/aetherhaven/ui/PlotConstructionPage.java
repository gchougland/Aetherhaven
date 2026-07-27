package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.MaterialRequirement;
import com.hexvane.aetherhaven.construction.PlotMaterialDepositService;
import com.hexvane.aetherhaven.construction.PrefabMaterialsCatalog;
import com.hexvane.aetherhaven.difficulty.EffectiveBuildingCosts;
import com.hexvane.aetherhaven.difficulty.WorldDifficultyState;
import com.hexvane.aetherhaven.economy.GoldCoinPayment;
import com.hexvane.aetherhaven.inventory.BenchAdjacentChestUtil;
import com.hexvane.aetherhaven.inventory.InventoryMaterials;
import com.hexvane.aetherhaven.plot.ManagementBlock;
import com.hexvane.aetherhaven.plot.PlotBlockRotationUtil;
import com.hexvane.aetherhaven.plot.PlotSignBlock;
import com.hexvane.aetherhaven.plot.PlotTokenIconSync;
import com.hexvane.aetherhaven.plot.PlotTokenInventory;
import com.hexvane.aetherhaven.placement.PlotPlacementOpenHelper;
import com.hexvane.aetherhaven.placement.PlotReconstructMessages;
import com.hexvane.aetherhaven.placement.PlotReconstructService;
import com.hexvane.aetherhaven.plugin.AetherhavenFeatures;
import com.hexvane.aetherhaven.plugin.AetherhavenPluginIds;
import com.hexvane.aetherhaven.construction.assembly.PlotAssemblyBuildStartResult;
import com.hexvane.aetherhaven.construction.assembly.PlotAssemblyService;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hexvane.aetherhaven.production.PlotProductionState;
import com.hexvane.aetherhaven.production.ProductionCatalog;
import com.hexvane.aetherhaven.production.ProductionWorkplaceKinds;
import com.hexvane.aetherhaven.production.WorkplaceProductionUpgrades;
import com.hexvane.aetherhaven.production.WorkplaceProductionUpgrades.Branch;
import com.hexvane.aetherhaven.production.WorkplaceProductionUpgrades.PurchaseResult;
import com.hexvane.aetherhaven.restaurant.PlotRestaurantState;
import com.hexvane.aetherhaven.restaurant.RestaurantUpgrades;
import com.hexvane.aetherhaven.map.TownBorderMapOverlayService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.HouseResidentAssignment;
import com.hexvane.aetherhaven.town.WorkplacePlotAssignment;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownMemberRole;
import com.hexvane.aetherhaven.town.TownMembershipActions;
import com.hexvane.aetherhaven.town.TownPlayerLookup;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.tourist.TownPortalTravelColor;
import com.hexvane.aetherhaven.tourist.TouristPortalTickService;
import com.hexvane.aetherhaven.guild.GuardHireManifest;
import com.hexvane.aetherhaven.questboard.TownRankCapacity;
import com.hexvane.aetherhaven.tourist.TouristVisitManifest;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import org.joml.Vector3d;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hexvane.aetherhaven.ui.AetherhavenInteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PlotConstructionPage extends AetherhavenInteractiveCustomUIPage<PlotConstructionPage.PageData> {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int BREAK_SETTINGS = 10;
    private static final String MATERIALS_GRID = "#MaterialsScroll #MaterialsGrid";
    private static final int MATERIAL_GRID_COLS = 6;
    private static final String MEMBER_ROWS = "#MemberRows";
    private static final int MAX_MEMBER_ROWS = 24;
    private static final String HOUSE_RESIDENT_ROWS =
        "#HouseResidentPickerModal #HouseResidentListScroll #HouseResidentRows";
    private static final String HOUSE_RESIDENT_SLOT_ROWS = "#HouseResidentSlotScroll #HouseResidentSlotRows";
    private static final String WORKPLACE_WORKER_ROWS =
        "#WorkplaceWorkerPickerModal #WorkplaceWorkerListScroll #WorkplaceWorkerRows";
    private static final String WORKPLACE_ASSIGN_ROLE_ROWS =
        "#WorkplaceAssignSection #WorkplaceAssignRoleScroll #WorkplaceAssignRoleRows";
    private static final String TOURIST_MANIFEST_ROWS = "#TouristManifestScroll #TouristManifestRows";
    private static final int MAX_TOURIST_MANIFEST_ROWS = 48;
    private static final String GUARD_MANIFEST_ROWS = "#GuardManifestScroll #GuardManifestRows";
    private static final int MAX_GUARD_MANIFEST_ROWS = 48;
    private static final String VP_COLOR_MODAL = "#VisitorPortalColorPickerModal";
    private static final String VP_COLOR_GRID = VP_COLOR_MODAL + " #Content #VisitorPortalColorPresetGrid";
    private static final String VP_COLOR_CHOOSE_BTN = "#ChooseVisitorPortalColorButton";
    private static final String VP_COLOR_CANCEL_BTN = VP_COLOR_MODAL + " #Content #VisitorPortalColorPickerCancelButton";

    private final Ref<ChunkStore> blockRef;
    @Nonnull
    private final Vector3i blockWorldPos;
    private final boolean managementUi;
    /** 0 = Plot, 1 = Players (management UI only). */
    private int managementTab;
    /** When both production and restaurant perk trees apply: 0 = production, 1 = restaurant. */
    private int perkTreeTab;
    /** Move-building confirmation modal (management block, completed plot). */
    private boolean moveBuildingConfirmOpen;
    /** Reconstruct-building confirmation modal (management block, completed plot). */
    private boolean reconstructBuildingConfirmOpen;
    /** Pick-up plot confirmation modal (plot sign, blueprint plot). */
    private boolean pickUpPlotConfirmOpen;
    /** Open the move-building modal on the first {@link #build} (e.g. returning from town needs). */
    private boolean pendingMoveBuildingModal;
    /**
     * {@code append(ui)} must run only once per page instance; repeating it on every {@link #sendUpdate} duplicates the
     * whole tree and breaks selectors (wrong title, orphan "Materials" label, empty tabs).
     */
    private boolean templateAppended;
    private boolean productionUpgradeTreeAppended;
    private boolean restaurantUpgradeTreeAppended;
    /** House management: hide villagers who already have a home assigned on another completed house plot. */
    private boolean hideHouseResidentElsewhereHoused;
    /** House resident picker modal open. */
    private boolean houseResidentPickerOpen;
    /** Workplace worker picker modal open. */
    private boolean workplaceWorkerPickerOpen;
    /** Visitor portal preset color picker modal. */
    private boolean visitorPortalColorPickerOpen;
    /** {@link TownVillagerBinding} kind for the open workplace picker. */
    @Nullable
    private String workplacePickerResidentKind;
    /** NPC role filter for the open workplace picker (guild hall bard vs guild master). */
    @Nullable
    private String workplacePickerFilterNpcRoleId;
    /** Slot index for the open house resident picker. */
    private int activeHouseResidentSlot;
    /** Persist house-resident hide toggle per player across page instances. */
    private static final Map<UUID, Boolean> HOUSE_RESIDENT_HIDE_ELSEWHERE_PREF = new LinkedHashMap<>();

    public PlotConstructionPage(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<ChunkStore> blockRef,
        @Nonnull Vector3i blockWorldPos,
        boolean managementUi
    ) {
        this(playerRef, blockRef, blockWorldPos, managementUi, 0, false);
    }

    public PlotConstructionPage(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<ChunkStore> blockRef,
        @Nonnull Vector3i blockWorldPos,
        boolean managementUi,
        int initialManagementTab,
        boolean openMoveBuildingModalOnFirstBuild
    ) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.blockRef = blockRef;
        this.blockWorldPos = new Vector3i(blockWorldPos);
        this.managementUi = managementUi;
        this.managementTab = initialManagementTab;
        this.pendingMoveBuildingModal = openMoveBuildingModalOnFirstBuild;
        UUID prefKey = playerRef.getUuid();
        if (prefKey != null) {
            Boolean saved = HOUSE_RESIDENT_HIDE_ELSEWHERE_PREF.get(prefKey);
            if (saved != null) {
                this.hideHouseResidentElsewhereHoused = saved;
            }
        }
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store
    ) {
        if (!templateAppended) {
            commandBuilder.append("Aetherhaven/PlotConstructionPage.ui");
            templateAppended = true;
            AetherhavenUiLocalization.applyPlotConstructionPage(commandBuilder);
        }
        if (!productionUpgradeTreeAppended) {
            commandBuilder.append("#ProductionUpgradeTreeSlot", "Aetherhaven/ProductionUpgradeSkillTree.ui");
            productionUpgradeTreeAppended = true;
        }
        if (!restaurantUpgradeTreeAppended) {
            commandBuilder.append("#ProductionUpgradeTreeSlot", "Aetherhaven/RestaurantUpgradeSkillTree.ui");
            restaurantUpgradeTreeAppended = true;
        }
        if (managementUi && pendingMoveBuildingModal) {
            moveBuildingConfirmOpen = true;
            pendingMoveBuildingModal = false;
        }
        commandBuilder.set(
            "#ShellTitleText.TextSpans",
            managementUi
                ? Message.translation("aetherhaven_ui_town.aetherhaven.ui.plotmanagement.title")
                : Message.translation("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.title")
        );
        boolean plotTabActive = !managementUi || managementTab == 0;
        commandBuilder.set("#ManagementTabStrip.Visible", managementUi);
        commandBuilder.set("#PlotTabContent.Visible", plotTabActive);
        commandBuilder.set("#PlayersTabContent.Visible", managementUi && managementTab == 1);
        commandBuilder.set("#MoveBuildingModal.Visible", managementUi && moveBuildingConfirmOpen);
        commandBuilder.set("#ReconstructBuildingModal.Visible", managementUi && reconstructBuildingConfirmOpen);
        commandBuilder.set("#PickUpPlotModal.Visible", !managementUi && pickUpPlotConfirmOpen);
        commandBuilder.set("#HouseResidentPickerModal.Visible", houseResidentPickerOpen);
        commandBuilder.set("#WorkplaceWorkerPickerModal.Visible", workplaceWorkerPickerOpen);
        commandBuilder.set("#VisitorPortalColorPickerModal.Visible", visitorPortalColorPickerOpen);

        ConstructionDefinition def = resolveDefinition(store, ref);
        Player player = store.getComponent(ref, Player.getComponentType());
        boolean plotReqBypassCreative = player != null && player.getGameMode() == GameMode.Creative;
        CombinedItemContainer inv = materialCombinedForPlotBlock(store, ref);

        if (def == null) {
            commandBuilder.set(
                "#BuildingTitle.TextSpans",
                managementUi
                    ? Message.translation("aetherhaven_ui_shell.aetherhaven.ui.plotConstruction.buildingTitle")
                    : Message.translation("aetherhaven_ui_shell.aetherhaven.ui.plotConstruction.plotSignTitle")
            );
            commandBuilder.set(
                "#Description.TextSpans",
                Message.translation("aetherhaven_ui_shell.aetherhaven.ui.plotConstruction.noConstruction")
            );
            commandBuilder.set("#VillagerRow.Visible", false);
            commandBuilder.set("#TreasuryRow.Visible", false);
            commandBuilder.set("#HouseResidentRow.Visible", false);
            commandBuilder.set("#TouristManifestRow.Visible", false);
            commandBuilder.set("#GuardManifestRow.Visible", false);
            commandBuilder.set("#WorkplaceAssignSection.Visible", false);
            commandBuilder.clear(WORKPLACE_ASSIGN_ROLE_ROWS);
            commandBuilder.set("#ProductionUpgradeTreeSlot.Visible", false);
            commandBuilder.set("#MaterialsHeader.Visible", false);
            commandBuilder.set("#MaterialsProgress.Visible", false);
            commandBuilder.set("#MaterialsScroll.Visible", false);
            commandBuilder.set("#PlotActionRow.Visible", false);
            commandBuilder.set("#ManagementPlotActions.Visible", false);
            commandBuilder.clear(MATERIALS_GRID);
            commandBuilder.set("#BuildButton.Disabled", true);
            commandBuilder.set("#PickUpPlotButton.Visible", false);
            commandBuilder.set("#TabNeedsButton.Disabled", true);
            commandBuilder.set("#TabMoveButton.Disabled", true);
            if (managementUi) {
                commandBuilder.set("#TabPlotButton.Disabled", managementTab == 0);
                commandBuilder.set("#TabPlayersButton.Disabled", managementTab == 1);
                bindManagementTabEvents(eventBuilder, false);
                if (managementTab == 1) {
                    buildManagementPlayersTab(ref, store, commandBuilder, eventBuilder);
                } else {
                    commandBuilder.clear(MEMBER_ROWS);
                }
            }
            return;
        }

        PlotInstanceState state = resolvePlotState(store, ref);
        boolean completed = state == PlotInstanceState.COMPLETE;
        boolean assembling = state == PlotInstanceState.ASSEMBLING;
        boolean hideConstructionDetails = managementUi && completed;

        commandBuilder.set("#BuildingTitle.TextSpans", Message.raw(def.getDisplayName()));
        String desc = def.getDescription() != null ? def.getDescription() : "";
        if (assembling && !managementUi) {
            commandBuilder.set(
                "#Description.TextSpans",
                Message.translation("aetherhaven_ui_shell.aetherhaven.ui.plotConstruction.assemblingHint")
            );
            commandBuilder.set("#Description.Visible", true);
        } else {
            if (completed && !hideConstructionDetails) {
                desc = desc.isEmpty() ? "Construction complete." : desc + "\n\nConstruction complete.";
            }
            commandBuilder.set("#Description.TextSpans", Message.raw(desc));
            commandBuilder.set("#Description.Visible", !desc.isBlank());
        }

        commandBuilder.set("#VillagerRow.Visible", false);

        EffectiveBuildingCosts effectiveCosts = resolveEffectiveCosts(store, def);
        List<MaterialRequirement> requiredMaterials = effectiveCosts.getMaterials();
        long goldCost = effectiveCosts.getTreasuryGoldCoinCost();
        PlotInstance blueprintPlot = resolveBlueprintPlot(store, ref);
        TownRecord treasuryTown =
            !managementUi && !completed && goldCost > 0 ? resolveTownForPlotSign(store, ref) : null;
        UUIDComponent ucComp = store.getComponent(ref, UUIDComponent.getComponentType());
        UUID playerUuid = ucComp != null ? ucComp.getUuid() : null;
        boolean treasuryPerm =
            treasuryTown != null && playerUuid != null && treasuryTown.playerCanSpendTreasuryGold(playerUuid);
        long spendableGold =
            treasuryTown != null && inv != null
                ? GoldCoinPayment.totalAvailable(treasuryTown, inv, treasuryPerm)
                : inv != null ? InventoryMaterials.count(inv, AetherhavenConstants.ITEM_GOLD_COIN) : 0L;
        boolean treasuryOk =
            completed
                || goldCost <= 0
                || plotReqBypassCreative
                || (treasuryTown != null
                    && inv != null
                    && GoldCoinPayment.canAfford(treasuryTown, inv, goldCost, treasuryPerm));
        boolean showTreasury = !hideConstructionDetails && goldCost > 0;
        commandBuilder.set("#TreasuryRow.Visible", showTreasury);
        if (showTreasury) {
            commandBuilder.set(
                "#TreasuryLabel.TextSpans",
                Message.translation("aetherhaven_ui_shell.aetherhaven.ui.plotConstruction.treasuryGold")
                    .param("available", String.valueOf(spendableGold))
                    .param("required", String.valueOf(goldCost))
            );
            commandBuilder.set(
                "#TreasuryLabel.Style.TextColor",
                plotReqBypassCreative || spendableGold >= goldCost ? "#3d913f" : "#962f2f"
            );
        }

        boolean showMaterials = !hideConstructionDetails && !requiredMaterials.isEmpty();
        boolean showPlotActions = !managementUi && !completed && !assembling;
        boolean showDeposit =
            showMaterials && showPlotActions && !plotReqBypassCreative && blueprintPlot != null;
        commandBuilder.set("#MaterialsHeader.Visible", showMaterials);
        commandBuilder.set("#MaterialsScroll.Visible", showMaterials);
        commandBuilder.set("#PlotActionRow.Visible", showPlotActions);
        commandBuilder.set("#DepositMaterialsButton.Visible", showDeposit);
        if (showMaterials && blueprintPlot != null) {
            buildMaterialsGrid(commandBuilder, eventBuilder, blueprintPlot, requiredMaterials, completed, plotReqBypassCreative);
        } else {
            commandBuilder.clear(MATERIALS_GRID);
            commandBuilder.set("#MaterialsProgress.Visible", false);
        }

        boolean matsOk =
            completed
                || plotReqBypassCreative
                || (blueprintPlot != null && PlotMaterialDepositService.allDeposited(blueprintPlot, requiredMaterials));
        boolean canBuild = !managementUi && !completed && matsOk && treasuryOk;
        commandBuilder.set("#BuildButton.Visible", showPlotActions);
        commandBuilder.set("#BuildButton.Disabled", !canBuild);

        boolean canPickupPlot =
            def.getPlotTokenItemId() != null && !def.getPlotTokenItemId().isBlank();
        commandBuilder.set("#PickUpPlotButton.Visible", showPlotActions);
        commandBuilder.set("#PickUpPlotButton.Disabled", !canPickupPlot);

        boolean needsMoveTabsOk = managementUi && completed;
        commandBuilder.set("#TabNeedsButton.Disabled", !needsMoveTabsOk);
        commandBuilder.set("#TabMoveButton.Disabled", !needsMoveTabsOk);
        TownRecord mgmtTown = managementUi ? resolveManagementTown(store) : null;
        AetherhavenPlugin plugWork = AetherhavenPlugin.get();
        boolean isTownHall =
            plugWork != null
                && plugWork.getConstructionCatalog().matchesGameplayConstruction(
                    def.getId(),
                    AetherhavenConstants.CONSTRUCTION_PLOT_TOWN_HALL
                );
        boolean showExpandTerritory =
            managementUi
                && completed
                && isTownHall
                && mgmtTown != null
                && playerUuid != null
                && mgmtTown.playerCanClaimTerritoryExpansion(playerUuid);
        commandBuilder.set("#ExpandTerritoryButton.Visible", showExpandTerritory);
        commandBuilder.set("#ExpandTerritoryButton.Disabled", !showExpandTerritory);
        boolean canReconstruct =
            managementUi
                && completed
                && plotTabActive
                && !def.isWallSegment()
                && !def.isDecorationPlot()
                && playerUuid != null
                && mgmtTown != null
                && mgmtTown.playerCanPlacePlots(playerUuid);
        commandBuilder.set("#ManagementPlotActions.Visible", canReconstruct || showExpandTerritory);
        if (managementUi) {
            commandBuilder.set("#TabPlotButton.Disabled", managementTab == 0);
            commandBuilder.set("#TabPlayersButton.Disabled", managementTab == 1);
        }

        boolean showHouseResident =
            managementUi
                && completed
                && plugWork != null
                && plugWork.getConstructionCatalog().matchesGameplayConstruction(
                    def.getId(),
                    AetherhavenConstants.CONSTRUCTION_PLOT_HOUSE
                );

        List<String> gameplayIds = List.of();
        if (plugWork != null) {
            gameplayIds = plugWork.getConstructionCatalog().resolveGameplayConstructionIds(def.getId());
        }
        List<String> workplaceRoles =
            plugWork != null
                ? ProductionWorkplaceKinds.residentBindingKindsForPlot(plugWork.getConstructionCatalog(), def.getId())
                : List.of();
        boolean showWorkplaceAssign = managementUi && completed && !workplaceRoles.isEmpty();
        boolean multiWorkplaceLabels = workplaceRoles.size() > 1;
        boolean showProductionUpgrades =
            managementUi
                && completed
                && plotTabActive
                && gameplayIds.stream().anyMatch(ProductionCatalog::isProductionWorkplaceConstruction)
                && AetherhavenFeatures.isLoaded(AetherhavenPluginIds.PRODUCTION);
        boolean showRestaurantUpgrades =
            managementUi
                && completed
                && plotTabActive
                && gameplayIds.stream().anyMatch(AetherhavenConstants.CONSTRUCTION_PLOT_RESTAURANT::equals);
        boolean showTouristManifest =
            managementUi
                && completed
                && plotTabActive
                && plugWork != null
                && plugWork.getConstructionCatalog().matchesGameplayConstruction(
                    def.getId(),
                    AetherhavenConstants.CONSTRUCTION_PLOT_TOURIST_PORTAL
                );
        boolean showGuardManifest =
            managementUi
                && completed
                && plotTabActive
                && plugWork != null
                && plugWork.getConstructionCatalog().matchesGameplayConstruction(
                    def.getId(),
                    AetherhavenConstants.CONSTRUCTION_PLOT_GUILD_HALL
                );

        commandBuilder.set("#HouseResidentRow.Visible", showHouseResident);
        commandBuilder.set("#TouristManifestRow.Visible", showTouristManifest);
        commandBuilder.set("#GuardManifestRow.Visible", showGuardManifest);
        commandBuilder.set("#WorkplaceAssignSection.Visible", showWorkplaceAssign);
        if (!showWorkplaceAssign) {
            commandBuilder.clear(WORKPLACE_ASSIGN_ROLE_ROWS);
        }
        boolean bothPerkTrees = showProductionUpgrades && showRestaurantUpgrades;
        commandBuilder.set("#PerkTreeTabStrip.Visible", bothPerkTrees);
        commandBuilder.set("#ProductionUpgradeTreeSlot.Visible", showProductionUpgrades || showRestaurantUpgrades);
        boolean showProductionTreeNow = showProductionUpgrades && (!bothPerkTrees || perkTreeTab == 0);
        boolean showRestaurantTreeNow = showRestaurantUpgrades && (!bothPerkTrees || perkTreeTab == 1);
        if (!showProductionTreeNow) {
            commandBuilder.set("#ProductionUpgradeTreeSlot #ProductionUpgradeTree.Visible", false);
        }
        if (!showRestaurantTreeNow) {
            commandBuilder.set("#ProductionUpgradeTreeSlot #RestaurantUpgradeTree.Visible", false);
        }

        Store<ChunkStore> csMb = blockRef.getStore();
        ManagementBlock mbHouse = csMb.getComponent(blockRef, ManagementBlock.getComponentType());
        UUID plotUuidMgmt = null;
        UUID townUuidMgmt = null;
        if (mbHouse != null && mbHouse.getPlotId() != null && !mbHouse.getPlotId().isBlank()) {
            try {
                plotUuidMgmt = UUID.fromString(mbHouse.getPlotId().trim());
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (mbHouse != null && mbHouse.getTownId() != null && !mbHouse.getTownId().isBlank()) {
            try {
                townUuidMgmt = UUID.fromString(mbHouse.getTownId().trim());
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (showWorkplaceAssign && plotTabActive && plotUuidMgmt != null && townUuidMgmt != null && plugWork != null) {
            commandBuilder.set(
                "#WorkplaceAssignHint.TextSpans",
                multiWorkplaceLabels
                    ? Message.translation("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.guildHallWorkplaceAssignHint")
                    : Message.translation("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.workplaceAssignHint")
            );
            World worldW = store.getExternalData().getWorld();
            TownManager tmw = AetherhavenWorldRegistries.getOrCreateTownManager(worldW, plugWork);
            TownRecord townW = tmw.getTown(townUuidMgmt);
            buildWorkplaceAssignRoleRows(
                store,
                commandBuilder,
                eventBuilder,
                townW,
                plugWork,
                def.getId(),
                plotUuidMgmt,
                workplaceRoles,
                multiWorkplaceLabels
            );
            if (workplaceWorkerPickerOpen && workplacePickerResidentKind != null) {
                buildWorkplaceWorkerPickerModal(
                    store,
                    commandBuilder,
                    eventBuilder,
                    townW,
                    plugWork,
                    gameplayWorkplaceIdForRole(
                        plugWork.getConstructionCatalog(),
                        def.getId(),
                        workplacePickerResidentKind
                    ),
                    plotUuidMgmt,
                    workplacePickerFilterNpcRoleId,
                    workplacePickerResidentKind
                );
            }
        }

        if (bothPerkTrees) {
            commandBuilder.set(
                "#PerkTabProductionButton.TextSpans",
                Message.translation("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.perkTabProduction")
            );
            commandBuilder.set(
                "#PerkTabRestaurantButton.TextSpans",
                Message.translation("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.perkTabRestaurant")
            );
            commandBuilder.set("#PerkTabProductionButton.Disabled", perkTreeTab == 0);
            commandBuilder.set("#PerkTabRestaurantButton.Disabled", perkTreeTab == 1);
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#PerkTabProductionButton",
                new EventData().append("Action", "SwitchPerkTabProduction"),
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#PerkTabRestaurantButton",
                new EventData().append("Action", "SwitchPerkTabRestaurant"),
                false
            );
        }

        if (showProductionTreeNow && plotUuidMgmt != null && townUuidMgmt != null && plugWork != null) {
            World worldPu = store.getExternalData().getWorld();
            TownManager tmpu = AetherhavenWorldRegistries.getOrCreateTownManager(worldPu, plugWork);
            TownRecord townPu = tmpu.getTown(townUuidMgmt);
            CombinedItemContainer invPu =
                player != null ? InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING) : null;
            if (townPu != null && invPu != null && ucComp != null) {
                PlotProductionState prodState = townPu.getOrCreatePlotProduction(plotUuidMgmt);
                prodState.migrateIfNeeded();
                boolean allowTreasury = townPu.playerCanSpendTreasuryGold(ucComp.getUuid());
                ProductionUpgradeTreeUi.bind(commandBuilder, eventBuilder, prodState, townPu, invPu, allowTreasury);
            }
        }

        if (showRestaurantTreeNow && plotUuidMgmt != null && townUuidMgmt != null && plugWork != null) {
            World worldRu = store.getExternalData().getWorld();
            TownManager tmru = AetherhavenWorldRegistries.getOrCreateTownManager(worldRu, plugWork);
            TownRecord townRu = tmru.getTown(townUuidMgmt);
            CombinedItemContainer invRu =
                player != null ? InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING) : null;
            if (townRu != null && invRu != null && ucComp != null) {
                PlotRestaurantState restaurantState = townRu.getOrCreatePlotRestaurant(plotUuidMgmt);
                restaurantState.migrateIfNeeded();
                boolean allowTreasury = townRu.playerCanSpendTreasuryGold(ucComp.getUuid());
                RestaurantUpgradeTreeUi.bind(commandBuilder, eventBuilder, restaurantState, townRu, invRu, allowTreasury);
            }
        }

        if (showHouseResident && plotTabActive) {
            buildHouseResidentSection(
                store,
                commandBuilder,
                eventBuilder,
                plotUuidMgmt,
                townUuidMgmt,
                plugWork
            );
        }

        if (showTouristManifest && townUuidMgmt != null && plugWork != null) {
            buildTouristManifestSection(
                ref,
                store,
                commandBuilder,
                eventBuilder,
                townUuidMgmt,
                plugWork,
                playerUuid
            );
        }

        if (showGuardManifest && townUuidMgmt != null && plugWork != null) {
            buildGuardManifestSection(store, commandBuilder, townUuidMgmt, plugWork);
        }

        if (showDeposit) {
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#DepositMaterialsButton",
                new EventData().append("Action", "DepositMaterials"),
                false
            );
        }
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#BuildButton",
            new EventData().append("Action", "Build"),
            false
        );
        if (!managementUi && canPickupPlot) {
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#PickUpPlotButton",
                new EventData().append("Action", "BeginPickUpPlot"),
                false
            );
        }

        if (managementUi) {
            bindManagementTabEvents(eventBuilder, needsMoveTabsOk);
            if (canReconstruct) {
                eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#ReconstructBuildingButton",
                    new EventData().append("Action", "BeginReconstructBuilding"),
                    false
                );
            }
            if (showExpandTerritory) {
                eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#ExpandTerritoryButton",
                    new EventData().append("Action", "OpenTerritoryExpansion"),
                    false
                );
            }
            if (managementTab == 1) {
                buildManagementPlayersTab(ref, store, commandBuilder, eventBuilder);
            } else {
                commandBuilder.clear(MEMBER_ROWS);
            }
        }
        if (managementUi && moveBuildingConfirmOpen) {
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#MoveBuildingConfirmButton",
                new EventData().append("Action", "ConfirmMoveBuilding"),
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#MoveBuildingCancelButton",
                new EventData().append("Action", "CancelMoveBuilding"),
                false
            );
        }
        if (managementUi && reconstructBuildingConfirmOpen) {
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ReconstructBuildingConfirmButton",
                new EventData().append("Action", "ConfirmReconstructBuilding"),
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ReconstructBuildingCancelButton",
                new EventData().append("Action", "CancelReconstructBuilding"),
                false
            );
        }
        if (!managementUi && pickUpPlotConfirmOpen) {
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#PickUpPlotConfirmButton",
                new EventData().append("Action", "ConfirmPickUpPlot"),
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#PickUpPlotCancelButton",
                new EventData().append("Action", "CancelPickUpPlot"),
                false
            );
        }
    }

    private void bindManagementTabEvents(@Nonnull UIEventBuilder eventBuilder, boolean needsMoveTabsEnabled) {
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#TabPlotButton",
            new EventData().append("Action", "SwitchTabPlot"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#TabPlayersButton",
            new EventData().append("Action", "SwitchTabPlayers"),
            false
        );
        if (needsMoveTabsEnabled) {
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#TabNeedsButton",
                new EventData().append("Action", "OpenTownNeeds"),
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#TabMoveButton",
                new EventData().append("Action", "BeginMoveBuilding"),
                false
            );
        }
    }

    private void buildTouristManifestSection(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull UUID townUuid,
        @Nonnull AetherhavenPlugin plugin,
        @Nullable UUID playerUuid
    ) {
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townUuid);
        commandBuilder.clear(TOURIST_MANIFEST_ROWS);
        if (town == null) {
            commandBuilder.set("#TouristManifestEmpty.Visible", true);
            commandBuilder.set("#TouristManifestScroll.Visible", false);
            commandBuilder.set("#ClearVisitingTouristsButton.Visible", false);
            commandBuilder.set("#VisitorPortalTravelRow.Visible", false);
            return;
        }
        Store<EntityStore> entityStore = world.getEntityStore().getStore();
        List<TouristVisitManifest.Row> rows = TouristVisitManifest.listRows(town, entityStore, plugin);
        if (rows.isEmpty()) {
            commandBuilder.set("#TouristManifestEmpty.Visible", true);
            commandBuilder.set("#TouristManifestScroll.Visible", false);
        } else {
            commandBuilder.set("#TouristManifestEmpty.Visible", false);
            commandBuilder.set("#TouristManifestScroll.Visible", true);
            int n = Math.min(rows.size(), MAX_TOURIST_MANIFEST_ROWS);
            for (int i = 0; i < n; i++) {
                TouristVisitManifest.Row row = rows.get(i);
                String rowPath = TOURIST_MANIFEST_ROWS + "[" + i + "]";
                commandBuilder.append(TOURIST_MANIFEST_ROWS, "Aetherhaven/TouristManifestRow.ui");
                commandBuilder.set(rowPath + " #Portrait.AssetPath", row.portraitPath());
                commandBuilder.set(rowPath + " #NameLabel.TextSpans", Message.raw(row.label()));
                commandBuilder.set(rowPath + " #StatusLabel.Visible", true);
                commandBuilder.set(rowPath + " #StatusLabel.TextSpans", touristManifestStatus(row.kind()));
            }
        }
        boolean canClear = playerUuid != null && town.playerCanPlacePlots(playerUuid);
        commandBuilder.set("#ClearVisitingTouristsButton.Visible", canClear);
        commandBuilder.set("#ClearVisitingTouristsButton.Disabled", !canClear);
        commandBuilder.set("#VisitorPortalTravelRow.Visible", canClear);
        if (canClear) {
            commandBuilder.set("#VisitorPortalTravelToggle.Value", town.isAllowVisitorPortalTravel());
            commandBuilder.set("#VisitorPortalTravelToggle.Disabled", false);
            String portalColor = TownPortalTravelColor.resolveHex(town);
            TownPortalTravelColor.applyTeleportIconTint(commandBuilder, "#VisitorPortalColorPreviewIcon", portalColor);
            commandBuilder.set("#ChooseVisitorPortalColorButton.Disabled", false);
            TownPortalTravelColorPickerUi.bindOpenButton(eventBuilder, VP_COLOR_CHOOSE_BTN);
            TownPortalTravelColorPickerUi.bindCloseButton(eventBuilder, VP_COLOR_CANCEL_BTN);
            if (visitorPortalColorPickerOpen) {
                TownPortalTravelColorPickerUi.buildPresetGrid(
                    commandBuilder,
                    eventBuilder,
                    VP_COLOR_GRID,
                    TownPortalTravelColor.normalizePresetHex(portalColor)
                );
            }
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#VisitorPortalTravelToggle",
                new EventData()
                    .append("Action", "SetVisitorPortalTravel")
                    .append("@AllowVisitorPortalTravel", "#VisitorPortalTravelToggle.Value"),
                false
            );
        }
        if (canClear) {
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ClearVisitingTouristsButton",
                new EventData().append("Action", "ClearVisitingTourists"),
                false
            );
        }
    }

    private void buildGuardManifestSection(
        @Nonnull Store<EntityStore> store,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UUID townUuid,
        @Nonnull AetherhavenPlugin plugin
    ) {
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townUuid);
        commandBuilder.clear(GUARD_MANIFEST_ROWS);
        if (town == null) {
            commandBuilder.set("#GuardManifestHeader.TextSpans", Message.raw(""));
            commandBuilder.set("#GuardManifestEmpty.Visible", true);
            commandBuilder.set("#GuardManifestScroll.Visible", false);
            return;
        }
        int current = town.getHiredGuardRecords().size();
        int max = TownRankCapacity.maxHiredGuards(town, plugin.getQuestBoardCatalog());
        commandBuilder.set(
            "#GuardManifestHeader.TextSpans",
            Message.translation("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.guardManifestHeader")
                .param("current", String.valueOf(current))
                .param("max", String.valueOf(max))
        );
        Store<EntityStore> entityStore = world.getEntityStore().getStore();
        List<GuardHireManifest.Row> rows = GuardHireManifest.listRows(town, entityStore, plugin);
        if (rows.isEmpty()) {
            commandBuilder.set("#GuardManifestEmpty.Visible", true);
            commandBuilder.set("#GuardManifestScroll.Visible", false);
        } else {
            commandBuilder.set("#GuardManifestEmpty.Visible", false);
            commandBuilder.set("#GuardManifestScroll.Visible", true);
            int n = Math.min(rows.size(), MAX_GUARD_MANIFEST_ROWS);
            for (int i = 0; i < n; i++) {
                GuardHireManifest.Row row = rows.get(i);
                String rowPath = GUARD_MANIFEST_ROWS + "[" + i + "]";
                commandBuilder.append(GUARD_MANIFEST_ROWS, "Aetherhaven/TouristManifestRow.ui");
                commandBuilder.set(rowPath + " #Portrait.AssetPath", row.portraitPath());
                commandBuilder.set(rowPath + " #NameLabel.TextSpans", Message.raw(row.label()));
                commandBuilder.set(rowPath + " #StatusLabel.Visible", true);
                commandBuilder.set(rowPath + " #StatusLabel.TextSpans", guardManifestStatus(row.guardRoleId(), row.housed()));
            }
        }
    }

    private void handleSetVisitorPortalTravel(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nullable Boolean allow
    ) {
        if (!managementUi || allow == null) {
            return;
        }
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            return;
        }
        TownRecord town = resolveManagementTown(store);
        if (town == null) {
            playerRef.sendMessage(Message.translation("aetherhaven_common.aetherhaven.common.townNotFound"));
            return;
        }
        if (!town.playerCanPlacePlots(uc.getUuid())) {
            playerRef.sendMessage(
                Message.translation("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.visitorPortalTravelNoPermission")
            );
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            playerRef.sendMessage(Message.translation("aetherhaven_common.aetherhaven.common.pluginNotLoaded"));
            return;
        }
        World world = store.getExternalData().getWorld();
        town.setAllowVisitorPortalTravel(allow);
        AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).updateTown(town);
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder ev = new UIEventBuilder();
        build(ref, cmd, ev, store);
        sendUpdate(cmd, ev, false);
    }

    private void handleSetVisitorPortalColorPick(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nullable String presetHex
    ) {
        if (!managementUi || presetHex == null) {
            return;
        }
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            return;
        }
        TownRecord town = resolveManagementTown(store);
        if (town == null) {
            playerRef.sendMessage(Message.translation("aetherhaven_common.aetherhaven.common.townNotFound"));
            return;
        }
        if (!town.playerCanPlacePlots(uc.getUuid())) {
            playerRef.sendMessage(
                Message.translation("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.visitorPortalTravelNoPermission")
            );
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            playerRef.sendMessage(Message.translation("aetherhaven_common.aetherhaven.common.pluginNotLoaded"));
            return;
        }
        World world = store.getExternalData().getWorld();
        TownPortalTravelColor.applyStoredHex(town, presetHex);
        AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).updateTown(town);
        TownBorderMapOverlayService.refreshPlayer(world, uc.getUuid());
        visitorPortalColorPickerOpen = false;
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder ev = new UIEventBuilder();
        build(ref, cmd, ev, store);
        sendUpdate(cmd, ev, false);
    }

    @Nonnull
    private static Message touristManifestStatus(@Nonnull TouristVisitManifest.ManifestKind kind) {
        return switch (kind) {
            case VISITING ->
                Message.translation("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.touristManifestVisiting");
            case INVITED ->
                Message.translation("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.touristManifestInvited");
            case HOUSED ->
                Message.translation("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.touristManifestHoused");
        };
    }

    @Nonnull
    private static Message guardManifestStatus(@Nonnull String guardRoleId, boolean housed) {
        String key =
            housed
                ? "aetherhaven_ui_town.aetherhaven.ui.plotconstruction.guardManifestStatusHoused"
                : "aetherhaven_ui_town.aetherhaven.ui.plotconstruction.guardManifestStatusUnhoused";
        return Message.translation(key).param("type", Message.translation(GuardRoleLabels.guardTypeLangKey(guardRoleId)));
    }

    private void buildManagementPlayersTab(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder
    ) {
        World world = store.getExternalData().getWorld();
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (plugin == null || uc == null) {
            commandBuilder.set(
                "#PlayersHint.TextSpans",
                Message.translation("aetherhaven_ui_shell.aetherhaven.ui.plotConstruction.couldNotLoadMembers")
            );
            commandBuilder.clear(MEMBER_ROWS);
            return;
        }
        TownRecord town = resolveManagementTown(store);
        if (town == null) {
            commandBuilder.set(
                "#PlayersHint.TextSpans",
                Message.translation("aetherhaven_ui_shell.aetherhaven.ui.plotConstruction.townDataMissing")
            );
            commandBuilder.clear(MEMBER_ROWS);
            return;
        }
        UUID viewer = uc.getUuid();
        boolean viewerOwner = town.getOwnerUuid().equals(viewer);
        commandBuilder.set(
            "#PlayersHint.TextSpans",
            viewerOwner
                ? Message.translation("aetherhaven_ui_town.aetherhaven.ui.plotmanagement.playersHint")
                : Message.translation("aetherhaven_ui_town.aetherhaven.ui.plotmanagement.playersHintReadOnly")
        );
        commandBuilder.set("#InviteLabel.Visible", viewerOwner);
        commandBuilder.set("#InvitePlayerInput.Visible", viewerOwner);
        commandBuilder.set("#InviteSendButton.Visible", viewerOwner);
        if (viewerOwner) {
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#InviteSendButton",
                new EventData().append("Action", "InviteMember").append("@InviteName", "#InvitePlayerInput.Value"),
                false
            );
        }

        commandBuilder.clear(MEMBER_ROWS);

        List<UUID> ordered = new ArrayList<>();
        ordered.add(town.getOwnerUuid());
        List<UUID> mem = new ArrayList<>(town.getMemberPlayerUuids());
        mem.sort(Comparator.comparing(u -> TownPlayerLookup.displayNameForUuid(world, u), String.CASE_INSENSITIVE_ORDER));
        ordered.addAll(mem);

        int n = Math.min(ordered.size(), MAX_MEMBER_ROWS);
        for (int i = 0; i < n; i++) {
            UUID pid = ordered.get(i);
            boolean isOwner = pid.equals(town.getOwnerUuid());
            String rowPath = MEMBER_ROWS + "[" + i + "]";
            commandBuilder.append(MEMBER_ROWS, "Aetherhaven/TownMemberRow.ui");
            String display = TownPlayerLookup.displayNameForUuid(world, pid);
            commandBuilder.set(rowPath + " #OpenMemberName #NameLabel.TextSpans", Message.raw(display));
            if (viewerOwner) {
                commandBuilder.set(rowPath + " #OpenMemberName.Disabled", false);
                eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    rowPath + " #OpenMemberName",
                    new EventData().append("Action", "OpenMemberPermissions").append("MemberUuid", pid.toString()),
                    false
                );
            } else {
                commandBuilder.set(rowPath + " #OpenMemberName.Disabled", true);
            }
            if (isOwner) {
                commandBuilder.set(rowPath + " #RoleReadOnly.Visible", true);
                commandBuilder.set(rowPath + " #RoleReadOnly.TextSpans", Message.translation("aetherhaven_ui_town.aetherhaven.ui.plotmanagement.roleOwner"));
                commandBuilder.set(rowPath + " #KickButton.Visible", false);
            } else {
                TownMemberRole role = town.getMemberRoleOrNull(pid);
                String roleName = role != null ? role.name() : TownMemberRole.BOTH.name();
                commandBuilder.set(rowPath + " #RoleReadOnly.Visible", true);
                commandBuilder.set(rowPath + " #RoleReadOnly.TextSpans", Message.raw(roleName));
                if (viewerOwner) {
                    commandBuilder.set(rowPath + " #KickButton.Visible", true);
                    commandBuilder.set(
                        rowPath + " #KickButton.TextSpans",
                        Message.translation("aetherhaven_ui_town.aetherhaven.ui.plotmanagement.kick")
                    );
                    eventBuilder.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        rowPath + " #KickButton",
                        new EventData().append("Action", "KickMember").append("MemberUuid", pid.toString()),
                        false
                    );
                } else {
                    commandBuilder.set(rowPath + " #KickButton.Visible", false);
                }
            }
        }
    }

    private void buildHouseResidentSection(
        @Nonnull Store<EntityStore> store,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nullable UUID plotUuidMgmt,
        @Nullable UUID townUuidMgmt,
        @Nullable AetherhavenPlugin plugWork
    ) {
        commandBuilder.set(
            "#HouseResidentHint.TextSpans",
            Message.translation("aetherhaven_ui_shell.aetherhaven.ui.plotConstruction.assignResidentHint")
        );
        commandBuilder.set("#HouseResidentHideElsewhereCheckbox #CheckBox.Value", hideHouseResidentElsewhereHoused);
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#HouseResidentHideElsewhereCheckbox #CheckBox",
            EventData.of("@HouseResidentHideElsewhere", "#HouseResidentHideElsewhereCheckbox #CheckBox.Value"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#HouseResidentPickerCancelButton",
            new EventData().append("Action", "CloseHouseResidentPicker"),
            false
        );

        String langU = playerRef.getLanguage() != null ? playerRef.getLanguage() : "en-US";
        String unassignedLabel =
            I18nModule.get().getMessage(langU, "aetherhaven_ui_shell.aetherhaven.ui.plotConstruction.houseResidentUnassigned");
        if (unassignedLabel == null || unassignedLabel.isEmpty()) {
            unassignedLabel = "Unassigned";
        }

        int maxSlots = 1;
        PlotInstance plotInstance = null;
        TownRecord town = null;
        List<HouseResidentDirectory.HouseResidentRow> assignable = List.of();
        if (plotUuidMgmt != null && townUuidMgmt != null && plugWork != null) {
            World world = store.getExternalData().getWorld();
            TownManager townManager = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugWork);
            town = townManager.getTown(townUuidMgmt);
            if (town != null) {
                plotInstance = town.findPlotById(plotUuidMgmt);
                if (plotInstance != null) {
                    ConstructionDefinition houseDef = plugWork.getConstructionCatalog().get(plotInstance.getConstructionId());
                    if (houseDef != null) {
                        maxSlots = houseDef.getMaxHomeResidents();
                    }
                }
                assignable =
                    HouseResidentDirectory.listAssignable(
                        store,
                        town,
                        plugWork,
                        plotUuidMgmt,
                        hideHouseResidentElsewhereHoused
                    );
            }
        }

        commandBuilder.clear(HOUSE_RESIDENT_SLOT_ROWS);
        for (int slot = 0; slot < maxSlots; slot++) {
            commandBuilder.append(HOUSE_RESIDENT_SLOT_ROWS, "Aetherhaven/HouseResidentSlotRow.ui");
            String slotPath = HOUSE_RESIDENT_SLOT_ROWS + "[" + slot + "]";
            commandBuilder.set(
                slotPath + " #SlotLabel.TextSpans",
                Message.translation("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.houseResidentSlot")
                    .param("n", String.valueOf(slot + 1))
            );
            commandBuilder.set(
                slotPath + " #ChooseHouseResidentButton.TextSpans",
                Message.translation("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.chooseHouseResident")
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                slotPath + " #ChooseHouseResidentButton",
                new EventData().append("Action", "OpenHouseResidentPicker").append("HouseResidentSlot", String.valueOf(slot)),
                false
            );

            UUID slotResident = plotInstance != null ? plotInstance.getHomeResidentAt(slot) : null;
            if (slotResident == null) {
                commandBuilder.set(slotPath + " #HouseResidentSelection #PreviewPortrait.Visible", false);
                commandBuilder.set(slotPath + " #HouseResidentSelection #PreviewNameLine.TextSpans", Message.raw(unassignedLabel));
                commandBuilder.set(slotPath + " #HouseResidentSelection #PreviewRoleLine.Visible", false);
            } else {
                HouseResidentDirectory.HouseResidentRow preview =
                    HouseResidentDirectory.findRow(assignable, slotResident);
                if (preview == null && town != null && plugWork != null) {
                    preview = HouseResidentDirectory.resolvePreviewRow(store, town, plugWork, slotResident);
                }
                if (preview != null) {
                    commandBuilder.set(slotPath + " #HouseResidentSelection #PreviewPortrait.Visible", true);
                    commandBuilder.set(slotPath + " #HouseResidentSelection #PreviewPortrait.AssetPath", preview.portraitPath());
                    commandBuilder.set(
                        slotPath + " #HouseResidentSelection #PreviewNameLine.TextSpans",
                        Message.raw(preview.displayName())
                    );
                    commandBuilder.set(slotPath + " #HouseResidentSelection #PreviewRoleLine.Visible", true);
                    commandBuilder.set(slotPath + " #HouseResidentSelection #PreviewRoleLine.TextSpans", preview.roleLine());
                } else {
                    commandBuilder.set(slotPath + " #HouseResidentSelection #PreviewPortrait.Visible", false);
                    commandBuilder.set(slotPath + " #HouseResidentSelection #PreviewNameLine.TextSpans", Message.raw(unassignedLabel));
                    commandBuilder.set(slotPath + " #HouseResidentSelection #PreviewRoleLine.Visible", false);
                }
            }
        }

        if (!houseResidentPickerOpen) {
            return;
        }

        Set<UUID> occupiedOtherSlots = new HashSet<>();
        if (plotInstance != null) {
            for (int slot = 0; slot < maxSlots; slot++) {
                if (slot == activeHouseResidentSlot) {
                    continue;
                }
                UUID u = plotInstance.getHomeResidentAt(slot);
                if (u != null) {
                    occupiedOtherSlots.add(u);
                }
            }
        }

        commandBuilder.clear(HOUSE_RESIDENT_ROWS);
        int rowIndex = 0;
        commandBuilder.append(HOUSE_RESIDENT_ROWS, "Aetherhaven/HouseResidentAssignRow.ui");
        String unassignedRow = HOUSE_RESIDENT_ROWS + "[" + rowIndex + "]";
        AetherhavenUiLocalization.applyHouseResidentAssignRow(commandBuilder, unassignedRow);
        commandBuilder.set(unassignedRow + " #Portrait.Visible", false);
        commandBuilder.set(unassignedRow + " #NameLine.TextSpans", Message.raw(unassignedLabel));
        commandBuilder.set(unassignedRow + " #RoleLine.Visible", false);
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            unassignedRow + " #SelectButton",
            new EventData().append("Action", "SelectHouseResident").append("HouseResidentUuid", ""),
            false
        );
        rowIndex++;
        for (HouseResidentDirectory.HouseResidentRow row : assignable) {
            if (occupiedOtherSlots.contains(row.entityUuid())) {
                continue;
            }
            commandBuilder.append(HOUSE_RESIDENT_ROWS, "Aetherhaven/HouseResidentAssignRow.ui");
            String rowPath = HOUSE_RESIDENT_ROWS + "[" + rowIndex + "]";
            AetherhavenUiLocalization.applyHouseResidentAssignRow(commandBuilder, rowPath);
            commandBuilder.set(rowPath + " #Portrait.AssetPath", row.portraitPath());
            commandBuilder.set(rowPath + " #NameLine.TextSpans", Message.raw(row.displayName()));
            commandBuilder.set(rowPath + " #RoleLine.TextSpans", row.roleLine());
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                rowPath + " #SelectButton",
                new EventData()
                    .append("Action", "SelectHouseResident")
                    .append("HouseResidentUuid", row.entityUuid().toString()),
                false
            );
            rowIndex++;
        }
    }

    private void applyHouseResidentSelection(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nullable UUID residentUuid
    ) {
        if (!managementUi) {
            return;
        }
        PlotInstanceState st = resolvePlotState(store, ref);
        if (st != PlotInstanceState.COMPLETE) {
            return;
        }
        ConstructionDefinition def = resolveDefinition(store, ref);
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (def == null
            || plugin == null
            || !plugin
                .getConstructionCatalog()
                .matchesGameplayConstruction(def.getId(), AetherhavenConstants.CONSTRUCTION_PLOT_HOUSE)) {
            return;
        }
        if (activeHouseResidentSlot < 0 || activeHouseResidentSlot >= def.getMaxHomeResidents()) {
            return;
        }
        Store<ChunkStore> cs = blockRef.getStore();
        ManagementBlock mb = cs.getComponent(blockRef, ManagementBlock.getComponentType());
        if (mb == null || mb.getPlotId().isBlank() || mb.getTownId().isBlank()) {
            return;
        }
        UUID plotId;
        UUID townId;
        try {
            plotId = UUID.fromString(mb.getPlotId().trim());
            townId = UUID.fromString(mb.getTownId().trim());
        } catch (IllegalArgumentException e) {
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townId);
        if (town == null) {
            return;
        }
        HouseResidentAssignment.assignResident(
            town,
            plotId,
            activeHouseResidentSlot,
            residentUuid,
            tm,
            world,
            store,
            plugin.getConstructionCatalog()
        );
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (pr != null) {
            pr.sendMessage(
                residentUuid == null
                    ? Message.translation("aetherhaven_ui_shell.aetherhaven.ui.plotConstruction.clearedHome")
                    : Message.translation("aetherhaven_ui_shell.aetherhaven.ui.plotConstruction.updatedHome")
            );
        }
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder ev = new UIEventBuilder();
        build(ref, cmd, ev, store);
        sendUpdate(cmd, ev, false);
    }

    private void handleClearVisitingTourists(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (!managementUi) {
            return;
        }
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            return;
        }
        TownRecord town = resolveManagementTown(store);
        if (town == null) {
            playerRef.sendMessage(Message.translation("aetherhaven_common.aetherhaven.common.townNotFound"));
            return;
        }
        if (!town.playerCanPlacePlots(uc.getUuid())) {
            playerRef.sendMessage(
                Message.translation("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.clearVisitingTouristsNoPermission")
            );
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            playerRef.sendMessage(Message.translation("aetherhaven_common.aetherhaven.common.pluginNotLoaded"));
            return;
        }
        World world = store.getExternalData().getWorld();
        TownRecord townRun = town;
        world.execute(
            () -> {
                if (isDismissed() || !ref.isValid()) {
                    return;
                }
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player == null || player.getPageManager().getCustomPage() != this) {
                    return;
                }
                Store<EntityStore> entityStore = world.getEntityStore().getStore();
                TouristPortalTickService.TouristTownPurgeResult result =
                    TouristPortalTickService.purgeActiveTouristsInTown(townRun, world, plugin, entityStore);
                if (result.removed() <= 0) {
                    playerRef.sendMessage(
                        Message.translation("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.clearVisitingTouristsNone")
                    );
                } else {
                    playerRef.sendMessage(
                        Message
                            .translation("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.clearVisitingTouristsDone")
                            .param("removed", String.valueOf(result.removed()))
                            .param("skippedProtected", String.valueOf(result.skippedProtected()))
                    );
                }
                refreshPage(ref, store);
            }
        );
    }

    @Nullable
    private TownRecord resolveManagementTown(@Nonnull Store<EntityStore> store) {
        if (!managementUi) {
            return null;
        }
        Store<ChunkStore> cs = blockRef.getStore();
        ManagementBlock mb = cs.getComponent(blockRef, ManagementBlock.getComponentType());
        if (mb == null || mb.getTownId() == null || mb.getTownId().isBlank()) {
            return null;
        }
        try {
            UUID tid = UUID.fromString(mb.getTownId().trim());
            AetherhavenPlugin p = AetherhavenPlugin.get();
            if (p == null) {
                return null;
            }
            World world = store.getExternalData().getWorld();
            return AetherhavenWorldRegistries.getOrCreateTownManager(world, p).getTown(tid);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        if (data.houseResidentHideElsewhere != null) {
            hideHouseResidentElsewhereHoused = data.houseResidentHideElsewhere;
            UUID prefKey = playerRef.getUuid();
            if (prefKey != null) {
                HOUSE_RESIDENT_HIDE_ELSEWHERE_PREF.put(prefKey, hideHouseResidentElsewhereHoused);
            }
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (data.action != null && data.action.equalsIgnoreCase("SetVisitorPortalTravel")) {
            handleSetVisitorPortalTravel(ref, store, data.allowVisitorPortalTravel);
            return;
        }
        if (TownPortalTravelColorPickerUi.ACTION_OPEN.equalsIgnoreCase(data.action)) {
            visitorPortalColorPickerOpen = true;
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (TownPortalTravelColorPickerUi.ACTION_CLOSE.equalsIgnoreCase(data.action)) {
            visitorPortalColorPickerOpen = false;
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (TownPortalTravelColorPickerUi.ACTION_PICK.equalsIgnoreCase(data.action)) {
            handleSetVisitorPortalColorPick(ref, store, data.presetHex);
            return;
        }
        if (data.action != null && data.action.equalsIgnoreCase("ClearVisitingTourists")) {
            handleClearVisitingTourists(ref, store);
            return;
        }
        if (data.action != null && data.action.equalsIgnoreCase("OpenHouseResidentPicker")) {
            int slot = 0;
            if (data.houseResidentSlot != null && !data.houseResidentSlot.isBlank()) {
                try {
                    slot = Integer.parseInt(data.houseResidentSlot.trim());
                } catch (NumberFormatException e) {
                    return;
                }
            }
            if (slot < 0) {
                return;
            }
            activeHouseResidentSlot = slot;
            houseResidentPickerOpen = true;
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (data.action != null && data.action.equalsIgnoreCase("CloseHouseResidentPicker")) {
            houseResidentPickerOpen = false;
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (data.action != null && data.action.equalsIgnoreCase("SelectHouseResident")) {
            UUID residentUuid = null;
            String rawSelect = data.houseResidentUuid;
            if (rawSelect != null && !rawSelect.isBlank()) {
                try {
                    residentUuid = UUID.fromString(rawSelect.trim());
                } catch (IllegalArgumentException e) {
                    return;
                }
            }
            houseResidentPickerOpen = false;
            applyHouseResidentSelection(ref, store, residentUuid);
            return;
        }
        if (data.action != null && data.action.equalsIgnoreCase("OpenWorkplaceWorkerPicker")) {
            if (data.workplacePickerResidentKind == null || data.workplacePickerResidentKind.isBlank()) {
                return;
            }
            workplacePickerResidentKind = data.workplacePickerResidentKind.trim();
            workplacePickerFilterNpcRoleId =
                data.workplacePickerFilterNpcRoleId != null && !data.workplacePickerFilterNpcRoleId.isBlank()
                    ? data.workplacePickerFilterNpcRoleId.trim()
                    : null;
            workplaceWorkerPickerOpen = true;
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (data.action != null && data.action.equalsIgnoreCase("CloseWorkplaceWorkerPicker")) {
            workplaceWorkerPickerOpen = false;
            workplacePickerResidentKind = null;
            workplacePickerFilterNpcRoleId = null;
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (data.action != null && data.action.equalsIgnoreCase("SelectWorkplaceWorker")) {
            String kind = data.workplacePickerResidentKind;
            if (kind == null || kind.isBlank()) {
                return;
            }
            UUID workerUuid = null;
            String rawSelect = data.workplaceWorkerUuid;
            if (rawSelect != null && !rawSelect.isBlank()) {
                try {
                    workerUuid = UUID.fromString(rawSelect.trim());
                } catch (IllegalArgumentException e) {
                    return;
                }
            }
            workplaceWorkerPickerOpen = false;
            workplacePickerResidentKind = null;
            workplacePickerFilterNpcRoleId = null;
            handleWorkplaceAssignment(ref, store, workerUuid != null ? workerUuid.toString() : "", kind.trim());
            return;
        }
        if (data.action != null && data.action.equalsIgnoreCase("BeginMoveBuilding")) {
            if (!managementUi) {
                return;
            }
            PlotInstanceState stBegin = resolvePlotState(store, ref);
            if (stBegin != PlotInstanceState.COMPLETE) {
                return;
            }
            moveBuildingConfirmOpen = true;
            reconstructBuildingConfirmOpen = false;
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (data.action != null && data.action.equalsIgnoreCase("CancelMoveBuilding")) {
            moveBuildingConfirmOpen = false;
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (data.action != null && data.action.equalsIgnoreCase("ConfirmMoveBuilding")) {
            if (!managementUi) {
                return;
            }
            moveBuildingConfirmOpen = false;
            PlotInstanceState stMove = resolvePlotState(store, ref);
            if (stMove != PlotInstanceState.COMPLETE) {
                UICommandBuilder cmd = new UICommandBuilder();
                UIEventBuilder ev = new UIEventBuilder();
                build(ref, cmd, ev, store);
                sendUpdate(cmd, ev, false);
                return;
            }
            Store<ChunkStore> csMove = blockRef.getStore();
            ManagementBlock mbMove = csMove.getComponent(blockRef, ManagementBlock.getComponentType());
            if (mbMove == null || mbMove.getPlotId().isBlank() || mbMove.getTownId().isBlank()) {
                return;
            }
            UUID plotIdMove;
            UUID townIdMove;
            try {
                plotIdMove = UUID.fromString(mbMove.getPlotId().trim());
                townIdMove = UUID.fromString(mbMove.getTownId().trim());
            } catch (IllegalArgumentException e) {
                return;
            }
            Player playerMove = store.getComponent(ref, Player.getComponentType());
            if (playerMove != null) {
                PlotPlacementPage placementPage = PlotPlacementOpenHelper.openForMove(ref, store, playerRef, townIdMove, plotIdMove);
                if (placementPage != null) {
                    playerMove.getPageManager().openCustomPage(ref, store, placementPage);
                }
            }
            return;
        }
        if (data.action != null && data.action.equalsIgnoreCase("BeginReconstructBuilding")) {
            if (!managementUi) {
                return;
            }
            PlotInstanceState stReconBegin = resolvePlotState(store, ref);
            if (stReconBegin != PlotInstanceState.COMPLETE) {
                return;
            }
            reconstructBuildingConfirmOpen = true;
            moveBuildingConfirmOpen = false;
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (data.action != null && data.action.equalsIgnoreCase("CancelReconstructBuilding")) {
            reconstructBuildingConfirmOpen = false;
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (data.action != null && data.action.equalsIgnoreCase("ConfirmReconstructBuilding")) {
            if (!managementUi) {
                return;
            }
            PlotInstanceState stRecon = resolvePlotState(store, ref);
            if (stRecon != PlotInstanceState.COMPLETE) {
                reconstructBuildingConfirmOpen = false;
                refreshPage(ref, store);
                return;
            }
            UUIDComponent ucRecon = store.getComponent(ref, UUIDComponent.getComponentType());
            if (ucRecon == null) {
                reconstructBuildingConfirmOpen = false;
                refreshPage(ref, store);
                return;
            }
            Store<ChunkStore> csRecon = blockRef.getStore();
            ManagementBlock mbRecon = csRecon.getComponent(blockRef, ManagementBlock.getComponentType());
            if (mbRecon == null || mbRecon.getPlotId().isBlank() || mbRecon.getTownId().isBlank()) {
                reconstructBuildingConfirmOpen = false;
                refreshPage(ref, store);
                return;
            }
            UUID plotIdRecon;
            UUID townIdRecon;
            try {
                plotIdRecon = UUID.fromString(mbRecon.getPlotId().trim());
                townIdRecon = UUID.fromString(mbRecon.getTownId().trim());
            } catch (IllegalArgumentException e) {
                reconstructBuildingConfirmOpen = false;
                refreshPage(ref, store);
                return;
            }
            AetherhavenPlugin pluginRecon = AetherhavenPlugin.get();
            if (pluginRecon == null) {
                reconstructBuildingConfirmOpen = false;
                playerRef.sendMessage(Message.translation("aetherhaven_common.aetherhaven.common.pluginNotLoaded"));
                refreshPage(ref, store);
                return;
            }
            World worldRecon = store.getExternalData().getWorld();
            TownManager tmRecon = AetherhavenWorldRegistries.getOrCreateTownManager(worldRecon, pluginRecon);
            TownRecord townRecon = tmRecon.getTown(townIdRecon);
            if (townRecon == null) {
                reconstructBuildingConfirmOpen = false;
                playerRef.sendMessage(Message.translation("aetherhaven_common.aetherhaven.common.townNotFound"));
                refreshPage(ref, store);
                return;
            }
            if (!townRecon.playerCanPlacePlots(ucRecon.getUuid())) {
                reconstructBuildingConfirmOpen = false;
                playerRef.sendMessage(Message.translation("aetherhaven_common.aetherhaven.common.noMoveBuildingsPermission"));
                refreshPage(ref, store);
                return;
            }
            PlotInstance plotRecon = townRecon.findPlotById(plotIdRecon);
            if (plotRecon == null) {
                reconstructBuildingConfirmOpen = false;
                refreshPage(ref, store);
                return;
            }
            reconstructBuildingConfirmOpen = false;
            refreshPage(ref, store);
            AetherhavenPlugin pluginReconRun = pluginRecon;
            TownRecord townReconRun = townRecon;
            PlotInstance plotReconRun = plotRecon;
            UUID actorUuid = ucRecon.getUuid();
            worldRecon.execute(
                () -> {
                    if (isDismissed() || !ref.isValid()) {
                        return;
                    }
                    Player playerRecon = store.getComponent(ref, Player.getComponentType());
                    if (playerRecon == null || playerRecon.getPageManager().getCustomPage() != this) {
                        return;
                    }
                    PlotReconstructService.ReconstructResult rr =
                        PlotReconstructService.reconstruct(
                            worldRecon, pluginReconRun, townReconRun, plotReconRun, actorUuid, store
                        );
                    playerRef.sendMessage(PlotReconstructMessages.forResult(rr));
                    refreshPage(ref, store);
                }
            );
            return;
        }
        if (data.action != null && data.action.equalsIgnoreCase("SwitchTabPlot")) {
            if (!managementUi) {
                return;
            }
            managementTab = 0;
            moveBuildingConfirmOpen = false;
            reconstructBuildingConfirmOpen = false;
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (data.action != null && data.action.equalsIgnoreCase("SwitchTabPlayers")) {
            if (!managementUi) {
                return;
            }
            managementTab = 1;
            moveBuildingConfirmOpen = false;
            reconstructBuildingConfirmOpen = false;
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (data.action != null && data.action.equalsIgnoreCase("SwitchPerkTabProduction")) {
            perkTreeTab = 0;
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (data.action != null && data.action.equalsIgnoreCase("SwitchPerkTabRestaurant")) {
            perkTreeTab = 1;
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (data.action != null && data.action.equalsIgnoreCase("OpenMemberPermissions")) {
            if (!managementUi || data.memberUuid == null) {
                return;
            }
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uc == null) {
                return;
            }
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            if (plugin == null) {
                return;
            }
            TownRecord town = resolveManagementTown(store);
            if (town == null || !town.getOwnerUuid().equals(uc.getUuid())) {
                return;
            }
            UUID targetId;
            try {
                targetId = UUID.fromString(data.memberUuid.trim());
            } catch (IllegalArgumentException e) {
                return;
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                player.getPageManager()
                    .openCustomPage(
                        ref,
                        store,
                        new TownMemberPermissionsPage(playerRef, blockRef, blockWorldPos, town.getTownId(), targetId)
                    );
            }
            return;
        }
        if (data.action != null && data.action.equalsIgnoreCase("KickMember")) {
            if (!managementUi || data.memberUuid == null) {
                return;
            }
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uc == null) {
                return;
            }
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            World world = store.getExternalData().getWorld();
            if (plugin == null) {
                return;
            }
            TownRecord town = resolveManagementTown(store);
            if (town == null || !town.getOwnerUuid().equals(uc.getUuid())) {
                return;
            }
            UUID memberId;
            try {
                memberId = UUID.fromString(data.memberUuid.trim());
            } catch (IllegalArgumentException e) {
                return;
            }
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            Message err = TownMembershipActions.tryKickMemberUuid(world, tm, town, playerRef, memberId);
            if (err != null) {
                playerRef.sendMessage(err);
            }
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (data.action != null && data.action.equalsIgnoreCase("InviteMember")) {
            if (!managementUi || data.inviteName == null) {
                return;
            }
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uc == null) {
                return;
            }
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            World world = store.getExternalData().getWorld();
            if (plugin == null) {
                return;
            }
            TownRecord town = resolveManagementTown(store);
            if (town == null || !town.getOwnerUuid().equals(uc.getUuid())) {
                return;
            }
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            Message err = TownMembershipActions.tryInviteMember(world, tm, town, uc.getUuid(), playerRef, data.inviteName);
            if (err != null) {
                playerRef.sendMessage(err);
            }
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (data.action != null && data.action.equalsIgnoreCase("PurchaseProductionUpgrade")) {
            handlePurchaseProductionUpgrade(ref, store, data.upgradeBranch);
            return;
        }
        if (data.action != null && data.action.equalsIgnoreCase("PurchaseRestaurantUpgrade")) {
            handlePurchaseRestaurantUpgrade(ref, store, data.upgradeBranch);
            return;
        }
        if (data.action != null && data.action.equalsIgnoreCase("DepositMaterial")) {
            if (managementUi || data.materialIndex == null || data.materialIndex < 0) {
                return;
            }
            handleDepositMaterial(store, ref, data.materialIndex);
            return;
        }
        if (data.action != null && data.action.equalsIgnoreCase("DepositMaterials")) {
            if (managementUi) {
                return;
            }
            handleDepositAllMaterials(store, ref);
            return;
        }
        if (data.action != null && data.action.equalsIgnoreCase("BeginPickUpPlot")) {
            if (managementUi) {
                return;
            }
            String pickUpErr = validatePickUpPlotAllowed(store, ref);
            if (pickUpErr != null) {
                sendBuildError(store, ref, pickUpErr);
                return;
            }
            pickUpPlotConfirmOpen = true;
            refreshPage(ref, store);
            return;
        }
        if (data.action != null && data.action.equalsIgnoreCase("CancelPickUpPlot")) {
            pickUpPlotConfirmOpen = false;
            refreshPage(ref, store);
            return;
        }
        if (data.action != null && data.action.equalsIgnoreCase("ConfirmPickUpPlot")) {
            if (managementUi) {
                return;
            }
            pickUpPlotConfirmOpen = false;
            String pickUpErr = validatePickUpPlotAllowed(store, ref);
            if (pickUpErr != null) {
                sendBuildError(store, ref, pickUpErr);
                refreshPage(ref, store);
                return;
            }
            executePickUpPlot(store, ref);
            return;
        }
        if (data.action != null && data.action.equalsIgnoreCase("OpenTerritoryExpansion")) {
            if (!managementUi) {
                return;
            }
            PlotInstanceState st = resolvePlotState(store, ref);
            if (st != PlotInstanceState.COMPLETE) {
                return;
            }
            Store<ChunkStore> cs = blockRef.getStore();
            ManagementBlock mb = cs.getComponent(blockRef, ManagementBlock.getComponentType());
            if (mb == null || mb.getTownId().isBlank()) {
                return;
            }
            UUID townUuid;
            try {
                townUuid = UUID.fromString(mb.getTownId().trim());
            } catch (IllegalArgumentException e) {
                return;
            }
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            World world = store.getExternalData().getWorld();
            if (plugin == null) {
                return;
            }
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            TownRecord town = tm.getTown(townUuid);
            UUID playerUuid = playerRef.getUuid();
            if (town == null || playerUuid == null || !town.playerCanClaimTerritoryExpansion(playerUuid)) {
                return;
            }
            int viewCx = ChunkUtil.chunkCoordinate(town.getCharterX());
            int viewCz = ChunkUtil.chunkCoordinate(town.getCharterZ());
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                player.getPageManager()
                    .openCustomPage(
                        ref,
                        store,
                        new TownExpansionPage(playerRef, blockRef, blockWorldPos, townUuid, viewCx, viewCz)
                    );
            }
            return;
        }
        if (data.action != null && data.action.equalsIgnoreCase("OpenTownNeeds")) {
            if (!managementUi) {
                return;
            }
            moveBuildingConfirmOpen = false;
            reconstructBuildingConfirmOpen = false;
            PlotInstanceState st = resolvePlotState(store, ref);
            if (st != PlotInstanceState.COMPLETE) {
                return;
            }
            Store<ChunkStore> cs = blockRef.getStore();
            ManagementBlock mb = cs.getComponent(blockRef, ManagementBlock.getComponentType());
            if (mb == null || mb.getTownId().isBlank()) {
                return;
            }
            UUID townUuid;
            try {
                townUuid = UUID.fromString(mb.getTownId().trim());
            } catch (IllegalArgumentException e) {
                return;
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                // openCustomPage replaces this UI; do not call close() or Page.None clears the new page.
                player.getPageManager()
                    .openCustomPage(ref, store, new VillagerNeedsOverviewPage(playerRef, townUuid, blockRef, blockWorldPos));
            }
            return;
        }
        if (data.action == null || !data.action.equalsIgnoreCase("Build")) {
            return;
        }
        if (managementUi) {
            return;
        }
        ConstructionDefinition def = resolveDefinition(store, ref);
        if (def == null) {
            return;
        }
        PlotInstanceState state = resolvePlotState(store, ref);
        if (state == PlotInstanceState.COMPLETE) {
            return;
        }
        if (state == PlotInstanceState.ASSEMBLING) {
            PlayerRef prAssembling = store.getComponent(ref, PlayerRef.getComponentType());
            if (prAssembling != null) {
                NotificationUtil.sendNotification(
                    prAssembling.getPacketHandler(),
                    Message.translation("aetherhaven_ui_shell.aetherhaven.ui.plotConstruction.buildFailedTitle"),
                    Message.translation("aetherhaven_ui_shell.aetherhaven.ui.plotConstruction.alreadyAssembling"),
                    NotificationStyle.Warning
                );
            }
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        boolean plotReqBypassCreative = player.getGameMode() == GameMode.Creative;
        EffectiveBuildingCosts effectiveCosts = resolveEffectiveCosts(store, def);
        List<MaterialRequirement> requiredMaterials = effectiveCosts.getMaterials();
        PlotInstance blueprintPlot = resolveBlueprintPlot(store, ref);
        CombinedItemContainer inv = materialCombinedForPlotBlock(store, ref);
        if (!plotReqBypassCreative
            && (blueprintPlot == null || !PlotMaterialDepositService.allDeposited(blueprintPlot, requiredMaterials))) {
            return;
        }
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            return;
        }
        Store<ChunkStore> cstore = blockRef.getStore();
        PlotSignBlock plot = cstore.getComponent(blockRef, PlotSignBlock.getComponentType());
        if (plot == null || plot.getPlotId() == null || plot.getPlotId().isBlank()) {
            sendBuildError(store, ref, "This plot sign has no plot id (legacy); replace the sign.");
            return;
        }
        UUID plotId;
        try {
            plotId = UUID.fromString(plot.getPlotId().trim());
        } catch (IllegalArgumentException e) {
            sendBuildError(store, ref, "Invalid plot id on sign.");
            return;
        }
        AetherhavenPlugin pluginEarly = AetherhavenPlugin.get();
        if (pluginEarly == null) {
            return;
        }
        World worldEarly = store.getExternalData().getWorld();
        TownManager tmEarly = AetherhavenWorldRegistries.getOrCreateTownManager(worldEarly, pluginEarly);
        TownRecord townEarly = tmEarly.findTownOwningPlot(plotId);
        PlotInstance plotEarly = townEarly != null ? townEarly.findPlotById(plotId) : null;
        if (townEarly == null || plotEarly == null) {
            sendBuildError(store, ref, "This plot is not registered in your town.");
            return;
        }
        Rotation yawEarly = plotEarly.resolvePrefabYaw();
        Vector3i anchorEarly = plotEarly.resolvePrefabAnchorWorld(def);
        Path prefabPathEarly = PrefabResolveUtil.resolvePrefabPath(def.getPrefabPath());
        if (prefabPathEarly == null) {
            sendBuildError(store, ref, "Prefab not found for path: " + def.getPrefabPath());
            return;
        }
        long goldCost = effectiveCosts.getTreasuryGoldCoinCost();
        if (goldCost > 0 && !plotReqBypassCreative) {
            TownRecord tr = tmEarly.findTownOwningPlot(plotId);
            if (tr == null) {
                sendBuildError(store, ref, "No town owns this plot.");
                return;
            }
            boolean allowTreasury = tr.playerCanSpendTreasuryGold(uc.getUuid());
            if (inv == null || !GoldCoinPayment.canAfford(tr, inv, goldCost, allowTreasury)) {
                sendBuildError(store, ref, "Not enough gold (inventory + town treasury).");
                return;
            }
        }

        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        Vector3i physicalSignPos = blockWorldPos;
        TownRecord townBuild = townEarly;
        PlotInstance buildPlot = plotEarly;
        Rotation yaw = yawEarly;
        Vector3i anchor = anchorEarly;
        IPrefabBuffer buffer = PrefabBufferUtil.getCached(prefabPathEarly);
        UUID ownerUuid = uc.getUuid();
        boolean creativeBypass = plotReqBypassCreative;
        long goldCostFinal = goldCost;
        world.execute(
            () -> {
                PlotAssemblyBuildStartResult result =
                    PlotAssemblyService.startFromBuildClick(
                        plugin,
                        world,
                        townBuild,
                        buildPlot,
                        physicalSignPos,
                        ownerUuid,
                        anchor,
                        yaw,
                        def,
                        buffer,
                        goldCostFinal,
                        creativeBypass
                    );
                if (result != PlotAssemblyBuildStartResult.OK) {
                    notifyBuildStartFailed(world, ownerUuid, result);
                }
            }
        );
        close();
    }

    private static void notifyBuildStartFailed(
        @Nonnull World world,
        @Nonnull UUID playerUuid,
        @Nonnull PlotAssemblyBuildStartResult result
    ) {
        if (world.getEntityStore() == null) {
            return;
        }
        Store<EntityStore> entityStore = world.getEntityStore().getStore();
        Ref<EntityStore> ref = entityStore.getExternalData().getRefFromUUID(playerUuid);
        if (ref == null || !ref.isValid()) {
            return;
        }
        PlayerRef pr = entityStore.getComponent(ref, PlayerRef.getComponentType());
        if (pr == null) {
            return;
        }
        String key =
            switch (result) {
                case ALREADY_ASSEMBLING, ASSEMBLY_ALREADY_ACTIVE ->
                    "aetherhaven_ui_shell.aetherhaven.ui.plotConstruction.alreadyAssembling";
                case PASTE_CANCELLED -> "aetherhaven_ui_shell.aetherhaven.ui.plotConstruction.buildFailedPaste";
                case PAYMENT_FAILED -> "aetherhaven_ui_shell.aetherhaven.ui.plotConstruction.buildFailedPayment";
                case BUILDER_UNAVAILABLE -> "aetherhaven_ui_shell.aetherhaven.ui.plotConstruction.buildFailedUnavailable";
                case OK -> null;
            };
        if (key != null) {
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation("aetherhaven_ui_shell.aetherhaven.ui.plotConstruction.buildFailedTitle"),
                Message.translation(key),
                NotificationStyle.Warning
            );
        }
    }

    /** Player {@link InventoryComponent#EVERYTHING} plus chests in the same volume vanilla benches search. */
    @Nullable
    private CombinedItemContainer materialCombinedForPlotBlock(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        if (store.getComponent(ref, Player.getComponentType()) == null) {
            return null;
        }
        World world = store.getExternalData().getWorld();
        return BenchAdjacentChestUtil.combinedPlayerAndAdjacentChestsForBlock(
            world, store, ref, blockWorldPos.x, blockWorldPos.y, blockWorldPos.z
        );
    }

    private void refreshPage(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (managementUi && !blockRef.isValid()) {
            close();
            return;
        }
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder ev = new UIEventBuilder();
        build(ref, cmd, ev, store);
        sendUpdate(cmd, ev, false);
    }

    /** Returns an error message when pick-up is not allowed, or null if it may proceed. */
    @Nullable
    private String validatePickUpPlotAllowed(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        if (managementUi) {
            return null;
        }
        if (resolvePlotState(store, ref) == PlotInstanceState.COMPLETE) {
            return null;
        }
        ConstructionDefinition def = resolveDefinition(store, ref);
        if (def == null) {
            return null;
        }
        String tokenId = def.getPlotTokenItemId();
        if (tokenId == null || tokenId.isBlank()) {
            return null;
        }
        Store<ChunkStore> cstore = blockRef.getStore();
        PlotSignBlock plot = cstore.getComponent(blockRef, PlotSignBlock.getComponentType());
        if (plot == null || plot.getPlotId() == null || plot.getPlotId().isBlank()) {
            return "This plot sign has no plot id (legacy); replace the sign.";
        }
        UUID plotId;
        try {
            plotId = UUID.fromString(plot.getPlotId().trim());
        } catch (IllegalArgumentException e) {
            return "Invalid plot id on sign.";
        }
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            return null;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return null;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.findTownOwningPlot(plotId);
        if (town == null || !town.getOwnerUuid().equals(uc.getUuid())) {
            return "Only the town owner can pick up this plot.";
        }
        if (town.findPlotById(plotId) == null) {
            return "This plot is not registered in your town.";
        }
        if (store.getComponent(ref, Player.getComponentType()) == null) {
            return null;
        }
        return null;
    }

    private void executePickUpPlot(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        ConstructionDefinition def = resolveDefinition(store, ref);
        if (def == null) {
            return;
        }
        String tokenId = def.getPlotTokenItemId();
        if (tokenId == null || tokenId.isBlank()) {
            return;
        }
        Store<ChunkStore> cstore = blockRef.getStore();
        PlotSignBlock plot = cstore.getComponent(blockRef, PlotSignBlock.getComponentType());
        if (plot == null || plot.getPlotId() == null || plot.getPlotId().isBlank()) {
            return;
        }
        UUID plotId;
        try {
            plotId = UUID.fromString(plot.getPlotId().trim());
        } catch (IllegalArgumentException e) {
            return;
        }
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.findTownOwningPlot(plotId);
        if (town == null || !town.getOwnerUuid().equals(uc.getUuid())) {
            return;
        }
        PlotInstance piPickup = town.findPlotById(plotId);
        List<MaterialRequirement> refunded =
            piPickup != null ? PlotMaterialDepositService.refundAll(piPickup) : List.of();
        if (!town.removePlotInstance(plotId)) {
            sendBuildError(store, ref, "Could not remove plot from town data.");
            return;
        }
        tm.updateTown(town);
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        Vector3d dropPos =
            tc != null
                ? new Vector3d(tc.getPosition())
                : new Vector3d(blockWorldPos.x + 0.5, blockWorldPos.y, blockWorldPos.z + 0.5);
        if (!refunded.isEmpty()) {
            PlotMaterialDepositService.refundToPlayer(player, ref, store, refunded, dropPos);
        }
        world.breakBlock(blockWorldPos.x, blockWorldPos.y, blockWorldPos.z, BREAK_SETTINGS);
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (def.consumesPlotToken()) {
            String language = "en-US";
            if (pr != null && pr.getLanguage() != null && !pr.getLanguage().isBlank()) {
                language = pr.getLanguage();
            }
            ItemStack tokenStack = PlotTokenInventory.createTokenStackForDefinition(def, language);
            ItemStackTransaction giveTx = player.giveItem(tokenStack, ref, store);
            if (giveTx.succeeded()) {
                PlotTokenIconSync.afterTokenGranted(pr);
            }
            if (!giveTx.succeeded() || !ItemStack.isEmpty(giveTx.getRemainder())) {
                List<ItemStack> tokenOverflow = new ArrayList<>();
                if (!giveTx.succeeded()) {
                    tokenOverflow.add(tokenStack);
                } else {
                    tokenOverflow.add(giveTx.getRemainder());
                }
                PlotMaterialDepositService.refundItemStacksToPlayer(player, ref, store, tokenOverflow, dropPos);
            }
        }
        if (pr != null) {
            pr.sendMessage(Message.translation("aetherhaven_ui_shell.aetherhaven.ui.plotConstruction.plotRemoved"));
        }
        close();
    }

    private void sendBuildError(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull String text) {
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (pr != null) {
            pr.sendMessage(Message.raw(text));
        }
    }

    @Nullable
    private TownRecord resolveTownForPlotSign(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        AetherhavenPlugin p = AetherhavenPlugin.get();
        if (p == null) {
            return null;
        }
        Store<ChunkStore> cs = blockRef.getStore();
        PlotSignBlock plot = cs.getComponent(blockRef, PlotSignBlock.getComponentType());
        if (plot == null || plot.getPlotId() == null || plot.getPlotId().isBlank()) {
            return null;
        }
        UUID plotId;
        try {
            plotId = UUID.fromString(plot.getPlotId().trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, p);
        return tm.findTownOwningPlot(plotId);
    }

    @Nullable
    private ConstructionDefinition resolveDefinition(@Nonnull Store<EntityStore> entityStore, @Nonnull Ref<EntityStore> playerRef) {
        AetherhavenPlugin p = AetherhavenPlugin.get();
        if (p == null) {
            return null;
        }
        if (!blockRef.isValid()) {
            return null;
        }
        Store<ChunkStore> cs = blockRef.getStore();
        World world = entityStore.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, p);

        if (managementUi) {
            ManagementBlock mb = cs.getComponent(blockRef, ManagementBlock.getComponentType());
            if (mb == null) {
                logManagementResolveFailure("no-component", null, null, false, false, null);
                return null;
            }
            if (mb.getTownId().isBlank() || mb.getPlotId().isBlank()) {
                logManagementResolveFailure("blank-ids", mb.getPlotId(), mb.getTownId(), false, false, null);
                return null;
            }
            try {
                UUID townUuid = UUID.fromString(mb.getTownId().trim());
                UUID plotUuid = UUID.fromString(mb.getPlotId().trim());
                TownRecord town = tm.getTown(townUuid);
                if (town == null) {
                    logManagementResolveFailure("town-missing", mb.getPlotId(), mb.getTownId(), false, false, null);
                    return null;
                }
                PlotInstance pi = town.findPlotById(plotUuid);
                if (pi == null) {
                    logManagementResolveFailure(
                        "plot-row-missing",
                        mb.getPlotId(),
                        mb.getTownId(),
                        true,
                        false,
                        null
                    );
                    return null;
                }
                ConstructionDefinition def = p.getConstructionCatalog().get(pi.getConstructionId());
                if (def == null) {
                    logManagementResolveFailure(
                        "unknown-construction",
                        mb.getPlotId(),
                        mb.getTownId(),
                        true,
                        true,
                        pi.getConstructionId()
                    );
                }
                return def;
            } catch (IllegalArgumentException e) {
                logManagementResolveFailure("bad-uuid", mb.getPlotId(), mb.getTownId(), false, false, null);
                return null;
            }
        }

        PlotSignBlock plot = cs.getComponent(blockRef, PlotSignBlock.getComponentType());
        if (plot == null) {
            return null;
        }
        return p.getConstructionCatalog().get(plot.getConstructionId());
    }

    private void logManagementResolveFailure(
        @Nonnull String reason,
        @Nullable String plotId,
        @Nullable String townId,
        boolean townExists,
        boolean plotRowExists,
        @Nullable String constructionId
    ) {
        LOGGER.atWarning().log(
            "Management UI resolveDefinition failed reason=%s plotId=%s townId=%s townExists=%s plotRowExists=%s constructionId=%s pos=%s,%s,%s",
            reason,
            plotId != null ? plotId : "",
            townId != null ? townId : "",
            townExists,
            plotRowExists,
            constructionId != null ? constructionId : "",
            blockWorldPos.x,
            blockWorldPos.y,
            blockWorldPos.z
        );
    }

    private void handleDepositAllMaterials(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        ConstructionDefinition def = resolveDefinition(store, ref);
        if (def == null) {
            return;
        }
        PlotInstance plot = resolveBlueprintPlot(store, ref);
        CombinedItemContainer inv = materialCombinedForPlotBlock(store, ref);
        if (plot == null || inv == null) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        EffectiveBuildingCosts costs = resolveEffectiveCosts(store, def);
        for (MaterialRequirement line : costs.getMaterials()) {
            if (line.getCount() > 0) {
                PlotMaterialDepositService.depositFromContainer(plot, line, inv);
            }
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = resolveTownForPlotSign(store, ref);
        if (town != null) {
            tm.updateTown(town);
        }
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder ev = new UIEventBuilder();
        build(ref, cmd, ev, store);
        sendUpdate(cmd, ev, false);
    }

    @Nonnull
    private void handleDepositMaterial(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, int materialIndex) {
        ConstructionDefinition def = resolveDefinition(store, ref);
        if (def == null) {
            return;
        }
        EffectiveBuildingCosts costs = resolveEffectiveCosts(store, def);
        List<MaterialRequirement> required = costs.getMaterials();
        if (materialIndex < 0 || materialIndex >= required.size()) {
            return;
        }
        PlotInstance plot = resolveBlueprintPlot(store, ref);
        CombinedItemContainer inv = materialCombinedForPlotBlock(store, ref);
        if (plot == null || inv == null) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        MaterialRequirement line = required.get(materialIndex);
        int deposited = PlotMaterialDepositService.depositFromContainer(plot, line, inv);
        if (deposited <= 0) {
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = resolveTownForPlotSign(store, ref);
        if (town != null) {
            tm.updateTown(town);
        }
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder ev = new UIEventBuilder();
        build(ref, cmd, ev, store);
        sendUpdate(cmd, ev, false);
    }

    private void buildMaterialsGrid(
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull PlotInstance plot,
        @Nonnull List<MaterialRequirement> required,
        boolean completed,
        boolean creativeBypass
    ) {
        commandBuilder.clear(MATERIALS_GRID);
        List<Integer> sourceIndices = new ArrayList<>();
        List<MaterialRequirement> lines = new ArrayList<>();
        for (int i = 0; i < required.size(); i++) {
            MaterialRequirement line = required.get(i);
            if (line.getCount() > 0) {
                sourceIndices.add(i);
                lines.add(line);
            }
        }
        int totalLines = lines.size();
        int ready = 0;
        for (MaterialRequirement line : lines) {
            if (PlotMaterialDepositService.depositedCount(plot, line) >= line.getCount()) {
                ready++;
            }
        }
        if (totalLines == 0) {
            commandBuilder.set("#MaterialsProgress.Visible", false);
            return;
        }
        int numRows = (totalLines + MATERIAL_GRID_COLS - 1) / MATERIAL_GRID_COLS;
        for (int r = 0; r < numRows; r++) {
            commandBuilder.append(MATERIALS_GRID, "Aetherhaven/PlotConstructionMaterialRow.ui");
            String rowBase = MATERIALS_GRID + "[" + r + "]";
            for (int c = 0; c < MATERIAL_GRID_COLS; c++) {
                int idx = r * MATERIAL_GRID_COLS + c;
                if (idx >= totalLines) {
                    break;
                }
                MaterialRequirement line = lines.get(idx);
                int sourceIndex = sourceIndices.get(idx);
                int deposited = PlotMaterialDepositService.depositedCount(plot, line);
                commandBuilder.append(rowBase + " #Strip", "Aetherhaven/PlotConstructionMaterialCell.ui");
                String cell = rowBase + " #Strip[" + c + "]";
                String iconPath = UiMaterialIcons.assetPathFor(line);
                if (iconPath != null && !iconPath.isBlank()) {
                    commandBuilder.set(cell + " #IconBox #Icon.AssetPath", iconPath);
                }
                boolean ok = completed || creativeBypass || deposited >= line.getCount();
                commandBuilder.set(
                    cell + ".TooltipTextSpans",
                    Message.raw(UiMaterialLabels.displayLabelFor(line) + "\n" + deposited + " / " + line.getCount())
                );
                commandBuilder.set(cell + " #Count.TextSpans", Message.raw(deposited + " / " + line.getCount()));
                commandBuilder.set(cell + " #Count.Style.TextColor", ok ? "#3d913f" : "#c8a060");
                if (!completed && !creativeBypass) {
                    eventBuilder.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        cell,
                        new EventData().append("Action", "DepositMaterial").append("MaterialIndex", String.valueOf(sourceIndex)),
                        false
                    );
                }
            }
        }
        boolean showProgress = !completed && !creativeBypass;
        commandBuilder.set("#MaterialsProgress.Visible", showProgress);
        if (showProgress) {
            commandBuilder.set(
                "#MaterialsProgress.TextSpans",
                Message.translation("aetherhaven_ui_shell.aetherhaven.ui.plotConstruction.materialsProgress")
                    .param("ready", String.valueOf(ready))
                    .param("total", String.valueOf(totalLines))
            );
        }
    }

    @Nonnull
    private EffectiveBuildingCosts resolveEffectiveCosts(@Nonnull Store<EntityStore> store, @Nonnull ConstructionDefinition def) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return EffectiveBuildingCosts.forDefinition(def, WorldDifficultyState.normalUntilChosen(), PrefabMaterialsCatalog.empty());
        }
        World world = store.getExternalData().getWorld();
        WorldDifficultyState difficulty = AetherhavenWorldRegistries.getOrLoadWorldDifficulty(world, plugin);
        return EffectiveBuildingCosts.forDefinition(def, difficulty, plugin.getPrefabMaterialsCatalog());
    }

    @Nullable
    private PlotInstance resolveBlueprintPlot(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        AetherhavenPlugin p = AetherhavenPlugin.get();
        if (p == null) {
            return null;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, p);
        Store<ChunkStore> cs = blockRef.getStore();
        if (managementUi) {
            return null;
        }
        PlotSignBlock plot = cs.getComponent(blockRef, PlotSignBlock.getComponentType());
        if (plot == null || plot.getPlotId() == null || plot.getPlotId().isBlank()) {
            return null;
        }
        UUID plotId;
        try {
            plotId = UUID.fromString(plot.getPlotId().trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
        TownRecord town = tm.findTownOwningPlot(plotId);
        if (town == null) {
            return null;
        }
        return town.findPlotById(plotId);
    }

    private PlotInstanceState resolvePlotState(@Nonnull Store<EntityStore> entityStore, @Nonnull Ref<EntityStore> playerRef) {
        AetherhavenPlugin p = AetherhavenPlugin.get();
        if (p == null) {
            return PlotInstanceState.BLUEPRINTING;
        }
        World world = entityStore.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, p);
        Store<ChunkStore> cs = blockRef.getStore();

        if (managementUi) {
            if (!blockRef.isValid()) {
                return PlotInstanceState.ASSEMBLING;
            }
            ManagementBlock mb = cs.getComponent(blockRef, ManagementBlock.getComponentType());
            if (mb == null || mb.getTownId().isBlank() || mb.getPlotId().isBlank()) {
                return PlotInstanceState.COMPLETE;
            }
            try {
                TownRecord town = tm.getTown(UUID.fromString(mb.getTownId().trim()));
                if (town == null) {
                    return PlotInstanceState.COMPLETE;
                }
                PlotInstance pi = town.findPlotById(UUID.fromString(mb.getPlotId().trim()));
                return pi != null ? pi.getState() : PlotInstanceState.COMPLETE;
            } catch (IllegalArgumentException e) {
                return PlotInstanceState.COMPLETE;
            }
        }

        PlotSignBlock plot = cs.getComponent(blockRef, PlotSignBlock.getComponentType());
        if (plot == null || plot.getPlotId() == null || plot.getPlotId().isBlank()) {
            return PlotInstanceState.BLUEPRINTING;
        }
        UUID plotId;
        try {
            plotId = UUID.fromString(plot.getPlotId().trim());
        } catch (IllegalArgumentException e) {
            return PlotInstanceState.BLUEPRINTING;
        }
        TownRecord town = tm.findTownOwningPlot(plotId);
        if (town == null) {
            return PlotInstanceState.BLUEPRINTING;
        }
        PlotInstance pi = town.findPlotById(plotId);
        return pi != null ? pi.getState() : PlotInstanceState.BLUEPRINTING;
    }

    private void handlePurchaseProductionUpgrade(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nullable String branchRaw
    ) {
        if (!AetherhavenFeatures.isLoaded(AetherhavenPluginIds.PRODUCTION)) {
            return;
        }
        if (!managementUi) {
            return;
        }
        Branch branch = ProductionUpgradeTreeUi.parseBranch(branchRaw);
        if (branch == null) {
            return;
        }
        PlotInstanceState st = resolvePlotState(store, ref);
        if (st != PlotInstanceState.COMPLETE) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (plugin == null || player == null || pr == null || uc == null) {
            return;
        }
        Store<ChunkStore> cs = blockRef.getStore();
        ManagementBlock mb = cs.getComponent(blockRef, ManagementBlock.getComponentType());
        if (mb == null || mb.getPlotId().isBlank() || mb.getTownId().isBlank()) {
            return;
        }
        UUID plotId;
        UUID townId;
        try {
            plotId = UUID.fromString(mb.getPlotId().trim());
            townId = UUID.fromString(mb.getTownId().trim());
        } catch (IllegalArgumentException e) {
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townId);
        if (town == null || !town.playerCanManageConstructions(uc.getUuid())) {
            return;
        }
        PlotInstance plot = town.findPlotById(plotId);
        String storedId = plot != null ? plot.getConstructionId() : "";
        boolean productionOk =
            plot != null
                && plot.getState() == PlotInstanceState.COMPLETE
                && plugin.getConstructionCatalog().resolveGameplayConstructionIds(storedId).stream()
                    .anyMatch(ProductionCatalog::isProductionWorkplaceConstruction);
        if (!productionOk) {
            return;
        }
        CombinedItemContainer inv = InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING);
        if (inv == null) {
            return;
        }
        PlotProductionState state = town.getOrCreatePlotProduction(plotId);
        state.migrateIfNeeded();
        boolean allowTreasury = town.playerCanSpendTreasuryGold(uc.getUuid());
        PurchaseResult result = WorkplaceProductionUpgrades.tryPurchase(state, branch, town, inv, allowTreasury);
        if (result == PurchaseResult.OK) {
            tm.updateTown(town);
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation("aetherhaven_ui_town.aetherhaven.ui.productionUpgrades.notify.purchased"),
                NotificationStyle.Success
            );
        } else {
            String notifyKey =
                switch (result) {
                    case NEED_INGOT -> "aetherhaven.ui.productionUpgrades.notify.needIngot";
                    case NEED_GOLD -> "aetherhaven.ui.productionUpgrades.notify.needGold";
                    case PREREQUISITES -> "aetherhaven.ui.productionUpgrades.notify.locked";
                    case MAXED -> "aetherhaven.ui.productionUpgrades.notify.maxed";
                    default -> "aetherhaven.ui.productionUpgrades.notify.failed";
                };
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation("aetherhaven_ui_town." + notifyKey),
                NotificationStyle.Warning
            );
        }
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder ev = new UIEventBuilder();
        build(ref, cmd, ev, store);
        sendUpdate(cmd, ev, false);
    }

    private void handlePurchaseRestaurantUpgrade(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nullable String branchRaw
    ) {
        if (!managementUi) {
            return;
        }
        RestaurantUpgrades.Branch branch = RestaurantUpgradeTreeUi.parseBranch(branchRaw);
        if (branch == null) {
            return;
        }
        PlotInstanceState st = resolvePlotState(store, ref);
        if (st != PlotInstanceState.COMPLETE) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (plugin == null || player == null || pr == null || uc == null) {
            return;
        }
        Store<ChunkStore> cs = blockRef.getStore();
        ManagementBlock mb = cs.getComponent(blockRef, ManagementBlock.getComponentType());
        if (mb == null || mb.getPlotId().isBlank() || mb.getTownId().isBlank()) {
            return;
        }
        UUID plotId;
        UUID townId;
        try {
            plotId = UUID.fromString(mb.getPlotId().trim());
            townId = UUID.fromString(mb.getTownId().trim());
        } catch (IllegalArgumentException e) {
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townId);
        if (town == null || !town.playerCanManageConstructions(uc.getUuid())) {
            return;
        }
        PlotInstance plot = town.findPlotById(plotId);
        String storedId = plot != null ? plot.getConstructionId() : "";
        boolean restaurantOk =
            plot != null
                && plot.getState() == PlotInstanceState.COMPLETE
                && plugin.getConstructionCatalog().matchesGameplayConstruction(
                    storedId,
                    AetherhavenConstants.CONSTRUCTION_PLOT_RESTAURANT
                );
        if (!restaurantOk) {
            return;
        }
        CombinedItemContainer inv = InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING);
        if (inv == null) {
            return;
        }
        PlotRestaurantState state = town.getOrCreatePlotRestaurant(plotId);
        state.migrateIfNeeded();
        boolean allowTreasury = town.playerCanSpendTreasuryGold(uc.getUuid());
        RestaurantUpgrades.PurchaseResult result = RestaurantUpgrades.tryPurchase(state, branch, town, inv, allowTreasury);
        if (result == RestaurantUpgrades.PurchaseResult.OK) {
            tm.updateTown(town);
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation("aetherhaven_ui_town.aetherhaven.ui.restaurantUpgrades.notify.purchased"),
                NotificationStyle.Success
            );
        } else {
            String notifyKey =
                switch (result) {
                    case NEED_INGREDIENT -> "aetherhaven.ui.restaurantUpgrades.notify.needIngredient";
                    case NEED_GOLD -> "aetherhaven.ui.restaurantUpgrades.notify.needGold";
                    case MAXED -> "aetherhaven.ui.restaurantUpgrades.notify.maxed";
                    default -> "aetherhaven.ui.restaurantUpgrades.notify.failed";
                };
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation("aetherhaven_ui_town." + notifyKey),
                NotificationStyle.Warning
            );
        }
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder ev = new UIEventBuilder();
        build(ref, cmd, ev, store);
        sendUpdate(cmd, ev, false);
    }

    private void buildWorkplaceAssignRoleRows(
        @Nonnull Store<EntityStore> store,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nullable TownRecord town,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull String plotConstructionId,
        @Nonnull UUID plotUuidMgmt,
        @Nonnull List<String> workplaceRoles,
        boolean showRoleLabels
    ) {
        commandBuilder.clear(WORKPLACE_ASSIGN_ROLE_ROWS);
        ConstructionCatalog catalog = plugin.getConstructionCatalog();
        for (int i = 0; i < workplaceRoles.size(); i++) {
            String residentKind = workplaceRoles.get(i);
            commandBuilder.append(WORKPLACE_ASSIGN_ROLE_ROWS, "Aetherhaven/WorkplaceAssignRoleRow.ui");
            String rowPath = WORKPLACE_ASSIGN_ROLE_ROWS + "[" + i + "]";
            AetherhavenUiLocalization.applyWorkplaceAssignRoleRow(
                commandBuilder,
                rowPath,
                TownVillagerBinding.KIND_BARD.equals(residentKind)
            );
            commandBuilder.set(rowPath + " #RoleLabel.Visible", showRoleLabels);
            if (showRoleLabels) {
                commandBuilder.set(
                    rowPath + " #RoleLabel.TextSpans",
                    Message.translation(workplaceRoleLabelKey(residentKind))
                );
            }
            buildWorkplaceRoleRow(
                store,
                commandBuilder,
                eventBuilder,
                town,
                plugin,
                gameplayWorkplaceIdForRole(catalog, plotConstructionId, residentKind),
                plotUuidMgmt,
                npcRoleFilterForWorkplaceKind(residentKind),
                residentKind,
                rowPath
            );
        }
    }

    private void buildWorkplaceRoleRow(
        @Nonnull Store<EntityStore> store,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nullable TownRecord town,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull String gameplayWorkplaceId,
        @Nonnull UUID plotUuidMgmt,
        @Nullable String filterNpcRoleId,
        @Nonnull String residentKind,
        @Nonnull String rowPath
    ) {
        String langU = playerRef.getLanguage() != null ? playerRef.getLanguage() : "en-US";
        String unassignedLabel =
            I18nModule.get().getMessage(langU, "aetherhaven_ui_shell.aetherhaven.ui.plotConstruction.houseResidentUnassigned");
        if (unassignedLabel == null || unassignedLabel.isEmpty()) {
            unassignedLabel = "Unassigned";
        }

        String assignedUuid = "";
        List<WorkplaceWorkerDirectory.WorkplaceWorkerRow> eligible = List.of();
        if (town != null) {
            assignedUuid = findEntityUuidWithJobPlotAndKind(store, town.getTownId(), plotUuidMgmt, residentKind);
            eligible =
                WorkplaceWorkerDirectory.listEligible(store, town, plugin, gameplayWorkplaceId, filterNpcRoleId);
        }

        String selectionPath = rowPath + " #WorkplaceWorkerSelection";
        if (assignedUuid.isEmpty()) {
            commandBuilder.set(selectionPath + " #PreviewPortrait.Visible", false);
            commandBuilder.set(selectionPath + " #PreviewNameLine.TextSpans", Message.raw(unassignedLabel));
            commandBuilder.set(selectionPath + " #PreviewRoleLine.Visible", false);
        } else {
            UUID assigned = UUID.fromString(assignedUuid);
            WorkplaceWorkerDirectory.WorkplaceWorkerRow preview = WorkplaceWorkerDirectory.findRow(eligible, assigned);
            if (preview == null && town != null) {
                preview =
                    WorkplaceWorkerDirectory.resolvePreviewRow(
                        store,
                        town,
                        plugin,
                        gameplayWorkplaceId,
                        filterNpcRoleId,
                        assigned
                    );
            }
            if (preview != null) {
                commandBuilder.set(selectionPath + " #PreviewPortrait.Visible", true);
                commandBuilder.set(selectionPath + " #PreviewPortrait.AssetPath", preview.portraitPath());
                commandBuilder.set(selectionPath + " #PreviewNameLine.TextSpans", Message.raw(preview.displayName()));
                commandBuilder.set(selectionPath + " #PreviewRoleLine.Visible", true);
                commandBuilder.set(selectionPath + " #PreviewRoleLine.TextSpans", preview.roleLine());
            } else {
                commandBuilder.set(selectionPath + " #PreviewPortrait.Visible", false);
                commandBuilder.set(selectionPath + " #PreviewNameLine.TextSpans", Message.raw(unassignedLabel));
                commandBuilder.set(selectionPath + " #PreviewRoleLine.Visible", false);
            }
        }

        EventData openPicker =
            new EventData()
                .append("Action", "OpenWorkplaceWorkerPicker")
                .append("WorkplacePickerResidentKind", residentKind);
        if (filterNpcRoleId != null) {
            openPicker.append("WorkplacePickerFilterNpcRoleId", filterNpcRoleId);
        }
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            rowPath + " #ChooseWorkplaceWorkerButton",
            openPicker,
            false
        );
    }

    @Nullable
    private static String npcRoleFilterForWorkplaceKind(@Nonnull String residentKind) {
        if (TownVillagerBinding.KIND_GUILD_MASTER.equals(residentKind)) {
            return AetherhavenConstants.GUILD_MASTER_NPC_ROLE_ID;
        }
        if (TownVillagerBinding.KIND_BARD.equals(residentKind)) {
            return AetherhavenConstants.BARD_NPC_ROLE_ID;
        }
        return null;
    }

    private void buildWorkplaceWorkerPickerModal(
        @Nonnull Store<EntityStore> store,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nullable TownRecord town,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull String gameplayWorkplaceId,
        @Nonnull UUID plotUuidMgmt,
        @Nullable String filterNpcRoleId,
        @Nonnull String residentKind
    ) {
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#WorkplaceWorkerPickerCancelButton",
            new EventData().append("Action", "CloseWorkplaceWorkerPicker"),
            false
        );

        String langU = playerRef.getLanguage() != null ? playerRef.getLanguage() : "en-US";
        String unassignedLabel =
            I18nModule.get().getMessage(langU, "aetherhaven_ui_shell.aetherhaven.ui.plotConstruction.houseResidentUnassigned");
        if (unassignedLabel == null || unassignedLabel.isEmpty()) {
            unassignedLabel = "Unassigned";
        }

        List<WorkplaceWorkerDirectory.WorkplaceWorkerRow> eligible =
            town != null
                ? WorkplaceWorkerDirectory.listEligible(store, town, plugin, gameplayWorkplaceId, filterNpcRoleId)
                : List.of();
        boolean allowUnassign =
            town != null
                && WorkplacePlotAssignment.allowsWorkplaceUnassign(
                    plugin,
                    store,
                    town,
                    gameplayWorkplaceId,
                    plotUuidMgmt,
                    residentKind
                );

        commandBuilder.clear(WORKPLACE_WORKER_ROWS);
        int rowIndex = 0;
        if (allowUnassign) {
            commandBuilder.append(WORKPLACE_WORKER_ROWS, "Aetherhaven/HouseResidentAssignRow.ui");
            String unassignedRow = WORKPLACE_WORKER_ROWS + "[" + rowIndex + "]";
            AetherhavenUiLocalization.applyHouseResidentAssignRow(commandBuilder, unassignedRow);
            commandBuilder.set(unassignedRow + " #Portrait.Visible", false);
            commandBuilder.set(unassignedRow + " #NameLine.TextSpans", Message.raw(unassignedLabel));
            commandBuilder.set(unassignedRow + " #RoleLine.Visible", false);
            EventData unassignEvent =
                new EventData()
                    .append("Action", "SelectWorkplaceWorker")
                    .append("WorkplacePickerResidentKind", residentKind)
                    .append("WorkplaceWorkerUuid", "");
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                unassignedRow + " #SelectButton",
                unassignEvent,
                false
            );
            rowIndex++;
        }
        for (WorkplaceWorkerDirectory.WorkplaceWorkerRow row : eligible) {
            commandBuilder.append(WORKPLACE_WORKER_ROWS, "Aetherhaven/HouseResidentAssignRow.ui");
            String rowPath = WORKPLACE_WORKER_ROWS + "[" + rowIndex + "]";
            AetherhavenUiLocalization.applyHouseResidentAssignRow(commandBuilder, rowPath);
            commandBuilder.set(rowPath + " #Portrait.AssetPath", row.portraitPath());
            commandBuilder.set(rowPath + " #NameLine.TextSpans", Message.raw(row.displayName()));
            commandBuilder.set(rowPath + " #RoleLine.TextSpans", row.roleLine());
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                rowPath + " #SelectButton",
                new EventData()
                    .append("Action", "SelectWorkplaceWorker")
                    .append("WorkplacePickerResidentKind", residentKind)
                    .append("WorkplaceWorkerUuid", row.entityUuid().toString()),
                false
            );
            rowIndex++;
        }
    }

    private void handleWorkplaceAssignment(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nullable String rawUuid,
        @Nonnull String residentKind
    ) {
        if (!managementUi) {
            return;
        }
        PlotInstanceState stW = resolvePlotState(store, ref);
        if (stW != PlotInstanceState.COMPLETE) {
            return;
        }
        ConstructionDefinition defW = resolveDefinition(store, ref);
        AetherhavenPlugin pluginW = AetherhavenPlugin.get();
        if (defW == null || pluginW == null) {
            return;
        }
        var catalogW = pluginW.getConstructionCatalog();
        List<String> plotRoles = ProductionWorkplaceKinds.residentBindingKindsForPlot(catalogW, defW.getId());
        if (!plotRoles.contains(residentKind.trim())) {
            return;
        }
        String roleGameplayId = gameplayWorkplaceIdForRole(catalogW, defW.getId(), residentKind);
        if (roleGameplayId == null || roleGameplayId.isBlank()) {
            return;
        }
        Store<ChunkStore> csw = blockRef.getStore();
        ManagementBlock mbw = csw.getComponent(blockRef, ManagementBlock.getComponentType());
        if (mbw == null || mbw.getPlotId().isBlank() || mbw.getTownId().isBlank()) {
            return;
        }
        UUID plotIdW;
        UUID townIdW;
        try {
            plotIdW = UUID.fromString(mbw.getPlotId().trim());
            townIdW = UUID.fromString(mbw.getTownId().trim());
        } catch (IllegalArgumentException e) {
            return;
        }
        World worldW = store.getExternalData().getWorld();
        TownManager tmw = AetherhavenWorldRegistries.getOrCreateTownManager(worldW, pluginW);
        TownRecord townW = tmw.getTown(townIdW);
        if (townW == null) {
            return;
        }
        Store<EntityStore> entityStore = worldW.getEntityStore().getStore();
        PlayerRef prw = store.getComponent(ref, PlayerRef.getComponentType());
        if (rawUuid == null || rawUuid.isBlank()) {
            String currentWorker =
                findEntityUuidWithJobPlotAndKind(entityStore, townIdW, plotIdW, residentKind);
            if (currentWorker != null && !currentWorker.isBlank()) {
                String clearErr =
                    WorkplacePlotAssignment.tryClearWorker(
                        worldW,
                        pluginW,
                        townW,
                        tmw,
                        plotIdW,
                        residentKind,
                        entityStore
                    );
                if (clearErr != null) {
                    if (prw != null) {
                        if ("This villager must stay assigned to the building.".equals(clearErr)) {
                            prw.sendMessage(
                                Message.translation("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.workplaceCannotUnassignMandatory")
                            );
                        } else if ("Assign another worker before removing this one.".equals(clearErr)) {
                            prw.sendMessage(
                                Message.translation("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.workplaceCannotUnassignAlternate")
                            );
                        } else {
                            prw.sendMessage(Message.raw(clearErr));
                        }
                    }
                    return;
                }
                if (prw != null) {
                    prw.sendMessage(Message.translation("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.workplaceUpdated"));
                }
                UICommandBuilder cmdClear = new UICommandBuilder();
                UIEventBuilder evClear = new UIEventBuilder();
                build(ref, cmdClear, evClear, store);
                sendUpdate(cmdClear, evClear, false);
                return;
            }
            return;
        }
        UUID npcUuid;
        try {
            npcUuid = UUID.fromString(rawUuid.trim());
        } catch (IllegalArgumentException e) {
            if (prw != null) {
                prw.sendMessage(Message.translation("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.workplaceInvalidWorker"));
            }
            return;
        }
        String err = WorkplacePlotAssignment.tryAssignWorker(worldW, pluginW, townW, tmw, plotIdW, npcUuid, entityStore);
        if (err != null) {
            if (prw != null) {
                prw.sendMessage(Message.raw(err));
            }
            return;
        }
        if (prw != null) {
            prw.sendMessage(Message.translation("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.workplaceUpdated"));
        }
        UICommandBuilder cmdw = new UICommandBuilder();
        UIEventBuilder evw = new UIEventBuilder();
        build(ref, cmdw, evw, store);
        sendUpdate(cmdw, evw, false);
    }

    @Nonnull
    private static String findEntityUuidWithJobPlotAndKind(
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID townId,
        @Nonnull UUID jobPlotId,
        @Nonnull String residentKind
    ) {
        final String[] holder = new String[] {""};
        Query<EntityStore> q = Query.and(TownVillagerBinding.getComponentType(), UUIDComponent.getComponentType());
        store.forEachChunk(
            q,
            (ArchetypeChunk<EntityStore> archetypeChunk, CommandBuffer<EntityStore> commandBuffer) -> {
                if (!holder[0].isEmpty()) {
                    return;
                }
                for (int i = 0; i < archetypeChunk.size(); i++) {
                    TownVillagerBinding b = archetypeChunk.getComponent(i, TownVillagerBinding.getComponentType());
                    if (b == null || !townId.equals(b.getTownId()) || !residentKind.equals(b.getKind())) {
                        continue;
                    }
                    UUID jp = b.getJobPlotId();
                    if (jp == null || !jp.equals(jobPlotId)) {
                        continue;
                    }
                    UUIDComponent uc = archetypeChunk.getComponent(i, UUIDComponent.getComponentType());
                    if (uc != null) {
                        holder[0] = uc.getUuid().toString();
                        return;
                    }
                }
            }
        );
        return holder[0];
    }

    @Nonnull
    private static String gameplayWorkplaceIdForRole(
        @Nonnull ConstructionCatalog catalog,
        @Nonnull String plotConstructionId,
        @Nonnull String residentKind
    ) {
        String mapped =
            ProductionWorkplaceKinds.gameplayConstructionIdForResidentKind(catalog, plotConstructionId, residentKind);
        if (mapped != null && !mapped.isBlank()) {
            return mapped;
        }
        return catalog.resolveGameplayConstructionId(plotConstructionId);
    }

    private static String workplaceRoleLabelKey(@Nonnull String residentKind) {
        return switch (residentKind.trim()) {
            case TownVillagerBinding.KIND_GUILD_MASTER ->
                "aetherhaven_ui_town.aetherhaven.ui.plotconstruction.workplaceGuildMaster";
            case TownVillagerBinding.KIND_BARD ->
                "aetherhaven_ui_town.aetherhaven.ui.plotconstruction.workplaceBard";
            case TownVillagerBinding.KIND_INNKEEPER ->
                "aetherhaven_ui_town.aetherhaven.ui.plotconstruction.workplaceInnkeeper";
            case TownVillagerBinding.KIND_ELDER ->
                "aetherhaven_ui_town.aetherhaven.ui.plotconstruction.workplaceElder";
            case TownVillagerBinding.KIND_CHEF ->
                "aetherhaven_ui_town.aetherhaven.ui.plotconstruction.workplaceChef";
            default -> "aetherhaven_ui_town.aetherhaven.ui.plotconstruction.workplaceRole." + residentKind.trim();
        };
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, a) -> d.action = a, d -> d.action)
            .add()
            .append(new KeyedCodec<>("MemberUuid", Codec.STRING), (d, v) -> d.memberUuid = v, d -> d.memberUuid)
            .add()
            .append(new KeyedCodec<>("@InviteName", Codec.STRING), (d, v) -> d.inviteName = v, d -> d.inviteName)
            .add()
            .append(new KeyedCodec<>("HouseResidentUuid", Codec.STRING), (d, v) -> d.houseResidentUuid = v, d -> d.houseResidentUuid)
            .add()
            .append(new KeyedCodec<>("HouseResidentSlot", Codec.STRING), (d, v) -> d.houseResidentSlot = v, d -> d.houseResidentSlot)
            .add()
            .append(
                new KeyedCodec<>("@HouseResidentHideElsewhere", Codec.BOOLEAN),
                (d, v) -> d.houseResidentHideElsewhere = v,
                d -> d.houseResidentHideElsewhere
            )
            .add()
            .append(
                new KeyedCodec<>("WorkplacePickerResidentKind", Codec.STRING),
                (d, v) -> d.workplacePickerResidentKind = v,
                d -> d.workplacePickerResidentKind
            )
            .add()
            .append(
                new KeyedCodec<>("WorkplacePickerFilterNpcRoleId", Codec.STRING),
                (d, v) -> d.workplacePickerFilterNpcRoleId = v,
                d -> d.workplacePickerFilterNpcRoleId
            )
            .add()
            .append(
                new KeyedCodec<>("WorkplaceWorkerUuid", Codec.STRING),
                (d, v) -> d.workplaceWorkerUuid = v,
                d -> d.workplaceWorkerUuid
            )
            .add()
            .append(new KeyedCodec<>("MaterialIndex", Codec.INTEGER), (d, v) -> d.materialIndex = v, d -> d.materialIndex)
            .add()
            .append(new KeyedCodec<>("UpgradeBranch", Codec.STRING), (d, v) -> d.upgradeBranch = v, d -> d.upgradeBranch)
            .add()
            .append(
                new KeyedCodec<>("@AllowVisitorPortalTravel", Codec.BOOLEAN),
                (d, v) -> d.allowVisitorPortalTravel = v,
                d -> d.allowVisitorPortalTravel
            )
            .add()
            .append(new KeyedCodec<>("PresetHex", Codec.STRING), (d, v) -> d.presetHex = v, d -> d.presetHex)
            .add()
            .build();

        private String action;
        @Nullable
        private String memberUuid;
        @Nullable
        private String inviteName;
        private String houseResidentUuid;
        @Nullable
        private String houseResidentSlot;
        @Nullable
        private Boolean houseResidentHideElsewhere;
        @Nullable
        private String workplacePickerResidentKind;
        @Nullable
        private String workplacePickerFilterNpcRoleId;
        @Nullable
        private String workplaceWorkerUuid;
        @Nullable
        private Integer materialIndex;
        @Nullable
        private String upgradeBranch;
        @Nullable
        private Boolean allowVisitorPortalTravel;
        @Nullable
        private String presetHex;
    }
}
