package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import org.joml.Vector3d;

/** Mining uses actual block reach, including a worker already standing above a descending face. */
public final class ExcavationApproach {
    private ExcavationApproach() {}
    public static Point feet(Vector3d position){return new Point((int)Math.floor(position.x),(int)Math.floor(position.y+.001),(int)Math.floor(position.z));}
    /** Check the tool path from the worker's chest to the reviewed block, not just distance. */
    public static boolean clearReach(Vector3d position,Point target,Predicate<Point> clear){
        double x=position.x,y=position.y+1.5,z=position.z;
        double dx=target.x()+.5-x,dy=target.y()+.5-y,dz=target.z()+.5-z;
        int steps=(int)Math.ceil(Math.sqrt(dx*dx+dy*dy+dz*dz)*8);
        if(steps>80)return false;
        Point previous=null;
        for(int i=0;i<steps;i++){
            double t=(double)i/Math.max(1,steps);
            var p=new Point((int)Math.floor(x+dx*t),(int)Math.floor(y+dy*t),(int)Math.floor(z+dz*t));
            if(p.equals(target))return true;
            if(!p.equals(previous)&&!clear.test(p))return false;
            previous=p;
        }
        return true;
    }
    public static boolean supported(Vector3d position,double minX,double maxX,double minZ,double maxZ,Predicate<Point> stand){
        int y=feet(position).y();
        int x0=(int)Math.floor(position.x+minX+.001),x1=(int)Math.floor(position.x+maxX-.001);
        int z0=(int)Math.floor(position.z+minZ+.001),z1=(int)Math.floor(position.z+maxZ-.001);
        if(x1<x0||z1<z0||x1-x0>2||z1-z0>2)return false;
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)if(stand.test(new Point(x,y,z)))return true;
        return false;
    }
    /** Intersect the finite tool path with an actual block hitbox, not its entire voxel. */
    public static boolean intersects(Vector3d position,Point target,Point cell,com.hypixel.hytale.math.shape.Box box){
        double[] start={position.x,position.y+1.5,position.z};
        double[] end={target.x()+.5,target.y()+.5,target.z()+.5};
        double[] min={cell.x()+box.min.x,cell.y()+box.min.y,cell.z()+box.min.z};
        double[] max={cell.x()+box.max.x,cell.y()+box.max.y,cell.z()+box.max.z};
        double near=0,far=1;
        for(int axis=0;axis<3;axis++){
            double delta=end[axis]-start[axis];
            if(Math.abs(delta)<1e-9){if(start[axis]<=min[axis]||start[axis]>=max[axis])return false;continue;}
            double a=(min[axis]-start[axis])/delta,b=(max[axis]-start[axis])/delta;
            near=Math.max(near,Math.min(a,b));far=Math.min(far,Math.max(a,b));
            if(near>=far)return false;
        }
        return true;
    }
    public static boolean arrived(ExcavationPlan plan,Point target,Vector3d position,Vector3d approach){
        double dx=position.x-approach.x,dz=position.z-approach.z;
        // Native walking can stop straddling the preceding one-block stair edge.
        // Mining is still safe there if the actual feet position can reach the block.
        return dx*dx+dz*dz<=.49&&Math.abs(position.y-approach.y)<=1.05&&plan.withinWorkReach(feet(position),target);
    }
    public static Vector3d choose(ExcavationPlan plan,Point target,Vector3d from,int attempt,Function<Point,Vector3d> stand){
        return choose(plan,target,from,attempt,stand,p->true);
    }
    /** The side lanes of a three wide passage are valid rail installation positions. */
    public static boolean besideRail(Point target,Vector3d position){
        return Math.abs(position.x-target.x()-.5)>=.9||Math.abs(position.z-target.z()-.5)>=.9;
    }
    public static Vector3d choose(ExcavationPlan plan,Point target,Vector3d from,int attempt,Function<Point,Vector3d> stand,Predicate<Vector3d> usable){
        var feet=feet(from);
        var current=stand.apply(feet);
        if(attempt==0&&current!=null&&Math.abs(current.y-from.y)<=.15&&plan.withinWorkReach(feet,target)&&usable.test(from))return new Vector3d(from.x,current.y,from.z);
        int reach=plan.layout()==ExcavationPlan.Layout.ROOM?6:plan.area()!=null&&ExcavationPlan.passageHeight(plan.area(),plan.layout())==4?5:4;
        var options=new ArrayList<Vector3d>();
        for(int dx=-reach;dx<=reach;dx++)for(int dz=-reach;dz<=reach;dz++)for(int dy=-reach;dy<=2;dy++){
            var cell=new Point(target.x()+dx,target.y()+dy,target.z()+dz);
            if(!plan.withinWorkReach(cell,target))continue;
            var point=stand.apply(cell);if(point!=null&&usable.test(point))options.add(point);
        }
        options.sort(Comparator.comparingDouble(from::distanceSquared));
        return options.isEmpty()?null:options.get(Math.floorMod(attempt,options.size()));
    }
}
