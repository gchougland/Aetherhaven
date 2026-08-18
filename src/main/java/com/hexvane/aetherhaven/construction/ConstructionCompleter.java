package com.hexvane.aetherhaven.construction;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.inn.BlacksmithShopCompletion;
import com.hexvane.aetherhaven.inn.FarmerPlotCompletion;
import com.hexvane.aetherhaven.inn.GaiaAltarCompletion;
import com.hexvane.aetherhaven.inn.BarnCompletion;
import com.hexvane.aetherhaven.inn.LumbermillCompletion;
import com.hexvane.aetherhaven.inn.InnPoolService;
import com.hexvane.aetherhaven.inn.InnVisitorShopPromotion;
import com.hexvane.aetherhaven.inn.MerchantStallCompletion;
import com.hexvane.aetherhaven.inn.MinerHutCompletion;
import com.hexvane.aetherhaven.festival.FestivalPrefabSize;
import com.hexvane.aetherhaven.guild.GuildHallCompletion;
import com.hexvane.aetherhaven.map.TeleporterWarpSanitizer;
import com.hexvane.aetherhaven.poi.PoiExtractor;
import com.hexvane.aetherhaven.poi.marker.PoiMarkerDedupUtil;
import com.hexvane.aetherhaven.shopspot.ShopSpotExtractor;
import com.hexvane.aetherhaven.tourist.TouristPortalExtractor;
import com.hexvane.aetherhaven.plot.PlotBlockStamper;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.placement.PlotFootprintUtil;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.WallSegmentRecord;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import java.nio.file.Path;
import com.hexvane.aetherhaven.production.PlotProductionState;
import com.hexvane.aetherhaven.production.ProductionCatalog;
import com.hexvane.aetherhaven.production.ProductionEffectiveCatalog;
import com.hexvane.aetherhaven.quest.QuestProgressionService;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.logger.HytaleLogger;
import org.joml.Vector3i;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;

public final class ConstructionCompleter {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private ConstructionCompleter() {}

    /**
     * Run on the world thread after prefab placement finishes: either {@link com.hexvane.aetherhaven.prefab.ConstructionAnimator}
     * (sign removed in {@code onComplete}) or passive assembly (sign removed at assembly start; do not break again here).
     */
    public static void finishBuild(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID ownerUuid,
        @Nonnull UUID plotId,
        @Nonnull Vector3i prefabAnchorWorld,
        @Nonnull Rotation prefabYaw
    ) {
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.findTownOwningPlot(plotId);
        if (town == null) {
            LOGGER.atWarning().log("Construction complete but no town owns plot %s", plotId);
            return;
        }
        // Prefab is already fully written; do not gate bookkeeping on the starter's manage permission (members who
        // staff-finish a build must still get COMPLETE, POIs, and linked blocks).
        if (!town.playerCanManageConstructions(ownerUuid)) {
            LOGGER.atInfo().log(
                "Construction complete for plot %s by player %s without manage permission; applying town bookkeeping",
                plotId,
                ownerUuid
            );
        }
        PlotInstance plot = town.findPlotById(plotId);
        if (plot == null) {
            LOGGER.atWarning().log("Construction complete but plot %s missing in town %s", plotId, town.getTownId());
            return;
        }
        ConstructionDefinition def = plugin.getConstructionCatalog().get(plot.getConstructionId());
        if (def == null) {
            LOGGER.atWarning().log("Unknown construction id %s for plot %s", plot.getConstructionId(), plotId);
        }

        long now = System.currentTimeMillis();
        plot.clearAssemblyPersistence();
        plot.setPrefabWorldPlacement(prefabAnchorWorld.x, prefabAnchorWorld.y, prefabAnchorWorld.z, prefabYaw);

        if (def != null && def.isWallSegment()) {
            PlotFootprintRecord fp = plot.toFootprint();
            Path prefabPath = PrefabResolveUtil.resolvePrefabPath(def.getPrefabPath());
            if (prefabPath != null) {
                IPrefabBuffer buf = PrefabBufferUtil.getCached(prefabPath);
                try {
                    fp = PlotFootprintUtil.computeFootprint(prefabAnchorWorld, prefabYaw, buf, def);
                } finally {
                }
            }
            town.removePlotInstance(plotId);
            town.addWallSegment(
                new WallSegmentRecord(plotId, plot.getConstructionId(), fp, prefabAnchorWorld.x, prefabAnchorWorld.y, prefabAnchorWorld.z, prefabYaw, now)
            );
            tm.updateTown(town);
            return;
        }

        if (def != null && def.isDecorationPlot()) {
            // Keep prefab management shelves as inert props (e.g. Jimmy's School). The plot is not registered, so
            // they are not town-linked; stripping them removed the furniture from decoration builds.
            town.removePlotInstance(plotId);
            tm.updateTown(town);
            return;
        }

        plot.setState(PlotInstanceState.COMPLETE);
        plot.setLastStateChangeEpochMs(now);
        if (def != null && FestivalPrefabSize.usesReservedFootprint(def)) {
            plot.applySignAndFootprint(
                plot.getSignX(),
                plot.getSignY(),
                plot.getSignZ(),
                FestivalPrefabSize.footprintAt(prefabAnchorWorld, prefabYaw)
            );
        }
        QuestProgressionService.onConstructionBuilt(plugin, town, plot.getConstructionId());
        tm.updateTown(town);

        if (def != null) {
            List<String> gameplayIds = plugin.getConstructionCatalog().resolveGameplayConstructionIds(def.getId());
            if (gameplayIds.isEmpty()) {
                gameplayIds = List.of(def.getGameplayConstructionId());
            }
            Store<EntityStore> entityStore =
                world.getEntityStore() != null ? world.getEntityStore().getStore() : null;
            if (entityStore != null) {
                PoiMarkerDedupUtil.dedupeInPlot(entityStore, plot);
                PoiExtractor.registerForCompletedBuild(
                    plugin,
                    world,
                    entityStore,
                    town,
                    plotId,
                    plot,
                    def.getId(),
                    prefabAnchorWorld,
                    prefabYaw
                );
                ShopSpotExtractor.registerForCompletedBuild(world, plugin, entityStore, town, plotId, plot);
                TouristPortalExtractor.registerForCompletedBuild(world, plugin, entityStore, town, plotId, plot);
            }
            PlotBlockStamper.stampAllLinkedBlocks(world, town, plot, def, prefabAnchorWorld, prefabYaw);
            TeleporterWarpSanitizer.schedulePlotFootprintSanitize(world, plot.toFootprint());
            for (String gid : gameplayIds) {
                if (AetherhavenConstants.CONSTRUCTION_PLOT_MARKET_STALL.equals(gid)) {
                    MerchantStallCompletion.onStallBuilt(world, plugin, town, plotId, tm);
                }
                if (AetherhavenConstants.CONSTRUCTION_PLOT_FARM.equals(gid)) {
                    FarmerPlotCompletion.onFarmBuilt(world, plugin, town, plotId, tm);
                }
                if (AetherhavenConstants.CONSTRUCTION_PLOT_BLACKSMITH_SHOP.equals(gid)) {
                    BlacksmithShopCompletion.onShopBuilt(world, plugin, town, plotId, tm);
                }
                if (AetherhavenConstants.CONSTRUCTION_PLOT_GAIA_ALTAR.equals(gid)) {
                    GaiaAltarCompletion.onAltarBuilt(world, plugin, town, plotId, tm);
                }
                if (AetherhavenConstants.CONSTRUCTION_PLOT_MINERS_HUT.equals(gid)) {
                    MinerHutCompletion.onMinerHutBuilt(world, plugin, town, plotId, tm);
                }
                if (AetherhavenConstants.CONSTRUCTION_PLOT_LUMBERMILL.equals(gid)) {
                    LumbermillCompletion.onLumbermillBuilt(world, plugin, town, plotId, tm);
                }
                if (AetherhavenConstants.CONSTRUCTION_PLOT_BARN.equals(gid)) {
                    BarnCompletion.onBarnBuilt(world, plugin, town, plotId, tm);
                }
                if (AetherhavenConstants.CONSTRUCTION_PLOT_GUILD_HALL.equals(gid)) {
                    GuildHallCompletion.onGuildHallBuilt(world, plugin, town, plotId, tm);
                }
                InnVisitorShopPromotion.promoteForCompletedPlot(
                    world,
                    plugin,
                    town,
                    plotId,
                    tm,
                    def.getId(),
                    gid
                );
            }
            String productionGid =
                gameplayIds.stream()
                    .filter(ProductionCatalog::isProductionWorkplaceConstruction)
                    .findFirst()
                    .orElse(null);
            if (productionGid != null) {
                PlotProductionState pps = town.getOrCreatePlotProduction(plotId);
                ProductionCatalog.Entry eff =
                    ProductionEffectiveCatalog.effective(
                        plugin.getProductionCatalog(),
                        plugin.getWorkplaceUnlockCatalog(),
                        productionGid,
                        pps
                    );
                if (eff != null && eff.catalogSize() > 0) {
                    pps.initDefaultSlotCursorsForNewWorkplace(eff.catalogSize());
                    tm.updateTown(town);
                }
            }
            // If onStallBuilt/onFarmBuilt/etc. no-op (e.g. missing WORK POI, NPC ref not resolved), promotion may still
            // succeed here without requiring a separate fixinn run.
            if (entityStore != null) {
                InnPoolService.repairInnPoolForTown(world, plugin, town, tm, entityStore, false);
            }
        }
    }
}
