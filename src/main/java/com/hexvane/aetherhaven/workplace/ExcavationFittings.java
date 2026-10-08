package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import java.util.*;

/** Paid fittings stay inside reviewed air and leave the center walking route clear. */
public final class ExcavationFittings {
    public static final String BEAM="Wood_Softwood_Beam",TORCH="Furniture_Crude_Torch",LANTERN="Deco_Lantern",RAIL="Rail";
    public static final String CORNER="Aetherhaven_Mine_Beam_Corner",CEILING="Aetherhaven_Mine_Ceiling_Beam";
    public enum Mode {
        NONE("No fittings",false,false,false),SUPPORTS("Beams",true,false,false),LIGHTS("Wall torches",false,true,false),BOTH("Beams and wall torches",true,true,false),
        LANTERNS("Ceiling lanterns",false,false,true),SUPPORTS_LANTERNS("Beams and lanterns",true,false,true),LIGHTS_LANTERNS("Torches and lanterns",false,true,true),ALL("Beams, torches and lanterns",true,true,true),
        RAILS("Rails",false,false,false,true),SUPPORTS_RAILS("Beams and rails",true,false,false,true),LIGHTS_RAILS("Torches and rails",false,true,false,true),BOTH_RAILS("Beams, torches and rails",true,true,false,true),
        LANTERNS_RAILS("Lanterns and rails",false,false,true,true),SUPPORTS_LANTERNS_RAILS("Beams, lanterns and rails",true,false,true,true),LIGHTS_LANTERNS_RAILS("Torches, lanterns and rails",false,true,true,true),ALL_RAILS("All fittings",true,true,true,true);
        public final String label;public final boolean supports,lights,lanterns,rails;
        Mode(String label,boolean supports,boolean lights,boolean lanterns){this(label,supports,lights,lanterns,false);}
        Mode(String label,boolean supports,boolean lights,boolean lanterns,boolean rails){this.label=label;this.supports=supports;this.lights=lights;this.lanterns=lanterns;this.rails=rails;}
        public static Mode of(boolean supports,boolean lights,boolean lanterns){return of(supports,lights,lanterns,false);}
        public static Mode of(boolean supports,boolean lights,boolean lanterns,boolean rails){return Arrays.stream(values()).filter(m->m.supports==supports&&m.lights==lights&&m.lanterns==lanterns&&m.rails==rails).findFirst().orElseThrow();}
    }
    public record Fitting(Point point,String item,int rotation,String block,Point support) {
        public Fitting(Point point,String item,int rotation){this(point,item,rotation,null,null);}
        @Override public String block(){return block==null?item:block;}
        @Override public int rotation(){
            // Native Wood_Torch_Wall attaches on local North. Derive the yaw from
            // the actual support so queued fittings in existing saves are fixed too.
            if(TORCH.equals(item)&&"Wood_Torch_Wall".equals(block())&&support!=null)
                return wallTorchRotation(support.x()-point.x(),support.z()-point.z());
            return rotation;
        }
    }
    private ExcavationFittings(){}
    public static List<Fitting> plan(WorkplaceArea a,ExcavationPlan.Entrance side,ExcavationPlan.Layout layout,Mode mode){
        if(mode==null||mode==Mode.NONE)return List.of();
        var air=new HashSet<>(ExcavationPlan.tunnel(a,side,layout));if(air.isEmpty())return List.of();
        boolean alongZ=side==ExcavationPlan.Entrance.NORTH||side==ExcavationPlan.Entrance.SOUTH;
        int length=layout==ExcavationPlan.Layout.ROOM?9:Math.min(layout==ExcavationPlan.Layout.BRANCHES?16:32,(alongZ?a.maxZ()-a.minZ():a.maxX()-a.minX())-3);
        int fx=alongZ?0:side==ExcavationPlan.Entrance.WEST?1:-1,fz=alongZ?(side==ExcavationPlan.Entrance.NORTH?1:-1):0;
        var result=new LinkedHashMap<Point,Fitting>();int half=layout==ExcavationPlan.Layout.ROOM?4:1;
        for(int d=4;d<3+length;d+=6){
            int height=ExcavationPlan.rowHeight(a,layout,d);
            if(layout==ExcavationPlan.Layout.BRANCHES&&Math.abs(d-(3+length/2))<=2)continue;
            var center=center(a,side,layout,d,0);int sx=fz,sz=fx;
            if(mode.supports){
                for(int y=0;y<height-1;y++)for(int edge:new int[]{-half,half})put(result,air,new Fitting(offset(center,edge*sx,y,edge*sz),BEAM,0,BEAM,offset(center,edge*sx,y-1,edge*sz)));
                int rotation=yaw(sx==0?90:0);
                for(int edge:new int[]{-half,half})put(result,air,new Fitting(offset(center,edge*sx,height-1,edge*sz),BEAM,rotation,CORNER,offset(center,edge*sx,height-2,edge*sz)));
                for(int edge=-half+1;edge<half;edge++)put(result,air,new Fitting(offset(center,edge*sx,height-1,edge*sz),BEAM,rotation,CEILING,offset(center,edge*sx,height,edge*sz)));
            }
            // Lights occupy roof/wall cells between frames, preserving two blocks of central headroom.
            if(d+2<3+length){
                var light=center(a,side,layout,d+2,0);
                if(mode.lights){int dx=-sx,dz=-sz;int rotation=wallTorchRotation(dx,dz);
                    put(result,air,new Fitting(offset(light,dx*half,1,dz*half),TORCH,rotation,"Wood_Torch_Wall",offset(light,dx*(half+1),1,dz*(half+1))));}
                int lightHeight=ExcavationPlan.rowHeight(a,layout,d+2);
                if(mode.lanterns)put(result,air,new Fitting(offset(light,0,lightHeight-1,0),LANTERN,0,"Deco_Lantern_Ceiling",offset(light,0,lightHeight,0)));
            }
        }
        if(mode.rails&&layout!=ExcavationPlan.Layout.ROOM)for(int d=3;d<3+length;d++){
            var p=center(a,side,layout,d,0);
            var ahead=center(a,side,layout,d+1,0);var behind=center(a,side,layout,d-1,0);
            int ux=0,uz=0;
            if(d+1<3+length&&ahead.y()>p.y()){ux=fx;uz=fz;}
            else if(d>3&&behind.y()>p.y()){ux=-fx;uz=-fz;}
            boolean slope=ux!=0||uz!=0;
            int rotation=slope?yaw(uz<0?0:ux>0?270:uz>0?180:90):yaw(alongZ?0:90);
            put(result,air,new Fitting(p,RAIL,rotation,slope?"*Rail_State_Definitions_Slope":RAIL,offset(p,0,-1,0)));
        }
        return List.copyOf(result.values());
    }
    private static int wallTorchRotation(int dx,int dz){return yaw(dz<0?0:dx<0?90:dz>0?180:270);}
    private static int yaw(int degrees){return RotationTuple.of(switch(degrees){case 90->Rotation.Ninety;case 180->Rotation.OneEighty;case 270->Rotation.TwoSeventy;default->Rotation.None;},Rotation.None,Rotation.None).index();}
    private static void put(Map<Point,Fitting> result,Set<Point> air,Fitting fitting){if(air.contains(fitting.point()))result.putIfAbsent(fitting.point(),fitting);}
    private static Point offset(Point p,int x,int y,int z){return new Point(p.x()+x,p.y()+y,p.z()+z);}
    private static Point center(WorkplaceArea a,ExcavationPlan.Entrance side,ExcavationPlan.Layout layout,int depth,int offset){
        boolean alongZ=side==ExcavationPlan.Entrance.NORTH||side==ExcavationPlan.Entrance.SOUTH;
        int x=alongZ?a.minX()+(a.maxX()-a.minX()-1)/2+offset:side==ExcavationPlan.Entrance.WEST?a.minX()+depth:a.maxX()-1-depth;
        int z=!alongZ?a.minZ()+(a.maxZ()-a.minZ()-1)/2+offset:side==ExcavationPlan.Entrance.NORTH?a.minZ()+depth:a.maxZ()-1-depth;
        var p=new Point(x,a.floorY(),z);return new Point(x,ExcavationPlan.floorAt(a,side,layout,p),z);
    }
}
