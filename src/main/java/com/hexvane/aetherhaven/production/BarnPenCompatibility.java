package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.VillagerBlockUtil;
import com.hexvane.aetherhaven.construction.PrefabLocalOffset;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.*;
import java.util.function.Function;
import org.joml.Vector3i;

/** Missing old-barn markers use saved, clear floor spaces. Never changes terrain or the author's building file. */
public final class BarnPenCompatibility {
    private BarnPenCompatibility() {}
    public record Pen(Point local,float yaw,boolean automatic) {}
    public enum Space { CLEAR, BLOCKED, UNLOADED }
    static boolean separated(Point a,Point b) {return Math.abs(a.y()-b.y())>=4||Math.abs(a.x()-b.x())>=3||Math.abs(a.z()-b.z())>=3;}
    /** Stable slot numbers; unloaded saved positions are retained, never relocated on an incomplete scan. */
    static Map<Integer,Pen> plan(List<Pen> authored,Map<Integer,Point> saved,Iterable<Point> candidates,Function<Point,Space> space) {
        var result=new LinkedHashMap<Integer,Pen>();
        for(int i=0;i<Math.min(5,authored.size());i++){result.put(i,authored.get(i));saved.remove(i);}
        for(int i=authored.size();i<5;i++) {
            Point old=saved.get(i);
            if(old!=null&&space.apply(old)!=Space.BLOCKED&&result.values().stream().allMatch(p->separated(p.local(),old)))result.put(i,new Pen(old,0,true));
            else saved.remove(i);
        }
        for(Point p:candidates) {
            if(result.size()==5)break;
            if(result.values().stream().anyMatch(other->!separated(p,other.local()))||space.apply(p)!=Space.CLEAR)continue;
            for(int slot=0;slot<5;slot++)if(!result.containsKey(slot)){result.put(slot,new Pen(p,0,true));saved.put(slot,p);break;}
        }
        return Collections.unmodifiableMap(result);
    }
    public static Vector3i worldPoint(PlotInstance plot,Pen pen) {
        if(pen==null||!plot.hasStoredPrefabWorldAnchor())return null;
        var p=pen.local();return plot.getStoredPrefabWorldAnchor().add(PrefabLocalOffset.rotate(plot.resolvePrefabYaw(),p.x(),p.y(),p.z()));
    }
    public static Map<Integer,Pen> resolve(World world,AetherhavenPlugin plugin,PlotInstance plot,HerdProductionState herd) {
        var authored=NativeHerdService.pens(plugin,plot).stream().map(p->new Pen(new Point(p.getLocalX(),p.getLocalY(),p.getLocalZ()),p.getInteractionTargetYawDegrees()==null?0:p.getInteractionTargetYawDegrees(),false)).toList();
        if(!plot.hasStoredPrefabWorldAnchor())return Map.of();
        var def=plugin.getConstructionCatalog().get(plot.getConstructionId());
        var reserved=new ArrayList<Point>();
        if(def!=null) {
            for(var p:def.getPois())if(!p.getTags().contains(NativeHerdService.PEN_TAG))reserved.add(new Point(p.getLocalX(),p.getLocalY(),p.getLocalZ()));
            for(int[] p:new int[][]{def.getManagementBlockLocalPos(),def.getProductionStorageLocalPos()})if(p!=null)reserved.add(new Point(p[0],p[1],p[2]));
        }
        Function<Point,Space> available=p->{
            if(reserved.stream().anyMatch(r->!separated(p,r)))return Space.BLOCKED;
            return space(world,plot,worldPoint(plot,new Pen(p,0,true)));
        };
        var existing=plan(authored,herd.automaticPens(),List.of(),available);
        if(existing.size()==5)return existing;
        var f=plot.toFootprint();var origin=plot.getStoredPrefabWorldAnchor();
        var candidates=new ArrayList<Point>();
        // Ground floor only: do not invent rooftop pens. Bounded even for a malformed marketplace footprint.
        long footprint=((long)f.getMaxX()-f.getMinX()+1)*((long)f.getMaxZ()-f.getMinZ()+1);
        if(authored.size()<5&&footprint>0&&footprint<=16384)
            for(int y=f.getMinY()+1;y<=Math.min(f.getMaxY()-3,f.getMinY()+4);y++)
                for(int x=f.getMinX()+1;x<f.getMaxX();x++)for(int z=f.getMinZ()+1;z<f.getMaxZ();z++) {
                    var p=PrefabLocalOffset.inverseRotateWorldDelta(plot.resolvePrefabYaw(),x-origin.x,y-origin.y,z-origin.z);
                    candidates.add(new Point(p.x,p.y,p.z));
                }
        // Local order makes a fresh placement choose the same relative spots at every rotation.
        candidates.sort(Comparator.comparingInt(Point::y).thenComparingInt(Point::x).thenComparingInt(Point::z));
        return plan(authored,herd.automaticPens(),candidates,available);
    }
    public static Space space(World world,PlotInstance plot,Vector3i at) {
        return space(at,new Cells() {
            public boolean inside(int x,int y,int z){return plot.containsWorldBlock(x,y,z);}
            public boolean loaded(int x,int z){return ChunkSectionBlockUtil.worldChunkIfTicking(world,x,z)!=null;}
            public boolean floor(int x,int y,int z){return VillagerBlockUtil.isNpcStandColumn(world,x,y,z);}
            public boolean empty(int x,int y,int z){return FarmFieldWorld.free(world,new Point(x,y,z));}
        });
    }
    interface Cells {
        boolean inside(int x,int y,int z);
        boolean loaded(int x,int z);
        boolean floor(int x,int y,int z);
        boolean empty(int x,int y,int z);
    }
    static Space space(Vector3i at,Cells cells) {
        if(at==null)return Space.BLOCKED;
        boolean unread=false;
        for(int x=at.x-1;x<=at.x+1;x++)for(int z=at.z-1;z<=at.z+1;z++) {
            if(!cells.inside(x,at.y-1,z)||!cells.inside(x,at.y+3,z))return Space.BLOCKED;
            if(!cells.loaded(x,z)){unread=true;continue;}
            if(!cells.floor(x,at.y,z))return Space.BLOCKED;
            for(int y=at.y;y<=at.y+3;y++)if(!cells.empty(x,y,z))return Space.BLOCKED;
        }
        return unread?Space.UNLOADED:Space.CLEAR;
    }
}
