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
