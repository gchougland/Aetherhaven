package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.economy.GoldCoinPayment;
import com.hexvane.aetherhaven.inventory.InventoryMaterials;
import com.hexvane.aetherhaven.production.PlotProductionState;
import com.hexvane.aetherhaven.production.WorkplaceProductionUpgrades;
import com.hexvane.aetherhaven.production.WorkplaceProductionUpgrades.Branch;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Binds the production upgrade skill tree on the town records plot tab. */
public final class ProductionUpgradeTreeUi {
    private static final String ROOT = "#ProductionUpgradeTreeSlot #ProductionUpgradeTree";
    private static final String TOOLTIP_OK = "#3d913f";
    private static final String TOOLTIP_BAD = "#d14d4d";

    private static final String IRON_TINT = "#b0b4bc";
    private static final String THORIUM_TINT = "#5ecf6a";
    private static final String COBALT_TINT = "#4a8fd4";
    private static final String ADAM_TINT = "#d4a0ff";
    private static final String DOT_FILLED = "#d8ccb4";
    private static final String DOT_EMPTY = "#5a5a5a";

    private ProductionUpgradeTreeUi() {}

    public static void applyChrome(@Nonnull UICommandBuilder b) {
        b.set(ROOT + ".Visible", true);
        b.set("#ProductionUpgradeTreeSlot #RestaurantUpgradeTree.Visible", false);
        b.set(ROOT + " #ProductionUpgradeHeading.TextSpans", t("aetherhaven.ui.productionUpgrades.heading"));
    }

    public static void bind(
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull PlotProductionState state,
        @Nonnull TownRecord town,
        @Nonnull CombinedItemContainer inv,
        boolean allowTreasuryGold
    ) {
        applyChrome(commandBuilder);
        bindIron(commandBuilder, eventBuilder, state, town, inv, allowTreasuryGold);
        bindMultiBranch(
            commandBuilder,
            eventBuilder,
            state,
            town,
            inv,
            allowTreasuryGold,
            Branch.THORIUM,
            " #UpgThorium",
            " #UpgThoriumTitle",
            " #UpgThoriumIconOuter #UpgThoriumIconInner #UpgThoriumDim",
            THORIUM_TINT,
            "thorium"
        );
        bindMultiBranch(
            commandBuilder,
            eventBuilder,
            state,
            town,
            inv,
            allowTreasuryGold,
            Branch.COBALT,
            " #UpgCobalt",
            " #UpgCobaltTitle",
            " #UpgCobaltIconOuter #UpgCobaltIconInner #UpgCobaltDim",
            COBALT_TINT,
            "cobalt"
        );
        bindMultiBranch(
            commandBuilder,
            eventBuilder,
            state,
            town,
            inv,
            allowTreasuryGold,
            Branch.ADAMANTITE,
            " #UpgAdamantite",
            " #UpgAdamantiteTitle",
            " #UpgAdamIconOuter #UpgAdamIconInner #UpgAdamDim",
            ADAM_TINT,
            "adamantite"
        );
    }

    private static void bindIron(
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull PlotProductionState state,
        @Nonnull TownRecord town,
        @Nonnull CombinedItemContainer inv,
        boolean allowTreasuryGold
    ) {
        String btn = ROOT + " #UpgIron";
        Branch branch = Branch.IRON;
        boolean maxed = WorkplaceProductionUpgrades.isMaxed(state, branch);
        boolean prereq = WorkplaceProductionUpgrades.prerequisitesMet(state, branch);
        boolean disabled = maxed || !prereq;
        commandBuilder.set(btn + " #UpgIronTitle.TextSpans", t("aetherhaven.ui.productionUpgrades.iron.name"));
        commandBuilder.set(btn + ".Disabled", disabled);
        commandBuilder.set(btn + " #UpgIronIconOuter #UpgIronIconInner #UpgIronDim.Visible", maxed);
        commandBuilder.set(btn + ".TooltipTextSpans", tooltipFor(state, branch, town, inv, allowTreasuryGold, maxed));
        if (!maxed && prereq) {
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                btn,
                new EventData().append("Action", "PurchaseProductionUpgrade").append("UpgradeBranch", "iron"),
                false
            );
        }
    }

    private static void bindMultiBranch(
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull PlotProductionState state,
        @Nonnull TownRecord town,
        @Nonnull CombinedItemContainer inv,
        boolean allowTreasuryGold,
        @Nonnull Branch branch,
        @Nonnull String btnSuffix,
        @Nonnull String titleSuffix,
        @Nonnull String dimOverlaySuffix,
        @Nonnull String tintUnused,
        @Nonnull String branchId
    ) {
        String btn = ROOT + btnSuffix;
        int level = WorkplaceProductionUpgrades.currentLevel(state, branch);
        boolean maxed = WorkplaceProductionUpgrades.isMaxed(state, branch);
        boolean prereq = WorkplaceProductionUpgrades.prerequisitesMet(state, branch);
        boolean disabled = maxed || !prereq;
        String nameKey =
            switch (branch) {
                case THORIUM -> "aetherhaven.ui.productionUpgrades.thorium.name";
                case COBALT -> "aetherhaven.ui.productionUpgrades.cobalt.name";
                case ADAMANTITE -> "aetherhaven.ui.productionUpgrades.adamantite.name";
                default -> "aetherhaven.ui.productionUpgrades.iron.name";
            };
        commandBuilder.set(btn + titleSuffix + ".TextSpans", t(nameKey));
        commandBuilder.set(btn + ".Disabled", disabled);
        commandBuilder.set(btn + dimOverlaySuffix + ".Visible", maxed);
        commandBuilder.set(btn + ".TooltipTextSpans", tooltipFor(state, branch, town, inv, allowTreasuryGold, maxed));
        bindDots(commandBuilder, btn, branch, level);
        if (!maxed && prereq) {
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                btn,
                new EventData().append("Action", "PurchaseProductionUpgrade").append("UpgradeBranch", branchId),
                false
            );
        }
    }

    private static void bindDots(@Nonnull UICommandBuilder b, @Nonnull String btn, @Nonnull Branch branch, int level) {
        String dotsGroup =
            switch (branch) {
                case THORIUM -> " #UpgThoriumDots";
                case COBALT -> " #UpgCobaltDots";
                case ADAMANTITE -> " #UpgAdamDots";
                default -> null;
            };
        String prefix =
            switch (branch) {
                case THORIUM -> " #UpgThoriumDot";
                case COBALT -> " #UpgCobaltDot";
                case ADAMANTITE -> " #UpgAdamDot";
                default -> null;
            };
        if (prefix == null || dotsGroup == null) {
            return;
        }
        for (int i = 0; i < WorkplaceProductionUpgrades.MAX_BRANCH_LEVEL; i++) {
            b.set(btn + dotsGroup + prefix + i + ".Background", i < level ? DOT_FILLED : DOT_EMPTY);
        }
    }

    @Nonnull
    private static Message tooltipFor(
        @Nonnull PlotProductionState state,
        @Nonnull Branch branch,
        @Nonnull TownRecord town,
        @Nonnull CombinedItemContainer inv,
        boolean allowTreasuryGold,
        boolean maxed
    ) {
        String descKey =
            switch (branch) {
                case IRON -> "aetherhaven.ui.productionUpgrades.iron.desc";
                case THORIUM -> "aetherhaven.ui.productionUpgrades.thorium.desc";
                case COBALT -> "aetherhaven.ui.productionUpgrades.cobalt.desc";
                case ADAMANTITE -> "aetherhaven.ui.productionUpgrades.adamantite.desc";
            };
        String nameKey =
            switch (branch) {
                case IRON -> "aetherhaven.ui.productionUpgrades.iron.name";
                case THORIUM -> "aetherhaven.ui.productionUpgrades.thorium.name";
                case COBALT -> "aetherhaven.ui.productionUpgrades.cobalt.name";
                case ADAMANTITE -> "aetherhaven.ui.productionUpgrades.adamantite.name";
            };
        Message head = Message.join(t(nameKey), Message.raw("\n"), t(descKey));
        if (maxed) {
            return Message.join(head, Message.raw("\n\n"), t("aetherhaven.ui.productionUpgrades.tooltip.maxed"));
        }
        if (!WorkplaceProductionUpgrades.prerequisitesMet(state, branch)) {
            return Message.join(head, Message.raw("\n\n"), t("aetherhaven.ui.productionUpgrades.tooltip.locked"));
        }
        int tier = WorkplaceProductionUpgrades.nextTier(state, branch);
        String ingot = WorkplaceProductionUpgrades.ingotItemId(branch);
        int needIngot = ingot != null ? WorkplaceProductionUpgrades.ingotCost(branch, tier) : 0;
        long needGold = WorkplaceProductionUpgrades.goldCost(branch, tier);
        int heldIngot = ingot != null ? InventoryMaterials.count(inv, ingot) : 0;
        boolean ingotOk = heldIngot >= needIngot;
        Message ingotLine =
            t("aetherhaven.ui.productionUpgrades.tooltip.ingotNeed")
                .param("item", UiMaterialLabels.itemNameMessage(ingot))
                .param("held", String.valueOf(heldIngot))
                .param("need", String.valueOf(needIngot))
                .color(ingotOk ? TOOLTIP_OK : TOOLTIP_BAD);
        Message body = Message.join(head, Message.raw("\n\n"), ingotLine);
        if (needGold > 0L) {
            long goldHeld = GoldCoinPayment.totalAvailable(town, inv, allowTreasuryGold);
            boolean goldOk = goldHeld >= needGold;
            Message goldLine =
                t("aetherhaven.ui.productionUpgrades.tooltip.goldNeed")
                    .param("held", String.valueOf(goldHeld))
                    .param("need", String.valueOf(needGold))
                    .color(goldOk ? TOOLTIP_OK : TOOLTIP_BAD);
            body = Message.join(body, Message.raw("\n"), goldLine);
        }
        return body;
    }

    @Nonnull
    private static Message t(@Nonnull String key) {
        return Message.translation("aetherhaven_ui_town." + key);
    }

    @Nullable
    public static Branch parseBranch(@Nullable String raw) {
        return WorkplaceProductionUpgrades.branchFromId(raw);
    }
}
