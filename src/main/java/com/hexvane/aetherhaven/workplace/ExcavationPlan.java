package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Saved finite terrain order. A confirmed empty cell creates cargo, never a timer or renewable node. */
public final class ExcavationPlan {
    public enum Entrance { NORTH, EAST, SOUTH, WEST }
    public enum Layout {
        STRAIGHT("Straight", "A level passage three blocks wide and three high."),
        BRANCHES("Branches", "A level main passage with two side passages. The wider workplace is set for you."),
        STAIRS_UP("Stairs up", "Rise one block every four rows, up to two blocks above the entrance."),
        STAIRS_DOWN("Stairs down", "Descend one block every four rows, up to two blocks below the entrance.");
        public final String label, description;
        Layout(String label,String description){this.label=label;this.description=description;}
    }
    public record Cell(Point point,String block,int rotation,int skill) {}
    private UUID id,author,mine;
    private Point cargoPoint,pendingCargoPoint;private String carrier;
    private WorkplaceArea area;
    private Entrance entrance=Entrance.NORTH;
    private Layout layout=Layout.STRAIGHT;
    private ExcavationFittings.Mode fittingsMode=ExcavationFittings.Mode.NONE;
    private List<ExcavationFittings.Fitting> fittings=new ArrayList<>();
    private int fitted;
    public ExcavationFittings.Mode fittingsMode(){return fittingsMode==null?ExcavationFittings.Mode.NONE:fittingsMode;}
    public int fitted(){return fitted;}public int fittingsTotal(){return fittings==null?0:fittings.size();}
    public ExcavationFittings.Fitting nextFitting(){return fitted<fittingsTotal()?fittings.get(fitted):null;}
    private List<Cell> cells=new ArrayList<>();
    private int cursor;private long revision;private boolean paused=true,edited,pending;
    private double work;
    private Map<String,Integer> drops=new LinkedHashMap<>(),cargo=new LinkedHashMap<>();
    public Point cargoPoint(){return cargoPoint;}public String carrier(){return carrier;}
    public UUID id(){return id;}public UUID author(){return author;}public UUID mine(){return mine;}
    public WorkplaceArea area(){return area;}public Entrance entrance(){return entrance;}
    public Layout layout(){return layout==null?Layout.STRAIGHT:layout;}
    public int floorAt(Point point){return floorAt(area,entrance,layout(),point);}
    public long revision(){return revision;}public boolean paused(){return paused;}public boolean edited(){return edited;}
    public boolean pending(){return pending;}public boolean held(){return pending||!cargo.isEmpty();}
    public int total(){return cells.size();}public int completed(){return cursor;}
    public Cell next(){return cursor<cells.size()?cells.get(cursor):null;}public double work(){return Math.max(0,work);}
    public Map<String,Integer> drops(){return Collections.unmodifiableMap(drops);}public Map<String,Integer> cargo(){return Collections.unmodifiableMap(cargo);}
    public String start(long expected,UUID author,UUID mine,WorkplaceArea area,Entrance entrance,List<Cell> reviewed) {
        return start(expected,author,mine,area,entrance,Layout.STRAIGHT,reviewed);
    }
    public String start(long expected,UUID author,UUID mine,WorkplaceArea area,Entrance entrance,Layout layout,List<Cell> reviewed) {
        return start(expected,author,mine,area,entrance,layout,reviewed,ExcavationFittings.Mode.NONE);
    }
    public String start(long expected,UUID author,UUID mine,WorkplaceArea area,Entrance entrance,Layout layout,List<Cell> reviewed,ExcavationFittings.Mode fittingsMode) {
        if(expected!=revision)return "The tunnel changed. Review it again.";
        if(held())return "Let the Apprentice deliver the collected material first.";
        if(author==null||mine==null||area==null||!area.valid()||entrance==null||layout==null||reviewed==null||reviewed.isEmpty()||reviewed.size()>288)return "Review a tunnel with rock left to excavate.";
        var points=new HashSet<Point>();
        for(var cell:reviewed)if(cell==null||cell.point()==null||cell.block()==null||cell.block().isBlank()||cell.skill()<1||cell.skill()>10||!points.add(cell.point())||!area.contains(cell.point().x(),cell.point().y(),cell.point().z()))return "The tunnel review is invalid.";
        this.id=UUID.randomUUID();this.author=author;this.mine=mine;this.area=area;this.entrance=entrance;this.layout=layout;cells=new ArrayList<>(reviewed);cursor=0;work=0;drops.clear();paused=false;edited=false;
        this.fittingsMode=fittingsMode==null?ExcavationFittings.Mode.NONE:fittingsMode;fittings=new ArrayList<>(ExcavationFittings.plan(area,entrance,layout,this.fittingsMode));fitted=0;revision++;return null;
    }
    public boolean pause(long expected,boolean value){if(expected!=revision||id==null||!value&&edited)return false;paused=value;revision++;return true;}
    // Rock edits invalidate the reviewed drop order. Fittings only fill empty cells and recheck each
    // position at placement, so a temporary obstruction can be cleared without losing the plan.
    public boolean markEdited(Point p){if(id==null||edited||cells.stream().skip(cursor).noneMatch(c->c.point().equals(p)))return false;edited=true;paused=true;revision++;return true;}
    public boolean prepare(Map<String,Integer> rolled) {
        if(next()==null||held()||paused||!drops.isEmpty()||rolled==null||rolled.isEmpty()||rolled.size()>8)return false;
        if(rolled.entrySet().stream().anyMatch(e->e.getKey()==null||e.getKey().isBlank()||e.getValue()==null||e.getValue()<1||e.getValue()>64))return false;
        drops.putAll(rolled);return true;
    }
    public void attend(double seconds,boolean eligible,double multiplier){if(eligible&&!paused&&!held()&&(next()!=null&&!drops.isEmpty()||next()==null&&nextFitting()!=null)&&Double.isFinite(seconds)&&Double.isFinite(multiplier))work=Math.min(120,work+Math.clamp(seconds,0,.5)*Math.clamp(multiplier,.1,5));}
    public boolean fit(UUID expected,ExcavationFittings.Fitting fitting,BooleanSupplier place) {
        if(!Objects.equals(id,expected)||fitting==null||!fitting.equals(nextFitting())||next()!=null||held()||paused||edited||work()<3||!place.getAsBoolean())return false;
        fitted++;work=0;revision++;return true;
    }
    public boolean begin(UUID expected,double required,Point standing,BooleanSupplier remove) {
        if(!Objects.equals(id,expected)||paused||held()||next()==null||drops.isEmpty()||!Double.isFinite(required)||required<=0||work()<required)return false;
        if(!withinPickupReach(standing,next().point()))return false;
        pending=true;pendingCargoPoint=standing;
        try{if(remove.getAsBoolean())return true;}catch(RuntimeException error){pending=false;pendingCargoPoint=null;throw error;}
        pending=false;pendingCargoPoint=null;return false;
    }
    /** Null means unavailable. A changed solid cell suspends the order with no payout. */
    public boolean settle(UUID expected,Boolean empty) {
        if(!Objects.equals(id,expected)||!pending||empty==null)return false;
        pending=false;
        if(empty){cargoPoint=pendingCargoPoint!=null?pendingCargoPoint:new Point(next().point().x(),floorAt(next().point()),next().point().z());carrier=null;cargo.putAll(drops);cursor++;work=0;drops.clear();}
        else {paused=true;edited=true;revision++;}
        pendingCargoPoint=null;
        return true;
    }
    public boolean pickup(UUID expected,String worker,Point standing) {
        if(!Objects.equals(id,expected)||cargo.isEmpty()||worker==null||worker.isBlank()||standing==null||cargoPoint==null)return false;
        if(!withinPickupReach(standing,cargoPoint))return false;
        carrier=worker;cargoPoint=standing;return true;
    }
    public static boolean withinPickupReach(Point standing,Point point) {
        if(standing==null||point==null)return false;
        long dx=(long)standing.x()-point.x(),dy=(long)standing.y()-point.y(),dz=(long)standing.z()-point.z();
        return Math.abs(dx)<=4&&Math.abs(dy)<=4&&Math.abs(dz)<=4&&dx*dx+dy*dy+dz*dz<=16;
    }
    public void trackCarrier(String worker,Point standing){if(worker!=null&&worker.equals(carrier)&&!cargo.isEmpty()&&standing!=null)cargoPoint=standing;}
    public boolean delivered(UUID expected,BooleanSupplier store) {
        if(!Objects.equals(id,expected)||cargo.isEmpty()||!store.getAsBoolean())return false;cargo.clear();carrier=null;cargoPoint=null;return true;
    }
    /** North means the entrance is at the north edge. Three clear approach rows precede excavation. */
    public static List<Point> tunnel(WorkplaceArea a,Entrance side) {
        return tunnel(a,side,Layout.STRAIGHT);
    }
    public static List<Point> tunnel(WorkplaceArea a,Entrance side,Layout layout) {
        if(a==null||!a.valid()||side==null||layout==null)return List.of();
        boolean alongZ=side==Entrance.NORTH||side==Entrance.SOUTH;
        int width=alongZ?a.maxX()-a.minX():a.maxZ()-a.minZ(),depth=alongZ?a.maxZ()-a.minZ():a.maxX()-a.minX();
        if(width<(layout==Layout.BRANCHES?9:5)||depth<6)return List.of();
        var points=new ArrayList<Point>();int length=Math.min(layout==Layout.BRANCHES?16:32,depth-3);
        for(int d=3;d<3+length;d++)for(int y=2;y>=0;y--)for(int w=-1;w<=1;w++) {
            points.add(point(a,side,layout,d,w,y));
        }
        if(layout==Layout.BRANCHES) {
            // Excavate the main route first, then each complete branch face from the junction out.
            // Sixteen main faces plus at most sixteen branch faces retain the 288 block limit.
            int junction=3+length/2,center=(width-1)/2;
            for(int direction:new int[]{-1,1}) {
                int faces=Math.min(8,(direction<0?center:width-1-center)-2);
                for(int step=2;step<2+faces;step++)for(int y=2;y>=0;y--)for(int d=junction-1;d<=junction+1;d++)
                    points.add(point(a,side,layout,d,direction*step,y));
            }
        }
        return List.copyOf(points);
    }
    private static Point point(WorkplaceArea a,Entrance side,Layout layout,int depth,int offset,int height) {
        boolean alongZ=side==Entrance.NORTH||side==Entrance.SOUTH;
        int x=alongZ?a.minX()+(a.maxX()-a.minX()-1)/2+offset:side==Entrance.WEST?a.minX()+depth:a.maxX()-1-depth;
        int z=!alongZ?a.minZ()+(a.maxZ()-a.minZ()-1)/2+offset:side==Entrance.NORTH?a.minZ()+depth:a.maxZ()-1-depth;
        var horizontal=new Point(x,a.floorY(),z);
        return new Point(x,floorAt(a,side,layout,horizontal)+height,z);
    }
    /** Feet height, not the supporting block. All steps stay inside the workplace's five block band. */
    public static int floorAt(WorkplaceArea a,Entrance side,Layout layout,Point point) {
        int depth=switch(side){case NORTH->point.z()-a.minZ();case SOUTH->a.maxZ()-1-point.z();case WEST->point.x()-a.minX();case EAST->a.maxX()-1-point.x();};
        int rise=Math.clamp((depth-3)/4,0,2);
        return a.floorY()+switch(layout){case STAIRS_UP->rise;case STAIRS_DOWN->2-rise;default->0;};
    }
}
