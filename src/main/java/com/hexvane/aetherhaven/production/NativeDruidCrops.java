package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch;
import com.hexvane.aetherhaven.workplace.AlchemicalCrop;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.*;

/** Core Druid perennial beds. Initial plants are supplied by the player; native growth remains authoritative. */
public final class NativeDruidCrops {
    public static final String CONSTRUCTION = "plot_druid";
    private NativeDruidCrops() {}
    public static boolean isPlot(PlotInstance plot) {
        var plugin = AetherhavenPlugin.get();
        return plot != null && (CONSTRUCTION.equals(plot.getConstructionId()) || plugin != null
            && plugin.getConstructionCatalog().matchesGameplayConstruction(plot.getConstructionId(), CONSTRUCTION));
    }
    public static boolean supported(String item) { return AlchemicalCrop.fromBlock(item) != null; }
    private static boolean excluded(TownRecord town, WarehouseBatch.Point p) {
        // Legacy Apprentice plots and their living crops remain owned by their saved orders.
        return town.getApprenticeWorkplaces().sites().stream().anyMatch(site -> site.area().contains(p.x(),p.y(),p.z()));
    }
    public static NativeFarmCrops.Review review(World world,TownRecord town,PlotInstance plot) {
        if(!world.isInThread())throw new IllegalStateException("Conservatory review requires the world thread");
        var f=plot.toFootprint();
        long width=(long)f.getMaxX()-f.getMinX()+1,height=(long)f.getMaxY()-f.getMinY()+1,depth=(long)f.getMaxZ()-f.getMinZ()+1;
        if(width<=0||height<=0||depth<=0||width>1024||height>320||depth>1024||width*height*depth>262144)
            return new NativeFarmCrops.Review(List.of(),"This conservatory is too large for a bed review.");
        for(int x=ChunkUtil.chunkCoordinate(f.getMinX());x<=ChunkUtil.chunkCoordinate(f.getMaxX());x++)
            for(int z=ChunkUtil.chunkCoordinate(f.getMinZ());z<=ChunkUtil.chunkCoordinate(f.getMaxZ());z++)
                if(!TownTerritoryClaims.contains(town,x,z)||ChunkSectionBlockUtil.worldChunkIfTicking(world,ChunkUtil.indexChunk(x,z))==null)
                    return new NativeFarmCrops.Review(List.of(),"Visit the whole conservatory inside town borders to review its beds.");
        var beds=new ArrayList<FieldProductionState.Bed>();
        for(int x=f.getMinX();x<=f.getMaxX();x++)for(int z=f.getMinZ();z<=f.getMaxZ();z++)for(int y=f.getMinY();y<=f.getMaxY();y++) {
            var type=ChunkSectionBlockUtil.blockType(world,x,y,z);
            var crop=type==null?null:AlchemicalCrop.fromBlock(type.getId());
            if(crop==null)continue;
            var bed=new FieldProductionState.Bed(new WarehouseBatch.Point(x,y,z),crop.plantedBlock(),crop.item());
            if(usable(world,town,plot,bed))beds.add(bed);
            if(beds.size()>FieldProductionState.MAX_BEDS)return new NativeFarmCrops.Review(List.of(),"Keep no more than 64 planted beds inside this conservatory.");
        }
        return new NativeFarmCrops.Review(beds,beds.isEmpty()?"Plant alchemical seeds on crystal soil, then review their beds here.":null);
    }
    public static boolean usable(World world,TownRecord town,PlotInstance plot,FieldProductionState.Bed bed) {
        var p=bed.point();
        if(!plot.containsWorldBlock(p.x(),p.y(),p.z())||!TownTerritoryClaims.contains(town,ChunkUtil.chunkCoordinate(p.x()),ChunkUtil.chunkCoordinate(p.z()))
            ||excluded(town,p)||ChunkSectionBlockUtil.worldChunkIfTicking(world,ChunkUtil.indexChunkFromBlock(p.x(),p.z()))==null)return false;
        var section=ChunkSectionBlockUtil.blockSectionAt(world,p.x(),p.y(),p.z());
        var soil=ChunkSectionBlockUtil.blockType(world,p.x(),p.y()-1,p.z());
        var plant=ChunkSectionBlockUtil.blockType(world,p.x(),p.y(),p.z());
        var crop=plant==null?null:AlchemicalCrop.fromBlock(plant.getId());
        return section!=null&&section.getFiller(p.x(),p.y(),p.z())==0&&soil!=null&&AlchemicalCrop.SOIL.equals(soil.getId())
            &&crop!=null&&crop.plantedBlock().equals(bed.crop())&&crop.item().equals(bed.output());
    }
    public static boolean ripe(World world,FieldProductionState.Bed bed) {
        var p=bed.point();var block=ChunkSectionBlockUtil.blockType(world,p.x(),p.y(),p.z());
        return block!=null&&bed.output().equals(block.getId());
    }
    /** Preserve the established root by restarting its native growth block, then confirm before awarding cargo. */
    public static boolean harvest(World world,TownRecord town,PlotInstance plot,PlotProductionState production,
            FieldProductionState.Bed bed,Set<String> outputs,Runnable save) {
        var crop=AlchemicalCrop.fromBlock(bed.crop());
        if(crop==null||!isPlot(plot)||!outputs.contains(crop.item())
            ||com.hexvane.aetherhaven.mastery.CoreProductionRequirements.issueForKind(town,"druid",bed.output())!=null
            ||!usable(world,town,plot,bed)||!ripe(world,bed))return false;
        var p=bed.point();
        boolean begun=production.field().beginHarvest(bed,crop.plantedBlock(),Map.of(crop.item(),1),
            ()->ChunkSectionBlockUtil.setBlockByKey(world,p.x(),p.y(),p.z(),crop.plantedBlock(),0));
        if(begun) {
            UUID claim=production.field().pending().id();
            world.execute(()->{if(production.field().pending()!=null&&claim.equals(production.field().pending().id())
                &&NativeFarmCrops.settleHarvest(world,production))save.run();});
        }
        return begun;
    }
}
