package com.hexvane.aetherhaven.mastery;

import com.hexvane.aetherhaven.workplace.WorkplaceRecipe;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import java.util.*;

/**
 * Where a learned recipe is made, read from the recipe itself: the bench is never a fixed name here, so a pack that
 * moves a recipe to another bench is followed without a code change.
 */
final class RewardCraftingPlace {
    private RewardCraftingPlace() {}

    /** {@code town}: made by townsfolk in a workplace, never at the player's own bench. */
    record Place(String benchId, boolean town) {}

    /**
     * Follows the order used when the reward is claimed: an output of town crafting is taught to the town, and
     * anything else to the player. Returns null when no recipe names a bench.
     */
    static Place find(String itemId, Collection<WorkplaceRecipe> townRecipes, Collection<CraftingRecipe> playerRecipes) {
        if (itemId == null || itemId.isBlank()) return null;
        String town = null;
        for (var recipe : townRecipes) {
            if (recipe == null || !recipe.townOnly() || !itemId.equals(recipe.output())) continue;
            town = first(town, recipe.bench());
        }
        if (town != null) return new Place(town, true);
        String own = null;
        for (var recipe : playerRecipes) {
            if (recipe == null || recipe.getBenchRequirement() == null) continue;
            var output = recipe.getPrimaryOutput();
            if (output == null || !itemId.equals(output.getItemId())) continue;
            for (var bench : recipe.getBenchRequirement()) if (bench != null) own = first(own, bench.id);
        }
        return own == null ? null : new Place(own, false);
    }

    /** Asset maps have no order, so the same bench is named every time the page is drawn. */
    private static String first(String chosen, String candidate) {
        if (candidate == null || candidate.isBlank()) return chosen;
        return chosen == null || candidate.compareTo(chosen) < 0 ? candidate : chosen;
    }

    /**
     * The item that stands for each bench id, so the bench can be shown by its own translated name. Several blocks may
     * share one id (the Farmer's Workbench and the Trough do): the first item id by name stands for it.
     */
    static Map<String, String> itemPerBench(Map<String, String> benchIdByItemId) {
        var result = new HashMap<String, String>();
        for (var entry : benchIdByItemId.entrySet()) {
            String item = entry.getKey(), bench = entry.getValue();
            if (item == null || item.isBlank() || bench == null || bench.isBlank()) continue;
            result.merge(bench, item, (a, b) -> a.compareTo(b) <= 0 ? a : b);
        }
        return result;
    }

    /** Every placeable bench in the loaded assets, base game and packs alike. */
    static Map<String, String> loadedBenchItems() {
        var benchByItem = new HashMap<String, String>();
        for (var item : Item.getAssetMap().getAssetMap().values()) {
            if (item == null || !item.hasBlockType()) continue;
            var block = BlockType.getAssetMap().getAsset(item.getBlockId());
            var bench = block == null ? null : block.getBench();
            if (bench != null && bench.getId() != null) benchByItem.put(item.getId(), bench.getId());
        }
        return itemPerBench(benchByItem);
    }
}
