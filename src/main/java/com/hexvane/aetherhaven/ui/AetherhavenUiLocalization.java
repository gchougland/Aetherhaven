package com.hexvane.aetherhaven.ui;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import java.util.function.UnaryOperator;
import javax.annotation.Nonnull;

/**
 * Pushes translated {@link Message}s into Custom UI via {@code .TextSpans} / {@code .TooltipTextSpans} (and
 * {@code .PlaceholderText} on text fields) because long {@code %bundle.key} references in {@code .ui} markup often
 * render as raw keys on the client.
 */
public final class AetherhavenUiLocalization {
    private AetherhavenUiLocalization() {}

    private static Message t(@Nonnull String key) {
        return Message.translation(key);
    }

    /** Tab icon strip shared by plot construction and villager needs when opened from the management block. */
    public static void applyManagementTabTooltips(@Nonnull UICommandBuilder b) {
        b.set("#TabPlotButton.TooltipTextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotmanagement.tabPlotTooltip"));
        b.set("#TabNeedsButton.TooltipTextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotmanagement.tabNeedsTooltip"));
        b.set("#TabPlayersButton.TooltipTextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotmanagement.tabPlayersTooltip"));
        b.set("#TabMoveButton.TooltipTextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotmanagement.tabMoveTooltip"));
    }

    public static void applyPlotConstructionPage(@Nonnull UICommandBuilder b) {
        applyManagementTabTooltips(b);
        b.set("#MaterialsHeader.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.materials"));
        b.set("#HouseResidentPickerTitle.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.houseResidentPickerTitle"));
        b.set("#WorkplaceWorkerPickerTitle.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.workplaceWorkerPickerTitle"));
        b.set("#HouseResidentPickerCancelButton.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.houseResidentPickerCancel"));
        b.set("#WorkplaceWorkerPickerCancelButton.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.workplaceWorkerPickerCancel"));
        b.set("#HouseResidentHideElsewhereCheckbox #HouseResidentHideElsewhereLabel.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.hideHousedResidentsElsewhere"));
        b.set("#HouseResidentHideElsewhereCheckbox #HouseResidentHideElsewhereLabel.TooltipTextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.hideHousedResidentsElsewhereTooltip"));
        b.set("#PickUpPlotButton.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.pickUpPlot"));
        b.set("#BuildButton.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.build"));
        b.set("#DepositMaterialsButton.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.depositMaterials"));
        b.set("#InviteLabel.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotmanagement.inviteLabel"));
        b.set("#InviteSendButton.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotmanagement.inviteSend"));
        b.set("#InvitePlayerInput.PlaceholderText", t("aetherhaven_ui_town.aetherhaven.ui.plotmanagement.invitePlaceholder"));
        b.set("#MoveBuildingModalTitle.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.moveBuildingModalTitle"));
        b.set("#MoveBuildingModalText.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.moveBuildingModalBody"));
        b.set("#MoveBuildingConfirmButton.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.moveBuildingModalConfirm"));
        b.set("#MoveBuildingCancelButton.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.moveBuildingModalCancel"));
        b.set("#ReconstructBuildingButton.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.reconstructBuilding"));
        b.set("#ExpandTerritoryButton.TextSpans", t("aetherhaven_town.aetherhaven.ui.plotconstruction.expandTerritory"));
        b.set("#ReconstructBuildingModalTitle.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.reconstructBuildingModalTitle"));
        b.set("#ReconstructBuildingModalText.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.reconstructBuildingModalBody"));
        b.set("#ReconstructBuildingConfirmButton.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.reconstructBuildingModalConfirm"));
        b.set("#ReconstructBuildingCancelButton.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.reconstructBuildingModalCancel"));
        b.set("#TouristManifestHeader.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.touristManifestHeader"));
        b.set("#TouristManifestEmpty.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.touristManifestEmpty"));
        b.set("#GuardManifestEmpty.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.guardManifestEmpty"));
        b.set("#ClearVisitingTouristsButton.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.clearVisitingTourists"));
        b.set("#VisitorPortalTravelLabel.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.visitorPortalTravelAllow"));
        b.set("#VisitorPortalTravelLabel.TooltipTextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.visitorPortalTravelAllowTooltip"));
        b.set("#PlayerShopNpcBuyLabel.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.playerShopNpcBuyAllow"));
        b.set("#PlayerShopNpcBuyLabel.TooltipTextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.playerShopNpcBuyAllowTooltip"));
        b.set("#VisitorPortalColorLabel.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.visitorPortalColor"));
        b.set("#VisitorPortalColorLabel.TooltipTextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.visitorPortalColorTooltip"));
        b.set("#ChooseVisitorPortalColorButton.TextSpans", t("aetherhaven_tourist.aetherhaven.tourist.portalTravel.chooseColor"));
        b.set("#VisitorPortalColorPickerTitle.TextSpans", t("aetherhaven_tourist.aetherhaven.tourist.portalTravel.colorPickerTitle"));
        b.set("#VisitorPortalColorPickerHint.TextSpans", t("aetherhaven_tourist.aetherhaven.tourist.portalTravel.colorPickerHint"));
        b.set("#VisitorPortalColorPickerCancelButton.TextSpans", t("aetherhaven_tourist.aetherhaven.tourist.portalTravel.colorPickerClose"));
        b.set("#PickUpPlotModalTitle.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.pickUpPlotModalTitle"));
        b.set("#PickUpPlotModalText.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.pickUpPlotModalBody"));
        b.set("#PickUpPlotConfirmButton.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.pickUpPlotModalConfirm"));
        b.set("#PickUpPlotCancelButton.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.pickUpPlotModalCancel"));
    }

    public static void applyTownMemberPermissionsPage(@Nonnull UICommandBuilder b) {
        b.set("#MemberPermTitleText.TextSpans", t("aetherhaven_town.aetherhaven.ui.memberPermissions.title"));
        b.set("#MemberPermBack.TooltipTextSpans", t("aetherhaven_town.aetherhaven.ui.memberPermissions.backTooltip"));
        b.set("#CapPlacePlots.TextSpans", t("aetherhaven_town.aetherhaven.ui.memberPermissions.placePlots"));
        b.set("#CapManageConstructions.TextSpans", t("aetherhaven_town.aetherhaven.ui.memberPermissions.manageConstructions"));
        b.set("#CapRemovePlots.TextSpans", t("aetherhaven_town.aetherhaven.ui.memberPermissions.removePlots"));
        b.set("#CapSpendTreasuryGold.TextSpans", t("aetherhaven_town.aetherhaven.ui.memberPermissions.spendTreasuryGold"));
        b.set("#CapOpenTreasuryPanel.TextSpans", t("aetherhaven_town.aetherhaven.ui.memberPermissions.openTreasuryPanel"));
        b.set("#CapAcceptQuests.TextSpans", t("aetherhaven_town.aetherhaven.ui.memberPermissions.acceptQuests"));
        b.set("#CapCompleteQuests.TextSpans", t("aetherhaven_town.aetherhaven.ui.memberPermissions.completeQuests"));
        b.set("#CapAbandonQuests.TextSpans", t("aetherhaven_town.aetherhaven.ui.memberPermissions.abandonQuests"));
        b.set("#CapReviveVillagers.TextSpans", t("aetherhaven_town.aetherhaven.ui.memberPermissions.reviveVillagers"));
        b.set("#CapUseShopSpots.TextSpans", t("aetherhaven_town.aetherhaven.ui.memberPermissions.useShopSpots"));
        b.set("#CapBreakBlocks.TextSpans", t("aetherhaven_town.aetherhaven.ui.memberPermissions.breakBlocks"));
        b.set("#CapPlaceBlocks.TextSpans", t("aetherhaven_town.aetherhaven.ui.memberPermissions.placeBlocks"));
        b.set("#CapHarvestBlocks.TextSpans", t("aetherhaven_town.aetherhaven.ui.memberPermissions.harvestBlocks"));
        b.set("#CapOpenContainers.TextSpans", t("aetherhaven_town.aetherhaven.ui.memberPermissions.openContainers"));
        b.set("#CapUseDoors.TextSpans", t("aetherhaven_town.aetherhaven.ui.memberPermissions.useDoors"));
    }

    public static void applyTownExpansionPage(@Nonnull UICommandBuilder b) {
        b.set("#ExpansionTitleText.TextSpans", t("aetherhaven_town.aetherhaven.ui.expansion.title"));
        b.set("#ExpansionSummary.TextSpans", t("aetherhaven_town.aetherhaven.ui.expansion.summary"));
        b.set("#ExpansionBackButton.TextSpans", t("aetherhaven_town.aetherhaven.ui.expansion.back"));
        b.set("#ExpansionClaimButton.TextSpans", t("aetherhaven_town.aetherhaven.ui.expansion.claimButton"));
    }

    public static void applyDifficultyPage(@Nonnull UICommandBuilder b) {
        b.set("#DifficultyTitleText.TextSpans", t("aetherhaven_difficulty.aetherhaven.difficulty.title"));
        b.set("#CardEasyTitle.TextSpans", t("aetherhaven_difficulty.aetherhaven.difficulty.easy.title"));
        b.set("#CardEasyDesc.TextSpans", t("aetherhaven_difficulty.aetherhaven.difficulty.easy.desc"));
        b.set("#CardNormalTitle.TextSpans", t("aetherhaven_difficulty.aetherhaven.difficulty.normal.title"));
        b.set("#CardNormalDesc.TextSpans", t("aetherhaven_difficulty.aetherhaven.difficulty.normal.desc"));
        b.set("#CardHardTitle.TextSpans", t("aetherhaven_difficulty.aetherhaven.difficulty.hard.title"));
        b.set("#CardHardDesc.TextSpans", t("aetherhaven_difficulty.aetherhaven.difficulty.hard.desc"));
        b.set("#CustomizeButton.TextSpans", t("aetherhaven_difficulty.aetherhaven.difficulty.customize"));
        b.set("#ResourceMultLabel.TextSpans", t("aetherhaven_difficulty.aetherhaven.difficulty.resourceMult"));
        b.set("#GoldMultLabel.TextSpans", t("aetherhaven_difficulty.aetherhaven.difficulty.goldMult"));
        b.set("#AllBlocksToggle #AllBlocksLabel.TextSpans", t("aetherhaven_difficulty.aetherhaven.difficulty.allBlocks"));
        b.set("#BackToPresetsButton.TextSpans", t("aetherhaven_difficulty.aetherhaven.difficulty.back"));
        b.set("#SaveButton.TextSpans", t("aetherhaven_difficulty.aetherhaven.difficulty.save"));
        b.set("#CancelButton.TextSpans", t("aetherhaven_difficulty.aetherhaven.difficulty.cancel"));
    }

    public static void applyCharterAmendmentsChrome(@Nonnull UICommandBuilder b) {
        b.set("#CharterAmendmentsTitleText.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.charter.title"));
        b.set("#TierRailMark1.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.charter.tierMark1"));
        b.set("#TierRailMark2.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.charter.tierMark2"));
    }

    public static void applyVillagerNeedsOverview(@Nonnull UICommandBuilder b) {
        applyManagementTabTooltips(b);
        b.set("#VillagerNeedsTitleText.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.villagerneeds.title"));
        b.set("#GiftHistoryButton.TooltipTextSpans", t("aetherhaven_ui_town.aetherhaven.ui.villagerneeds.giftHistoryTooltip"));
        b.set("#RescueTeleportButton.TooltipTextSpans", t("aetherhaven_ui_town.aetherhaven.ui.villagerneeds.rescueTooltip"));
        b.set("#ReputationLabel.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.reputation.label"));
        b.set("#NeedHungerLabel.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.villagerneeds.hunger"));
        b.set("#NeedEnergyLabel.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.villagerneeds.energy"));
        b.set("#NeedFunLabel.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.villagerneeds.fun"));
        b.set("#VillagersHeader.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.villagerneeds.villagers"));
    }

    public static void applyVillagerGiftHistory(@Nonnull UICommandBuilder b) {
        b.set("#GiftHistoryTitleText.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.giftHistory.title"));
        b.set("#GiftHistoryBack.TooltipTextSpans", t("aetherhaven_ui_town.aetherhaven.ui.giftHistory.backTooltip"));
        b.set("#EmptyHint.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.giftHistory.empty"));
        b.set("#GiftCyclePrev.TooltipTextSpans", t("aetherhaven_ui_town.aetherhaven.ui.giftHistory.cyclePrevTooltip"));
        b.set("#GiftCycleNext.TooltipTextSpans", t("aetherhaven_ui_town.aetherhaven.ui.giftHistory.cycleNextTooltip"));
    }

    public static void applyDialoguePage(@Nonnull UICommandBuilder b) {
        b.set("#ReputationLabel.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.reputation.label"));
    }

    public static void applyGeodeOpen(@Nonnull UICommandBuilder b) {
        b.set("#GeodeOpenTitleText.TextSpans", t("aetherhaven_jewelry_geode.aetherhaven.ui.geodeopen.title"));
    }

    public static void applyGaiaStatueRevivePage(@Nonnull UICommandBuilder b) {
        b.set("#GaiaReviveTitleText.TextSpans", t("aetherhaven_ui_shell.aetherhaven.ui.gaiaStatue.title"));
        b.set("#Footer.TextSpans", t("aetherhaven_ui_shell.aetherhaven.ui.gaiaStatue.footer"));
    }

    public static void applyGaiaStatueReviveRow(@Nonnull UICommandBuilder b, @Nonnull String rowPath) {
        b.set(rowPath + " #ReviveButton.TextSpans", t("aetherhaven_ui_shell.aetherhaven.ui.gaiaStatue.revive"));
    }

    public static void applyPatrolWandStatusHudTitle(
        @Nonnull UICommandBuilder b,
        @Nonnull java.util.function.Function<String, String> scoped
    ) {
        b.set(scoped.apply("#PatrolWandHudTitleText.TextSpans"), t("aetherhaven_items.aetherhaven.patrolWand.hudTitle"));
    }

    public static void applyPatrolWandAssignGuardPage(@Nonnull UICommandBuilder b) {
        b.set("#PatrolAssignTitleText.TextSpans", t("aetherhaven_items.aetherhaven.patrolWand.assignPageTitle"));
    }

    public static void applyPatrolWandAssignGuardRow(@Nonnull UICommandBuilder b, @Nonnull String rowPath) {
        b.set(rowPath + " #AssignButton.TextSpans", t("aetherhaven_items.aetherhaven.patrolWand.assignPageAssignButton"));
    }

    public static void applyHouseResidentAssignRow(@Nonnull UICommandBuilder b, @Nonnull String rowPath) {
        b.set(rowPath + " #SelectButton.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.houseResidentSelect"));
    }

    public static void applyWorkplaceAssignRoleRow(
        @Nonnull UICommandBuilder b,
        @Nonnull String rowPath,
        boolean bardRole
    ) {
        b.set(
            rowPath + " #ChooseWorkplaceWorkerButton.TextSpans",
            bardRole
                ? t("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.chooseWorkplaceBard")
                : t("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.chooseWorkplaceWorker")
        );
    }

    public static void applyPatrolWandNameRoutePage(@Nonnull UICommandBuilder b) {
        b.set("#PatrolNameRouteTitleText.TextSpans", t("aetherhaven_items.aetherhaven.patrolWand.nameRouteTitle"));
        b.set("#NameLabel.TextSpans", t("aetherhaven_items.aetherhaven.patrolWand.nameRouteLabel"));
        b.set("#RouteNameInput.PlaceholderText", t("aetherhaven_items.aetherhaven.patrolWand.nameRoutePlaceholder"));
        b.set("#SaveRouteNameButton.TextSpans", t("aetherhaven_items.aetherhaven.patrolWand.nameRouteSave"));
        b.set("#CancelRouteNameButton.TextSpans", t("aetherhaven_items.aetherhaven.patrolWand.nameRouteCancel"));
    }

    public static void applyTreasuryPage(@Nonnull UICommandBuilder b) {
        b.set("#TreasuryTitleText.TextSpans", t("aetherhaven_jewelry_geode.aetherhaven.ui.treasury.title"));
        b.set("#TabCoinsButton.TooltipTextSpans", t("aetherhaven_jewelry_geode.aetherhaven.ui.treasury.tabCoinsTooltip"));
        b.set("#TabTitheButton.TooltipTextSpans", t("aetherhaven_jewelry_geode.aetherhaven.ui.treasury.tabTitheTooltip"));
        b.set("#DepositButton.TextSpans", t("aetherhaven_jewelry_geode.aetherhaven.ui.treasury.deposit"));
        b.set("#WithdrawButton.TextSpans", t("aetherhaven_jewelry_geode.aetherhaven.ui.treasury.withdraw"));
        b.set("#CoinsTabContent #Hint.TextSpans", t("aetherhaven_jewelry_geode.aetherhaven.ui.treasury.hint"));
        b.set("#TaxIntro.TextSpans", t("aetherhaven_jewelry_geode.aetherhaven.ui.treasury.tax.intro"));
        b.set("#TaxHallMissing.TextSpans", t("aetherhaven_jewelry_geode.aetherhaven.ui.treasury.tax.hallMissing"));
        b.set("#TitheTotalLabel.TextSpans", t("aetherhaven_jewelry_geode.aetherhaven.ui.treasury.tax.sheetTotalLabel"));
        b.set("#TaxResidentsHeader.TextSpans", t("aetherhaven_jewelry_geode.aetherhaven.ui.treasury.tax.residentsHeader"));
    }

    public static void applyTouristPortalTravelPage(@Nonnull UICommandBuilder b) {
        b.set("#TravelTitleText.TextSpans", t("aetherhaven_tourist.aetherhaven.tourist.portalTravel.title"));
        b.set("#TravelIntro.TextSpans", t("aetherhaven_tourist.aetherhaven.tourist.portalTravel.intro"));
        b.set("#AllowInboundLabel.TextSpans", t("aetherhaven_tourist.aetherhaven.tourist.portalTravel.allowInbound"));
        b.set("#AllowInboundLabel.TooltipTextSpans", t("aetherhaven_tourist.aetherhaven.tourist.portalTravel.allowInboundTooltip"));
        b.set("#TownPortalColorLabel.TextSpans", t("aetherhaven_tourist.aetherhaven.tourist.portalTravel.townColor"));
        b.set("#TownPortalColorLabel.TooltipTextSpans", t("aetherhaven_tourist.aetherhaven.tourist.portalTravel.townColorTooltip"));
        b.set("#ChoosePortalColorButton.TextSpans", t("aetherhaven_tourist.aetherhaven.tourist.portalTravel.chooseColor"));
        b.set("#PortalColorPickerTitle.TextSpans", t("aetherhaven_tourist.aetherhaven.tourist.portalTravel.colorPickerTitle"));
        b.set("#PortalColorPickerHint.TextSpans", t("aetherhaven_tourist.aetherhaven.tourist.portalTravel.colorPickerHint"));
        b.set("#PortalColorPickerCancelButton.TextSpans", t("aetherhaven_tourist.aetherhaven.tourist.portalTravel.colorPickerClose"));
        b.set("#DestinationsHeader.TextSpans", t("aetherhaven_tourist.aetherhaven.tourist.portalTravel.destinationsHeader"));
        b.set("#DestinationsEmpty.TextSpans", t("aetherhaven_tourist.aetherhaven.tourist.portalTravel.destinationsEmpty"));
    }

    public static void applyFeasts(@Nonnull UICommandBuilder b) {
        b.set("#FeastTitleText.TextSpans", t("aetherhaven_feasts_production.aetherhaven.ui.feast.title"));
        b.set("#GridLabel.TextSpans", t("aetherhaven_feasts_production.aetherhaven.ui.feast.selectHint"));
        b.set("#ConfirmFeast.TextSpans", t("aetherhaven_feasts_production.aetherhaven.ui.feast.confirm"));
    }

    public static void applyJewelryAppraisal(@Nonnull UICommandBuilder b) {
        b.set("#JewelryAppraisalTitleText.TextSpans", t("aetherhaven_jewelry_geode.aetherhaven.ui.jewelryAppraisal.title"));
        b.set("#DetailPick.TextSpans", t("aetherhaven_jewelry_geode.aetherhaven.ui.jewelryAppraisal.pick"));
        b.set("#Appraise.TextSpans", t("aetherhaven_jewelry_geode.aetherhaven.ui.jewelryAppraisal.appraise"));
    }

    public static void applyJewelryCraftingPage(@Nonnull UICommandBuilder b) {
        b.set("#JewelryCraftTitleText.TextSpans", t("aetherhaven_jewelry_geode.aetherhaven.ui.jewelryCrafting.title"));
        b.set("#Hint.TextSpans", t("aetherhaven_jewelry_geode.aetherhaven.ui.jewelryCrafting.hint"));
        b.set("#ShapeTitle.TextSpans", t("aetherhaven_jewelry_geode.aetherhaven.ui.jewelryCrafting.colShape"));
        b.set("#MetalTitle.TextSpans", t("aetherhaven_jewelry_geode.aetherhaven.ui.jewelryCrafting.colMetal"));
        b.set("#GemTitle.TextSpans", t("aetherhaven_jewelry_geode.aetherhaven.ui.jewelryCrafting.colGem"));
        b.set("#EssTitle.TextSpans", t("aetherhaven_jewelry_geode.aetherhaven.ui.jewelryCrafting.colEssence"));
        b.set("#TakeButton.TextSpans", t("aetherhaven_jewelry_geode.aetherhaven.ui.jewelryCrafting.take"));
        b.set("#TakeButton.TooltipTextSpans", t("aetherhaven_jewelry_geode.aetherhaven.ui.jewelryCrafting.takeHint"));
        b.set("#CraftButton.TooltipTextSpans", t("aetherhaven_jewelry_geode.aetherhaven.ui.jewelryCrafting.craft"));
        b.set("#InvTitle.TextSpans", t("aetherhaven_jewelry_geode.aetherhaven.ui.jewelryCrafting.yourBags"));
    }

    public static void applyHandMirror(@Nonnull UICommandBuilder b) {
        b.set("#HandMirrorTitleText.TextSpans", t("aetherhaven_jewelry_geode.aetherhaven.ui.handmirror.title"));
        b.set("#EquipSection.TextSpans", t("aetherhaven_jewelry_geode.aetherhaven.ui.handmirror.equipment"));
        b.set("#Ring1Label.TextSpans", t("aetherhaven_jewelry_geode.aetherhaven.ui.handmirror.ring1"));
        b.set("#Ring2Label.TextSpans", t("aetherhaven_jewelry_geode.aetherhaven.ui.handmirror.ring2"));
        b.set("#NeckLabel.TextSpans", t("aetherhaven_jewelry_geode.aetherhaven.ui.handmirror.necklace"));
        b.set("#ListSection.TextSpans", t("aetherhaven_jewelry_geode.aetherhaven.ui.handmirror.inventory"));
        b.set("#TraitTitle.TextSpans", t("aetherhaven_jewelry_geode.aetherhaven.ui.handmirror.traitsColumnHeading"));
    }

    public static void applyCharterTownPage(@Nonnull UICommandBuilder b) {
        b.set("#CharterTownTitleText.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.chartertown.title"));
        b.set("#MoveCharterHint.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.chartertown.moveCharterHint"));
        b.set("#MoveCharterButton.TooltipTextSpans", t("aetherhaven_ui_town.aetherhaven.ui.chartertown.moveCharterTooltip"));
        b.set("#TownNameLabel.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.chartertown.townNameLabel"));
        b.set("#SaveTownNameButton.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.chartertown.saveTownName"));
        b.set("#NameInput.PlaceholderText", t("aetherhaven_ui_town.aetherhaven.ui.chartertown.namePlaceholder"));
        b.set("#OwnerOnlyHint.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.chartertown.ownerOnlyHint"));
        b.set("#DissolveButton.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.chartertown.dissolve"));
        b.set("#DissolveHint.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.chartertown.dissolveHint"));
        b.set("#CharterModalConfirmButton.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.chartertown.dissolveConfirm"));
        b.set("#CharterModalCancelButton.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.chartertown.modalCancel"));
    }

    public static void applyProductionStorage(@Nonnull UICommandBuilder b) {
        b.set("#ProductionTitleText.TextSpans", t("aetherhaven_feasts_production.aetherhaven.ui.production.title"));
        Message pickTip = t("aetherhaven_feasts_production.aetherhaven.ui.production.pickTooltip");
        Message take1 = t("aetherhaven_feasts_production.aetherhaven.ui.production.take1");
        Message take10 = t("aetherhaven_feasts_production.aetherhaven.ui.production.take10");
        Message take100 = t("aetherhaven_feasts_production.aetherhaven.ui.production.take100");
        for (int i = 0; i < 5; i++) {
            String base = "#SlotsRow #Slot" + i + "Host[0]";
            b.set(base + " #Pick.TooltipTextSpans", pickTip);
            b.set(base + " #Take1.TextSpans", take1);
            b.set(base + " #Take10.TextSpans", take10);
            b.set(base + " #Take100.TextSpans", take100);
        }
        b.set("#OpenUnlocks.TooltipTextSpans", t("aetherhaven_feasts_production.aetherhaven.ui.production.unlockTooltip"));
        b.set("#OpenUnlocks.TextSpans", t("aetherhaven_feasts_production.aetherhaven.ui.production.unlockButton"));
        b.set("#CollectAll.TooltipTextSpans", t("aetherhaven_feasts_production.aetherhaven.ui.production.collectAllTooltip"));
        b.set("#CollectAll.TextSpans", t("aetherhaven_feasts_production.aetherhaven.ui.production.collectAllButton"));
    }

    public static void applyProductionMaterialPicker(@Nonnull UICommandBuilder b) {
        b.set("#MaterialPickerTitleText.TextSpans", t("aetherhaven_feasts_production.aetherhaven.ui.materialPicker.title"));
        b.set("#MaterialPickerIntro.TextSpans", t("aetherhaven_feasts_production.aetherhaven.ui.materialPicker.intro"));
        b.set("#NavToProduction.TooltipTextSpans", t("aetherhaven_feasts_production.aetherhaven.ui.materialPicker.backTooltip"));
        b.set("#NavToProduction.TextSpans", t("aetherhaven_feasts_production.aetherhaven.ui.materialPicker.back"));
    }

    public static void applyProductionStorageUnlocks(@Nonnull UICommandBuilder b) {
        b.set("#ProductionUnlocksTitleText.TextSpans", t("aetherhaven_feasts_production.aetherhaven.ui.productionUnlocks.title"));
        b.set("#UnlockIntro.TextSpans", t("aetherhaven_feasts_production.aetherhaven.ui.productionUnlocks.intro"));
        b.set("#NavToProduction.TooltipTextSpans", t("aetherhaven_feasts_production.aetherhaven.ui.productionUnlocks.navProductionTooltip"));
        b.set("#NavToProduction.TextSpans", t("aetherhaven_feasts_production.aetherhaven.ui.productionUnlocks.navProduction"));
    }

    public static void applyTownJournalStatic(@Nonnull UICommandBuilder b) {
        b.set("#JournalShellTitle.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.shellTitle"));
        b.set("#TabTown.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.tab.town"));
        b.set("#TabGuide.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.tab.guide"));
        b.set("#TabQuests.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.tab.quests"));
        b.set("#TabSettings.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.tab.settings"));
        b.set(
            "#ActiveTownLabel.TextSpans",
            t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.activeTownLabel")
        );
        b.set("#TownVillagersHeading.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.townVillagersHeading"));
        b.set("#TownPlotsHeading.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.townPlotsHeading"));
        b.set("#SettingsShowBordersLabel.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.showTownBorders"));
        b.set(
            "#SettingsShowBordersLabel.TooltipTextSpans",
            t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.showTownBordersTooltip")
        );
        b.set(
            "#SettingsDifficultyHeading.TextSpans",
            t("aetherhaven_difficulty.aetherhaven.difficulty.journalHeading")
        );
        b.set(
            "#SettingsOpenDifficultyButton.TextSpans",
            t("aetherhaven_difficulty.aetherhaven.difficulty.journalButton")
        );
        b.set("#SettingsGeneralHeading.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.generalHeading"));
        b.set("#SettingsPassiveLabel.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.passiveLabel"));
        b.set("#SettingsConstrHeading.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.constrHeading"));
        b.set("#SettingsConstrBptLabel.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.constrBptLabel"));
        b.set("#SettingsConstrMsLabel.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.constrMsLabel"));
        b.set("#SettingsLootHeading.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.lootHeading"));
        b.set("#SettingsGeodeLabel.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.geodeLabel"));
        b.set("#SettingsChestJewelryLabel.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.chestJewelryLabel"));
        b.set("#SettingsGoldChanceLabel.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.goldChanceLabel"));
        b.set("#SettingsGoldMinLabel.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.goldMinLabel"));
        b.set("#SettingsGoldMaxLabel.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.goldMaxLabel"));
        b.set("#SettingsBreakableHeading.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.breakableHeading"));
        b.set(
            "#SettingsBreakableWeightNoneLabel.TextSpans",
            t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.breakableWeightNoneLabel")
        );
        b.set(
            "#SettingsBreakableWeightOneLabel.TextSpans",
            t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.breakableWeightOneLabel")
        );
        b.set(
            "#SettingsBreakableWeightTwoLabel.TextSpans",
            t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.breakableWeightTwoLabel")
        );
        b.set("#SettingsShopHeading.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.shopHeading"));
        b.set(
            "#SettingsShopMemberPriceLabel.TextSpans",
            t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.shopMemberPriceLabel")
        );
        b.set("#SettingsGiftHeading.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.giftHeading"));
        b.set("#SettingsGiftEnabledLabel.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.giftEnabledLabel"));
        b.set("#SettingsGiftDaysMinLabel.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.giftDaysMinLabel"));
        b.set("#SettingsGiftDaysMaxLabel.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.giftDaysMaxLabel"));
        b.set("#SettingsSaveButton.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.saveButton"));
        b.set("#SettingsResetDefaultsButton.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.resetDefaultsButton"));
        b.set("#SettingsTabPersonal.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.tab.personal"));
        b.set("#SettingsTabServer.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.tab.server"));
        b.set("#SettingsPersonalHeading.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.personalHeading"));
        b.set("#SettingsPersonalHint.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.personalHint"));
        b.set("#SettingsHudHeading.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.hud.heading"));
        b.set("#SettingsHudHint.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.hud.hint"));
        b.set("#SettingsSpeechHeading.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.speech.heading"));
        b.set("#SettingsSpeechHint.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.speech.hint"));
        b.set("#SettingsSpeechEnableLabel.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.speech.enable"));
        b.set("#SettingsSpeechVolumeLabel.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.speech.volume"));
        b.set("#SettingsHudTimeLabel.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.hud.showTime"));
        b.set("#SettingsHudDateLabel.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.hud.showDate"));
        b.set("#SettingsHudGoldLabel.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.hud.showGold"));
        b.set("#SettingsHudQuestsLabel.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.hud.showQuests"));
        b.set("#SettingsHudOpacityLabel.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.hud.opacity"));
        b.set("#SettingsHudStatusPositionLabel.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.hud.statusPosition"));
        b.set("#SettingsHudQuestPositionLabel.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.hud.questPosition"));
        b.set("#SettingsHudStatusXLabel.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.hud.x"));
        b.set("#SettingsHudStatusYLabel.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.hud.y"));
        b.set("#SettingsHudQuestXLabel.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.hud.x"));
        b.set("#SettingsHudQuestYLabel.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.hud.y"));
        b.set("#SettingsRtsPickHeading.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.rtsPickHeading"));
        b.set(
            "#SettingsRtsPickFovLabel.TextSpans",
            t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.rtsPickFovLabel")
        );
        b.set(
            "#SettingsRtsPickAspectLabel.TextSpans",
            t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.rtsPickAspectLabel")
        );
        b.set(
            "#SettingsPersonalSaveButton.TextSpans",
            t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.personalSaveButton")
        );
        b.set(
            "#SettingsPersonalResetButton.TextSpans",
            t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.personalResetButton")
        );
        b.set("#JournalSettingsResetModalTitle.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.resetModalTitle"));
        b.set("#JournalSettingsResetModalText.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.resetModalBody"));
        b.set("#JournalSettingsResetModalConfirm.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.resetModalConfirm"));
        b.set("#JournalSettingsResetModalCancel.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.resetModalCancel"));
        b.set("#SettingsToolsHeading.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.toolsHeading"));
        b.set("#SettingsResetVillagersButton.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.resetVillagers"));
        b.set("#SettingsFixInnButton.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.fixInn"));
        b.set("#SettingsRepairPlotsButton.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.repairPlots"));
        b.set("#SettingsFinishPlotButton.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.finishPlot"));
        b.set("#SettingsVillagerReportButton.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.villagerReport"));
        b.set("#SettingsToolKeybindsHeading.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.toolKeybindsHeading"));
        b.set("#SettingsToolKeybindsHint.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.toolKeybindsHint"));
        b.set("#SettingsOpenToolKeybindsButton.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.toolKeybindsButton"));
        b.set("#GuideListHeading.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.guide.listHeading"));
        b.set("#QuestListHeading.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.questListHeading"));
        b.set("#RewardSectionHeading.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.rewardHeading"));
        b.set("#AbandonQuestButton.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.abandon"));
        b.set("#JournalAbandonModalTitle.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.abandonConfirmTitle"));
        b.set("#JournalAbandonModalText.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.abandonConfirmBody"));
        b.set("#JournalAbandonModalConfirm.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.abandonConfirm"));
        b.set("#JournalAbandonModalCancel.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.abandonCancel"));
        b.set("#JournalPlotRemoveModalTitle.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.plotRemoveConfirmTitle"));
        b.set("#JournalPlotRemoveModalText.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.plotRemoveConfirmBody"));
        b.set("#JournalPlotRemoveModalConfirm.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.plotRemoveConfirm"));
        b.set("#JournalPlotRemoveModalCancel.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.plotRemoveCancel"));
    }

    /** Title only; dynamic lines are set in {@link PathToolStatusHud#refresh}. */
    public static void applyPathToolStatusHudTitle(
        @Nonnull UICommandBuilder b,
        @Nonnull UnaryOperator<String> scoped
    ) {
        b.set(scoped.apply("#PathToolHudTitleText.TextSpans"), t("aetherhaven_items.aetherhaven.pathTool.hudTitle"));
    }

    /** Title only; dynamic lines are set in {@link com.hexvane.aetherhaven.plotcreator.PlotCreatorStatusHud#refresh}. */
    public static void applyPlotCreatorStatusHudTitle(
        @Nonnull UICommandBuilder b,
        @Nonnull UnaryOperator<String> scoped
    ) {
        b.set(
            scoped.apply("#PlotCreatorHudTitleText.TextSpans"),
            t("aetherhaven_plot_creator.aetherhaven.plotcreator.hud.title")
        );
    }

    public static void applyPathToolStyleListPage(@Nonnull UICommandBuilder b) {
        b.set("#PathToolStyleListTitleText.TextSpans", t("aetherhaven_items.aetherhaven.pathTool.styleListTitle"));
        b.set("#CreateButton.TextSpans", t("aetherhaven_items.aetherhaven.pathTool.styleListCreate"));
        b.set("#EditButton.TextSpans", t("aetherhaven_items.aetherhaven.pathTool.styleListEdit"));
        b.set("#CancelButton.TextSpans", t("aetherhaven_items.aetherhaven.pathTool.styleListCancel"));
    }

    public static void applyPathToolWidthPage(@Nonnull UICommandBuilder b) {
        b.set("#PathToolWidthTitleText.TextSpans", t("aetherhaven_items.aetherhaven.pathTool.widthPageTitle"));
        b.set("#PreviewLabel.TextSpans", t("aetherhaven_items.aetherhaven.pathTool.widthPagePreview"));
        b.set("#WidthLabel.TextSpans", t("aetherhaven_items.aetherhaven.pathTool.widthPageSliderLabel"));
        b.set("#ApplyButton.TextSpans", t("aetherhaven_items.aetherhaven.pathTool.widthPageApply"));
        b.set("#CancelButton.TextSpans", t("aetherhaven_items.aetherhaven.pathTool.widthPageCancel"));
    }

    public static void applyPathToolStylePickPage(@Nonnull UICommandBuilder b) {
        b.set("#PathToolStylePickTitleText.TextSpans", t("aetherhaven_items.aetherhaven.pathTool.stylePickTitle"));
        b.set("#ChooseButton.TextSpans", t("aetherhaven_items.aetherhaven.pathTool.stylePickChoose"));
        b.set("#CancelButton.TextSpans", t("aetherhaven_items.aetherhaven.pathTool.stylePickCancel"));
    }

    public static void applyPathToolStyleNamePage(@Nonnull UICommandBuilder b) {
        b.set("#PathToolStyleNameTitleText.TextSpans", t("aetherhaven_items.aetherhaven.pathTool.styleNameTitle"));
        b.set("#Hint.TextSpans", t("aetherhaven_items.aetherhaven.pathTool.styleNameHint"));
        b.set("#NameLabel.TextSpans", t("aetherhaven_items.aetherhaven.pathTool.styleEditNameLabel"));
        b.set("#StyleNameInput.PlaceholderText", t("aetherhaven_items.aetherhaven.pathTool.styleEditNamePlaceholder"));
        b.set("#ContinueButton.TextSpans", t("aetherhaven_items.aetherhaven.pathTool.styleNameContinue"));
        b.set("#CancelButton.TextSpans", t("aetherhaven_items.aetherhaven.pathTool.styleListCancel"));
    }

    /** Title only; dynamic lines are set in {@link com.hexvane.aetherhaven.poi.tool.PoiToolLegendHud#refresh}. */
    public static void applyPoiToolLegendHudTitle(
        @Nonnull UICommandBuilder b,
        @Nonnull UnaryOperator<String> scoped
    ) {
        b.set(scoped.apply("#PoiToolHudTitleText.TextSpans"), t("aetherhaven_items.aetherhaven.poiTool.hudTitle"));
    }

    public static void applyPlotCraftingPage(@Nonnull UICommandBuilder b) {
        b.set("#PlotCraftTitleText.TextSpans", t("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.title"));
        b.set("#PreviewTitle.TextSpans", t("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.previewTitle"));
        b.set("#BuildingInfoTitle.TextSpans", t("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.infoTitle"));
        b.set("#BuildingInfoEmptyText.TextSpans", t("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.infoEmpty"));
        b.set("#InfoGoldTitle.TextSpans", t("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.infoGoldTitle"));
        b.set("#InfoCountsAsTitle.TextSpans", t("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.infoCountsAsTitle"));
        b.set("#InfoRequiredModsTitle.TextSpans", t("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.infoRequiredModsTitle"));
        b.set("#InfoMaterialsTitle.TextSpans", t("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.infoMaterialsTitle"));
        b.set("#VariantSectionTitle.TextSpans", t("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.variantSectionTitle"));
        b.set("#VariantPrev.TooltipTextSpans", t("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.variantPrevTooltip"));
        b.set("#VariantNext.TooltipTextSpans", t("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.variantNextTooltip"));
        b.set("#CraftButton.TextSpans", t("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.craftButton"));
        b.set("#LoadPreviewButton.TextSpans", t("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.loadPreviewButton"));
        b.set("#DownloadButton.TextSpans", t("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.downloadButton"));
        b.set("#RemoveButton.TextSpans", t("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.removeButton"));
        b.set("#ModerationLoadPreviewButton.TextSpans", t("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.moderationLoadPreviewButton"));
        b.set("#ModerationCraftButton.TextSpans", t("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.moderationCraftButton"));
        b.set("#ApproveButton.TextSpans", t("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.approveButton"));
        b.set("#DenyButton.TextSpans", t("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.denyButton"));
        b.set("#MarketplaceRefreshButton.TooltipTextSpans", t("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.refreshMarketplaceTooltip"));
        b.set("#MarketplacePagePrev.TooltipTextSpans", t("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.marketplacePagePrevTooltip"));
        b.set("#MarketplacePageNext.TooltipTextSpans", t("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.marketplacePageNextTooltip"));
        b.set("#StyleFilterTitle.TextSpans", t("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.styleFilterTitle"));
        b.set("#SearchInput.PlaceholderText", t("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.searchPlaceholder"));
    }

    public static void applyQuestBoardPage(@Nonnull UICommandBuilder b) {
        b.set("#QuestBoardTitleText.TextSpans", t("aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.title"));
    }

    public static void applyToolKeybindsPage(@Nonnull UICommandBuilder b) {
        String p = "aetherhaven_ui_journal_items_tail.aetherhaven.ui.toolKeybinds";
        b.set("#ToolKeybindsTitleText.TextSpans", t(p + ".title"));
        b.set("#ToolKeybindsIntro.TextSpans", t(p + ".intro"));
        b.set("#ToolKeybindsSaveButton.TextSpans", t(p + ".saveButton"));
        b.set("#ToolKeybindsResetButton.TextSpans", t(p + ".resetButton"));
        b.set("#ToolKeybindsBackButton.TextSpans", t(p + ".backButton"));
    }
}
