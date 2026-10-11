package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch;
import com.hexvane.aetherhaven.workplace.FoodSiteService;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.*;

/** Explicit crop families, loaded field inspection and native regrowth. No natural terrain replacement. */
public final class NativeFarmCrops {
    private NativeFarmCrops() {}
    public static final String LIFE_ESSENCE="Ingredient_Life_Essence";
    public record Crop(String block,String output) {}
    private static final Map<String,Crop> CROPS=new java.util.concurrent.ConcurrentHashMap<>();
    static {
        for(String name:List.of("Wheat","Lettuce","Carrot","Corn","Cauliflower","Turnip","Aubergine",
                "Pumpkin","Tomato","Chilli","Cotton","Rice","Onion","Potato"))
            register(new Crop("Plant_Crop_"+name+"_Block","Plant_Crop_"+name+"_Item"));
    }
    public static void register(Crop crop) {
        Objects.requireNonNull(crop);
        if(crop.block()==null||crop.block().isBlank()||crop.output()==null||crop.output().isBlank())throw new IllegalArgumentException("Crop IDs are required");
        var old=CROPS.putIfAbsent(crop.block(),crop);
        if(old!=null&&!old.equals(crop))throw new IllegalArgumentException("Crop already registered: "+crop.block());
    }
    public static List<Crop> crops(){return CROPS.values().stream().sorted(Comparator.comparing(Crop::block)).toList();}
    public static boolean supportedOutput(String item){return LIFE_ESSENCE.equals(item)||NativeDruidCrops.supported(item)||item!=null&&CROPS.values().stream().anyMatch(c->c.output().equals(item));}
    public static Crop crop(BlockType block) {
        if(block==null)return null;
        var crop=CROPS.get(block.getId());
        String base=block.getDefaultStateKey();
        return crop!=null?crop:base==null?null:CROPS.get(base);
    }
    public record Review(List<FieldProductionState.Bed> beds,String issue) {
        public Review {beds=List.copyOf(beds);}
        public boolean ready(){return issue==null&&!beds.isEmpty();}
    }
    public static Review review(World world,TownRecord town,PlotInstance plot) {
        if(NativeDruidCrops.isPlot(plot))return NativeDruidCrops.review(world,town,plot);
        if(!world.isInThread())throw new IllegalStateException("Field review requires the world thread");
        var f=plot.toFootprint();
        long width=(long)f.getMaxX()-f.getMinX()+1,height=(long)f.getMaxY()-f.getMinY()+1,depth=(long)f.getMaxZ()-f.getMinZ()+1;
        if(width<=0||height<=0||depth<=0||width>1024||height>320||depth>1024)return new Review(List.of(),"This farm is too large for a field review.");
        long volume=width*height*depth;
        if(volume<=0||volume>262144)return new Review(List.of(),"This farm is too large for a field review.");
        for(int x=ChunkUtil.chunkCoordinate(f.getMinX());x<=ChunkUtil.chunkCoordinate(f.getMaxX());x++)
            for(int z=ChunkUtil.chunkCoordinate(f.getMinZ());z<=ChunkUtil.chunkCoordinate(f.getMaxZ());z++)
                if(!TownTerritoryClaims.contains(town,x,z)||ChunkSectionBlockUtil.worldChunkIfTicking(world,ChunkUtil.indexChunk(x,z))==null)
                    return new Review(List.of(),"Visit the whole farm inside town borders to review its beds.");
        var beds=new ArrayList<FieldProductionState.Bed>();
        for(int x=f.getMinX();x<=f.getMaxX();x++)for(int z=f.getMinZ();z<=f.getMaxZ();z++)for(int y=f.getMinY();y<=f.getMaxY();y++) {
            var p=new WarehouseBatch.Point(x,y,z);var section=ChunkSectionBlockUtil.blockSectionAt(world,x,y,z);
            if(section==null||section.getFiller(x,y,z)!=0)continue;
            var type=ChunkSectionBlockUtil.blockType(world,x,y,z);var crop=crop(type);
            if(crop==null||type.getFarming()==null||excluded(town,p))continue;
            var bed=new FieldProductionState.Bed(p,crop.block(),crop.output());
            if(!usable(world,town,plot,bed))continue;
            beds.add(bed);
            if(beds.size()>FieldProductionState.MAX_BEDS)return new Review(List.of(),"Keep no more than 75 crop beds inside this farm.");
        }
        return new Review(beds,beds.isEmpty()?"Plant crops on the farm's tilled beds, then review them here.":null);
    }
    private static boolean excluded(TownRecord town,WarehouseBatch.Point p) {
        return town.getApprenticeWorkplaces().sites().stream().anyMatch(site->site.area().contains(p.x(),p.y(),p.z()));
    }
    public static boolean usable(World world,TownRecord town,PlotInstance plot,FieldProductionState.Bed bed) {
        if(NativeDruidCrops.supported(bed.crop()))return NativeDruidCrops.isPlot(plot)&&NativeDruidCrops.usable(world,town,plot,bed);
        var p=bed.point();
        if(!plot.containsWorldBlock(p.x(),p.y(),p.z())||!TownTerritoryClaims.contains(town,ChunkUtil.chunkCoordinate(p.x()),ChunkUtil.chunkCoordinate(p.z()))
                ||excluded(town,p)||ChunkSectionBlockUtil.worldChunkIfTicking(world,p.x(),p.z())==null)return false;
        var section=ChunkSectionBlockUtil.blockSectionAt(world,p.x(),p.y(),p.z());
        if(section==null||section.getFiller(p.x(),p.y(),p.z())!=0)return false;
        var crop=crop(ChunkSectionBlockUtil.blockType(world,p.x(),p.y(),p.z()));
        return crop!=null&&crop.block().equals(bed.crop())&&crop.output().equals(bed.output());
    }
    public static boolean ripe(World world,FieldProductionState.Bed bed) {
        if(NativeDruidCrops.supported(bed.crop()))return NativeDruidCrops.ripe(world,bed);
        var p=bed.point();var block=ChunkSectionBlockUtil.blockType(world,p.x(),p.y(),p.z());
        var crop=crop(block);
        return crop!=null&&crop.block().equals(bed.crop())&&block.getFarming()!=null
            &&block.getGathering()!=null&&block.getGathering().getHarvest()!=null;
    }
    public static boolean needsWater(World world,FieldProductionState.Bed bed) {
        if(NativeDruidCrops.supported(bed.crop()))return false;
        var p=bed.point();var ref=ChunkSectionBlockUtil.blockEntityRefAt(world,p.x(),p.y()-1,p.z());
        if(ref==null||!ref.isValid())return false;
        var soil=ref.getStore().getComponent(ref,com.hypixel.hytale.builtin.adventure.farming.states.TilledSoilBlock.getComponentType());
        if(soil==null)return false;
        var now=world.getEntityStore().getStore().getResource(com.hypixel.hytale.server.core.modules.time.WorldTimeResource.getResourceType()).getGameTime();
        return soil.getWateredUntil()==null||soil.getWateredUntil().isBefore(now.plusSeconds(4*60*60));
    }
    public static boolean harvest(World world,TownRecord town,PlotInstance plot,PlotProductionState production,FieldProductionState.Bed bed) {
        return harvest(world,town,plot,production,bed,()->{});
    }
    public static boolean harvest(World world,TownRecord town,PlotInstance plot,PlotProductionState production,FieldProductionState.Bed bed,Runnable save) {
        return harvest(world,town,plot,production,bed,Set.of(bed.output()),save);
    }
    public static boolean harvest(World world,TownRecord town,PlotInstance plot,PlotProductionState production,FieldProductionState.Bed bed,Set<String> outputs,Runnable save) {
        return harvest(world,town,plot,production,bed,outputs,0,save);
    }
    /**
     * @param essenceRoom how much more Life Essence this farm can store. Every harvest brings some back while there is
     *                    room, whether or not Life Essence is one of the farm's chosen outputs.
     */
    public static boolean harvest(World world,TownRecord town,PlotInstance plot,PlotProductionState production,FieldProductionState.Bed bed,Set<String> outputs,long essenceRoom,Runnable save) {
        if(NativeDruidCrops.supported(bed.crop()))return NativeDruidCrops.harvest(world,town,plot,production,bed,outputs,save);
        if(com.hexvane.aetherhaven.mastery.CoreProductionRequirements.issueForKind(town,"farmer",bed.output())!=null)return false;
        if(production.field().cargo()!=null||production.field().pending()!=null||!usable(world,town,plot,bed)||!ripe(world,bed))return false;
        var p=bed.point();var block=ChunkSectionBlockUtil.blockType(world,p.x(),p.y(),p.z());var config=block.getFarming();
        String next=config.getStageSetAfterHarvest()!=null?config.getStageSetAfterHarvest():config.getStartingStageSet();
        var stages=config.getStages().get(next);
        if(stages==null||stages.length==0||!(stages[0] instanceof com.hypixel.hytale.builtin.adventure.farming.config.stages.BlockStateFarmingStageData state))return false;
        var regrown=block.getBlockForState(state.getState());
        if(regrown==null||regrown.getId().equals(block.getId())||regrown.getGathering()!=null&&regrown.getGathering().getHarvest()!=null)return false;
        var yields=harvestYields(block,bed,outputs,essenceRoom);
        boolean begun=production.field().beginHarvest(bed,regrown.getId(),yields,()->FoodSiteService.regrowCrop(world,p,bed.crop()));
        if(begun) {
            UUID claim=production.field().pending().id();
            // BlockStateFarmingStageData enqueues its block mutation first. This runs after it.
            world.execute(()->{if(production.field().pending()!=null&&claim.equals(production.field().pending().id())&&settleHarvest(world,production))save.run();});
        }
        return begun;
    }
    /** Also used after reload. It observes only already simulated cells and never applies a second crop transition. */
    public static boolean settleHarvest(World world,PlotProductionState production) {
        var pending=production.field().pending();if(pending==null)return false;
        var p=pending.bed().point();
        if(ChunkSectionBlockUtil.worldChunkIfTicking(world,p.x(),p.z())==null||ChunkSectionBlockUtil.blockSectionAt(world,p.x(),p.y(),p.z())==null)return false;
        var actual=ChunkSectionBlockUtil.blockType(world,p.x(),p.y(),p.z());
        if(actual!=null&&NativeDruidCrops.supported(pending.bed().crop())) {
            var crop=com.hexvane.aetherhaven.workplace.AlchemicalCrop.fromBlock(actual.getId());
            if(crop!=null&&crop.plantedBlock().equals(pending.bed().crop())&&!crop.item().equals(actual.getId()))
                return production.field().settleHarvest(pending.id(),pending.regrownBlock());
        }
        return actual!=null&&production.field().settleHarvest(pending.id(),actual.getId());
    }
    /** Roll the ripe plant's harvest table once. Seeds and unrelated drops are not town outputs. */
    public static Map<String,Integer> harvestYields(BlockType block,FieldProductionState.Bed bed,Set<String> outputs) {
        return harvestYields(block,bed,outputs,0);
    }
    /**
     * Life Essence comes with every crop harvest. A crop that drops none still gives one, and what is taken never
     * exceeds the room left for it, so the farmer is never left holding essence that will not fit.
     *
     * @param chosen what the harvest gives from the farm's chosen outputs
     * @param rolledEssence the essence the plant dropped this time, chosen or not
     * @param essenceRoom how much more essence the farm can store
     */
    static Map<String,Integer> withEssence(Map<String,Integer> chosen,int rolledEssence,long essenceRoom) {
        if(chosen.containsKey(LIFE_ESSENCE)||essenceRoom<1)return chosen;
        var result=new LinkedHashMap<>(chosen);
        result.put(LIFE_ESSENCE,(int)Math.min(essenceRoom,Math.max(1,rolledEssence)));
        return result;
    }
    public static Map<String,Integer> harvestYields(BlockType block,FieldProductionState.Bed bed,Set<String> outputs,long essenceRoom) {
        var harvest=block.getGathering()==null?null:block.getGathering().getHarvest();
        if(harvest==null)return Map.of();
        var module=com.hypixel.hytale.server.core.modules.item.ItemModule.get();
        var rolled=harvest.getDropListId()!=null?module.getRandomItemDrops(harvest.getDropListId()):
            harvest.getItemId()==null?List.<com.hypixel.hytale.server.core.inventory.ItemStack>of():List.of(new com.hypixel.hytale.server.core.inventory.ItemStack(harvest.getItemId(),1));
        var result=new LinkedHashMap<String,Integer>();int essence=0;
        if(rolled!=null)for(var stack:rolled) {
            if(com.hypixel.hytale.server.core.inventory.ItemStack.isEmpty(stack))continue;
            if(LIFE_ESSENCE.equals(stack.getItemId()))essence=Math.addExact(essence,stack.getQuantity());
            if(outputs.contains(stack.getItemId())&&(bed.output().equals(stack.getItemId())||LIFE_ESSENCE.equals(stack.getItemId())))
                result.merge(stack.getItemId(),stack.getQuantity(),Math::addExact);
        }
        return withEssence(result,essence,essenceRoom);
    }
    public static Set<String> selectedOutputs(PlotProductionState p,ProductionCatalog.Entry entry,FieldProductionState.Bed bed) {
        var result=new LinkedHashSet<String>();
        for(String item:List.of(bed.output(),LIFE_ESSENCE))if(selected(p,entry,item))result.add(item);
        return Collections.unmodifiableSet(result);
    }
    public static boolean canTend(PlotProductionState p,ProductionCatalog.Entry entry,FieldProductionState.Bed bed) {
        var outputs=selectedOutputs(p,entry,bed);
        return !outputs.isEmpty()&&outputs.stream().allMatch(item->p.getAmount(item)<WorkplaceProductionUpgrades.effectiveMaxStorage(p,entry,item));
    }
    public static boolean selected(PlotProductionState production,ProductionCatalog.Entry entry,String item) {
        for(int slot=0;slot<WorkplaceProductionUpgrades.slotCount(production);slot++)
            if(item.equals(entry.itemAtCursor(production.getSlotCursor(slot))))return true;
        return false;
    }
}
