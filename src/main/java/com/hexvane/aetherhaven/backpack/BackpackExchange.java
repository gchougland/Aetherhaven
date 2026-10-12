package com.hexvane.aetherhaven.backpack;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.*;
import java.util.*;
import java.util.function.Function;

/** Plans the entire operation before replacing anything under the native inventory lock. */
public final class BackpackExchange {
    private BackpackExchange(){}
    static boolean commit(ItemContainer inventory,Function<ItemStack[],Boolean> edit){
        return new LockedInventory(inventory).edit(edit);
    }
    /** Native replaceAll skips empty slots. This view includes them under the same native locks. */
    private static final class LockedInventory extends CombinedItemContainer {
        LockedInventory(ItemContainer inventory){super(inventory);}
        boolean edit(Function<ItemStack[],Boolean> edit){
            var tx=writeAction(()->{
                var before=new ItemStack[getCapacity()];for(short i=0;i<before.length;i++)before[i]=internal_getSlot(i);
                var plan=before.clone();var changes=new ArrayList<ItemStackSlotTransaction>();
                if(!edit.apply(plan))return new ListTransaction<ItemStackSlotTransaction>(false,changes);
                for(short i=0;i<plan.length;i++)if(!Objects.equals(before[i],plan[i])){
                    internal_setSlot(i,plan[i]);changes.add(new ItemStackSlotTransaction(true,ActionType.REPLACE,i,before[i],plan[i],before[i],true,false,false,false,plan[i],plan[i]));
                }
                return new ListTransaction<ItemStackSlotTransaction>(true,changes);
            });
            if(tx.succeeded())sendUpdate(tx);return tx.succeeded();
        }
    }
    public static boolean craft(ItemContainer inv){
        return commit(inv,plan->{int slot=-1;for(int i=0;i<plan.length;i++)if(ItemStack.isEmpty(plan[i])){slot=i;break;}
            if(slot<0||!pay(plan,-1,Map.of("Ingredient_Leather_Light",10)))return false;plan[slot]=Backpack.create();return true;});
    }
    public static boolean change(ItemContainer inv,short slot,ItemStack expected,Map<String,Integer> cost,Function<Backpack,Boolean> edit){
        return commit(inv,plan->{if(slot<0||slot>=plan.length||!Objects.equals(expected,plan[slot]))return false;
            var pack=Backpack.read(plan[slot]);if(pack==null||!edit.apply(pack)||!pay(plan,slot,cost))return false;
            plan[slot]=pack.write(plan[slot]);return true;});
    }
    public static boolean transfer(ItemContainer inv,short bagSlot,ItemStack expected,int slot,ItemStack source,boolean deposit,boolean one){
        return commit(inv,plan->{if(bagSlot<0||bagSlot>=plan.length||!Objects.equals(expected,plan[bagSlot]))return false;
            var pack=Backpack.read(expected);if(pack==null)return false;
            if(deposit){
                if(slot<0||slot>=plan.length||slot==bagSlot||ItemStack.isEmpty(source)||!Objects.equals(source,plan[slot]))return false;
                int accepted=pack.insert(one?source.withQuantity(1):source);if(accepted==0)return false;
                plan[slot]=accepted==source.getQuantity()?null:source.withQuantity(source.getQuantity()-accepted);
            }else{
                if(slot<0||slot>=pack.tier.slots||ItemStack.isEmpty(source)||!Objects.equals(source,pack.items[slot]))return false;
                int requested=one?1:com.hexvane.aetherhaven.inventory.StackedSlots.oneStack(source);
                int left=requested;
                for(int pass=0;pass<2;pass++)for(int i=0;i<plan.length&&left>0;i++){
                    if(i==bagSlot)continue;var held=plan[i];boolean empty=ItemStack.isEmpty(held);
                    if(pass==0?empty||!held.isStackableWith(source):!empty)continue;
                    int n=empty?0:held.getQuantity(),take=Math.min(left,Math.max(0,source.getItem().getMaxStack()-n));
                    if(take>0){plan[i]=source.withQuantity(n+take);left-=take;}
                }
                int accepted=requested-left;if(accepted==0)return false;
                pack.items[slot]=accepted==source.getQuantity()?null:source.withQuantity(source.getQuantity()-accepted);
            }
            plan[bagSlot]=pack.write(expected);return true;
        });
    }
    /**
     * Quick stack: moves every stack in inventory slots {@code from} (inclusive) to {@code to} (exclusive) that the bag
     * already holds some of. What does not fit stays where it was.
     *
     * @return the number of items stored, or 0 when nothing matched, nothing fitted or the bag changed
     */
    public static int storeMatching(ItemContainer inv,short bagSlot,ItemStack expected,int from,int to){
        int[] moved={0};
        boolean done=commit(inv,plan->{moved[0]=0;
            if(bagSlot<0||bagSlot>=plan.length||!Objects.equals(expected,plan[bagSlot]))return false;
            var pack=Backpack.read(expected);if(pack==null)return false;
            for(int i=Math.max(0,from);i<Math.min(to,plan.length);i++){
                var stack=plan[i];
                if(i==bagSlot||ItemStack.isEmpty(stack)||!pack.holds(stack))continue;
                int accepted=pack.insert(stack);if(accepted==0)continue;
                plan[i]=accepted==stack.getQuantity()?null:stack.withQuantity(stack.getQuantity()-accepted);moved[0]+=accepted;
            }
            if(moved[0]==0)return false;
            plan[bagSlot]=pack.write(expected);return true;
        });
        return done?moved[0]:0;
    }
    public static int collect(ItemContainer inv,short slot,ItemStack expected,ItemStack incoming){
        int[] accepted={0};boolean done=change(inv,slot,expected,Map.of(),pack->{accepted[0]=pack.insert(incoming);return accepted[0]>0;});return done?accepted[0]:0;
    }
    private static boolean pay(ItemStack[] plan,int excluded,Map<String,Integer> costs){
        var left=new HashMap<>(costs);
        for(int i=0;i<plan.length;i++)if(i!=excluded&&!ItemStack.isEmpty(plan[i])){
            var stack=plan[i];int take=Math.min(stack.getQuantity(),left.getOrDefault(stack.getItemId(),0));
            if(take>0){left.put(stack.getItemId(),left.get(stack.getItemId())-take);plan[i]=take==stack.getQuantity()?null:stack.withQuantity(stack.getQuantity()-take);}
        }return left.values().stream().allMatch(n->n==0);
    }
}
