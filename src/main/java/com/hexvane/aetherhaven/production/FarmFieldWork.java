package com.hexvane.aetherhaven.production;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

/**
 * What the Farmer does next, decided from the crop plan and from what stands in each planned cell. Nothing here
 * touches the world: the caller says what each cell looks like, so the same choices can be checked without a server.
 */
public final class FarmFieldWork {
    private FarmFieldWork() {}

    /**
     * In the order she prefers them: a full basket goes home, compost goes on tired soil, then ripe crops, empty
     * cells and dry soil. Compost comes early because a busy field always has something ripe, and soil that waited
     * for a quiet moment would never be mended.
     */
    public enum Action { DELIVER, COMPOST, HARVEST, PLANT, WATER }

    /**
     * What one planned cell looks like right now.
     *
     * @param workable loaded, inside town and not handed to a townsfolk workplace
     * @param plantable free air over farmland or over ground the hoe can till
     * @param standing the crop item of the registered crop standing there, or null
     * @param ripe true when that crop is at its last stage
     * @param thirsty true when the farmland will soon be dry
     */
    public record View(boolean workable, boolean plantable, String standing, boolean ripe, boolean thirsty) {
        public static final View FAR = new View(false, false, null, false, false);
    }
    public interface Look { View at(int slot, FarmFieldsState.Cell cell); }

    /**
     * @param crop for a harvest the crop standing there, for a planting the planned crop
     */
    public record Job(Action action, int slot, FarmFieldsState.Cell cell, String crop) {}

    /**
     * @param onDuty false outside her shift, when she only carries home what is already in her basket
     * @param basket items she carries before walking to the stores
     * @param growable true for a crop this farm's Farmer may plant
     * @param seedReady true when a seed of this crop can be had, from the seed store or made from Life Essence
     * @param harvestWanted true while gathering this crop is worth it: the stores or the compost heap can take it
     * @param compostEnriches true when compost may also go on Good soil to make it Rich
     * @param skip cells to leave alone for now, such as one she could not reach a moment ago
     * @param distance how far a cell is from where she stands
     */
    public record Needs(boolean onDuty, int basket, Predicate<String> growable, Predicate<String> seedReady,
                        Predicate<String> harvestWanted, boolean compostEnriches, Predicate<FarmFieldsState.Cell> skip,
                        ToDoubleFunction<FarmFieldsState.Cell> distance) {}

    /** The next thing to do, or null when the fields need nothing right now. */
    public static Job next(FarmFieldsState farm, Look look, Needs needs) {
        if (farm.carrying() && (!needs.onDuty() || farm.carried() >= needs.basket())) return new Job(Action.DELIVER, -1, null, null);
        if (!needs.onDuty()) return null;
        Job harvest = null, plant = null, water = null, compost = null;
        double harvestAt = 0, plantAt = 0, waterAt = 0, compostAt = 0;
        for (var claimed : farm.fields().entrySet()) for (var cell : claimed.getValue().cells()) {
            if (!cell.planned() || needs.skip().test(cell)) continue;
            var view = look.at(claimed.getKey(), cell);
            if (view == null || !view.workable()) continue;
            double far = needs.distance().applyAsDouble(cell);
            if (view.standing() != null) {
                if (view.ripe()) {
                    if (needs.harvestWanted().test(view.standing()) && (harvest == null || far < harvestAt)) {
                        harvest = new Job(Action.HARVEST, claimed.getKey(), cell, view.standing()); harvestAt = far;
                    }
                } else if (view.thirsty() && cell.soil() != FarmFieldsState.WORN && (water == null || far < waterAt)) {
                    water = new Job(Action.WATER, claimed.getKey(), cell, view.standing()); waterAt = far;
                }
            } else if (view.plantable() && needs.growable().test(cell.crop()) && needs.seedReady().test(cell.crop())
                && (plant == null || far < plantAt)) {
                plant = new Job(Action.PLANT, claimed.getKey(), cell, cell.crop()); plantAt = far;
            }
            // Compost goes to the most worn soil first, and to the nearest cell among equals.
            if (farm.compost() > 0 && FarmFieldsState.takesCompost(cell.soil(), needs.compostEnriches()) && (compost == null || cell.soil() < compost.cell().soil()
                || cell.soil() == compost.cell().soil() && far < compostAt)) {
                compost = new Job(Action.COMPOST, claimed.getKey(), cell, cell.crop()); compostAt = far;
            }
        }
        if (compost != null) return compost;
        if (harvest != null) return harvest;
        if (plant != null) return plant;
        if (water != null) return water;
        return farm.carrying() ? new Job(Action.DELIVER, -1, null, null) : null;
    }

    /** True when the next seed of a crop can be had: one in the seed store, or enough spare Life Essence to make one. */
    public static boolean seedReady(FarmFieldsState farm, String seed, int essenceCost, long spareEssence) {
        return seed != null && (farm.seeds(seed) > 0 || essenceCost > 0 && spareEssence >= essenceCost);
    }
    /**
     * Takes one seed for planting. A seed from the store is used first. With none there, she makes one from the
     * farm's Life Essence at the price of the game's own Farming Bench recipe, never touching essence that is
     * promised to the warehouse.
     *
     * @return "store", "essence", or null when no seed could be had
     */
    public static String takeSeed(PlotProductionState production, String seed, String essence, int essenceCost, long reservedEssence) {
        var farm = production.farm();
        if (seed == null) return null;
        if (farm.takeSeed(seed)) return "store";
        if (essenceCost < 1 || production.getAmount(essence) - Math.max(0, reservedEssence) < essenceCost) return null;
        return production.removeAmountUpTo(essence, essenceCost) == essenceCost ? "essence" : null;
    }

    /**
     * Seeds the plan still lacks, by crop: empty planned cells, less the seeds in the store, less what the farm's
     * spare Life Essence can make. Life Essence is shared, so the crops are served in the order given.
     *
     * @param seedOf the seed item of a crop item
     * @param essenceCost Life Essence for one seed of a crop item
     */
    public static Map<String, Integer> missingSeeds(FarmFieldsState farm, Look look, Predicate<String> growable,
            java.util.function.UnaryOperator<String> seedOf, ToIntFunction<String> essenceCost, long spareEssence) {
        var empty = new LinkedHashMap<String, Integer>();
        for (var claimed : farm.fields().entrySet()) for (var cell : claimed.getValue().cells()) {
            if (!cell.planned() || !growable.test(cell.crop())) continue;
            var view = look.at(claimed.getKey(), cell);
            if (view != null && view.workable() && view.standing() == null && view.plantable()) empty.merge(cell.crop(), 1, Integer::sum);
        }
        var missing = new LinkedHashMap<String, Integer>(); long essence = Math.max(0, spareEssence);
        for (var need : empty.entrySet()) {
            int lacking = Math.max(0, need.getValue() - farm.seeds(seedOf.apply(need.getKey())));
            int cost = essenceCost.applyAsInt(need.getKey());
            if (lacking > 0 && cost > 0) {
                int made = (int) Math.min(lacking, essence / cost);
                lacking -= made; essence -= (long) made * cost;
            }
            if (lacking > 0) missing.put(need.getKey(), lacking);
        }
        return missing;
    }
}
