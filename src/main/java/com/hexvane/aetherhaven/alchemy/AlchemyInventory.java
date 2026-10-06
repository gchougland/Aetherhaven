package com.hexvane.aetherhaven.alchemy;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hexvane.aetherhaven.inventory.AtomicMaterialExchange;
import java.util.*;

/** A single native inventory write lock validates and replaces all participating slots together. */
public final class AlchemyInventory {
    private AlchemyInventory() {}
    public static boolean exchange(ItemContainer inventory, short flaskSlot, ItemStack expectedFlask, ItemStack replacement, Map<String, Integer> costs) {
        if (inventory == null || !AlchemyFlask.isFlask(expectedFlask) || !AlchemyFlask.isFlask(replacement)) return false;
        return AtomicMaterialExchange.exchange(inventory,flaskSlot,expectedFlask,replacement,costs,slot->0,()->true);
    }
    public static Map<String,Integer> brewCosts(List<String> ingredients, int charges) {
        var costs = new LinkedHashMap<String,Integer>();
        ingredients.forEach(item -> costs.merge(item, Math.clamp(charges,1,5), Integer::sum));
        return costs;
    }
}
