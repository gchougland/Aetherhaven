package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import java.util.*;

/** Optional native fittings inside cleared corridors. Center feet/head cells stay open. */
public final class ExcavationFittings {
    public static final String BEAM="Wood_Softwood_Beam", TORCH="Furniture_Crude_Torch";
    public enum Mode {
        NONE("No fittings",false,false), SUPPORTS("Supports",true,false), LIGHTS("Torches",false,true), BOTH("Supports and torches",true,true);
        public final String label;public final boolean supports,lights;
        Mode(String label,boolean supports,boolean lights){this.label=label;this.supports=supports;this.lights=lights;}
    }
    public record Fitting(Point point,String item,int rotation) {}
    private ExcavationFittings() {}
    public static List<Fitting> plan(WorkplaceArea area,ExcavationPlan.Entrance entrance,ExcavationPlan.Layout layout,Mode mode) {
        if(mode==null||mode==Mode.NONE)return List.of();
        var tunnel=new HashSet<>(ExcavationPlan.tunnel(area,entrance,layout));if(tunnel.isEmpty())return List.of();
        boolean alongZ=entrance==ExcavationPlan.Entrance.NORTH||entrance==ExcavationPlan.Entrance.SOUTH;
        int depth=alongZ?area.maxZ()-area.minZ():area.maxX()-area.minX();
        int length=Math.min(layout==ExcavationPlan.Layout.BRANCHES?16:32,depth-3),junction=3+length/2;
        int fx=alongZ?0:entrance==ExcavationPlan.Entrance.WEST?1:-1,fz=alongZ?(entrance==ExcavationPlan.Entrance.NORTH?1:-1):0;
        var result=new ArrayList<Fitting>();
        for(int d=4;d<3+length;d+=8) {
            if(layout==ExcavationPlan.Layout.BRANCHES&&Math.abs(d-junction)<=2)continue;
            frame(result,tunnel,center(area,entrance,layout,d,0),fx,fz,mode);
        }
        if(layout==ExcavationPlan.Layout.BRANCHES)for(int direction:new int[]{-1,1})for(int offset=4;offset<=8;offset+=4)
            frame(result,tunnel,center(area,entrance,layout,junction,direction*offset),alongZ?direction:0,alongZ?0:direction,mode);
        return List.copyOf(result);
    }
    private static Point center(WorkplaceArea a,ExcavationPlan.Entrance side,ExcavationPlan.Layout layout,int depth,int offset) {
        boolean alongZ=side==ExcavationPlan.Entrance.NORTH||side==ExcavationPlan.Entrance.SOUTH;
        int x=alongZ?a.minX()+(a.maxX()-a.minX()-1)/2+offset:side==ExcavationPlan.Entrance.WEST?a.minX()+depth:a.maxX()-1-depth;
        int z=!alongZ?a.minZ()+(a.maxZ()-a.minZ()-1)/2+offset:side==ExcavationPlan.Entrance.NORTH?a.minZ()+depth:a.maxZ()-1-depth;
        var p=new Point(x,a.floorY(),z);return new Point(x,ExcavationPlan.floorAt(a,side,layout,p),z);
    }
    private static void frame(List<Fitting> result,Set<Point> tunnel,Point center,int fx,int fz,Mode mode) {
        int sx=fz,sz=fx;var frame=new ArrayList<Fitting>();
        if(mode.supports) {
            for(int y=0;y<2;y++)for(int side:new int[]{-1,1})frame.add(new Fitting(new Point(center.x()+side*sx,center.y()+y,center.z()+side*sz),BEAM,0));
            int rotation=RotationTuple.of(Rotation.None,sx==0?Rotation.Ninety:Rotation.None,sx!=0?Rotation.Ninety:Rotation.None).index();
            for(int side=-1;side<=1;side++)frame.add(new Fitting(new Point(center.x()+side*sx,center.y()+2,center.z()+side*sz),BEAM,rotation));
        }
        if(mode.lights)frame.add(new Fitting(new Point(center.x()-fx-sx,center.y(),center.z()-fz-sz),TORCH,0));
        // A short arm, step boundary or edge must never cause placement outside its reviewed air.
        if(frame.stream().allMatch(f->tunnel.contains(f.point())))result.addAll(frame);
    }
}
