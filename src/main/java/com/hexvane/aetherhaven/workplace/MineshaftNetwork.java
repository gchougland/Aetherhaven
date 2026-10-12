package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import java.util.*;

/** Saved tunnel geometry shared by the map, connection validation and queued reviews. */
public final class MineshaftNetwork {
    private MineshaftNetwork(){}
    /** Map cells select the center of a passage, never an arbitrary edge block. */
    public static Set<Point> mapPoints(WorkplaceArea area,ExcavationPlan.Entrance entrance,ExcavationPlan.Layout layout){
        var result=new LinkedHashSet<Point>();
        boolean alongZ=entrance==ExcavationPlan.Entrance.NORTH||entrance==ExcavationPlan.Entrance.SOUTH;
        int center=alongZ?area.minX()+(area.maxX()-area.minX()-1)/2:area.minZ()+(area.maxZ()-area.minZ()-1)/2;
        for(var p:ExcavationPlan.tunnel(area,entrance,layout))
            if(layout==ExcavationPlan.Layout.ROOM||layout==ExcavationPlan.Layout.BRANCHES||(alongZ?p.x():p.z())==center)
                result.add(new Point(p.x(),ExcavationPlan.floorAt(area,entrance,layout,p),p.z()));
        return result;
    }
    public static Map<Point,UUID> mapConnections(TownWorkplaces sites,ApprenticeWorkplace base){
        var result=new LinkedHashMap<Point,UUID>();var a=base.area();
        for(int x=a.minX();x<a.maxX();x++)for(int z=a.minZ();z<a.maxZ();z++)result.put(new Point(x,a.floorY(),z),base.id());
        for(var site:sites.tunnels(base.id()))for(var p:mapPoints(site.area(),site.excavation().entrance(),site.excavation().layout()))if(isLanding(site.area(),site.excavation().entrance(),site.excavation().layout(),p.y()))result.putIfAbsent(p,site.id());
        return result;
    }
    public static Point mapConnection(Map<Point,UUID> connections,Point center){
        return connections.keySet().stream().filter(p->p.y()==center.y()&&Math.abs((long)p.x()-center.x())<=1&&Math.abs((long)p.z()-center.z())<=1)
            .min(Comparator.comparingLong((Point p)->Math.abs((long)p.x()-center.x())+Math.abs((long)p.z()-center.z())).thenComparingInt(Point::x).thenComparingInt(Point::z)).orElse(null);
    }
    /** Stair branches attach to the entrance or exit landing, never a step between floors. */
    public static boolean isLanding(WorkplaceArea area,ExcavationPlan.Entrance side,ExcavationPlan.Layout layout,int y){
        int length=Math.min(32,(side==ExcavationPlan.Entrance.NORTH||side==ExcavationPlan.Entrance.SOUTH?area.maxZ()-area.minZ():area.maxX()-area.minX())-3);
        return switch(layout){
            case DEEP_STAIRS_DOWN,DEEP_STAIRS_UP -> y==area.floorY()||y==area.floorY()+7;
            case STAIRS_DOWN -> y==area.floorY()+2||y==area.floorY()+2-Math.clamp((length-1)/4,0,2);
            case STAIRS_UP -> y==area.floorY()||y==area.floorY()+Math.clamp((length-1)/4,0,2);
            default -> true;
        };
    }
    public static String connectionIssue(TownWorkplaces sites,ApprenticeWorkplace site){
        var parent=sites.find(site.minePrerequisiteId());
        if(parent==null||site.excavation().layout()!=ExcavationPlan.Layout.ROOM)return null;
        var ground=MineshaftSelection.ground(site.area(),site.excavation().entrance(),site.excavation().layout());
        return isLanding(parent.area(),parent.excavation().entrance(),parent.excavation().layout(),ground.y()+1)?null:
            "This room starts between stair landings. Remove this room order, select the bottom floor, and plan it beyond the stairs.";
    }
    /** How every refusal for closing a passage begins. The passage and the place follow. */
    public static final String COVERED_PASSAGE="This tunnel would fill in the open space of ";
    /** How every refusal for cutting a walkway begins. The passage and the place follow. */
    public static final String CUT_FLOOR="This tunnel would dig out the floor of ";
    public static final String NO_NEW_ROCK="This route has no new rock. Choose a longer route or another direction.";
    private static String place(Point p){return p.x()+", "+p.y()+", "+p.z();}
    private static int centerLine(WorkplaceArea area,ExcavationPlan.Entrance entrance){
        boolean alongZ=entrance==ExcavationPlan.Entrance.NORTH||entrance==ExcavationPlan.Entrance.SOUTH;
        return alongZ?area.minX()+(area.maxX()-area.minX()-1)/2:area.minZ()+(area.maxZ()-area.minZ()-1)/2;
    }
    /**
     * A new passage needs ground under its center line. Where that ground is the open space of a saved passage,
     * typically the steps of the stairway it starts from, workers would fill it in and close that passage. A route
     * clicked on a stairway is therefore refused; the stair's own landings stay the only places to start from.
     */
    public static String coveredPassageIssue(TownWorkplaces sites,ApprenticeWorkplace base,WorkplaceArea area,ExcavationPlan.Entrance entrance,ExcavationPlan.Layout layout){
        var open=new HashMap<Point,ApprenticeWorkplace>();
        for(var tunnel:sites.tunnels(base.id()))for(var p:ExcavationPlan.tunnel(tunnel.area(),tunnel.excavation().entrance(),tunnel.excavation().layout()))open.putIfAbsent(p,tunnel);
        boolean alongZ=entrance==ExcavationPlan.Entrance.NORTH||entrance==ExcavationPlan.Entrance.SOUTH;
        int center=centerLine(area,entrance);
        for(var p:ExcavationPlan.tunnel(area,entrance,layout)) {
            if((alongZ?p.x():p.z())!=center)continue;
            var under=new Point(p.x(),ExcavationPlan.floorAt(area,entrance,layout,p)-1,p.z());var owner=open.get(under);
            if(owner!=null)return COVERED_PASSAGE+owner.name()+" at "+place(under)+". Choose another direction, or start from the other end of that passage.";
        }
        return null;
    }
    /**
     * The ground a new tunnel leaves to the passages already there: everything the passage it starts from will dig,
     * and the space any other passage has already dug. Beams, lights and rails standing in a finished passage are
     * therefore never dug out by a branch or a crossing.
     */
    public static Set<Point> leftAlone(TownWorkplaces sites,ApprenticeWorkplace base,UUID prerequisite){
        var left=ancestorCells(sites,prerequisite);
        for(var tunnel:sites.tunnels(base.id())) {
            var plan=tunnel.excavation();var pending=plan.pendingPoints();
            for(var p:ExcavationPlan.tunnel(tunnel.area(),plan.entrance(),plan.layout()))if(!pending.contains(p))left.add(p);
        }
        return left;
    }
    /** The block under each place someone walks: the base's floor and every column of every passage. */
    private static Map<Point,ApprenticeWorkplace> walkways(TownWorkplaces sites,ApprenticeWorkplace base,UUID except){
        var floors=new HashMap<Point,ApprenticeWorkplace>();var a=base.area();
        if(!base.id().equals(except))for(int x=a.minX();x<a.maxX();x++)for(int z=a.minZ();z<a.maxZ();z++)floors.put(new Point(x,a.floorY()-1,z),base);
        for(var tunnel:sites.tunnels(base.id())) {
            if(tunnel.id().equals(except))continue;
            var plan=tunnel.excavation();
            for(var p:ExcavationPlan.tunnel(tunnel.area(),plan.entrance(),plan.layout()))floors.putIfAbsent(new Point(p.x(),plan.floorAt(p)-1,p.z()),tunnel);
        }
        return floors;
    }
    /** The block under each place someone walks in every other passage of the mine. A tunnel never digs these. */
    public static Set<Point> walkwaysFor(TownWorkplaces sites,ApprenticeWorkplace base,UUID tunnel){
        return new HashSet<>(walkways(sites,base,tunnel).keySet());
    }
    /** Everything the mine's passages open up, planned or dug. A post standing in it would stand in a doorway. */
    public static Set<Point> openSpace(TownWorkplaces sites,ApprenticeWorkplace base,UUID except){
        var open=new HashSet<Point>();
        for(var tunnel:sites.tunnels(base.id()))if(!tunnel.id().equals(except))open.addAll(ExcavationPlan.tunnel(tunnel.area(),tunnel.excavation().entrance(),tunnel.excavation().layout()));
        return open;
    }
    /** The ground a passage shares with the others: where a branch leaves it, where it leaves its parent, or a crossing. */
    public static Set<Point> junction(TownWorkplaces sites,ApprenticeWorkplace base,ApprenticeWorkplace site){
        var shared=new HashSet<>(ExcavationPlan.tunnel(site.area(),site.excavation().entrance(),site.excavation().layout()));
        shared.retainAll(openSpace(sites,base,site.id()));
        shared.removeAll(walkways(sites,base,site.id()).keySet());
        return shared;
    }
    /**
     * Keeps posts out of every opening between one passage and the rest of its mine, both ways round: this passage's
     * frames where the others open into it, and the others' frames where this one opens into them.
     *
     * @return whether any order changed
     */
    public static boolean openDoorways(TownWorkplaces sites,ApprenticeWorkplace base,ApprenticeWorkplace site){
        if(site.existingExcavation()==null||site.excavation().id()==null)return false;
        var own=new HashSet<>(ExcavationPlan.tunnel(site.area(),site.excavation().entrance(),site.excavation().layout()));
        boolean changed=false;
        for(var other:sites.tunnels(base.id())) {
            if(other.id().equals(site.id()))continue;
            var theirs=new HashSet<>(ExcavationPlan.tunnel(other.area(),other.excavation().entrance(),other.excavation().layout()));
            if(Collections.disjoint(own,theirs))continue;
            changed|=other.excavation().openDoorways(own);
            changed|=site.excavation().openDoorways(theirs);
        }
        return changed;
    }
    /**
     * A passage at another height that is in the way of a level route on one floor: its steps reach into the route's
     * foot or head room, or its own headroom is open where the route would need a floor.
     *
     * @param low    the lower of the two heights the passage joins
     * @param high   the higher of them, equal to {@code low} for a level passage
     * @param climbs the way its floor rises, null for a level passage
     */
    public record Passing(ApprenticeWorkplace passage,int low,int high,MineshaftSelection.Direction climbs){
        public boolean stairs(){return climbs!=null;}
        /** "Stairs up to 111", "Stairs down to 104", "Stairs between 104 and 111", or the passage's own name. */
        public String subject(int floor){
            if(!stairs())return passage.name();
            return floor==low?"Stairs up to "+high:floor==high?"Stairs down to "+low:"Stairs between "+low+" and "+high;
        }
        /** The map's hover text for a square the passage goes through. */
        public String hover(int floor){return stairs()?subject(floor)+" pass here":passage.name()+" passes here at height "+low;}
    }
    private static Passing passing(ApprenticeWorkplace passage){
        var plan=passage.existingExcavation();
        if(plan==null||plan.id()==null||plan.area()==null)return new Passing(passage,passage.area().floorY(),passage.area().floorY(),null);
        int low=Integer.MAX_VALUE,high=Integer.MIN_VALUE;
        for(var p:mapPoints(passage.area(),plan.entrance(),plan.layout())){low=Math.min(low,p.y());high=Math.max(high,p.y());}
        if(low>=high)return new Passing(passage,low==Integer.MAX_VALUE?passage.area().floorY():low,low==Integer.MAX_VALUE?passage.area().floorY():low,null);
        var travel=MineshaftSelection.direction(plan.entrance());
        boolean rising=plan.layout()==ExcavationPlan.Layout.DEEP_STAIRS_UP||plan.layout()==ExcavationPlan.Layout.STAIRS_UP;
        return new Passing(passage,low,high,rising?travel:opposite(travel));
    }
    public static MineshaftSelection.Direction opposite(MineshaftSelection.Direction direction){
        return switch(direction){
            case NORTH->MineshaftSelection.Direction.SOUTH;case SOUTH->MineshaftSelection.Direction.NORTH;
            case EAST->MineshaftSelection.Direction.WEST;case WEST->MineshaftSelection.Direction.EAST;
        };
    }
    /**
     * Every column of one floor that a passage at another height goes through, keyed by the column at that floor's
     * height. The map draws these so a stairway is seen on each floor it passes, not only on its two landings: seen
     * from its lower floor the squares under its first steps are otherwise drawn as free rock.
     */
    public static Map<Point,Passing> passing(TownWorkplaces sites,ApprenticeWorkplace base,int floor){
        var result=new LinkedHashMap<Point,Passing>();
        for(var tunnel:sites.tunnels(base.id())) {
            var plan=tunnel.excavation();var cells=ExcavationPlan.tunnel(tunnel.area(),plan.entrance(),plan.layout());
            var open=new HashSet<>(cells);var feet=new LinkedHashMap<Point,Integer>();
            for(var p:cells)feet.putIfAbsent(new Point(p.x(),floor,p.z()),plan.floorAt(p));
            Passing through=null;
            for(var column:feet.entrySet()) {
                int f=column.getValue();var at=column.getKey();
                if(f==floor)continue;
                // Its step, or the block under it, is in the route's foot or head room; or its headroom is the route's floor.
                boolean steps=f-1==floor||f-1==floor+1;
                boolean headroom=open.contains(new Point(at.x(),floor-1,at.z()));
                if(!steps&&!headroom)continue;
                if(through==null)through=passing(tunnel);
                result.putIfAbsent(at,through);
            }
        }
        var a=base.area();
        if(a.floorY()-1==floor||a.floorY()-1==floor+1) {
            var through=new Passing(base,a.floorY(),a.floorY(),null);
            for(int x=a.minX();x<a.maxX();x++)for(int z=a.minZ();z<a.maxZ();z++)result.putIfAbsent(new Point(x,floor,z),through);
        }
        return result;
    }
    /**
     * One block of a route's middle lane that an existing passage makes impossible.
     *
     * @param block which block of the route it is, counting from one at the start
     * @param under true when the passage is open where the route needs a floor, false when the passage's own floor
     *              stands in the route
     */
    public record Clash(Point at,int block,ApprenticeWorkplace passage,boolean under){}
    /** Every block of a route's middle lane that an existing passage makes impossible, nearest the start first. */
    public static List<Clash> clashes(TownWorkplaces sites,ApprenticeWorkplace base,UUID prerequisite,WorkplaceArea area,
        ExcavationPlan.Entrance entrance,ExcavationPlan.Layout layout){
        var left=leftAlone(sites,base,prerequisite);var walkways=walkways(sites,base,null);
        var open=new HashMap<Point,ApprenticeWorkplace>();
        for(var tunnel:sites.tunnels(base.id()))for(var p:ExcavationPlan.tunnel(tunnel.area(),tunnel.excavation().entrance(),tunnel.excavation().layout()))open.putIfAbsent(p,tunnel);
        boolean alongZ=entrance==ExcavationPlan.Entrance.NORTH||entrance==ExcavationPlan.Entrance.SOUTH;
        int center=centerLine(area,entrance);
        var found=new TreeMap<Integer,Clash>();
        for(var p:ExcavationPlan.tunnel(area,entrance,layout)) {
            if((alongZ?p.x():p.z())!=center)continue;
            int block=ExcavationPlan.depthOf(area,entrance,p)-2;
            if(found.containsKey(block))continue;
            int floor=ExcavationPlan.floorAt(area,entrance,layout,p);
            var below=open.get(new Point(p.x(),floor-1,p.z()));
            if(below!=null){found.put(block,new Clash(new Point(p.x(),floor,p.z()),block,below,true));continue;}
            if(p.y()>floor+1||left.contains(p))continue;
            var owner=walkways.get(p);
            if(owner!=null)found.put(block,new Clash(p,block,owner,false));
        }
        return List.copyOf(found.values());
    }
    /** The saved passage a start hangs from, or null when it is still the base's own floor. */
    public static UUID prerequisiteAt(TownWorkplaces sites,ApprenticeWorkplace base,Point start){
        var owner=mapConnections(sites,base).get(start);
        return owner!=null&&owner.equals(base.id())&&(base.existingExcavation()==null||base.excavation().id()==null)?null:owner;
    }
    private static boolean newGround(TownWorkplaces sites,ApprenticeWorkplace base,UUID prerequisite,WorkplaceArea area,
        ExcavationPlan.Entrance entrance,ExcavationPlan.Layout layout){
        var left=leftAlone(sites,base,prerequisite);var room=base.area();
        for(var p:ExcavationPlan.tunnel(area,entrance,layout))
            if(!left.contains(p)&&!(room.contains(p.x(),p.y(),p.z())))return true;
        return false;
    }
    /** Whether any lane of a route, not only its middle one, would have another passage's open space for a floor. */
    private static boolean opensUnder(TownWorkplaces sites,ApprenticeWorkplace base,WorkplaceArea area,ExcavationPlan.Entrance entrance,ExcavationPlan.Layout layout){
        var open=openSpace(sites,base,null);
        for(var p:ExcavationPlan.tunnel(area,entrance,layout))
            if(open.contains(new Point(p.x(),ExcavationPlan.floorAt(area,entrance,layout,p)-1,p.z())))return true;
        return false;
    }
    /**
     * Whether a route from this start is worth suggesting: its middle lane is clear of every other passage, no lane of
     * it would be left without a floor, and it reaches ground of its own. A step that keeps its block in a side lane,
     * as beside a landing, is no objection.
     */
    private static boolean clear(TownWorkplaces sites,ApprenticeWorkplace base,Point start,MineshaftSelection.Direction travel,int length,ExcavationPlan.Layout layout){
        try{
            var area=MineshaftSelection.area(start.x(),start.y()-1,start.z(),travel,length,layout);var entrance=MineshaftSelection.entrance(travel);
            var prerequisite=prerequisiteAt(sites,base,start);
            return clashes(sites,base,prerequisite,area,entrance,layout).isEmpty()&&!opensUnder(sites,base,area,entrance,layout)
                &&newGround(sites,base,prerequisite,area,entrance,layout);
        }catch(IllegalArgumentException outside){return false;}
    }
    /**
     * A change the player can make with one button, said in the button's own words. Exactly one of the two is set.
     *
     * @param length the shorter length that stops short of the passage in the way
     * @param start  the start beside it from which the same route is clear
     */
    public record Fix(String button,Integer length,Point start){}
    /** Why a route cannot be dug, the shortest way round it in real numbers, and the blocks that clash. */
    public record Advice(String message,Fix fix,List<Clash> clashes){}
    private static String way(MineshaftSelection.Direction direction){return direction.name().toLowerCase(Locale.ROOT);}
    private static String blocks(int count){return count+(count==1?" block":" blocks");}
    private static String list(List<String> words){
        if(words.size()==1)return words.getFirst();
        return String.join(", ",words.subList(0,words.size()-1))+" or "+words.getLast();
    }
    /**
     * Explains a route that would cut through another passage, and how to carry on: stop short of it, start beside
     * it, or dig another way. Null when nothing is in the route's way.
     */
    public static Advice advice(TownWorkplaces sites,ApprenticeWorkplace base,Point start,MineshaftSelection.Direction travel,int length,ExcavationPlan.Layout layout){
        List<Clash> clashes;
        try{
            var area=MineshaftSelection.area(start.x(),start.y()-1,start.z(),travel,length,layout);
            clashes=clashes(sites,base,prerequisiteAt(sites,base,start),area,MineshaftSelection.entrance(travel),layout);
        }catch(IllegalArgumentException outside){return null;}
        if(clashes.isEmpty())return null;
        var first=clashes.getFirst();int run=1;
        while(run<clashes.size()&&clashes.get(run).block()==first.block()+run&&clashes.get(run).passage()==first.passage()&&clashes.get(run).under()==first.under())run++;
        var through=first.passage()==base?new Passing(base,base.area().floorY(),base.area().floorY(),null):passing(first.passage());
        String subject=through.stairs()?through.subject(first.at().y()):first.under()?through.passage().name():"The floor of "+through.passage().name();
        boolean plural=through.stairs();
        String where=first.block()==1
            ?subject+(first.under()?(plural?" open under":" is open under"):(plural?" pass through":" lies in"))+" the first "+blocks(run)+" "+way(travel)+" of here."
            :subject+(first.under()?(plural?" open under":" is open under"):(plural?" cross":" crosses"))+" this route "+blocks(first.block())+" "+way(travel)+" of here.";
        // Stop short of it.
        boolean deep=layout==ExcavationPlan.Layout.DEEP_STAIRS_UP||layout==ExcavationPlan.Layout.DEEP_STAIRS_DOWN;
        int room=first.block()-1,shorter=room/3*3;
        Fix fix=null;String change=null;
        if(layout!=ExcavationPlan.Layout.ROOM&&shorter>=(deep?9:MineshaftSelection.MINIMUM_LENGTH)&&shorter<length&&clear(sites,base,start,travel,shorter,layout)) {
            fix=new Fix("Shorten to "+blocks(shorter),shorter,null);change="Make it "+blocks(shorter)+" long";
        }
        // Start beside it: the nearest place on a passage of this floor, straight north, east, south or west of here.
        if(fix==null) {
            Point best=null;int distance=Integer.MAX_VALUE;
            for(var candidate:mapConnections(sites,base).keySet()) {
                if(candidate.y()!=start.y()||candidate.equals(start))continue;
                int dx=candidate.x()-start.x(),dz=candidate.z()-start.z();
                if(dx!=0&&dz!=0)continue;
                int d=Math.abs(dx)+Math.abs(dz);
                if(d>8||d>distance||d==distance&&best!=null&&(candidate.x()>best.x()||candidate.x()==best.x()&&candidate.z()>=best.z()))continue;
                if(!clear(sites,base,candidate,travel,length,layout))continue;
                best=candidate;distance=d;
            }
            if(best!=null) {
                var toward=best.x()>start.x()?MineshaftSelection.Direction.EAST:best.x()<start.x()?MineshaftSelection.Direction.WEST
                    :best.z()>start.z()?MineshaftSelection.Direction.SOUTH:MineshaftSelection.Direction.NORTH;
                fix=new Fix("Move start "+blocks(distance)+" "+way(toward),null,best);change="Start "+blocks(distance)+" further "+way(toward);
            }
        }
        var ways=new ArrayList<String>();
        for(var other:MineshaftSelection.Direction.values())if(other!=travel&&clear(sites,base,start,other,length,layout))ways.add(way(other));
        String how=change!=null?change+(ways.isEmpty()?".":", or dig "+list(ways)+".")
            :!ways.isEmpty()?"Dig "+list(ways)+" from here.":"Choose another start.";
        return new Advice(where+" "+how,fix,clashes);
    }
    /** What a new tunnel would dig, or the one thing about it the player has to change. */
    public record Route(List<ExcavationPlan.Cell> cells,String issue){
        public Route{cells=List.copyOf(cells);}
        public boolean ready(){return issue==null&&!cells.isEmpty();}
    }
    /**
     * Works out a new tunnel's digging order from the rock in its way.
     *
     * <p>The block someone stands on in an existing passage is never dug. It is left standing inside the new tunnel,
     * which is how a tunnel leaves a stair's landing sideways: the step beside the landing keeps its block, and the
     * tunnel opens beyond it. Leaving a passage at its own height, in any direction, and crossing one at its own height
     * therefore always work, and two planned tunnels that cross share the rock where they meet.
     *
     * <p>The tunnel is refused only when such a block would stand in its own walkway, which means it was aimed through a
     * passage's floor: back under the stairs it starts from, or through a passage one or two blocks higher.
     */
    public static Route route(TownWorkplaces sites,ApprenticeWorkplace base,UUID prerequisite,WorkplaceArea area,
        ExcavationPlan.Entrance entrance,ExcavationPlan.Layout layout,List<ExcavationPlan.Cell> reviewed){
        String covered=coveredPassageIssue(sites,base,area,entrance,layout);
        if(covered!=null)return new Route(List.of(),covered);
        var left=leftAlone(sites,base,prerequisite);var walkways=walkways(sites,base,null);
        var cells=new ArrayList<ExcavationPlan.Cell>();var standing=new HashMap<Point,ApprenticeWorkplace>();
        for(var cell:reviewed) {
            if(left.contains(cell.point()))continue;
            var owner=walkways.get(cell.point());
            if(owner!=null)standing.put(cell.point(),owner);else cells.add(cell);
        }
        boolean alongZ=entrance==ExcavationPlan.Entrance.NORTH||entrance==ExcavationPlan.Entrance.SOUTH;
        int center=centerLine(area,entrance);
        for(var p:ExcavationPlan.tunnel(area,entrance,layout)) {
            // A worker needs the middle lane clear at foot and head height. A block left higher up is only a low ceiling.
            if((alongZ?p.x():p.z())!=center||p.y()>ExcavationPlan.floorAt(area,entrance,layout,p)+1)continue;
            var owner=standing.get(p);
            if(owner!=null)return new Route(cells,CUT_FLOOR+owner.name()+" at "+place(p)+". Choose another direction, or a start further from that passage.");
        }
        return new Route(cells,cells.isEmpty()?NO_NEW_ROCK:null);
    }
    public record MapStart(Point anchor,MineshaftSelection.Direction direction) {}
    /** Empty cells immediately beyond a passage extend from its edge, on this exact floor. */
    public static MapStart mapStart(Map<Point,UUID> connections,Point cell){
        var direct=mapConnection(connections,cell);
        if(direct!=null)return new MapStart(direct,null);
        Point best=null;int distance=Integer.MAX_VALUE;
        for(var p:connections.keySet()){
            if(p.y()!=cell.y())continue;
            int dx=cell.x()-p.x(),dz=cell.z()-p.z();
            boolean extension=Math.abs(dx)<=1&&Math.abs(dz)<=4||Math.abs(dz)<=1&&Math.abs(dx)<=4;
            int d=Math.abs(dx)+Math.abs(dz);
            if(extension&&d<distance){best=p;distance=d;}
        }
        if(best==null)return null;
        int dx=cell.x()-best.x(),dz=cell.z()-best.z();
        return new MapStart(best,Math.abs(dx)>Math.abs(dz)?(dx>0?MineshaftSelection.Direction.EAST:MineshaftSelection.Direction.WEST):(dz>0?MineshaftSelection.Direction.SOUTH:MineshaftSelection.Direction.NORTH));
    }
    /** Base, room and stair landing floors, excluding the intermediate stair steps. */
    public static int adjacentFloor(TownWorkplaces sites,ApprenticeWorkplace base,int current,boolean up){
        var levels=new TreeSet<Integer>();levels.add(base.area().floorY());
        for(var tunnel:sites.tunnels(base.id())){
            var points=mapPoints(tunnel.area(),tunnel.excavation().entrance(),tunnel.excavation().layout());
            points.stream().mapToInt(Point::y).min().ifPresent(levels::add);
            points.stream().mapToInt(Point::y).max().ifPresent(levels::add);
        }
        Integer next=up?levels.higher(current):levels.lower(current);
        return next==null?current:next;
    }
    public static Set<Point> protectedFloors(TownWorkplaces sites,ApprenticeWorkplace base){
        var result=new HashSet<Point>();
        for(var p:floors(sites,base).keySet())result.add(new Point(p.x(),p.y()-1,p.z()));
        return result;
    }
    /** A low landing shows up; its neighboring high landing shows down, on their own floors. */
    public static Map<Point,Integer> stairs(WorkplaceArea area,ExcavationPlan.Entrance entrance,ExcavationPlan.Layout layout){
        var heights=new HashMap<String,Point>();var result=new HashMap<Point,Integer>();
        if(layout!=ExcavationPlan.Layout.STAIRS_UP&&layout!=ExcavationPlan.Layout.STAIRS_DOWN&&layout!=ExcavationPlan.Layout.DEEP_STAIRS_UP&&layout!=ExcavationPlan.Layout.DEEP_STAIRS_DOWN)return result;
        for(var p:ExcavationPlan.tunnel(area,entrance,layout))heights.put(p.x()+","+p.z(),new Point(p.x(),ExcavationPlan.floorAt(area,entrance,layout,p),p.z()));
        for(var p:heights.values())for(var offset:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){
            var neighbor=heights.get((p.x()+offset[0])+","+(p.z()+offset[1]));
            if(neighbor!=null&&neighbor.y()!=p.y())result.merge(p,neighbor.y()>p.y()?1:2,(a,b)->a|b);
        }
        return result;
    }
    public static Map<Point,UUID> floors(TownWorkplaces sites,ApprenticeWorkplace base){
        var result=new LinkedHashMap<Point,UUID>();
        var a=base.area();
        for(int x=a.minX();x<a.maxX();x++)for(int z=a.minZ();z<a.maxZ();z++)result.put(new Point(x,a.floorY(),z),base.id());
        for(var tunnel:sites.tunnels(base.id())){
            var plan=tunnel.excavation();
            for(var p:ExcavationPlan.tunnel(tunnel.area(),plan.entrance(),plan.layout()))result.putIfAbsent(new Point(p.x(),plan.floorAt(p),p.z()),tunnel.id());
        }
        return result;
    }
    public static Set<Point> ancestorCells(TownWorkplaces sites,UUID parent){
        var cells=new HashSet<Point>();var visited=new HashSet<UUID>();
        while(parent!=null&&visited.add(parent)){
            var site=sites.find(parent);if(site==null)break;
            var plan=site.existingExcavation();
            if(plan!=null&&plan.id()!=null)cells.addAll(ExcavationPlan.tunnel(site.area(),plan.entrance(),plan.layout()));
            parent=site.minePrerequisiteId();
        }
        return cells;
    }
    public static List<ExcavationPlan.Cell> remaining(List<ExcavationPlan.Cell> reviewed,Set<Point> ancestors){return reviewed.stream().filter(c->!ancestors.contains(c.point())).toList();}
}
