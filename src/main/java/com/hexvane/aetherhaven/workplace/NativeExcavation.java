package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.*;
import com.hexvane.aetherhaven.production.*;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.warehouse.*;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.item.ItemModule;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.*;

/** Terrain adapters read loaded cells only. Native drops are kept as cargo; no loose duplicate drops. */
public final class NativeExcavation {
    private NativeExcavation() {}
    private static final Map<String,Integer> MATERIALS=new java.util.concurrent.ConcurrentHashMap<>();
    static {
        register("Ore_Adamantite_Magma",9);
        register("Ore_Adamantite_Magma_Cracked",9);
        register("Ore_Cobalt_Shale",7);
        register("Ore_Cobalt_Slate",7);
        register("Ore_Cobalt_Slate_Cracked",7);
        register("Ore_Copper_Sandstone",1);
        register("Ore_Copper_Shale",1);
        register("Ore_Copper_Stone",1);
        register("Ore_Gold_Basalt",4);
        register("Ore_Gold_Calcite",4);
        register("Ore_Gold_Sandstone",4);
        register("Ore_Gold_Shale",4);
        register("Ore_Gold_Stone",4);
        register("Ore_Gold_Volcanic",4);
        register("Ore_Iron_Basalt",3);
        register("Ore_Iron_Basalt_Cracked",3);
        register("Ore_Iron_Sandstone",3);
        register("Ore_Iron_Shale",3);
        register("Ore_Iron_Slate",3);
        register("Ore_Iron_Stone",3);
        register("Ore_Iron_Volcanic",3);
        register("Ore_Mithril_Stone",10);
        register("Ore_Silver_Basalt",4);
        register("Ore_Silver_Sandstone",4);
        register("Ore_Silver_Shale",4);
        register("Ore_Silver_Slate",4);
        register("Ore_Silver_Stone",4);
        register("Ore_Silver_Volcanic",4);
        register("Ore_Thorium_Mud",5);
        register("Ore_Thorium_Mud_Cracked",5);
        register("Ore_Thorium_Sandstone",5);
        register("Rock_Aqua",1);
        register("Rock_Basalt",1);
        register("Rock_Calcite",1);
        register("Rock_Chalk",1);
        register("Rock_Lime",1);
        register("Rock_Magma_Cooled",1);
        register("Rock_Marble",1);
        register("Rock_Peach",1);
        register("Rock_Quartzite",1);
        register("Rock_Sandstone",1);
        register("Rock_Sandstone_Red",1);
        register("Rock_Sandstone_White",1);
        register("Rock_Shale",1);
        register("Rock_Slate",1);
        register("Rock_Stone",1);
        register("Rock_Volcanic",1);
        register("Soil_Dirt",1);
        register("Soil_Gravel",1);
    }
    /** Only plain resource drops are supported. Structures and block entities remain excluded. */
    public static void register(String block,int skill){if(block==null||block.isBlank()||skill<1||skill>10)throw new IllegalArgumentException("Excavation material requires a block and skill from 1 to 10");MATERIALS.put(block,skill);}
    public static int skill(String block){return block==null?0:MATERIALS.getOrDefault(block,0);}
    public interface Protection { boolean permits(World world,TownRecord town,WarehouseBatch.Point point); }
    private static final List<Protection> PROTECTIONS=new java.util.concurrent.CopyOnWriteArrayList<>();
    public static void registerProtection(Protection protection){PROTECTIONS.add(Objects.requireNonNull(protection));}
    public record Review(List<ExcavationPlan.Cell> cells,String issue){public Review{cells=List.copyOf(cells);}public boolean ready(){return issue==null&&!cells.isEmpty();}}
    public static TimberProductionState.Ground observe(World world,WarehouseBatch.Point p){return NativeTimberPlanting.observe(world,new TimberHarvestPlan.Position(p.x(),p.y(),p.z()));}
    private static boolean allowed(World world,TownRecord town,ApprenticeWorkplace site,WarehouseBatch.Point p) {
        return site.type()==TownsfolkWorkType.MINING&&site.area().contains(p.x(),p.y(),p.z())
            &&TownTerritoryClaims.contains(town,ChunkUtil.chunkCoordinate(p.x()),ChunkUtil.chunkCoordinate(p.z()))
            &&town.getPlotInstances().stream().noneMatch(plot->plot.toFootprint().containsBlock(p.x(),p.y(),p.z()))
            &&PROTECTIONS.stream().allMatch(rule->rule.permits(world,town,p));
    }
    public static Review review(World world,TownRecord town,ApprenticeWorkplace site,ExcavationPlan.Entrance entrance) {
        return review(world,town,site,entrance,ExcavationPlan.Layout.STRAIGHT);
    }
    public static Review review(World world,TownRecord town,ApprenticeWorkplace site,ExcavationPlan.Entrance entrance,ExcavationPlan.Layout layout) {
        var tunnel=ExcavationPlan.tunnel(site.area(),entrance,layout);
        if(tunnel.isEmpty())return new Review(List.of(),"Choose Direction and length to size this tunnel, or use the Building Staff's Plan mineshaft mode to choose its entrance.");
        var cells=new ArrayList<ExcavationPlan.Cell>();
        for(var p:tunnel) {
            if(!allowed(world,town,site,p))return new Review(cells,"Keep the tunnel inside town and outside building plots.");
            var ground=observe(world,p);if(!ground.loaded())return new Review(cells,"This tunnel reaches beyond nearby loaded ground. Shorten it or move closer to review it.");
            if(ground.protectedCell()||ground.filler()!=0)return new Review(cells,"The tunnel contains water, a furnishing or part of another block.");
            if(ground.empty())continue;
            int skill=skill(ground.block());if(skill==0||!ground.naturalTerrain())return new Review(cells,"The tunnel contains something other than supported rock, ore, dirt or gravel.");
            cells.add(new ExcavationPlan.Cell(p,ground.block(),ground.rotation(),skill));
        }
        return new Review(cells,cells.isEmpty()?"There is no rock left to excavate in this tunnel.":null);
    }
    public static String issue(World world,TownRecord town,ApprenticeWorkplace site,int workerSkill) {
        var plan=site.excavation();var cell=plan.next();
        if(plan.id()==null)return "Review a tunnel before starting work.";
        if(plan.edited())return "The tunnel was edited. Review its remaining rock again.";
        if(plan.paused())return "Excavation paused.";
        if(!site.area().equals(plan.area())||site.type()!=TownsfolkWorkType.MINING)return "The workplace changed. Review the tunnel again.";
        if(!town.hasMemberOrOwner(plan.author())||!town.playerCanManageConstructions(plan.author())||!town.playerCanBreakBlocks(plan.author()))return "The player who planned this tunnel no longer has permission to excavate.";
        if(cell==null)return fittingIssue(world,town,site);
        if(workerSkill<cell.skill())return "Needs Mining skill "+cell.skill()+" for the next block.";
        if(!allowed(world,town,site,cell.point()))return "The next block is inside a building plot or outside town borders.";
        var g=observe(world,cell.point());if(!g.loaded())return "The next tunnel section is not being simulated.";
        if(g.protectedCell()||g.filler()!=0||!cell.block().equals(g.block())||cell.rotation()!=g.rotation())return "The next block changed. Review the tunnel again.";
        // Never open a face directly into fluid, unknown terrain or a block entity.
        for(int[] d:new int[][]{{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}}) {
            var p=cell.point();var neighbor=observe(world,new WarehouseBatch.Point(p.x()+d[0],p.y()+d[1],p.z()+d[2]));
            if(!neighbor.loaded()||neighbor.protectedCell())return "Clear water or protected blocks beside the next rock before continuing.";
        }
        return null;
    }
    private static String fittingIssue(World world,TownRecord town,ApprenticeWorkplace site) {
        var plan=site.excavation();var fitting=plan.nextFitting();
        if(fitting==null)return "Tunnel complete. Review another tunnel to continue.";
        if(!town.playerCanPlaceBlocks(plan.author())||!town.playerCanOpenContainers(plan.author()))return "Fittings need building and town storage permission.";
        var p=fitting.point();
        if(!allowed(world,town,site,p))return "Keep fittings inside town and outside building plots.";
        var ground=observe(world,p);if(!ground.loaded())return "Visit the tunnel before adding fittings.";
        if(!ground.empty()||ground.protectedCell()||ground.filler()!=0)return "Clear the next fitting position before continuing.";
        var support=observe(world,new WarehouseBatch.Point(p.x(),p.y()-1,p.z()));
        var supportType=support.block()==null?null:BlockType.getAssetMap().getAsset(support.block());
        if(!support.loaded()||fitting.rotation()==0&&(support.empty()||support.protectedCell()||supportType==null||supportType.getMaterial()!=com.hypixel.hytale.protocol.BlockMaterial.Solid))return "Keep solid ground beneath the next fitting.";
        if(!town.getWarehouse().hasUnreservedStock(fitting.item()))return "Supply "+(ExcavationFittings.BEAM.equals(fitting.item())?"Softwood Beams":"Crude Torches")+" in town storage to continue.";
        return BlockType.getAssetMap().getAsset(fitting.item())==null?"The fitting is unavailable.":null;
    }
    public static boolean placeFitting(World world,TownRecord town,ApprenticeWorkplace site,org.joml.Vector3d worker) {
        var plan=site.excavation();var fitting=plan.nextFitting();
        if(worker==null||fitting==null||issue(world,town,site,1)!=null||worker.distanceSquared(fitting.point().x()+.5,fitting.point().y()+.5,fitting.point().z()+.5)>16)return false;
        var p=fitting.point();var type=BlockType.getAssetMap().getAsset(fitting.item());
        var boxes=com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes.getAssetMap().getAsset(type.getHitboxTypeIndex());
        if(boxes==null)return false;
        var box=boxes.get(fitting.rotation()).getBoundingBox();
        // Native fittings must occupy only their reviewed cell, and never intersect a citizen or player.
        if(box.min.x<-.000001||box.min.y<-.000001||box.min.z<-.000001||box.max.x>1.000001||box.max.y>1.000001||box.max.z>1.000001)return false;
        boolean[] occupied={false};var store=world.getEntityStore().getStore();
        var transformType=com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType();
        var boundsType=com.hypixel.hytale.server.core.modules.entity.component.BoundingBox.getComponentType();
        store.forEachChunk(com.hypixel.hytale.component.query.Query.and(transformType,boundsType),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++) {
                var at=chunk.getComponent(i,transformType).getPosition();var b=chunk.getComponent(i,boundsType).getBoundingBox();
                if(at.x+b.max.x>p.x()+box.min.x&&at.x+b.min.x<p.x()+box.max.x&&at.y+b.max.y>p.y()+box.min.y&&at.y+b.min.y<p.y()+box.max.y
                    &&at.z+b.max.z>p.z()+box.min.z&&at.z+b.min.z<p.z()+box.max.z){occupied[0]=true;return;}
            }
        });
        return !occupied[0]&&plan.fit(plan.id(),fitting,()->town.getWarehouse().placeFromStock(fitting.item(),
            ()->ChunkSectionBlockUtil.setBlockByKey(world,p.x(),p.y(),p.z(),fitting.item(),fitting.rotation(),0)));
    }
    public static Map<String,Integer> drops(ExcavationPlan.Cell cell) {
        var type=BlockType.getAssetMap().getAsset(cell.block());if(type==null||skill(type.getId())==0)return Map.of();
        var gathering=type.getGathering();var breaking=gathering==null?null:gathering.getBreaking();
        List<ItemStack> rolled;
        if(breaking!=null&&breaking.getDropListId()!=null)rolled=ItemModule.get().getRandomItemDrops(breaking.getDropListId());
        else {String item=breaking==null?type.getItem()==null?null:type.getItem().getId():breaking.getItemId();if(item==null&&type.getItem()!=null)item=type.getItem().getId();if(item==null)return Map.of();rolled=List.of(new ItemStack(item,breaking==null?1:Math.max(1,breaking.getQuantity())));}
        var result=new LinkedHashMap<String,Integer>();if(rolled!=null)for(var item:rolled)if(!ItemStack.isEmpty(item))result.merge(item.getItemId(),item.getQuantity(),Math::addExact);
        return result;
    }
    public static PlotInstance mine(TownRecord town,AetherhavenPlugin plugin){return town.findCompletePlotWithConstruction(plugin.getConstructionCatalog(),AetherhavenConstants.CONSTRUCTION_PLOT_MINERS_HUT);}
    public static boolean room(TownRecord town,AetherhavenPlugin plugin,ExcavationPlan plan,Map<String,Integer> items) {
        var plot=town.findPlotById(plan.mine());if(plot==null||plot.getState()!=PlotInstanceState.COMPLETE||!plugin.getConstructionCatalog().matchesGameplayConstruction(plot.getConstructionId(),AetherhavenConstants.CONSTRUCTION_PLOT_MINERS_HUT))return false;
        var state=town.getOrCreatePlotProduction(plot.getPlotId());var entry=ProductionEffectiveCatalog.effective(plugin.getProductionCatalog(),plugin.getWorkplaceUnlockCatalog(),AetherhavenConstants.CONSTRUCTION_PLOT_MINERS_HUT,state);
        return entry!=null&&items.entrySet().stream().allMatch(e->WorkplaceProductionUpgrades.effectiveMaxStorage(state,entry,e.getKey())-state.getAmount(e.getKey())>=e.getValue());
    }
    public static double duration(ExcavationPlan.Cell cell){return 4+2*cell.skill();}
    public static boolean excavate(World world,TownRecord town,ApprenticeWorkplace site,int skill,WarehouseBatch.Point standing,Runnable changed) {
        var plan=site.excavation();var plugin=AetherhavenPlugin.get();if(issue(world,town,site,skill)!=null||plugin==null||!room(town,plugin,plan,plan.drops()))return false;
        var id=plan.id();var p=plan.next().point();
        if(!ExcavationPlan.withinPickupReach(standing,p)||!com.hexvane.aetherhaven.autonomy.VillagerBlockUtil.isNpcStandColumn(world,standing.x(),standing.y(),standing.z()))return false;
        if(!plan.begin(id,duration(plan.next()),standing,()->ChunkSectionBlockUtil.breakBlock(world,p.x(),p.y(),p.z(),0)))return false;
        changed.run();world.execute(()->{if(settle(world,site))changed.run();});return true;
    }
    public static boolean settle(World world,ApprenticeWorkplace site) {
        var plan=site.excavation();if(!plan.pending()||plan.next()==null)return false;
        var g=observe(world,plan.next().point());return plan.settle(plan.id(),g.loaded()?g.empty():null);
    }
    public static boolean deliver(World world,TownRecord town,AetherhavenPlugin plugin,ApprenticeWorkplace site,WarehouseBatch.Point target) {
        var plan=site.excavation();var plot=town.findPlotById(plan.mine());
        if(!ProductionStorageLocator.valid(world,plot,target)||!room(town,plugin,plan,plan.cargo()))return false;
        return plan.delivered(plan.id(),()->{var state=town.getOrCreatePlotProduction(plot.getPlotId());var entry=ProductionEffectiveCatalog.effective(plugin.getProductionCatalog(),plugin.getWorkplaceUnlockCatalog(),AetherhavenConstants.CONSTRUCTION_PLOT_MINERS_HUT,state);
            state.markExcavationOutput();for(var e:plan.cargo().entrySet())state.addAmount(e.getKey(),e.getValue(),WorkplaceProductionUpgrades.effectiveMaxStorage(state,entry,e.getKey()));town.getWarehouse().markProductionWithdrawal();return true;});
    }
}
