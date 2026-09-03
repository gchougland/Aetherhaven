package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.economy.GoldCoinPayment;
import com.hexvane.aetherhaven.inventory.InventoryMaterials;
import com.hexvane.aetherhaven.restaurant.PlotRestaurantState;
import com.hexvane.aetherhaven.restaurant.RestaurantUpgrades;
import com.hexvane.aetherhaven.restaurant.RestaurantUpgrades.Branch;
import com.hexvane.aetherhaven.restaurant.RestaurantUpgrades.IngredientCost;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Binds the restaurant upgrade tree on the town records plot tab. */
public final class RestaurantUpgradeTreeUi {
    private static final String ROOT = "#ProductionUpgradeTreeSlot #RestaurantUpgradeTree";
    private static final String TOOLTIP_OK = "#3d913f";
    private static final String TOOLTIP_BAD = "#d14d4d";
    private static final String DOT_FILLED = "#d8ccb4";
    private static final String DOT_EMPTY = "#5a5a5a";

    private RestaurantUpgradeTreeUi() {}

    public static void applyChrome(@Nonnull UICommandBuilder b) {
        b.set(ROOT + ".Visible", true);
        b.set("#ProductionUpgradeTreeSlot #ProductionUpgradeTree.Visible", false);
        b.set(ROOT + " #RestaurantUpgradeHeading.TextSpans", t("aetherhaven.ui.restaurantUpgrades.heading"));
    }

    public static void hideProductionTree(@Nonnull UICommandBuilder b) {
        b.set("#ProductionUpgradeTreeSlot #ProductionUpgradeTree.Visible", false);
    }

    public static void bind(
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull PlotRestaurantState state,
        @Nonnull TownRecord town,
        @Nonnull CombinedItemContainer inv,
        boolean allowTreasuryGold
    ) {
        applyChrome(commandBuilder);
        hideProductionTree(commandBuilder);
        bindBranch(
            commandBuilder,
            eventBuilder,
            state,
            town,
            inv,
            allowTreasuryGold,
            Branch.SATIETY,
            " #UpgSatiety",
            " #UpgSatietyTitle",
            " #UpgSatietyIconOuter #UpgSatietyIconInner #UpgSatietyDim",
            " #UpgSatietyDots #UpgSatietyDot",
            "satiety"
        );
        bindBranch(
            commandBuilder,
            eventBuilder,
            state,
            town,
            inv,
            allowTreasuryGold,
            Branch.SERVICE,
            " #UpgService",
            " #UpgServiceTitle",
            " #UpgServiceIconOuter #UpgServiceIconInner #UpgServiceDim",
            " #UpgServiceDots #UpgServiceDot",
            "service"
        );
    }

    private static void bindBranch(
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull PlotRestaurantState state,
        @Nonnull TownRecord town,
        @Nonnull CombinedItemContainer inv,
        boolean allowTreasuryGold,
        @Nonnull Branch branch,
        @Nonnull String btnSuffix,
        @Nonnull String titleSuffix,
        @Nonnull String dimOverlaySuffix,
        @Nonnull String dotPrefix,
        @Nonnull String branchId
    ) {
        String btn = ROOT + btnSuffix;
        int level = RestaurantUpgrades.currentLevel(state, branch);
        boolean maxed = RestaurantUpgrades.isMaxed(state, branch);
        boolean disabled = maxed;
        String nameKey =
            branch == Branch.SATIETY
                ? "aetherhaven.ui.restaurantUpgrades.satiety.name"
                : "aetherhaven.ui.restaurantUpgrades.service.name";
        commandBuilder.set(btn + titleSuffix + ".TextSpans", t(nameKey));
        commandBuilder.set(btn + ".Disabled", disabled);
        commandBuilder.set(btn + dimOverlaySuffix + ".Visible", maxed);
        commandBuilder.set(btn + ".TooltipTextSpans", tooltipFor(state, branch, town, inv, allowTreasuryGold, maxed));
        for (int i = 0; i < RestaurantUpgrades.MAX_BRANCH_LEVEL; i++) {
            commandBuilder.set(
                btn + dotPrefix + i + ".Background",
                i < level ? DOT_FILLED : DOT_EMPTY
            );
        }
        if (!maxed) {
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                btn,
                new EventData().append("Action", "PurchaseRestaurantUpgrade").append("UpgradeBranch", branchId),
                false
            );
        }
    }

    @Nonnull
    private static Message tooltipFor(
        @Nonnull PlotRestaurantState state,
        @Nonnull Branch branch,
        @Nonnull TownRecord town,
        @Nonnull CombinedItemContainer inv,
        boolean allowTreasuryGold,
        boolean maxed
    ) {
        String descKey =
            branch == Branch.SATIETY
                ? "aetherhaven.ui.restaurantUpgrades.satiety.desc"
                : "aetherhaven.ui.restaurantUpgrades.service.desc";
        String nameKey =
            branch == Branch.SATIETY
                ? "aetherhaven.ui.restaurantUpgrades.satiety.name"
                : "aetherhaven.ui.restaurantUpgrades.service.name";
        Message head = Message.join(t(nameKey), Message.raw("\n"), t(descKey));
        if (maxed) {
            return Message.join(head, Message.raw("\n\n"), t("aetherhaven.ui.restaurantUpgrades.tooltip.maxed"));
        }
        int tier = RestaurantUpgrades.nextTier(state, branch);
        Message body = head;
        boolean firstCost = true;
        for (IngredientCost c : RestaurantUpgrades.effectiveIngredientCosts(branch, tier, town)) {
            int held = InventoryMaterials.count(inv, c.itemId());
            boolean ok = held >= c.count();
            Message line =
                t("aetherhaven.ui.restaurantUpgrades.tooltip.ingredientNeed")
                    .param("item", UiMaterialLabels.itemNameMessage(c.itemId()))
                    .param("held", String.valueOf(held))
                    .param("need", String.valueOf(c.count()))
                    .color(ok ? TOOLTIP_OK : TOOLTIP_BAD);
            body = Message.join(body, Message.raw(firstCost ? "\n\n" : "\n"), line);
            firstCost = false;
        }
        long needGold = RestaurantUpgrades.effectiveGoldCost(branch, tier, town);
        if (needGold > 0L) {
            long goldHeld = GoldCoinPayment.totalAvailable(town, inv, allowTreasuryGold);
            boolean goldOk = goldHeld >= needGold;
            Message goldLine =
                t("aetherhaven.ui.restaurantUpgrades.tooltip.goldNeed")
                    .param("held", String.valueOf(goldHeld))
                    .param("need", String.valueOf(needGold))
                    .color(goldOk ? TOOLTIP_OK : TOOLTIP_BAD);
            body = Message.join(body, Message.raw(firstCost ? "\n\n" : "\n"), goldLine);
        }
        return body;
    }

    @Nonnull
    private static Message t(@Nonnull String key) {
        return Message.translation("aetherhaven_ui_town." + key);
    }

    @Nullable
    public static Branch parseBranch(@Nullable String raw) {
        return RestaurantUpgrades.branchFromId(raw);
    }
}
