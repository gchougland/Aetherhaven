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
        for(var site:town.getApprenticeWorkplaces().sites()){
            var inventory=com.hexvane.aetherhaven.workplace.MineshaftOutput.find(town,site.id());
            if(inventory==null)continue;
            var point=com.hexvane.aetherhaven.workplace.NativeExcavation.bufferPoint(town,site);
            if(com.hexvane.aetherhaven.workplace.NativeExcavation.observe(world,point).loaded())result.add(new TownWarehouse.ProductionStock(site.id(),point,inventory));
        }
        return result;
    }

    public static TownWarehouse.ProductionStock at(World world, TownRecord town, AetherhavenPlugin plugin,
                                                   UUID plotId, WarehouseBatch.Point pickup) {
        var output=com.hexvane.aetherhaven.workplace.MineshaftOutput.find(town,plotId);
        if(output!=null){var site=town.getApprenticeWorkplaces().find(plotId);return Objects.equals(pickup,com.hexvane.aetherhaven.workplace.NativeExcavation.bufferPoint(town,site))&&com.hexvane.aetherhaven.workplace.NativeExcavation.observe(world,pickup).loaded()?new TownWarehouse.ProductionStock(plotId,pickup,output):null;}
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
