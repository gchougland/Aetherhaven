package com.hexvane.aetherhaven.alchemy;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.hunter.HuntService;
import com.hexvane.aetherhaven.production.ProductionCatalog;
import com.hexvane.aetherhaven.production.ProductionEffectiveCatalog;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.warehouse.*;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import java.util.*;
import java.util.function.Supplier;

/** Same production pools as the warehouse Production tab. Every operation runs on the owning world thread. */
public final class AlchemyTownStock {
    private AlchemyTownStock() {}
    public record Source(UUID plot,ProductionInventory inventory) {}
    public record Production(Source source,Map<String,Long> amounts,Map<String,Long> reserved) {
        public Production { amounts=Map.copyOf(amounts);reserved=Map.copyOf(reserved); }
        long available(String item){return Math.max(0,amounts.getOrDefault(item,0L)-reserved.getOrDefault(item,0L));}
    }
    public record Snapshot(TownWarehouse.CraftingStock storage,List<Production> production) {
        public Snapshot { production=List.copyOf(production); }
        public long count(String item){return storage.count(item)+production.stream().mapToLong(p->p.available(item)).sum();}
    }
    public static List<Source> sources(TownRecord town,AetherhavenPlugin plugin) {
        var sources=new ArrayList<Source>();
        for(var plot:town.getPlotInstances()) {
            String kind=plugin.getConstructionCatalog().resolveGameplayConstructionId(plot.getConstructionId());
            if(plot.getState()!=PlotInstanceState.COMPLETE||!ProductionCatalog.isProductionWorkplaceConstruction(kind))continue;
            var state=town.getOrCreatePlotProduction(plot.getPlotId());
            if(ProductionEffectiveCatalog.effective(plugin.getProductionCatalog(),plugin.getWorkplaceUnlockCatalog(),kind,state)!=null)
                sources.add(new Source(plot.getPlotId(),state));
        }
        var lodge=town.findCompletePlotWithConstruction(plugin.getConstructionCatalog(),HuntService.CONSTRUCTION);
        if(lodge!=null)sources.add(new Source(lodge.getPlotId(),town.getHunts()));
        return List.copyOf(sources);
    }
    public static Snapshot snapshot(TownWarehouse warehouse,List<Source> sources) {
        var production=new ArrayList<Production>();var plots=new HashSet<UUID>();
        var inventories=Collections.newSetFromMap(new IdentityHashMap<ProductionInventory,Boolean>());
        for(var source:sources) {
            if(source==null||source.plot()==null||source.inventory()==null||!plots.add(source.plot())||!inventories.add(source.inventory()))continue;
            var amounts=new LinkedHashMap<String,Long>();var reserved=new LinkedHashMap<String,Long>();
            for(var ingredient:AlchemyIngredients.all()) {
                long count=source.inventory().getAmount(ingredient.item());
                if(count>0) {amounts.put(ingredient.item(),count);reserved.put(ingredient.item(),warehouse.reservedProduction(source.plot(),ingredient.item()));}
            }
            production.add(new Production(source,amounts,reserved));
        }
        return new Snapshot(warehouse.craftingStock(),production);
    }
    public static boolean brew(TownWarehouse warehouse,Supplier<List<Source>> sources,Snapshot seen,ItemContainer bag,
                               short flaskSlot,ItemStack expected,ItemStack replacement,Map<String,Integer> costs) {
        if(seen==null||!AlchemyFlask.isFlask(expected)||!AlchemyFlask.isFlask(replacement))return false;
        var spentProduction=new boolean[]{false};
        boolean accepted=warehouse.exchangeForCrafting(bag,flaskSlot,expected,replacement,costs,seen.storage(),
            ()->seen.equals(snapshot(warehouse,sources.get())),remaining->{
                var left=new HashMap<>(remaining);var plans=new LinkedHashMap<Source,Map<String,Long>>();
                for(var held:seen.production())for(var need:List.copyOf(left.entrySet())) {
                    long take=Math.min(need.getValue(),held.available(need.getKey()));if(take<=0)continue;
                    plans.computeIfAbsent(held.source(),unused->new LinkedHashMap<>()).put(need.getKey(),take);
                    left.put(need.getKey(),need.getValue()-(int)take);
                }
                if(left.values().stream().anyMatch(n->n>0))return false;
                // All sources and reservations were checked above, and no callback or
                // task can interleave on this world thread between this plan and commit.
                for(var plan:plans.entrySet())for(var take:plan.getValue().entrySet())
                    plan.getKey().inventory().removeAmountUpTo(take.getKey(),take.getValue());
                spentProduction[0]=!plans.isEmpty();return true;
            });
        if(accepted&&spentProduction[0])warehouse.markProductionWithdrawal();
        return accepted;
    }
}
