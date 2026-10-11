package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.builtin.adventure.farming.states.FarmingBlock;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.HashSet;

/** Attended growth assistance. Native farming alone performs each geometry transition. */
public final class NativeTimberTending {
    private NativeTimberTending() {}
    public static boolean growing(World world,TimberPlantingReceipt tree) { return growth(world,tree)!=null; }
    private static FarmingBlock growth(World world,TimberPlantingReceipt tree) {
        if(!world.isInThread())throw new IllegalStateException("Tree tending requires the world thread");
        var p=tree.root();if(ChunkSectionBlockUtil.worldChunkIfTicking(world,p.x(),p.z())==null)return null;
        var ref=ChunkSectionBlockUtil.blockEntityRefAt(world,p.x(),p.y(),p.z());
        var farming=ref==null||!ref.isValid()?null:ref.getStore().getComponent(ref,FarmingBlock.getComponentType());
        if(farming==null||!"Default".equals(farming.getCurrentStageSet()))return null;
        float progress=farming.getGrowthProgress();
        if(!Float.isFinite(progress)||progress<0||progress>=tree.mature().index()||farming.getGeneration()!=(int)progress)return null;
        String previous=farming.getPreviousBlockType();
        if(previous!=null&&!previous.equals(tree.sapling()))return null;
        var current=ChunkSectionBlockUtil.blockType(world,p.x(),p.y(),p.z());
        if(previous==null&&(current==null||!tree.sapling().equals(current.getId())))return null;
        return farming;
    }
    /** Share of the way to a full tree, or -1 once it is grown or no longer growing here. */
    public static double progress(World world,TimberPlantingReceipt tree) {
        var farming=growth(world,tree);return farming==null?-1:Math.min(1,farming.getGrowthProgress()/(double)tree.mature().index());
    }
    public static boolean tend(World world,TimberPlantingReceipt tree,double seconds,double workBudget) {
        var farming=growth(world,tree);if(farming==null)return false;
        // Read only loaded columns. A missing canopy column never triggers a load.
        var columns=new HashSet<Long>();
        for(var cell:tree.footprint())columns.add(ChunkUtil.indexChunkFromBlock(cell.x(),cell.z()));
        for(long column:columns)if(ChunkSectionBlockUtil.worldChunkIfTicking(world,column)==null)return false;
        float next=assistedProgress(farming.getGrowthProgress(),tree.mature().index(),seconds,workBudget);
        if(next<=farming.getGrowthProgress())return false;
        farming.setGrowthProgress(next);
        var p=tree.root();var section=ChunkSectionBlockUtil.blockSectionAt(world,p.x(),p.y(),p.z());
        var components=ChunkSectionBlockUtil.blockComponentSectionAt(world,p.x(),p.y(),p.z());
        if(components!=null)components.markBlockNeedsSaving(ChunkUtil.indexBlock(p.x(),p.y(),p.z()));
        var now=world.getEntityStore().getStore().getResource(WorldTimeResource.getResourceType()).getGameTime();
        section.setTicking(p.x(),p.y(),p.z(),true);section.scheduleTick(ChunkUtil.indexBlock(p.x(),p.y(),p.z()),now);
        return true;
    }
    static float assistedProgress(float progress,int timedStages,double seconds,double budget) {
        if(!Float.isFinite(progress)||progress<0||timedStages<1||progress>=timedStages
            ||!Double.isFinite(seconds)||seconds<=0||!Double.isFinite(budget)||budget<=0)return progress;
        // Crossing the integer here would skip native stage application and leave the wrong tree in the world.
        float ceiling=Math.nextDown((float)((int)progress+1));
        return (float)Math.min(ceiling,progress+Math.min(1,seconds)*timedStages/budget);
    }
}
