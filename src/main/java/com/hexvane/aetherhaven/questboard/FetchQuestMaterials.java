package com.hexvane.aetherhaven.questboard;

import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/** Plan disjoint native item/metadata predicates, then consume all requested materials atomically. */
final class FetchQuestMaterials {
    private FetchQuestMaterials() {}
    static boolean available(ItemContainer inventory,List<QuestBoardItemRequirement> requirements) {
        var costs=costs(inventory,requirements);
        return costs!=null&&inventory.canRemoveMaterials(costs,true,true);
    }
    static boolean consume(ItemContainer inventory,List<QuestBoardItemRequirement> requirements) {
        var costs=costs(inventory,requirements);
        return costs!=null&&inventory.removeMaterials(costs,true,true,true).succeeded();
    }
    private static List<MaterialQuantity> costs(ItemContainer inventory,List<QuestBoardItemRequirement> requirements) {
        if(requirements.isEmpty())return null;
        var counts=new LinkedHashMap<String,Long>();
        for(var requirement:requirements) {
            String item=requirement.itemIdOrEmpty();if(item.isBlank())return null;
            long count=counts.getOrDefault(item,0L)+requirement.count();
            if(count>Integer.MAX_VALUE)return null;
            counts.put(item,count);
        }
        var variants=new ArrayList<MaterialQuantity>();
        var result=new ArrayList<MaterialQuantity>();
        for(short slot=0;slot<inventory.getCapacity();slot++) {
            var stack=inventory.getItemStack(slot);if(ItemStack.isEmpty(stack))continue;
            String id=stack.getItemId();long remaining=counts.getOrDefault(id,0L);
            if(remaining==0)continue;
            if(variants.stream().anyMatch(v->id.equals(v.getItemId())&&Objects.equals(stack.getMetadata(),v.getMetadata())))continue;
            var variant=new MaterialQuantity(id,null,null,1,stack.getMetadata());
            variants.add(variant);
            int available=inventory.countRemovableMaterial(variant);
            int take=(int)Math.min(remaining,Math.max(0,available));
            if(take==0)continue;
            result.add(variant.clone(take));counts.put(id,remaining-take);
        }
        return counts.values().stream().anyMatch(n->n>0)?null:result;
    }
}
