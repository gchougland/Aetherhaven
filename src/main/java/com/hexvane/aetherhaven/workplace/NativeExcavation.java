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
        return review(world,town,site,entrance,layout,Set.of());
    }
    /**
     * @param left cells that belong to passages already there. They are not this tunnel's to dig, so whatever stands in
     *             them, a wall torch of the passage being left for one, is no reason to refuse it.
     */
    public static Review review(World world,TownRecord town,ApprenticeWorkplace site,ExcavationPlan.Entrance entrance,ExcavationPlan.Layout layout,
        Set<WarehouseBatch.Point> left) {
        var tunnel=ExcavationPlan.tunnel(site.area(),entrance,layout);
        if(tunnel.isEmpty())return new Review(List.of(),"Choose a direction and a length for this tunnel.");
        var cells=new ArrayList<ExcavationPlan.Cell>();
        for(var p:tunnel) {
            if(left.contains(p))continue;
            String where=" at "+p.x()+", "+p.y()+", "+p.z();
            if(!allowed(world,town,site,p))return new Review(cells,protection(town,site,p)+where+" is in this tunnel's way. Shorten the tunnel or choose another direction.");
            var ground=observe(world,p);if(!ground.loaded())return new Review(cells,"The ground"+where+" is too far away to see. Shorten the tunnel or stand closer to it.");
            if(ground.protectedCell()||ground.filler()!=0)return new Review(cells,(ground.empty()?"Water":label(ground.block()))+where+" is in this tunnel's way. Move it, shorten the tunnel or choose another direction.");
            if(ground.empty())continue;
            int skill=skill(ground.block());
            cells.add(new ExcavationPlan.Cell(p,ground.block(),ground.rotation(),skill));
        }
        return new Review(cells,cells.isEmpty()?NOTHING_LEFT:null);
    }
    public static final String NOTHING_LEFT="There are no blocks left to excavate in this tunnel.";
    /** The item a dug tunnel is waiting for because the town has none to spare, or null. */
    public static String awaitedFitting(TownRecord town,ApprenticeWorkplace site) {
        var plan=site.excavation();var fitting=plan.id()==null||plan.paused()||plan.next()!=null?null:plan.nextFitting();
        return fitting==null||town.getWarehouse().hasUnreservedStock(fitting.item())?null:fitting.item();
    }
    /** A block's or item's name as players see it. */
    public static String label(String id) {
        if(id==null)return "Something";
        var store=com.hypixel.hytale.server.core.asset.type.item.config.Item.getAssetStore();
        var item=store==null?null:store.getAssetMap().getAsset(id);
        var i18n=com.hypixel.hytale.server.core.modules.i18n.I18nModule.get();
        String name=item==null||i18n==null||item.getTranslationKey()==null?null:i18n.getMessage("en-US",item.getTranslationKey());
        return name==null||name.isBlank()?id.replace('_',' '):name;
    }
    private static String protection(TownRecord town,ApprenticeWorkplace site,WarehouseBatch.Point p) {
        if(!site.area().contains(p.x(),p.y(),p.z()))return "Ground outside this tunnel";
        if(!TownTerritoryClaims.contains(town,ChunkUtil.chunkCoordinate(p.x()),ChunkUtil.chunkCoordinate(p.z())))return "Ground outside the town border";
        if(town.getPlotInstances().stream().anyMatch(plot->plot.toFootprint().containsBlock(p.x(),p.y(),p.z())))return "A building plot";
        return "Protected ground";
    }
    private static boolean furnished(World world,WarehouseBatch.Point p) {
        var components=ChunkSectionBlockUtil.blockComponentSectionAt(world,p.x(),p.y(),p.z());
        int index=ChunkUtil.indexBlock(p.x(),p.y(),p.z());
        return components!=null&&(components.getBlockReference(index)!=null||components.getBlockHolder(index)!=null);
    }
    /**
     * What a planned cell holds right now. Open space, with or without water, is nothing to dig. Anything else is dug as
     * what it is, except what the planner would never have accepted: ground outside the town or inside a building plot,
     * and a block that keeps things, such as a chest or a bench. A cell that is part of a larger block is cleared by
     * breaking that block, when the whole of it may be broken.
     */
    public static ExcavationPlan.Found found(World world,TownRecord town,ApprenticeWorkplace site,WarehouseBatch.Point p) {
        var g=observe(world,p);
        if(!g.loaded())return ExcavationPlan.Found.UNLOADED;
        if(g.empty())return ExcavationPlan.Found.EMPTY;
        if(!allowed(world,town,site,p))return ExcavationPlan.Found.obstacle(protection(town,site,p));
        var base=p;
        if(g.filler()!=0) {
            base=new WarehouseBatch.Point(p.x()-com.hypixel.hytale.server.core.util.FillerBlockUtil.unpackX(g.filler()),
                p.y()-com.hypixel.hytale.server.core.util.FillerBlockUtil.unpackY(g.filler()),p.z()-com.hypixel.hytale.server.core.util.FillerBlockUtil.unpackZ(g.filler()));
            var whole=observe(world,base);
            if(!whole.loaded())return ExcavationPlan.Found.UNLOADED;
            if(!g.block().equals(whole.block())||!allowed(world,town,site,base))return ExcavationPlan.Found.obstacle(label(g.block()));
        }
        if(furnished(world,base))return ExcavationPlan.Found.obstacle(label(g.block()));
        return ExcavationPlan.Found.rock(g.block(),g.rotation(),skill(g.block()),base,g.naturalTerrain());
    }
    /**
     * Brings a tunnel's order up to date with the ground before anyone judges or works it, so a block that was
     * swapped, removed or added since planning never stops the dig. World thread only.
     */
    public static void refresh(World world,TownRecord town,ApprenticeWorkplace site) {
        var plan=site.excavation();
        if(plan.id()==null||plan.pending()||plan.paused()||site.type()!=TownsfolkWorkType.MINING||!site.area().equals(plan.area()))return;
        long now=System.currentTimeMillis();
        java.util.function.Function<WarehouseBatch.Point,ExcavationPlan.Found> look=p->found(world,town,site,p);
        var sites=town.getApprenticeWorkplaces();var base=sites.mineBase(site);
        // Openings made before posts were kept out of doorways are put right once, the first time the passage is looked at.
        if(base!=null&&plan.claimDoorwayCheck())MineshaftNetwork.openDoorways(sites,base,site);
        // A built piece that now stands in a doorway is taken down, if it is still there.
        for(var piece:List.copyOf(plan.unbuild())) {
            var standing=observe(world,piece.point());
            if(standing.loaded())plan.resolveUnbuild(piece,piece.block().equals(standing.block()));
        }
        // Rock that has turned up where this passage meets another is cleared first, whichever of the two is at work.
        if(base!=null&&plan.unfinished()&&plan.junctionCheckDue(now)) {
            for(var p:ordered(plan,MineshaftNetwork.junction(sites,base,site))) {
                var found=found(world,town,site,p);
                if(found.kind()==ExcavationPlan.Found.Kind.ROCK&&found.terrain())plan.requeueFirst(new ExcavationPlan.Cell(p,found.block(),found.rotation(),found.skill()));
            }
        }
        plan.survey(look,now);
        if(plan.sweepDue()) {
            var arrived=arrivedRock(world,town,site);
            if(arrived!=null){plan.sweep(arrived);plan.survey(look,now);}
        }
        if(plan.next()==null)settleFittings(world,town,site);
    }
    /**
     * Blocks this tunnel never digs, however the ground changes: the one under each place someone walks in another
     * passage of its mine. The open space it shares with them is its own to keep clear as much as theirs.
     */
    private static Set<WarehouseBatch.Point> kept(TownRecord town,ApprenticeWorkplace site) {
        var sites=town.getApprenticeWorkplaces();var base=sites.mineBase(site);
        return base==null?Set.of():MineshaftNetwork.walkwaysFor(sites,base,site.id());
    }
    /** The given cells in the order the passage is dug, last first, so that queueing each at the front leaves the nearest in front. */
    private static List<WarehouseBatch.Point> ordered(ExcavationPlan plan,Set<WarehouseBatch.Point> cells) {
        var result=new ArrayList<WarehouseBatch.Point>();
        if(cells.isEmpty())return result;
        for(var p:ExcavationPlan.tunnel(plan.area(),plan.entrance(),plan.layout()))if(cells.contains(p))result.add(p);
        Collections.reverse(result);return result;
    }
    /** Rock, ore or soil now standing in the passage that the order does not list. Null while part of it is unloaded. */
    private static List<ExcavationPlan.Cell> arrivedRock(World world,TownRecord town,ApprenticeWorkplace site) {
        var plan=site.excavation();var kept=kept(town,site);
        var waiting=new HashSet<WarehouseBatch.Point>();for(var cell:plan.blocked())waiting.add(cell.point());
        var arrived=new ArrayList<ExcavationPlan.Cell>();
        for(var p:ExcavationPlan.tunnel(plan.area(),plan.entrance(),plan.layout())) {
            if(kept.contains(p)||waiting.contains(p))continue;
            var found=found(world,town,site,p);
            if(found.kind()==ExcavationPlan.Found.Kind.UNLOADED)return null;
            if(found.kind()==ExcavationPlan.Found.Kind.ROCK&&found.terrain())arrived.add(new ExcavationPlan.Cell(p,found.block(),found.rotation(),found.skill()));
        }
        return arrived;
    }
    /**
     * Passes over fittings that cannot go in: one already standing, a position something else occupies, or one with no
     * floor, wall or ceiling to hold it. Rock that has fallen into a position is queued to be dug first.
     */
    private static void settleFittings(World world,TownRecord town,ApprenticeWorkplace site) {
        var plan=site.excavation();
        for(var fitting=plan.nextFitting();fitting!=null&&plan.next()==null;fitting=plan.nextFitting()) {
            var p=fitting.point();
            if(!allowed(world,town,site,p)){plan.passFitting(fitting,false);continue;}
            var ground=observe(world,p);if(!ground.loaded())return;
            if(!ground.empty()) {
                if(fitting.block().equals(ground.block())){plan.passFitting(fitting,true);continue;}
                var found=found(world,town,site,p);
                if(found.kind()==ExcavationPlan.Found.Kind.UNLOADED)return;
                if(found.kind()==ExcavationPlan.Found.Kind.ROCK&&found.terrain()&&!kept(town,site).contains(p)
                    &&plan.requeue(new ExcavationPlan.Cell(p,found.block(),found.rotation(),found.skill())))return;
                plan.passFitting(fitting,false);continue;
            }
            if(ground.protectedCell()||BlockType.getAssetMap().getAsset(fitting.block())==null){plan.passFitting(fitting,false);continue;}
            var support=observe(world,fitting.support()!=null?fitting.support():new WarehouseBatch.Point(p.x(),p.y()-1,p.z()));
            if(!support.loaded())return;
            var supportType=support.block()==null?null:BlockType.getAssetMap().getAsset(support.block());
            if((fitting.support()!=null||fitting.rotation()==0)&&(support.empty()||supportType==null||supportType.getMaterial()!=com.hypixel.hytale.protocol.BlockMaterial.Solid)){plan.passFitting(fitting,false);continue;}
            return;
        }
    }
    /** What is in the way of the cells the miners had to leave, and how the tunnel carries on. Null when nothing is. */
    public static String obstruction(World world,TownRecord town,ApprenticeWorkplace site) {
        var plan=site.excavation();
        if(plan.id()==null||plan.blocked().isEmpty())return null;
        var first=plan.blocked().getFirst().point();var found=found(world,town,site,first);
        return ExcavationFittings.obstructionLine(found.kind()==ExcavationPlan.Found.Kind.OBSTACLE?found.obstacle():"A block that will not break",first,plan.blocked().size());
    }
    public static String issue(World world,TownRecord town,ApprenticeWorkplace site,int workerSkill) {
        var plan=site.excavation();
        if(plan.id()==null)return "Plan a tunnel on the map before starting work.";
        String connectionIssue=MineshaftNetwork.connectionIssue(town.getApprenticeWorkplaces(),site);if(connectionIssue!=null)return connectionIssue;
        plan.resumeAfterEdit();
        if(plan.paused())return "This tunnel is paused. Choose Resume tunnel in the planner to carry on.";
        if(!site.area().equals(plan.area())||site.type()!=TownsfolkWorkType.MINING)return "This workplace was resized after its tunnel was planned. Give it its old corners again with the Building Staff and the miners carry on.";
        if(!town.hasMemberOrOwner(plan.author())||!town.playerCanManageConstructions(plan.author())||!town.playerCanBreakBlocks(plan.author()))return "The player who planned this tunnel no longer has permission to excavate.";
        refresh(world,town,site);
        var cell=plan.next();
        if(cell==null)return fittingIssue(world,town,site);
        var at=cell.point();
        if(found(world,town,site,at).kind()==ExcavationPlan.Found.Kind.UNLOADED)return "Waiting for a town member to come near this tunnel.";
        if(workerSkill<cell.skill())return "Needs Mining "+cell.skill()+" to dig the "+label(cell.block())+" at "+at.x()+", "+at.y()+", "+at.z()+".";
        return null;
    }
    /** The same dispatch gates are used by workers, the journal and the tunnel review. */
    public static String workIssue(World world,TownRecord town,ApprenticeWorkplace site,AetherhavenPlugin plugin,int workerSkill) {
        var plan=site.excavation();
        if(plan.id()==null)return "Plan a tunnel on the map before assigning work.";
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
    /** The fitting next in line has already been checked by {@link #refresh}: it has room and something to hold it. */
    private static String fittingIssue(World world,TownRecord town,ApprenticeWorkplace site) {
        var plan=site.excavation();var fitting=plan.nextFitting();
        if(fitting==null) {
            String obstruction=obstruction(world,town,site);if(obstruction!=null)return obstruction;
            return "Tunnel complete."+(plan.fittingsSkipped()>0?" "+plan.fittingsSkipped()+(plan.fittingsSkipped()==1?" fitting was":" fittings were")+" left out where something was in the way.":"")+" Plan another tunnel on the map to keep digging.";
        }
        if(!town.playerCanPlaceBlocks(plan.author())||!town.playerCanOpenContainers(plan.author()))return "The player who planned this tunnel needs building and town storage permission before miners can fit it out.";
        var p=fitting.point();
        var ground=observe(world,p);if(!ground.loaded())return "Waiting for a town member to come near this tunnel.";
        if(!town.getWarehouse().hasUnreservedStock(fitting.item())) {
            var stock=town.getWarehouse().craftingStock();
            return ExcavationFittings.waitingLine(fitting.item(),plan.fittingsNeeded(),stock::count,NativeExcavation::label);
        }
        return null;
    }
    public static boolean placeFitting(World world,TownRecord town,ApprenticeWorkplace site,org.joml.Vector3d worker) {
        var plan=site.excavation();
        // Judge the tunnel first: doing so passes over fittings that have nowhere to go.
        if(worker==null||issue(world,town,site,1)!=null)return false;
        var fitting=plan.nextFitting();
        // Use the same reach as approach selection and arrival, including taller stairs.
        if(fitting==null||plan.next()!=null||!plan.withinWorkReach(ExcavationApproach.feet(worker),fitting.point()))return false;
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
        // Miners now dig whatever is in the way. A block with an unusually large haul must not be one they can never start.
        result.replaceAll((item,count)->Math.clamp(count,1,64));
        while(result.size()>8)result.remove(result.lastEntry().getKey());
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
        var plan=site.excavation();var plugin=AetherhavenPlugin.get();if(issue(world,town,site,skill)!=null||plugin==null||plan.next()==null)return false;
        var id=plan.id();var p=plan.next().point();
        // The order now names the block that is really there. A part of a larger block is cleared by breaking its base.
        var found=found(world,town,site,p);if(found.kind()!=ExcavationPlan.Found.Kind.ROCK)return false;
        var at=found.breakAt();
        boolean supported=position!=null&&bounds!=null
            ?ExcavationApproach.supported(position,bounds.min.x,bounds.max.x,bounds.min.z,bounds.max.z,
                cell->com.hexvane.aetherhaven.autonomy.VillagerBlockUtil.isNpcStandColumn(world,cell.x(),cell.y(),cell.z()))
            :com.hexvane.aetherhaven.autonomy.VillagerBlockUtil.isNpcStandColumn(world,standing.x(),standing.y(),standing.z());
        if(!plan.withinWorkReach(standing,p)||!supported||!clearReach(world,position==null?new org.joml.Vector3d(standing.x()+.5,standing.y(),standing.z()+.5):position,p))return false;
        if(!plan.begin(id,duration(plan.next()),standing,()->ChunkSectionBlockUtil.breakBlock(world,at.x(),at.y(),at.z(),0)))return false;
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
        Set<WarehouseBatch.Point> open=null;
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
            // Where this floor would be the open space of another passage, such as the step beside a landing, it stays open.
            if(open==null){open=new HashSet<>();for(var other:town.getApprenticeWorkplaces().tunnels(mineId(town,site)))if(other!=site)open.addAll(ExcavationPlan.tunnel(other.area(),other.excavation().entrance(),other.excavation().layout()));}
            if(open.contains(p))continue;
            ChunkSectionBlockUtil.setBlockByKey(world,p.x(),p.y(),p.z(),"Rock_Stone_Cobble",0,0);
        }
    }
    private static UUID mineId(TownRecord town,ApprenticeWorkplace site){var base=town.getApprenticeWorkplaces().mineBase(site);return (base==null?site:base).id();}
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
