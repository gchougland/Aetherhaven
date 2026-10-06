package com.hexvane.aetherhaven.warehouse;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.*;

/** Incremental discovery also supports existing marketplace prefabs without adding new markers. */
public final class ProductionStorageLocator {
    private ProductionStorageLocator() {}
    private static final Map<World,Map<UUID,Search>> SEARCHES=Collections.synchronizedMap(new WeakHashMap<>());
    private static final class Search { long cursor,retry;WarehouseBatch.Point found; }
    public static WarehouseBatch.Point find(World world,PlotInstance plot,long now) {
        Map<UUID,Search> searches;
        synchronized(SEARCHES) {searches=SEARCHES.computeIfAbsent(world,w->new HashMap<>());}
        var search=searches.computeIfAbsent(plot.getPlotId(),id->new Search());
        if(anchor(world,plot,search.found))return search.found;
        search.found=null;if(now<search.retry)return null;
        var bounds=plot.toFootprint();
        long width=(long)bounds.getMaxX()-bounds.getMinX()+1,height=(long)bounds.getMaxY()-bounds.getMinY()+1,depth=(long)bounds.getMaxZ()-bounds.getMinZ()+1;
        if(width<=0||height<=0||depth<=0)return null;
        long volume=width*height*depth;
        for(int checked=0;checked<2048;checked++) {
            if(search.cursor>=volume) {search.cursor=0;search.retry=now+10_000;return null;}
            long index=search.cursor++;
            int x=bounds.getMinX()+(int)(index%width),z=bounds.getMinZ()+(int)((index/width)%depth),y=bounds.getMinY()+(int)(index/(width*depth));
            var point=new WarehouseBatch.Point(x,y,z);
            if(anchor(world,plot,point)) {search.found=point;return point;}
        }
        return null;
    }
    private static boolean anchor(World world,PlotInstance plot,WarehouseBatch.Point point) {
        return valid(world,plot,point)&&ChunkSectionBlockUtil.filler(world,point.x(),point.y(),point.z())==0;
    }
    // Existing saved pickups may address a filler cell. Keep validating those physical positions,
    // while new searches consistently choose the actual storage anchor.
    public static boolean valid(World world,PlotInstance plot,WarehouseBatch.Point point) {
        if(plot==null||point==null||!plot.containsWorldBlock(point.x(),point.y(),point.z()))return false;
        if(ChunkSectionBlockUtil.worldChunkIfTicking(world,ChunkUtil.indexChunk(ChunkUtil.chunkCoordinate(point.x()),ChunkUtil.chunkCoordinate(point.z())))==null)return false;
        var block=ChunkSectionBlockUtil.blockType(world,point.x(),point.y(),point.z());
        return block!=null&&AetherhavenConstants.BLOCK_PRODUCTION_STORAGE.equals(block.getId());
    }
}
