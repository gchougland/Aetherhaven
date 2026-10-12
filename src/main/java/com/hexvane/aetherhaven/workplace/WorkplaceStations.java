package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hypixel.hytale.builtin.crafting.component.BenchBlock;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which item a player sets down to give a workplace type its station, and where that item is made. Both are read from
 * the loaded items and recipes, never from a list kept here, so a pack that adds a bench or moves a recipe is followed.
 */
public final class WorkplaceStations {
    private WorkplaceStations() {}

    /** The bench id recipes use for crafting in the player's own inventory. */
    public static final String POCKET = "Fieldcraft";

    /** An item that places a block, as the search needs it. */
    public record Placeable(String itemId, String benchId, String benchBaseBlock, String baseBlock) {
        WorkplaceQuestSteps.Standing standing() { return new WorkplaceQuestSteps.Standing(benchId, benchBaseBlock, baseBlock); }
    }

    /** @param tier the lowest tier of that bench the recipe accepts, 1 when it does not care */
    public record CraftedAt(String benchId, int tier) {
        public boolean pocket() { return POCKET.equals(benchId); }
    }

    /**
     * @param itemId          the item to set down
     * @param craftedAt       where its recipe is made, or null when no recipe names a place
     * @param craftedAtItemId the item of that bench, for its name, or null
     */
    public record Station(String itemId, CraftedAt craftedAt, String craftedAtItemId) {}

    /**
     * The item that stands for a type's station. A grower site with no bench is named by its first bed. When several
     * items place a fitting block, the first by id is used, so the same one is named every time.
     */
    static String item(TownsfolkWorkType type, GrowerPlan plan, Collection<Placeable> placeables) {
        if (type == null) return null;
        var fitting = new ArrayList<Placeable>();
        for (var placeable : placeables) if (WorkplaceQuestSteps.isStation(type, plan, placeable.standing())) fitting.add(placeable);
        if (fitting.isEmpty()) return null;
        boolean beds = plan != null && plan.bench() == null;
        fitting.sort(Comparator.<Placeable>comparingInt(p -> beds ? plan.bedBlocks().indexOf(p.baseBlock()) : 0).thenComparing(Placeable::itemId));
        return fitting.getFirst().itemId();
    }

    /**
     * Where an item is made. The player's own inventory comes first, because it needs nothing built. After that the
     * bench that asks for the lowest tier, and between equals the first by id.
     */
    static CraftedAt craftedAt(String itemId, Collection<CraftingRecipe> recipes) {
        if (itemId == null) return null;
        CraftedAt best = null;
        for (var recipe : recipes) {
            if (recipe == null || recipe.getBenchRequirement() == null) continue;
            var output = recipe.getPrimaryOutput();
            if (output == null || !itemId.equals(output.getItemId())) continue;
            for (var bench : recipe.getBenchRequirement()) {
                if (bench == null || bench.id == null || bench.id.isBlank()) continue;
                var place = new CraftedAt(bench.id, Math.max(1, bench.requiredTierLevel));
                if (best == null || ORDER.compare(place, best) < 0) best = place;
            }
        }
        return best;
    }

    private static final Comparator<CraftedAt> ORDER = Comparator.<CraftedAt>comparingInt(place -> place.pocket() ? 0 : 1)
        .thenComparingInt(CraftedAt::tier).thenComparing(CraftedAt::benchId);

    /** The item that places a bench, by the bench's id. Several may share an id: the first by item id is used. */
    static String benchItem(String benchId, Collection<Placeable> placeables) {
        String chosen = null;
        for (var placeable : placeables)
            if (benchId != null && benchId.equals(placeable.benchId()) && (chosen == null || placeable.itemId().compareTo(chosen) < 0)) chosen = placeable.itemId();
        return chosen;
    }

    static Station station(TownsfolkWorkType type, GrowerPlan plan, Collection<Placeable> placeables, Collection<CraftingRecipe> recipes) {
        String item = item(type, plan, placeables);
        if (item == null) return null;
        var place = craftedAt(item, recipes);
        return new Station(item, place, place == null || place.pocket() ? null : benchItem(place.benchId(), placeables));
    }

    private static final Map<TownsfolkWorkType, Station> FOUND = new ConcurrentHashMap<>();

    /** Every item in the loaded assets that places a block. */
    static List<Placeable> loadedPlaceables() {
        var placeables = new ArrayList<Placeable>();
        for (var item : Item.getAssetMap().getAssetMap().values()) {
            if (item == null || !item.hasBlockType()) continue;
            var block = BlockType.getAssetMap().getAsset(item.getBlockId());
            if (block == null) continue;
            String bench = WorkplaceEquipment.benchId(block);
            placeables.add(new Placeable(item.getId(), bench, bench == null ? null : BenchBlock.getBaseBlockType(block).getId(), GrowerSiteService.baseId(block)));
        }
        return placeables;
    }

    /** A miss is only believed for this long, in case it was asked before the assets were ready. */
    private static final long MISS_MILLIS = 30_000;
    private static final Map<TownsfolkWorkType, Long> MISSED = new ConcurrentHashMap<>();

    /**
     * The station of a type in the running game, or null when no loaded item places one. Found once and remembered,
     * because a quest line asks for it every time the HUD is drawn.
     */
    public static Station find(TownsfolkWorkType type) {
        if (type == null) return null;
        var known = FOUND.get(type);
        if (known != null) return known;
        long now = System.currentTimeMillis();
        Long missed = MISSED.get(type);
        if (missed != null && now - missed < MISS_MILLIS) return null;
        var found = station(type, GrowerSiteService.plan(type), loadedPlaceables(), CraftingRecipe.getAssetMap().getAssetMap().values());
        if (found != null) FOUND.put(type, found);
        else MISSED.put(type, now);
        return found;
    }

    /** Forgets what was found, for a reload of the assets. */
    public static void reset() { FOUND.clear(); MISSED.clear(); }
}
