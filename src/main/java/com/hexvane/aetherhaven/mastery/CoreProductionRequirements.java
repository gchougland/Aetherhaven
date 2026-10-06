package com.hexvane.aetherhaven.mastery;

import com.hexvane.aetherhaven.production.NativeTimberGrowth;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.workplace.AlchemicalCrop;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import java.util.*;

/** Checks starting new production only. Earned stock and saved cargo remain collectable. */
public final class CoreProductionRequirements {
    private CoreProductionRequirements() {}
    public record Requirement(int mastery, int toolTier) {}
    public static String kind(String construction) {
        if (construction == null) return null;
        return switch (construction) {
            case "plot_miners_hut" -> "miner";
            case "plot_lumbermill" -> "logger";
            case "plot_farm" -> "farmer";
            case "plot_barn" -> "rancher";
            case "plot_druid" -> "druid";
            default -> null;
        };
    }
    public static Requirement ore(String item) {
        return switch (item == null ? "" : item) {
            case "Ore_Iron" -> new Requirement(3, 1);
            case "Ore_Thorium" -> new Requirement(5, 2);
            case "Ore_Cobalt" -> new Requirement(6, 2);
            case "Ore_Adamantite" -> new Requirement(8, 3);
            case "Ore_Mithril" -> new Requirement(10, 4);
            case "Ore_Silver" -> new Requirement(4, 1);
            case "Ore_Gold" -> new Requirement(7, 2);
            default -> new Requirement(1, 0);
        };
    }
    /** Native sapling bench levels are used as authored, including zero for starter saplings. */
    public static Requirement wood(int benchTier) {
        int tier = Math.clamp(benchTier, 0, 10);
        return new Requirement(Math.max(1, tier), tier >= 7 ? 4 : tier >= 5 ? 3 : tier >= 4 ? 2 : tier >= 3 ? 1 : 0);
    }
    public static int nativeFarmingTier(String itemId, int fallback) {
        var item = itemId == null ? null : Item.getAssetMap().getAsset(itemId);
        if (item == null) return fallback;
        var recipes = new ArrayList<CraftingRecipe>();
        item.collectRecipesToGenerate(recipes);
        int tier = Integer.MAX_VALUE;
        for (var recipe : recipes) if (recipe.getBenchRequirement() != null)
            for (var bench : recipe.getBenchRequirement()) if ("Farmingbench".equals(bench.id))
                tier = Math.min(tier, bench.requiredTierLevel);
        return tier == Integer.MAX_VALUE ? fallback : Math.max(0, tier);
    }
    public static Requirement requirement(String kind, String item) {
        if ("miner".equals(kind)) return ore(item);
        if ("logger".equals(kind)) {
            if (com.hexvane.aetherhaven.production.TimberCharring.OUTPUT.equals(item)) return wood(4);
            var species = NativeTimberGrowth.species(item);
            // Add-on species with no authored sapling recipe require full training and the best ordinary tool.
            return species == null ? new Requirement(1, 0) : wood(nativeFarmingTier(species.sapling(), 10));
        }
        if ("druid".equals(kind)) {
            var crop = AlchemicalCrop.fromBlock(item);
            return new Requirement(crop == null ? 1 : crop.skill(), 0);
        }
        if ("farmer".equals(kind) && item != null && item.startsWith("Plant_Crop_") && item.endsWith("_Item")) {
            String seed = "Plant_Seeds_" + item.substring("Plant_Crop_".length(), item.length() - "_Item".length());
            return new Requirement(Math.clamp(nativeFarmingTier(seed, 1), 1, 10), 0);
        }
        if ("rancher".equals(kind)) return new Requirement(switch (item == null ? "" : item) {
            case "Ingredient_Hide_Medium", "Ingredient_Feathers_Red" -> 4;
            case "Ingredient_Hide_Heavy" -> 7;
            default -> 1;
        }, 0);
        return new Requirement(1, 0);
    }
    public static String issue(TownRecord town, String construction, String item) {
        String kind = kind(construction);
        return kind == null ? null : issueForKind(town, kind, item);
    }
    public static String issueForKind(TownRecord town, String kind, String item) {
        var need = requirement(kind, item);
        if (CoreVillagerMasteryService.level(town, kind) < need.mastery()) return "Requires Mastery " + need.mastery() + ".";
        if (CoreVillagerMasteryService.toolFamily(kind) != null) {
            var saved = town.getCoreVillagerMastery().get(kind);
            if (saved != null && saved.tool() != null && saved.tool().isBroken()) return "Repair the equipped tool.";
            if (CoreVillagerMasteryService.toolTier(kind, CoreVillagerMasteryService.toolId(town, kind)) < need.toolTier())
                return "Requires " + toolLabel(need.toolTier()) + ("miner".equals(kind) ? " Pickaxe." : " Hatchet.");
        }
        return null;
    }
    public static String toolLabel(int tier) {
        return switch (tier) { case 0 -> "Crude"; case 1 -> "Copper"; case 2 -> "Iron"; case 3 -> "Thorium or Cobalt"; case 4 -> "Adamantite"; default -> "Mithril"; };
    }
}
