package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.builtin.adventure.farming.states.FarmingBlock;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import java.util.*;
import java.util.function.Predicate;
import static com.hexvane.aetherhaven.production.TimberHarvestPlan.*;

/** Loaded-world adapter. The saved planting determines every block that can be removed. */
public final class NativeTimberHarvest {
    private NativeTimberHarvest() {}
    public static Map<Position,Cell> matureCells(TimberPlantingReceipt tree) {
        var result=new LinkedHashMap<Position,Cell>();
        for(var c:tree.mature().cells())result.put(c.position(),c);
        for(var c:tree.mature().cells()) {
            if(c.filler()!=0)continue;
            var type=BlockType.getAssetMap().getAsset(c.blockId());
            var boxes=type==null?null:BlockBoundingBoxes.getAssetMap().getAsset(type.getHitboxType());
            if(boxes==null)throw new IllegalArgumentException("Missing tree hitbox");
            FillerBlockUtil.forEachFillerBlock(boxes.get(c.rotation()),(dx,dy,dz)->{
                var p=new Position(Math.addExact(c.position().x(),dx),Math.addExact(c.position().y(),dy),Math.addExact(c.position().z(),dz));
                result.putIfAbsent(p,new Cell(p,c.blockId(),c.rotation(),FillerBlockUtil.pack(dx,dy,dz),Kind.FOLIAGE,false));
                if(result.size()>MAX_TREE_CELLS)throw new IllegalArgumentException("Tree hitbox limit");
            });
        }
        return result;
    }
    public static TimberFellingPlan.Inspection inspect(World world,TimberProductionState.Planting planted,Predicate<Position> bounds) {
        if(!world.isInThread())throw new IllegalStateException("Timber inspection requires the world thread");
        if(planted==null)return new TimberFellingPlan.Inspection(null,"No tree is planted here.");
        var root=planted.tree().root();
        if(ChunkSectionBlockUtil.worldChunkIfTicking(world,root.x(),root.z())==null)
            return new TimberFellingPlan.Inspection(null,"The tree must be in the active area.");
        var rootRef=ChunkSectionBlockUtil.blockEntityRefAt(world,root.x(),root.y(),root.z());
        var growth=rootRef==null||!rootRef.isValid()?null:rootRef.getStore().getComponent(rootRef,FarmingBlock.getComponentType());
        // A growing sapling does not need a whole-canopy scan on each work search.
        if(growth!=null&&growth.getGrowthProgress()<planted.tree().mature().index())
            return new TimberFellingPlan.Inspection(null,"This tree is still growing.");
        try {
            var cells=matureCells(planted.tree());
            return TimberFellingPlan.inspect(planted,cells,bounds,p->observe(world,p,cells.get(p)),NativeTimberHarvest::matches);
        } catch(IllegalArgumentException unavailable) {
            return new TimberFellingPlan.Inspection(null,"This tree's growth assets need review.");
        }
    }
    public static boolean fell(World world,PlotProductionState production,int slot,Predicate<Position> bounds) {
        return fell(world,production,slot,null,bounds);
    }
    public static boolean fell(World world,PlotProductionState production,int slot,ProductionCatalog.Entry entry,Predicate<Position> bounds) {
        var planted=production.timber().trees().get(slot);var checked=inspect(world,planted,bounds);
        // A tree the player removed entirely just frees its stone to grow again.
        if(planted!=null&&TimberFellingPlan.GONE.equals(checked.issue()))return production.timber().release(slot,planted.tree().id());
        if(!checked.ready())return false;
        var cells=matureCells(planted.tree());var plan=checked.plan();
        // Only leaves or branches remain: clear them away without a harvest.
        if(plan.yield()<1)return plan.restore(p->observe(world,p,cells.get(p)),(p,target)->write(world,p,target))
            &&production.timber().release(slot,plan.tree());
        if(entry!=null&&!NativeTimberProducts.hasRoom(production,entry,planted.tree()))return false;
        var products=entry==null?Map.<String,Integer>of():NativeTimberProducts.harvest(plan,planted.tree(),NativeTimberProducts.collectionLimits(production,entry,planted.tree()));
        return production.timber().fell(slot,plan.tree(),plan.yield(),products,()->plan.restore(
            p->observe(world,p,cells.get(p)),(p,target)->write(world,p,target)));
    }
    private static boolean write(World world,Position p,Observed target) {
        if(!world.isInThread())throw new IllegalStateException("Timber removal requires the world thread");
        if(ChunkSectionBlockUtil.worldChunkIfTicking(world,p.x(),p.z())==null)return false;
        var type=BlockType.getAssetMap().getAsset(target.blockId());if(type==null)return false;
        // Every filler is in the preflight. Native auto filler removal must not reach outside it.
        int flags=SetBlockSettings.NO_SET_FILLER|SetBlockSettings.NO_BREAK_FILLER|SetBlockSettings.NO_DROP_ITEMS
            |SetBlockSettings.NO_FIRE_ON_BREAK|SetBlockSettings.NO_SEND_PARTICLES;
        return ChunkSectionBlockUtil.setBlock(world,p.x(),p.y(),p.z(),BlockType.getAssetMap().getIndex(type.getId()),
            type,target.rotation(),target.filler(),flags);
    }
    private static boolean matches(Cell expected,Observed current) {
        if(expected.rotation()!=current.rotation() || expected.filler()!=current.filler())return false;
        if(expected.blockId().equals(current.blockId()))return true;
        var type=BlockType.getAssetMap().getAsset(expected.blockId());
        return expected.nativeFarming() && type!=null && type.getStateForBlock(current.blockId())!=null;
    }
    private static Observed observe(World world,Position p,Cell expected) {
        var ground=NativeTimberPlanting.observe(world,p);
        boolean protectedCell=ground.protectedCell();
        var current=new Observed(ground.loaded(),ground.block(),ground.rotation(),ground.filler(),protectedCell);
        if(ground.loaded() && protectedCell && expected!=null && expected.nativeFarming() && matches(expected,current)) {
            var section=ChunkSectionBlockUtil.sectionRefAt(world,p.x(),p.y(),p.z());
            var fluids=world.getChunkStore().getStore().getComponent(section,FluidSection.getComponentType());
            var components=ChunkSectionBlockUtil.blockComponentSectionAt(world,p.x(),p.y(),p.z());
            var ref=components==null?null:components.getBlockReference(ChunkUtil.indexBlock(p.x(),p.y(),p.z()));
            var holder=components==null?null:components.getBlockHolder(ChunkUtil.indexBlock(p.x(),p.y(),p.z()));
            var archetype=ref!=null && ref.isValid()?ref.getStore().getArchetype(ref):holder==null?null:holder.getArchetype();
            boolean known=archetype!=null;
            if(archetype!=null)for(int i=archetype.getMinIndex();i<archetype.length();i++) {
                var type=archetype.get(i);
                if(type!=null && type!=FarmingBlock.getComponentType() && type!=BlockModule.BlockStateInfo.getComponentType())known=false;
            }
            if(known && (fluids==null || fluids.getFluidId(p.x(),p.y(),p.z())==0))protectedCell=false;
        }
        return new Observed(ground.loaded(),ground.block(),ground.rotation(),ground.filler(),protectedCell);
    }
}
