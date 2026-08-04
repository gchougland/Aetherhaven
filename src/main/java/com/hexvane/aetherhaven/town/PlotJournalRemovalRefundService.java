package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.PrefabMaterialsCatalog;
import com.hexvane.aetherhaven.difficulty.EffectiveBuildingCosts;
import com.hexvane.aetherhaven.difficulty.WorldDifficultyState;
import com.hexvane.aetherhaven.plot.PlotTokenInventory;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/** Refunds when a building is removed from the town journal. */
public final class PlotJournalRemovalRefundService {
    private static final String LANG_PREFIX =
        "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.";

    private PlotJournalRemovalRefundService() {}

    public record RefundResult(long goldToTreasury, boolean tokenGranted) {
        public static final RefundResult NONE = new RefundResult(0L, false);
    }

    public static long computeGoldRefund(
        @Nonnull ConstructionDefinition def,
        @Nonnull PlotInstance plot,
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin
    ) {
        if (!buildGoldWasPaid(plot)) {
            return 0L;
        }
        WorldDifficultyState difficulty = AetherhavenWorldRegistries.getOrLoadWorldDifficulty(world, plugin);
        PrefabMaterialsCatalog prefabMaterials = plugin.getPrefabMaterialsCatalog();
        long paidGold =
            EffectiveBuildingCosts.forDefinition(def, difficulty, prefabMaterials).getTreasuryGoldCoinCost();
        return goldRefundFromEffectiveBuildGold(paidGold);
    }

    public static long goldRefundFromEffectiveBuildGold(long effectiveBuildGold) {
        if (effectiveBuildGold <= 0L) {
            return 0L;
        }
        return (long) Math.floor(effectiveBuildGold * AetherhavenConstants.JOURNAL_PLOT_REMOVE_GOLD_REFUND_FRACTION);
    }

    public static boolean buildGoldWasPaid(@Nonnull PlotInstance plot) {
        PlotInstanceState state = plot.getState();
        return state == PlotInstanceState.ASSEMBLING || state == PlotInstanceState.COMPLETE;
    }

    @Nonnull
    public static RefundResult applyRefunds(
        @Nonnull TownRecord town,
        @Nonnull PlotInstance plot,
        @Nonnull ConstructionDefinition def,
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Player player,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull Vector3d dropPosition
    ) {
        long goldRefund = computeGoldRefund(def, plot, world, plugin);
        if (goldRefund > 0L) {
            town.addTreasuryGoldCoins(goldRefund);
        }
        boolean tokenGranted = false;
        if (def.consumesPlotToken()) {
            PlotTokenInventory.grantDefinitionTokenToPlayer(def, player, ref, store, dropPosition);
            tokenGranted = true;
        }
        return new RefundResult(goldRefund, tokenGranted);
    }

    @Nonnull
    public static String confirmBodyLangKey(
        @Nonnull ConstructionDefinition def,
        @Nonnull PlotInstance plot,
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin
    ) {
        boolean gold = computeGoldRefund(def, plot, world, plugin) > 0L;
        boolean token = def.consumesPlotToken();
        if (gold && token) {
            return LANG_PREFIX + "plotRemoveConfirmBodyGoldAndToken";
        }
        if (token) {
            return LANG_PREFIX + "plotRemoveConfirmBodyTokenOnly";
        }
        if (gold) {
            return LANG_PREFIX + "plotRemoveConfirmBodyGoldOnly";
        }
        return LANG_PREFIX + "plotRemoveConfirmBody";
    }

    @Nonnull
    public static String successLangKey(@Nonnull RefundResult result) {
        if (result.goldToTreasury() > 0L && result.tokenGranted()) {
            return LANG_PREFIX + "removePlotSuccessGoldAndToken";
        }
        if (result.tokenGranted()) {
            return LANG_PREFIX + "removePlotSuccessTokenOnly";
        }
        if (result.goldToTreasury() > 0L) {
            return LANG_PREFIX + "removePlotSuccessGoldOnly";
        }
        return LANG_PREFIX + "removePlotSuccess";
    }
}
