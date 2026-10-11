package com.hexvane.aetherhaven.production;

import java.util.*;
import java.util.function.*;
import static com.hexvane.aetherhaven.production.TimberHarvestPlan.*;

/**
 * Restores only the saved footprint of an owned mature tree, and within it only the cells that still
 * hold that tree. Player edits are left alone and simply reduce the harvest. No connected-block search.
 */
public final class TimberFellingPlan {
    /** Nothing of the tree remains. Its stone can grow a new one. */
    public static final String GONE = "This tree is gone. A new one will grow here.";
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
            snapshot.put(p,current);
            var restored=new Observed(true,original.block(),original.rotation(),0,false);
            var expected=expanded.get(p);
            // Native roots may leave nonreplaceable underground stone untouched.
            if(current.equals(restored))continue;
            // Anything a player trimmed, replaced or built here is theirs. Only the tree's own remaining blocks are removed.
            if(current.protectedCell())continue;
            if(expected!=null && matches.test(expected,current)) {
                if(expected.kind()==Kind.TIMBER && expected.filler()==0)yield++;
            } else if(!(earlierRoots.contains(p) && "Empty".equals(current.blockId())
                    && current.rotation()==0 && current.filler()==0)) continue;
            changes.add(new Change(p,current,restored));
        }
        if(changes.isEmpty())return reject(GONE);
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
