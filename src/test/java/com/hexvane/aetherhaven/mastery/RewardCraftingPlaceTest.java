package com.hexvane.aetherhaven.mastery;

import com.hexvane.aetherhaven.workplace.WorkplaceRecipe;
import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class RewardCraftingPlaceTest {
    private static WorkplaceRecipe townRecipe(String id, String bench, String output) {
        return new WorkplaceRecipe(id, "Example kit", "crafting", "workplace:" + id, bench, 2, 4,
            List.of(new WorkplaceRecipe.Ingredient("Ingredient_Bar_Iron", null, 5)), output, 1, 30, true);
    }
    private static CraftingRecipe playerRecipe(String output, String... benches) {
        var requirements = new BenchRequirement[benches.length];
        for (int i = 0; i < benches.length; i++) {
            requirements[i] = new BenchRequirement(); requirements[i].id = benches[i]; requirements[i].type = BenchType.Crafting;
        }
        var result = new MaterialQuantity(output, null, null, 1, null);
        return new CraftingRecipe(new MaterialQuantity[]{new MaterialQuantity("Ingredient_Fibre", null, null, 3, null)},
            result, new MaterialQuantity[]{result}, 1, requirements, 4, true, 1);
    }

    @Test void aKitNamesTheBenchOfItsTownRecipeNotAFixedOne() {
        var kit = townRecipe("craft_cook_kit", "Workbench", "Example_Cook_Kit");
        assertEquals(new RewardCraftingPlace.Place("Workbench", true),
            RewardCraftingPlace.find("Example_Cook_Kit", List.of(kit), List.of()));
        // A pack that moves the recipe to another bench is followed.
        var moved = townRecipe("craft_cook_kit", "Cookingbench", "Example_Cook_Kit");
        assertEquals(new RewardCraftingPlace.Place("Cookingbench", true),
            RewardCraftingPlace.find("Example_Cook_Kit", List.of(moved), List.of()));
    }

    @Test void townCraftingWinsBecauseThatIsWhereTheRewardIsTaught() {
        var kit = townRecipe("craft_cook_kit", "Workbench", "Example_Cook_Kit");
        var place = RewardCraftingPlace.find("Example_Cook_Kit", List.of(kit), List.of(playerRecipe("Example_Cook_Kit", "Weapon_Bench")));
        assertEquals(new RewardCraftingPlace.Place("Workbench", true), place);
    }

    @Test void aRecipeThePlayerLearnsNamesItsOwnBench() {
        var recipes = List.of(playerRecipe("Other_Item", "Workbench"), playerRecipe("Example_Sprinkler", "Farmingbench"));
        assertEquals(new RewardCraftingPlace.Place("Farmingbench", false),
            RewardCraftingPlace.find("Example_Sprinkler", List.of(), recipes));
        // A workplace rule that only routes a native recipe does not make it town crafting.
        var routed = new WorkplaceRecipe("route", "Example", "crafting", "Example_Sprinkler_Recipe", "Workbench", 1, 1);
        assertEquals(new RewardCraftingPlace.Place("Farmingbench", false),
            RewardCraftingPlace.find("Example_Sprinkler", List.of(routed), recipes));
    }

    @Test void theSameBenchIsNamedWhateverOrderTheRecipesArriveIn() {
        var a = playerRecipe("Example_Item", "Weapon_Bench");
        var b = playerRecipe("Example_Item", "Armor_Bench", "Workbench");
        var expected = new RewardCraftingPlace.Place("Armor_Bench", false);
        assertEquals(expected, RewardCraftingPlace.find("Example_Item", List.of(), List.of(a, b)));
        assertEquals(expected, RewardCraftingPlace.find("Example_Item", List.of(), List.of(b, a)));
    }

    @Test void nothingIsShownWhenNoRecipeNamesABench() {
        assertNull(RewardCraftingPlace.find("Example_Item", List.of(), List.of()));
        assertNull(RewardCraftingPlace.find("Example_Item", List.of(), List.of(playerRecipe("Example_Item"))));
        assertNull(RewardCraftingPlace.find("", List.of(townRecipe("craft_kit", "Workbench", "")), List.of()));
        assertNull(RewardCraftingPlace.find(null, List.of(), List.of()));
    }

    /** The shipped data, so a kit added later without a town recipe is caught here and not by a player. */
    @Test void everyKitAVillagerTeachesSaysWhereItIsMade() throws Exception {
        var resources = java.nio.file.Path.of("src/main/resources/Server/Aetherhaven");
        var gson = new com.google.gson.Gson();
        var townRecipes = new ArrayList<WorkplaceRecipe>();
        try (var files = java.nio.file.Files.list(resources.resolve("WorkplaceRecipes"))) {
            for (var file : files.filter(f -> f.toString().endsWith(".json")).toList())
                townRecipes.add(gson.fromJson(java.nio.file.Files.readString(file), WorkplaceRecipe.class));
        }
        var kits = new TreeSet<String>();
        try (var files = java.nio.file.Files.list(resources.resolve("Villagers"))) {
            for (var file : files.filter(f -> f.toString().endsWith(".json")).toList())
                collectTaughtKits(com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(file)), kits);
        }
        assertFalse(kits.isEmpty(), "no villager teaches a kit recipe any more: this check reads the wrong field");
        for (String kit : kits) {
            var place = RewardCraftingPlace.find(kit, townRecipes, List.of());
            assertNotNull(place, kit + " is taught by a villager but no town recipe makes it");
            assertTrue(place.town(), kit);
            assertFalse(place.benchId().isBlank(), kit);
        }
    }
    private static void collectTaughtKits(com.google.gson.JsonElement element, Set<String> kits) {
        if (element.isJsonArray()) element.getAsJsonArray().forEach(child -> collectTaughtKits(child, kits));
        if (!element.isJsonObject()) return;
        for (var entry : element.getAsJsonObject().entrySet()) {
            var value = entry.getValue();
            if ("learnRecipeItemId".equals(entry.getKey()) && value.isJsonPrimitive() && value.getAsString().endsWith("_Kit"))
                kits.add(value.getAsString());
            else collectTaughtKits(value, kits);
        }
    }

    @Test void aBenchSharedByTwoBlocksIsShownByTheFirstItemName() {
        var benches = new HashMap<String, String>();
        benches.put("Bench_Trough", "Farmingbench");
        benches.put("Bench_Farming", "Farmingbench");
        benches.put("Bench_WorkBench", "Workbench");
        benches.put("Broken_Bench", " ");
        assertEquals(Map.of("Farmingbench", "Bench_Farming", "Workbench", "Bench_WorkBench"),
            RewardCraftingPlace.itemPerBench(benches));
    }
}
