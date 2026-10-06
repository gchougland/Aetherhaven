package com.hexvane.aetherhaven.warehouse;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.hunter.HuntService;
import com.hexvane.aetherhaven.production.ProductionCatalog;
import com.hexvane.aetherhaven.town.*;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.*;

/** Resolves both reservation and arrival to the same physical building and authoritative inventory. */
public final class ProductionStockSources {
    private ProductionStockSources() {}

    public static List<TownWarehouse.ProductionStock> loaded(World world, TownRecord town, AetherhavenPlugin plugin, long now) {
        var result = new ArrayList<TownWarehouse.ProductionStock>();
        for (var plot : town.getPlotInstances()) {
            var inventory = inventory(town, plugin, plot);
            if (inventory == null) continue;
            var pickup = ProductionStorageLocator.find(world, plot, now);
            if (pickup != null) result.add(new TownWarehouse.ProductionStock(plot.getPlotId(), pickup, inventory));
        }
        return result;
    }

    public static TownWarehouse.ProductionStock at(World world, TownRecord town, AetherhavenPlugin plugin,
                                                   UUID plotId, WarehouseBatch.Point pickup) {
        var plot = plotId == null ? null : town.findPlotById(plotId);
        var inventory = inventory(town, plugin, plot);
        return inventory != null && ProductionStorageLocator.valid(world, plot, pickup)
            ? new TownWarehouse.ProductionStock(plotId, pickup, inventory) : null;
    }

    private static ProductionInventory inventory(TownRecord town, AetherhavenPlugin plugin, PlotInstance plot) {
        if (plot == null || plot.getState() != PlotInstanceState.COMPLETE) return null;
        String type = plugin.getConstructionCatalog().resolveGameplayConstructionId(plot.getConstructionId());
        if (HuntService.CONSTRUCTION.equals(type)) {
            // Hunt spoils belong to the town's active lodge, never one duplicate pool per plot.
            var lodge = town.findCompletePlotWithConstruction(plugin.getConstructionCatalog(), HuntService.CONSTRUCTION);
            return lodge != null && lodge.getPlotId().equals(plot.getPlotId()) ? town.getHunts() : null;
        }
        return ProductionCatalog.isProductionWorkplaceConstruction(type)
            ? town.getOrCreatePlotProduction(plot.getPlotId()) : null;
    }

    public static long availableSpoils(TownRecord town, AetherhavenPlugin plugin, String item) {
        var lodge = town.findCompletePlotWithConstruction(plugin.getConstructionCatalog(), HuntService.CONSTRUCTION);
        long reserved = lodge == null ? 0 : town.getWarehouse().reservedProduction(lodge.getPlotId(), item);
        return Math.max(0, town.getHunts().amount(item) - reserved);
    }
}
