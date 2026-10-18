package com.hexvane.aetherhaven.production;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BarnPenCompatibilityTest {
    @Test void worldScanRequiresEntireAnimalSpaceAndFloorInsideLoadedBarn() {
        var blocked=new HashSet<Point>();boolean[] loaded={true};boolean[] supported={true};
        var cells=new BarnPenCompatibility.Cells() {
            public boolean inside(int x,int y,int z){return x>=0&&x<9&&z>=0&&z<9&&y>=0&&y<6;}
            public boolean loaded(int x,int z){return loaded[0];}
            public boolean floor(int x,int y,int z){return supported[0];}
            public boolean empty(int x,int y,int z){return !blocked.contains(new Point(x,y,z));}
        };
        var at=new org.joml.Vector3i(4,1,4);
        assertEquals(BarnPenCompatibility.Space.CLEAR,BarnPenCompatibility.space(at,cells));
        blocked.add(new Point(5,3,4));assertEquals(BarnPenCompatibility.Space.BLOCKED,BarnPenCompatibility.space(at,cells));blocked.clear();
        supported[0]=false;assertEquals(BarnPenCompatibility.Space.BLOCKED,BarnPenCompatibility.space(at,cells));supported[0]=true;
        loaded[0]=false;assertEquals(BarnPenCompatibility.Space.UNLOADED,BarnPenCompatibility.space(at,cells));loaded[0]=true;
        assertEquals(BarnPenCompatibility.Space.BLOCKED,BarnPenCompatibility.space(new org.joml.Vector3i(0,1,4),cells));
        assertEquals(BarnPenCompatibility.Space.BLOCKED,BarnPenCompatibility.space(new org.joml.Vector3i(4,3,4),cells));
    }
    private static List<Point> floor(){var result=new ArrayList<Point>();for(int x=0;x<12;x++)for(int z=0;z<12;z++)result.add(new Point(x,1,z));return result;}
    @Test void oldBarnGetsFiveDistinctSpacedPensAndTheySurviveReload() {
        var state=new HerdProductionState();var candidates=floor();
        var pens=BarnPenCompatibility.plan(List.of(),state.automaticPens(),candidates,p->BarnPenCompatibility.Space.CLEAR);
        assertEquals(5,pens.size());assertTrue(pens.values().stream().allMatch(BarnPenCompatibility.Pen::automatic));
        for(var a:pens.entrySet())for(var b:pens.entrySet())if(!a.getKey().equals(b.getKey()))assertTrue(BarnPenCompatibility.separated(a.getValue().local(),b.getValue().local()));
        var loaded=new Gson().fromJson(new Gson().toJson(state),HerdProductionState.class);
        Collections.reverse(candidates);
        assertEquals(pens,BarnPenCompatibility.plan(List.of(),loaded.automaticPens(),candidates,p->BarnPenCompatibility.Space.CLEAR));
    }
    @Test void unloadedPositionsStayPutAndBlockedPositionsFindSafeAlternatives() {
        var saved=new HashMap<Integer,Point>();saved.put(0,new Point(0,1,0));saved.put(1,new Point(3,1,0));
        var pens=BarnPenCompatibility.plan(List.of(),saved,floor(),p->p.x()==0&&p.z()==0?BarnPenCompatibility.Space.UNLOADED:p.x()==3&&p.z()==0?BarnPenCompatibility.Space.BLOCKED:BarnPenCompatibility.Space.CLEAR);
        assertEquals(new Point(0,1,0),pens.get(0).local());assertNotEquals(new Point(3,1,0),pens.get(1).local());assertEquals(5,pens.size());
    }
    @Test void updatedAuthorsMarkersTakePrecedenceAndNeverDuplicateAnimalsOrSlots() {
        var saved=new HashMap<Integer,Point>();saved.put(0,new Point(0,1,0));
        var authored=List.of(new BarnPenCompatibility.Pen(new Point(6,1,6),90,false));
        var pens=BarnPenCompatibility.plan(authored,saved,floor(),p->BarnPenCompatibility.Space.CLEAR);
        assertEquals(authored.getFirst(),pens.get(0));assertFalse(saved.containsKey(0));assertEquals(5,pens.size());
        assertTrue(pens.values().stream().filter(BarnPenCompatibility.Pen::automatic).allMatch(p->BarnPenCompatibility.separated(p.local(),authored.getFirst().local())));
    }
    @Test void crampedOrUnloadedBarnDoesNotInventUsableSpace() {
        assertTrue(BarnPenCompatibility.plan(List.of(),new HashMap<>(),floor(),p->BarnPenCompatibility.Space.BLOCKED).isEmpty());
        assertTrue(BarnPenCompatibility.plan(List.of(),new HashMap<>(),floor(),p->BarnPenCompatibility.Space.UNLOADED).isEmpty());
        assertEquals(1,BarnPenCompatibility.plan(List.of(),new HashMap<>(),List.of(new Point(0,1,0)),p->BarnPenCompatibility.Space.CLEAR).size());
    }
}
