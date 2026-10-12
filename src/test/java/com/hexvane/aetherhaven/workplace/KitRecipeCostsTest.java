package com.hexvane.aetherhaven.workplace;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** What profession kits and guard kits cost a town to make. */
@Tag("town")
class KitRecipeCostsTest {
    private static final Path RECIPES = Path.of("src/main/resources/Server/Aetherhaven/WorkplaceRecipes");
    private static final List<String> METALS = List.of("copper", "iron", "thorium", "cobalt", "adamantite");
    private static final List<String> CLASSES = List.of("knight", "archer", "rogue", "mage");

    private static Map<String, Integer> cost(String id) throws Exception {
        var recipe = new Gson().fromJson(Files.readString(RECIPES.resolve(id + ".json")), WorkplaceRecipe.class);
        assertTrue(recipe.valid(), id);
        var result = new LinkedHashMap<String, Integer>();
        for (var input : recipe.inputs()) result.merge(input.itemId() != null ? input.itemId() : input.resourceType(), input.count(), Integer::sum);
        return result;
    }

    @Test void everyKitCostsMultiplesOfFive() throws Exception {
        int kits = 0;
        try (var list = Files.list(RECIPES)) {
            for (Path file : list.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                var recipe = new Gson().fromJson(Files.readString(file), WorkplaceRecipe.class);
                if (recipe.output() == null || !recipe.output().endsWith("_Kit")) continue;
                kits++;
                for (var input : recipe.inputs())
                    assertTrue(input.count() >= 5 && input.count() % 5 == 0, recipe.id() + " asks for " + input.count());
            }
        }
        assertEquals(31, kits, "Twenty guard kits and eleven profession kits");
    }

    @Test void guardKitsCostAboutHalfAgainMoreAndEachMetalCostsMoreThanTheLast() throws Exception {
        int[] bars = {30, 35, 45, 60, 70}, leather = {15, 15, 15, 20, 25}, fibre = {15, 20, 30, 30, 35};
        int[] oldBars = {20, 25, 30, 40, 45};
        for (String guard : CLASSES) for (int tier = 0; tier < METALS.size(); tier++) {
            String metal = METALS.get(tier);
            String bar = "Ingredient_Bar_" + Character.toUpperCase(metal.charAt(0)) + metal.substring(1);
            assertEquals(Map.of(bar, bars[tier], "Ingredient_Leather_Light", leather[tier], "Ingredient_Fibre", fibre[tier]),
                cost("craft_" + metal + "_" + guard + "_kit"), metal + " " + guard);
            double rise = bars[tier] / (double) oldBars[tier];
            assertTrue(rise >= 1.4 && rise <= 1.6, metal + " bars rose by " + rise);
            if (tier > 0) {
                assertTrue(bars[tier] > bars[tier - 1]);
                assertTrue(leather[tier] >= leather[tier - 1] && fibre[tier] >= fibre[tier - 1]);
            }
        }
    }

    @Test void professionKitsCostMoreOfTheirMainMaterialAndBetterKitsStillCostMore() throws Exception {
        assertEquals(Map.of("Ingredient_Leather_Light", 15, "Ingredient_Bar_Copper", 5, "Ingredient_Fibre", 5), cost("craft_courier_kit"));
        assertEquals(Map.of("Ingredient_Leather_Light", 20, "Ingredient_Bar_Iron", 10, "Ingredient_Fibre", 15), cost("craft_quartermaster_pack"));
        assertEquals(Map.of("Ingredient_Bar_Iron", 10, "Ingredient_Leather_Light", 5, "Wood_Trunk", 5), cost("craft_smith_kit"));
        assertEquals(Map.of("Ingredient_Bar_Iron", 15, "Ingredient_Bar_Cobalt", 5, "Ingredient_Leather_Medium", 15), cost("craft_mastersmith_kit"));
        assertEquals(Map.of("Ingredient_Bar_Iron", 10, "Ingredient_Leather_Light", 5, "Ingredient_Fibre", 15), cost("craft_botanist_kit"));
        assertEquals(Map.of("Ingredient_Bar_Copper", 10, "Ingredient_Leather_Light", 5, "Potion_Empty_Small", 5), cost("craft_alchemist_kit"));
        assertEquals(Map.of("Ingredient_Bar_Iron", 10, "Ingredient_Leather_Light", 5, "Ingredient_Fabric_Scrap_Linen", 15), cost("craft_cook_kit"));
        assertEquals(Map.of("Ingredient_Bar_Silver", 10, "Ingredient_Leather_Light", 5, "Ingredient_Crystal_White", 5), cost("craft_jeweler_kit"));
        assertEquals(Map.of("Ingredient_Leather_Light", 15, "Ingredient_Bar_Iron", 5, "Ingredient_Fibre", 15), cost("craft_rancher_kit"));
        assertEquals(Map.of("Ingredient_Bar_Iron", 10, "Ingredient_Leather_Light", 5, "Wood_Beech_Trunk", 15), cost("craft_crafter_kit"));
        assertEquals(Map.of("Ingredient_Bar_Iron", 5, "Ingredient_Leather_Light", 15, "Ingredient_Fibre", 15), cost("craft_leatherworker_kit"));
        // The upgrade of a kit costs more than the kit it replaces.
        assertTrue(cost("craft_quartermaster_pack").get("Ingredient_Leather_Light") > cost("craft_courier_kit").get("Ingredient_Leather_Light"));
        assertTrue(cost("craft_mastersmith_kit").get("Ingredient_Bar_Iron") > cost("craft_smith_kit").get("Ingredient_Bar_Iron"));
    }
}
