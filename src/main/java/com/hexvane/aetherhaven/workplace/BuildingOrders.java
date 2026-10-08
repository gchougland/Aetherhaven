package com.hexvane.aetherhaven.workplace;

import java.util.*;

/** Ordered paid construction projects. Apprentice effort belongs to one actual frontier cell. */
public final class BuildingOrders {
    public record Order(UUID id,UUID plot,UUID author) {}
    public record Cell(UUID order,int index,int x,int y,int z,String block,int rotation) {}
    private List<Order> orders=new ArrayList<>();private long revision;private boolean paused;
    private Cell cell;private double work;private long contributions;
    private transient Cell committing;
    private transient Set<UUID> deferredCompleted;
    public List<Order> list(){return Collections.unmodifiableList(orders);}public long revision(){return revision;}
    public boolean paused(){return paused;}public Cell cell(){return cell;}public double work(){return Math.max(0,work);}public long contributions(){return Math.max(0,contributions);}
    public Order find(UUID id){return orders.stream().filter(o->o.id().equals(id)).findFirst().orElse(null);}
    public String add(long expected,UUID plot,UUID author) {
        if(expected!=revision)return "The project queue changed. Review it again.";
        if(plot==null||author==null)return "Choose a construction project.";
        if(orders.size()>=35)return "This yard already has 35 queued projects.";
        if(orders.stream().anyMatch(o->o.plot().equals(plot)))return "This project is already queued here.";
        orders.add(new Order(UUID.randomUUID(),plot,author));revision++;return null;
    }
    public boolean remove(long expected,UUID id){if(expected!=revision||find(id)==null)return false;orders.removeIf(o->o.id().equals(id));if(cell!=null&&cell.order().equals(id)){cell=null;work=0;}revision++;return true;}
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
        revision++;return true;
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
    public boolean move(long expected,UUID id,int delta){if(expected!=revision||Math.abs(delta)!=1)return false;var order=find(id);int index=orders.indexOf(order);if(index<0||index+delta<0||index+delta>=orders.size())return false;Collections.swap(orders,index,index+delta);revision++;return true;}
    public boolean pause(long expected,boolean value){if(expected!=revision)return false;paused=value;revision++;return true;}
    public boolean focus(Cell next){if(next==null||find(next.order())==null||next.index()<0)return false;if(!next.equals(cell)){cell=next;work=0;}return true;}
    public void attend(Cell expected,double seconds,boolean eligible,double multiplier){if(expected!=null&&eligible&&!paused&&find(expected.order())!=null&&Objects.equals(cell,expected)&&Double.isFinite(seconds)&&Double.isFinite(multiplier))work=Math.min(30,work+Math.clamp(seconds,0,.5)*Math.clamp(multiplier,.1,5));}
    public boolean ready(Cell expected,double duration){return expected!=null&&!paused&&find(expected.order())!=null&&Objects.equals(cell,expected)&&Double.isFinite(duration)&&duration>0&&work()>=duration;}
    public boolean committed(Cell expected){if(expected==null||paused||find(expected.order())==null||!Objects.equals(cell,expected))return false;contributions++;work=0;cell=null;return true;}
    public static double duration(int skill,int benchTier){return Math.max(1.25,5-.3*Math.clamp(skill,1,10)-.25*(Math.clamp(benchTier,1,3)-1));}
}
