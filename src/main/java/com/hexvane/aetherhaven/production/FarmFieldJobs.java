package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.mastery.CoreVillagerMasteryService;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.List;
import java.util.function.DoubleSupplier;

/**
 * The Farmer's finished actions, each one a change to the real field and to the farm's records together. The work
 * service calls these once she has stood at the cell for long enough, and the in server check calls the very same
 * methods, so what is tested is what she does.
 */
public final class FarmFieldJobs {
    private FarmFieldJobs() {}

    private static long reservedEssence(TownRecord town, PlotInstance plot) {
        return Math.max(0, town.getWarehouse().reservedProduction(plot.getPlotId(), FarmFieldRules.get().seeds().essence()));
    }
    /** Life Essence the farm can spend on seeds: what is stored, less what the warehouse has already been promised. */
    public static long spareEssence(TownRecord town, PlotInstance plot, PlotProductionState production) {
        return Math.max(0, production.getAmount(FarmFieldRules.get().seeds().essence()) - reservedEssence(town, plot));
    }
    /** True when a seed of this crop can be had right now. */
    public static boolean seedReady(TownRecord town, PlotInstance plot, PlotProductionState production, String crop) {
        String seed = NativeFarmCrops.seedFor(crop);
        return FarmFieldWork.seedReady(production.farm(), seed, FarmFields.seedCost(seed), spareEssence(town, plot, production));
    }
    /** Gathering a crop is worth it while the stores can take it, or while the compost heap can take what they cannot. */
    public static boolean harvestWanted(PlotProductionState production, ProductionCatalog.Entry entry, String crop) {
        var farm = production.farm();
        return production.getAmount(crop) + farm.basket().getOrDefault(crop, 0) < FarmUpgrades.capacity(production, entry, crop)
            || farm.compost() < FarmFieldRules.get().compost().keep();
    }

    /**
     * Tills the ground if it needs it, sets the planned crop's seedling and waters it. One seed is used: from the
     * seed store, or made from the farm's Life Essence. A favoured crop goes in already sprouted.
     *
     * @param favoured this week's favoured crops for this farm
     */
    public static boolean plant(World world, TownRecord town, PlotInstance plot, PlotProductionState production,
            ProductionCatalog.Entry entry, int slot, int x, int z, List<String> favoured) {
        var farm = production.farm(); var cell = farm.cell(slot, x, z);
        if (cell == null || !cell.planned()) return false;
        String crop = cell.crop(), seed = NativeFarmCrops.seedFor(crop);
        var grown = NativeFarmCrops.byOutput(crop); var at = cell.point();
        if (grown == null || seed == null || FarmFields.cropIssue(town, entry, crop) != null
            || !FarmFieldWorld.workable(world, town, at) || !FarmFieldWorld.free(world, at)) return false;
        var rules = FarmFieldRules.get(); int cost = FarmFields.seedCost(seed); long reserved = reservedEssence(town, plot);
        if (!FarmFieldWork.seedReady(farm, seed, cost, production.getAmount(rules.seeds().essence()) - reserved)) return false;
        if (!FarmFieldWorld.till(world, at) || !FarmFieldWorld.plant(world, at, grown)) return false;
        if (FarmFieldWork.takeSeed(production, seed, rules.seeds().essence(), cost, reserved) == null) {
            FarmFieldWorld.uproot(world, at); return false;
        }
        farm.planted(slot, x, z, crop);
        // The seedling's growth record can arrive a moment after its block, so the sprout is tried again next task.
        if (favoured.contains(crop) && !FarmFieldWorld.sprout(world, at, grown))
            world.execute(() -> FarmFieldWorld.sprout(world, at, grown));
        if (farm.soil(slot, x, z) == FarmFieldsState.WORN) FarmFieldWorld.dry(world, at);
        else FarmFieldWorld.water(world, at, rules.work().waterHours());
        return true;
    }

    /**
     * Gathers the ripe crop standing in a planned cell: the game's own harvest list is rolled, the plant comes out
     * as it does for a player, the harvest goes into her basket, and the planned crop is sown again at once when a
     * seed is to hand.
     *
     * @param dice numbers from 0 to 1 for the Golden Harvest and for the seed that may come back
     * @return what was gathered, or null when nothing ripe stood there
     */
    public static FarmFieldsState.Harvest harvest(World world, TownRecord town, PlotInstance plot, PlotProductionState production,
            ProductionCatalog.Entry entry, int slot, int x, int z, List<String> favoured, DoubleSupplier dice) {
        var farm = production.farm(); var cell = farm.cell(slot, x, z);
        if (cell == null || !cell.planned()) return null;
        var at = cell.point(); var standing = FarmFieldWorld.cropAt(world, at);
        if (standing == null || !FarmFieldWorld.workable(world, town, at)) return null;
        int[] rolled = FarmFieldWorld.rollHarvest(world, at, standing);
        if (rolled == null || !FarmFieldWorld.uproot(world, at)) return null;
        var result = farm.harvest(slot, x, z, standing.output(), NativeFarmCrops.seedFor(standing.output()), rolled[0], rolled[1],
            favoured.contains(standing.output()), FarmUpgrades.seedChance(production), dice.getAsDouble(), dice.getAsDouble(), FarmFieldRules.get());
        if (result != null && result.soilAfter() == FarmFieldsState.WORN) FarmFieldWorld.dry(world, at);
        plant(world, town, plot, production, entry, slot, x, z, favoured);
        return result;
    }

    /**
     * Waters the cell she stands at and, with better watering, the planned cells around it. Worn soil is passed
     * over: it will not hold water.
     *
     * @return how many cells were watered
     */
    public static int water(World world, TownRecord town, PlotProductionState production, int x, int y, int z) {
        var farm = production.farm(); int radius = FarmUpgrades.waterRadius(production), watered = 0;
        int hours = FarmFieldRules.get().work().waterHours();
        for (var field : farm.fields().values()) for (var cell : field.cells()) {
            if (!cell.planned() || cell.soil() == FarmFieldsState.WORN || Math.abs(cell.x() - x) > radius || Math.abs(cell.z() - z) > radius
                || Math.abs(cell.y() - y) > 1 || !FarmFieldWorld.workable(world, town, cell.point())) continue;
            if (FarmFieldWorld.water(world, cell.point(), hours)) watered++;
        }
        return watered;
    }

    /** One compost is worked into one cell, lifting its soil a step. */
    public static boolean compost(World world, TownRecord town, PlotProductionState production, int slot, int x, int z) {
        var cell = production.farm().cell(slot, x, z);
        return cell != null && FarmFieldWorld.workable(world, town, cell.point())
            && production.farm().spread(slot, x, z, FarmUpgrades.compostEnriches(production));
    }

    /** Empties the basket into the farm's stores. Spare crops become compost, and what was stored teaches the Farmer. */
    public static FarmFieldsState.Delivery deliver(TownRecord town, PlotProductionState production, ProductionCatalog.Entry entry) {
        var rules = FarmFieldRules.get();
        var delivery = production.farm().deliverAll(production, item -> FarmUpgrades.capacity(production, entry, item),
            item -> NativeFarmCrops.byOutput(item) != null, FarmUpgrades.cropsPerCompost(production), rules.compost().keep());
        long stored = delivery.stored().values().stream().mapToLong(Integer::longValue).sum();
        if (stored > 0 && rules.xp() > 0) CoreVillagerMasteryService.progress(town, FarmFields.KIND).earnXp(stored * rules.xp());
        return delivery;
    }
}
