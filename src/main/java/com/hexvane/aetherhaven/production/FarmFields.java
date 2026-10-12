package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.mastery.CoreProductionRequirements;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The Farmer's Scarecrow Fields. A Scarecrow claims the ground around it as one field with its own crop plan, and
 * the farm building has a field of its own. Plans, soil and stores live in {@link FarmFieldsState}, in the town save.
 */
public final class FarmFields {
    private FarmFields() {}
    public static final String CONSTRUCTION = AetherhavenConstants.CONSTRUCTION_PLOT_FARM;
    public static final String KIND = "farmer";
    public static final String BLOCK = "Aetherhaven_Scarecrow";
    public static final String PAGE = "AetherhavenScarecrow";
    public static final String LANG = "aetherhaven_fields.";
    /** The widest field the map can draw. A farm building larger than this shows the middle of its ground. */
    public static final int MAX_SPAN = 24;
    /** How far below and above a Scarecrow's foot the ground of its field may lie. */
    public static final int GROUND_BELOW = 3, GROUND_ABOVE = 1;

    /** The ground a field covers: columns from west to east and north to south, and the heights searched for soil. */
    public record Bounds(int minX, int minZ, int maxX, int maxZ, int minY, int maxY) {
        public int width() { return maxX - minX + 1; }
        public int depth() { return maxZ - minZ + 1; }
        public boolean contains(int x, int z) { return x >= minX && x <= maxX && z >= minZ && z <= maxZ; }
    }

    public static boolean is(String blockId) { return BLOCK.equals(blockId); }

    public static boolean isPlot(PlotInstance plot) {
        var plugin = AetherhavenPlugin.get();
        return plot != null && (CONSTRUCTION.equals(plot.getConstructionId()) || plugin != null
            && plugin.getConstructionCatalog().matchesGameplayConstruction(plot.getConstructionId(), CONSTRUCTION));
    }

    /** A farm's production, with anything from before the Scarecrow Fields carried across once. */
    public static PlotProductionState production(TownRecord town, PlotInstance plot) {
        var production = town.getOrCreatePlotProduction(plot.getPlotId());
        FarmUpgrades.mapLegacy(production);
        // Beds adopted the old way, before or after this farm first met the Scarecrow Fields, always reach the plan.
        production.farm().adoptLegacy(production.peekField());
        return production;
    }

    /** The ground of the farm building's own field: its plot, or the middle of a very large one. */
    public static Bounds homeBounds(PlotInstance plot) {
        var f = plot.toFootprint();
        int minX = f.getMinX(), maxX = f.getMaxX(), minZ = f.getMinZ(), maxZ = f.getMaxZ();
        if (maxX - minX + 1 > MAX_SPAN) { minX = f.horizontalCenterX() - MAX_SPAN / 2; maxX = minX + MAX_SPAN - 1; }
        if (maxZ - minZ + 1 > MAX_SPAN) { minZ = f.horizontalCenterZ() - MAX_SPAN / 2; maxZ = minZ + MAX_SPAN - 1; }
        return new Bounds(minX, minZ, maxX, maxZ, f.getMinY(), Math.max(f.getMinY(), f.getMaxY() - 1));
    }
    /** The ground a Scarecrow claims: a square around it. Its soil lies about level with the Scarecrow's foot. */
    public static Bounds scarecrowBounds(Point anchor, int reach) {
        int r = Math.clamp(reach, 1, (MAX_SPAN - 1) / 2);
        return new Bounds(anchor.x() - r, anchor.z() - r, anchor.x() + r, anchor.z() + r, anchor.y() - GROUND_BELOW, anchor.y() + GROUND_ABOVE - 1);
    }
    public static Bounds bounds(PlotInstance plot, PlotProductionState production, int slot) {
        if (slot == FarmFieldsState.HOME) return homeBounds(plot);
        var field = production.farm().field(slot);
        return field == null || field.anchor() == null ? null : scarecrowBounds(field.anchor(), FarmUpgrades.reach(production));
    }

    /** Mastery the Farmer needs for a crop: the level of the game's own Farming Bench that makes its seed. */
    public static int mastery(String crop) {
        return CoreProductionRequirements.requirement(KIND, crop).mastery();
    }
    /**
     * Life Essence the game's own Farming Bench asks for one seed. The Farmer pays the same when she has to make a
     * seed herself. A seed with no such recipe costs the fallback in the rules.
     */
    public static int seedCost(String seed) {
        var rules = FarmFieldRules.get().seeds();
        var item = seed == null ? null : com.hypixel.hytale.server.core.asset.type.item.config.Item.getAssetMap().getAsset(seed);
        if (item == null) return rules.fallbackEssenceCost();
        var recipes = new ArrayList<com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe>();
        item.collectRecipesToGenerate(recipes);
        int best = Integer.MAX_VALUE;
        for (var recipe : recipes) {
            if (recipe.getInput() == null) continue;
            int essence = 0;
            for (var input : recipe.getInput()) if (rules.essence().equals(input.getItemId())) essence += input.getQuantity();
            int made = recipe.getPrimaryOutput() == null ? 1 : Math.max(1, recipe.getPrimaryOutput().getQuantity());
            if (essence > 0) best = Math.min(best, (essence + made - 1) / made);
        }
        return best == Integer.MAX_VALUE ? rules.fallbackEssenceCost() : best;
    }
    /** Every crop the Farmer knows of, in their usual order: by the Mastery they need, and within that as the game lists them. */
    public static List<String> cropOrder() {
        var crops = new ArrayList<String>();
        for (String crop : NativeFarmCrops.outputsInOrder()) if (NativeFarmCrops.seedFor(crop) != null) crops.add(crop);
        // A stable sort keeps the listed order inside each Mastery.
        crops.sort(Comparator.comparingInt(FarmFields::mastery));
        return crops;
    }
    /** What stops this farm growing a crop: "locked" until it is unlocked in farm storage, "mastery" until she is trained, or null. */
    public static String cropIssue(TownRecord town, ProductionCatalog.Entry entry, String crop) {
        if (NativeFarmCrops.byOutput(crop) == null || NativeFarmCrops.seedFor(crop) == null) return "unknown";
        if (entry == null || !entry.containsItemId(crop)) return "locked";
        return CoreProductionRequirements.issueForKind(town, KIND, crop) == null ? null : "mastery";
    }
    /** The crops this farm's Farmer can grow today, in their usual order. */
    public static List<String> growable(TownRecord town, ProductionCatalog.Entry entry) {
        return cropOrder().stream().filter(crop -> cropIssue(town, entry, crop) == null).toList();
    }
    /** The crops the Almanac favours for this farm in a week. */
    public static List<String> favoured(TownRecord town, ProductionCatalog.Entry entry, PlotProductionState production, long week) {
        return FarmAlmanac.favoured(week, growable(town, entry), FarmUpgrades.favouredCrops(production));
    }
}
