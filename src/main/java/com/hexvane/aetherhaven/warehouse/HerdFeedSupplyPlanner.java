package com.hexvane.aetherhaven.warehouse;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.production.HerdProductionState;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.*;

/** The barn's opt-in feed target uses ordinary source reservations and physically carried cargo. */
public final class HerdFeedSupplyPlanner {
    private HerdFeedSupplyPlanner() {}
    public static String food(WarehouseBatch batch) {
        String prefix="aetherhaven:barn_feed/";
        String id=batch.recipeId()!=null&&batch.recipeId().startsWith(prefix)?batch.recipeId().substring(prefix.length()):null;
        return com.hexvane.aetherhaven.production.BarnAnimalCatalog.food(id)?id:null;
    }

    private static HerdProductionState herd(TownRecord town,AetherhavenPlugin plugin,UUID plotId) {
        var plot=town.findPlotById(plotId);
        if(plot==null||plot.getState()!=PlotInstanceState.COMPLETE||!plugin.getConstructionCatalog()
            .matchesGameplayConstruction(plot.getConstructionId(),AetherhavenConstants.CONSTRUCTION_PLOT_BARN))return null;
        var production=town.peekPlotProduction(plotId);
        return production!=null&&production.usesPhysicalHerd()?production.herd():null;
    }

    /** Unavailable chunks pause deliveries. Only a known invalid destination cancels the order. */
    public static boolean invalid(World world,TownRecord town,AetherhavenPlugin plugin,WarehouseBatch batch) {
        if(!batch.herdFeed())return false;
        var herd=herd(town,plugin,batch.workplaceId());var point=batch.destination();
        if(herd==null||herd.courierFeedTarget()==0||point==null||food(batch)==null)return true;
        var state=town.peekPlotProduction(batch.workplaceId());
        if(!herd.requestedFoods(com.hexvane.aetherhaven.production.WorkplaceProductionUpgrades.slotCount(state)).contains(food(batch)))return true;
        var plot=town.findPlotById(batch.workplaceId());
        if(!plot.containsWorldBlock(point.x(),point.y(),point.z())
            ||!TownTerritoryClaims.contains(town,ChunkUtil.chunkCoordinate(point.x()),ChunkUtil.chunkCoordinate(point.z())))return true;
        return ChunkSectionBlockUtil.worldChunkIfTicking(world,point.x(),point.z())!=null
            &&!ProductionStorageLocator.valid(world,plot,point);
    }

    public static boolean destinationReady(World world,TownRecord town,AetherhavenPlugin plugin,WarehouseBatch batch) {
        return batch.herdFeed()&&!batch.cancelled()&&!invalid(world,town,plugin,batch)
            &&ProductionStorageLocator.valid(world,town.findPlotById(batch.workplaceId()),batch.destination());
    }

    public static boolean deliver(World world,TownRecord town,AetherhavenPlugin plugin,WarehouseBatch batch,String courier) {
        return destinationReady(world,town,plugin,batch)&&town.getWarehouse().deliverHerdFeed(batch.id(),courier,
            herd(town,plugin,batch.workplaceId()),batch.destination());
    }

    /** Called alongside workplace planning, after verifying the town's Warehouse Manager. */
    public static boolean queue(World world,TownRecord town,AetherhavenPlugin plugin,List<TownWarehouse.ProductionStock> sources,long now) {
        boolean changed=false;var stock=town.getWarehouse();
        for(var plot:town.getPlotInstances()) {
            var herd=herd(town,plugin,plot.getPlotId());
            if(herd==null||stock.workplaceBatch(plot.getPlotId())!=null)continue;
            var destination=ProductionStorageLocator.find(world,plot,now);
            if(destination==null||!TownTerritoryClaims.contains(town,ChunkUtil.chunkCoordinate(destination.x()),ChunkUtil.chunkCoordinate(destination.z())))continue;
            // A barn is never sent feed out of its own store: the courier would carry it out and straight back in.
            var elsewhere=sources.stream().filter(source->!plot.getPlotId().equals(source.plot())).toList();
            for(String food:herd.requestedFoods(com.hexvane.aetherhaven.production.WorkplaceProductionUpgrades.slotCount(town.peekPlotProduction(plot.getPlotId())))) {
                int quantity=Math.min(herd.courierFeedNeeded(food),availableFood(stock,elsewhere,food));
                if(quantity>0&&stock.reserveHerdFeed(plot.getPlotId(),destination,food,quantity,elsewhere)!=null){changed=true;break;}
            }
        }
        return changed;
    }

    /** Partial stock is useful now; never wait for an entire target quantity to exist. */
    public static int availableFood(TownWarehouse stock,List<TownWarehouse.ProductionStock> sources,String food) {
        long available=0;var seen=new HashSet<UUID>();
        for(var source:sources)if(source.plot()!=null&&source.pickup()!=null&&source.state()!=null&&seen.add(source.plot())) {
            available+=Math.min(HerdProductionState.FEED_CAPACITY,Math.max(0,source.state().getAmount(food)-stock.reservedProduction(source.plot(),food)));
            if(available>=HerdProductionState.FEED_CAPACITY)return HerdProductionState.FEED_CAPACITY;
        }
        for(short slot=0;slot<stock.slots(TownWarehouse.Page.GENERAL);slot++) {
            var stack=stock.stack(TownWarehouse.Page.GENERAL,slot);
            if(!ItemStack.isEmpty(stack)&&stack.isEquivalentType(new ItemStack(food,1)))
                available+=Math.max(0,stack.getQuantity()-stock.reserved(TownWarehouse.Page.GENERAL,slot));
            if(available>=HerdProductionState.FEED_CAPACITY)return HerdProductionState.FEED_CAPACITY;
        }
        return (int)available;
    }
}
