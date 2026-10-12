package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import java.util.Map;

public final class TownRecruitment {
    private TownRecruitment() {}
    private static final Map<String, Integer> LEVELS = Map.ofEntries(
        Map.entry("Elder_Lyren", 1), Map.entry("Innkeeper", 1), Map.entry("Farmer", 1), Map.entry("Rancher", 1),
        Map.entry("Miner", 2), Map.entry("Logger", 2), Map.entry("Builder", 2), Map.entry("Priestess", 2), Map.entry("Guild_Master", 2), Map.entry("Chef", 2), Map.entry("Warehouse_Manager", 2),
        Map.entry("Blacksmith", 3), Map.entry("Crafter", 3),
        Map.entry("Leatherworker", 4), Map.entry("Merchant", 4), Map.entry("Monster_Hunter", 4), Map.entry("Doctor", 4), Map.entry("Druid", 4),
        Map.entry("Alchemist", 5), Map.entry("Furniture_Merchant", 5), Map.entry("Florist", 5), Map.entry("Bard", 5),
        Map.entry("Crystal_Keeper", 6), Map.entry("Pyrotechnic", 6), Map.entry("Clown", 6));

    public static int minimumLevel(AetherhavenPlugin plugin, String roleId) {
        var definition = plugin.getVillagerDefinitionCatalog().byNpcRoleId(roleId);
        if (definition != null && definition.getMinimumTownLevel() > 0) return definition.getMinimumTownLevel();
        return fallbackLevel(roleId);
    }

    /** The level used when a villager's own file does not state one. */
    static int fallbackLevel(String roleId) {
        return LEVELS.getOrDefault(roleId.replaceFirst("^Aetherhaven_", ""), 2);
    }

    public static boolean available(AetherhavenPlugin plugin, TownRecord town, String roleId) {
        if (TownPopulation.levelFor(TownPopulation.count(town)) < minimumLevel(plugin, roleId)) return false;
        if ("Aetherhaven_Chef".equals(roleId)) return town.hasCompletePlotWithConstruction(plugin.getConstructionCatalog(), "plot_farm")
            && town.hasCompletePlotWithConstruction(plugin.getConstructionCatalog(), "plot_barn");
        return true;
    }
}
