package com.hexvane.aetherhaven.production;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.item.ItemModule;
import java.util.*;

/** Automatic harvest products and optional fibre from one physically felled tree. */
public final class NativeTimberProducts {
    private NativeTimberProducts() {}
    public static final Set<String> BYPRODUCTS=Set.of("Ingredient_Fibre","Ingredient_Stick","Ingredient_Tree_Sap");
    private static final Set<String> DROP_LISTS=java.util.concurrent.ConcurrentHashMap.newKeySet();
    static {DROP_LISTS.addAll(List.of("Tree_Leaves","Wood_Branch"));}
    /** Add-ons explicitly opt in foliage/branch tables; only the three supported byproducts are retained. */
    public static void registerDropList(String id) {
        if(id==null||id.isBlank())throw new IllegalArgumentException("A drop list ID is required");
        DROP_LISTS.add(id);
    }
    public static boolean supported(String item) {
        return item!=null&&(BYPRODUCTS.contains(item)||NativeTimberGrowth.species().stream().anyMatch(s->s.sapling().equals(item)));
    }
    public static boolean automatic(String item) {
        return supported(item)&&!"Ingredient_Fibre".equals(item);
    }
    public static Set<String> selected(PlotProductionState production,ProductionCatalog.Entry entry,TimberPlantingReceipt tree) {
        var result=new LinkedHashSet<String>();
        result.add(tree.sapling());
        result.add("Ingredient_Tree_Sap");
        result.add("Ingredient_Stick");
        for(int slot=0;slot<WorkplaceProductionUpgrades.slotCount(production);slot++) {
            String item=entry.itemAtCursor(production.getSlotCursor(slot));
            if(item!=null&&(BYPRODUCTS.contains(item)||tree.sapling().equals(item)))result.add(item);
        }
        return Collections.unmodifiableSet(result);
    }
    public static boolean hasRoom(PlotProductionState production,ProductionCatalog.Entry entry,TimberPlantingReceipt tree) {
        var outputs=new HashSet<>(selected(production,entry,tree));outputs.add(tree.output());
        return outputs.stream().allMatch(item->production.getAmount(item)<WorkplaceProductionUpgrades.effectiveMaxStorage(production,entry,item));
    }
    /** Extra collection is bounded by free local storage, before any cargo is created. */
    public static Map<String,Integer> collectionLimits(PlotProductionState production,ProductionCatalog.Entry entry,TimberPlantingReceipt tree) {
        var limits=new LinkedHashMap<String,Integer>();
        for(String item:selected(production,entry,tree)) {
            long capacity=WorkplaceProductionUpgrades.effectiveMaxStorage(production,entry,item);
            long free=Math.max(0,capacity-production.getAmount(item));
            limits.put(item,(int)Math.min(Integer.MAX_VALUE,free));
        }
        return limits;
    }
    /** Only real changed anchors contribute. Fillers, fruit, minerals and decorations cannot award their drops. */
    public static Map<String,Integer> harvest(TimberFellingPlan plan,TimberPlantingReceipt tree,Map<String,Integer> limits) {
        var result=new TimberProductCollection(limits);
        var selected=limits.keySet();
        if(!Collections.disjoint(selected,BYPRODUCTS))for(var change:plan.changes()) {
            if(change.before().filler()!=0)continue;
            var block=BlockType.getAssetMap().getAsset(change.before().blockId());
            if(block==null||block.getGathering()==null)continue;
            var gathering=block.getGathering();var soft=gathering.getSoft();var breaking=gathering.getBreaking();
            String list=soft!=null?soft.getDropListId():breaking!=null?breaking.getDropListId():null;
            if(list==null||!DROP_LISTS.contains(list))continue;
            var drops=ItemModule.get().getRandomItemDrops(list);
            if(drops!=null)for(var stack:drops)if(!ItemStack.isEmpty(stack)&&BYPRODUCTS.contains(stack.getItemId())&&selected.contains(stack.getItemId()))
                result.collect(stack.getItemId(),stack.getQuantity());
        }
        // Every completed tree yields these products, even when native random drops roll zero.
        result.ensureMinimum(tree.sapling(),1);
        result.ensureMinimum("Ingredient_Tree_Sap",1);
        result.ensureMinimum("Ingredient_Stick",1);
        return result.products();
    }
}
