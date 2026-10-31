package com.hexvane.aetherhaven.production;

import java.util.*;
import java.util.function.*;
import static com.hexvane.aetherhaven.production.TimberHarvestPlan.*;
import static com.hexvane.aetherhaven.production.TimberProductionState.Ground;

/** Soft, non-growing vegetation can be cleared for a tree; terrain, crops and player storage cannot. */
public final class TimberVegetation {
    private TimberVegetation() {}
    static boolean replaceable(String id, String drawType, boolean softGathering, boolean farming) {
        return id!=null && (id.startsWith("Plant_Grass") || id.startsWith("Plant_Bush") || id.startsWith("Plant_Fern"))
            && softGathering && !farming && !"Cube".equals(drawType) && !"CubeWithModel".equals(drawType);
    }
    private static Set<Position> plantCells(Position p,Ground ground){return ground.vegetationFootprint().isEmpty()?Set.of(p):ground.vegetationFootprint();}
    /** A plant touching the tree can extend beyond its sparse branches; clear its complete safe shape within town. */
    static String issue(Position p,Ground plant,Predicate<Position> bounds,Function<Position,Ground> read) {
        if(!plant.softVegetation())return null;
        var anchor=TimberGrowingSpace.fillerAnchor(p,plant.filler());
        for(var cell:plantCells(p,plant)) {
            if(!bounds.test(cell))return "A grass clump or bush reaches outside your town. Clear that plant first.";
            var current=read.apply(cell);
            if(current==null||!current.loaded())return "Move closer so the whole grass clump or bush is loaded.";
            if(empty(current))continue;
            if(current.protectedCell()||!current.softVegetation()||!plant.block().equals(current.block())
                    ||plant.rotation()!=current.rotation()||!anchor.equals(TimberGrowingSpace.fillerAnchor(cell,current.filler())))
                return "Something important is mixed into this grass clump or bush. Clear the plant first.";
        }
        return null;
    }
    public static boolean clearAndPlant(Set<Position> footprint, Function<Position,Ground> read,
            BiPredicate<Position,Observed> write, BooleanSupplier plant) {
        return clearAndPlant(footprint,p->true,read,write,plant);
    }
    public static boolean clearAndPlant(Set<Position> footprint,Predicate<Position> bounds, Function<Position,Ground> read,
            BiPredicate<Position,Observed> write, BooleanSupplier plant) {
        var snapshot=new LinkedHashMap<Position,Ground>();
        for(var p:footprint) {
            var current=read.apply(p);
            if(current==null||!current.loaded()||current.protectedCell()||!bounds.test(p))return false;
            snapshot.put(p,current);
        }
        for(var row:new ArrayList<>(snapshot.entrySet()))if(row.getValue().softVegetation()) {
            if(issue(row.getKey(),row.getValue(),bounds,read)!=null)return false;
            for(var p:plantCells(row.getKey(),row.getValue()))snapshot.putIfAbsent(p,read.apply(p));
        }
        var cleared=new ArrayList<Position>();
        try {
            var clearing=new ArrayList<>(snapshot.entrySet());
            clearing.sort(Comparator.comparingInt((Map.Entry<Position,Ground> row)->row.getKey().y()).reversed());
            for(var row:clearing) {
                if(!row.getValue().softVegetation())continue;
                if(!row.getValue().equals(read.apply(row.getKey()))||!write.test(row.getKey(),new Observed(true,"Empty",0,false))) {
                    restore(cleared,snapshot,read,write);return false;
                }
                cleared.add(row.getKey());
            }
            // Block callbacks must not let a new chest, wall or other edit be overwritten by the sapling.
            for(var row:snapshot.entrySet()) {
                var current=read.apply(row.getKey());
                if(row.getValue().softVegetation()? !empty(current):!row.getValue().equals(current)) {
                    restore(cleared,snapshot,read,write);return false;
                }
            }
            if(plant.getAsBoolean())return true;
            restore(cleared,snapshot,read,write);return false;
        }catch(RuntimeException failure){restore(cleared,snapshot,read,write);throw failure;}
    }
    private static boolean empty(Ground g){return g!=null&&g.loaded()&&g.empty()&&g.filler()==0&&!g.protectedCell();}
    private static void restore(List<Position> cleared,Map<Position,Ground> snapshot,Function<Position,Ground> read,BiPredicate<Position,Observed> write) {
        for(int i=cleared.size()-1;i>=0;i--){var p=cleared.get(i);var before=snapshot.get(p);
            if(empty(read.apply(p)))write.test(p,new Observed(true,before.block(),before.rotation(),before.filler(),false));}
    }
}
