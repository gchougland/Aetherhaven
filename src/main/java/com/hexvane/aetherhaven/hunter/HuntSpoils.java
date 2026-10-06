package com.hexvane.aetherhaven.hunter;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.item.ItemModule;
import java.util.*;

/** Roll once at verified defeat, using the live native drop table without equipment rewards. */
final class HuntSpoils {
    private HuntSpoils() {}
    static Map<String,Integer> roll(HuntFamily family) {
        if(family.nativeDropList()==null)return family.drops();
        return materials(ItemModule.get().getRandomItemDrops(family.nativeDropList()));
    }
    static boolean equipment(String id) {
        return id.startsWith("Weapon_")||id.startsWith("Armor_")||id.startsWith("Tool_");
    }
    static Map<String,Integer> materials(List<ItemStack> rolled) {
        var result=new LinkedHashMap<String,Integer>();
        for(var stack:rolled)if(!ItemStack.isEmpty(stack)&&!equipment(stack.getItemId()))
            result.merge(stack.getItemId(),stack.getQuantity(),Math::addExact);
        return Map.copyOf(result);
    }
}
