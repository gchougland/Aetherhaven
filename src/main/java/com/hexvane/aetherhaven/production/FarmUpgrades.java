package com.hexvane.aetherhaven.production;

import java.util.LinkedHashMap;
import java.util.Map;

/** The farm's own upgrades, paid with Life Essence and the crops the Farmer grows. Levels live in the fields state. */
public final class FarmUpgrades {
    private FarmUpgrades() {}
    /** More Scarecrows, wider reach, better watering, seed saving, richer compost, Almanac insight. */
    public enum Track { SCARECROWS, REACH, WATERING, SEEDS, COMPOST, ALMANAC }

    public static int maxLevel(Track track) { return FarmFieldRules.get().track(track).levels().size(); }
    public static int level(PlotProductionState production, Track track) {
        return Math.min(maxLevel(track), production.farm().level(track.name()));
    }
    /** What a track gives at a level. */
    public static double value(Track track, int level) {
        var rules = FarmFieldRules.get().track(track);
        return level <= 0 ? rules.base() : rules.levels().get(Math.min(level, rules.levels().size()) - 1).value();
    }
    private static double value(PlotProductionState production, Track track) { return value(track, level(production, track)); }

    /** Scarecrow fields the Farmer tends beside the farm's own field. */
    public static int scarecrows(PlotProductionState production) {
        return (int) Math.clamp(Math.round(value(production, Track.SCARECROWS)), 0, FarmFieldsState.MAX_FIELDS - 1);
    }
    /** Blocks a Scarecrow's field reaches from the Scarecrow, in every direction. */
    public static int reach(PlotProductionState production) {
        return (int) Math.clamp(Math.round(value(production, Track.REACH)), 1, 7);
    }
    /** Cells around the one she waters that get wet with it: 0 waters one cell, 1 a square of nine. */
    public static int waterRadius(PlotProductionState production) {
        return (int) Math.clamp(Math.round(value(production, Track.WATERING)), 0, 3);
    }
    /** Chance that a harvest gives its seed back. */
    public static double seedChance(PlotProductionState production) {
        return Math.clamp(value(production, Track.SEEDS), 0, 1);
    }
    /** Spare crops that make one compost. */
    public static int cropsPerCompost(PlotProductionState production) {
        return (int) Math.clamp(Math.round(value(production, Track.COMPOST)), 1, 64);
    }
    /**
     * True once compost also lifts Good soil to Rich. Before that it only mends Worn soil, so Rich soil has to be
     * earned with a change of crop.
     */
    public static boolean compostEnriches(PlotProductionState production) {
        return level(production, Track.COMPOST) >= FarmFieldRules.get().compost().enrichLevel();
    }
    /** Crops the Almanac favours each week. */
    public static int favouredCrops(PlotProductionState production) {
        return (int) Math.clamp(Math.round(value(production, Track.ALMANAC)), 1, 6);
    }
    /** Weeks of the Almanac this farm can read, this week included. */
    public static int weeksShown(PlotProductionState production) {
        return FarmFieldRules.get().weeksShown(level(production, Track.ALMANAC));
    }

    /**
     * How much of one item the farm keeps. Crops and Life Essence follow the farm's catalog line, widened by any
     * storage upgrade bought before the farm had fields. A Golden Harvest has its own small shelf.
     */
    public static long capacity(PlotProductionState production, ProductionCatalog.Entry entry, String item) {
        var rules = FarmFieldRules.get();
        if (rules.golden().item().equals(item)) return rules.golden().keep();
        long scaled = (long) Math.ceil(entry.maxStorageForItem(item) * WorkplaceProductionUpgrades.capacityMultiplier(production));
        return Math.max(1L, Math.min(ProductionCatalog.MAX_STORAGE_PER_OUTPUT, scaled));
    }

    /** The cost of buying a level, 1 based, scaled by the town's upgrade cost setting. */
    public static Map<String, Integer> cost(Track track, int level, double resourceMultiplier) {
        var levels = FarmFieldRules.get().track(track).levels();
        var result = new LinkedHashMap<String, Integer>();
        if (level < 1 || level > levels.size()) return result;
        for (var part : levels.get(level - 1).cost().entrySet())
            result.put(part.getKey(), com.hexvane.aetherhaven.difficulty.BuildingUpgradeCostScaler.scaleResourceCount(part.getValue(), resourceMultiplier));
        return result;
    }

    /**
     * Buys exactly the next level of one track. Nothing is taken unless the whole cost is there.
     *
     * @return null on success, otherwise the short name of what stopped it
     */
    public static String purchase(PlotProductionState production, Track track, int level, double resourceMultiplier, GardenUpgrades.Purse purse) {
        if (level(production, track) >= maxLevel(track)) return "maxed";
        if (level != level(production, track) + 1) return "order";
        var cost = cost(track, level, resourceMultiplier);
        for (var part : cost.entrySet()) if (purse.available(part.getKey()) < part.getValue()) return "short";
        if (!purse.take(cost)) return "short";
        production.farm().setLevel(track.name(), level);
        return null;
    }

    /**
     * Brings a farm from before the Scarecrow Fields across, once. Beds it already tended become the plan of its
     * own field. Output slots it bought become Scarecrows and then reach, because a crop plan needs no slots.
     * Speed and storage bought with ingots keep working exactly as they did. A farm that never had seeds is given
     * a first handful, so a first plan can be planted at once. Nothing is ever lowered or taken away.
     */
    public static boolean mapLegacy(PlotProductionState production) {
        var farm = production.farm();
        if (farm.legacyMapped()) return false;
        production.migrateIfNeeded();
        farm.home();
        int slots = production.getIronUpgrade() + production.getCobaltLevel();
        raise(farm, Track.SCARECROWS, slots);
        raise(farm, Track.REACH, slots - maxLevel(Track.SCARECROWS));
        if (!farm.starterGiven()) {
            var seeds = FarmFieldRules.get().seeds();
            if (seeds.starter() != null) for (var part : seeds.starter().entrySet()) farm.addSeeds(part.getKey(), part.getValue(), seeds.keep());
            farm.markStarterGiven();
        }
        farm.markLegacyMapped();
        return true;
    }
    private static void raise(FarmFieldsState farm, Track track, int level) {
        int capped = Math.min(maxLevel(track), level);
        if (capped > farm.level(track.name())) farm.setLevel(track.name(), capped);
    }
}
