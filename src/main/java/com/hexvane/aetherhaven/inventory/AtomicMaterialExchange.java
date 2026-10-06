package com.hexvane.aetherhaven.inventory;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

/** Validates all costs and a replacement under one native lock, including combined containers. */
public final class AtomicMaterialExchange {
    private AtomicMaterialExchange() {}
    public static boolean exchange(ItemContainer inventory,short outputSlot,ItemStack expected,ItemStack replacement,
                                   Map<String,Integer> costs,IntUnaryOperator reserved,BooleanSupplier snapshotValid) {
        return exchange(inventory,outputSlot,expected,replacement,costs,reserved,snapshotValid,Map::isEmpty);
    }
    /** Additional stock is world-thread owned. Its callback must validate its whole plan before spending any item. */
    public static boolean exchange(ItemContainer inventory,short outputSlot,ItemStack expected,ItemStack replacement,
                                   Map<String,Integer> costs,IntUnaryOperator reserved,BooleanSupplier snapshotValid,
                                   Predicate<Map<String,Integer>> spendAdditional) {
        if(inventory==null||ItemStack.isEmpty(expected)||ItemStack.isEmpty(replacement)||costs==null)return false;
        final boolean[] initialized={false},accepted={false};
        final Map<Short,ItemStack> replacements=new HashMap<>();
        inventory.replaceAll((slot,existing)->{
            if(!initialized[0]) {
                initialized[0]=true;
                if(outputSlot<0||outputSlot>=inventory.getCapacity()||!Objects.equals(expected,inventory.getItemStack(outputSlot))
                    ||!snapshotValid.getAsBoolean())return existing;
                var remaining=new HashMap<>(costs);
                if(remaining.entrySet().stream().anyMatch(e->e.getKey()==null||e.getValue()==null||e.getValue()<=0))return existing;
                for(short s=0;s<inventory.getCapacity();s++) {
                    var stack=inventory.getItemStack(s);
                    if(s==outputSlot||ItemStack.isEmpty(stack))continue;
                    int need=remaining.getOrDefault(stack.getItemId(),0);
                    int available=Math.max(0,stack.getQuantity()-Math.max(0,reserved.applyAsInt(s)));
                    if(need<=0||available<=0)continue;
                    int take=Math.min(need,available);
                    replacements.put(s,take==stack.getQuantity()?null:stack.withQuantity(stack.getQuantity()-take));
                    remaining.put(stack.getItemId(),need-take);
                }
                remaining.entrySet().removeIf(e->e.getValue()==0);
                if(!spendAdditional.test(Map.copyOf(remaining))){replacements.clear();return existing;}
                replacements.put(outputSlot,replacement);accepted[0]=true;
            }
            return accepted[0]&&replacements.containsKey(slot)?replacements.get(slot):existing;
        });
        return accepted[0];
    }
}
