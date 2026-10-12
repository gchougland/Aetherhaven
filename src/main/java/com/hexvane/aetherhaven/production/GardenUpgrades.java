package com.hexvane.aetherhaven.production;

import java.util.LinkedHashMap;
import java.util.Map;

/** The Conservatory's own upgrades, paid with Life Essence and the plants Rowan grows. Levels live in the garden state. */
public final class GardenUpgrades {
    private GardenUpgrades() {}
    /** More planters, a stronger rite, a richer harvest, wider stores. */
    public enum Track { PLANTERS, RITE, BOUNTY, STORES }
    /** Where a payment comes from. Taking is all or nothing. */
    public interface Purse {
        long available(String item);
        boolean take(Map<String, Integer> cost);
    }

    public static int maxLevel(Track track) { return SpiritGardenRules.get().track(track).levels().size(); }
    public static int level(PlotProductionState production, Track track) {
        return Math.min(maxLevel(track), production.garden().level(track.name()));
    }
    /** What a track gives at a level: planters, hours of rest between rites, plants in a harvest, or a store multiplier. */
    public static double value(Track track, int level) {
        var rules = SpiritGardenRules.get().track(track);
        return level <= 0 ? rules.base() : rules.levels().get(Math.min(level, rules.levels().size()) - 1).value();
    }
    public static int planters(PlotProductionState production) {
        return (int) Math.clamp(Math.round(value(Track.PLANTERS, level(production, Track.PLANTERS))), 1, SpiritGardenState.MAX_PLANTERS);
    }
    /** Game seconds a plant rests between rites. */
    public static long restSeconds(PlotProductionState production) {
        return Math.max(60, Math.round(value(Track.RITE, level(production, Track.RITE)) * 3600));
    }
    public static int plantsPerHarvest(PlotProductionState production) {
        return (int) Math.clamp(Math.round(value(Track.BOUNTY, level(production, Track.BOUNTY))), 1, 64);
    }
    /** Petals come from the plant's own drop list, rolled this many times. */
    public static int petalRolls(PlotProductionState production) {
        return plantsPerHarvest(production) * Math.max(0, SpiritGardenRules.get().track(Track.BOUNTY).petalRollsPerPlant());
    }
    public static double storeMultiplier(PlotProductionState production) {
        return Math.max(1, value(Track.STORES, level(production, Track.STORES)));
    }
    /** Room for one item in the Conservatory: its catalog line, widened by the stores track. */
    public static long capacity(PlotProductionState production, ProductionCatalog.Entry entry, String item) {
        long scaled = (long) Math.ceil(entry.maxStorageForItem(item) * storeMultiplier(production));
        return Math.max(1L, Math.min(ProductionCatalog.MAX_STORAGE_PER_OUTPUT, scaled));
    }

    /** The cost of buying a level, 1 based, scaled by the town's upgrade cost setting. */
    public static Map<String, Integer> cost(Track track, int level, double resourceMultiplier) {
        var levels = SpiritGardenRules.get().track(track).levels();
        var result = new LinkedHashMap<String, Integer>();
        if (level < 1 || level > levels.size()) return result;
        for (var part : levels.get(level - 1).cost().entrySet())
            result.put(part.getKey(), com.hexvane.aetherhaven.difficulty.BuildingUpgradeCostScaler.scaleResourceCount(part.getValue(), resourceMultiplier));
        return result;
    }

    /**
     * How much of a cost the Conservatory's own stores pay. The player's pack pays first, the stores cover what the
     * pack is short of, and only what is still missing is asked of the town warehouse.
     */
    public static Map<String, Long> fromStores(Map<String, Integer> cost, java.util.function.ToLongFunction<String> carried,
            java.util.function.ToLongFunction<String> spare) {
        var result = new LinkedHashMap<String, Long>();
        for (var part : cost.entrySet()) {
            long missing = part.getValue() - Math.min(part.getValue(), Math.max(0, carried.applyAsLong(part.getKey())));
            long stored = Math.min(missing, Math.max(0, spare.applyAsLong(part.getKey())));
            if (stored > 0) result.put(part.getKey(), stored);
        }
        return result;
    }

    /**
     * Buys exactly the next level of one track. Nothing is taken unless the whole cost is there.
     *
     * @return null on success, otherwise the short name of what stopped it
     */
    public static String purchase(PlotProductionState production, Track track, int level, double resourceMultiplier, Purse purse) {
        if (level(production, track) >= maxLevel(track)) return "maxed";
        if (level != level(production, track) + 1) return "order";
        var cost = cost(track, level, resourceMultiplier);
        for (var part : cost.entrySet()) if (purse.available(part.getKey()) < part.getValue()) return "short";
        if (!purse.take(cost)) return "short";
        production.garden().setLevel(track.name(), level);
        return null;
    }

    /**
     * A Conservatory upgraded under the old ingot tree keeps what it paid for: speed becomes the rite, storage
     * becomes the stores, and output slots become planters, with any slot beyond the last planter level becoming
     * a richer harvest. Runs once for each Conservatory and never lowers a level.
     */
    public static boolean mapLegacy(PlotProductionState production) {
        var garden = production.garden();
        if (garden.legacyMapped()) return false;
        production.migrateIfNeeded();
        int slots = production.getIronUpgrade() + production.getCobaltLevel();
        raise(garden, Track.RITE, production.getThoriumLevel());
        raise(garden, Track.STORES, production.getAdamantineLevel());
        raise(garden, Track.PLANTERS, slots);
        raise(garden, Track.BOUNTY, slots - maxLevel(Track.PLANTERS));
        garden.markLegacyMapped(); garden.adopt();
        return true;
    }
    private static void raise(SpiritGardenState garden, Track track, int level) {
        int capped = Math.min(maxLevel(track), level);
        if (capped > garden.level(track.name())) garden.setLevel(track.name(), capped);
    }
}
