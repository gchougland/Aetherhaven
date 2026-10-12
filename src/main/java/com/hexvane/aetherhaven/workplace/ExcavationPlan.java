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
        STAIRS_DOWN("Stairs down", "Descend one block every four rows, up to two blocks below the entrance."),
        DEEP_STAIRS_UP("Stairs up", "Rise seven blocks to a separate floor. At least nine blocks long."),
        DEEP_STAIRS_DOWN("Stairs down", "Descend seven blocks to a separate floor. At least nine blocks long."),
        ROOM("Room", "A room nine blocks wide, nine long and five high. The entrance is centered on its near wall.");
        public final String label, description;
        Layout(String label,String description){this.label=label;this.description=description;}
    }
    public record Cell(Point point,String block,int rotation,int skill) {}
    /**
     * What a planned cell holds when the work reaches it. The order was written from the ground as it was on the day it
     * was planned; the ground a miner meets is the one that counts.
     *
     * @param breakAt  the block to break to clear the cell: the cell itself, or the base of a larger block it is part of
     * @param terrain  plain rock, ore or soil, as opposed to something a player built or furnished
     * @param obstacle what must not be broken there, in words a player can read
     */
    public record Found(Kind kind,String block,int rotation,int skill,Point breakAt,boolean terrain,String obstacle) {
        public enum Kind { UNLOADED, EMPTY, ROCK, OBSTACLE }
        public static final Found UNLOADED=new Found(Kind.UNLOADED,null,0,0,null,false,null);
        public static final Found EMPTY=new Found(Kind.EMPTY,null,0,0,null,false,null);
        public static Found rock(String block,int rotation,int skill,Point breakAt,boolean terrain){return new Found(Kind.ROCK,block,rotation,skill,breakAt,terrain,null);}
        public static Found obstacle(String what){return new Found(Kind.OBSTACLE,null,0,0,null,false,what);}
    }
    // Cells a miner had to leave because something there must not be broken. They are dug once it is gone.
    private List<Cell> blocked=new ArrayList<>();
    // Set by any progress made under these rules, so an order finished long ago is never swept again.
    private boolean sweepArmed;
    private int fittingsSkipped;
    private transient long blockedCheckedAt;
    private transient int failedBreaks;
    private List<Cell> blockedCells(){if(blocked==null)blocked=new ArrayList<>();return blocked;}
    public List<Cell> blocked(){return Collections.unmodifiableList(blockedCells());}
    /** Every cell this order has still to dig, including the ones waiting for an obstruction to go. */
    public Set<Point> pendingPoints() {
        var points=new HashSet<Point>();
        for(int i=cursor;i<cells.size();i++)points.add(cells.get(i).point());
        for(var cell:blockedCells())points.add(cell.point());
        for(var cell:unbuildCells())points.add(cell.point());
        return points;
    }
    public int fittingsSkipped(){return fittingsSkipped;}
    // Frame pieces and wall torches already built where another passage now opens. They are taken down again.
    private List<Cell> unbuild=new ArrayList<>();
    private transient boolean doorwaysChecked;
    private transient long junctionCheckedAt;
    private List<Cell> unbuildCells(){if(unbuild==null)unbuild=new ArrayList<>();return unbuild;}
    public List<Cell> unbuild(){return Collections.unmodifiableList(unbuildCells());}
    /** True the first time it is asked in a session: openings made before this rule existed are checked once. */
    public boolean claimDoorwayCheck(){if(doorwaysChecked)return false;doorwaysChecked=true;return true;}
    /** Anything left for a miner to do here: digging, something set aside, a piece to take down or a fitting to place. */
    public boolean unfinished(){return id!=null&&(next()!=null||nextFitting()!=null||!blockedCells().isEmpty()||!unbuildCells().isEmpty());}
    /** The shared ground where this passage meets another is looked over every few seconds while work goes on. */
    public boolean junctionCheckDue(long now){if(now<junctionCheckedAt)return false;junctionCheckedAt=now+3000;return true;}
    /**
     * Keeps posts out of doorways. A frame with any piece standing in an opening another passage makes here is left
     * out whole, so no half frame remains, and so is a wall torch on the wall that is opened. Pieces not yet placed
     * simply leave the order. Pieces already placed are listed to be taken down, and their beams come back.
     *
     * @param opening the other passage's open space
     * @return whether anything changed
     */
    public boolean openDoorways(Set<Point> opening) {
        if(id==null||fittings==null||fittings.isEmpty()||opening==null||opening.isEmpty())return false;
        var doomed=ExcavationFittings.inDoorways(fittings,entrance,opening);
        if(doomed.isEmpty())return false;
        var kept=new ArrayList<ExcavationFittings.Fitting>();int placed=fitted;
        for(int i=0;i<fittings.size();i++) {
            var fitting=fittings.get(i);
            if(!doomed.contains(fitting)){kept.add(fitting);continue;}
            if(i<fitted){placed--;unbuildCells().add(new Cell(fitting.point(),fitting.block(),fitting.rotation(),1));}
        }
        fittings=kept;fitted=placed;work=0;revision++;return true;
    }
    /** A piece listed to be taken down is queued once the ground shows it still stands. One that has gone is forgotten. */
    public void resolveUnbuild(Cell cell,boolean standing) {
        if(!unbuildCells().remove(cell)||!standing)return;
        requeue(cell);
    }
    /**
     * Puts rock found in the way at the front of the order, so it is cleared before anything behind it. Used where
     * this passage meets another: rock that turns up there would otherwise close the junction.
     */
    public boolean requeueFirst(Cell cell) {
        if(pending||!requeue(cell))return false;
        cells.add(cursor,cells.removeLast());forgetPreparedWork();return true;
    }
    private void forgetPreparedWork(){drops.clear();dropsPrepared=false;work=0;failedBreaks=0;}
    /**
     * An order paused by the old rule, which stopped a tunnel for good when a block in it was placed or broken, carries
     * on by itself. Nothing in the planner could review such a tunnel again.
     */
    public boolean resumeAfterEdit(){if(!edited)return false;edited=false;paused=false;revision++;return true;}
    /**
     * Brings the order up to date with the ground. A cell that is already open is passed over, a cell that holds a
     * different block is dug as that block, and a cell holding something that must not be broken is set aside so the
     * rest of the tunnel is still dug. Cells set aside come back once the obstruction has gone.
     *
     * @return whether the order changed
     */
    public boolean survey(java.util.function.Function<Point,Found> look,long now) {
        if(id==null||pending)return false;
        boolean changed=false;
        next(); // A legacy room settles its digging order before any cell is judged.
        if(cursor>=cells.size()&&!blockedCells().isEmpty()&&now>=blockedCheckedAt) {
            blockedCheckedAt=now+2000;
            for(var waiting=blocked.iterator();waiting.hasNext();) {
                var cell=waiting.next();var found=look.apply(cell.point());
                if(found.kind()==Found.Kind.UNLOADED||found.kind()==Found.Kind.OBSTACLE)continue;
                waiting.remove();changed=true;
                if(found.kind()==Found.Kind.ROCK)cells.add(new Cell(cell.point(),found.block(),found.rotation(),found.skill()));
            }
        }
        while(cursor<cells.size()) {
            var cell=cells.get(cursor);var found=look.apply(cell.point());
            if(found.kind()==Found.Kind.UNLOADED)break;
            if(found.kind()==Found.Kind.EMPTY){cursor++;forgetPreparedWork();sweepArmed=true;changed=true;continue;}
            if(found.kind()==Found.Kind.OBSTACLE){blockedCells().add(cells.remove(cursor));forgetPreparedWork();changed=true;continue;}
            if(!cell.block().equals(found.block())||cell.rotation()!=found.rotation()||cell.skill()!=found.skill()) {
                cells.set(cursor,new Cell(cell.point(),found.block(),found.rotation(),found.skill()));forgetPreparedWork();sweepArmed=true;changed=true;
            }
            break;
        }
        return changed;
    }
    /** True once, when the listed cells are done: the passage is checked for rock that arrived after planning. */
    public boolean sweepDue(){return sweepArmed&&id!=null&&!pending&&next()==null;}
    public void sweep(List<Cell> arrived) {
        if(!sweepDue())return;
        sweepArmed=false;
        for(var cell:arrived)requeue(cell);
    }
    /** Adds rock found in the passage to the end of the order. Used for a fitting position that has filled in. */
    public boolean requeue(Cell cell) {
        if(id==null||cell==null||cell.point()==null||cell.block()==null||cell.block().isBlank()||cell.skill()<1||cell.skill()>10||area==null
            ||!area.contains(cell.point().x(),cell.point().y(),cell.point().z()))return false;
        for(int i=cursor;i<cells.size();i++)if(cells.get(i).point().equals(cell.point()))return false;
        if(blockedCells().stream().anyMatch(c->c.point().equals(cell.point())))return false;
        cells.add(cell);return true;
    }
    /** A fitting that is already there, or that has nowhere to go, is passed over rather than holding up the rest. */
    public boolean passFitting(ExcavationFittings.Fitting fitting,boolean present) {
        if(fitting==null||!fitting.equals(nextFitting())||next()!=null)return false;
        fitted++;if(!present)fittingsSkipped++;work=0;revision++;return true;
    }
    /** What the remaining fittings will take from town storage, by item, in the order they are fitted. */
    public Map<String,Integer> fittingsNeeded() {
        var needed=new LinkedHashMap<String,Integer>();
        for(int i=fitted;i<fittingsTotal();i++)needed.merge(fittings.get(i).item(),1,Integer::sum);
        return needed;
    }
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
    private boolean dropsPrepared;
    // A legacy room may already have rolled its inaccessible corner's drops.
    // Keep that exact roll and accumulated work until its cell is reached in the new order.
    private record DeferredCell(Point point,Map<String,Integer> drops,double work) {}
    private DeferredCell deferredRoomCell;
    private transient boolean roomOrderChecked;
    public boolean dropsPrepared(){return dropsPrepared||!drops.isEmpty();}
    private Map<String,Integer> drops=new LinkedHashMap<>(),cargo=new LinkedHashMap<>();
    // Each tunnel contributes to its base workplace's displayed buffer. Keeping its ledger here
    // preserves the source order and prevents deletion/replacement from discarding its products.
    private Map<String,Integer> output=new LinkedHashMap<>();
    private int carryLimit=25,cargoWork;
    private boolean haulingOutput,unloading;
    public static int carryCapacity(int miningLevel){return 25+10*(Math.clamp(miningLevel,1,10)-1);}
    public void carryLevel(int miningLevel){carryLimit=carryCapacity(miningLevel);}
    public int cargoCount(){return cargo.values().stream().mapToInt(Integer::intValue).sum();}
    public Map<String,Integer> output(){if(output==null)output=new LinkedHashMap<>();return Collections.unmodifiableMap(output);}
    public int outputCount(){return output().values().stream().mapToInt(Integer::intValue).sum();}
    public void moveOutputTo(ExcavationPlan destination){
        if(destination==this)return;
        var merged=new LinkedHashMap<>(destination.output());
        output().forEach((item,count)->merged.merge(item,count,Math::addExact));
        destination.output=merged;output.clear();
    }
    public boolean haulingOutput(){return haulingOutput;}
    public void returnLegacyDelivery(){if(haulingOutput){haulingOutput=false;unloading=true;}}
    public long withdrawOutput(String item,long count){int take=(int)Math.min(Math.max(0,count),output().getOrDefault(item,0));if(take>0){output.compute(item,(k,v)->v-take);if(output.get(item)==0)output.remove(item);}return take;}
    public boolean continueLoad(ExcavationPlan next,String worker){
        if(next==null||next==this||pending||haulingOutput||unloading||cargo.isEmpty()||!Objects.equals(carrier,worker)||next()!=null||nextFitting()!=null||paused||edited
            ||next.heldCargo()||next.paused()||next.edited()||next.next()==null||cargoCount()>=Math.max(25,carryLimit))return false;
        next.cargo.putAll(cargo);next.carrier=carrier;next.cargoPoint=cargoPoint;next.cargoWork=cargoWork();next.carryLimit=carryLimit;clearCargo();return true;
    }
    public int cargoWork(){return haulingOutput||unloading?0:cargoWork>0?cargoWork:cargo.isEmpty()?0:1;}
    private boolean canWork(){return !pending&&!haulingOutput&&!unloading&&(cargo.isEmpty()||carrier!=null&&cargoCount()<Math.max(25,carryLimit));}
    public boolean loadReady(){return !cargo.isEmpty()&&(haulingOutput||unloading||paused||edited||next()==null||cargoCount()>=Math.max(25,carryLimit)||cargoCount()+drops.values().stream().mapToInt(Integer::intValue).sum()>Math.max(25,carryLimit));}
    public boolean deposit(UUID expected){
        if(!Objects.equals(id,expected)||cargo.isEmpty()||haulingOutput)return false;
        output();for(var e:cargo.entrySet())output.merge(e.getKey(),e.getValue(),Math::addExact);
        clearCargo();return true;
    }
    public boolean pickupOutput(UUID expected,String worker,Point standing,Map<String,Integer> available,int skill){
        if(!Objects.equals(id,expected)||heldCargo()||worker==null||worker.isBlank()||standing==null)return false;
        int room=carryCapacity(skill);var selected=new LinkedHashMap<String,Integer>();
        for(var e:output().entrySet()){
            int amount=Math.min(room,Math.min(e.getValue(),Math.max(0,available.getOrDefault(e.getKey(),0))));
            if(amount>0){selected.put(e.getKey(),amount);room-=amount;}
        }
        if(selected.isEmpty())return false;
        for(var e:selected.entrySet()){output.compute(e.getKey(),(k,v)->v-e.getValue());if(output.get(e.getKey())==0)output.remove(e.getKey());}
        cargo.putAll(selected);carrier=worker;cargoPoint=standing;cargoWork=0;haulingOutput=true;unloading=false;return true;
    }
    /** Delivered amounts are removed once. Any remainder must physically return to the base. */
    public boolean acceptOutputDelivery(UUID expected,Map<String,Integer> accepted){
        if(!Objects.equals(id,expected)||!haulingOutput||cargo.isEmpty()||accepted==null||accepted.entrySet().stream().anyMatch(e->e.getValue()<0||e.getValue()>cargo.getOrDefault(e.getKey(),0)))return false;
        for(var e:accepted.entrySet()){cargo.computeIfPresent(e.getKey(),(k,v)->v-e.getValue());if(cargo.getOrDefault(e.getKey(),0)==0)cargo.remove(e.getKey());}
        haulingOutput=false;unloading=!cargo.isEmpty();cargoWork=0;
        if(cargo.isEmpty())clearCargo();return true;
    }
    private void clearCargo(){cargo.clear();carrier=null;cargoPoint=null;cargoWork=0;haulingOutput=false;unloading=false;}
    public boolean heldCargo(){return pending||!cargo.isEmpty();}
    public Point cargoPoint(){return cargoPoint;}public String carrier(){return carrier;}
    public UUID id(){return id;}public UUID author(){return author;}public UUID mine(){return mine;}
    public WorkplaceArea area(){return area;}public Entrance entrance(){return entrance;}
    public Layout layout(){return layout==null?Layout.STRAIGHT:layout;}
    /** Unfinished decorations do not block access to a fully excavated passage. */
    public boolean passageReady(){return id()!=null&&next()==null&&!pending()&&!heldCargo()&&unbuildCells().isEmpty();}
    public int floorAt(Point point){return floorAt(area,entrance,layout(),point);}
    /**
     * Where a worker's feet belong in this passage: the three approach rows in front of it, and every planned column at
     * its floor height. On stairs that makes the stand the step before the one being dug. Empty for a plan with no area.
     */
    public Set<Point> floorCells() {
        if(area==null||entrance==null)return Set.of();
        var cells=new LinkedHashSet<Point>();
        for(var p:tunnel(area,entrance,layout()))cells.add(new Point(p.x(),floorAt(area,entrance,layout(),p),p.z()));
        if(cells.isEmpty())return Set.of();
        for(int d=0;d<=2;d++)for(int w=-1;w<=1;w++)cells.add(point(area,entrance,layout(),d,w,0));
        return cells;
    }
    public long revision(){return revision;}public boolean paused(){return paused;}public boolean edited(){return edited;}
    public boolean pending(){return pending;}public boolean held(){return heldCargo()||!output().isEmpty();}
    public int total(){return cells.size()+blockedCells().size();}public int completed(){return cursor;}
    public Cell next(){normalizeRoomOrder();return cursor<cells.size()?cells.get(cursor):null;}public double work(){return Math.max(0,work);}
    private void normalizeRoomOrder(){
        if(roomOrderChecked||pending||layout()!=Layout.ROOM||area==null||cursor>=cells.size())return;
        roomOrderChecked=true;
        var rank=new HashMap<Point,Integer>();var order=tunnel(area,entrance,Layout.ROOM);
        for(int i=0;i<order.size();i++)rank.put(order.get(i),i);
        var previous=cells.get(cursor);
        cells.subList(cursor,cells.size()).sort(Comparator.comparingInt(c->rank.getOrDefault(c.point(),Integer.MAX_VALUE)));
        if(!previous.equals(cells.get(cursor))){
            if(dropsPrepared())deferredRoomCell=new DeferredCell(previous.point(),new LinkedHashMap<>(drops),work);
            drops.clear();dropsPrepared=false;work=0;revision++;
        }
    }
    public boolean prepareNext(java.util.function.Supplier<Map<String,Integer>> roll){
        var cell=next();if(cell==null||dropsPrepared())return false;
        if(deferredRoomCell!=null&&deferredRoomCell.point().equals(cell.point())){
            if(!prepare(deferredRoomCell.drops()))return false;
            work=deferredRoomCell.work();deferredRoomCell=null;return true;
        }
        return prepare(roll.get());
    }
    public Map<String,Integer> drops(){return Collections.unmodifiableMap(drops);}public Map<String,Integer> cargo(){return Collections.unmodifiableMap(cargo);}
    public String start(long expected,UUID author,UUID mine,WorkplaceArea area,Entrance entrance,List<Cell> reviewed) {
        return start(expected,author,mine,area,entrance,Layout.STRAIGHT,reviewed);
    }
    public String start(long expected,UUID author,UUID mine,WorkplaceArea area,Entrance entrance,Layout layout,List<Cell> reviewed) {
        return start(expected,author,mine,area,entrance,layout,reviewed,ExcavationFittings.Mode.NONE);
    }
    public String start(long expected,UUID author,UUID mine,WorkplaceArea area,Entrance entrance,Layout layout,List<Cell> reviewed,ExcavationFittings.Mode fittingsMode) {
        if(expected!=revision)return "A miner worked on this tunnel while the page was open. Choose the same button again.";
        if(held())return "Let the Apprentice deliver the collected material first.";
        if(author==null||mine==null||area==null||!area.valid()||entrance==null||layout==null||reviewed==null||reviewed.isEmpty()||reviewed.size()>900)return "Choose a tunnel with blocks left to excavate.";
        var points=new HashSet<Point>();
        for(var cell:reviewed)if(cell==null||cell.point()==null||cell.block()==null||cell.block().isBlank()||cell.skill()<1||cell.skill()>10||!points.add(cell.point())||!area.contains(cell.point().x(),cell.point().y(),cell.point().z()))return "This tunnel could not be read. Choose its start on the map again.";
        this.id=UUID.randomUUID();this.author=author;this.mine=mine;this.area=area;this.entrance=entrance;this.layout=layout;cells=new ArrayList<>(reviewed);cursor=0;work=0;drops.clear();dropsPrepared=false;paused=false;edited=false;
        blocked=new ArrayList<>();unbuild=new ArrayList<>();sweepArmed=true;fittingsSkipped=0;failedBreaks=0;blockedCheckedAt=0;junctionCheckedAt=0;
        deferredRoomCell=null;roomOrderChecked=false;normalizeRoomOrder();
        this.fittingsMode=fittingsMode==null?ExcavationFittings.Mode.NONE:fittingsMode;fittings=new ArrayList<>(ExcavationFittings.plan(area,entrance,layout,this.fittingsMode));fitted=0;revision++;return null;
    }
    public boolean pause(long expected,boolean value){if(expected!=revision||id==null)return false;if(!value)edited=false;paused=value;revision++;return true;}
    public boolean prepare(Map<String,Integer> rolled) {
        if(next()==null||!canWork()||paused||dropsPrepared()||rolled==null||rolled.size()>8)return false;
        if(rolled.entrySet().stream().anyMatch(e->e.getKey()==null||e.getKey().isBlank()||e.getValue()==null||e.getValue()<1||e.getValue()>64))return false;
        drops.putAll(rolled);dropsPrepared=true;return true;
    }
    public void attend(double seconds,boolean eligible,double multiplier){if(eligible&&!paused&&canWork()&&(next()!=null&&dropsPrepared()||next()==null&&nextFitting()!=null)&&Double.isFinite(seconds)&&Double.isFinite(multiplier))work=Math.min(120,work+Math.clamp(seconds,0,.5)*Math.clamp(multiplier,.1,5));}
    public boolean fit(UUID expected,ExcavationFittings.Fitting fitting,BooleanSupplier place) {
        if(!Objects.equals(id,expected)||fitting==null||!fitting.equals(nextFitting())||next()!=null||heldCargo()||paused||edited||work()<3||!place.getAsBoolean())return false;
        fitted++;work=0;revision++;return true;
    }
    public boolean begin(UUID expected,double required,Point standing,BooleanSupplier remove) {
        if(!Objects.equals(id,expected)||paused||!canWork()||next()==null||!dropsPrepared()||cargoCount()+drops.values().stream().mapToInt(Integer::intValue).sum()>Math.max(25,carryLimit)||!Double.isFinite(required)||required<=0||work()<required)return false;
        if(!withinWorkReach(standing,next().point()))return false;
        pending=true;pendingCargoPoint=standing;
        try{if(remove.getAsBoolean())return true;}catch(RuntimeException error){pending=false;pendingCargoPoint=null;throw error;}
        pending=false;pendingCargoPoint=null;return false;
    }
    /**
     * Null means unavailable. A cell that is still solid pays nothing and is simply dug again: something may have fallen
     * into it. A cell that will not break after three tries is set aside so it cannot hold up the rest of the tunnel.
     */
    public boolean settle(UUID expected,Boolean empty) {
        if(!Objects.equals(id,expected)||!pending||empty==null)return false;
        var confirmed=next(); // Do not reorder a legacy room until its in-flight cell has settled.
        pending=false;
        if(empty){cargoPoint=pendingCargoPoint!=null?pendingCargoPoint:new Point(confirmed.point().x(),floorAt(confirmed.point()),confirmed.point().z());if(cargo.isEmpty())carrier=null;for(var e:drops.entrySet())cargo.merge(e.getKey(),e.getValue(),Math::addExact);cargoWork++;cursor++;work=0;drops.clear();dropsPrepared=false;sweepArmed=true;failedBreaks=0;}
        else {
            work=0;
            if(++failedBreaks>=3){blockedCells().add(cells.remove(cursor));forgetPreparedWork();}
        }
        pendingCargoPoint=null;
        return true;
    }
    public boolean pickup(UUID expected,String worker,Point standing) {
        if(!Objects.equals(id,expected)||cargo.isEmpty()||worker==null||worker.isBlank()||standing==null||cargoPoint==null)return false;
        if(!withinPickupReach(standing,cargoPoint))return false;
        carrier=worker;cargoPoint=standing;return true;
    }
    public boolean withinWorkReach(Point standing,Point point) {
        boolean tallStair=area!=null&&layout()!=Layout.ROOM&&passageHeight(area,layout())==4;
        if(layout()!=Layout.ROOM&&!tallStair)return withinPickupReach(standing,point);
        if(standing==null||point==null)return false;
        long dx=(long)standing.x()-point.x(),dy=(long)standing.y()-point.y(),dz=(long)standing.z()-point.z();
        return Math.abs(dx)<=4&&Math.abs(dz)<=4&&Math.abs(dy)<=5&&dx*dx+dy*dy+dz*dz<=(tallStair?25:36);
    }
    public static boolean withinPickupReach(Point standing,Point point) {
        if(standing==null||point==null)return false;
        long dx=(long)standing.x()-point.x(),dy=(long)standing.y()-point.y(),dz=(long)standing.z()-point.z();
        return Math.abs(dx)<=4&&Math.abs(dy)<=4&&Math.abs(dz)<=4&&dx*dx+dy*dy+dz*dz<=16;
    }
    public void trackCarrier(String worker,Point standing){if(worker!=null&&worker.equals(carrier)&&!cargo.isEmpty()&&standing!=null)cargoPoint=standing;}
    public boolean delivered(UUID expected,BooleanSupplier store) {
        if(!Objects.equals(id,expected)||cargo.isEmpty()||!store.getAsBoolean())return false;clearCargo();return true;
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
        boolean deep=layout==Layout.DEEP_STAIRS_UP||layout==Layout.DEEP_STAIRS_DOWN;
        if(deep&&(a.height()<10||depth<12))return List.of();
        var points=new ArrayList<Point>();int length=layout==Layout.ROOM?9:Math.min(layout==Layout.BRANCHES?16:32,depth-3);
        int half=layout==Layout.ROOM?4:1;
        if(layout==Layout.ROOM&&(width<9||depth<12))return List.of();
        if(layout==Layout.ROOM){
            // Open a walkable doorway, then widen each face before advancing.
            // Starting at the upper outside corner requires mining through solid rock.
            for(int d=3;d<3+length;d++)for(int w:new int[]{0,-1,1,-2,2,-3,3,-4,4})for(int y:new int[]{2,1,0,3,4})
                points.add(point(a,side,layout,d,w,y));
        }else for(int d=3;d<3+length;d++)for(int y=rowHeight(a,layout,d)-1;y>=0;y--)for(int w=-half;w<=half;w++) {
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
    public static int passageHeight(WorkplaceArea area,Layout layout){
        return switch(layout){
            case ROOM -> 5;
            case DEEP_STAIRS_UP,DEEP_STAIRS_DOWN -> area.height()>=11?4:3;
            case STAIRS_UP,STAIRS_DOWN -> area.height()>=6?4:3;
            default -> 3;
        };
    }
    /** The first two rows meet an ordinary three block tunnel without a raised doorway. */
    public static int rowHeight(WorkplaceArea area,Layout layout,int depth){
        int height=passageHeight(area,layout);
        return height==4&&depth<5?3:height;
    }
    public static List<Point> floors(WorkplaceArea area,Entrance entrance,Layout layout){
        var floors=new LinkedHashSet<Point>();
        for(var p:tunnel(area,entrance,layout))floors.add(new Point(p.x(),floorAt(area,entrance,layout,p)-1,p.z()));
        return List.copyOf(floors);
    }
    private static Point point(WorkplaceArea a,Entrance side,Layout layout,int depth,int offset,int height) {
        boolean alongZ=side==Entrance.NORTH||side==Entrance.SOUTH;
        int x=alongZ?a.minX()+(a.maxX()-a.minX()-1)/2+offset:side==Entrance.WEST?a.minX()+depth:a.maxX()-1-depth;
        int z=!alongZ?a.minZ()+(a.maxZ()-a.minZ()-1)/2+offset:side==Entrance.NORTH?a.minZ()+depth:a.maxZ()-1-depth;
        var horizontal=new Point(x,a.floorY(),z);
        return new Point(x,floorAt(a,side,layout,horizontal)+height,z);
    }
    /** How far into the workplace a point lies from its entrance edge. The first row that is dug is three. */
    public static int depthOf(WorkplaceArea a,Entrance side,Point point) {
        return switch(side){case NORTH->point.z()-a.minZ();case SOUTH->a.maxZ()-1-point.z();case WEST->point.x()-a.minX();case EAST->a.maxX()-1-point.x();};
    }
    /** Feet height, not the supporting block. All steps stay inside the workplace's five block band. */
    public static int floorAt(WorkplaceArea a,Entrance side,Layout layout,Point point) {
        int depth=switch(side){case NORTH->point.z()-a.minZ();case SOUTH->a.maxZ()-1-point.z();case WEST->point.x()-a.minX();case EAST->a.maxX()-1-point.x();};
        int rise=Math.clamp((depth-3)/4,0,2);
        int length=Math.min(32,(side==Entrance.NORTH||side==Entrance.SOUTH?a.maxZ()-a.minZ():a.maxX()-a.minX())-3);
        int deepRise=Math.clamp((depth-3)*7/Math.max(1,length-1),0,7);
        return a.floorY()+switch(layout){case STAIRS_UP->rise;case STAIRS_DOWN->2-rise;case DEEP_STAIRS_UP->deepRise;case DEEP_STAIRS_DOWN->7-deepRise;default->0;};
    }
}
