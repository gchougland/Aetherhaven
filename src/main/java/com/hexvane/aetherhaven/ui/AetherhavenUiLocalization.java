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
        b.set("#HouseResidentDropdown #HouseResidentFieldLabel.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.houseResident"));
        b.set("#AssignHouseResidentButton.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.assignHouseResident"));
        b.set("#PickUpPlotButton.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.pickUpPlot"));
        b.set("#BuildButton.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.build"));
        b.set("#InviteLabel.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotmanagement.inviteLabel"));
        b.set("#InviteSendButton.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotmanagement.inviteSend"));
        b.set("#InvitePlayerInput.PlaceholderText", t("aetherhaven_ui_town.aetherhaven.ui.plotmanagement.invitePlaceholder"));
        b.set("#MoveBuildingModalTitle.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.moveBuildingModalTitle"));
        b.set("#MoveBuildingModalText.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.moveBuildingModalBody"));
        b.set("#MoveBuildingConfirmButton.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.moveBuildingModalConfirm"));
        b.set("#MoveBuildingCancelButton.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.moveBuildingModalCancel"));
    }

    public static void applyTownMemberPermissionsPage(@Nonnull UICommandBuilder b) {
        b.set("#MemberPermTitleText.TextSpans", t("aetherhaven_town.aetherhaven.ui.memberPermissions.title"));
        b.set("#MemberPermBack.TooltipTextSpans", t("aetherhaven_town.aetherhaven.ui.memberPermissions.backTooltip"));
        b.set("#CapPlacePlots.TextSpans", t("aetherhaven_town.aetherhaven.ui.memberPermissions.placePlots"));
        b.set("#CapManageConstructions.TextSpans", t("aetherhaven_town.aetherhaven.ui.memberPermissions.manageConstructions"));
        b.set("#CapSpendTreasuryGold.TextSpans", t("aetherhaven_town.aetherhaven.ui.memberPermissions.spendTreasuryGold"));
        b.set("#CapOpenTreasuryPanel.TextSpans", t("aetherhaven_town.aetherhaven.ui.memberPermissions.openTreasuryPanel"));
        b.set("#CapAcceptQuests.TextSpans", t("aetherhaven_town.aetherhaven.ui.memberPermissions.acceptQuests"));
        b.set("#CapCompleteQuests.TextSpans", t("aetherhaven_town.aetherhaven.ui.memberPermissions.completeQuests"));
        b.set("#CapAbandonQuests.TextSpans", t("aetherhaven_town.aetherhaven.ui.memberPermissions.abandonQuests"));
        b.set("#CapReviveVillagers.TextSpans", t("aetherhaven_town.aetherhaven.ui.memberPermissions.reviveVillagers"));
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

    public static void applyPlotSignAdmin(@Nonnull UICommandBuilder b) {
        b.set("#PlotSignAdminTitleText.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotadmin.title"));
        b.set("#ConstructionFieldLabel.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotadmin.construction"));
        b.set("#GiveButton.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.plotadmin.give"));
    }

    public static void applyDialoguePage(@Nonnull UICommandBuilder b) {
        b.set("#ReputationLabel.TextSpans", t("aetherhaven_ui_town.aetherhaven.ui.reputation.label"));
    }

    public static void applyGeodeOpen(@Nonnull UICommandBuilder b) {
        b.set("#GeodeOpenTitleText.TextSpans", t("aetherhaven_jewelry_geode.aetherhaven.ui.geodeopen.title"));
    }

    public static void applyGaiaStatueRevivePage(@Nonnull UICommandBuilder b) {
        b.set("#GaiaReviveTitleText.TextSpans", t("aetherhaven_jewelry_geode.aetherhaven.ui.gaiaStatue.title"));
        b.set("#Footer.TextSpans", t("aetherhaven_jewelry_geode.aetherhaven.ui.gaiaStatue.footer"));
    }

    public static void applyGaiaStatueReviveRow(@Nonnull UICommandBuilder b, @Nonnull String rowPath) {
        b.set(rowPath + " #ReviveButton.TextSpans", t("aetherhaven_jewelry_geode.aetherhaven.ui.gaiaStatue.revive"));
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
        b.set("#CharterTownTitleText.TextSpans", t("aetherhaven_jewelry_geode.aetherhaven.ui.chartertown.title"));
        b.set("#MoveCharterHint.TextSpans", t("aetherhaven_jewelry_geode.aetherhaven.ui.chartertown.moveCharterHint"));
        b.set("#MoveCharterButton.TooltipTextSpans", t("aetherhaven_jewelry_geode.aetherhaven.ui.chartertown.moveCharterTooltip"));
        b.set("#TownNameLabel.TextSpans", t("aetherhaven_jewelry_geode.aetherhaven.ui.chartertown.townNameLabel"));
        b.set("#SaveTownNameButton.TextSpans", t("aetherhaven_jewelry_geode.aetherhaven.ui.chartertown.saveTownName"));
        b.set("#NameInput.PlaceholderText", t("aetherhaven_jewelry_geode.aetherhaven.ui.chartertown.namePlaceholder"));
        b.set("#OwnerOnlyHint.TextSpans", t("aetherhaven_jewelry_geode.aetherhaven.ui.chartertown.ownerOnlyHint"));
        b.set("#DissolveButton.TextSpans", t("aetherhaven_jewelry_geode.aetherhaven.ui.chartertown.dissolve"));
        b.set("#DissolveHint.TextSpans", t("aetherhaven_jewelry_geode.aetherhaven.ui.chartertown.dissolveHint"));
        b.set("#CharterModalConfirmButton.TextSpans", t("aetherhaven_jewelry_geode.aetherhaven.ui.chartertown.dissolveConfirm"));
        b.set("#CharterModalCancelButton.TextSpans", t("aetherhaven_jewelry_geode.aetherhaven.ui.chartertown.modalCancel"));
    }

    public static void applyProductionStorage(@Nonnull UICommandBuilder b) {
        b.set("#ProductionTitleText.TextSpans", t("aetherhaven_feasts_production.aetherhaven.ui.production.title"));
        Message prevTip = t("aetherhaven_feasts_production.aetherhaven.ui.production.prevTooltip");
        Message nextTip = t("aetherhaven_feasts_production.aetherhaven.ui.production.nextTooltip");
        Message take1 = t("aetherhaven_feasts_production.aetherhaven.ui.production.take1");
        Message take10 = t("aetherhaven_feasts_production.aetherhaven.ui.production.take10");
        Message take100 = t("aetherhaven_feasts_production.aetherhaven.ui.production.take100");
        for (int i = 0; i < 3; i++) {
            String p = "#Slot" + i;
            b.set(p + "Prev.TooltipTextSpans", prevTip);
            b.set(p + "Next.TooltipTextSpans", nextTip);
            b.set(p + "Take1.TextSpans", take1);
            b.set(p + "Take10.TextSpans", take10);
            b.set(p + "Take100.TextSpans", take100);
        }
        b.set("#OpenUnlocks.TooltipTextSpans", t("aetherhaven_feasts_production.aetherhaven.ui.production.unlockTooltip"));
        b.set("#OpenUnlocks.TextSpans", t("aetherhaven_feasts_production.aetherhaven.ui.production.unlockButton"));
    }

    public static void applyProductionStorageUnlocks(@Nonnull UICommandBuilder b) {
        b.set("#ProductionUnlocksTitleText.TextSpans", t("aetherhaven_feasts_production.aetherhaven.ui.productionUnlocks.title"));
        b.set("#UnlockIntro.TextSpans", t("aetherhaven_feasts_production.aetherhaven.ui.productionUnlocks.intro"));
        b.set("#NavToProduction.TooltipTextSpans", t("aetherhaven_feasts_production.aetherhaven.ui.productionUnlocks.navProductionTooltip"));
        b.set("#NavToProduction.TextSpans", t("aetherhaven_feasts_production.aetherhaven.ui.productionUnlocks.navProduction"));
    }

    public static void applyQuestJournalStatic(@Nonnull UICommandBuilder b) {
        b.set("#JournalPageTitle.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.questjournal.title"));
        b.set("#Hint.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.questjournal.hint"));
        b.set("#DetailTitle.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.questjournal.detailPlaceholder"));
        b.set("#DetailBody.TextSpans", t("aetherhaven_ui_journal_items_tail.aetherhaven.ui.questjournal.detailPlaceholderBody"));
    }

    /** Title only; dynamic lines are set in {@link PathToolStatusHud#refresh}. */
    public static void applyPathToolStatusHudTitle(
        @Nonnull UICommandBuilder b,
        @Nonnull UnaryOperator<String> scoped
    ) {
        b.set(scoped.apply("#PathToolHudTitleText.TextSpans"), t("aetherhaven_items.aetherhaven.pathTool.hudTitle"));
    }
}
