package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import java.util.*;

/** Paid fittings stay inside reviewed air and leave the center walking route clear. */
public final class ExcavationFittings {
    public static final String BEAM="Wood_Hardwood_Beam",TORCH="Furniture_Crude_Torch",LANTERN="Deco_Lantern",RAIL="Rail";
    /** Supports were softwood before. Fittings still queued in older saves are built in hardwood like the rest. */
    static final String LEGACY_BEAM="Wood_Softwood_Beam";
    public static final String CORNER="Aetherhaven_Mine_Beam_Corner",CEILING="Aetherhaven_Mine_Ceiling_Beam";
    /**
     * The same joining pieces lying the other way, for a frame across a tunnel that runs east to west. The posts are
     * native beams, which never turn, and the native beam texture is lit on two faces only: a turned joining piece shows
     * its lit faces where the posts under it show their shaded ones. These are placed unturned instead.
     */
    public static final String CORNER_CROSS="Aetherhaven_Mine_Beam_Corner_Cross",CEILING_CROSS="Aetherhaven_Mine_Ceiling_Beam_Cross";
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
        @Override public String item(){return LEGACY_BEAM.equals(item)?BEAM:item;}
        @Override public String block(){
            String placed=block==null?item:block;
            if(turnedJoint())return CORNER.equals(placed)?CORNER_CROSS:CEILING_CROSS;
            return LEGACY_BEAM.equals(placed)?BEAM:placed;
        }
        @Override public int rotation(){
            // Native Wood_Torch_Wall attaches on local North. Derive the yaw from
            // the actual support so queued fittings in existing saves are fixed too.
            if(TORCH.equals(item)&&"Wood_Torch_Wall".equals(block())&&support!=null)
                return wallTorchRotation(support.x()-point.x(),support.z()-point.z());
            return turnedJoint()?yaw(0):rotation;
        }
        /** Planned and saved orders turn a joining piece a quarter for a frame across an east to west tunnel. */
        private boolean turnedJoint(){return (CORNER.equals(block)||CEILING.equals(block))&&(rotation==yaw(90)||rotation==yaw(270));}
    }
    private ExcavationFittings(){}
    /** Every item a tunnel can be fitted with, in the order the planner lists them. No other item is accepted. */
    public static final List<String> ITEMS=List.of(BEAM,TORCH,LANTERN,RAIL);
    /** Where miners take fittings from, in the warehouse page's own words. */
    public static final String STORAGE="General storage at the Warehouse Ledger Desk";
    /** How many of each item a route takes. Posts, corners and ceiling pieces are all cut from the one beam item. */
    public static Map<String,Integer> needs(WorkplaceArea a,ExcavationPlan.Entrance side,ExcavationPlan.Layout layout,Mode mode) {
        return needs(a,side,layout,mode,Set.of());
    }
    /** @param opening the open space of the passages already there: frames that would stand in it are not counted */
    public static Map<String,Integer> needs(WorkplaceArea a,ExcavationPlan.Entrance side,ExcavationPlan.Layout layout,Mode mode,Set<Point> opening) {
        var needed=new LinkedHashMap<String,Integer>();
        for(var item:ITEMS)needed.put(item,0);
        var planned=plan(a,side,layout,mode);var doorways=inDoorways(planned,side,opening);
        for(var fitting:planned)if(!doorways.contains(fitting))needed.merge(fitting.item(),1,Integer::sum);
        return needed;
    }
    /**
     * The pieces that must not be built because another passage opens where they would stand. A post in a doorway
     * takes its whole frame with it: the other post, both corners and the ceiling piece, so no half frame is left
     * hanging. A wall torch goes when its own cell is part of the opening. Lanterns and rails never block a doorway.
     */
    public static Set<Fitting> inDoorways(List<Fitting> fittings,ExcavationPlan.Entrance side,Set<Point> opening) {
        if(fittings==null||opening==null||opening.isEmpty())return Set.of();
        boolean alongZ=side==ExcavationPlan.Entrance.NORTH||side==ExcavationPlan.Entrance.SOUTH;
        var rows=new HashSet<Integer>();
        for(var fitting:fittings)if(BEAM.equals(fitting.item())&&opening.contains(fitting.point()))rows.add(alongZ?fitting.point().z():fitting.point().x());
        var doomed=new HashSet<Fitting>();
        for(var fitting:fittings) {
            if(BEAM.equals(fitting.item())?rows.contains(alongZ?fitting.point().z():fitting.point().x()):TORCH.equals(fitting.item())&&opening.contains(fitting.point()))doomed.add(fitting);
        }
        return doomed;
    }
    /** One fitting's line in the planner: what the route takes and what the town holds. */
    public static String countLine(boolean chosen,int needed,long stored) {
        if(!chosen)return "Not used. Town storage has "+stored+".";
        return "Needs "+needed+". Town storage has "+stored+".";
    }
    /** Shown when a dug tunnel waits for a fitting the town has run out of. Names the item, the count and the place. */
    public static String waitingLine(String item,Map<String,Integer> needed,java.util.function.ToLongFunction<String> stored,
        java.util.function.Function<String,String> name) {
        var also=new ArrayList<String>();
        for(var entry:needed.entrySet())if(!entry.getKey().equals(item)&&entry.getValue()>stored.applyAsLong(entry.getKey()))
            also.add((entry.getValue()-stored.applyAsLong(entry.getKey()))+" "+name.apply(entry.getKey()));
        long have=stored.applyAsLong(item);
        return "Digging is done. Waiting for "+needed.getOrDefault(item,1)+" "+name.apply(item)+": town storage has "+(have<=0?"none":Long.toString(have))+"."
            +(also.isEmpty()?"":" Also short: "+String.join(", ",also)+".")+" Put them in "+STORAGE+".";
    }
    /** Shown while miners fit out a dug tunnel: what is still to place, and whether the town holds enough of it. */
    public static String fittingLine(Map<String,Integer> needed,java.util.function.ToLongFunction<String> stored,java.util.function.Function<String,String> name) {
        var parts=new ArrayList<String>();
        for(var entry:needed.entrySet())parts.add(entry.getValue()+" "+name.apply(entry.getKey())+" ("+stored.applyAsLong(entry.getKey())+" in town storage)");
        return "Digging is done. Still to fit: "+String.join(", ",parts)+". Miners take them from "+STORAGE+".";
    }
    /** Shown while miners work round something they must not break. Says what it is, where, and how the tunnel carries on. */
    public static String obstructionLine(String what,Point at,int cells) {
        return what+" at "+at.x()+", "+at.y()+", "+at.z()+" is in the way"+(cells>1?", with "+(cells-1)+" more blocked "+(cells==2?"block":"blocks")+" near it":"")
            +". Miners dig the rest of the tunnel and finish here once it is gone.";
    }
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
