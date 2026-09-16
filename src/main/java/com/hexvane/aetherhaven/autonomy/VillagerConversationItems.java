package com.hexvane.aetherhaven.autonomy;

import java.util.Set;

/** Small, actual items that suit a one-handed presentation; never pull out buildings or furniture. */
final class VillagerConversationItems {
    private static final Set<String> ITEMS = Set.of(
        "Food_Bread", "Plant_Fruit_Apple", "Plant_Crop_Carrot_Item", "Plant_Crop_Wheat_Item",
        "Rock_Gem_Ruby", "Rock_Gem_Diamond", "Rock_Gem_Topaz", "Rock_Gem_Zephyr",
        "Potion_Health", "Tool_Hammer_Iron", "Tool_Hammer_Crude", "Tool_Repair_Kit_Iron",
        "Plant_Flower_Bushy_Blue", "Plant_Flower_Bushy_Cyan",
        "Plant_Seeds_Carrot", "Plant_Seeds_Wheat", "Plant_Seeds_Tomato",
        "Ingredient_Bar_Iron", "Ingredient_Bar_Gold", "Ingredient_Bar_Copper"
    );
    static String forTopic(String topic, double roll) {
        if (topic == null || !topic.startsWith("Item_") || !(roll >= 0 && roll < .4)) return null;
        String item = topic.substring(5);
        if (item.equals("Plant_Crop_Carrot")) item = "Plant_Crop_Carrot_Item";
        return ITEMS.contains(item) ? item : null;
    }
    private VillagerConversationItems() {}
}
