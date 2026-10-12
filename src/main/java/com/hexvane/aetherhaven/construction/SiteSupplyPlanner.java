package com.hexvane.aetherhaven.construction;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.difficulty.EffectiveBuildingCosts;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownTerritoryClaims;
import com.hexvane.aetherhaven.warehouse.TownWarehouse;
import com.hexvane.aetherhaven.warehouse.WarehouseAccess;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch;
import com.hexvane.aetherhaven.workplace.ApprenticeWorkplace;
import com.hexvane.aetherhaven.workplace.BuildingOrders;
import com.hexvane.aetherhaven.workplace.BuildingProjectAutoQueue;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.List;
import java.util.UUID;

/**
 * Couriers bring a planned building's materials from the warehouse to its plot sign.
 *
 * <p>Only stock nothing else has reserved is used, and it leaves storage only when a courier collects it at a desk.
 * A building is supplied while it is a plan on a running Builder's yard, and only when the player who put it there may
 * both manage buildings and open town storage. The player still starts the build and pays its gold; whatever a site
 * no longer needs is walked back to storage.
 */
public final class SiteSupplyPlanner {
    private SiteSupplyPlanner() {}

    /** A reservation that would not fit is tried again with half as much, this many times. */
    private static final int SMALLER_ATTEMPTS = 4;

    /** Materials for this building under the town's difficulty, exactly as the plot sign asks for them. */
    public static List<MaterialRequirement> required(TownRecord town, AetherhavenPlugin plugin, PlotInstance plot) {
        ConstructionDefinition definition = plugin.getConstructionCatalog().get(plot.getConstructionId());
        if (definition == null) return List.of();
        return EffectiveBuildingCosts.forDefinition(definition, town.effectiveDifficultyForGameplay(), plugin.getPrefabMaterialsCatalog())
            .getMaterials();
    }

    /** The plot sign is where the player deposits by hand, so it is where couriers deliver. */
    public static WarehouseBatch.Point sign(PlotInstance plot) {
        return new WarehouseBatch.Point(plot.getSignX(), plot.getSignY(), plot.getSignZ());
    }

    /** True while couriers may start new trips for this plan. */
    public static boolean supplied(TownRecord town, PlotInstance plot) {
        if (plot == null || plot.getState() != PlotInstanceState.BLUEPRINTING) return false;
        ApprenticeWorkplace yard = BuildingProjectAutoQueue.holder(town, plot.getPlotId());
        BuildingOrders plan = yard == null ? null : yard.existingBuilding();
        if (plan == null || plan.paused()) return false;
        for (var order : plan.list()) {
            if (!order.plot().equals(plot.getPlotId())) continue;
            UUID author = order.author();
            return author != null && town.hasMemberOrOwner(author) && town.playerCanManageConstructions(author)
                && WarehouseAccess.canBrowse(town, author);
        }
        return false;
    }

    /** Unreserved general storage holding this material, counted no further than {@code enough}. */
    public static int available(TownWarehouse stock, MaterialRequirement line, int enough) {
        long total = 0;
        for (short slot = 0; slot < stock.slots(TownWarehouse.Page.GENERAL) && total < enough; slot++) {
            ItemStack held = stock.stack(TownWarehouse.Page.GENERAL, slot);
            if (ItemStack.isEmpty(held) || !PlotMaterialDepositService.lineAccepts(line, held)) continue;
            total += Math.max(0, held.getQuantity() - stock.reserved(TownWarehouse.Page.GENERAL, slot));
        }
        return (int) Math.min(enough, total);
    }

    /**
     * Reserves the next trip for one plan: the first material it still needs that storage can spare.
     *
     * @return the new delivery, or null when one is already on its way or nothing can be spared
     */
    public static WarehouseBatch reserveNext(TownRecord town, PlotInstance plot, List<MaterialRequirement> required, int courierStacks) {
        TownWarehouse stock = town.getWarehouse();
        if (!supplied(town, plot) || stock.workplaceBatch(plot.getPlotId()) != null) return null;
        for (MaterialRequirement line : PlotMaterialDepositService.missing(plot, required)) {
            int quantity = available(stock, line, line.getCount());
            for (int attempt = 0; attempt <= SMALLER_ATTEMPTS && quantity > 0; attempt++, quantity /= 2) {
                boolean byType = line.getResourceTypeId() != null && !line.getResourceTypeId().isBlank();
                var material = new MaterialQuantity(byType ? null : line.getItemId(), byType ? line.getResourceTypeId().trim() : null, null, quantity, null);
                // No production buildings: a site is supplied from the town's storage, not straight from a workshop.
                WarehouseBatch batch = stock.reserveSiteSupply(plot.getPlotId(), sign(plot), material, List.of(), courierStacks);
                if (batch != null) return batch;
            }
        }
        return null;
    }

    /** Called with workplace planning, after the town's Warehouse Manager has been verified. */
    public static boolean queue(World world, TownRecord town, AetherhavenPlugin plugin, int courierStacks) {
        boolean changed = false;
        for (PlotInstance plot : town.getPlotInstances()) {
            if (!supplied(town, plot) || town.getWarehouse().workplaceBatch(plot.getPlotId()) != null) continue;
            var sign = sign(plot);
            // Like every other delivery, nothing is reserved for a place that is not in live terrain right now.
            if (!TownTerritoryClaims.contains(town, ChunkUtil.chunkCoordinate(sign.x()), ChunkUtil.chunkCoordinate(sign.z()))
                || ChunkSectionBlockUtil.worldChunkIfTicking(world, sign.x(), sign.z()) == null) continue;
            changed |= reserveNext(town, plot, required(town, plugin, plot), courierStacks) != null;
        }
        return changed;
    }

    /**
     * A delivery to cancel: the plan is gone, was started, moved its sign, or already has all it needs. Supplies not
     * yet collected are released at once and anything being carried is returned by the ordinary return trip.
     */
    public static boolean invalid(TownRecord town, AetherhavenPlugin plugin, WarehouseBatch batch) {
        if (!batch.siteSupply()) return false;
        PlotInstance plot = town.findPlotById(batch.workplaceId());
        return invalid(plot, plot == null ? List.of() : required(town, plugin, plot), batch);
    }

    static boolean invalid(PlotInstance plot, List<MaterialRequirement> required, WarehouseBatch batch) {
        return plot == null || plot.getState() != PlotInstanceState.BLUEPRINTING || !sign(plot).equals(batch.destination())
            || PlotMaterialDepositService.missing(plot, required).isEmpty();
    }

    public static boolean destinationReady(World world, TownRecord town, AetherhavenPlugin plugin, WarehouseBatch batch) {
        var point = batch.destination();
        return batch.siteSupply() && !batch.cancelled() && point != null && !invalid(town, plugin, batch)
            && ChunkSectionBlockUtil.worldChunkIfTicking(world, point.x(), point.z()) != null;
    }

    /** Only after the courier has stood at the plot sign. */
    public static boolean deliver(World world, TownRecord town, AetherhavenPlugin plugin, WarehouseBatch batch, String courier) {
        if (!destinationReady(world, town, plugin, batch)) return false;
        PlotInstance plot = town.findPlotById(batch.workplaceId());
        return deliver(town.getWarehouse(), plot, required(town, plugin, plot), batch, courier);
    }

    static boolean deliver(TownWarehouse stock, PlotInstance plot, List<MaterialRequirement> required, WarehouseBatch batch, String courier) {
        return stock.deliverSiteSupply(batch.id(), courier, batch.destination(),
            stack -> PlotMaterialDepositService.depositStack(plot, required, stack));
    }
}
