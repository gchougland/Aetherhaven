package com.hexvane.aetherhaven.alchemy;

import java.util.*;

/** Uses existing native plants only. Stronger ingredients replace weaker effects of the same family. */
public final class AlchemyIngredients {
    private AlchemyIngredients() {}
    public record Ingredient(String item, String name, String family, int strength, String effect, String description) {}
    private static final List<Ingredient> ALL = create();
    private static List<Ingredient> create() {
        var result = new ArrayList<Ingredient>();
        String[][] names = {{"Blood Rose", "Bloodcap Mushroom", "Blood Leaf"}, {"Azure Fern", "Azurecap Mushroom", "Azure Kelp"}, {"Storm Thistle", "Stormcap Mushroom", "Storm Rush"}};
        String[] families = {"Health", "Mana", "Stamina"};
        int[] amounts = {15, 30, 50};
        for (int f = 0; f < families.length; f++) for (int tier = 1; tier <= 3; tier++) {
            String family = families[f];
            result.add(new Ingredient("Plant_Crop_" + family + tier, names[f][tier-1], family, tier,
                "Aetherhaven_Alchemy_" + family + tier, "Restores " + amounts[tier-1] + "% " + family.toLowerCase(Locale.ROOT)));
        }
        result.add(mushroom("Boomshroom_Small", "Small Boomshroom", "BlastWard", 1, "Explosion immunity for 60 seconds"));
        result.add(mushroom("Boomshroom_Large", "Large Boomshroom", "BlastWard", 1, "Explosion immunity for 60 seconds"));
        result.add(mushroom("Common_Lime", "Puffy Green Common Mushroom", "PoisonWard", 1, "Poison immunity for 60 seconds"));
        result.add(mushroom("Cap_Red", "Red Cap Mushroom", "HealthBoost", 1, "Health Boost 1: +15 maximum health for 120 seconds"));
        result.add(mushroom("Glowing_Red", "Red Glowing Mushroom", "HealthBoost", 2, "Health Boost 2: +20 maximum health for 240 seconds"));
        result.add(mushroom("Common_Blue", "Blue Common Mushroom", "ManaBoost", 1, "Mana Boost 1: +15 maximum mana for 120 seconds"));
        result.add(mushroom("Glowing_Blue", "Blue Glowing Mushroom", "ManaBoost", 2, "Mana Boost 2: +20 maximum mana for 240 seconds"));
        // Health over time. The amounts and durations live in the Aetherhaven_Alchemy_Regen effect assets.
        result.add(plant("Plant_Flower_Common_Blue2", "Blue Aloe", "Regen", 1, "Regeneration 1: restores 15% health over 30 seconds"));
        result.add(mushroom("Flatcap_Green", "Green Flatcap Mushroom", "Regen", 1, "Regeneration 1: restores 15% health over 30 seconds"));
        result.add(mushroom("Cap_Green", "Spotted Green Cap Mushroom", "Regen", 2, "Regeneration 2: restores 30% health over 40 seconds"));
        result.add(mushroom("Glowing_Green", "Green Glowing Mushroom", "Regen", 3, "Regeneration 3: restores 50% health over 50 seconds"));
        result.add(plant("Plant_Flower_Flax_Orange", "Fire Flower", "FireWard", 1, "Fire and lava immunity for 60 seconds"));
        // Which creatures are repelled is listed in the Aetherhaven_Alchemy_Repelled NPC group.
        result.add(plant("Plant_Flower_Tall_Purple", "Lavender", "Repellent", 1, "Bug repellent: scaraks, spiders and scorpions ignore you for 120 seconds or until you attack one"));
        result.add(plant("Plant_Flower_Common_Orange", "Chrysanthemum", "Repellent", 1, "Bug repellent: scaraks, spiders and scorpions ignore you for 120 seconds or until you attack one"));
        return List.copyOf(result);
    }
    private static Ingredient mushroom(String suffix, String name, String family, int strength, String description) {
        return plant("Plant_Crop_Mushroom_" + suffix, name, family, strength, description);
    }
    private static Ingredient plant(String item, String name, String family, int strength, String description) {
        return new Ingredient(item, name, family, strength, effectId(family, strength), description);
    }
    public static String effectId(String family, int strength) { return "Aetherhaven_Alchemy_" + family + strength; }
    /** Every strength an ingredient offers for this family, weakest first. */
    public static List<Integer> strengths(String family) {
        return ALL.stream().filter(i -> i.family().equals(family)).map(Ingredient::strength).distinct().sorted().toList();
    }
    /** What drinking one strength does when other strengths of the same family are still active. */
    public record Overlap(boolean apply, List<Integer> replace) {}
    /** Strengths of one family never stack: a stronger active one is kept, weaker active ones are replaced. */
    public static Overlap overlap(int strength, Collection<Integer> activeStrengths) {
        if (activeStrengths.stream().anyMatch(active -> active > strength)) return new Overlap(false, List.of());
        return new Overlap(true, activeStrengths.stream().filter(active -> active < strength).distinct().sorted().toList());
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
}
