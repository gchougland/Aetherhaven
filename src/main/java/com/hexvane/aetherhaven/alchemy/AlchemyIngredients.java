package com.hexvane.aetherhaven.alchemy;

import java.util.*;

/** Uses existing native plants only. Stronger ingredients replace weaker effects of the same family. */
public final class AlchemyIngredients {
    private AlchemyIngredients() {}
    public record Ingredient(String item, String name, String family, int strength, String effect, String description, String preparation) {}
    private static final List<Ingredient> ALL = create();
    private static List<Ingredient> create() {
        var result = new ArrayList<Ingredient>();
        String[][] names = {{"Blood Rose", "Bloodcap Mushroom", "Blood Leaf"}, {"Azure Fern", "Azurecap Mushroom", "Azure Kelp"}, {"Storm Thistle", "Stormcap Mushroom", "Storm Rush"}};
        String[] families = {"Health", "Mana", "Stamina"};
        String[] verbs = {"Grind", "Stir", "Simmer"};
        int[] amounts = {15, 30, 50};
        for (int f = 0; f < families.length; f++) for (int tier = 1; tier <= 3; tier++) {
            String family = families[f];
            result.add(new Ingredient("Plant_Crop_" + family + tier, names[f][tier-1], family, tier,
                "Aetherhaven_Alchemy_" + family + tier, "Restores " + amounts[tier-1] + "% " + family.toLowerCase(Locale.ROOT), verbs[f]));
        }
        result.add(mushroom("Boomshroom_Small", "Boomshroom", "BlastWard", 1, "Explosion immunity for 60 seconds", "Simmer"));
        result.add(mushroom("Boomshroom_Large", "Large Boomshroom", "BlastWard", 1, "Explosion immunity for 60 seconds", "Simmer"));
        result.add(mushroom("Common_Lime", "Puffy Green Common Mushroom", "PoisonWard", 1, "Poison immunity for 60 seconds", "Grind"));
        result.add(mushroom("Cap_Red", "Red Cap Mushroom", "HealthBoost", 1, "Health Boost 1: +15 maximum health for 120 seconds", "Grind"));
        result.add(mushroom("Glowing_Red", "Red Glowing Mushroom", "HealthBoost", 2, "Health Boost 2: +20 maximum health for 240 seconds", "Grind"));
        result.add(mushroom("Common_Blue", "Blue Common Mushroom", "ManaBoost", 1, "Mana Boost 1: +15 maximum mana for 120 seconds", "Stir"));
        result.add(mushroom("Glowing_Blue", "Blue Glowing Mushroom", "ManaBoost", 2, "Mana Boost 2: +20 maximum mana for 240 seconds", "Stir"));
        return List.copyOf(result);
    }
    private static Ingredient mushroom(String suffix, String name, String family, int strength, String description, String preparation) {
        return new Ingredient("Plant_Crop_Mushroom_" + suffix, name, family, strength,
            "Aetherhaven_Alchemy_" + family + strength, description, preparation);
    }
    public static List<Ingredient> all() { return ALL; }
    public static Ingredient find(String item) { return ALL.stream().filter(i -> i.item().equals(item)).findFirst().orElse(null); }
    public static List<Ingredient> effects(List<String> items) {
        var byFamily = new LinkedHashMap<String, Ingredient>();
        for (String item : items) {
            Ingredient ingredient = find(item);
            if (ingredient == null) continue;
            byFamily.merge(ingredient.family(), ingredient, (a, b) -> a.strength() >= b.strength() ? a : b);
        }
        return List.copyOf(byFamily.values());
    }
    public static boolean validMix(List<String> items, int slots) {
        return items != null && !items.isEmpty() && items.size() <= Math.clamp(slots, 1, 3)
            && items.stream().allMatch(i -> find(i) != null) && new HashSet<>(items).size() == items.size();
    }
    public static List<String> sequence(List<String> items) {
        if (!validMix(items, 3)) return List.of();
        var steps = new ArrayList<String>();
        items.forEach(i -> steps.add(find(i).preparation()));
        steps.add("Stir");
        steps.add("Simmer");
        return List.copyOf(steps);
    }
    public static String clue(String action) {
        return switch (action) {
            case "Grind" -> "Coarse pieces remain. Grind them into a fine powder.";
            case "Stir" -> "The mixture is separating. Stir until the colours blend.";
            default -> "The aroma is faint. Simmer gently to release its essence.";
        };
    }
}
