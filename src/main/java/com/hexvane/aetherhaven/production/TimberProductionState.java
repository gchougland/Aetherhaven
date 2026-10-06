package com.hexvane.aetherhaven.production;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import static com.hexvane.aetherhaven.production.TimberHarvestPlan.*;

/** Saved planting ownership and original terrain. No entity, chunk or world references. */
public final class TimberProductionState {
    public record Ground(boolean loaded, String block, int rotation, int filler,
            boolean empty, boolean soil, boolean naturalTerrain, boolean protectedCell) {}
    public record Original(Position position, String block, int rotation) {}
    public record Planting(TimberPlantingReceipt tree, List<Original> originals) {
        public Planting { originals = List.copyOf(originals); }
    }
    public record Prepared(Planting planting, String issue) {
        public boolean ready() { return planting != null && issue == null; }
    }
    private Map<Integer, Planting> trees = new LinkedHashMap<>();
    private Map<Integer, TimberPlantingReceipt> groves = new LinkedHashMap<>();
    private long layoutRevision;
    /** At most one tree's yield can wait for delivery. It is not warehouse stock. */
    public record Cargo(UUID tree, String output, Position source, int remaining, int carried) {}
    private Cargo cargo;
    private List<Cargo> remainingCargo=new ArrayList<>();
    private int nextSlot;
    private Map<Integer, Double> chopping = new LinkedHashMap<>();
    private Set<UUID> editedTrees = new HashSet<>();
    // Once explicitly converted, harvesting the last tree must not restart the
    // old resource timer. The future removal workflow must retain this decision.
    private boolean adopted;

    public boolean adopted() { return adopted || trees != null && !trees.isEmpty(); }
    public Map<Integer, Planting> trees() { return trees == null ? Map.of() : Collections.unmodifiableMap(trees); }
    public Map<Integer, TimberPlantingReceipt> groves() { return groves == null ? Map.of() : Collections.unmodifiableMap(groves); }
    public long layoutRevision() { return layoutRevision; }
    public Cargo cargo() { return cargo; }
    public Map<String,Integer> waitingProducts() {
        var result=new LinkedHashMap<String,Integer>();
        if(cargo!=null)result.put(cargo.output(),cargo.remaining());
        if(remainingCargo!=null)for(var held:remainingCargo)result.merge(held.output(),held.remaining(),Math::addExact);
        return Collections.unmodifiableMap(result);
    }
    public int firstSlot(int count) { return count<=0?0:Math.floorMod(nextSlot,count); }
    public void rotateAfter(int slot) { nextSlot=slot+1; }
    public String activate(long expectedRevision) {
        if(expectedRevision!=layoutRevision)return "The grove plan changed. Open it again before starting work.";
        if(adopted())return "This lumbermill already works its grove.";
        if(groves().isEmpty())return "Save a tree location before starting grove work.";
        adopted=true;layoutRevision++;return null;
    }
    /** Retires ownership only. Player edits and remaining tree blocks are left exactly where they are. */
    public String releaseEdited(int slot,long expectedRevision) {
        var planted=trees().get(slot);
        if(expectedRevision!=layoutRevision)return "The grove plan changed. Open it again before releasing this tree.";
        if(planted==null||!edited(planted.tree().id()))return "Only a tree changed by building or breaking can be released here.";
        editedTrees.remove(planted.tree().id());trees.remove(slot);if(chopping!=null)chopping.remove(slot);
        layoutRevision++;return null;
    }
    public boolean edited(UUID tree) { return editedTrees != null && editedTrees.contains(tree); }
    /** Remember completed player edits even when they happen to resemble native root removal. */
    public boolean markEdited(Position position) {
        boolean changed=false;
        for(var planted:trees().values())if(planted.originals().stream().anyMatch(c->c.position().equals(position))) {
            if(editedTrees==null)editedTrees=new HashSet<>();
            changed|=editedTrees.add(planted.tree().id());
        }
        return changed;
    }
    public double chopping(int slot) { return chopping == null ? 0 : chopping.getOrDefault(slot, 0.0); }
    public void work(int slot, double seconds) {
        if (!trees().containsKey(slot) || edited(trees().get(slot).tree().id()) || cargo != null || !Double.isFinite(seconds) || seconds <= 0) return;
        if (chopping == null) chopping = new LinkedHashMap<>();
        chopping.put(slot, Math.min(86_400, chopping(slot) + Math.min(seconds, 1)));
    }
    /** Only a successful physical removal creates a pending delivery. No stock is credited here. */
    public boolean fell(int slot, UUID treeId, int yield, BooleanSupplier remove) {
        return fell(slot,treeId,yield,Map.of(),remove);
    }
    public boolean fell(int slot,UUID treeId,int yield,Map<String,Integer> extras,BooleanSupplier remove) {
        if(extras==null||extras.size()>4||extras.entrySet().stream().anyMatch(e->e.getKey()==null||e.getKey().isBlank()||e.getValue()==null||e.getValue()<1||e.getValue()>TimberHarvestPlan.MAX_TREE_CELLS*16))return false;
        var planted = trees().get(slot);
        if(planted!=null&&extras.containsKey(planted.tree().output()))return false;
        if (cargo != null || planted == null || edited(treeId) || !planted.tree().id().equals(treeId)
                || yield < 1 || yield > TimberHarvestPlan.MAX_TREE_CELLS || !remove.getAsBoolean()) return false;
        cargo = new Cargo(treeId, planted.tree().output(), planted.tree().root(), yield, 0);
        remainingCargo=new ArrayList<>();
        for(var entry:extras.entrySet())remainingCargo.add(new Cargo(treeId,entry.getKey(),planted.tree().root(),entry.getValue(),0));
        trees.remove(slot);
        if (chopping != null) chopping.remove(slot);
        nextSlot=slot+1;
        adopted = true;
        return true;
    }
    /** A paid charring batch joins the same saved delivery path, already held by the attending Logger. */
    public boolean charLogs(Position source, BooleanSupplier pay) {
        if (!adopted() || source == null || cargo != null
            || remainingCargo != null && !remainingCargo.isEmpty() || !pay.getAsBoolean()) return false;
        cargo = new Cargo(UUID.randomUUID(), TimberCharring.OUTPUT, source, 1, 1);
        return true;
    }
    /** Caller has verified attendance at the felled tree. One bounded armful per trip. */
    public int pickUp(int carryLimit) {
        if(cargo==null || carryLimit<=0 || cargo.carried()>0)return 0;
        int count=Math.min(cargo.remaining(),carryLimit);
        cargo=new Cargo(cargo.tree(),cargo.output(),cargo.source(),cargo.remaining(),count);
        return count;
    }
    /** Caller has verified physical attendance at this plot's storage on the world thread. */
    public int deliver(PlotProductionState production, long capacity, int carryLimit) {
        if (cargo == null || carryLimit <= 0 || capacity <= 0) return 0;
        long cap = Math.min(capacity, ProductionCatalog.MAX_STORAGE_PER_OUTPUT);
        int count = (int)Math.min(Math.min(cargo.carried(), carryLimit), Math.max(0, cap - production.getAmount(cargo.output())));
        if (count <= 0) return 0;
        production.addAmount(cargo.output(), count, cap);
        if(count==cargo.remaining()) {
            if(remainingCargo==null||remainingCargo.isEmpty()){cargo=null;}
            else {var next=remainingCargo.removeFirst();cargo=new Cargo(next.tree(),next.output(),next.source(),next.remaining(),0);}
        }
        else cargo=new Cargo(cargo.tree(),cargo.output(),cargo.source(),cargo.remaining()-count,cargo.carried()-count);
        return count;
    }
    public boolean conflictsWith(TimberPlantingReceipt tree) {
        Set<Position> footprint = new HashSet<>(tree.footprint()); footprint.add(below(tree.root()));
        for (var other : groves().values()) {
            if (other == null || other.root() == null || footprint.contains(below(other.root()))
                || other.footprint().stream().anyMatch(footprint::contains)) return true;
        }
        for (var other : trees().values()) {
            if (other == null || other.originals().stream().anyMatch(c -> footprint.contains(c.position()))) return true;
        }
        return false;
    }

    /** Planning never plants a tree, spends supplies or changes the production mode. */
    public String plan(int slot, int allowance, long expectedRevision, TimberPlantingReceipt tree,
            Predicate<Position> withinGrove, Function<Position, Ground> read) {
        if (expectedRevision != layoutRevision) return "The grove plan changed. Open it again before choosing a tree location.";
        Prepared prepared = prepare(slot, allowance, tree, withinGrove, read);
        if (!prepared.ready()) return prepared.issue();
        if (groves == null) groves = new LinkedHashMap<>();
        groves.put(slot, tree); layoutRevision++;
        return null;
    }
    public String removePlan(int slot, long expectedRevision) {
        if (expectedRevision != layoutRevision) return "The grove plan changed. Open it again before removing a location.";
        if (trees().containsKey(slot)) return "This location has a planted tree. It cannot be removed from the plan yet.";
        if (groves == null || !groves.containsKey(slot)) return "This slot has no planned tree location.";
        groves.remove(slot); layoutRevision++; return null;
    }

    public Prepared prepare(int slot, int allowance, TimberPlantingReceipt tree, Predicate<Position> withinGrove,
            Function<Position, Ground> read) {
        if (slot < 0 || slot >= allowance || allowance > WorkplaceProductionUpgrades.MAX_SLOT_COUNT || tree == null)
            return rejected("This timber slot is not available.");
        if (trees().containsKey(slot)) return rejected("This timber slot already has a growing tree.");
        String issue = tree.issue(withinGrove);
        if (issue != null) return rejected(issue);
        if (tree.initialGeneration() != 0) return rejected("Plant a fresh sapling to start this tree.");
        Set<Position> footprint = new LinkedHashSet<>(tree.footprint());
        Position below = below(tree.root());
        if (below == null || !withinGrove.test(below)) return rejected("The tree's soil must be inside its grove.");
        footprint.add(below);
        for (var planned : groves().entrySet()) {
            if (planned.getKey() == slot) continue;
            var other = planned.getValue();
            if (other == null || other.root() == null) return rejected("A saved grove location needs review before planting.");
            if (other.footprint().stream().anyMatch(footprint::contains) || footprint.contains(below(other.root())))
                return rejected("This tree would overlap another planned tree.");
        }
        for (Planting existing : trees().values()) {
            if (existing == null || existing.tree() == null) return rejected("A saved tree needs review before planting.");
            if (existing.tree().id().equals(tree.id())) return rejected("This planting is already recorded.");
            if (existing.originals().stream().anyMatch(c -> footprint.contains(c.position())))
                return rejected("This tree would overlap another growing tree.");
        }
        List<Original> originals = new ArrayList<>();
        for (Position point : footprint) {
            Ground ground = read.apply(point);
            String blocked = groundIssue(point, tree.root(), below, ground);
            if (blocked != null) return rejected(blocked);
            originals.add(new Original(point, ground.block(), ground.rotation()));
        }
        return new Prepared(new Planting(tree, originals), null);
    }

    /**
     * Rechecks the complete preflight on the world thread. No ownership is saved
     * unless placement returns success. The caller saves the town immediately
     * after success and owns worker-attendance prerequisites. Planting consumes no items.
     */
    public String plant(int slot, int allowance, Prepared prepared, Predicate<Position> withinGrove,
            Function<Position, Ground> read, BooleanSupplier place) {
        if (prepared == null || !prepared.ready()) return "Prepare this tree before planting it.";
        Prepared current = prepare(slot, allowance, prepared.planting().tree(), withinGrove, read);
        if (!current.ready()) return current.issue();
        if (!current.planting().originals().equals(prepared.planting().originals()))
            return "The growing space changed. Check it before planting.";
        if (!place.getAsBoolean()) return "The sapling could not be planted.";
        if (trees == null) trees = new LinkedHashMap<>();
        trees.put(slot, prepared.planting()); adopted = true;
        return null;
    }

    private static Position below(Position root) {
        return root.y() == Integer.MIN_VALUE ? null : new Position(root.x(), root.y()-1, root.z());
    }
    private static String groundIssue(Position point, Position root, Position below, Ground ground) {
        if (ground == null || !ground.loaded()) return "The growing space must be in the active area.";
        if (ground.block() == null || ground.block().isBlank() || ground.rotation() < 0
                || ground.filler() != 0 || ground.protectedCell()) return "A protected block is inside the growing space.";
        if (point.equals(below) && !ground.soil()) return "The sapling needs soil beneath it.";
        if (point.y() >= root.y() && !ground.empty()) return "Clear the space where this tree will grow.";
        if (point.y() < root.y() && !ground.empty() && !ground.naturalTerrain())
            return "The tree's roots would reach a built structure.";
        return null;
    }
    private static Prepared rejected(String issue) { return new Prepared(null, issue); }
}
