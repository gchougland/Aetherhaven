package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.construction.PrefabLocalOffset;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import org.joml.Vector3i;
import java.util.function.Function;

/** Shared editor/fixture preflight. A failed conversion does not change the layout or world. */
public final class MiningLayoutAdoption {
    private MiningLayoutAdoption() {}
    record Cell(String block, boolean safe, boolean clearSide) {}
    public static String issue(World world, PlotInstance plot, BuildingProductionLayout layout) {
        return issue(plot,layout,p->{
            if(!MiningNodeBlocks.loaded(world,p))return null;
            boolean clearSide=false;
            for(int[] side:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})for(int dy=-1;dy<=1;dy++) {
                var at=new Vector3i(p).add(side[0],dy,side[1]);
                if(MiningNodeBlocks.loaded(world,at)&&com.hexvane.aetherhaven.autonomy.VillagerBlockUtil.isNpcStandColumn(world,at.x,at.y,at.z))clearSide=true;
            }
            var block=ChunkSectionBlockUtil.blockType(world,p.x,p.y,p.z);
            return new Cell(block==null?null:block.getId(),MiningNodeBlocks.safeOriginal(block)
                &&ChunkSectionBlockUtil.filler(world,p.x,p.y,p.z)==0
                &&ChunkSectionBlockUtil.blockEntityHolderAt(world,p.x,p.y,p.z)==null,clearSide);
        });
    }
    /** Read-only snapshot validation; every cell is checked again when applying the reviewed layout. */
    static String issue(PlotInstance plot, BuildingProductionLayout layout, Function<Vector3i,Cell> read) {
        if (plot==null || !plot.getProductionLayout().legacy()) return "This mine already uses physical nodes.";
        if (layout==null || layout.legacy()) return "Choose at least one mining cell.";
        String error=layout.validationError(); if(error!=null) return error;
        if(!plot.hasStoredPrefabWorldAnchor()) return "Finish building this mine first.";
        for(var node:layout.nodes()) {
            var p=plot.getStoredPrefabWorldAnchor().add(PrefabLocalOffset.rotate(plot.resolvePrefabYaw(),node.x(),node.y(),node.z()));
            if(!plot.containsWorldBlock(p.x,p.y,p.z)) return "Every mining cell must be inside this mine.";
            var cell=read.apply(p);
            if(cell==null) return "Move closer so every mining cell is loaded.";
            if(!node.expectedBlockId().equals(cell.block())) return "A mining cell has changed. Review the building's node locations.";
            if(!cell.safe())
                return "Select plain rock, ore or soil. Furniture and storage cannot become mining cells.";
            if(!cell.clearSide())return "Leave a clear standing space beside every mining cell.";
        }
        return null;
    }
    public static boolean adopt(World world, PlotInstance plot, BuildingProductionLayout layout) {
        return issue(world,plot,layout)==null && plot.adoptMiningLayout(layout);
    }
}
