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
    /** Overrides specify ore skill requirements; other ordinary blocks use Mining 1. */
    public static void register(String block,int skill){if(block==null||block.isBlank()||skill<1||skill>10)throw new IllegalArgumentException("Excavation material requires a block and skill from 1 to 10");MATERIALS.put(block,skill);}
    public static int skill(String block){return block==null||block.isBlank()?0:MATERIALS.getOrDefault(block,1);}
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
            int skill=skill(ground.block());
            cells.add(new ExcavationPlan.Cell(p,ground.block(),ground.rotation(),skill));
        }
        return new Review(cells,cells.isEmpty()?"There are no blocks left to excavate in this tunnel.":null);
    }
    public static String issue(World world,TownRecord town,ApprenticeWorkplace site,int workerSkill) {
        var plan=site.excavation();var cell=plan.next();
        if(plan.id()==null)return "Review a tunnel before starting work.";
        String connectionIssue=MineshaftNetwork.connectionIssue(town.getApprenticeWorkplaces(),site);if(connectionIssue!=null)return connectionIssue;
        if(plan.edited())return "The tunnel was edited. Review its remaining blocks again.";
        if(plan.paused())return "Excavation paused.";
        if(!site.area().equals(plan.area())||site.type()!=TownsfolkWorkType.MINING)return "The workplace changed. Review the tunnel again.";
        if(!town.hasMemberOrOwner(plan.author())||!town.playerCanManageConstructions(plan.author())||!town.playerCanBreakBlocks(plan.author()))return "The player who planned this tunnel no longer has permission to excavate.";
        if(cell==null)return fittingIssue(world,town,site);
        if(workerSkill<cell.skill())return "Needs Mining skill "+cell.skill()+" for the next block.";
        if(!allowed(world,town,site,cell.point()))return "The next block is inside a building plot or outside town borders.";
        var g=observe(world,cell.point());if(!g.loaded())return "The next tunnel section is not being simulated.";
        if(g.protectedCell()||g.filler()!=0||!cell.block().equals(g.block())||cell.rotation()!=g.rotation())return "The next block changed. Review the tunnel again.";
        return null;
    }
    /** The same dispatch gates are used by workers, the journal and the tunnel review. */
    public static String workIssue(World world,TownRecord town,ApprenticeWorkplace site,AetherhavenPlugin plugin,int workerSkill) {
        var plan=site.excavation();
        if(plan.id()==null)return "Start the reviewed tunnel before assigning work.";
        if(plan.pending())return null;
        if(!plan.cargo().isEmpty())return null; // Cargo can always return to the workplace buffer.

        if(site.minePrerequisiteId()!=null){
            var previous=town.getApprenticeWorkplaces().find(site.minePrerequisiteId());
            if(previous==null)return "The connecting tunnel is missing.";
            var connection=previous.excavation();
            if(!connection.passageReady())return "Waiting for the connecting tunnel to be finished.";
        }
        String problem=issue(world,town,site,workerSkill);
        if(problem!=null&&plan.next()==null&&plan.nextFitting()==null&&!plan.output().isEmpty())return "Excavation complete. Collect stored output here or through the warehouse. Couriers can use it.";
        return problem;
    }
    public static WarehouseBatch.Point bufferPoint(TownRecord town,ApprenticeWorkplace site){
        var base=town.getApprenticeWorkplaces().mineBase(site);var a=(base==null?site:base).area();
        return new WarehouseBatch.Point(a.minX()+(a.maxX()-a.minX())/2,a.floorY(),a.minZ()+(a.maxZ()-a.minZ())/2);
    }
    public static Map<String,Integer> availableOutput(TownRecord town,AetherhavenPlugin plugin,ExcavationPlan plan){
        var result=new LinkedHashMap<String,Integer>();var plot=town.findPlotById(plan.mine());
        if(plot==null||plot.getState()!=PlotInstanceState.COMPLETE||!plugin.getConstructionCatalog().matchesGameplayConstruction(plot.getConstructionId(),AetherhavenConstants.CONSTRUCTION_PLOT_MINERS_HUT))return result;
        var state=town.getOrCreatePlotProduction(plot.getPlotId());var entry=ProductionEffectiveCatalog.effective(plugin.getProductionCatalog(),plugin.getWorkplaceUnlockCatalog(),AetherhavenConstants.CONSTRUCTION_PLOT_MINERS_HUT,state);
        if(entry==null)return result;
        for(var e:plan.output().entrySet()){
            long room=WorkplaceProductionUpgrades.effectiveMaxStorage(state,entry,e.getKey())-state.getAmount(e.getKey());
            if(room>0)result.put(e.getKey(),(int)Math.min(e.getValue(),room));
        }
        return result;
    }
    public static boolean outputAvailable(World world,TownRecord town,AetherhavenPlugin plugin,ExcavationPlan plan){
        return !plan.output().isEmpty()&&ProductionStorageLocator.find(world,town.findPlotById(plan.mine()),System.currentTimeMillis())!=null&&!availableOutput(town,plugin,plan).isEmpty();
    }
    private static String fittingIssue(World world,TownRecord town,ApprenticeWorkplace site) {
        var plan=site.excavation();var fitting=plan.nextFitting();
        if(fitting==null)return "Tunnel complete. Review another tunnel to continue.";
        if(!town.playerCanPlaceBlocks(plan.author())||!town.playerCanOpenContainers(plan.author()))return "Fittings need building and town storage permission.";
        var p=fitting.point();
        if(!allowed(world,town,site,p))return "Keep fittings inside town and outside building plots.";
        var ground=observe(world,p);if(!ground.loaded())return "Visit the tunnel before adding fittings.";
        if(!ground.empty()||ground.protectedCell()||ground.filler()!=0)return "Clear the next fitting position before continuing.";
        var support=observe(world,fitting.support()!=null?fitting.support():new WarehouseBatch.Point(p.x(),p.y()-1,p.z()));
        var supportType=support.block()==null?null:BlockType.getAssetMap().getAsset(support.block());
        if(!support.loaded()||(fitting.support()!=null||fitting.rotation()==0)&&(support.empty()||supportType==null||supportType.getMaterial()!=com.hypixel.hytale.protocol.BlockMaterial.Solid))return "The next fitting needs its supporting floor, wall or ceiling.";
        if(!town.getWarehouse().hasUnreservedStock(fitting.item()))return "Digging complete. Supply "+(ExcavationFittings.BEAM.equals(fitting.item())?"Softwood Beams":ExcavationFittings.LANTERN.equals(fitting.item())?"Lanterns":ExcavationFittings.RAIL.equals(fitting.item())?"Rails":"Crude Torches")+" in town storage for the remaining fittings.";
        return BlockType.getAssetMap().getAsset(fitting.block())==null?"The fitting is unavailable.":null;
    }
    public static boolean placeFitting(World world,TownRecord town,ApprenticeWorkplace site,org.joml.Vector3d worker) {
        var plan=site.excavation();var fitting=plan.nextFitting();
        // Use the same reach as approach selection and arrival, including taller stairs.
        if(worker==null||fitting==null||issue(world,town,site,1)!=null||!plan.withinWorkReach(ExcavationApproach.feet(worker),fitting.point()))return false;
        var p=fitting.point();var type=BlockType.getAssetMap().getAsset(fitting.block());
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
            ()->ChunkSectionBlockUtil.setBlockByKey(world,p.x(),p.y(),p.z(),fitting.block(),fitting.rotation(),0)));
    }
    public static Map<String,Integer> drops(ExcavationPlan.Cell cell) {
        var type=BlockType.getAssetMap().getAsset(cell.block());if(type==null)return Map.of();
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
        return excavate(world,town,site,skill,standing,null,null,changed);
    }
    public static boolean excavate(World world,TownRecord town,ApprenticeWorkplace site,int skill,org.joml.Vector3d position,
        com.hypixel.hytale.math.shape.Box bounds,Runnable changed){
        return excavate(world,town,site,skill,ExcavationApproach.feet(position),position,bounds,changed);
    }
    private static boolean excavate(World world,TownRecord town,ApprenticeWorkplace site,int skill,WarehouseBatch.Point standing,
        org.joml.Vector3d position,com.hypixel.hytale.math.shape.Box bounds,Runnable changed){
        var plan=site.excavation();var plugin=AetherhavenPlugin.get();if(issue(world,town,site,skill)!=null||plugin==null)return false;
        var id=plan.id();var p=plan.next().point();
        boolean supported=position!=null&&bounds!=null
            ?ExcavationApproach.supported(position,bounds.min.x,bounds.max.x,bounds.min.z,bounds.max.z,
                cell->com.hexvane.aetherhaven.autonomy.VillagerBlockUtil.isNpcStandColumn(world,cell.x(),cell.y(),cell.z()))
            :com.hexvane.aetherhaven.autonomy.VillagerBlockUtil.isNpcStandColumn(world,standing.x(),standing.y(),standing.z());
        if(!plan.withinWorkReach(standing,p)||!supported||!clearReach(world,position==null?new org.joml.Vector3d(standing.x()+.5,standing.y(),standing.z()+.5):position,p))return false;
        if(!plan.begin(id,duration(plan.next()),standing,()->ChunkSectionBlockUtil.breakBlock(world,p.x(),p.y(),p.z(),0)))return false;
        // The block mutation is synchronous. Settle before releasing this worker's assignment.
        settle(world,site);repairFloors(world,town,site,standing);changed.run();return true;
    }
    public static boolean clearReach(World world,org.joml.Vector3d position,WarehouseBatch.Point target){
        return ExcavationApproach.clearReach(position,target,p->{
            var g=observe(world,p);if(!g.loaded()||g.protectedCell()||g.filler()!=0)return false;
            var type=g.block()==null?null:BlockType.getAssetMap().getAsset(g.block());
            if(g.empty()||type!=null&&type.getMaterial()!=com.hypixel.hytale.protocol.BlockMaterial.Solid)return true;
            if(type==null)return false;
            var boxes=com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes.getAssetMap().getAsset(type.getHitboxTypeIndex());
            if(boxes==null)return false;
            var rotated=boxes.get(g.rotation());
            if(!rotated.hasDetailBoxes())return !ExcavationApproach.intersects(position,target,p,rotated.getBoundingBox());
            for(var box:rotated.getDetailBoxes())if(ExcavationApproach.intersects(position,target,p,box))return false;
            return true;
        });
    }
    /** Fill exposed gaps and soft plants in the planned floor near the attending worker. */
    public static void repairFloors(World world,TownRecord town,ApprenticeWorkplace site,WarehouseBatch.Point worker){
        var plan=site.excavation();
        if(plan.id()==null||plan.paused()||plan.edited()||!town.playerCanPlaceBlocks(plan.author()))return;
        for(var p:ExcavationPlan.floors(plan.area(),plan.entrance(),plan.layout())){
            var air=new WarehouseBatch.Point(p.x(),p.y()+1,p.z());
            if(!plan.withinWorkReach(worker,air)||!allowed(world,town,site,air)
                ||town.getPlotInstances().stream().anyMatch(plot->plot.toFootprint().containsBlock(p.x(),p.y(),p.z()))
                ||PROTECTIONS.stream().anyMatch(rule->!rule.permits(world,town,p)))continue;
            var g=observe(world,p);var above=observe(world,air);
            if(!g.loaded()||g.protectedCell()||g.filler()!=0||!above.loaded()||!above.empty())continue;
            var type=g.block()==null?null:BlockType.getAssetMap().getAsset(g.block());
            if(!g.empty()&&(type==null||type.getGathering()==null||!type.getGathering().isSoft()))continue;
            if(!clearReach(world,new org.joml.Vector3d(worker.x()+.5,worker.y(),worker.z()+.5),air))continue;
            ChunkSectionBlockUtil.setBlockByKey(world,p.x(),p.y(),p.z(),"Rock_Stone_Cobble",0,0);
        }
    }
    public static boolean settle(World world,ApprenticeWorkplace site) {
        var plan=site.excavation();if(!plan.pending()||plan.next()==null)return false;
        var g=observe(world,plan.next().point());return plan.settle(plan.id(),g.loaded()?g.empty():null);
    }
    public static boolean deliver(World world,TownRecord town,AetherhavenPlugin plugin,ApprenticeWorkplace site,WarehouseBatch.Point target) {
        var plan=site.excavation();var plot=town.findPlotById(plan.mine());
        if(!plan.haulingOutput())return false;
        var accepted=new LinkedHashMap<String,Integer>();
        if(ProductionStorageLocator.valid(world,plot,target)&&plot.getState()==PlotInstanceState.COMPLETE) {
            var state=town.getOrCreatePlotProduction(plot.getPlotId());var entry=ProductionEffectiveCatalog.effective(plugin.getProductionCatalog(),plugin.getWorkplaceUnlockCatalog(),AetherhavenConstants.CONSTRUCTION_PLOT_MINERS_HUT,state);
            if(entry!=null){
                for(var e:plan.cargo().entrySet()){
                    long max=WorkplaceProductionUpgrades.effectiveMaxStorage(state,entry,e.getKey());
                    int amount=(int)Math.min(e.getValue(),Math.max(0,max-state.getAmount(e.getKey())));
                    if(amount>0){state.addAmount(e.getKey(),amount,max);accepted.put(e.getKey(),amount);}
                }
                if(!accepted.isEmpty()){state.markExcavationOutput();town.getWarehouse().markProductionWithdrawal();}
            }
        }
        return plan.acceptOutputDelivery(plan.id(),accepted);
    }
}
