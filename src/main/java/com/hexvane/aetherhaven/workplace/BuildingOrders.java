package com.hexvane.aetherhaven.workplace;

import java.util.*;

/**
 * Ordered paid construction projects. Apprentice effort belongs to one actual frontier cell.
 *
 * <p>A yard takes any number of builders. Each has their own cell and their own unfinished effort, two builders are
 * never on the same cell, and a cell is credited to whoever commits it first and to nobody after that.
 */
public final class BuildingOrders {
    public static final int CAPACITY=35;
    /** A builder who has not been seen at their cell for this long no longer keeps others off it. */
    static final long CREW_IDLE_MS=5_000;
    static final long CREW_FORGET_MS=60_000;
    public record Order(UUID id,UUID plot,UUID author) {}
    public record Cell(UUID order,int index,int x,int y,int z,String block,int rotation) {}
    /** One builder's unfinished effort. {@code seen} is wall time and is not saved: after a load every cell is free. */
    private static final class Effort {Cell cell;double work;transient long seen;}
    private List<Order> orders=new ArrayList<>();private long revision;private boolean paused;
    private Cell cell;private double work;private long contributions;
    /** Efforts of named builders, by townsfolk id. The unnamed slot above is kept for saves made before crews. */
    private Map<String,Effort> crew;
    private transient Cell committing;
    private transient Set<UUID> deferredCompleted;
    public List<Order> list(){return Collections.unmodifiableList(orders);}public long revision(){return revision;}
    public boolean paused(){return paused;}public Cell cell(){return cell;}public double work(){return Math.max(0,work);}public long contributions(){return Math.max(0,contributions);}
    public Order find(UUID id){return orders.stream().filter(o->o.id().equals(id)).findFirst().orElse(null);}
    /**
     * Queues a project at the end of the board as it is now. The board changes when a building is finished or a
     * builder moves on, and that is no reason to refuse a press: each one names its project and is judged alone.
     */
    public String add(UUID plot,UUID author) {
        if(plot==null||author==null)return "Choose a construction project.";
        if(orders.size()>=CAPACITY)return "This yard already has 35 queued projects.";
        if(orders.stream().anyMatch(o->o.plot().equals(plot)))return "This project is already queued here.";
        orders.add(new Order(UUID.randomUUID(),plot,author));revision++;return null;
    }
    public boolean remove(UUID id){if(find(id)==null)return false;orders.removeIf(o->o.id().equals(id));if(cell!=null&&cell.order().equals(id)){cell=null;work=0;}dropCrewWithoutOrder();revision++;return true;}
    /** Retire completed projects without changing other orders, attendance, pause state or earned credit. */
    public boolean removeCompleted(Set<UUID> plots) {
        if(plots.isEmpty())return false;
        Set<UUID> removable=plots;
        var pending=committing==null?null:find(committing.order());
        if(pending!=null&&plots.contains(pending.plot())) {
            if(deferredCompleted==null)deferredCompleted=new HashSet<>();
            deferredCompleted.add(pending.plot());
            removable=new HashSet<>(plots);removable.remove(pending.plot());
        }
        final Set<UUID> completed=removable;
        boolean focused=cell!=null&&orders.stream().anyMatch(o->completed.contains(o.plot())&&o.id().equals(cell.order()));
        if(!orders.removeIf(o->completed.contains(o.plot())))return false;
        if(focused){cell=null;work=0;}
        dropCrewWithoutOrder();
        revision++;return true;
    }
    /**
     * Makes room on a full board by dropping orders whose project is gone. The order being placed right now is kept,
     * for the same reason {@link #removeCompleted} defers it.
     */
    public boolean makeRoom(java.util.function.Predicate<UUID> projectGone) {
        var pending=committing==null?null:find(committing.order());boolean changed=false;
        for(var order:List.copyOf(orders)) {
            if(orders.size()<CAPACITY)break;
            if(order!=pending&&projectGone.test(order.plot())){orders.remove(order);changed=true;}
        }
        if(!changed)return false;
        if(cell!=null&&find(cell.order())==null){cell=null;work=0;}
        dropCrewWithoutOrder();revision++;return true;
    }
    /** Native chunk loads can drain world tasks before placement returns. Hold only the active order until credited. */
    boolean beginPlacement(Cell expected) {
        if(committing!=null||expected==null||paused||find(expected.order())==null||!Objects.equals(cell,expected))return false;
        committing=expected;return true;
    }
    boolean endPlacement() {
        committing=null;var completed=deferredCompleted;deferredCompleted=null;
        return completed!=null&&removeCompleted(completed);
    }
    /** Moves the named project one place, wherever it stands now. False when it is gone or already at that end. */
    public boolean move(UUID id,int delta){if(Math.abs(delta)!=1)return false;var order=find(id);int index=orders.indexOf(order);if(index<0||index+delta<0||index+delta>=orders.size())return false;Collections.swap(orders,index,index+delta);revision++;return true;}
    /** Sets the state the press asked for. False when the yard is already in it. */
    public boolean pause(boolean value){if(paused==value)return false;paused=value;revision++;return true;}
    public boolean focus(Cell next){if(next==null||find(next.order())==null||next.index()<0)return false;if(!next.equals(cell)){cell=next;work=0;}return true;}
    public void attend(Cell expected,double seconds,boolean eligible,double multiplier){if(expected!=null&&eligible&&!paused&&find(expected.order())!=null&&Objects.equals(cell,expected)&&Double.isFinite(seconds)&&Double.isFinite(multiplier))work=Math.min(30,work+Math.clamp(seconds,0,.5)*Math.clamp(multiplier,.1,5));}
    public boolean ready(Cell expected,double duration){return expected!=null&&!paused&&find(expected.order())!=null&&Objects.equals(cell,expected)&&Double.isFinite(duration)&&duration>0&&work()>=duration;}
    public boolean committed(Cell expected){if(expected==null||paused||find(expected.order())==null||!Objects.equals(cell,expected))return false;contributions++;work=0;cell=null;return true;}

    // Named builders. A null or blank name is the single unnamed slot above.
    private static boolean unnamed(String worker){return worker==null||worker.isBlank();}
    private static boolean sameSpot(Cell a,Cell b){return a!=null&&b!=null&&a.order().equals(b.order())&&a.index()==b.index();}
    private Effort effort(String worker){return crew==null?null:crew.get(worker);}
    private void dropCrewWithoutOrder(){if(crew!=null)crew.values().removeIf(e->e.cell==null||find(e.cell.order())==null);}
    public Cell cell(String worker){if(unnamed(worker))return cell;var e=effort(worker);return e==null?null:e.cell;}
    public double work(String worker){if(unnamed(worker))return work();var e=effort(worker);return e==null?0:Math.max(0,e.work);}
    public int crewSize(long now){return crew==null?0:(int)crew.values().stream().filter(e->e.cell!=null&&now-e.seen<=CREW_IDLE_MS).count();}
    /** True while a different builder is at this cell. Their effort stays theirs; nobody else may start on it. */
    public boolean heldByAnother(String worker,Cell target,long now) {
        if(target==null||crew==null)return false;
        for(var entry:crew.entrySet())if(!entry.getKey().equals(worker)&&sameSpot(entry.getValue().cell,target)&&now-entry.getValue().seen<=CREW_IDLE_MS)return true;
        return false;
    }
    /** Call every tick the builder is heading for or standing at the cell, so it stays theirs. */
    public boolean focus(String worker,Cell next,long now) {
        if(unnamed(worker))return focus(next);
        if(next==null||find(next.order())==null||next.index()<0||heldByAnother(worker,next,now))return false;
        if(crew==null)crew=new LinkedHashMap<>();
        crew.entrySet().removeIf(entry->!entry.getKey().equals(worker)&&(entry.getValue().cell==null||now-entry.getValue().seen>CREW_FORGET_MS));
        var mine=crew.computeIfAbsent(worker,key->new Effort());
        if(!next.equals(mine.cell)){mine.cell=next;mine.work=0;}
        mine.seen=now;return true;
    }
    public void attend(String worker,Cell expected,double seconds,boolean eligible,double multiplier,long now) {
        if(unnamed(worker)){attend(expected,seconds,eligible,multiplier);return;}
        var mine=effort(worker);
        if(mine==null||expected==null||!eligible||paused||find(expected.order())==null||!Objects.equals(mine.cell,expected)||!Double.isFinite(seconds)||!Double.isFinite(multiplier))return;
        mine.work=Math.min(30,mine.work+Math.clamp(seconds,0,.5)*Math.clamp(multiplier,.1,5));mine.seen=now;
    }
    public boolean ready(String worker,Cell expected,double duration) {
        if(unnamed(worker))return ready(expected,duration);
        var mine=effort(worker);
        return mine!=null&&expected!=null&&!paused&&find(expected.order())!=null&&Objects.equals(mine.cell,expected)&&Double.isFinite(duration)&&duration>0&&mine.work>=duration;
    }
    /** Credits one placed piece to the yard. Any other effort spent on that same cell is discarded, never paid. */
    public boolean committed(String worker,Cell expected) {
        if(unnamed(worker)){if(!committed(expected))return false;}
        else {
            var mine=effort(worker);
            if(mine==null||expected==null||paused||find(expected.order())==null||!Objects.equals(mine.cell,expected))return false;
            contributions++;crew.remove(worker);
            if(sameSpot(cell,expected)){cell=null;work=0;}
        }
        if(crew!=null)crew.values().removeIf(e->sameSpot(e.cell,expected));
        return true;
    }
    boolean beginPlacement(String worker,Cell expected) {
        if(unnamed(worker))return beginPlacement(expected);
        if(committing!=null||expected==null||paused||find(expected.order())==null||!Objects.equals(cell(worker),expected))return false;
        committing=expected;return true;
    }
    /** The builder left for other work or rest. Their cell is free at once and unfinished effort is dropped. */
    public void release(String worker){if(!unnamed(worker)&&crew!=null)crew.remove(worker);}
    public static double duration(int skill,int benchTier){return Math.max(1.25,5-.3*Math.clamp(skill,1,10)-.25*(Math.clamp(benchTier,1,3)-1));}
}
