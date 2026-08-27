package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.PrefabMaterialsCatalog;
import com.hexvane.aetherhaven.difficulty.EffectiveBuildingCosts;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * One-time gold return for towns that already built Jimmy/Jszza buildings now sold on the marketplace.
 * Existing plots stay in the world; hidden catalog stubs keep house and job matching working.
 */
public final class RetiredBuiltInPlotMigration {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String NOTICE_LANG =
        "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.retiredBuildingsGoldReturned";

    private RetiredBuiltInPlotMigration() {}

    public static void applyAll(@Nonnull TownManager towns, @Nonnull AetherhavenPlugin plugin) {
        ConstructionCatalog catalog = plugin.getConstructionCatalog();
        PrefabMaterialsCatalog prefabMaterials = plugin.getPrefabMaterialsCatalog();
        boolean any = false;
        for (TownRecord town : towns.allTowns()) {
            if (applyToTown(town, catalog, prefabMaterials)) {
                any = true;
            }
        }
        if (any) {
            TownSaveCoordinator.requestSave(towns);
        }
    }

    static boolean applyToTown(
        @Nonnull TownRecord town,
        @Nonnull ConstructionCatalog catalog,
        @Nonnull PrefabMaterialsCatalog prefabMaterials
    ) {
        long gold = 0L;
        boolean changed = false;
        for (PlotInstance plot : town.getPlotInstances()) {
            String constructionId = plot.getConstructionId();
            if (constructionId == null || constructionId.isBlank()) {
                continue;
            }
            ConstructionDefinition def = catalog.get(constructionId.trim());
            if (def == null || !def.isLegacyPlotSupport()) {
                continue;
            }
            UUID plotId = plot.getPlotId();
            if (plotId == null) {
                continue;
            }
            String plotKey = plotId.toString();
            if (!town.reimbursedRetiredPlotIds().add(plotKey)) {
                continue;
            }
            changed = true;
            if (!PlotJournalRemovalRefundService.buildGoldWasPaid(plot)) {
                continue;
            }
            long paid =
                EffectiveBuildingCosts.forDefinition(def, town.effectiveDifficultyForGameplay(), prefabMaterials)
                    .getTreasuryGoldCoinCost();
            if (paid > 0L) {
                gold += paid;
            }
        }
        if (gold > 0L) {
            town.addTreasuryGoldCoins(gold);
            town.addPendingRetiredBuildingGoldNotice(gold);
            LOGGER.atInfo().log(
                "Returned %s gold for buildings that now come from the marketplace",
                gold
            );
        }
        return changed;
    }

    public static void notifyOwnerIfNeeded(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nullable PlayerRef playerRef,
        @Nonnull UUID playerUuid
    ) {
        if (playerRef == null) {
            return;
        }
        TownManager towns = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = towns.findTownForOwnerInWorld(playerUuid);
        if (town == null) {
            return;
        }
        long gold = town.getPendingRetiredBuildingGoldNotice();
        if (gold <= 0L) {
            return;
        }
        town.consumePendingRetiredBuildingGoldNotice();
        towns.updateTown(town);
        playerRef.sendMessage(Message.translation(NOTICE_LANG).param("gold", String.valueOf(gold)));
    }
}
