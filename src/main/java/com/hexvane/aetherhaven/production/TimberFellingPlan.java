package com.hexvane.aetherhaven.production;

import java.util.*;
import java.util.function.*;
import static com.hexvane.aetherhaven.production.TimberHarvestPlan.*;

/** Restores only the saved footprint of an owned, intact mature tree. No connected-block search. */
public final class TimberFellingPlan {
    public record Change(Position position, Observed before, Observed after) {}
    public record Inspection(TimberFellingPlan plan, String issue) {
        public boolean ready() { return plan != null && issue == null; }
    }
    private final UUID tree;
    private final int yield;
    private final List<Change> changes;
    private final Map<Position, Observed> snapshot;
    private TimberFellingPlan(UUID tree, int yield, List<Change> changes, Map<Position, Observed> snapshot) {
        this.tree=tree; this.yield=yield; this.changes=List.copyOf(changes); this.snapshot=Map.copyOf(snapshot);
    }
    public UUID tree() { return tree; }
    public int yield() { return yield; }
    public List<Change> changes() { return changes; }

    /** expanded contains the mature prefab and its native hitbox fillers, with no cells outside the receipt. */
    public static Inspection inspect(TimberProductionState.Planting planting, Map<Position,Cell> expanded,
            Predicate<Position> bounds, Function<Position,Observed> read, BiPredicate<Cell,Observed> matches) {
        if (planting==null || planting.tree()==null || planting.tree().issue(bounds)!=null)
            return reject("This tree's planting record needs review.");
        var receipt=planting.tree(); var footprint=new HashSet<>(receipt.footprint());
        footprint.add(new Position(receipt.root().x(),receipt.root().y()-1,receipt.root().z()));
        var originals=new LinkedHashMap<Position,TimberProductionState.Original>();
        for(var original:planting.originals()) {
            if(original==null || original.position()==null || original.block()==null || original.block().isBlank()
                    || original.rotation()<0 || !bounds.test(original.position()) || originals.put(original.position(),original)!=null)
                return reject("This tree's original ground needs review.");
        }
        if(!originals.keySet().equals(footprint) || !footprint.containsAll(expanded.keySet()))
            return reject("This tree's growing space has changed.");
        for(var cell:receipt.mature().cells()) if(!cell.equals(expanded.get(cell.position())))
            return reject("This tree's mature shape has changed.");
        var earlierRoots=new HashSet<Position>();
        for(var stage:receipt.stages())if(stage.index()<receipt.mature().index())
            for(var cell:stage.cells())if(cell.position().y()<receipt.root().y())earlierRoots.add(cell.position());
        var snapshot=new LinkedHashMap<Position,Observed>();var changes=new ArrayList<Change>();int yield=0;
        for(var original:originals.values()) {
            var p=original.position();var current=read.apply(p);
            if(current==null || !current.loaded())return reject("The whole tree must be in the active area.");
            if(current.protectedCell())return reject("A protected block occupies this tree's growing space.");
            snapshot.put(p,current);
            var restored=new Observed(true,original.block(),original.rotation(),0,false);
            var expected=expanded.get(p);
            // Native roots may leave nonreplaceable underground stone untouched.
            boolean untouched=current.equals(restored);
            if(expected!=null && !(p.y()<receipt.root().y() && untouched)) {
                if(!matches.test(expected,current))return reject("This tree is still growing or its blocks have changed.");
                if(expected.kind()==Kind.TIMBER && expected.filler()==0)yield++;
            } else if(!untouched && !(earlierRoots.contains(p) && "Empty".equals(current.blockId())
                    && current.rotation()==0 && current.filler()==0)) {
                return reject("The space around this tree has changed. Check it before harvesting.");
            }
            if(!untouched)changes.add(new Change(p,current,restored));
        }
        if(yield<1 || changes.isEmpty())return reject("This tree has no timber ready to harvest.");
        // Canopy first keeps the intact lower trunk visible until the final changes.
        changes.sort(Comparator.comparingInt((Change c)->c.position().y()).reversed());
        return new Inspection(new TimberFellingPlan(receipt.id(),yield,changes,snapshot),null);
    }

    /** Recheck every cell before the first write. A failed write never authorizes timber credit. */
    public boolean restore(Function<Position,Observed> read, BiPredicate<Position,Observed> write) {
        for(var cell:snapshot.entrySet())if(!cell.getValue().equals(read.apply(cell.getKey())))return false;
        var applied=new ArrayList<Change>();
        try {
            for(var change:changes) {
                if(!write.test(change.position(),change.after())) { rollback(applied,read,write); return false; }
                applied.add(change);
            }
            return true;
        } catch(RuntimeException failure) {
            rollback(applied,read,write);
            throw failure;
        }
    }
    private static void rollback(List<Change> applied,Function<Position,Observed> read,BiPredicate<Position,Observed> write) {
        for(int i=applied.size()-1;i>=0;i--) {
            var change=applied.get(i);
            // Never replace a fresh edit made by a block callback during removal.
            if(change.after().equals(read.apply(change.position())))write.test(change.position(),change.before());
        }
    }
    private static Inspection reject(String issue) { return new Inspection(null,issue); }
}
