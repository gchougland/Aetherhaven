package com.hexvane.aetherhaven.workplace;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import java.util.*;
import org.joml.Vector3d;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class RoomExcavationOrderTest {
    @Test void pendingLegacyCornerSettlesBeforeReordering(){
        var gson=new Gson();var area=new WorkplaceArea(0,100,0,12,12,5);
        var points=ExcavationPlan.tunnel(area,ExcavationPlan.Entrance.NORTH,ExcavationPlan.Layout.ROOM);
        var plan=new ExcavationPlan();
        assertNull(plan.start(0,UUID.randomUUID(),UUID.randomUUID(),area,ExcavationPlan.Entrance.NORTH,
            ExcavationPlan.Layout.ROOM,points.stream().map(p->new ExcavationPlan.Cell(p,"Rock_Stone",0,1)).toList()));
        var json=gson.toJsonTree(plan).getAsJsonObject();var legacy=new ArrayList<>(points);
        legacy.sort(Comparator.comparingInt(Point::z).thenComparing(Comparator.comparingInt(Point::y).reversed()).thenComparingInt(Point::x));
        json.add("cells",gson.toJsonTree(legacy.stream().map(p->new ExcavationPlan.Cell(p,"Rock_Stone",0,1)).toList()));
        json.addProperty("pending",true);json.addProperty("dropsPrepared",true);json.add("drops",gson.toJsonTree(Map.of("Rare_Drop",3)));
        plan=gson.fromJson(json,ExcavationPlan.class);var corner=legacy.getFirst();
        assertEquals(corner,plan.next().point());assertTrue(plan.settle(plan.id(),true));
        assertEquals(new Point(corner.x(),100,corner.z()),plan.cargoPoint());assertEquals(1,plan.completed());
        assertEquals(points.getFirst(),plan.next().point());assertEquals(Map.of("Rare_Drop",3),plan.cargo());
        assertTrue(plan.pickup(plan.id(),"miner",plan.cargoPoint()));assertTrue(plan.deposit(plan.id()));
        while(plan.next()!=null){
            assertNotEquals(corner,plan.next().point());assertTrue(plan.prepareNext(Map::of));
            for(int i=0;i<12;i++)plan.attend(.5,true,1);
            assertTrue(plan.begin(plan.id(),6,plan.next().point(),()->true));assertTrue(plan.settle(plan.id(),true));
        }
        assertEquals(405,plan.completed());assertEquals(Map.of("Rare_Drop",3),plan.output());
    }
    @Test void everyRoomCellIsReachableFromANarrowEntranceInAllDirections(){
        for(var side:ExcavationPlan.Entrance.values()){
            var area=new WorkplaceArea(0,100,0,12,12,5);
            var cells=ExcavationPlan.tunnel(area,side,ExcavationPlan.Layout.ROOM);
            var plan=new ExcavationPlan();
            assertNull(plan.start(0,UUID.randomUUID(),UUID.randomUUID(),area,side,ExcavationPlan.Layout.ROOM,
                cells.stream().map(p->new ExcavationPlan.Cell(p,"Rock_Stone",0,1)).toList()));
            var first=cells.getFirst();
            int dx=side==ExcavationPlan.Entrance.WEST?-1:side==ExcavationPlan.Entrance.EAST?1:0;
            int dz=side==ExcavationPlan.Entrance.NORTH?-1:side==ExcavationPlan.Entrance.SOUTH?1:0;
            var clear=new HashSet<Point>();
            for(int d=1;d<=3;d++)for(int w=-1;w<=1;w++)for(int h=0;h<3;h++)
                clear.add(new Point(first.x()+dx*d+(dz!=0?w:0),100+h,first.z()+dz*d+(dx!=0?w:0)));
            var position=new Vector3d(first.x()+dx+.5,100,first.z()+dz+.5);
            for(var cell:cells){
                var approach=ExcavationApproach.choose(plan,cell,position,0,p->
                    p.y()==100&&clear.contains(p)&&clear.contains(new Point(p.x(),101,p.z()))&&clear.contains(new Point(p.x(),102,p.z()))
                        ?new Vector3d(p.x()+.5,100,p.z()+.5):null,
                    p->ExcavationApproach.clearReach(p,cell,clear::contains));
                assertNotNull(approach,side+" blocked at "+cell);
                position=approach;clear.add(cell);
            }
            assertEquals(405,cells.size());
        }
    }

    @Test void legacyRoomRetainsCompletedCellsAndPreparedDropsAcrossReload(){
        for(var rolled:List.of(Map.of("Rare_Drop",3),Map.<String,Integer>of())){
            var gson=new Gson();var area=new WorkplaceArea(0,100,0,12,12,5);
            var points=ExcavationPlan.tunnel(area,ExcavationPlan.Entrance.NORTH,ExcavationPlan.Layout.ROOM);
            var plan=new ExcavationPlan();
            assertNull(plan.start(0,UUID.randomUUID(),UUID.randomUUID(),area,ExcavationPlan.Entrance.NORTH,
                ExcavationPlan.Layout.ROOM,points.stream().map(p->new ExcavationPlan.Cell(p,"Rock_Stone",0,1)).toList()));
            var json=gson.toJsonTree(plan).getAsJsonObject();
            var legacy=new ArrayList<>(points);
            legacy.sort(Comparator.comparingInt(Point::z).thenComparing(Comparator.comparingInt(Point::y).reversed()).thenComparingInt(Point::x));
            json.add("cells",gson.toJsonTree(legacy.stream().map(p->new ExcavationPlan.Cell(p,"Rock_Stone",0,1)).toList()));
            json.add("drops",gson.toJsonTree(rolled));json.addProperty("dropsPrepared",true);json.addProperty("work",2.5);
            var corner=legacy.getFirst();plan=gson.fromJson(json,ExcavationPlan.class);
            assertEquals(points.getFirst(),plan.next().point());assertEquals(0,plan.completed());
            plan=gson.fromJson(gson.toJson(plan),ExcavationPlan.class);
            while(!plan.next().point().equals(corner)){
                assertTrue(plan.prepareNext(Map::of));for(int i=0;i<12;i++)plan.attend(.5,true,1);
                assertTrue(plan.begin(plan.id(),6,plan.next().point(),()->true));assertTrue(plan.settle(plan.id(),true));
            }
            var completed=plan.completed();plan=gson.fromJson(gson.toJson(plan),ExcavationPlan.class);
            assertEquals(completed,plan.completed());
            assertTrue(plan.prepareNext(()->{fail("Saved roll must not be rerolled");return Map.of();}));
            assertEquals(rolled,plan.drops());assertEquals(2.5,plan.work());
        }
    }
}
